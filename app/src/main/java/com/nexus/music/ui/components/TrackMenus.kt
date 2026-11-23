package com.nexus.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.data.db.TrackEntity
import com.nexus.music.data.soundcloud.SoundCloudTrack

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

@Composable
fun AddSoundCloudToPlaylistDialog(
    vm: AppViewModel,
    track: SoundCloudTrack,
    onDismiss: () -> Unit
) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    PlaylistPickerDialog(
        playlists = playlists,
        onPick = { id ->
            vm.addSoundCloudToPlaylist(id, track)
            onDismiss()
        },
        onCreate = { name ->
            vm.createPlaylistWithSoundCloudTrack(name, track)
            onDismiss()
        },
        onDismiss = onDismiss
    )
}

fun trackMenu(
    vm: AppViewModel,
    t: TrackEntity,
    onAddToPlaylist: (TrackEntity) -> Unit
): List<MenuAction> = listOfNotNull(
    if (AppViewModel.AlbumTarget.from(t) != null) MenuAction("Альбом") { vm.openAlbum(t) } else null,
    if (t.source in setOf("local", "soundcloud")) MenuAction("Исполнитель") { vm.openArtist(t) } else null,
    MenuAction(if (t.isFavorite) "Убрать из избранного" else "В избранное") { vm.toggleFavorite(t) },
    if (t.source == "local" && t.downloadedPath == null) MenuAction("Сохранить офлайн") { vm.saveOffline(t) } else null,
    if (t.source == "local" && t.downloadedPath != null) MenuAction("Удалить офлайн-копию") { vm.removeOffline(t) } else null,
    if (t.source == "local" && t.downloadedPath == null) MenuAction("Выбрать файл заново") { vm.relinkTarget.value = t } else null,
    MenuAction("В плейлист…") { onAddToPlaylist(t) },
    MenuAction("Играть следующим") { vm.player.playNext(t) },
    MenuAction("В конец очереди") { vm.player.enqueue(t) },
    MenuAction("Удалить из медиатеки") { vm.deleteTrack(t) }
)
