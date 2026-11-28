package com.nexus.music.playback

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.nexus.music.data.db.SkipRange
import com.nexus.music.data.db.TrackMoment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.ceil

internal const val MIN_TRACK_GAIN = -12f
internal const val MAX_TRACK_GAIN = 6f
internal const val LEGACY_MIN_TRACK_GAIN = -24f
internal fun trackBoost(decibels: Float): Float =
    10f.pow(decibels.takeIf { it.isFinite() }?.coerceIn(0f, MAX_TRACK_GAIN)?.div(20f) ?: 0f)
internal fun attenuation(decibels: Float): Float =
    10f.pow(decibels.takeIf { it.isFinite() }?.coerceIn(LEGACY_MIN_TRACK_GAIN, 0f)?.div(20f) ?: 0f)

internal fun mergedRanges(rows: List<SkipRange>, duration: Long): List<LongRange> {
    if (duration <= 0) return emptyList()
    val merged = mutableListOf<LongRange>()
    for (row in rows.sortedBy { it.startMs }) {
        if (!row.enabled || row.startMs < 0 || row.endMs <= row.startMs || row.endMs > duration) continue
        val previous = merged.lastOrNull()
        if (previous != null && row.startMs <= previous.last + 1) {
            merged[merged.lastIndex] = previous.first..maxOf(previous.last, row.endMs - 1)
        } else {
            merged += row.startMs until row.endMs
        }
    }
    // A corrupt or imported whole-track selection must never consume the queue.
    return merged.takeUnless { it.singleOrNull() == (0L until duration) }.orEmpty()
}

private const val SKIP_FADE_MS = 48L

internal fun skipFadeProgress(elapsed: Long): Float {
    val fraction = (elapsed.toFloat() / SKIP_FADE_MS).coerceIn(0f, 1f)
    return fraction * fraction * (3f - 2f * fraction)
}

internal class TrackPlaybackTools(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private val moment: StateFlow<TrackMoment?>,
    private val preview: kotlinx.coroutines.flow.MutableStateFlow<TrackMoment?>,
    private val setSkipGain: (Float) -> Unit,
    private val clearMoment: () -> Unit
) : Player.Listener {
    var ranges: List<SkipRange> = emptyList()
        set(value) {
            if (field != value) resetFade()
            field = value
            normalizedDuration = 0L
        }
    private var previewEnd: androidx.media3.exoplayer.PlayerMessage? = null
    private var job: Job? = null
    private var guardedRange: LongRange? = null
    private var manualRange: LongRange? = null
    private var normalizedDuration = 0L
    private var normalizedRanges = emptyList<LongRange>()
    private var commandedSeek = false
    private var autoTarget: Long? = null
    private var fadeOutAt: Long? = null
    private var fadeInAt: Long? = null
    private var recovering = false
    private var lastSkipGain = 1f

    private fun resetFade() {
        fadeOutAt = null
        fadeInAt = null
        recovering = false
        applyGain(1f)
    }

    private fun applyGain(gain: Float) {
        if (gain == lastSkipGain) return
        lastSkipGain = gain
        setSkipGain(gain)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady) resetFade()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        job?.cancel()
        job = if (isPlaying) scope.launch {
            while (true) {
                delay(1)
                delay(update())
            }
        } else null
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        resetFade()
        guardedRange = null
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) manualRange = null
        normalizedDuration = 0L
        autoTarget = null
        if (moment.value?.trackId?.toString() != mediaItem?.mediaId) clearMoment()
        val clip = preview.value
        if (clip != null && clip.trackId.toString() != mediaItem?.mediaId) preview.value = null
        schedulePreviewEnd()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) schedulePreviewEnd()
        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) resetFade()
        if (playbackState == Player.STATE_ENDED) preview.value = null
    }

    fun schedulePreviewEnd() {
        previewEnd?.cancel()
        previewEnd = null
        val clip = preview.value
        if (clip != null) resetFade()
        player.setPauseAtEndOfMediaItems(clip != null)
        val end = clip?.endMs ?: return
        if (clip.trackId.toString() != player.currentMediaItem?.mediaId || !player.isCurrentMediaItemSeekable ||
            player.duration <= 0) return
        val stopAt = minOf(end, player.duration - 1)
        previewEnd = player.createMessage { _, _ ->
            if (preview.value == clip && player.currentMediaItem?.mediaId == clip.trackId.toString()) finishPreview(end)
        }.setLooper(player.applicationLooper).setPosition(player.currentMediaItemIndex, stopAt).send()
    }

    private fun finishPreview(end: Long) {
        player.pause()
        seek(end.coerceAtMost(player.duration))
        preview.value = null
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
    ) {
        if (reason != Player.DISCONTINUITY_REASON_SEEK || commandedSeek || autoTarget == newPosition.positionMs) {
            autoTarget = null
            return
        }
        resetFade()
        guardedRange = null
        manualRange = skipRanges().firstOrNull { newPosition.positionMs in it }
        val active = moment.value ?: return
        val end = active.endMs ?: return
        if (newPosition.positionMs !in active.startMs until end) clearMoment()
    }

    private fun update(): Long {
        if (!player.isPlaying || !player.isCurrentMediaItemSeekable || player.duration <= 0) return 100L
        val position = player.currentPosition
        val clip = preview.value
        if (clip != null && clip.trackId.toString() == player.currentMediaItem?.mediaId) {
            val end = clip.endMs
            if (end != null && position >= end.coerceAtMost(player.duration - 1)) finishPreview(end)
            return 30L
        }
        val active = moment.value?.takeIf { it.trackId.toString() == player.currentMediaItem?.mediaId }
        if (active?.endMs != null) {
            resetFade()
            if (active.endMs > player.duration) clearMoment()
            else if (position >= active.endMs) seek(active.startMs)
            return 50L
        }

        val now = SystemClock.elapsedRealtime()
        if (recovering) {
            val started = fadeInAt ?: now.also { fadeInAt = it }
            val gain = skipFadeProgress(now - started)
            applyGain(gain)
            if (gain < 1f) return 8L
            resetFade()
        }

        if (manualRange?.contains(position) != true) manualRange = null
        if (guardedRange?.contains(position) != true) guardedRange = null
        val range = skipRanges().firstOrNull { it.last >= position && it != manualRange && it != guardedRange }
        if (range == null) {
            resetFade()
            return 100L
        }

        val untilStart = ceil((range.first - position).toDouble() / player.playbackParameters.speed).toLong()
        if (untilStart > SKIP_FADE_MS) {
            resetFade()
            return (untilStart - SKIP_FADE_MS).coerceIn(1L, 100L)
        }

        // Anchor the envelope to the measured approach. When starting inside a skip,
        // the listener still gets a short fade before the discontinuity.
        val started = fadeOutAt ?: now.also { fadeOutAt = it }
        val gain = 1f - skipFadeProgress(now - started)
        applyGain(gain)
        if (gain == 0f && position >= range.first) {
            guardedRange = range
            fadeOutAt = null
            recovering = true
            seek(range.last + 1)
        }
        return 8L
    }

    private fun skipRanges(): List<LongRange> {
        val duration = player.duration
        if (duration <= 0) return emptyList()
        if (duration != normalizedDuration) {
            val trackId = player.currentMediaItem?.mediaId?.toLongOrNull()
            normalizedRanges = mergedRanges(ranges.filter { it.trackId == trackId }, duration)
            normalizedDuration = duration
        }
        return normalizedRanges
    }

    private fun seek(position: Long) {
        commandedSeek = true
        autoTarget = position
        try {
            player.seekTo(position)
        } finally {
            commandedSeek = false
        }
    }
}
