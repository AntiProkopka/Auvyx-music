package com.nexus.music.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.data.db.TrackEntity
import com.nexus.music.ui.components.AddToPlaylistDialog
import com.nexus.music.ui.components.EmptyHint
import com.nexus.music.ui.components.TrackRow
import com.nexus.music.ui.components.trackMenu

@Composable
fun SearchScreen(vm: AppViewModel) {
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val current by vm.player.state.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<TrackEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.searchQuery.value = it },
            singleLine = true,
            label = { Text("Поиск по медиатеке") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.searchQuery.value = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Очистить")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
        if (query.isBlank()) {
            EmptyHint("Ищите по названию, исполнителю или альбому. Пока поиск работает по локальной медиатеке.")
        } else if (results.isEmpty()) {
            EmptyHint("Ничего не найдено.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
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
