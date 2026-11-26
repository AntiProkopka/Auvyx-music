package com.nexus.music.ui.screens

import com.nexus.music.ui.theme.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import com.nexus.music.ui.components.ExpressiveFilterChip
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
import com.nexus.music.R
import com.nexus.music.AppViewModel
import com.nexus.music.ui.LocalBottomBarPadding
import com.nexus.music.data.db.TrackEntity
import com.nexus.music.ui.components.AddToPlaylistDialog
import com.nexus.music.ui.components.EmptyHint
import com.nexus.music.ui.components.NameDialog
import com.nexus.music.ui.components.TrackRow
import com.nexus.music.ui.components.trackMenu

@Composable
fun LibraryScreen(vm: AppViewModel, onOpenPlaylist: (Long) -> Unit) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val collection by vm.knownTracks.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val sort by vm.sortMode.collectAsStateWithLifecycle()
    val current by vm.player.state.collectAsStateWithLifecycle()

    val glass = isGlass
    var savedTab by rememberSaveable(glass) { mutableIntStateOf(if (glass) 7 else 0) }
    val tab = if (savedTab == 4) 0 else savedTab
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
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 10.dp),
                    onClick = { filePicker.launch(arrayOf("audio/*", "application/ogg", "application/octet-stream")) }
                ) {
                    if (!compactTools) {
                        com.nexus.music.ui.components.BrandIcon(R.drawable.glass_plus, Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("Файлы", maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                }
                FilledTonalButton(
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 10.dp),
                    onClick = { folderPicker.launch(null) }
                ) {
                    if (!compactTools) {
                        com.nexus.music.ui.components.BrandIcon(R.drawable.glass_files, Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("Папка", maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                }
                FilledTonalButton(
                    shapes = ButtonDefaults.shapes(),
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
            ((if (glass) listOf(7 to "Коллекции") else emptyList()) + listOf(0 to "Треки", 1 to "Избранное", 2 to "Плейлисты", 3 to "История", 5 to "Офлайн", 6 to "Исполнители")).forEach { (index, label) ->
                ExpressiveFilterChip(
                    selected = tab == index,
                    onClick = { savedTab = index },
                    label = { Text(label, maxLines = 1) }
                )
            }
        }

        when (tab) {
            7 -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomBarPadding.current),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    CollectionPanel("Понравившиеся песни", favorites.size, null, true) { savedTab = 1 }
                }
                items(playlists, key = { it.id }) { playlist ->
                    val members by remember(playlist.id) { vm.playlistTracks(playlist.id) }.collectAsStateWithLifecycle(initialValue = emptyList())
                    CollectionPanel(playlist.name, playlist.trackCount, members.firstNotNullOfOrNull { it.artworkUri }, false) {
                        onOpenPlaylist(playlist.id)
                    }
                }
                item { TextButton(onClick = { creatingPlaylist = true }) { Text("Новый плейлист") } }
            }
            6 -> LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomBarPadding.current)) {
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
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false },
                            shape = MaterialTheme.shapes.large, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
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
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = LocalBottomBarPadding.current)
                    ) {
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
                    com.nexus.music.ui.components.BrandIcon(R.drawable.glass_plus, Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Новый плейлист")
                }
                if (playlists.isEmpty()) {
                    EmptyHint("Плейлистов пока нет.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = LocalBottomBarPadding.current)
                    ) {
                        items(playlists, key = { it.id }) { playlist ->
                            if (glass) {
                                val members by remember(playlist.id) { vm.playlistTracks(playlist.id) }.collectAsStateWithLifecycle(initialValue = emptyList())
                                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                    CollectionPanel(playlist.name, playlist.trackCount, members.firstNotNullOfOrNull { it.artworkUri }, false) {
                                        onOpenPlaylist(playlist.id)
                                    }
                                }
                            } else ListItem(
                                modifier = Modifier.fillMaxWidth().clickable { onOpenPlaylist(playlist.id) },
                                leadingContent = { com.nexus.music.ui.components.BrandIcon(R.drawable.glass_queue, Icons.Default.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
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
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = LocalBottomBarPadding.current)
                    ) {
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
    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(bottom = LocalBottomBarPadding.current)
    ) {
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

@Composable
private fun CollectionPanel(title: String, count: Int, cover: String?, favorites: Boolean, open: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 102.dp).frosted(MaterialTheme.shapes.large).clickable(onClick = open)
        .padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (favorites) {
            val context = LocalContext.current
            val res = remember { context.resources.getIdentifier("glass_liked_songs", "drawable", context.packageName) }
            if (res != 0) Image(painterResource(res), null, Modifier.size(76.dp), contentScale = ContentScale.Fit)
        } else ArtTile(cover, null, Modifier.size(76.dp))
        Column { Text(title, style = MaterialTheme.typography.titleMedium); Text("Треков · $count", style = MaterialTheme.typography.bodySmall) }
    }
}
