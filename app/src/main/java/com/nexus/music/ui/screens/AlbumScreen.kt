package com.nexus.music.ui.screens

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.ui.LocalBottomBarPadding
import com.nexus.music.data.db.TrackEntity
import com.nexus.music.data.local.OfflineAudio
import com.nexus.music.data.soundcloud.SoundCloudRepository
import com.nexus.music.data.soundcloud.SoundCloudTrack
import com.nexus.music.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AlbumScreen(vm: AppViewModel, album: AppViewModel.AlbumTarget) {
    val soundCloud = album.soundCloud
    if (soundCloud != null) {
        SoundCloudAlbumScreen(vm, soundCloud)
        return
    }
    val library by vm.tracks.collectAsStateWithLifecycle()
    val player by vm.player.state.collectAsStateWithLifecycle()
    val members = remember(library, album) { library.filter(album::contains) }
    val context = LocalContext.current
    val metadata by produceState(emptyMap<Long, AlbumTrackTags>(), members) {
        value = withContext(Dispatchers.IO) {
            members.mapNotNull { track ->
                runCatching {
                    MediaMetadataRetriever().use { retriever ->
                        val original = Uri.parse(track.uri)
                        val copy = OfflineAudio.existingFile(context, track.uri)
                        if (copy != null) retriever.setDataSource(copy.absolutePath)
                        else retriever.setDataSource(context, original)
                        val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                        val date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                        track.id to AlbumTrackTags(
                            number = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER).tagNumber(),
                            disc = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER).tagNumber(),
                            year = sequenceOf(year, date).filterNotNull().firstNotNullOfOrNull { Regex("^\\d{4}").find(it)?.value }
                        )
                    }
                }.getOrNull()
            }.toMap()
        }
    }
    val tracks = remember(members, metadata) {
        members.sortedWith(compareBy<TrackEntity> { metadata[it.id]?.disc ?: 1 }
            .thenBy { metadata[it.id]?.number ?: Int.MAX_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }
    val first = tracks.firstOrNull()
    val year = tracks.firstNotNullOfOrNull { metadata[it.id]?.year }
    var playlistTrack by remember { mutableStateOf<TrackEntity?>(null) }
    val artwork = tracks.firstNotNullOfOrNull { it.artworkUri?.takeIf(String::isNotBlank) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addFiles(uris, album)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + LocalBottomBarPadding.current),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { TextButton(onClick = { vm.albumTarget.value = null }) { Text("← Назад") } }
        item {
            val duration = tracks.takeIf { it.all { track -> track.durationMs > 0 } }?.sumOf { it.durationMs } ?: 0
            AlbumHeader(album.title, album.artist, artwork, year, duration, first?.let { { vm.openArtist(it) } }) {
                if (tracks.isEmpty()) Text("В медиатеке нет треков этого альбома")
                Button(shapes = ButtonDefaults.shapes(), enabled = tracks.isNotEmpty(), onClick = { vm.player.playTracks(tracks, 0) }) {
                    Text("Воспроизвести альбом")
                }
                OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { filePicker.launch(arrayOf("audio/*", "application/ogg")) }) {
                    Text("Добавить треки в медиатеку")
                }
            }
        }
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            TrackRow(
                track = track,
                isCurrent = player.mediaId == track.id.toString(),
                menuItems = trackMenu(vm, track) { playlistTrack = it }.filterNot { it.label == "Альбом" },
                onClick = { vm.player.playTracks(tracks, index) }
            )
        }
    }
    playlistTrack?.let { track -> AddToPlaylistDialog(vm, track, onDismiss = { playlistTrack = null }) }
}

@Composable
private fun SoundCloudAlbumScreen(vm: AppViewModel, target: AppViewModel.AlbumTarget.SoundCloudAlbumTrack) {
    var attempt by remember { mutableIntStateOf(0) }
    val response by produceState<Result<SoundCloudRepository.Album?>?>(null, target, attempt) {
        value = null
        value = try {
            Result.success(SoundCloudRepository.album(target.id, target.permalink))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
    val album = response?.getOrNull()
    val library by vm.knownTracks.collectAsStateWithLifecycle()
    val playback by vm.player.state.collectAsStateWithLifecycle()
    val saved = remember(library) { library.filter { it.source == "soundcloud" }.associateBy { it.sourceId } }
    val tracks = album?.tracks.orEmpty()
    val playable = remember(tracks) { tracks.filter { it.full } }
    val unsaved = remember(tracks, saved) { tracks.filterNot { saved.containsKey(it.id.toString()) } }
    var playlistTrack by remember { mutableStateOf<SoundCloudTrack?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + LocalBottomBarPadding.current),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { TextButton(onClick = { vm.albumTarget.value = null }) { Text("← Назад") } }
        item {
            when {
                response == null -> CircularProgressIndicator()
                response?.isFailure == true -> {
                    Text("Не удалось загрузить альбом SoundCloud.")
                    TextButton(onClick = { attempt++ }) { Text("Повторить") }
                }
                album == null -> Text("Данные альбома отсутствуют.")
                else -> {
                    val artistTrack = tracks.firstOrNull { it.id == target.id } ?: tracks.firstOrNull()
                    AlbumHeader(album.title, album.artist, album.artworkUrl, album.releaseDate, album.durationMs,
                        artistTrack?.let { { vm.openArtist(it) } }) {
                        if (tracks.size < album.trackCount) Text("SoundCloud предоставил ${tracks.size} из ${album.trackCount} треков")
                        Button(shapes = ButtonDefaults.shapes(), enabled = playable.isNotEmpty(), onClick = { vm.playSoundCloud(playable, 0) }) {
                            Text("Воспроизвести альбом")
                        }
                        OutlinedButton(shapes = ButtonDefaults.shapes(), enabled = unsaved.isNotEmpty(), onClick = { vm.addSoundCloudToLibrary(unsaved) }) {
                            Text("Добавить треки в медиатеку")
                        }
                    }
                }
            }
        }
        itemsIndexed(tracks, key = { index, track -> "${track.id}:$index" }) { _, track ->
            val entry = saved[track.id.toString()]
            SoundCloudTrackRow(
                track = track,
                isCurrent = entry != null && playback.mediaId == entry.id.toString(),
                isFavorite = entry?.isFavorite == true,
                playEnabled = track.full,
                onPlay = { vm.playSoundCloud(playable, playable.indexOf(track)) },
                onFavorite = { vm.toggleSoundCloudFavorite(track) },
                onEnqueue = { vm.enqueueSoundCloud(track) },
                onPlayNext = { vm.playNextSoundCloud(track) },
                onAddToPlaylist = { playlistTrack = track },
                onArtist = { vm.openArtist(track) },
                onAddToLibrary = if (entry == null) ({ vm.addSoundCloudToLibrary(listOf(track)) }) else null
            )
        }
    }
    playlistTrack?.let { AddSoundCloudToPlaylistDialog(vm, it, onDismiss = { playlistTrack = null }) }
}

@Composable
private fun AlbumHeader(
    title: String,
    artist: String,
    artwork: String?,
    date: String?,
    duration: Long,
    onArtist: (() -> Unit)?,
    actions: @Composable () -> Unit
) {
    Card(shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val cover = Modifier.size(144.dp).clip(MaterialTheme.shapes.large)
            if (artwork?.startsWith("https://") == true) RemoteArtworkImage(artwork, "Обложка $title", cover)
            else ArtworkImage(artwork, "Обложка $title", cover)
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (artist.isNotBlank()) {
                if (onArtist != null) TextButton(onClick = onArtist) { Text(artist, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                else Text(artist, style = MaterialTheme.typography.titleMedium)
            }
            val details = listOfNotNull(date, formatTime(duration).takeIf { duration > 0 })
            if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            actions()
        }
    }
}

private data class AlbumTrackTags(val number: Int?, val disc: Int?, val year: String?)

private fun String?.tagNumber() = this?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it > 0 }
