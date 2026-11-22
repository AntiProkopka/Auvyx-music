package com.nexus.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onEnqueue: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onPlay),
        leadingContent = {
            if (!track.artworkUrl.isNullOrBlank()) {
                Box(modifier = Modifier.size(56.dp)) {
                    RemoteArtworkImage(
                        imageUrl = track.artworkUrl,
                        contentDescription = "Обложка ${track.title}",
                        modifier = Modifier.matchParentSize().clip(RoundedCornerShape(16.dp))
                    )
                    Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                        PlatformArtworkBadge(platform = "soundcloud")
                    }
                }
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(40.dp))
            }
        },
        headlineContent = {
            Text(
                track.title,
                maxLines = 1,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Text(
                "${track.artist} · ${formatTime(track.durationMs)}",
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Убрать из избранного" else "В избранное",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEnqueue) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить в очередь")
                }
            }
        }
    )
}
