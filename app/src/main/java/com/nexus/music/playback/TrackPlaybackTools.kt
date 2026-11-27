package com.nexus.music.playback

import android.os.SystemClock
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

internal const val MIN_TRACK_GAIN = -24f
internal fun attenuation(decibels: Float): Float =
    10f.pow(decibels.takeIf { it.isFinite() }?.coerceIn(MIN_TRACK_GAIN, 0f)?.div(20f) ?: 0f)

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
    private val clearMoment: () -> Unit
) : Player.Listener {
    var ranges: List<SkipRange> = emptyList()
    private var job: Job? = null
    private var guardedRange: LongRange? = null
    private var manualRangeUntil = 0L
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

    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
        guardedRange = null
        manualRangeUntil = 0L
        autoTarget = null
        if (moment.value?.trackId?.toString() != mediaItem?.mediaId) clearMoment()
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
    ) {
        if (reason != Player.DISCONTINUITY_REASON_SEEK || commandedSeek || autoTarget == newPosition.positionMs) {
            autoTarget = null
            return
        }
        guardedRange = null
        manualRangeUntil = SystemClock.elapsedRealtime() + 1250
        val active = moment.value ?: return
        val end = active.endMs ?: return
        if (newPosition.positionMs !in active.startMs until end) clearMoment()
    }

    private fun update() {
        if (!player.isPlaying || !player.isCurrentMediaItemSeekable || player.duration <= 0) return
        val position = player.currentPosition
        val active = moment.value?.takeIf { it.trackId.toString() == player.currentMediaItem?.mediaId }
        if (active != null && active.endMs != null) {
            if (active.endMs > player.duration) clearMoment()
            else if (position >= active.endMs) seek(active.startMs)
            return
        }
        val range = mergedRanges(ranges, player.duration).firstOrNull { position in it }
        if (range == null) guardedRange = null
        else if (guardedRange != range && SystemClock.elapsedRealtime() >= manualRangeUntil) {
            guardedRange = range
            seek(range.last + 1)
        }
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
