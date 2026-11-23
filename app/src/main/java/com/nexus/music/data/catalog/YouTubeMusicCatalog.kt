package com.nexus.music.data.catalog

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal enum class CatalogKind(val label: String, val parameter: String) {
    Songs("Треки", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"),
    Artists("Исполнители", "EgWKAQIgAWoKEAkQBRAKEAMQBA%3D%3D"),
    Albums("Альбомы", "EgWKAQIYAWoKEAkQBRAKEAMQBA%3D%3D"),
    Playlists("Плейлисты", "EgWKAQIoAWoKEAkQBRAKEAMQBA%3D%3D")
}

internal data class CatalogEntry(val url: String, val title: String, val details: String, val artwork: String?)

internal object YouTubeMusicCatalog {
    suspend fun search(query: String, kind: CatalogKind): List<CatalogEntry> = withContext(Dispatchers.IO) {
        val client = JSONObject().put("clientName", "WEB_REMIX").put("clientVersion", "1.20261118.01.00").put("hl", "ru")
        val payload = JSONObject().put("context", JSONObject().put("client", client))
            .put("query", query).put("params", kind.parameter)
        val connection = URL("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 8000
            connection.readTimeout = 12000
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.outputStream.bufferedWriter().use { it.write(payload.toString()) }
            val response = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            parse(response, kind)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(response: JSONObject, kind: CatalogKind): List<CatalogEntry> {
        val tabs = response.getJSONObject("contents").getJSONObject("tabbedSearchResultsRenderer").getJSONArray("tabs")
        val songs = mutableListOf<CatalogEntry>()
        for (tab in tabs.objects()) {
            val sections = tab.optJSONObject("tabRenderer")?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")?.optJSONArray("contents") ?: continue
            for (section in sections.objects()) {
                val rows = section.optJSONObject("musicShelfRenderer")?.optJSONArray("contents") ?: continue
                for (row in rows.objects()) {
                    val item = row.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    val url = entryUrl(item, kind) ?: continue
                    val columns = item.optJSONArray("flexColumns") ?: continue
                    val title = columnText(columns, 0)
                    if (title.isBlank()) continue
                    val images = item.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    songs.add(CatalogEntry(url, title, columnText(columns, 1), images?.objects()?.lastOrNull()?.optString("url")))
                }
            }
        }
        return songs.distinctBy(CatalogEntry::url)
    }

    private fun entryUrl(item: JSONObject, kind: CatalogKind): String? {
        if (kind == CatalogKind.Songs) {
            val id = item.optJSONObject("playlistItemData")?.optString("videoId") ?: return null
            return if (videoIdPattern.matches(id)) "https://www.youtube.com/embed/$id" else null
        }
        val id = item.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId") ?: return null
        if (!browseIdPattern.matches(id)) return null
        return when (kind) {
            CatalogKind.Playlists -> "https://music.youtube.com/playlist?list=" + id.removePrefix("VL")
            CatalogKind.Artists -> "https://music.youtube.com/channel/$id"
            else -> "https://music.youtube.com/browse/$id"
        }
    }

    private val browseIdPattern = Regex("[A-Za-z0-9_-]{2,120}")
    private val videoIdPattern = Regex("[A-Za-z0-9_-]{11}")
    private fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }
    private fun columnText(columns: JSONArray, index: Int) = columns.optJSONObject(index)
        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
        ?.optJSONArray("runs")?.objects()?.joinToString("") { it.optString("text") }.orEmpty()
}
