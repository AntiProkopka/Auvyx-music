package com.nexus.music.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material3.TextButton
import com.nexus.music.R
import com.nexus.music.ui.theme.*
import com.nexus.music.data.soundcloud.SoundCloudRepository
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import com.nexus.music.ui.components.ExpressiveFilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.ui.LocalBottomBarPadding
import com.nexus.music.data.db.TrackEntity
import com.nexus.music.data.soundcloud.SoundCloudTrack
import com.nexus.music.ui.components.AddSoundCloudToPlaylistDialog
import com.nexus.music.ui.components.AddToPlaylistDialog
import com.nexus.music.ui.components.EmptyHint
import com.nexus.music.ui.components.SoundCloudTrackRow
import com.nexus.music.ui.components.TrackRow
import com.nexus.music.ui.components.trackMenu
import kotlinx.coroutines.delay
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

@Composable
fun SearchScreen(vm: AppViewModel) {
    var savedSource by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }

    val source = savedSource.takeIf { it == "local" || it == "soundcloud" } ?: "all"
    val localResults by vm.searchResults.collectAsStateWithLifecycle()
    val cloudResults by vm.soundCloudResults.collectAsStateWithLifecycle()
    val cloudLoading by vm.soundCloudLoading.collectAsStateWithLifecycle()
    val cloudError by vm.soundCloudError.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val knownTracks by vm.knownTracks.collectAsStateWithLifecycle()
    val current by vm.player.state.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<TrackEntity?>(null) }
    var pendingSoundCloud by remember { mutableStateOf<SoundCloudTrack?>(null) }

    val latestQuery by androidx.compose.runtime.rememberUpdatedState(query)
    androidx.compose.runtime.DisposableEffect(vm) {
        onDispose { if (latestQuery.isNotBlank()) vm.rememberSearch(latestQuery) }
    }
    val glass = isGlass
    var localEnabled by rememberSaveable { mutableStateOf(true) }
    var cloudEnabled by rememberSaveable { mutableStateOf(true) }
    val queries by vm.queries.collectAsStateWithLifecycle()
    val opened by vm.searchTracks.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val showLocal = if (glass) localEnabled else source == "all" || source == "local"
    val showSoundCloud = if (glass) cloudEnabled else source == "all" || source == "soundcloud"

    var artists by remember { mutableStateOf(emptyList<SoundCloudRepository.Artist>()) }
    var albums by remember { mutableStateOf(emptyList<SoundCloudRepository.Release>()) }
    var artistError by remember { mutableStateOf(false) }
    var albumError by remember { mutableStateOf(false) }
    LaunchedEffect(query, showSoundCloud, glass) {
        artists = emptyList()
        artistError = false
        if (glass && showSoundCloud && query.isNotBlank()) {
            delay(450)
            try { artists = SoundCloudRepository.searchArtists(query.trim()) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { artistError = true }
        }
    }
    LaunchedEffect(query, showSoundCloud, glass) {
        albums = emptyList()
        albumError = false
        if (glass && showSoundCloud && query.isNotBlank()) {
            delay(450)
            try { albums = SoundCloudRepository.searchReleases(query.trim()) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { albumError = true }
        }
    }
    LaunchedEffect(query, showLocal, showSoundCloud) {
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
            if (glass) {
                SourceToggle(localEnabled, "На устройстве", R.drawable.glass_phone) { localEnabled = it }
                SourceToggle(cloudEnabled, "SoundCloud", R.drawable.platform_soundcloud, logo = true) { cloudEnabled = it }
            } else {
            ExpressiveFilterChip(selected = source == "all", onClick = { savedSource = "all" }, label = { Text("Все") })
            ExpressiveFilterChip(selected = source == "local", onClick = { savedSource = "local" }, label = { Text("На устройстве") })
            ExpressiveFilterChip(selected = source == "soundcloud", onClick = { savedSource = "soundcloud" }, label = { Text("SoundCloud") })
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(160) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.rememberSearch(query); keyboard?.hide() }),
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            ),
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).frosted(MaterialTheme.shapes.extraLarge)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("search-results"),
            contentPadding = PaddingValues(bottom = LocalBottomBarPadding.current)
        ) {
            if (query.isBlank()) {
                if (glass) {
                    item { SearchHeading("Последние запросы") }
                    itemsIndexed(queries) { _, term -> TextButton(onClick = { query = term; vm.rememberSearch(term) }) { Text(term) } }
                    item { SearchHeading("Недавно открытые песни") }
                    itemsIndexed(opened) { _, track ->
                        TrackRow(track, current.mediaId == track.id.toString(), trackMenu(vm, track) { pending = it }) {
                            vm.playTrack(track)
                            vm.rememberSearch("", track.uri)
                        }
                    }
                    if (queries.isNotEmpty() || opened.isNotEmpty()) item {
                        TextButton(onClick = vm::clearSearch) { Text("Очистить историю поиска") }
                    }
                } else item { EmptyHint("Введи запрос. Поиск одновременно проверит выбранные каталоги.") }
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
                        itemsIndexed(localResults, key = { _, track -> "local-${track.id}" }) { _, track ->
                            TrackRow(
                                track = track,
                                isCurrent = current.mediaId == track.id.toString(),
                                menuItems = trackMenu(vm, track) { pending = it },
                                onClick = { vm.playTrack(track); vm.rememberSearch(query, track.uri) }
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
                        itemsIndexed(cloudResults, key = { _, track -> "soundcloud-${track.id}" }) { _, track ->
                            SoundCloudTrackRow(
                                track = track,
                                isCurrent = knownTracks.any {
                                    it.source == "soundcloud" && it.sourceId == track.id.toString() &&
                                        current.mediaId == it.id.toString()
                                },
                                isFavorite = favorites.any {
                                    it.source == "soundcloud" && it.sourceId == track.id.toString()
                                },
                                playEnabled = track.full,
                                onPlay = { vm.playTrack(track, query) },
                                onFavorite = { vm.toggleSoundCloudFavorite(track) },
                                onEnqueue = { vm.enqueueSoundCloud(track) },
                                onArtist = { vm.openArtist(track) },
                                onAlbum = track.albumTitle?.let { { vm.openAlbum(track) } },
                                onPlayNext = { vm.playNextSoundCloud(track) },
                                onAddToPlaylist = { pendingSoundCloud = track }
                            )
                        }
                    }
                }
                if (glass) {
                    val localArtists = if (showLocal) localResults.filter { it.artist.contains(query.trim(), true) }.distinctBy { it.artist.lowercase() } else emptyList()
                    val localAlbums = if (showLocal) localResults.filter { it.album.contains(query.trim(), true) && AppViewModel.AlbumTarget.from(it) != null }
                        .distinctBy { it.album.lowercase() to it.artist.lowercase() } else emptyList()
                    if (localArtists.isNotEmpty() || artists.isNotEmpty() || artistError) item { SearchHeading("Исполнители") }
                    itemsIndexed(localArtists) { _, track -> TextButton(onClick = { vm.rememberSearch(query); vm.openArtist(track) }) { Text(track.artist) } }
                    itemsIndexed(artists) { _, artist -> TextButton(onClick = {
                        vm.rememberSearch(query)
                        vm.artistTarget.value = AppViewModel.ArtistTarget(artist.name, true, artist.id, null)
                    }) { Text(artist.name) } }
                    if (artistError) item { Text("Поиск исполнителей SoundCloud недоступен", Modifier.padding(horizontal = 16.dp)) }
                    if (localAlbums.isNotEmpty() || albums.isNotEmpty() || albumError) item { SearchHeading("Альбомы") }
                    itemsIndexed(localAlbums) { _, track -> TextButton(onClick = { vm.rememberSearch(query); vm.openAlbum(track) }) { Text(track.album) } }
                    itemsIndexed(albums) { _, album -> TextButton(onClick = { vm.rememberSearch(query); vm.openRelease(album) }) { Text(album.title) } }
                    if (albumError) item { Text("Поиск альбомов SoundCloud недоступен", Modifier.padding(horizontal = 16.dp)) }
                    if (!showLocal && !showSoundCloud) item { EmptyHint("Выбери источник поиска") }
                }
            }
        }
    }

    pending?.let { track -> AddToPlaylistDialog(vm, track) { pending = null } }
    pendingSoundCloud?.let { track ->
        AddSoundCloudToPlaylistDialog(vm, track) { pendingSoundCloud = null }
    }
}

@Composable
private fun SearchHeading(title: String) {
    Text(title, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun SourceToggle(active: Boolean, label: String, icon: Int, logo: Boolean = false, change: (Boolean) -> Unit) {
    Box(Modifier.size(54.dp, 46.dp).frosted(MaterialTheme.shapes.medium)
        .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = .23f) else Color.Transparent)
        .toggleable(active, role = Role.Checkbox, onValueChange = change), contentAlignment = Alignment.Center) {
        if (logo) Image(painterResource(icon), label, Modifier.size(30.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xffff5600)))
        else Icon(painterResource(icon), label, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
