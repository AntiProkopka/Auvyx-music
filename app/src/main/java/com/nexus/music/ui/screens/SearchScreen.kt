package com.nexus.music.ui.screens

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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.data.db.TrackEntity
import com.nexus.music.ui.components.AddToPlaylistDialog
import com.nexus.music.ui.components.EmptyHint
import com.nexus.music.ui.components.SoundCloudTrackRow
import com.nexus.music.ui.components.TrackRow
import com.nexus.music.ui.components.trackMenu

@Composable
fun SearchScreen(vm: AppViewModel) {
    var source by rememberSaveable { mutableStateOf("local") }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
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
            shape = RoundedCornerShape(24.dp),
            label = { Text("Название, исполнитель или альбом") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { vm.searchQuery.value = "" }) {
                    Icon(Icons.Default.Clear, contentDescription = "Очистить")
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
                itemsIndexed(results, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = current.mediaId == track.id.toString(),
                        menuItems = trackMenu(vm, track) { pending = it },
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
    val favorites by vm.soundCloudFavorites.collectAsStateWithLifecycle()
    val current by vm.player.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("SoundCloud", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    "Публичный каталог SoundCloud в поиске AUVYX. Часть треков может быть недоступна для воспроизведения.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
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
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                    Icon(Icons.Default.Clear, contentDescription = "Очистить")
                }
            }
        )
        Button(
            onClick = { vm.searchSoundCloud(query) },
            enabled = query.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Icon(Icons.Default.Search, contentDescription = null)
            Text(if (loading) "Ищем…" else "Найти")
        }

        if (error != null) {
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp))
        } else if (results.isEmpty() && !loading) {
            EmptyHint("Введи запрос, чтобы искать музыку в SoundCloud.")
        } else if (results.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(results, key = { _, track -> track.id }) { index, track ->
                    SoundCloudTrackRow(
                        track = track,
                        isCurrent = current.mediaId == "soundcloud:${track.id}",
                        isFavorite = favorites.any { it.trackId == track.id },
                        onPlay = { vm.playSoundCloud(results, index) },
                        onFavorite = { vm.toggleSoundCloudFavorite(track) },
                        onEnqueue = { vm.enqueueSoundCloud(track) }
                    )
                }
            }
        }
    }
}
