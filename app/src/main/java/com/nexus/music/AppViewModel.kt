package com.nexus.music

import androidx.room.withTransaction
import com.nexus.music.data.settings.GlassAccent
import com.nexus.music.data.settings.GlassQuality
import com.nexus.music.data.settings.FollowedArtist
import kotlinx.coroutines.flow.combine
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.music.data.db.DAY_MILLIS
import com.nexus.music.data.db.ForgottenTrack
import com.nexus.music.data.db.REDISCOVER_DAYS
import com.nexus.music.data.db.REDISCOVER_MIN_STARTS
import com.nexus.music.data.db.diversifyForgotten
import com.nexus.music.data.db.PlaylistEntity
import com.nexus.music.data.db.PlaylistSummary
import com.nexus.music.data.db.PlaylistTrackEntity
import com.nexus.music.data.db.hasEnabledSource
import kotlinx.coroutines.flow.map
import com.nexus.music.data.db.TrackEntity
import com.nexus.music.data.settings.SettingsStore
import com.nexus.music.data.soundcloud.SoundCloudRepository
import com.nexus.music.data.soundcloud.SoundCloudTrack
import com.nexus.music.playback.SixBandEqualizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.nexus.music.data.backup.LibraryExport

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val nexus = application as NexusApp
    private val db = nexus.database
    private val library = nexus.library
    private val settings = nexus.settings
    val player = nexus.playerController
    internal val trackTools = db.trackTools()
    internal val trackToolsTarget = MutableStateFlow<TrackEntity?>(null)
    internal val activeMoment = nexus.activeMoment.asStateFlow()

    internal fun saveTrackTools(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                messages.emit(if (failure is IllegalArgumentException) failure.message ?: "Проверьте значения"
                    else "Не удалось сохранить изменения трека")
            }
        }
    }

    private val lazily = SharingStarted.WhileSubscribed(5_000)

    val tracks: StateFlow<List<TrackEntity>> =
        db.trackDao().observeLocal().stateIn(viewModelScope, lazily, emptyList())
    val knownTracks: StateFlow<List<TrackEntity>> =
        db.trackDao().observeAll().map { tracks -> tracks.filter { it.hasEnabledSource } }.stateIn(viewModelScope, lazily, emptyList())
    val favorites: StateFlow<List<TrackEntity>> =
        db.trackDao().observeFavorites().map { tracks -> tracks.filter { it.hasEnabledSource } }.stateIn(viewModelScope, lazily, emptyList())
    val recent: StateFlow<List<TrackEntity>> =
        db.historyDao().observeRecent(50).map { tracks -> tracks.filter { it.hasEnabledSource } }.stateIn(viewModelScope, lazily, emptyList())
    val listenCounts = db.historyDao().observeCounts().map { rows -> rows.associate { it.trackId to it.listens } }.stateIn(viewModelScope, lazily, emptyMap())
    val playlists: StateFlow<List<PlaylistSummary>> =
        db.playlistDao().observePlaylists().stateIn(viewModelScope, lazily, emptyList())

    val themeMode: StateFlow<String> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, "system")
    val visualStyle = settings.visualStyle.stateIn(viewModelScope, SharingStarted.Eagerly, "classic")
    fun setStyle(style: String) { viewModelScope.launch { settings.setStyle(style) } }
    val accent = settings.accent.stateIn(viewModelScope, lazily, GlassAccent.Sunset)
    val photo = settings.photo.stateIn(viewModelScope, lazily, "")
    val glassQuality = settings.glassQuality.stateIn(viewModelScope, SharingStarted.Eagerly, GlassQuality.Ultra)
    fun setGlassQuality(quality: GlassQuality) {
        viewModelScope.launch { settings.setGlassQuality(quality) }
    }
    val blur = settings.blur.stateIn(viewModelScope, lazily, 32)
    val recentLabels = settings.recentLabels.stateIn(viewModelScope, lazily, true)
    val followed = settings.followed.stateIn(viewModelScope, lazily, emptyList())
    val queries = settings.queries.stateIn(viewModelScope, lazily, emptyList())
    val searchTracks = combine(settings.openedTracks, knownTracks) { uris, tracks ->
        val byUri = tracks.associateBy { it.uri }
        uris.mapNotNull(byUri::get)
    }.stateIn(viewModelScope, lazily, emptyList())

    fun selectAppearance(accent: GlassAccent, photo: String = "") { viewModelScope.launch { settings.selectAppearance(accent, photo) } }
    fun setBlur(value: Int) { viewModelScope.launch { settings.setBlur(value) } }
    fun setRecentLabels(value: Boolean) { viewModelScope.launch { settings.setLabels(value) } }
    fun toggleFollow(artist: FollowedArtist) { viewModelScope.launch { settings.toggleFollow(artist) } }
    fun rememberSearch(query: String, uri: String? = null) { viewModelScope.launch { settings.rememberSearch(query, uri) } }
    fun clearSearch() { viewModelScope.launch { settings.clearSearch() } }
    val crossfade = settings.crossfade.stateIn(viewModelScope, lazily, 0)
    fun setCrossfade(value: Int) { viewModelScope.launch { settings.setCrossfade(value) } }

    val sortMode: StateFlow<String> =
        settings.sortMode.stateIn(viewModelScope, SharingStarted.Eagerly, "added")
    val folders: StateFlow<Set<String>> =
        settings.folders.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _nickname = MutableStateFlow<String?>(null)
    val nickname: StateFlow<String?> = _nickname.asStateFlow()
    val nicknameReady = MutableStateFlow(false)
    private val _equalizerGains = MutableStateFlow(SettingsStore.DEFAULT_EQ_GAINS)
    val equalizerGains: StateFlow<List<Float>> = _equalizerGains.asStateFlow()
    val equalizerPreset: StateFlow<String> = settings.equalizerPreset.stateIn(
        viewModelScope, SharingStarted.Eagerly, "bass_boost"
    )
    val equalizerEnabled: StateFlow<Boolean> = settings.equalizerEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, true
    )

    data class AlbumTarget(val title: String, val artist: String, val soundCloud: SoundCloudAlbumTrack? = null) {
        data class SoundCloudAlbumTrack(val id: Long, val permalink: String, val releaseId: Long? = null)

        companion object {
            fun from(track: TrackEntity): AlbumTarget? {
                val title = track.album.trim()
                if (track.source == "soundcloud") {
                    val id = track.sourceId?.toLongOrNull() ?: return null
                    val permalink = track.sourceUrl?.takeIf { it.startsWith("https://") } ?: return null
                    return AlbumTarget(title.takeUnless { it.equals("SoundCloud", true) }.orEmpty(), track.artist,
                        SoundCloudAlbumTrack(id, permalink))
                }
                if (track.source != "local" || title.isEmpty() || title == "Неизвестный альбом") return null
                return AlbumTarget(title, track.artist.trim())
            }
        }

        fun contains(track: TrackEntity): Boolean {
            if (soundCloud != null || track.source != "local") return false
            val album = from(track) ?: return false
            return title.equals(album.title, ignoreCase = true) && artist.equals(album.artist, ignoreCase = true)
        }
    }

    val albumTarget = MutableStateFlow<AlbumTarget?>(null)
    fun openAlbum(track: TrackEntity) {
        albumTarget.value = AlbumTarget.from(track)
        artistTarget.value = null
    }

    fun openRelease(release: SoundCloudRepository.Release) {
        albumTarget.value = AlbumTarget(release.title, release.artist, AlbumTarget.SoundCloudAlbumTrack(0, "", release.id))
        artistTarget.value = null
    }
    fun openAlbum(track: SoundCloudTrack) {
        val target = AlbumTarget.SoundCloudAlbumTrack(track.id, track.permalinkUrl)
        albumTarget.value = AlbumTarget(track.albumTitle.orEmpty(), track.artist, target)
        artistTarget.value = null
    }

    data class ArtistTarget(val name: String, val cloud: Boolean, val owner: Long?, val permalink: String?)
    val artistTarget = MutableStateFlow<ArtistTarget?>(null)
    fun openArtist(track: TrackEntity) {
        artistTarget.value = ArtistTarget(track.artist, track.source == "soundcloud", track.artistId?.toLongOrNull(), track.sourceUrl)
    }
    fun openArtist(track: SoundCloudTrack) {
        artistTarget.value = ArtistTarget(track.artist, true, track.artistId, track.permalinkUrl)
    }

    val busy = MutableStateFlow(false)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val searchQuery = MutableStateFlow("")

    val soundCloudResults = MutableStateFlow<List<SoundCloudTrack>>(emptyList())
    val soundCloudLoading = MutableStateFlow(false)
    val soundCloudError = MutableStateFlow<String?>(null)
    private var soundCloudSearchJob: Job? = null
    private var equalizerSaveJob: Job? = null

    init {
        // Previous builds used the provider name as a fake album. Clean it once
        // without altering genuine local albums or the IDs of saved songs.
        viewModelScope.launch(Dispatchers.IO) { db.trackDao().clearFakeSoundCloudAlbums() }
        viewModelScope.launch {
            settings.nickname.collect { value ->
                _nickname.value = value
                nicknameReady.value = true
            }
        }
        viewModelScope.launch {
            settings.equalizerGains.collect { gains ->
                _equalizerGains.value = gains
                nexus.equalizer.setGains(gains)
            }
        }
        viewModelScope.launch { settings.equalizerEnabled.collect(nexus.equalizer::setEnabled) }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<TrackEntity>> = searchQuery
        .debounce(300)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList<TrackEntity>()) else db.trackDao().search(q.trim())
        }
        .stateIn(viewModelScope, lazily, emptyList())

    val catalogPages = settings.catalogPages.stateIn(viewModelScope, lazily, emptyMap())

    fun toggleCatalogPage(url: String, title: String) {
        viewModelScope.launch { settings.toggleCatalogPage(url, title) }
    }

    val dislikedTracks = settings.dislikedTracks.stateIn(viewModelScope, lazily, emptySet())

    fun toggleDisliked(id: String) {
        viewModelScope.launch { settings.toggleDisliked(id) }
    }

    fun lyrics(id: String) = settings.lyrics(id)

    fun saveLyrics(id: String, content: String) {
        viewModelScope.launch { settings.saveLyrics(id, content) }
    }

    suspend fun saveDiscoveredLyrics(id: String, content: String) = settings.saveLyricsIfMissing(id, content)

    fun importLibrary(uri: Uri) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) {
                com.nexus.music.data.backup.LibraryImport.read(nexus.contentResolver, uri, db, settings)
            } }.onSuccess { player.loadImportedQueue(); messages.tryEmit("Медиатека восстановлена. Для недоступного трека выбери «Выбрать файл заново».") }
                .onFailure { messages.tryEmit("Не удалось восстановить копию: ${it.localizedMessage}") }
        }
    }

    fun exportLibrary(uri: Uri) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    LibraryExport.write(nexus.contentResolver, uri, db, settings)
                    true
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (_: Exception) {
                    false
                }
            }
            messages.tryEmit(if (success) "Резервная копия сохранена" else "Не удалось сохранить резервную копию")
        }
    }

    fun setNickname(value: String) {
        viewModelScope.launch {
            settings.setNickname(value)
            messages.tryEmit("Имя сохранено")
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        nexus.equalizer.setEnabled(enabled)
        viewModelScope.launch { settings.setEqualizerEnabled(enabled) }
    }

    fun setEqualizerPreset(preset: String) {
        val gains = when (preset) {
            "default" -> SixBandEqualizer.DEFAULT
            "treble" -> SixBandEqualizer.TREBLE_BOOST
            "vocal" -> SixBandEqualizer.VOCAL
            "custom" -> equalizerGains.value
            else -> SixBandEqualizer.BASS_BOOST
        }
        _equalizerGains.value = gains
        nexus.equalizer.setGains(gains)
        equalizerSaveJob?.cancel()
        equalizerSaveJob = viewModelScope.launch {
            settings.setEqualizerPreset(preset, gains)
        }
    }

    fun setEqualizerBand(index: Int, value: Float) {
        if (index !in 0 until SixBandEqualizer.BAND_COUNT) return
        val updated = _equalizerGains.value.toMutableList()
        updated[index] = value.coerceIn(-12f, 12f)
        _equalizerGains.value = updated
        nexus.equalizer.setGains(updated)
        equalizerSaveJob?.cancel()
        equalizerSaveJob = viewModelScope.launch {
            delay(180)
            settings.setEqualizerPreset("custom", updated)
        }
    }

    // SoundCloud catalogue
    fun searchSoundCloud(query: String) {
        val trimmed = query.trim()
        soundCloudSearchJob?.cancel()
        if (trimmed.isBlank()) {
            soundCloudLoading.value = false
            soundCloudResults.value = emptyList()
            soundCloudError.value = null
            return
        }
        soundCloudSearchJob = viewModelScope.launch {
            soundCloudLoading.value = true
            soundCloudError.value = null
            try {
                soundCloudResults.value = SoundCloudRepository.searchTracks(trimmed)
                if (soundCloudResults.value.isEmpty()) {
                    soundCloudError.value = "Ничего не найдено. Попробуй другой запрос."
                }
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel
            } catch (error: Exception) {
                soundCloudResults.value = emptyList()
                android.util.Log.w("SoundCloud", "Не удалось выполнить поиск", error)
                soundCloudError.value = when (error) {
                    is java.net.UnknownHostException, is java.net.ConnectException ->
                        "SoundCloud недоступен. Проверь подключение к интернету."
                    is java.net.SocketTimeoutException -> "SoundCloud не ответил вовремя. Повтори поиск."
                    else -> "Не удалось выполнить поиск SoundCloud."
                }
            } finally {
                if (kotlinx.coroutines.currentCoroutineContext()[Job]?.isActive == true) soundCloudLoading.value = false
            }
        }
    }

    private suspend fun ensureCatalog(track: TrackEntity): TrackEntity {
        val dao = db.trackDao()
        dao.insertOrIgnore(track)
        val saved = requireNotNull(dao.getBySourceId(track.source, requireNotNull(track.sourceId)))
        if (saved.uri != track.uri || saved.durationMs != track.durationMs) {
            dao.updateCatalogAudio(saved.id, track.uri, track.durationMs)
            return saved.copy(uri = track.uri, durationMs = track.durationMs)
        }
        return saved
    }

    fun withCatalog(track: TrackEntity, action: (TrackEntity) -> Unit) {
        viewModelScope.launch {
            runCatching { action(ensureCatalog(track)) }.onFailure { messages.tryEmit("Не удалось сохранить трек") }
        }
    }

    private var catalogQueueJob: Job? = null
    fun playCatalog(tracks: List<TrackEntity>, index: Int) {
        catalogQueueJob?.cancel()
        catalogQueueJob = viewModelScope.launch {
            runCatching { player.playTracks(tracks.map { ensureCatalog(it) }, index) }
                .onFailure { if (it !is kotlinx.coroutines.CancellationException) messages.tryEmit("Не удалось подготовить очередь") }
        }
    }

    private var requestedTrack: Job? = null

    fun playTrack(track: TrackEntity) {
        requestedTrack?.cancel()
        player.playTrack(track)
    }

    fun playTrack(track: SoundCloudTrack, search: String? = null) {
        if (!track.full) return
        requestedTrack?.cancel()
        requestedTrack = viewModelScope.launch {
            try {
                val saved = ensureSoundCloudTrack(track)
                player.playTrack(saved)
                if (search != null) settings.rememberSearch(search, saved.uri)
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                messages.tryEmit("Не удалось подготовить трек SoundCloud")
            }
        }
    }

    fun playSoundCloud(results: List<SoundCloudTrack>, index: Int) {
        if (results.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val unified = db.withTransaction { results.map { ensureSoundCloudTrack(it) } }
                player.playTracks(unified, index.coerceIn(0, unified.lastIndex))
            }.onFailure {
                messages.tryEmit("Не удалось подготовить треки SoundCloud: ${it.message ?: "ошибка"}")
            }
        }
    }

    fun enqueueSoundCloud(track: SoundCloudTrack) {
        viewModelScope.launch {
            runCatching { player.enqueue(ensureSoundCloudTrack(track)) }
                .onSuccess { messages.tryEmit("Добавлено в очередь: ${track.title}") }
                .onFailure { messages.tryEmit("Не удалось добавить трек в очередь") }
        }
    }

    fun playNextSoundCloud(track: SoundCloudTrack) {
        viewModelScope.launch {
            runCatching { player.playNext(ensureSoundCloudTrack(track)) }
                .onSuccess { messages.tryEmit("Следующим: ${track.title}") }
                .onFailure { messages.tryEmit("Не удалось добавить трек следующим") }
        }
    }

    private suspend fun ensureSoundCloudTrack(track: SoundCloudTrack): TrackEntity {
        val sourceId = track.id.toString()
        val dao = db.trackDao()
        val existing = dao.getBySourceId("soundcloud", sourceId)
        if (existing != null) {
            dao.updateSoundCloudMetadata(
                sourceId = sourceId,
                title = track.title,
                artist = track.artist,
                album = track.albumTitle ?: existing.album,
                durationMs = track.durationMs,
                artworkUri = track.artworkUrl,
                artistId = track.artistId?.toString(),
                sourceUrl = track.permalinkUrl
            )
            return dao.getBySourceId("soundcloud", sourceId) ?: existing
        }

        dao.insertOrIgnore(
            TrackEntity(
                uri = "auvyx-sc://track/$sourceId",
                title = track.title,
                artist = track.artist,
                album = track.albumTitle.orEmpty(),
                durationMs = track.durationMs,
                addedAt = System.currentTimeMillis(),
                artworkUri = track.artworkUrl,
                source = "soundcloud",
                sourceId = sourceId,
                artistId = track.artistId?.toString(),
                sourceUrl = track.permalinkUrl
            )
        )
        return dao.getBySourceId("soundcloud", sourceId)
            ?: error("SoundCloud-трек не удалось сохранить в медиатеку")
    }

    fun addSoundCloudToLibrary(tracks: List<SoundCloudTrack>) {
        viewModelScope.launch {
            try {
                db.withTransaction { tracks.forEach { ensureSoundCloudTrack(it) } }
                messages.tryEmit(if (tracks.size == 1) "Трек в медиатеке" else "Альбом добавлен в медиатеку")
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                messages.tryEmit("Не удалось сохранить треки в медиатеке")
            }
        }
    }

    fun addSoundCloudToPlaylist(playlistId: Long, track: SoundCloudTrack) {
        viewModelScope.launch {
            runCatching {
                val saved = ensureSoundCloudTrack(track)
                addSavedTrackToPlaylist(playlistId, saved.id)
            }.onFailure { messages.tryEmit("Не удалось добавить трек в плейлист") }
        }
    }

    fun createPlaylistWithSoundCloudTrack(name: String, track: SoundCloudTrack) {
        viewModelScope.launch {
            runCatching {
                val saved = ensureSoundCloudTrack(track)
                createPlaylistAndLink(name, saved.id)
                messages.tryEmit("Плейлист «$name» создан")
            }.onFailure { messages.tryEmit("Не удалось создать плейлист с треком") }
        }
    }

    private suspend fun addSavedTrackToPlaylist(playlistId: Long, trackId: Long) {
        val dao = db.playlistDao()
        db.withTransaction {
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

    fun toggleSoundCloudFavorite(track: SoundCloudTrack) {
        viewModelScope.launch {
            runCatching {
                val saved = ensureSoundCloudTrack(track)
                db.trackDao().toggleFavorite(saved.id)
                val next = db.trackDao().getByIds(listOf(saved.id)).first().isFavorite
                messages.tryEmit(if (next) "Добавлено в избранное" else "Убрано из избранного")
            }.onFailure { messages.tryEmit("Не удалось обновить избранное SoundCloud") }
        }
    }

    fun toggleCurrentFavorite() {
        val state = player.state.value
        val mediaId = state.mediaId ?: return
        val id = mediaId.toLongOrNull() ?: return
        viewModelScope.launch { db.trackDao().toggleFavorite(id) }
    }

    // Local library
    fun addFiles(uris: List<Uri>, album: AlbumTarget? = null) {
        viewModelScope.launch {
            busy.value = true
            try {
                val n = library.addFiles(uris) { album == null || album.contains(it) }
                messages.tryEmit(when {
                    n > 0 -> "Добавлено треков: $n"
                    album != null -> "Среди выбранных файлов нет новых треков этого альбома"
                    else -> "Новых треков не найдено"
                })
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
        viewModelScope.launch { db.trackDao().toggleFavorite(t.id) }
    }

    val offlineTransfers = nexus.offline.progress
    fun saveOffline(t: TrackEntity) = nexus.offline.save(t) { messages.tryEmit(it) }
    fun cancelOffline(t: TrackEntity) = nexus.offline.cancel(t.id)
    fun removeOffline(t: TrackEntity) {
        viewModelScope.launch { runCatching { nexus.offline.remove(t) }.onFailure { messages.tryEmit("Не удалось удалить копию") } }
    }
    val relinkTarget = MutableStateFlow<TrackEntity?>(null)
    fun relink(t: TrackEntity, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                require(t.source == "local" && uri.scheme == "content")
                withContext(Dispatchers.IO) {
                    nexus.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    nexus.contentResolver.openAssetFileDescriptor(uri, "r")!!.use { }
                }
                db.trackDao().relink(t.id, uri.toString())
            }.onFailure { messages.tryEmit("Не удалось привязать файл. Проверь доступ и дубликаты.") }
        }
    }

    fun deleteTrack(t: TrackEntity) {
        viewModelScope.launch {
            try {
                nexus.offline.remove(t) { library.deleteTracks(listOf(t.id)) }
                player.removeTracks(setOf(t.id))
                messages.tryEmit("Трек удалён из медиатеки")
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                messages.tryEmit("Не удалось удалить трек из медиатеки")
            }
        }
    }

    // Playlists
    fun playlist(id: Long): Flow<PlaylistEntity?> = db.playlistDao().observePlaylist(id)
    fun playlistTracks(id: Long): Flow<List<TrackEntity>> = db.playlistDao().observeTracks(id).map { tracks -> tracks.filter { it.hasEnabledSource } }

    internal fun forgottenTracks(): Flow<List<ForgottenTrack>> = combine(
        db.historyDao().forgotten(System.currentTimeMillis() - REDISCOVER_DAYS * DAY_MILLIS, REDISCOVER_MIN_STARTS),
        settings.dislikedTracks,
        settings.rediscoverHidden
    ) { rows, disliked, hidden ->
        withContext(Dispatchers.IO) {
            val resolver = nexus.contentResolver
            val eligible = rows.asSequence().filter {
                val track = it.track
                track.id.toString() !in disliked && track.id.toString() !in hidden
            }.filter {
                val track = it.track
                val copy = track.downloadedPath?.let { path -> java.io.File(path).canRead() } == true
                copy || if (track.source == "soundcloud") !track.sourceUrl.isNullOrBlank() else {
                    try {
                        val uri = Uri.parse(track.uri)
                        if (uri.scheme == "file") uri.path?.let { path -> java.io.File(path).canRead() } == true
                        else resolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
                    } catch (_: java.io.IOException) {
                        false
                    } catch (_: SecurityException) {
                        false
                    } catch (_: IllegalArgumentException) {
                        false
                    }
                }
            }.take(90).toList()
            diversifyForgotten(eligible, 30)
        }
    }

    internal fun hideForgotten(id: Long) = saveTrackTools { settings.hideRediscover(id) }
    internal fun showHiddenForgotten() = saveTrackTools { settings.resetRediscoverHidden() }

    internal fun savePlaylistSnapshot(name: String, tracks: List<TrackEntity>) = saveTrackTools {
        require(name.isNotBlank() && tracks.isNotEmpty())
        db.withTransaction {
            val dao = db.playlistDao()
            val id = dao.insertPlaylist(PlaylistEntity(name = name.trim(), createdAt = System.currentTimeMillis()))
            tracks.distinctBy { it.id }.forEachIndexed { position, track ->
                dao.insertLink(PlaylistTrackEntity(playlistId = id, trackId = track.id, position = position))
            }
        }
        messages.emit("Плейлист «${name.trim()}» создан")
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            db.playlistDao().insertPlaylist(PlaylistEntity(name = name, createdAt = System.currentTimeMillis()))
        }
    }

    fun createPlaylistWithTrack(name: String, trackId: Long) {
        viewModelScope.launch {
            createPlaylistAndLink(name, trackId)
            messages.tryEmit("Плейлист «$name» создан")
        }
    }

    private suspend fun createPlaylistAndLink(name: String, trackId: Long) = db.withTransaction {
        val dao = db.playlistDao()
        val id = dao.insertPlaylist(PlaylistEntity(name = name, createdAt = System.currentTimeMillis()))
        dao.insertLink(PlaylistTrackEntity(playlistId = id, trackId = trackId, position = 0))
    }

    fun renamePlaylist(id: Long, name: String) {
        viewModelScope.launch { db.playlistDao().rename(id, name) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { db.playlistDao().delete(id) }
    }

    fun addToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch { addSavedTrackToPlaylist(playlistId, trackId) }
    }

    fun removeFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch { db.playlistDao().removeTrack(playlistId, trackId) }
    }

    // Settings and history
    fun clearHistory() {
        viewModelScope.launch {
            db.historyDao().clear()

            messages.tryEmit("История прослушивания очищена")
        }
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
