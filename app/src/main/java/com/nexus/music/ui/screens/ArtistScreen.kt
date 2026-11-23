package com.nexus.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.data.soundcloud.SoundCloudRepository
import com.nexus.music.data.soundcloud.SoundCloudTrack
import com.nexus.music.ui.components.ArtworkImage
import com.nexus.music.ui.components.RemoteArtworkImage
import com.nexus.music.ui.components.formatTime

/** Artist page always uses a stable provider profile ID when SoundCloud is involved. */
@Composable
fun ArtistScreen(vm: AppViewModel, target: AppViewModel.ArtistTarget) {
    val local by vm.tracks.collectAsStateWithLifecycle()
    val songs = remember(local, target) {
        local.filter { it.artist.trim().equals(target.name.trim(), ignoreCase = true) }
    }
    var profile by remember(target) { mutableStateOf<SoundCloudRepository.Artist?>(null) }
    var rows by remember(target) { mutableStateOf(emptyList<SoundCloudTrack>()) }
    var cursor by remember(target) { mutableStateOf<String?>(null) }
    var request by remember(target) { mutableIntStateOf(0) }
    var loading by remember(target) { mutableStateOf(target.cloud) }
    var error by remember(target) { mutableStateOf<String?>(null) }

    LaunchedEffect(target, request) {
        if (!target.cloud) return@LaunchedEffect
        loading = true
        error = null
        try {
            val owner = profile ?: SoundCloudRepository.artist(target.owner, target.permalink)
            profile = owner
            val page = SoundCloudRepository.artistTracks(owner.id, cursor)
            rows = (rows + page.tracks).distinctBy { it.id }
            cursor = page.next
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (exception: Exception) {
            error = exception.localizedMessage ?: "Не удалось открыть профиль"
        } finally {
            loading = false
        }
    }

    val shape = MaterialTheme.shapes.medium
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            TextButton(onClick = { vm.artistTarget.value = null }) { Text("← Назад") }
        }
        item {
            val colors = MaterialTheme.colorScheme
            Column(
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
                    .background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.surfaceContainerHigh)))
                    .padding(20.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.size(132.dp).clip(RoundedCornerShape(32.dp))
                        .background(colors.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    val avatar = profile?.avatar
                    if (target.cloud && !avatar.isNullOrBlank()) {
                        RemoteArtworkImage(avatar, "Исполнитель ${target.name}", Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Default.MusicNote, contentDescription = null,
                            modifier = Modifier.size(54.dp), tint = colors.onSurfaceVariant)
                    }
                }
                Text(profile?.name ?: target.name, style = MaterialTheme.typography.headlineLarge,
                    color = colors.onSurface)
                Text(if (target.cloud) "Исполнитель · SoundCloud" else "Исполнитель · На устройстве",
                    style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                profile?.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(description, style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
                val canPlay = if (target.cloud) rows.isNotEmpty() else songs.isNotEmpty()
                Button(
                    shapes = ButtonDefaults.shapes(),
                    enabled = canPlay,
                    onClick = {
                        if (target.cloud && rows.isNotEmpty()) vm.playSoundCloud(rows, 0)
                        else if (!target.cloud && songs.isNotEmpty()) vm.player.playTracks(songs, 0)
                    }
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Слушать")
                }
            }
        }
        item {
            Text("Треки", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
        }
        if (target.cloud) {
            itemsIndexed(rows, key = { _, song -> "sc-${song.id}" }) { index, song ->
                ListItem(
                    modifier = Modifier.fillMaxWidth().clip(shape)
                        .clickable { vm.playSoundCloud(rows, index) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    leadingContent = {
                        Box(Modifier.size(60.dp).clip(RoundedCornerShape(12.dp))) {
                            RemoteArtworkImage(song.artworkUrl, "Обложка ${song.title}", Modifier.fillMaxSize())
                        }
                    },
                    headlineContent = { Text(song.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(if (song.full) formatTime(song.durationMs) else "Недоступна полная версия",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = { Icon(Icons.Default.PlayArrow, contentDescription = "Воспроизвести") }
                )
            }
            item {
                when {
                    loading -> CircularProgressIndicator()
                    error != null -> {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { request++ }) { Text("Повторить") }
                    }
                    cursor != null -> TextButton(onClick = { request++ }) { Text("Ещё треки") }
                    rows.isEmpty() -> Text("У исполнителя пока нет доступных треков")
                }
            }
        } else {
            itemsIndexed(songs, key = { _, song -> "local-${song.id}" }) { index, song ->
                ListItem(
                    modifier = Modifier.fillMaxWidth().clip(shape)
                        .clickable { vm.player.playTracks(songs, index) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    leadingContent = {
                        Box(Modifier.size(60.dp).clip(RoundedCornerShape(12.dp))) {
                            ArtworkImage(song.artworkUri, "Обложка ${song.title}", Modifier.fillMaxSize())
                        }
                    },
                    headlineContent = { Text(song.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(listOf(song.album.takeIf { it.isNotBlank() }, formatTime(song.durationMs))
                            .filterNotNull().joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = { Icon(Icons.Default.PlayArrow, contentDescription = "Воспроизвести") }
                )
            }
            if (songs.isEmpty()) item { Text("У исполнителя пока нет треков в медиатеке") }
        }
    }
}
