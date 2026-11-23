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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.nexus.music.data.soundcloud.SoundCloudTrack
import com.nexus.music.ui.components.AddSoundCloudToPlaylistDialog
import com.nexus.music.ui.components.AddToPlaylistDialog
import com.nexus.music.ui.components.EmptyHint
import com.nexus.music.ui.components.SoundCloudTrackRow
import com.nexus.music.ui.components.TrackRow
import com.nexus.music.ui.components.trackMenu
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import com.nexus.music.data.catalog.CatalogKind
import com.nexus.music.data.catalog.CatalogEntry
import com.nexus.music.data.catalog.YouTubeMusicCatalog
import com.nexus.music.ui.components.RemoteArtworkImage

@Composable
fun SearchScreen(vm: AppViewModel) {
    var source by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }

    val showYoutube = source == "all" || source == "youtube"
    val spotifySession = remember { SpotifySession() }
    val localResults by vm.searchResults.collectAsStateWithLifecycle()
    val cloudResults by vm.soundCloudResults.collectAsStateWithLifecycle()
    val cloudLoading by vm.soundCloudLoading.collectAsStateWithLifecycle()
    val cloudError by vm.soundCloudError.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val knownTracks by vm.knownTracks.collectAsStateWithLifecycle()
    val current by vm.player.state.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<TrackEntity?>(null) }
    var pendingSoundCloud by remember { mutableStateOf<SoundCloudTrack?>(null) }

    val showLocal = source == "all" || source == "local"
    val showSoundCloud = source == "all" || source == "soundcloud"

    LaunchedEffect(query, source) {
        vm.searchQuery.value = if (showLocal) query else ""
        if (showSoundCloud) {
            if (query.isBlank()) {
                vm.searchSoundCloud("")
            } else {
                delay(450)
                vm.searchSoundCloud(query)
            }
        } else {
            vm.searchSoundCloud("")
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = source == "all", onClick = { source = "all" }, label = { Text("Все") })
            FilterChip(selected = source == "local", onClick = { source = "local" }, label = { Text("На устройстве") })
            FilterChip(selected = source == "spotify", onClick = { source = "spotify" }, label = { Text("Spotify") })
            FilterChip(selected = source == "youtube", onClick = { source = "youtube" }, label = { Text("YouTube Music") })
            FilterChip(selected = source == "soundcloud", onClick = { source = "soundcloud" }, label = { Text("SoundCloud") })
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            label = { Text("Трек или исполнитель") },
            placeholder = { Text("Название композиции или артиста") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Очистить")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (source == "spotify") {
            SpotifyScreen(spotifySession, query) { source = "all" }
            return@Column
        }
        val youtube = catalogResults(query, showYoutube)
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (query.isNotBlank()) {
                if (showYoutube) catalogRows(youtube, source == "all")
            }
            if (query.isBlank()) {
                item { EmptyHint("Введи запрос. Поиск одновременно проверит выбранные каталоги.") }
            } else {
                if (showLocal) {
                    item {
                        Text(
                            "На устройстве · ${localResults.size}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp)
                        )
                    }
                    if (localResults.isEmpty()) {
                        item {
                            Text(
                                "В локальной медиатеке совпадений нет.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                            )
                        }
                    } else {
                        itemsIndexed(localResults, key = { _, track -> "local-${track.id}" }) { index, track ->
                            TrackRow(
                                track = track,
                                isCurrent = current.mediaId == track.id.toString(),
                                menuItems = trackMenu(vm, track) { pending = it },
                                onClick = { vm.player.playTracks(localResults, index) }
                            )
                        }
                    }
                }

                if (showSoundCloud) {
                    item {
                        Text(
                            "SoundCloud${if (cloudLoading) " · поиск…" else if (cloudError == null) " · ${cloudResults.size}" else ""}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp)
                        )
                    }
                    if (cloudLoading && cloudResults.isEmpty()) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    } else if (cloudError != null && cloudResults.isEmpty()) {
                        item {
                            Text(
                                cloudError.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                            )
                        }
                    } else {
                        itemsIndexed(cloudResults, key = { _, track -> "soundcloud-${track.id}" }) { index, track ->
                            SoundCloudTrackRow(
                                track = track,
                                isCurrent = knownTracks.any {
                                    it.source == "soundcloud" && it.sourceId == track.id.toString() &&
                                        current.mediaId == it.id.toString()
                                },
                                isFavorite = favorites.any {
                                    it.source == "soundcloud" && it.sourceId == track.id.toString()
                                },
                                onPlay = { vm.playSoundCloud(cloudResults, index) },
                                onFavorite = { vm.toggleSoundCloudFavorite(track) },
                                onEnqueue = { vm.enqueueSoundCloud(track) },
                                onArtist = { vm.openArtist(track) },
                                onPlayNext = { vm.playNextSoundCloud(track) },
                                onAddToPlaylist = { pendingSoundCloud = track }
                            )
                        }
                    }
                }
            }
        }
    }

    pending?.let { track -> AddToPlaylistDialog(vm, track) { pending = null } }
    pendingSoundCloud?.let { track ->
        AddSoundCloudToPlaylistDialog(vm, track) { pendingSoundCloud = null }
    }
}
