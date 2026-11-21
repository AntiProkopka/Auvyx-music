package com.nexus.music.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
    val tracks by vm.tracks.collectAsStateWithLifecycle()
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

    val sortedTracks = remember(tracks, sort) {
        when (sort) {
            "title" -> tracks.sortedBy { it.title.lowercase() }
            "artist" -> tracks.sortedBy { it.artist.lowercase() }
            else -> tracks
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = { filePicker.launch(arrayOf("audio/*", "application/ogg", "application/octet-stream")) }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Файлы")
                }
                FilledTonalButton(modifier = Modifier.weight(1f), onClick = { folderPicker.launch(null) }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Папка")
                }
                FilledTonalButton(modifier = Modifier.weight(1f), onClick = { vm.refreshLibrary() }) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Скан")
                }
            }
        }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        val titles = listOf("Треки", "Избранное", "Плейлисты", "История")
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            titles.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
            }
        }

        when (tab) {
            0 -> {
                if (tracks.isEmpty()) {
                    EmptyHint("Пока пусто. Нажмите «Файлы» или «Папка», чтобы добавить музыку с устройства.")
                } else {
                    Box(modifier = Modifier.padding(start = 8.dp)) {
                        TextButton(onClick = { sortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("${tracks.size} • $sortLabel")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf(
                                "added" to "Недавно добавленные",
                                "title" to "По названию",
                                "artist" to "По исполнителю"
                            ).forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        sortMenu = false
                                        vm.setSort(key)
                                    }
                                )
                            }
                        }
                    }
                    TrackList(vm, sortedTracks, current.mediaId, onAddToPlaylist = { pending = it })
                }
            }
            1 -> {
                if (favorites.isEmpty()) {
                    EmptyHint("В избранное можно добавить трек через меню «⋮» или кнопку в плеере.")
                } else {
                    TrackList(vm, favorites, current.mediaId, onAddToPlaylist = { pending = it })
                }
            }
            2 -> {
                TextButton(
                    onClick = { creatingPlaylist = true },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Новый плейлист")
                }
                if (playlists.isEmpty()) {
                    EmptyHint("Плейлистов пока нет.")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(playlists, key = { it.id }) { p ->
                            ListItem(
                                modifier = Modifier.fillMaxWidth().clickable { onOpenPlaylist(p.id) },
                                leadingContent = {
                                    Icon(Icons.Default.QueueMusic, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary)
                                },
                                headlineContent = { Text(p.name, style = MaterialTheme.typography.titleSmall) },
                                supportingContent = {
                                    Text("Треков: ${p.trackCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            )
                        }
                    }
                }
            }
            else -> {
                if (recent.isEmpty()) {
                    EmptyHint("История пуста. Здесь появятся треки, которые вы слушали.")
                } else {
                    TextButton(
                        onClick = { vm.clearHistory() },
                        modifier = Modifier.padding(start = 8.dp)
                    ) { Text("Очистить историю") }
                    TrackList(vm, recent, current.mediaId, onAddToPlaylist = { pending = it })
                }
            }
        }
    }

    if (creatingPlaylist) {
        NameDialog(
            title = "Новый плейлист",
            initial = "",
            confirmLabel = "Создать",
            onConfirm = {
                vm.createPlaylist(it)
                creatingPlaylist = false
            },
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
        itemsIndexed(list, key = { _, t -> t.id }) { index, t ->
            TrackRow(
                track = t,
                isCurrent = currentId == t.id.toString(),
                menuItems = trackMenu(vm, t, onAddToPlaylist),
                onClick = { vm.player.playTracks(list, index) }
            )
        }
    }
}
