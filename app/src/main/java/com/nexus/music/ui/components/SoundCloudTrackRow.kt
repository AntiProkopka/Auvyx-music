package com.nexus.music.ui.components

import com.nexus.music.R
import com.nexus.music.ui.theme.*
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ListItemDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nexus.music.data.soundcloud.SoundCloudTrack

@Composable
fun SoundCloudTrackRow(
    track: SoundCloudTrack,
    isCurrent: Boolean = false,
    isFavorite: Boolean = false,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onEnqueue: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onArtist: (() -> Unit)? = null,
    onAlbum: (() -> Unit)? = null,
    onAddToLibrary: (() -> Unit)? = null,
    playEnabled: Boolean = true
) {
    CatalogResultRow(track.title, if (track.full) track.artist else "Полная версия недоступна · ${track.artist}", track.durationMs, track.artworkUrl, "soundcloud",
        isCurrent, isFavorite, onPlay, onFavorite, onEnqueue, onPlayNext, onAddToPlaylist,
        onArtist = onArtist, onAlbum = onAlbum, onAddToLibrary = onAddToLibrary, playEnabled = playEnabled)
}

@Composable
fun CatalogResultRow(title: String, artist: String, durationMs: Long, artwork: String?, platform: String,
    isCurrent: Boolean, isFavorite: Boolean, onPlay: () -> Unit, onFavorite: () -> Unit, onEnqueue: () -> Unit,
    onPlayNext: (() -> Unit)?, onAddToPlaylist: (() -> Unit)?, onOpenSpotify: (() -> Unit)? = null, onArtist: (() -> Unit)? = null,
    onAlbum: (() -> Unit)? = null, onAddToLibrary: (() -> Unit)? = null, playEnabled: Boolean = true) {
    var menuExpanded by remember(title, artist) { mutableStateOf(false) }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.surface),
        modifier = Modifier.then(if (isGlass) Modifier.padding(horizontal = 10.dp, vertical = 3.dp) else Modifier).frosted(MaterialTheme.shapes.medium).clickable(enabled = playEnabled, onClick = onPlay),
        leadingContent = {
            if (!artwork.isNullOrBlank()) {
                Box(modifier = Modifier.size(56.dp)) {
                    RemoteArtworkImage(
                        imageUrl = artwork,
                        contentDescription = "Обложка $title",
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                    )
                    Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                        PlatformArtworkBadge(platform = platform)
                    }
                }
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(40.dp))
            }
        },
        headlineContent = {
            Text(
                title,
                maxLines = 1,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Text(
                "$artist · ${formatTime(durationMs)}",
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onFavorite) {
                    BrandIcon(if (isFavorite) R.drawable.glass_heartfill else R.drawable.glass_favorite,
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Убрать из избранного" else "В избранное",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEnqueue, enabled = playEnabled) {
                    BrandIcon(R.drawable.glass_queue_add, Icons.Default.Add, contentDescription = "Добавить в очередь")
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        BrandIcon(R.drawable.glass_more, Icons.Default.MoreVert, contentDescription = "Меню трека")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false },
                            shape = MaterialTheme.shapes.large, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        onArtist?.let { DropdownMenuItem(text = { Text("Исполнитель") }, onClick = { menuExpanded = false; it() }) }
                        onAlbum?.let { DropdownMenuItem(text = { Text("Альбом") }, onClick = { menuExpanded = false; it() }) }
                        onAddToLibrary?.let {
                            DropdownMenuItem(text = { Text("Добавить в медиатеку") }, onClick = { menuExpanded = false; it() })
                        }
                        onOpenSpotify?.let { action ->
                            DropdownMenuItem(
                                text = { Text("Открыть в Spotify") },
                                onClick = {
                                    menuExpanded = false
                                    action()
                                }
                            )
                        }
                        onPlayNext?.let { action ->
                            DropdownMenuItem(
                                text = { Text("Играть следующим") },
                                enabled = playEnabled,
                                onClick = { menuExpanded = false; action() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("В конец очереди") },
                            enabled = playEnabled,
                            onClick = { menuExpanded = false; onEnqueue() }
                        )
                        onAddToPlaylist?.let { action ->
                            DropdownMenuItem(
                                text = { Text("В плейлист…") },
                                onClick = { menuExpanded = false; action() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (isFavorite) "Убрать из избранного" else "В избранное") },
                            onClick = { menuExpanded = false; onFavorite() }
                        )
                    }
                }
            }
        }
    )
}
