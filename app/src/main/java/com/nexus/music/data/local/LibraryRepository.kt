package com.nexus.music.data.local

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.nexus.music.data.db.NexusDatabase
import com.nexus.music.data.db.TrackEntity
import com.nexus.music.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class LibraryRepository(
    private val context: Context,
    private val db: NexusDatabase,
    private val settings: SettingsStore
) {
    private val audioExtensions = setOf("mp3", "flac", "wav", "aac", "m4a", "ogg", "oga", "opus")

    /** Добавляет отдельные файлы, выбранные через системный выбор файлов. */
    suspend fun addFiles(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        uris.forEach { uri -> persistPermission(uri) }
        val items = uris.map { uri -> uri to DocumentFile.fromSingleUri(context, uri)?.name }
        insertNew(items)
    }

    /** Запоминает папку и рекурсивно добавляет из неё аудиофайлы. */
    suspend fun addFolder(tree: Uri): Int = withContext(Dispatchers.IO) {
        persistPermission(tree)
        settings.addFolder(tree.toString())
        scanFolder(tree)
    }

    /** Пересканирует сохранённые папки и убирает треки, чьих файлов больше нет. */
    suspend fun refresh(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var added = 0
        for (folder in settings.folders.first()) {
            try {
                added += scanFolder(Uri.parse(folder))
            } catch (_: Exception) {
            }
        }
        added to removeMissing()
    }

    suspend fun deleteTracks(ids: List<Long>) = withContext(Dispatchers.IO) {
        ids.chunked(500).forEach { db.trackDao().deleteByIds(it) }
    }

    private fun persistPermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
    }

    private suspend fun scanFolder(tree: Uri): Int {
        val root = DocumentFile.fromTreeUri(context, tree) ?: return 0
        val found = ArrayList<Pair<Uri, String?>>()
        collectAudio(root, found)
        return insertNew(found)
    }

    private fun collectAudio(dir: DocumentFile, out: MutableList<Pair<Uri, String?>>) {
        for (file in dir.listFiles()) {
            if (file.isDirectory) {
                collectAudio(file, out)
            } else if (isAudio(file.name, file.type)) {
                out.add(file.uri to file.name)
            }
        }
    }

    private fun isAudio(name: String?, mime: String?): Boolean {
        if (mime != null && mime.startsWith("audio/")) return true
        val ext = name?.substringAfterLast('.', "")?.lowercase() ?: return false
        return ext in audioExtensions
    }

    private suspend fun insertNew(items: List<Pair<Uri, String?>>): Int {
        val existing = db.trackDao().allUris().toHashSet()
        val now = System.currentTimeMillis()
        val tracks = items
            .filter { it.first.toString() !in existing }
            .mapNotNull { (uri, name) -> readTrack(uri, name, now) }
        if (tracks.isEmpty()) return 0
        val ids = db.trackDao().insertAll(tracks)
        return ids.count { it != -1L }
    }

    private fun readTrack(uri: Uri, name: String?, addedAt: Long): TrackEntity? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: name?.substringBeforeLast('.')
                ?: "Без названия"
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: "Неизвестный исполнитель"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() } ?: "Неизвестный альбом"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            TrackEntity(
                uri = uri.toString(),
                title = title,
                artist = artist,
                album = album,
                durationMs = duration,
                addedAt = addedAt
            )
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun removeMissing(): Int {
        val missing = db.trackDao().getAll().filter { track ->
            val doc = try {
                DocumentFile.fromSingleUri(context, Uri.parse(track.uri))
            } catch (_: Exception) {
                null
            }
            doc == null || !doc.exists()
        }
        missing.map { it.id }.chunked(500).forEach { db.trackDao().deleteByIds(it) }
        return missing.size
    }
}
