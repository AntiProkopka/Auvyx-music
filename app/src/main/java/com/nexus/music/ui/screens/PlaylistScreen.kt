package com.nexus.music.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.R
import com.nexus.music.ui.theme.*
import com.nexus.music.AppViewModel
import com.nexus.music.ui.LocalBottomBarPadding
import com.nexus.music.ui.components.EmptyHint
import com.nexus.music.ui.components.MenuAction
import com.nexus.music.ui.components.NameDialog
import com.nexus.music.ui.components.TrackRow
import com.nexus.music.ui.components.SmartCollectionEditor
import com.nexus.music.data.db.TrackEntity

@Composable
fun PlaylistScreen(vm: AppViewModel, playlistId: Long, onBack: () -> Unit) {
    val playlistFlow = remember(playlistId) { vm.playlist(playlistId) }
    val tracksFlow = remember(playlistId) { vm.playlistTracks(playlistId) }
    val playlist by playlistFlow.collectAsStateWithLifecycle(initialValue = null)
    val tracks by tracksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var renaming by remember { mutableStateOf(false) }
    PlaylistContent(vm, playlist?.name ?: "", tracks, null, false, onBack,
        edit = { renaming = true }, delete = { vm.deletePlaylist(playlistId); onBack() },
        removeTrack = { vm.removeFromPlaylist(playlistId, it.id) })
    if (renaming) {
        NameDialog(title = "Переименовать плейлист", initial = playlist?.name.orEmpty(), confirmLabel = "Сохранить",
            onConfirm = { vm.renamePlaylist(playlistId, it); renaming = false }, onDismiss = { renaming = false })
    }
}

@Composable
internal fun SmartPlaylistScreen(vm: AppViewModel, id: String, onBack: () -> Unit) {
    val playlist by remember(id) { vm.smartCollections.collection(id) }.collectAsStateWithLifecycle(null)
    val tracks by remember(id) { vm.smartPlaylistTracks(id) }.collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val selected = playlist ?: return
    PlaylistContent(vm, selected.name, tracks, selected.coverUri, true, onBack,
        edit = { editing = true }, delete = { deleting = true })
    if (editing) SmartCollectionEditor(vm, selected, close = { editing = false }, saved = { editing = false })
    if (deleting) androidx.compose.material3.AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Удалить «${selected.name}»?") },
        text = { Text("Треки останутся в медиатеке.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = {
            vm.saveTrackTools { vm.smartCollections.delete(selected); deleting = false; onBack() }
        }) { Text("Удалить") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { deleting = false }) { Text("Отмена") } }
    )
}

@Composable
private fun PlaylistContent(
    vm: AppViewModel,
    name: String,
    tracks: List<TrackEntity>,
    cover: String?,
    smart: Boolean,
    onBack: () -> Unit,
    edit: () -> Unit,
    delete: () -> Unit,
    removeTrack: ((TrackEntity) -> Unit)? = null
) {
    val current by vm.player.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { com.nexus.music.ui.components.BrandIcon(R.drawable.glass_back, Icons.Default.ArrowBack, contentDescription = "Назад") }
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = edit) { Icon(Icons.Default.Edit, contentDescription = if (smart) "Изменить коллекцию" else "Переименовать") }
            IconButton(onClick = delete) { Icon(Icons.Default.Delete, contentDescription = "Удалить плейлист") }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).frosted(RoundedCornerShape(26.dp)),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = if (isGlass) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val artwork = cover ?: tracks.firstOrNull()?.artworkUri
                if ((isGlass || smart) && artwork != null) ArtTile(artwork, null, Modifier.padding(start = 16.dp).size(76.dp))
            Column(modifier = Modifier.weight(1f).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (smart) "УМНАЯ КОЛЛЕКЦИЯ" else "ПЛЕЙЛИСТ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(name.ifBlank { "Моя музыка" }, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${tracks.size} треков", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f), enabled = tracks.isNotEmpty(), onClick = { vm.player.playTracks(tracks, 0) }) {
                com.nexus.music.ui.components.BrandIcon(R.drawable.glass_play, Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Играть", maxLines = 1)
            }
            FilledTonalButton(shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f), enabled = tracks.isNotEmpty(), onClick = { vm.player.playTracks(tracks, 0, shuffle = true) }) {
                com.nexus.music.ui.components.BrandIcon(R.drawable.glass_shuffle, Icons.Default.Shuffle, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Вперемешку", maxLines = 1)
            }
        }

        if (tracks.isEmpty()) {
            EmptyHint(if (smart) "Ни один трек не подходит под правила коллекции."
                else "В плейлисте пока нет треков. Добавьте их через меню «⋮» у трека в медиатеке.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = LocalBottomBarPadding.current)
            ) {
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = current.mediaId == track.id.toString(),
                        menuItems = listOf(
                            MenuAction(if (track.isFavorite) "Убрать из избранного" else "В избранное") { vm.toggleFavorite(track) },
                            MenuAction("Играть следующим") { vm.player.playNext(track) },
                            MenuAction("В конец очереди") { vm.player.enqueue(track) }
                        ) + listOfNotNull(removeTrack?.let { remove -> MenuAction("Убрать из плейлиста") { remove(track) } }),
                        onClick = { vm.player.playTracks(tracks, index) }
                    )
                }
            }
        }
    }
}
