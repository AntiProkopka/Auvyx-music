package com.nexus.music.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.nexus.music.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Counts natural item completions on the same looper as the session player. */
internal class FiniteRepeatSession(private val settings: SettingsStore, private val scope: CoroutineScope) : Player.Listener {
    private val active = MutableStateFlow<FiniteRepeat?>(null)
    val state = active.asStateFlow()
    private var player: Player? = null
    private var saving: Job? = null
    private var pendingRestore: FiniteRepeat? = null

    fun attach(player: Player) {
        this.player = player
        player.addListener(this)
    }

    fun detach() {
        player?.removeListener(this)
        player = null
        active.value = null
        pendingRestore = null
    }

    fun restore(saved: FiniteRepeat) {
        pendingRestore = saved
        restoreIfReady()
    }

    private fun queue(player: Player) = List(player.mediaItemCount) { player.getMediaItemAt(it).mediaId }

    private fun restoreIfReady() {
        val saved = pendingRestore ?: return
        val player = player ?: return
        if (player.mediaItemCount == 0) return
        pendingRestore = null
        if (saved.queue == queue(player) && saved.index == player.currentMediaItemIndex) change(saved)
        else persist(null, null)
    }

    fun start(total: Int) {
        val player = player ?: return
        if (total !in 2..99 || player.currentMediaItem == null || player.isCurrentMediaItemLive) return
        pendingRestore = null
        val base = active.value?.restoreMode ?: player.repeatMode
        val restore = if (base == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else base
        change(FiniteRepeat(queue(player), player.currentMediaItemIndex, total, total, restore))
    }

    fun cancel() {
        pendingRestore = null
        if (active.value != null) change(null)
    }

    override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
        val current = active.value ?: return
        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION && old.mediaItemIndex == new.mediaItemIndex &&
            old.mediaItemIndex == current.index && current.remaining > 1) {
            change(current.afterRepeat())
        } else if (old.mediaItemIndex != new.mediaItemIndex) cancel()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT && active.value != null) cancel()
    }

    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
        val player = player ?: return
        if (active.value?.queue?.let { it != queue(player) } == true) cancel()
        restoreIfReady()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) cancel()
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        val current = active.value ?: return
        if (repeatMode != current.playerMode) {
            active.value = null
            persist(null, repeatMode)
        }
    }

    private fun change(next: FiniteRepeat?) {
        val base = next?.restoreMode ?: active.value?.restoreMode ?: return
        active.value = next
        player?.repeatMode = next?.playerMode ?: base
        persist(next, base)
    }

    private fun persist(next: FiniteRepeat?, base: Int?) {
        saving?.cancel()
        saving = scope.launch { settings.saveFiniteRepeat(next?.encode(), base) }
    }
}
