package com.nexus.music.playback

import android.os.Handler
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/** Overlaps seekable local files and on-demand SoundCloud streams. The session player remains the owner of the timeline. */
@UnstableApi
internal class LocalCrossfade(private val primary: ExoPlayer, private val makeOverlay: (MediaItem) -> ExoPlayer) : Player.Listener {
    private var attenuationById = emptyMap<String, Float>()
    var canMixTrack: (String) -> Boolean = { true }

    fun setAttenuation(gains: Map<String, Float>) {
        attenuationById = gains
        if (overlay == null) primary.volume = gain(primary.currentMediaItem)
        else update()
    }

    private fun gain(item: MediaItem?): Float = attenuationById[item?.mediaId] ?: 1f

    var seconds = 0
        set(value) {
            val duration = value.coerceIn(0, 12)
            if (field == duration) return
            field = duration
            reset()
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
            primary.isCurrentMediaItemLive || !primary.isCurrentMediaItemSeekable || !canOverlap(primary.currentMediaItem) ||
            !canMixTrack(primary.currentMediaItem?.mediaId.orEmpty())) {
            reset()
            return
        }
        val next = primary.getMediaItemAt(index)
        val remaining = primary.duration - primary.currentPosition
        val requested = minOf((seconds * 1000L * primary.playbackParameters.speed).toLong(), primary.duration / 2)
        val lead = if (next.localConfiguration?.uri?.scheme == "auvyx-sc") 6000L else 1500L
        if (!canOverlap(next) || !canMixTrack(next.mediaId) || remaining > requested + lead ||
            (incoming != null && incoming != next)) { reset(); return }
        if (failedIndex == primary.currentMediaItemIndex) return
        val player = overlay ?: makeOverlay(next).also {
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
        // Keep the sum at unity so coincident peaks from the two audio tracks cannot overload the mix.
        primary.volume = gain(primary.currentMediaItem) * (1f - fraction)
        player.volume = gain(next) * fraction
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

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (overlay == null) primary.volume = gain(mediaItem)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) { schedule() }
    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) schedule()
    }

    private fun reset() {
        val previousOverlay = overlay
        overlay = null
        incoming = null
        if (previousOverlay != null) {
            PlaybackAudioRouting.clearOverlay()
            previousOverlay.release()
        }
        primary.volume = gain(primary.currentMediaItem)
    }
    fun release() { handler.removeCallbacks(tick); primary.removeListener(this); reset() }
}
