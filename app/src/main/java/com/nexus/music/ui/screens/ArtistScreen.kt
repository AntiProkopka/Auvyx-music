package com.nexus.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.R
import com.nexus.music.AppViewModel
import com.nexus.music.ui.LocalBottomBarPadding
import com.nexus.music.data.db.availableAlbumTitle
import com.nexus.music.data.soundcloud.SoundCloudRepository
import com.nexus.music.data.soundcloud.SoundCloudTrack
import com.nexus.music.ui.components.ArtworkImage
import com.nexus.music.ui.components.RemoteArtworkImage
import com.nexus.music.ui.components.formatTime

/** Artist page always uses a stable provider profile ID when SoundCloud is involved. */
@Composable
fun ArtistScreen(vm: AppViewModel, target: AppViewModel.ArtistTarget, onOpenPlayer: () -> Unit) {
    if (com.nexus.music.ui.theme.isGlass) { GlassArtist(vm, target, onOpenPlayer); return }
    val playback by vm.player.state.collectAsStateWithLifecycle()
    val known by vm.knownTracks.collectAsStateWithLifecycle()
    val current = known.firstOrNull { it.id.toString() == playback.mediaId }
    val currentCloudId = current?.takeIf { it.source == "soundcloud" }?.sourceId
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp + LocalBottomBarPadding.current),
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
                        com.nexus.music.ui.components.BrandIcon(R.drawable.glass_library, Icons.Default.MusicNote, contentDescription = null,
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
                val owner = profile?.id ?: target.owner
                val activeArtist = if (target.cloud) {
                    currentCloudId != null && (rows.any { it.id.toString() == currentCloudId } ||
                        (owner != null && owner.toString() == current?.artistId))
                } else {
                    songs.any { it.id.toString() == playback.mediaId }
                }
                val firstCloud = rows.firstOrNull { it.full }
                val showPause = activeArtist && playback.showPause
                Button(
                    shapes = ButtonDefaults.shapes(),
                    enabled = activeArtist || if (target.cloud) firstCloud != null else songs.isNotEmpty(),
                    onClick = {
                        when {
                            activeArtist -> vm.player.togglePlayPause()
                            target.cloud -> firstCloud?.let(vm::playTrack)
                            else -> songs.firstOrNull()?.let(vm::playTrack)
                        }
                    }
                ) {
                    com.nexus.music.ui.components.BrandIcon(if (showPause) R.drawable.glass_pause else R.drawable.glass_play, if (showPause) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (showPause) "Пауза" else "Слушать")
                }
            }
        }
        item {
            Text("Треки", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
        }
        if (target.cloud) {
            items(rows, key = { song -> "sc-${song.id}" }) { song ->
                val showPause = playback.showPause && currentCloudId == song.id.toString()
                ListItem(
                    modifier = Modifier.fillMaxWidth().clip(shape)
                        .clickable(enabled = song.full) { vm.playTrack(song); onOpenPlayer() },
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
                    trailingContent = { com.nexus.music.ui.components.BrandIcon(if (showPause) R.drawable.glass_pause else R.drawable.glass_play, if (showPause) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null) }
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
            items(songs, key = { song -> "local-${song.id}" }) { song ->
                val showPause = playback.showPause && playback.mediaId == song.id.toString()
                ListItem(
                    modifier = Modifier.fillMaxWidth().clip(shape)
                        .clickable { vm.playTrack(song); onOpenPlayer() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    leadingContent = {
                        Box(Modifier.size(60.dp).clip(RoundedCornerShape(12.dp))) {
                            ArtworkImage(song.artworkUri, "Обложка ${song.title}", Modifier.fillMaxSize())
                        }
                    },
                    headlineContent = { Text(song.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(listOf(availableAlbumTitle(song.album, song.source), formatTime(song.durationMs))
                            .filterNotNull().joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = { com.nexus.music.ui.components.BrandIcon(if (showPause) R.drawable.glass_pause else R.drawable.glass_play, if (showPause) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null) }
                )
            }
            if (songs.isEmpty()) item { Text("У исполнителя пока нет треков в медиатеке") }
        }
    }
}
