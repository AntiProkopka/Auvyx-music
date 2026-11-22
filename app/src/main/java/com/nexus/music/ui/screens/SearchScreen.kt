package com.nexus.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.data.db.TrackEntity
import com.nexus.music.data.soundcloud.SoundCloudTrack
import com.nexus.music.ui.components.AddToPlaylistDialog
import com.nexus.music.ui.components.RemoteArtworkImage
import com.nexus.music.ui.components.EmptyHint
import com.nexus.music.ui.components.TrackRow
import com.nexus.music.ui.components.trackMenu

@Composable
fun SearchScreen(vm: AppViewModel) {
    var source by rememberSaveable { mutableStateOf("local") }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = source == "local", onClick = { source = "local" }, label = { Text("На устройстве") })
            FilterChip(selected = source == "soundcloud", onClick = { source = "soundcloud" }, label = { Text("SoundCloud") })
        }
        if (source == "local") LocalLibrarySearch(vm) else SoundCloudSearch(vm)
    }
}

@Composable
private fun LocalLibrarySearch(vm: AppViewModel) {
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val current by vm.player.state.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<TrackEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.searchQuery.value = it },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            label = { Text("Название, исполнитель или альбом") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.searchQuery.value = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Очистить")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (query.isBlank()) {
            EmptyHint("Поиск по трекам, которые добавлены в локальную медиатеку.")
        } else if (results.isEmpty()) {
            EmptyHint("По этому запросу ничего не найдено.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(results, key = { _, t -> t.id }) { index, t ->
                    TrackRow(
                        track = t,
                        isCurrent = current.mediaId == t.id.toString(),
                        menuItems = trackMenu(vm, t) { pending = it },
                        onClick = { vm.player.playTracks(results, index) }
                    )
                }
            }
        }
    }
    pending?.let { AddToPlaylistDialog(vm, it) { pending = null } }
}

@Composable
private fun SoundCloudSearch(vm: AppViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    val results by vm.soundCloudResults.collectAsStateWithLifecycle()
    val loading by vm.soundCloudLoading.collectAsStateWithLifecycle()
    val error by vm.soundCloudError.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("SoundCloud", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    "Ищи публичные треки и запускай их в общем плеере AUVYX. Доступность зависит от ограничений SoundCloud.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            label = { Text("Трек или исполнитель") },
            placeholder = { Text("Например, название композиции") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Очистить")
                    }
                }
            }
        )
        Button(
            onClick = { vm.searchSoundCloud(query) },
            enabled = query.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Default.Search, contentDescription = null)
            }
            Text(if (loading) "Ищем…" else "Найти")
        }

        if (error != null) {
            Text(
                text = error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        } else if (results.isEmpty() && !loading) {
            EmptyHint("Введи запрос, чтобы искать музыку в SoundCloud.")
        } else if (results.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(results, key = { _, track -> track.id }) { index, track ->
                    SoundCloudTrackRow(
                        track = track,
                        onPlay = { vm.playSoundCloud(results, index) },
                        onEnqueue = { vm.enqueueSoundCloud(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SoundCloudTrackRow(
    track: SoundCloudTrack,
    onPlay: () -> Unit,
    onEnqueue: () -> Unit
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay),
        leadingContent = {
            if (track.artworkUrl != null) {
                RemoteArtworkImage(
                    imageUrl = track.artworkUrl,
                    contentDescription = "Обложка ${track.title}",
                    modifier = Modifier.size(54.dp).clip(RoundedCornerShape(12.dp))
                )
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(42.dp))
            }
        },
        headlineContent = {
            Text(track.title, maxLines = 1, style = MaterialTheme.typography.titleSmall)
        },
        supportingContent = {
            Text(
                "${track.artist} · ${formatSoundCloudDuration(track.durationMs)}",
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            IconButton(onClick = onEnqueue) {
                Icon(Icons.Default.Add, contentDescription = "Добавить в очередь")
            }
        }
    )
}

private fun formatSoundCloudDuration(ms: Long): String {
    val seconds = (ms / 1000L).coerceAtLeast(0L)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
