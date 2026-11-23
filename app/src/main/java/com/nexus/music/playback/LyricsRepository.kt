package com.nexus.music.playback

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs

/** Public catalogue lookup. No audio URLs or service access tokens are involved. */
object LyricsRepository {
    suspend fun find(title: String, artist: String, durationMs: Long): String? = withContext(Dispatchers.IO) {
        if (title.isBlank() || artist.isBlank()) return@withContext null
        val address = Uri.Builder()
            .scheme("https")
            .authority("lrclib.net")
            .appendPath("api")
            .appendPath("search")
            .appendQueryParameter("track_name", title.trim())
            .appendQueryParameter("artist_name", artist.trim())
            .build().toString()
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            connectTimeout = 7_000
            readTimeout = 9_000
            setRequestProperty("User-Agent", "AUVYX/2.0 (Android; lyrics lookup)")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode != 200) return@withContext null
            val response = connection.inputStream.bufferedReader().use { it.readText().take(400_000) }
            val candidates = JSONArray(response)
            val wantedTitle = normalized(title)
            val names = artist.split(Regex("(?i)\\s+(?:feat\\.?|ft\\.?|x|&|and)\\s+|,|/"))
                .map(::normalized).filter { it.isNotBlank() }
            val seconds = durationMs / 1000
            val matches = (0 until minOf(30, candidates.length())).mapNotNull { index ->
                candidates.optJSONObject(index)?.takeIf { item ->
                    normalized(item.optString("trackName")) == wantedTitle &&
                        names.any { name -> normalized(item.optString("artistName")).contains(name) } &&
                        (seconds <= 0 || abs(item.optLong("duration") - seconds) <= 6)
                }
            }
            val ordered = matches.filterNot { it.optBoolean("instrumental") }
                .sortedBy { if (seconds > 0) abs(it.optLong("duration") - seconds) else 0L }
            fun text(field: String) = ordered.firstNotNullOfOrNull { item ->
                item.optString(field).trim().takeIf { it.length in 1..65_536 && it != "null" }
            }
            text("syncedLyrics") ?: text("plainLyrics")
        } finally {
            connection.disconnect()
        }
    }

    private fun normalized(value: String) = value.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
