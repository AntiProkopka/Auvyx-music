package com.nexus.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.data.db.TrackEntity
import com.nexus.music.ui.components.AddToPlaylistDialog
import com.nexus.music.ui.components.SectionTitle
import com.nexus.music.ui.components.TrackRow
import com.nexus.music.ui.components.trackMenu
import java.util.Calendar

@Composable
fun HomeScreen(vm: AppViewModel) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val current by vm.player.state.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<TrackEntity?>(null) }

    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> "Доброе утро"
        in 12..17 -> "Добрый день"
        in 18..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(greeting, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Твоя музыка. В одном месте.", style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        "Auralis объединяет локальную коллекцию и музыкальные источники в одном интерфейсе.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = { if (tracks.isNotEmpty()) vm.player.playTracks(tracks, 0) }, enabled = tracks.isNotEmpty()) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text("Слушать")
                        }
                        Text("${tracks.size} треков", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.LibraryMusic, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Text("Медиатека пока пуста", style = MaterialTheme.typography.titleMedium)
                        Text("Добавь аудиофайлы или папку с музыкой во вкладке «Медиатека».",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionTitle("Недавно прослушанное") }
            itemsIndexed(recent.take(10), key = { _, t -> "r${t.id}" }) { index, t ->
                TrackRow(
                    track = t,
                    isCurrent = current.mediaId == t.id.toString(),
                    menuItems = trackMenu(vm, t) { pending = it },
                    onClick = { vm.player.playTracks(recent, index) }
                )
            }
        }
        if (tracks.isNotEmpty()) {
            item { SectionTitle("Твоя коллекция") }
            itemsIndexed(tracks.take(10), key = { _, t -> "a${t.id}" }) { index, t ->
                TrackRow(
                    track = t,
                    isCurrent = current.mediaId == t.id.toString(),
                    menuItems = trackMenu(vm, t) { pending = it },
                    onClick = { vm.player.playTracks(tracks, index) }
                )
            }
        }
    }

    pending?.let { AddToPlaylistDialog(vm, it) { pending = null } }
}
