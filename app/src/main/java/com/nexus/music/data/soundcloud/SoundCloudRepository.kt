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
    val permalinkUrl: String,
    val credit: String = "",
    val full: Boolean = true,
    val artistId: Long? = null,
    val albumTitle: String? = null
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
        parseTracks(root)
    }

    suspend fun creditedArtist(permalink: String): String? = withContext(Dispatchers.IO) {
        if (!isTrustedTrackUrl(permalink)) return@withContext null
        val track = getJson("$API/resolve?url=${encode(permalink)}&client_id=${clientId()}")
        track.optJSONObject("publisher_metadata")?.label("artist")
    }

    data class Artist(val name: String, val avatar: String?, val description: String, val id: Long)
    data class ArtistPage(val tracks: List<SoundCloudTrack>, val next: String?)
    data class Album(
        val title: String,
        val artist: String,
        val artworkUrl: String?,
        val releaseDate: String?,
        val durationMs: Long,
        val trackCount: Int,
        val tracks: List<SoundCloudTrack>
    )

    suspend fun album(trackId: Long, permalink: String): Album? = withContext(Dispatchers.IO) {
        if (!isTrustedTrackUrl(permalink)) return@withContext null
        val key = clientId()
        val track = getJson("$API/resolve?url=${encode(permalink)}&client_id=$key")
        if (track.optLong("id") != trackId) return@withContext null
        val publisher = track.optJSONObject("publisher_metadata") ?: return@withContext null
        val title = publisher.albumTitle() ?: return@withContext null
        val artist = publisher.label("artist") ?: track.optJSONObject("user")?.label("username")
            ?: return@withContext null
        val owner = track.optJSONObject("user")?.optLong("id")?.takeIf { it > 0L } ?: return@withContext null
        val results = getJson("$API/search/playlists?q=${encode("$title $artist")}&client_id=$key&limit=25&linked_partitioning=1")
        for (candidate in (results.optJSONArray("collection") ?: JSONArray()).objects()) {
            if (!candidate.optBoolean("is_album") || candidate.optLong("user_id") != owner ||
                !title.equals(candidate.optString("title").trim(), ignoreCase = true)) continue
            val id = candidate.optLong("id").takeIf { it > 0L } ?: continue
            val album = getJson("$API/playlists/$id?client_id=$key")
            if (!album.optBoolean("is_album") || album.optLong("user_id") != owner ||
                !title.equals(album.label("title"), ignoreCase = true)) continue
            val entries = album.optJSONArray("tracks")?.objects().orEmpty()
            if (entries.none { it.optLong("id") == trackId }) continue
            val existing = entries.mapNotNull(::parseTrack).associateBy { it.id }
            val ids = entries.map { it.optLong("id") }.filter { it > 0L }
            val remaining = ids.distinct().filterNot(existing::containsKey).chunked(50).flatMap { chunk ->
                JSONArray(readUrl("$API/tracks?ids=${chunk.joinToString(",")}&client_id=$key"))
                    .objects().mapNotNull(::parseTrack)
            }.associateBy { it.id }
            return@withContext Album(
                title = album.label("title") ?: title,
                artist = artist,
                artworkUrl = album.label("artwork_url")?.takeIf(::isTrustedMediaUrl),
                releaseDate = album.label("release_date")?.substringBefore('T'),
                durationMs = album.optLong("duration").coerceAtLeast(0),
                trackCount = album.optInt("track_count").coerceAtLeast(ids.size),
                tracks = ids.mapNotNull { existing[it] ?: remaining[it] }
            )
        }
        null
    }

    suspend fun artist(id: Long?, permalink: String?): Artist = withContext(Dispatchers.IO) {
        val key = clientId()
        val owner = id ?: run {
            require(permalink != null && isTrustedTrackUrl(permalink))
            getJson("$API/resolve?url=${encode(permalink)}&client_id=$key").getJSONObject("user").getLong("id")
        }
        val profile = getJson("$API/users/$owner?client_id=$key")
        Artist(profile.getString("username"), profile.optString("avatar_url"),
            profile.optString("description").takeUnless { it == "null" }.orEmpty(), owner)
    }

    suspend fun artistTracks(id: Long, next: String?): ArtistPage = withContext(Dispatchers.IO) {
        val endpoint = next ?: "$API/users/$id/tracks?limit=50&linked_partitioning=1"
        require(isTrustedApiUrl(endpoint) && Uri.parse(endpoint).path == "/users/$id/tracks")
        val url = Uri.parse(endpoint)
        val keyed = if (url.getQueryParameter("client_id") == null)
            url.buildUpon().appendQueryParameter("client_id", clientId()).build() else url
        val data = getJson(keyed.toString())
        ArtistPage(parseTracks(data), data.optString("next_href").takeIf { isTrustedApiUrl(it) })
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull(::optJSONObject)

    private fun JSONObject.label(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf(String::isNotEmpty)

    private fun JSONObject.albumTitle(): String? = label("album_title")?.takeUnless { it.equals("SoundCloud", ignoreCase = true) }

    private fun parseTracks(root: JSONObject): List<SoundCloudTrack> =
        root.optJSONArray("collection")?.objects().orEmpty().mapNotNull(::parseTrack)

    private fun parseTrack(item: JSONObject): SoundCloudTrack? {
        val id = item.optLong("id", -1L)
        val title = item.label("title") ?: return null
        val permalink = item.label("permalink_url") ?: return null
        if (id <= 0L || !isTrustedTrackUrl(permalink)) return null
        val user = item.optJSONObject("user")
        val publisher = item.optJSONObject("publisher_metadata")
        return SoundCloudTrack(
            id = id,
            title = title,
            artist = user?.label("username") ?: "Неизвестный исполнитель",
            durationMs = item.optLong("duration", 0L).coerceAtLeast(0L),
            artworkUrl = item.label("artwork_url")?.takeIf { it.startsWith("https://", ignoreCase = true) }
                ?: user?.label("avatar_url")?.takeIf { it.startsWith("https://", ignoreCase = true) },
            artistId = user?.optLong("id")?.takeIf { it > 0 },
            credit = publisher?.label("artist").orEmpty(),
            full = item.optString("policy") !in setOf("SNIP", "BLOCK"),
            permalinkUrl = permalink,
            albumTitle = publisher?.albumTitle()
        )
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
        val cacheKey = permalinkUrl
        val cached = streamCache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.cachedAt < CACHE_MS) {
            return cached.url
        }

        val key = clientId()
        val resolvedUrl = "$API/resolve?url=${encode(permalinkUrl)}&client_id=$key"
        val track = getJson(resolvedUrl)
        if (track.optString("policy") in setOf("SNIP", "BLOCK")) throw IOException("Полная версия недоступна в SoundCloud.")
        val media = track.optJSONObject("media")
            ?: throw IOException("SoundCloud не предоставил поток для этого трека.")
        val transcodings = media.optJSONArray("transcodings") ?: JSONArray()
        val authorization = track.optString("track_authorization").takeIf { it.isNotBlank() }

        val candidates = (0 until transcodings.length()).mapNotNull { transcodings.optJSONObject(it) }
            .filter { transcoding ->
                val format = transcoding.optJSONObject("format")
                !transcoding.optBoolean("snipped") &&
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
                streamCache[cacheKey] = CachedStream(streamUrl, System.currentTimeMillis())
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
