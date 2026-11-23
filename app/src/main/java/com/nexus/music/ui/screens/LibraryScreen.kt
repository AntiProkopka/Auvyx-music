package com.nexus.music.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.data.db.TrackEntity
import com.nexus.music.ui.components.AddToPlaylistDialog
import com.nexus.music.ui.components.EmptyHint
import com.nexus.music.ui.components.NameDialog
import com.nexus.music.ui.components.TrackRow
import com.nexus.music.ui.components.trackMenu

@Composable
fun LibraryScreen(vm: AppViewModel, onOpenPlaylist: (Long) -> Unit) {
    val catalogPages by vm.catalogPages.collectAsStateWithLifecycle()
    var catalogUrl by rememberSaveable { mutableStateOf<String?>(null) }
    catalogUrl?.let { url ->
        CatalogPage(vm, url, catalogPages[url].orEmpty()) { catalogUrl = null }
        return
    }
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val known by vm.knownTracks.collectAsStateWithLifecycle()
    val collection = remember(known) { known.filter { it.source in setOf("local","soundcloud") } }
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val sort by vm.sortMode.collectAsStateWithLifecycle()
    val current by vm.player.state.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<TrackEntity?>(null) }
    var creatingPlaylist by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addFiles(uris)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }

    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val compactTools = config.screenWidthDp / config.fontScale < 350f
    val sortedTracks = remember(collection, sort) {
        when (sort) {
            "title" -> collection.sortedBy { it.title.lowercase() }
            "artist" -> collection.sortedBy { it.artist.lowercase() }
            else -> collection
        }
    }
    val sortLabel = when (sort) {
        "title" -> "По названию"
        "artist" -> "По исполнителю"
        else -> "Недавно добавленные"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 10.dp),
                    onClick = { filePicker.launch(arrayOf("audio/*", "application/ogg", "application/octet-stream")) }
                ) {
                    if (!compactTools) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("Файлы", maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                }
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 10.dp),
                    onClick = { folderPicker.launch(null) }
                ) {
                    if (!compactTools) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("Папка", maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                }
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 10.dp),
                    onClick = { vm.refreshLibrary() }
                ) {
                    if (!compactTools) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("Скан", maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                }
            }
        }

        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Треки", "Избранное", "Плейлисты", "История", "Каталоги", "Офлайн", "Исполнители").forEachIndexed { index, label ->
                FilterChip(
                    selected = tab == index,
                    onClick = { tab = index },
                    label = { Text(label, maxLines = 1) }
                )
            }
        }

        when (tab) {
            6 -> LazyColumn {
                val artists = collection.distinctBy { it.source to (it.artistId ?: it.artist.trim().lowercase()) }.sortedBy { it.artist.lowercase() }
                items(artists) { t -> TextButton(onClick = { vm.openArtist(t) }) { Text(t.artist + if (t.source == "soundcloud") " · SoundCloud" else " · На устройстве") } }
            }
            5 -> {
                val transfers by vm.offlineTransfers.collectAsStateWithLifecycle()
                val copies = tracks.filter { it.downloadedPath != null }
                Text(android.text.format.Formatter.formatFileSize(androidx.compose.ui.platform.LocalContext.current,
                    copies.sumOf { java.io.File(it.downloadedPath!!).length() }), Modifier.padding(16.dp))
                transfers.forEach { (id, transfer) ->
                    Text("${tracks.find { it.id == id }?.title.orEmpty()} · ${transfer.bytes / 1024} КБ")
                    if (transfer.total > 0) LinearProgressIndicator(progress = { transfer.bytes.toFloat() / transfer.total })
                    else LinearProgressIndicator()
                    TextButton(onClick = { tracks.find { it.id == id }?.let(vm::cancelOffline) }) { Text("Отменить") }
                }
                if (copies.isEmpty() && transfers.isEmpty()) EmptyHint("Выбери «Сохранить офлайн» в меню локального трека.")
                else TrackList(vm, copies, current.mediaId) { pending = it }
            }
            4 -> {
                if (catalogPages.isEmpty()) EmptyHint("Здесь появятся закладки из предыдущей версии.")
                else LazyColumn {
                    items(catalogPages.entries.sortedBy { it.value.lowercase() }, key = { it.key }) { entry ->
                        Column(Modifier.fillMaxWidth().clickable {
                            catalogUrl = entry.key
                        }.padding(16.dp)) {
                            Text(entry.value)
                            Text(if (android.net.Uri.parse(entry.key).host == "open.spotify.com") "Spotify" else "YouTube Music",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            0 -> {
                if (collection.isEmpty()) {
                    EmptyHint("Пока пусто. Нажмите «Файлы» или «Папка», чтобы добавить музыку с устройства.")
                } else {
                    Box(modifier = Modifier.padding(start = 8.dp)) {
                        TextButton(onClick = { sortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("${collection.size} · $sortLabel")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf("added" to "Недавно добавленные", "title" to "По названию", "artist" to "По исполнителю").forEach { (key, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    sortMenu = false
                                    vm.setSort(key)
                                })
                            }
                        }
                    }
                    TrackList(vm, sortedTracks, current.mediaId, onAddToPlaylist = { pending = it })
                }
            }
            1 -> {
                if (favorites.isEmpty()) {
                    EmptyHint("Нажимай на сердечко у локальных треков или SoundCloud, чтобы сохранить их здесь.")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(favorites, key = { "${it.source}-${it.id}" }) { track ->
                            TrackRow(
                                track = track,
                                isCurrent = current.mediaId == track.id.toString(),
                                menuItems = trackMenu(vm, track) { pending = it },
                                onClick = { vm.player.playTracks(listOf(track), 0) }
                            )
                        }
                    }
                }
            }
            2 -> {
                TextButton(onClick = { creatingPlaylist = true }, modifier = Modifier.padding(start = 8.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Новый плейлист")
                }
                if (playlists.isEmpty()) {
                    EmptyHint("Плейлистов пока нет.")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(playlists, key = { it.id }) { playlist ->
                            ListItem(
                                modifier = Modifier.fillMaxWidth().clickable { onOpenPlaylist(playlist.id) },
                                leadingContent = { Icon(Icons.Default.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                headlineContent = { Text(playlist.name, style = MaterialTheme.typography.titleSmall) },
                                supportingContent = { Text("Треков: ${playlist.trackCount}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            )
                        }
                    }
                }
            }
            else -> {
                if (recent.isEmpty()) {
                    EmptyHint("История пуста. Здесь появятся треки, которые ты слушал.")
                } else {
                    TextButton(onClick = { vm.clearHistory() }, modifier = Modifier.padding(start = 8.dp)) {
                        Text("Очистить историю")
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(recent, key = { "${it.source}-history-${it.id}" }) { track ->
                            TrackRow(
                                track = track,
                                isCurrent = current.mediaId == track.id.toString(),
                                menuItems = trackMenu(vm, track) { pending = it },
                                onClick = { vm.player.playTracks(listOf(track), 0) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (creatingPlaylist) {
        NameDialog(
            title = "Новый плейлист",
            initial = "",
            confirmLabel = "Создать",
            onConfirm = { vm.createPlaylist(it); creatingPlaylist = false },
            onDismiss = { creatingPlaylist = false }
        )
    }
    pending?.let { AddToPlaylistDialog(vm, it) { pending = null } }
}

@Composable
private fun ColumnScope.TrackList(
    vm: AppViewModel,
    list: List<TrackEntity>,
    currentId: String?,
    onAddToPlaylist: (TrackEntity) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
        itemsIndexed(list, key = { _, track -> track.id }) { index, track ->
            TrackRow(
                track = track,
                isCurrent = currentId == track.id.toString(),
                menuItems = trackMenu(vm, track, onAddToPlaylist),
                onClick = { vm.player.playTracks(list, index) }
            )
        }
    }
}
