package com.nexus.music

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.music.data.db.PlaylistEntity
import com.nexus.music.data.db.PlaylistSummary
import com.nexus.music.data.db.PlaylistTrackEntity
import com.nexus.music.data.db.TrackEntity
import com.nexus.music.data.soundcloud.SoundCloudRepository
import com.nexus.music.data.soundcloud.SoundCloudTrack
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val nexus = application as NexusApp
    private val db = nexus.database
    private val library = nexus.library
    private val settings = nexus.settings
    val player = nexus.playerController

    private val lazily = SharingStarted.WhileSubscribed(5_000)

    val tracks: StateFlow<List<TrackEntity>> =
        db.trackDao().observeAll().stateIn(viewModelScope, lazily, emptyList())
    val favorites: StateFlow<List<TrackEntity>> =
        db.trackDao().observeFavorites().stateIn(viewModelScope, lazily, emptyList())
    val recent: StateFlow<List<TrackEntity>> =
        db.historyDao().observeRecent(50).stateIn(viewModelScope, lazily, emptyList())
    val playlists: StateFlow<List<PlaylistSummary>> =
        db.playlistDao().observePlaylists().stateIn(viewModelScope, lazily, emptyList())

    val themeMode: StateFlow<String> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, "system")
    val sortMode: StateFlow<String> =
        settings.sortMode.stateIn(viewModelScope, SharingStarted.Eagerly, "added")
    val folders: StateFlow<Set<String>> =
        settings.folders.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val busy = MutableStateFlow(false)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    val searchQuery = MutableStateFlow("")

    // SoundCloud catalogue state. Remote entries stay separate from the local Room library.
    val soundCloudResults = MutableStateFlow<List<SoundCloudTrack>>(emptyList())
    val soundCloudLoading = MutableStateFlow(false)
    val soundCloudError = MutableStateFlow<String?>(null)
    private var soundCloudSearchJob: Job? = null

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<TrackEntity>> = searchQuery
        .debounce(300)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList<TrackEntity>()) else db.trackDao().search(q.trim())
        }
        .stateIn(viewModelScope, lazily, emptyList())

    // ---------- SoundCloud ----------

    fun searchSoundCloud(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            soundCloudResults.value = emptyList()
            soundCloudError.value = null
            return
        }
        soundCloudSearchJob?.cancel()
        soundCloudSearchJob = viewModelScope.launch {
            soundCloudLoading.value = true
            soundCloudError.value = null
            try {
                soundCloudResults.value = SoundCloudRepository.searchTracks(trimmed)
                if (soundCloudResults.value.isEmpty()) {
                    soundCloudError.value = "Ничего не найдено. Попробуй другой запрос."
                }
            } catch (error: Exception) {
                soundCloudResults.value = emptyList()
                soundCloudError.value = error.message ?: "Не удалось выполнить поиск SoundCloud."
            } finally {
                soundCloudLoading.value = false
            }
        }
    }

    fun playSoundCloud(results: List<SoundCloudTrack>, index: Int) {
        player.playSoundCloudTracks(results, index)
    }

    fun enqueueSoundCloud(track: SoundCloudTrack) {
        player.enqueueSoundCloudTrack(track)
        messages.tryEmit("Добавлено в очередь: ${track.title}")
    }

    // ---------- Медиатека ----------

    fun addFiles(uris: List<Uri>) {
        viewModelScope.launch {
            busy.value = true
            try {
                val n = library.addFiles(uris)
                messages.tryEmit(if (n > 0) "Добавлено треков: $n" else "Новых треков не найдено")
            } catch (e: Exception) {
                messages.tryEmit("Ошибка при добавлении файлов")
            } finally {
                busy.value = false
            }
        }
    }

    fun addFolder(uri: Uri) {
        viewModelScope.launch {
            busy.value = true
            try {
                val n = library.addFolder(uri)
                messages.tryEmit(if (n > 0) "Добавлено треков: $n" else "Новых треков не найдено")
            } catch (e: Exception) {
                messages.tryEmit("Ошибка при сканировании папки")
            } finally {
                busy.value = false
            }
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            busy.value = true
            try {
                val (added, removed) = library.refresh()
                messages.tryEmit("Обновлено: добавлено $added, удалено $removed")
            } catch (e: Exception) {
                messages.tryEmit("Ошибка при обновлении")
            } finally {
                busy.value = false
            }
        }
    }

    fun toggleFavorite(t: TrackEntity) {
        viewModelScope.launch { db.trackDao().setFavorite(t.id, !t.isFavorite) }
    }

    fun deleteTrack(t: TrackEntity) {
        viewModelScope.launch {
            player.removeTracks(setOf(t.id))
            library.deleteTracks(listOf(t.id))
            messages.tryEmit("Удалено из медиатеки (файл на устройстве не тронут)")
        }
    }

    // ---------- Плейлисты ----------

    fun playlist(id: Long): Flow<PlaylistEntity?> = db.playlistDao().observePlaylist(id)

    fun playlistTracks(id: Long): Flow<List<TrackEntity>> = db.playlistDao().observeTracks(id)

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            db.playlistDao().insertPlaylist(PlaylistEntity(name = name, createdAt = System.currentTimeMillis()))
        }
    }

    fun createPlaylistWithTrack(name: String, trackId: Long) {
        viewModelScope.launch {
            val dao = db.playlistDao()
            val id = dao.insertPlaylist(PlaylistEntity(name = name, createdAt = System.currentTimeMillis()))
            dao.insertLink(PlaylistTrackEntity(playlistId = id, trackId = trackId, position = 0))
            messages.tryEmit("Плейлист «$name» создан")
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        viewModelScope.launch { db.playlistDao().rename(id, name) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { db.playlistDao().delete(id) }
    }

    fun addToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            val dao = db.playlistDao()
            if (dao.contains(playlistId, trackId) > 0) {
                messages.tryEmit("Трек уже в плейлисте")
            } else {
                dao.insertLink(
                    PlaylistTrackEntity(
                        playlistId = playlistId,
                        trackId = trackId,
                        position = dao.nextPosition(playlistId)
                    )
                )
                messages.tryEmit("Добавлено в плейлист")
            }
        }
    }

    fun removeFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch { db.playlistDao().removeTrack(playlistId, trackId) }
    }

    // ---------- Данные и настройки ----------

    fun clearHistory() {
        viewModelScope.launch { db.historyDao().clear() }
    }

    fun setTheme(mode: String) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setSort(mode: String) {
        viewModelScope.launch { settings.setSortMode(mode) }
    }

    fun resetSettings() {
        viewModelScope.launch {
            settings.resetUiSettings()
            messages.tryEmit("Настройки сброшены")
        }
    }
}
