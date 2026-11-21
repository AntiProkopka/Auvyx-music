package com.nexus.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.data.db.TrackEntity

@Composable
fun AddToPlaylistDialog(vm: AppViewModel, track: TrackEntity, onDismiss: () -> Unit) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    PlaylistPickerDialog(
        playlists = playlists,
        onPick = { id ->
            vm.addToPlaylist(id, track.id)
            onDismiss()
        },
        onCreate = { name ->
            vm.createPlaylistWithTrack(name, track.id)
            onDismiss()
        },
        onDismiss = onDismiss
    )
}

fun trackMenu(
    vm: AppViewModel,
    t: TrackEntity,
    onAddToPlaylist: (TrackEntity) -> Unit
): List<MenuAction> = listOf(
    MenuAction(if (t.isFavorite) "Убрать из избранного" else "В избранное") { vm.toggleFavorite(t) },
    MenuAction("В плейлист…") { onAddToPlaylist(t) },
    MenuAction("Играть следующим") { vm.player.playNext(t) },
    MenuAction("В конец очереди") { vm.player.enqueue(t) },
    MenuAction("Удалить из медиатеки") { vm.deleteTrack(t) }
)
