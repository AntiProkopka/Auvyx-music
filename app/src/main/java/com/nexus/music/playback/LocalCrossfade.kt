package com.nexus.music.playback

import android.os.Handler
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.sqrt

/** Overlaps seekable local files and on-demand SoundCloud streams. The session player remains the owner of the timeline. */
@UnstableApi
internal class LocalCrossfade(private val primary: ExoPlayer, private val makeOverlay: () -> ExoPlayer) : Player.Listener {
    var seconds = 0
        set(value) {
            field = value
            schedule()
        }
    private val handler = Handler(primary.applicationLooper)
    private var overlay: ExoPlayer? = null
    private var incoming: MediaItem? = null
    private var failedIndex = -1
    private var windowMs = 0L
    private val tick = object : Runnable {
        override fun run() {
            update()
            if (seconds > 0 && primary.isPlaying) handler.postDelayed(this, if (overlay == null) 180 else 35)
        }
    }

    init { primary.addListener(this) }

    private fun schedule() {
        handler.removeCallbacks(tick)
        if (seconds > 0 && primary.isPlaying) handler.post(tick) else reset()
    }

    private fun update() {
        val index = primary.nextMediaItemIndex
        if (seconds == 0 || !primary.isPlaying || index < 0 || index == primary.currentMediaItemIndex ||
            primary.repeatMode == Player.REPEAT_MODE_ONE || primary.duration <= 0 ||
            primary.isCurrentMediaItemLive || !primary.isCurrentMediaItemSeekable || !canOverlap(primary.currentMediaItem)) {
            reset()
            return
        }
        val next = primary.getMediaItemAt(index)
        val remaining = primary.duration - primary.currentPosition
        val requested = minOf(seconds * 1000L, primary.duration / 2)
        val lead = if (next.localConfiguration?.uri?.scheme == "auvyx-sc") 6000L else 1500L
        if (!canOverlap(next) || remaining > requested + lead ||
            (incoming != null && incoming != next)) { reset(); return }
        if (failedIndex == primary.currentMediaItemIndex) return
        val player = overlay ?: makeOverlay().also {
            overlay = it
            incoming = next
            it.volume = 0f
            it.setMediaItem(next)
            it.prepare()
        }
        if (player.playerError != null || player.playbackState == Player.STATE_ENDED) {
            failedIndex = primary.currentMediaItemIndex
            reset()
            return
        }
        if (player.playbackParameters != primary.playbackParameters) player.playbackParameters = primary.playbackParameters
        val allowed = minOf(requested, player.duration.coerceAtLeast(0) / 2)
        if (player.playWhenReady && player.playbackState != Player.STATE_READY) {
            failedIndex = primary.currentMediaItemIndex
            reset()
            return
        }
        if (player.playbackState != Player.STATE_READY || player.isCurrentMediaItemLive ||
            !player.isCurrentMediaItemSeekable || remaining > allowed || allowed < 100
        ) return
        if (!player.playWhenReady) {
            windowMs = remaining.coerceAtLeast(1)
            player.play()
        }
        val fraction = (1f - remaining.toFloat() / windowMs.coerceAtLeast(1)).coerceIn(0f, 1f)
        primary.volume = sqrt(1f - fraction)
        player.volume = sqrt(fraction)
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        val elapsed = overlay?.takeIf { it.isPlaying }?.currentPosition ?: 0
        val handoff = reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION &&
            incoming != null && newPosition.mediaItem == incoming && elapsed > 0
        failedIndex = -1
        reset()
        if (handoff) primary.seekTo(newPosition.mediaItemIndex, elapsed)
    }
    private fun canOverlap(item: MediaItem?): Boolean {
        return when (item?.localConfiguration?.uri?.scheme) {
            "content", "file", "auvyx-sc" -> true
            else -> false
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) { schedule() }
    private fun reset() {
        overlay?.let { PlaybackAudioRouting.clearOverlay(); it.release(); primary.volume = 1f }
        overlay = null
        incoming = null
    }
    fun release() { handler.removeCallbacks(tick); primary.removeListener(this); reset() }
}
