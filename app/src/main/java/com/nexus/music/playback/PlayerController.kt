package com.nexus.music.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nexus.music.data.db.HistoryEntity
import com.nexus.music.data.db.NexusDatabase
import com.nexus.music.data.db.QueueEntity
import com.nexus.music.data.db.TrackEntity
import com.nexus.music.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class QueueItem(val mediaId: String, val title: String, val artist: String)

data class PlayerState(
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueItem> = emptyList(),
    val currentIndex: Int = 0
)

/**
 * Мост между UI и PlaybackService (Media3). Хранит очередь и позицию в Room/DataStore,
 * чтобы после перезапуска приложения всё восстанавливалось.
 */
class PlayerController(
    private val context: Context,
    private val db: NexusDatabase,
    private val settings: SettingsStore,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private var controller: MediaController? = null
    private var connecting = false
    private var tickerStarted = false
    private var lastLoggedId: String? = null
    private var queue: List<QueueItem> = emptyList()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                rebuildQueue()
                persistQueue()
            }
            refreshState()
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                if (player.isPlaying) logHistory() else savePlaybackPosition()
            }
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                if (player.isPlaying) logHistory()
                savePlaybackPosition()
            }
        }
    }

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        scope.launch {
            try {
                val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                val c = MediaController.Builder(context, token)
                    .setListener(object : MediaController.Listener {
                        override fun onDisconnected(controller: MediaController) {
                            this@PlayerController.controller = null
                            _state.value = PlayerState()
                        }
                    })
                    .buildAsync()
                    .await()
                controller = c
                restoreQueue(c)
                c.addListener(listener)
                rebuildQueue()
                refreshState()
                startTicker()
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось подключиться к PlaybackService", e)
            } finally {
                connecting = false
            }
        }
    }

    // ---------- Управление ----------

    fun playTracks(tracks: List<TrackEntity>, startIndex: Int, shuffle: Boolean = false) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        val index = if (shuffle) tracks.indices.random() else startIndex.coerceIn(0, tracks.lastIndex)
        lastLoggedId = null
        if (shuffle) {
            c.shuffleModeEnabled = true
            scope.launch { settings.setShuffle(true) }
        }
        c.setMediaItems(tracks.map { toMediaItem(it) }, index, 0L)
        c.prepare()
        c.play()
    }

    fun playQueueIndex(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            lastLoggedId = null
            c.seekToDefaultPosition(index)
            c.play()
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _position.value = positionMs
    }

    fun toggleShuffle() {
        val c = controller ?: return
        val value = !c.shuffleModeEnabled
        c.shuffleModeEnabled = value
        scope.launch { settings.setShuffle(value) }
    }

    fun cycleRepeat() {
        val c = controller ?: return
        val next = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        c.repeatMode = next
        scope.launch { settings.setRepeatMode(next) }
    }

    /** Добавляет трек в конец очереди; если очередь пуста — начинает воспроизведение. */
    fun enqueue(track: TrackEntity) {
        val c = controller ?: return
        val wasEmpty = c.mediaItemCount == 0
        c.addMediaItem(toMediaItem(track))
        if (wasEmpty) {
            c.prepare()
            c.play()
        }
    }

    fun playNext(track: TrackEntity) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            enqueue(track)
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, toMediaItem(track))
        }
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    /** Убирает из очереди треки, удалённые из медиатеки. */
    fun removeTracks(ids: Set<Long>) {
        val c = controller ?: return
        for (i in c.mediaItemCount - 1 downTo 0) {
            val id = c.getMediaItemAt(i).mediaId.toLongOrNull()
            if (id != null && id in ids) c.removeMediaItem(i)
        }
    }

    // ---------- Внутреннее ----------

    private fun toMediaItem(t: TrackEntity): MediaItem =
        MediaItem.Builder()
            .setMediaId(t.id.toString())
            .setUri(t.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setAlbumTitle(t.album)
                    .build()
            )
            .build()

    private suspend fun restoreQueue(c: MediaController) {
        if (c.mediaItemCount > 0) return
        val saved = db.queueDao().getAll()
        if (saved.isEmpty()) return
        val byId = db.trackDao().getByIds(saved.map { it.trackId }).associateBy { it.id }
        val ordered = saved.mapNotNull { byId[it.trackId] }
        if (ordered.isEmpty()) return
        val index = settings.queueIndex.first().coerceIn(0, ordered.lastIndex)
        val position = settings.queuePosition.first().coerceAtLeast(0L)
        c.shuffleModeEnabled = settings.shuffle.first()
        c.repeatMode = settings.repeatMode.first()
        c.setMediaItems(ordered.map { toMediaItem(it) }, index, position)
        c.prepare()
    }

    private fun rebuildQueue() {
        val c = controller ?: return
        queue = (0 until c.mediaItemCount).map { i ->
            val item = c.getMediaItemAt(i)
            QueueItem(
                mediaId = item.mediaId,
                title = item.mediaMetadata.title?.toString() ?: "",
                artist = item.mediaMetadata.artist?.toString() ?: ""
            )
        }
    }

    private fun persistQueue() {
        val rows = queue.mapNotNull { it.mediaId.toLongOrNull() }
            .mapIndexed { i, id -> QueueEntity(position = i, trackId = id) }
        scope.launch {
            try {
                db.queueDao().replace(rows)
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось сохранить очередь", e)
            }
        }
    }

    private fun refreshState() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val duration = c.duration
        _state.value = PlayerState(
            mediaId = item?.mediaId,
            title = item?.mediaMetadata?.title?.toString() ?: "",
            artist = item?.mediaMetadata?.artist?.toString() ?: "",
            album = item?.mediaMetadata?.albumTitle?.toString() ?: "",
            isPlaying = c.isPlaying,
            durationMs = if (duration == C.TIME_UNSET) 0L else duration.coerceAtLeast(0L),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queue = queue,
            currentIndex = c.currentMediaItemIndex
        )
        _position.value = c.currentPosition.coerceAtLeast(0L)
    }

    private fun logHistory() {
        val id = controller?.currentMediaItem?.mediaId ?: return
        if (id == lastLoggedId) return
        lastLoggedId = id
        val trackId = id.toLongOrNull() ?: return
        scope.launch {
            try {
                db.historyDao().insert(HistoryEntity(trackId = trackId, playedAt = System.currentTimeMillis()))
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось записать историю", e)
            }
        }
    }

    private fun savePlaybackPosition() {
        val c = controller ?: return
        val index = c.currentMediaItemIndex
        val position = c.currentPosition.coerceAtLeast(0L)
        scope.launch { settings.setPlayback(index, position) }
    }

    private fun startTicker() {
        if (tickerStarted) return
        tickerStarted = true
        scope.launch {
            var tick = 0
            while (isActive) {
                delay(500)
                val c = controller
                if (c != null && c.isPlaying) {
                    _position.value = c.currentPosition.coerceAtLeast(0L)
                    tick++
                    if (tick % 10 == 0) savePlaybackPosition()
                }
            }
        }
    }

    private companion object {
        const val TAG = "NexusPlayer"
    }
}
