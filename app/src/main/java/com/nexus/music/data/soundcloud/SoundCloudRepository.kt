package com.nexus.music.data.soundcloud

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** A public SoundCloud catalogue item. It is not stored in the local Room library. */
data class SoundCloudTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val permalinkUrl: String
)

/**
 * Experimental SoundCloud client.
 *
 * This uses SoundCloud's public web-client endpoints, not its documented partner API.
 * The client id is read from the public SoundCloud web application at runtime, so no
 * secret or user credential is embedded in the APK. SoundCloud may change this interface.
 */
object SoundCloudRepository {
    private const val API = "https://api-v2.soundcloud.com"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val MAX_RESULTS = 30
    private const val CACHE_MS = 180_000L

    @Volatile private var cachedClientId: String? = null
    private val clientIdLock = Any()
    private val streamCache = ConcurrentHashMap<String, CachedStream>()

    private data class CachedStream(val url: String, val cachedAt: Long)

    suspend fun searchTracks(query: String): List<SoundCloudTrack> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        require(trimmed.isNotEmpty()) { "Введи название трека или исполнителя." }
        require(trimmed.length <= 160) { "Слишком длинный поисковый запрос." }

        val url = "$API/search/tracks?q=${encode(trimmed)}&client_id=${clientId()}&limit=$MAX_RESULTS&linked_partitioning=1"
        val root = getJson(url)
        val collection = root.optJSONArray("collection") ?: JSONArray()
        buildList {
            for (index in 0 until collection.length()) {
                val item = collection.optJSONObject(index) ?: continue
                val id = item.optLong("id", -1L)
                val title = item.optString("title").trim()
                val permalink = item.optString("permalink_url").trim()
                if (id <= 0L || title.isBlank() || !isTrustedTrackUrl(permalink)) continue

                val user = item.optJSONObject("user")
                val artist = user?.optString("username")?.takeIf { !it.isNullOrBlank() }
                    ?: "Неизвестный исполнитель"
                val artwork = item.optString("artwork_url")
                    .takeIf { it.startsWith("https://", ignoreCase = true) }
                    ?: user?.optString("avatar_url")?.takeIf { it.startsWith("https://", ignoreCase = true) }

                add(
                    SoundCloudTrack(
                        id = id,
                        title = title,
                        artist = artist,
                        durationMs = item.optLong("duration", 0L).coerceAtLeast(0L),
                        artworkUrl = artwork,
                        permalinkUrl = permalink
                    )
                )
            }
        }
    }

    /** Called by Media3's ResolvingDataSource on its loader thread. */
    fun resolvePlaybackUri(uri: Uri): Uri {
        if (uri.scheme != "auvyx-sc" || uri.host != "track") return uri
        val permalink = uri.getQueryParameter("url")
            ?: throw IOException("В ссылке SoundCloud отсутствует адрес трека.")
        return Uri.parse(resolveStreamUrlBlocking(permalink))
    }

    private fun resolveStreamUrlBlocking(permalinkUrl: String): String {
        if (!isTrustedTrackUrl(permalinkUrl)) {
            throw IOException("Недопустимая ссылка SoundCloud.")
        }
        val cached = streamCache[permalinkUrl]
        if (cached != null && System.currentTimeMillis() - cached.cachedAt < CACHE_MS) {
            return cached.url
        }

        val key = clientId()
        val resolvedUrl = "$API/resolve?url=${encode(permalinkUrl)}&client_id=$key"
        val track = getJson(resolvedUrl)
        val media = track.optJSONObject("media")
            ?: throw IOException("SoundCloud не предоставил поток для этого трека.")
        val transcodings = media.optJSONArray("transcodings") ?: JSONArray()
        val authorization = track.optString("track_authorization").takeIf { it.isNotBlank() }

        val candidates = (0 until transcodings.length()).mapNotNull { transcodings.optJSONObject(it) }
            .filter { transcoding ->
                val format = transcoding.optJSONObject("format")
                format?.optString("protocol") == "hls" &&
                    isTrustedApiUrl(transcoding.optString("url"))
            }
            .sortedBy { transcoding ->
                when (transcoding.optString("preset")) {
                    "aac_160k" -> 0
                    "aac_96k" -> 1
                    "mp3_0_0" -> 2
                    else -> 3
                }
            }

        var lastError: Throwable? = null
        for (candidate in candidates) {
            try {
                val endpoint = candidate.optString("url")
                val streamEndpoint = buildString {
                    append(endpoint)
                    append(if (endpoint.contains('?')) '&' else '?')
                    append("client_id=").append(key)
                    if (!authorization.isNullOrBlank()) {
                        append("&track_authorization=").append(encode(authorization))
                    }
                }
                val stream = getJson(streamEndpoint)
                val streamUrl = stream.optString("url")
                if (!isTrustedMediaUrl(streamUrl)) {
                    throw IOException("SoundCloud вернул неподдерживаемый адрес потока.")
                }
                streamCache[permalinkUrl] = CachedStream(streamUrl, System.currentTimeMillis())
                if (streamCache.size > 100) streamCache.clear()
                return streamUrl
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw IOException(
            "Для этого трека SoundCloud не нашёл доступный публичный аудиопоток.",
            lastError
        )
    }

    private fun clientId(): String {
        cachedClientId?.let { return it }
        synchronized(clientIdLock) {
            cachedClientId?.let { return it }
            val homepage = readUrl("https://m.soundcloud.com/")
            extractClientId(homepage)?.let {
                cachedClientId = it
                return it
            }

            val scriptPattern = Regex("""<script[^>]+src=[\"']([^\"']+)[\"']""", RegexOption.IGNORE_CASE)
            val scripts = scriptPattern.findAll(homepage)
                .mapNotNull { it.groupValues.getOrNull(1) }
                .map { source ->
                    when {
                        source.startsWith("//") -> "https:$source"
                        source.startsWith("/") -> "https://soundcloud.com$source"
                        else -> source
                    }
                }
                .filter { it.contains("sndcdn.com") && it.endsWith(".js") }
                .toList()
                .takeLast(12)
                .asReversed()

            for (scriptUrl in scripts) {
                val script = runCatching { readUrl(scriptUrl, rangeHeader = "bytes=0-160000") }.getOrNull()
                    ?: continue
                extractClientId(script)?.let {
                    cachedClientId = it
                    return it
                }
            }
            throw IOException("Не удалось получить публичный ключ SoundCloud. Проверь подключение к сервису.")
        }
    }

    private fun extractClientId(text: String): String? {
        val patterns = listOf(
            Regex("""["']clientId["']\s*:\s*["']([A-Za-z0-9_-]{20,})["']"""),
            Regex("""client_id\s*[:=]\s*["']([A-Za-z0-9_-]{20,})["']"""),
            Regex("""client_id%3D([A-Za-z0-9_-]{20,})"""),
            Regex("""client_id=([A-Za-z0-9_-]{20,})""")
        )
        return patterns.firstNotNullOfOrNull { pattern -> pattern.find(text)?.groupValues?.getOrNull(1) }
    }

    private fun getJson(url: String): JSONObject {
        val body = readUrl(url)
        return try {
            JSONObject(body)
        } catch (error: Exception) {
            throw IOException("SoundCloud вернул некорректный ответ.", error)
        }
    }

    private fun readUrl(url: String, rangeHeader: String? = null): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json, text/html, */*")
            setRequestProperty("Origin", "https://soundcloud.com")
            if (rangeHeader != null) setRequestProperty("Range", rangeHeader)
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                if (status == 401 || status == 403) cachedClientId = null
                throw IOException("SoundCloud ответил HTTP $status.")
            }
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun isTrustedTrackUrl(raw: String): Boolean {
        val uri = Uri.parse(raw)
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme.equals("https", true) &&
            host in setOf("soundcloud.com", "www.soundcloud.com", "m.soundcloud.com")
    }

    private fun isTrustedApiUrl(raw: String): Boolean {
        val uri = Uri.parse(raw)
        return uri.scheme.equals("https", true) && uri.host.equals("api-v2.soundcloud.com", true)
    }

    private fun isTrustedMediaUrl(raw: String): Boolean {
        val uri = Uri.parse(raw)
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme.equals("https", true) &&
            (host.endsWith(".sndcdn.com") || host == "sndcdn.com" ||
                host.endsWith(".soundcloud.cloud") || host == "soundcloud.cloud")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
