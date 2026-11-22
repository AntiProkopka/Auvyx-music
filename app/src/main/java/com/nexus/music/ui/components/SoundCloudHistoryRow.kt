package com.nexus.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
                RemoteArtworkImage(
                    imageUrl = item.artworkUrl,
                    contentDescription = "Обложка ${item.title}",
                    modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp))
                )
            } else {
                Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(38.dp))
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
                "${item.artist}$duration · SoundCloud",
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    )
}
