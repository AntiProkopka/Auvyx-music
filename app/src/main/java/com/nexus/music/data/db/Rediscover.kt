package com.nexus.music.data.db

import androidx.room.Embedded
import java.util.Locale

internal const val REDISCOVER_DAYS = 30L
internal const val REDISCOVER_MIN_STARTS = 3L
internal const val DAY_MILLIS = 86_400_000L

data class ForgottenTrack(
    @Embedded val track: TrackEntity,
    val starts: Long,
    val lastPlayed: Long
)

/** Interleave artists without losing the most relevant result from each artist. */
internal fun diversifyForgotten(entries: List<ForgottenTrack>, limit: Int): List<ForgottenTrack> {
    val artists = entries.groupBy {
        val track = it.track
        track.artistId?.let { id -> "${track.source}:$id" }
            ?: track.artist.trim().lowercase(Locale.ROOT).ifEmpty { "track:${track.id}" }
    }.values.map { ArrayDeque(it) }
    val selected = ArrayList<ForgottenTrack>(limit)
    while (selected.size < limit) {
        var added = false
        for (artist in artists) {
            val track = artist.removeFirstOrNull() ?: continue
            selected += track
            added = true
            if (selected.size == limit) break
        }
        if (!added) break
    }
    return selected
}
