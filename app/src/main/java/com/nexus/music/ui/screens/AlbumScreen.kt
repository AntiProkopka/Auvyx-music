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
import com.nexus.music.data.db.TrackEntity
import com.nexus.music.data.local.OfflineAudio
import com.nexus.music.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AlbumScreen(vm: AppViewModel, album: AppViewModel.AlbumTarget) {
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { TextButton(onClick = { vm.albumTarget.value = null }) { Text("← Назад") } }
        item {
            Card(shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArtworkImage(artwork, "Обложка ${album.title}", Modifier.size(144.dp).clip(MaterialTheme.shapes.large))
                    Text(album.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (first != null && album.artist.isNotBlank()) {
                        TextButton(onClick = { vm.openArtist(first) }) {
                            Text(album.artist, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    val duration = tracks.takeIf { it.all { track -> track.durationMs > 0 } }?.sumOf { it.durationMs } ?: 0
                    val details = listOfNotNull(year, formatTime(duration).takeIf { duration > 0 })
                    if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                    if (tracks.isEmpty()) Text("В медиатеке нет треков этого альбома")
                    Button(
                        shapes = ButtonDefaults.shapes(),
                        enabled = tracks.isNotEmpty(),
                        onClick = { vm.player.playTracks(tracks, 0) }
                    ) {
                        Text("Воспроизвести альбом")
                    }
                    OutlinedButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = { filePicker.launch(arrayOf("audio/*", "application/ogg")) }
                    ) { Text("Добавить треки в медиатеку") }
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

private data class AlbumTrackTags(val number: Int?, val disc: Int?, val year: String?)

private fun String?.tagNumber() = this?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it > 0 }
