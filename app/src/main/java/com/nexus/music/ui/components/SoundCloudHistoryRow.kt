package com.nexus.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nexus.music.data.db.SoundCloudHistoryEntity

@Composable
fun SoundCloudHistoryRow(
    item: SoundCloudHistoryEntity,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            if (!item.artworkUrl.isNullOrBlank()) {
                Box(modifier = Modifier.size(54.dp)) {
                    RemoteArtworkImage(
                        imageUrl = item.artworkUrl,
                        contentDescription = "Обложка ${item.title}",
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                    )
                    Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                        PlatformArtworkBadge(platform = "soundcloud")
                    }
                }
            } else {
                Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.BottomEnd) {
                    Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.align(Alignment.Center).size(38.dp))
                    PlatformArtworkBadge(platform = "soundcloud")
                }
            }
        },
        headlineContent = {
            Text(
                item.title,
                maxLines = 1,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            val duration = if (item.durationMs > 0L) " · ${formatTime(item.durationMs)}" else ""
            Text(
                "${item.artist}$duration",
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    )
}
