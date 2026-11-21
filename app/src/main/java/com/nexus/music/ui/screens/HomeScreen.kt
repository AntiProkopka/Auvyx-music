package com.nexus.music.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
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
    val added = tracks.take(10)
    val recentShown = recent.take(10)

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
        if (tracks.isEmpty()) {
            item {
                EmptyHint("Медиатека пуста. Откройте вкладку «Медиатека» и добавьте файлы или папку с музыкой.")
            }
        }
        if (recentShown.isNotEmpty()) {
            item { SectionTitle("Недавно прослушанное") }
            itemsIndexed(recentShown, key = { _, t -> "r${t.id}" }) { index, t ->
                TrackRow(
                    track = t,
                    isCurrent = current.mediaId == t.id.toString(),
                    menuItems = trackMenu(vm, t) { pending = it },
                    onClick = { vm.player.playTracks(recent, index) }
                )
            }
        }
        if (added.isNotEmpty()) {
            item { SectionTitle("Недавно добавленное") }
            itemsIndexed(added, key = { _, t -> "a${t.id}" }) { index, t ->
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
