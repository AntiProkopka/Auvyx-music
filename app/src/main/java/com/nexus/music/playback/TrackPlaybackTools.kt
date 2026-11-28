package com.nexus.music.playback

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

internal class TrackPlaybackTools(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private val moment: StateFlow<TrackMoment?>,
    private val preview: kotlinx.coroutines.flow.MutableStateFlow<TrackMoment?>,
    private val clearMoment: () -> Unit
) : Player.Listener {
    var ranges: List<SkipRange> = emptyList()
        set(value) {
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

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        job?.cancel()
        job = if (isPlaying) scope.launch {
            while (true) {
                update()
                delay(100)
            }
        } else null
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
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
        if (playbackState == Player.STATE_ENDED) preview.value = null
    }

    fun schedulePreviewEnd() {
        previewEnd?.cancel()
        previewEnd = null
        val clip = preview.value ?: return
        val end = clip.endMs ?: return
        if (clip.trackId.toString() != player.currentMediaItem?.mediaId || !player.isCurrentMediaItemSeekable ||
            player.duration <= 0) return
        val stopAt = minOf(end, player.duration - 1)
        previewEnd = player.createMessage { _, _ ->
            if (preview.value == clip && player.currentMediaItem?.mediaId == clip.trackId.toString()) {
                player.pause()
                preview.value = null
            }
        }.setLooper(player.applicationLooper).setPosition(player.currentMediaItemIndex, stopAt).send()
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
    ) {
        if (reason != Player.DISCONTINUITY_REASON_SEEK || commandedSeek || autoTarget == newPosition.positionMs) {
            autoTarget = null
            return
        }
        guardedRange = null
        manualRange = skipRanges().firstOrNull { newPosition.positionMs in it }
        val active = moment.value ?: return
        val end = active.endMs ?: return
        if (newPosition.positionMs !in active.startMs until end) clearMoment()
    }

    private fun update() {
        if (!player.isPlaying || !player.isCurrentMediaItemSeekable || player.duration <= 0) return
        val position = player.currentPosition
        if (preview.value?.trackId?.toString() == player.currentMediaItem?.mediaId && preview.value != null) return
        val active = moment.value?.takeIf { it.trackId.toString() == player.currentMediaItem?.mediaId }
        if (active != null && active.endMs != null) {
            if (active.endMs > player.duration) clearMoment()
            else if (position >= active.endMs) seek(active.startMs)
            return
        }
        val range = skipRanges().firstOrNull { position in it }
        if (range != manualRange) manualRange = null
        if (range == null) guardedRange = null
        else if (guardedRange != range && manualRange != range) {
            guardedRange = range
            seek(range.last + 1)
        }
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
