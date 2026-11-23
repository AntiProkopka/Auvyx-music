package com.nexus.music.data.local

import android.content.Context
import android.net.Uri
import com.nexus.music.data.db.TrackEntity
import com.nexus.music.data.db.TrackDao
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class OfflineAudio(private val context: Context, private val dao: TrackDao, private val scope: CoroutineScope) {
    data class Transfer(val bytes: Long, val total: Long)
    val progress = MutableStateFlow<Map<Long, Transfer>>(emptyMap())
    private val jobs = mutableMapOf<Long, Job>()

    private val recovery = scope.async(Dispatchers.IO) {
        val directory = File(context.filesDir, "offline")
        directory.listFiles { f -> f.name.endsWith(".part") }?.forEach { it.delete() }
        if (directory.exists()) {
            dao.getAll().filter { it.source == "local" }.forEach { track ->
                val actual = file(context, track.uri).takeIf { it.isFile && it.length() > 0 }?.absolutePath
                if (actual != track.downloadedPath) dao.setDownloaded(track.id, actual)
            }
        }
    }

    // Only user-selected documents are eligible. Streaming URLs are never copied.
    fun save(track: TrackEntity, onError: (String) -> Unit) {
        if (track.source != "local" || jobs.containsKey(track.id)) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                recovery.await()
                withContext(Dispatchers.IO) {
                    val uri = Uri.parse(track.uri)
                    require(uri.scheme == "content")
                    val dest = file(context, track.uri)
                    dest.parentFile!!.mkdirs()
                    val part = File.createTempFile("audio", ".part", dest.parentFile)
                    try {
                        val length = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: -1L
                        var count = 0L
                        progress.update { it + (track.id to Transfer(0, length)) }
                        requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                            part.outputStream().use { out ->
                                val buffer = ByteArray(65536)
                                while (true) {
                                    ensureActive()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    out.write(buffer, 0, n)
                                    count += n
                                    progress.update { it + (track.id to Transfer(count, length)) }
                                }
                                out.fd.sync()
                            }
                        }
                        check(count > 0 && (length < 0 || count == length))
                        ensureActive()
                        withContext(NonCancellable) {
                            check(part.renameTo(dest))
                            dao.setDownloaded(track.id, dest.absolutePath)
                        }
                    } finally { part.delete() }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { onError("Копирование не удалось. Можно повторить: ${e.localizedMessage}") }

        }
        jobs[track.id] = job
        job.invokeOnCompletion {
            scope.launch {
                if (jobs[track.id] === job) {
                    jobs.remove(track.id)
                    progress.update { it - track.id }
                }
            }
        }
        job.start()
    }

    fun cancel(id: Long) { jobs[id]?.cancel() }

    suspend fun remove(track: TrackEntity) {
        recovery.await()
        jobs[track.id]?.cancelAndJoin()
        withContext(Dispatchers.IO) {
            val dest = file(context, track.uri)
            check(!dest.exists() || dest.delete())
            dao.setDownloaded(track.id, null)
        }
    }

    companion object {
        fun file(context: Context, uri: String) =
            File(context.filesDir, "offline/" + UUID.nameUUIDFromBytes(uri.toByteArray(Charsets.UTF_8)))
    }
}
