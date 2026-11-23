package com.nexus.music.data.catalog

import androidx.media3.common.MediaItem
import com.nexus.music.data.db.TrackEntity
import java.io.IOException

internal object NativeCatalog {
    fun youtube(entry: CatalogEntry): TrackEntity {
        val id = entry.url.substringAfterLast('/')
        val parts = entry.details.split('•').map { it.trim() }
        val duration = parts.lastOrNull().orEmpty().split(':')
        val seconds = if (duration.size in 2..3 && duration.all { it.toLongOrNull() != null })
            duration.fold(0L) { sum, part -> sum * 60 + part.toLong() } else 0L
        return TrackEntity(uri = "auvyx-yt://track/$id", title = entry.title, artist = parts.firstOrNull().orEmpty(),
            album = parts.drop(1).firstOrNull { !it.matches(Regex("[0-9:]+")) }.orEmpty(), durationMs = seconds * 1000,
            addedAt = System.currentTimeMillis(), artworkUri = entry.artwork, source = "youtube", sourceId = id,
            sourceUrl = "https://music.youtube.com/watch?v=$id")
    }

    fun resolve(item: MediaItem): MediaItem {
        val spotify = item.localConfiguration?.uri?.scheme == "auvyx-spotify"
        val provider = if (spotify) "Spotify" else "YouTube Music"
        throw IOException("Этот трек из старой очереди. Открой его через вкладку $provider.")
    }
}
