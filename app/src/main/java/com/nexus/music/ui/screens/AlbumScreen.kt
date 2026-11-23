package com.nexus.music.ui.screens

import android.media.MediaMetadataRetriever
import android.net.Uri
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
    val tracks = remember(library, album) { library.filter(album::contains) }
    val first = tracks.firstOrNull()
    val context = LocalContext.current
    val year by produceState<String?>(null, first?.uri) {
        value = withContext(Dispatchers.IO) {
            first?.let { track ->
                runCatching {
                    MediaMetadataRetriever().use { retriever ->
                        val original = Uri.parse(track.uri)
                        val copy = OfflineAudio.file(context, track.uri)
                        if (copy.isFile) retriever.setDataSource(copy.absolutePath)
                        else retriever.setDataSource(context, original)
                        val metadata = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                            ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                        metadata?.let { Regex("^\\d{4}").find(it)?.value }
                    }
                }.getOrNull()
            }
        }
    }
    var playlistTrack by remember { mutableStateOf<TrackEntity?>(null) }
    val artwork = tracks.firstNotNullOfOrNull { it.artworkUri?.takeIf(String::isNotBlank) }

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
                    val duration = tracks.sumOf { it.durationMs }
                    val details = listOfNotNull(year, formatTime(duration).takeIf { duration > 0 })
                    if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                    Text(if (tracks.isEmpty()) "В медиатеке нет треков этого альбома" else "Треки уже в медиатеке")
                    Button(enabled = tracks.isNotEmpty(), onClick = { vm.player.playTracks(tracks, 0) }) {
                        Text("Воспроизвести альбом")
                    }
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
