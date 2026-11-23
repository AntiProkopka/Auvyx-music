package com.nexus.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.nexus.music.AppViewModel
import com.nexus.music.ui.components.EmptyHint
import com.nexus.music.ui.components.MenuAction
import com.nexus.music.ui.components.NameDialog
import com.nexus.music.ui.components.TrackRow

@Composable
fun PlaylistScreen(vm: AppViewModel, playlistId: Long, onBack: () -> Unit) {
    val playlistFlow = remember(playlistId) { vm.playlist(playlistId) }
    val tracksFlow = remember(playlistId) { vm.playlistTracks(playlistId) }
    val playlist by playlistFlow.collectAsStateWithLifecycle(initialValue = null)
    val tracks by tracksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val current by vm.player.state.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад") }
            Text(
                text = playlist?.name ?: "",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, contentDescription = "Переименовать") }
            IconButton(onClick = { vm.deletePlaylist(playlistId); onBack() }) { Icon(Icons.Default.Delete, contentDescription = "Удалить плейлист") }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ПЛЕЙЛИСТ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(playlist?.name ?: "Моя музыка", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${tracks.size} треков", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(modifier = Modifier.weight(1f), enabled = tracks.isNotEmpty(), onClick = { vm.player.playTracks(tracks, 0) }) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Играть", maxLines = 1)
            }
            FilledTonalButton(modifier = Modifier.weight(1f), enabled = tracks.isNotEmpty(), onClick = { vm.player.playTracks(tracks, 0, shuffle = true) }) {
                Icon(Icons.Default.Shuffle, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Вперемешку", maxLines = 1)
            }
        }

        if (tracks.isEmpty()) {
            EmptyHint("В плейлисте пока нет треков. Добавьте их через меню «⋮» у трека в медиатеке.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = current.mediaId == track.id.toString(),
                        menuItems = listOf(
                            MenuAction(if (track.isFavorite) "Убрать из избранного" else "В избранное") { vm.toggleFavorite(track) },
                            MenuAction("Играть следующим") { vm.player.playNext(track) },
                            MenuAction("В конец очереди") { vm.player.enqueue(track) },
                            MenuAction("Убрать из плейлиста") { vm.removeFromPlaylist(playlistId, track.id) }
                        ),
                        onClick = { vm.player.playTracks(tracks, index) }
                    )
                }
            }
        }
    }

    if (renaming) {
        NameDialog(
            title = "Переименовать плейлист",
            initial = playlist?.name ?: "",
            confirmLabel = "Сохранить",
            onConfirm = { vm.renamePlaylist(playlistId, it); renaming = false },
            onDismiss = { renaming = false }
        )
    }
}
