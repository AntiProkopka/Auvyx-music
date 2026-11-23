package com.nexus.music.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nexus.music.data.db.HistoryEntity
import com.nexus.music.data.db.NexusDatabase
import com.nexus.music.data.db.QueueEntity
import com.nexus.music.data.db.TrackEntity
import com.nexus.music.data.settings.SettingsStore
import com.nexus.music.data.soundcloud.SoundCloudTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
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
    val artworkUri: String? = null,
    val sourceUri: String? = null,
    val audioProvider: String? = null,
    val isPlaying: Boolean = false,
    val canSeek: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueItem> = emptyList(),
    val currentIndex: Int = 0,
    val speed: Float = 1f,
    val pitch: Float = 1f
)

/** UI bridge to the MediaSessionService player. */
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
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors = _errors

    private var controller: MediaController? = null
    private var connecting = false
    private var tickerStarted = false
    private var lastLoggedId: String? = null
    private var queue: List<QueueItem> = emptyList()
    private var saveParametersJob: Job? = null
    private val pendingActions = mutableListOf<(MediaController) -> Unit>()

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            lastLoggedId = null
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                rebuildQueue()
                persistQueue()
                savePlaybackPosition()
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

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Ошибка воспроизведения", error)
            _errors.tryEmit("Не удалось воспроизвести трек: ${error.cause?.message ?: error.errorCodeName}")
        }
    }

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        scope.launch {
            try {
                val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                val connected = MediaController.Builder(context, token)
                    .setListener(object : MediaController.Listener {
                        override fun onDisconnected(controller: MediaController) {
                            this@PlayerController.controller = null
                            _state.value = PlayerState()
                            _position.value = 0L
                        }
                    })
                    .buildAsync()
                    .await()
                controller = connected
                connected.shuffleModeEnabled = settings.shuffle.first()
                connected.repeatMode = settings.repeatMode.first()
                val speed = settings.speed.first().coerceIn(0.5f, 2f)
                val pitch = settings.pitch.first().coerceIn(0.5f, 2f)
                connected.setPlaybackParameters(PlaybackParameters(speed, pitch))
                restoreQueue(connected)
                connected.addListener(listener)
                rebuildQueue()
                refreshState()
                val waiting = pendingActions.toList()
                pendingActions.clear()
                waiting.forEach { action -> action(connected) }
                startTicker()
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось подключиться к PlaybackService", e)
                _errors.tryEmit("Не удалось запустить аудиоплеер. Перезапусти приложение.")
            } finally {
                connecting = false
            }
        }
    }

    private fun withController(action: (MediaController) -> Unit) {
        val current = controller
        if (current != null && !connecting) action(current) else {
            pendingActions += action
            connect()
        }
    }

    // Local playback
    fun playTracks(tracks: List<TrackEntity>, startIndex: Int, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        withController { c ->
            val index = if (shuffle) tracks.indices.random() else startIndex.coerceIn(0, tracks.lastIndex)
            lastLoggedId = null
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(tracks.map(::toMediaItem), index, 0L)
            c.prepare()
            c.play()
            scope.launch {
                settings.setShuffle(shuffle)
                settings.setPlayback(index, 0L)
            }
        }
    }

    // Transport controls
    fun playQueueIndex(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) {
            lastLoggedId = null
            c.seekToDefaultPosition(index)
            c.play()
        }
    }

    fun pause() = withController { it.pause() }

    fun stop() = withController { player ->
        savePlaybackPosition()
        player.pause()
        player.stop()
    }

    fun togglePlayPause() = withController { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }

    fun seekTo(positionMs: Long) {
        withController { c ->
            c.seekTo(positionMs.coerceAtLeast(0L))
            _position.value = positionMs.coerceAtLeast(0L)
        }
    }

    fun toggleShuffle() = withController { c ->
        val value = !c.shuffleModeEnabled
        c.shuffleModeEnabled = value
        scope.launch { settings.setShuffle(value) }
        refreshState()
    }

    fun cycleRepeat() = withController { c ->
        val nextMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        c.repeatMode = nextMode
        scope.launch { settings.setRepeatMode(nextMode) }
        refreshState()
    }

    fun setSpeed(value: Float) = setPlaybackParameters(value, _state.value.pitch)
    fun setPitch(value: Float) = setPlaybackParameters(_state.value.speed, value)
    fun resetSpeedLab() = setPlaybackParameters(1f, 1f)
    fun setSpeedAndPitch(speed: Float, pitch: Float) = setPlaybackParameters(speed, pitch)

    private fun setPlaybackParameters(speed: Float, pitch: Float) {
        val safeSpeed = speed.coerceIn(0.5f, 2f)
        val safePitch = pitch.coerceIn(0.5f, 2f)
        withController { c ->
            c.setPlaybackParameters(PlaybackParameters(safeSpeed, safePitch))
            saveParametersJob?.cancel()
            saveParametersJob = scope.launch {
                delay(300)
                settings.setPlaybackParameters(safeSpeed, safePitch)
            }
            refreshState()
        }
    }

    fun enqueue(track: TrackEntity) = withController { c ->
        val wasEmpty = c.mediaItemCount == 0
        c.addMediaItem(toMediaItem(track))
        if (wasEmpty) {
            c.prepare()
            c.play()
        }
    }

    fun playNext(track: TrackEntity) = withController { c ->
        if (c.mediaItemCount == 0) {
            c.setMediaItem(toMediaItem(track))
            c.prepare()
            c.play()
        } else {
            val insertAt = (c.currentMediaItemIndex + 1).coerceIn(0, c.mediaItemCount)
            c.addMediaItem(insertAt, toMediaItem(track))
        }
    }

    fun moveQueueItem(from: Int, to: Int) = withController { controller ->
        if (from != to && from in 0 until controller.mediaItemCount && to in 0 until controller.mediaItemCount) {
            controller.moveMediaItem(from, to)
        }
    }

    fun removeFromQueue(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun removeTracks(ids: Set<Long>) = withController { c ->
        for (i in c.mediaItemCount - 1 downTo 0) {
            val id = c.getMediaItemAt(i).mediaId.toLongOrNull()
            if (id != null && id in ids) c.removeMediaItem(i)
        }
    }

    // Internal helpers
    private fun toMediaItem(track: TrackEntity): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        track.artworkUri?.let { metadata.setArtworkUri(Uri.parse(it)) }

        val mediaUri = if (track.source in setOf("spotify", "youtube")) {
            val scheme = if (track.source == "spotify") "auvyx-spotify" else "auvyx-yt"
            Uri.Builder().scheme(scheme).authority("track").appendPath(track.sourceId)
                .appendQueryParameter("duration", (if (track.source == "spotify" && track.uri.startsWith("https:")) 0L else track.durationMs).toString()).build()
        } else if (track.source == "soundcloud") {
            Uri.Builder()
                .scheme("auvyx-sc")
                .authority("track")
                .appendQueryParameter("url", track.sourceUrl ?: track.uri)
                .build()
        } else {
            Uri.parse(track.uri)
        }

        val builder = MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(mediaUri)
            .setMediaMetadata(metadata.build())
        if (track.source == "soundcloud") {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        return builder.build()
    }

    fun loadImportedQueue() = withController { c ->
        scope.launch {
            if (c.mediaItemCount == 0) restoreQueue(c)
        }
    }

    private suspend fun restoreQueue(c: MediaController) {
        if (c.mediaItemCount > 0) return
        val saved = db.queueDao().getAll()
        if (saved.isEmpty()) return
        val byId = saved.map { it.trackId }.distinct().chunked(500).flatMap { db.trackDao().getByIds(it) }.associateBy { it.id }
        val ordered = saved.mapNotNull { byId[it.trackId] }
        if (ordered.isEmpty()) {
            db.queueDao().clear()
            return
        }
        val index = settings.queueIndex.first().coerceIn(0, ordered.lastIndex)
        val position = settings.queuePosition.first().coerceAtLeast(0L)
        c.shuffleModeEnabled = settings.shuffle.first()
        c.repeatMode = settings.repeatMode.first()
        if (c.mediaItemCount != 0) return
        c.setMediaItems(ordered.map(::toMediaItem), index, position)
        c.prepare()
        if (ordered.size != saved.size) {
            val rows = ordered.mapIndexed { i, track -> QueueEntity(position = i, trackId = track.id) }
            db.queueDao().replace(rows)
        }
    }

    private fun rebuildQueue() {
        val c = controller ?: return
        queue = (0 until c.mediaItemCount).map { i ->
            val item = c.getMediaItemAt(i)
            QueueItem(
                mediaId = item.mediaId,
                title = item.mediaMetadata.title?.toString() ?: "Без названия",
                artist = item.mediaMetadata.artist?.toString() ?: "Неизвестный исполнитель"
            )
        }
    }

    private fun persistQueue() {
        if (queue.any { it.mediaId.toLongOrNull() == null }) return
        val rows = queue.mapNotNull { it.mediaId.toLongOrNull() }
            .mapIndexed { index, id -> QueueEntity(position = index, trackId = id) }
        scope.launch {
            runCatching { db.queueDao().replace(rows) }
                .onFailure { Log.w(TAG, "Не удалось сохранить очередь", it) }
        }
    }

    private fun refreshState() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val duration = c.duration
        val params = c.playbackParameters
        _state.value = PlayerState(
            mediaId = item?.mediaId,
            title = item?.mediaMetadata?.title?.toString() ?: "",
            artist = item?.mediaMetadata?.artist?.toString() ?: "",
            album = item?.mediaMetadata?.albumTitle?.toString() ?: "",
            artworkUri = item?.mediaMetadata?.artworkUri?.toString(),
            audioProvider = item?.mediaMetadata?.subtitle?.toString(),
            sourceUri = item?.localConfiguration?.uri?.toString(),
            isPlaying = c.isPlaying,
            canSeek = c.playbackState == Player.STATE_READY && c.isCurrentMediaItemSeekable && c.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
            durationMs = if (duration == C.TIME_UNSET) 0L else duration.coerceAtLeast(0L),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queue = queue,
            currentIndex = c.currentMediaItemIndex.coerceAtLeast(0),
            speed = params.speed,
            pitch = params.pitch
        )
        _position.value = c.currentPosition.coerceAtLeast(0L)
    }

    private fun logHistory() {
        val c = controller ?: return
        val item = c.currentMediaItem ?: return
        val id = item.mediaId
        if (id == lastLoggedId) return
        lastLoggedId = id
        val trackId = id.toLongOrNull() ?: return
        scope.launch {
            runCatching {
                db.historyDao().insert(HistoryEntity(trackId = trackId, playedAt = System.currentTimeMillis()))
            }.onFailure { Log.w(TAG, "Не удалось записать историю", it) }
        }
    }

    private fun savePlaybackPosition() {
        val c = controller ?: return
        if (c.currentMediaItemIndex < 0) return
        if (c.currentMediaItem?.mediaId?.startsWith("soundcloud:") == true) return
        val index = c.currentMediaItemIndex
        val position = c.currentPosition.coerceAtLeast(0L)
        scope.launch { runCatching { settings.setPlayback(index, position) } }
    }

    private fun startTicker() {
        if (tickerStarted) return
        tickerStarted = true
        scope.launch {
            var tick = 0
            while (isActive) {
                delay(500)
                val c = controller
                if (c != null) _position.value = c.currentPosition.coerceAtLeast(0L)
                if (c != null && c.isPlaying) {
                    tick++
                    // A fallback guard for OEM/player event ordering: write the current
                    // track once after playback really starts, even if an event was missed.
                    if (tick % 2 == 0) logHistory()
                    if (tick % 10 == 0) savePlaybackPosition()
                }
            }
        }
    }

    private companion object {
        const val TAG = "AuvyxPlayer"
    }
}
