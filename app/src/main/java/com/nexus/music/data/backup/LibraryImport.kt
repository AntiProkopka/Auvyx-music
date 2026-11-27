package com.nexus.music.data.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.nexus.music.data.db.*
import com.nexus.music.data.settings.SettingsStore
import org.json.JSONObject

internal object LibraryImport {
    private fun JSONObject.rows(table: String): List<JSONObject> {
        val array = getJSONArray(table)
        return List(array.length(), array::getJSONObject)
    }
    private fun JSONObject.text(key: String) = if (isNull(key)) null else getString(key)

    suspend fun read(resolver: ContentResolver, uri: Uri, db: NexusDatabase, settings: SettingsStore) {
        val raw = requireNotNull(resolver.openInputStream(uri)).bufferedReader().use { input ->
            val out = StringBuilder()
            val buf = CharArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                require(out.length + n <= 32 * 1024 * 1024) { "Копия крупнее 32 МБ" }
                out.append(buf, 0, n)
            }
            out.toString()
        }
        val root = JSONObject(raw)
        require(root.getString("format") == "auvyx-library" && root.getInt("version") in 1..2) { "Неподдерживаемая версия" }
        val tables = root.getJSONObject("tables")
        val prefs = root.getJSONObject("settings")
        db.withTransaction {
            val tracks = mutableMapOf<Long, Long>()
            val playlists = mutableMapOf<Long, Long>()
            val dao = db.trackDao()
            val sql = db.openHelper.writableDatabase
            val existing = dao.getAll().associateBy { it.uri }.toMutableMap()
            for (row in tables.rows("tracks")) {
                val old = row.getLong("id")
                val source = row.getString("source")
                val path = row.getString("uri")
                require(old > 0 && old !in tracks)
                require(source in setOf("local","soundcloud","spotify","youtube"))
                if (source == "local") require(Uri.parse(path).scheme == "content")
                val item = TrackEntity(uri = path, title = row.getString("title"), artist = row.getString("artist"),
                    album = row.getString("album"), addedAt = row.getLong("addedAt"), durationMs = row.getLong("durationMs"),
                    isFavorite = row.getInt("isFavorite") == 1, source = source, sourceId = row.text("sourceId") ?: path.takeIf { source == "local" },
                    sourceUrl = row.text("sourceUrl"), artistId = row.text("artistId"),
                    artworkUri = row.text("artworkUri")?.takeIf { it.startsWith("https://") })
                val prior = existing[path] ?: item.sourceId?.let { dao.getBySourceId(source, it) }
                val id = prior?.id ?: dao.insertOrIgnore(item)
                require(id > 0)
                if (item.isFavorite) dao.setFavorite(id, true)
                existing[path] = item.copy(id = id)
                tracks[old] = id
            }
            val lists = db.playlistDao()
            for (row in tables.rows("playlists")) {
                val old = row.getLong("id")
                require(old > 0 && old !in playlists)
                val name = row.getString("name")
                val created = row.getLong("createdAt")
                val id = sql.query("SELECT id FROM playlists WHERE name=? AND createdAt=? ORDER BY id LIMIT 1", arrayOf(name, created)).use {
                    if (it.moveToFirst()) it.getLong(0) else null
                } ?: lists.insertPlaylist(PlaylistEntity(name = name, createdAt = created))
                playlists[old] = id
            }
            for (row in tables.rows("playlist_tracks").sortedBy { it.getInt("position") }) {
                val list = playlists.getValue(row.getLong("playlistId"))
                val track = tracks.getValue(row.getLong("trackId"))
                if (lists.contains(list, track) == 0) lists.insertLink(PlaylistTrackEntity(
                    playlistId = list, trackId = track, position = lists.nextPosition(list)))
            }
            for (row in tables.rows("history")) {
                val track = tracks.getValue(row.getLong("trackId"))
                val time = row.getLong("playedAt")
                sql.execSQL("INSERT INTO history(trackId,playedAt) SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM history WHERE trackId=? AND playedAt=?)",
                    arrayOf(track,time,track,time))
            }
            val queue = tables.rows("play_queue").sortedBy { it.getInt("position") }.mapIndexed { i, row ->
                QueueEntity(i, tracks.getValue(row.getLong("trackId")))
            }
            val restoreQueue = queue.isNotEmpty() && db.queueDao().getAll().isEmpty()
            if (restoreQueue) db.queueDao().replace(queue)
            restoreTrackTools(tables, tracks, db)
            settings.restoreSettings(prefs, tracks, if (restoreQueue) queue.size else 0)
        }
    }
}
