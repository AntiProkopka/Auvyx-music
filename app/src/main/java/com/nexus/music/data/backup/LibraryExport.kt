package com.nexus.music.data.backup

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import androidx.room.withTransaction
import com.nexus.music.data.db.NexusDatabase
import com.nexus.music.data.settings.SettingsStore
import org.json.JSONArray
import org.json.JSONObject

internal object LibraryExport {
    suspend fun write(resolver: ContentResolver, uri: Uri, database: NexusDatabase, settings: SettingsStore) {
        val tables = database.withTransaction {
            JSONObject().apply {
                for (table in listOf("tracks", "playlists", "playlist_tracks", "history", "play_queue")) {
                    val rows = JSONArray()
                    database.query("SELECT * FROM $table", emptyArray<Any>()).use { cursor ->
                        while (cursor.moveToNext()) {
                            val row = JSONObject()
                            for (column in 0 until cursor.columnCount) {
                                val value = when (cursor.getType(column)) {
                                    Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(column)
                                    Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(column)
                                    Cursor.FIELD_TYPE_STRING -> cursor.getString(column)
                                    else -> error("Неподдерживаемый тип данных")
                                }
                                row.put(cursor.getColumnName(column), value)
                            }
                            rows.put(row)
                        }
                    }
                    put(table, rows)
                }
            }
        }
        val backup = JSONObject()
            .put("format", "auvyx-library")
            .put("version", 1)
            .put("settings", settings.backupSettings())
            .put("tables", tables)
        checkNotNull(resolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(backup.toString()) }
    }
}
