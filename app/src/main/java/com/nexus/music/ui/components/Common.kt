package com.nexus.music.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.nexus.music.data.db.PlaylistSummary
import com.nexus.music.data.db.TrackEntity

data class MenuAction(val label: String, val action: () -> Unit)

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
fun TrackRow(
    track: TrackEntity,
    isCurrent: Boolean,
    menuItems: List<MenuAction>,
    onClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val style = com.nexus.music.ui.theme.LocalStyle.current
    val shape = MaterialTheme.shapes.medium
    ListItem(
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (style in listOf("hyper","glass")) 10.dp else 0.dp,
            vertical = if (style in listOf("hyper","glass")) 3.dp else 0.dp).clip(shape).clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (!track.artworkUri.isNullOrBlank()) {
                    if (track.artworkUri.startsWith("https://", ignoreCase = true) ||
                        track.artworkUri.startsWith("http://", ignoreCase = true)
                    ) {
                        RemoteArtworkImage(
                            imageUrl = track.artworkUri,
                            contentDescription = "Обложка ${track.title}",
                            modifier = Modifier.size(52.dp)
                        )
                    } else {
                        ArtworkImage(
                            artworkUri = track.artworkUri,
                            contentDescription = "Обложка ${track.title}",
                            modifier = Modifier.size(52.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = if (isCurrent) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(Modifier.align(Alignment.BottomEnd)) { PlatformArtworkBadge(track.source) }
            }
        },
        headlineContent = {
            Text(
                text = track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Text(
                text = "${track.artist} • ${formatTime(track.durationMs)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "В избранном",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (menuItems.isNotEmpty()) {
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                            shape = MaterialTheme.shapes.large, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            menuItems.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.label) },
                                    onClick = {
                                        expanded = false
                                        item.action()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun ArtworkImage(
    artworkUri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    LocalArtworkImage(rememberLocalArtwork(artworkUri), contentDescription, modifier)
}

@Composable
internal fun rememberLocalArtwork(artworkUri: String?): Bitmap? = key(artworkUri) {
    val context = LocalContext.current
    produceState<Bitmap?>(null) {
        value = withContext(Dispatchers.IO) {
            if (artworkUri.isNullOrBlank()) null else runCatching {
                val uri = Uri.parse(artworkUri)
                if (uri.scheme == "file") {
                    decodeSampledBitmap(uri.path) { BitmapFactory.decodeFile(uri.path, it) }
                } else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, bounds)
                    }
                    val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 640, 640)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(
                            stream,
                            null,
                            BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
                        )
                    }
                }
            }.getOrNull()
        }
    }.value
}

@Composable
internal fun LocalArtworkImage(bitmap: Bitmap?, contentDescription: String?, modifier: Modifier) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
fun EmptyHint(text: String) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp)
        )
    }
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Название") }
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun PlaylistPickerDialog(
    playlists: List<PlaylistSummary>,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog(
            title = "Новый плейлист",
            initial = "",
            confirmLabel = "Создать",
            onConfirm = { onCreate(it) },
            onDismiss = { creating = false }
        )
        return
    }
    AlertDialog(
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        onDismissRequest = onDismiss,
        title = { Text("Добавить в плейлист") },
        text = {
            LazyColumn {
                item {
                    TextButton(onClick = { creating = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Новый плейлист")
                    }
                }
                items(playlists, key = { it.id }) { p ->
                    Text(
                        text = "${p.name} (${p.trackCount})",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(p.id) }
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

private fun decodeSampledBitmap(
    path: String?,
    decoder: (BitmapFactory.Options) -> Bitmap?
): Bitmap? {
    if (path.isNullOrBlank()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 640, 640)
    return decoder(BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) })
}

private fun calculateInSampleSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while (width / (sample * 2) >= maxWidth && height / (sample * 2) >= maxHeight) {
        sample *= 2
    }
    return sample
}
