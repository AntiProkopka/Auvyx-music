package com.nexus.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.ui.components.EmptyHint
import com.nexus.music.ui.components.formatTime
import com.nexus.music.ui.components.ArtworkImage
import com.nexus.music.ui.components.RemoteArtworkImage

@Composable
fun PlayerScreen(vm: AppViewModel, onClose: () -> Unit) {
    val s by vm.player.state.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val pos by vm.player.position.collectAsStateWithLifecycle()
    var showQueue by remember { mutableStateOf(false) }
    var showSpeedLab by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val track = remember(tracks, s.mediaId) { tracks.firstOrNull { it.id.toString() == s.mediaId } }
    val artworkUri = track?.artworkUri ?: s.artworkUri
    val duration = s.durationMs.coerceAtLeast(1L)
    val sliderValue = if (dragging) dragValue else pos.toFloat().coerceIn(0f, duration.toFloat())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Свернуть")
            }
            Text(
                text = if (showQueue) "Очередь" else "Сейчас играет",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showQueue = !showQueue }) {
                Icon(
                    Icons.Default.LibraryMusic,
                    contentDescription = "Очередь",
                    tint = if (showQueue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (showQueue) {
            if (s.queue.isEmpty()) {
                EmptyHint("Очередь пуста.")
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(s.queue) { index, item ->
                        val isCurrent = index == s.currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.player.playQueueIndex(index) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified
                                )
                                Text(
                                    item.artist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { vm.player.removeFromQueue(index) }) {
                                Icon(Icons.Default.Close, contentDescription = "Убрать из очереди")
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.tertiaryContainer
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!artworkUri.isNullOrBlank()) {
                        if (artworkUri.startsWith("https://", ignoreCase = true)) {
                            RemoteArtworkImage(
                                imageUrl = artworkUri,
                                contentDescription = "Обложка ${s.title}",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            ArtworkImage(
                                artworkUri = artworkUri,
                                contentDescription = "Обложка ${s.title}",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(96.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = s.title.ifBlank { "Ничего не играет" },
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = listOf(s.artist, s.album).filter { it.isNotBlank() }.joinToString(" • "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Slider(
            value = sliderValue,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                vm.player.seekTo(dragValue.toLong())
                dragging = false
            },
            valueRange = 0f..duration.toFloat()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(sliderValue.toLong()), style = MaterialTheme.typography.bodySmall)
            Text(formatTime(s.durationMs), style = MaterialTheme.typography.bodySmall)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.player.toggleShuffle() }) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "Перемешать",
                    tint = if (s.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { vm.player.previous() }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Предыдущий", modifier = Modifier.size(36.dp))
            }
            FilledIconButton(onClick = { vm.player.togglePlayPause() }, modifier = Modifier.size(72.dp)) {
                Icon(
                    if (s.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (s.isPlaying) "Пауза" else "Играть",
                    modifier = Modifier.size(40.dp)
                )
            }
            IconButton(onClick = { vm.player.next() }) {
                Icon(Icons.Default.SkipNext, contentDescription = "Следующий", modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = { vm.player.cycleRepeat() }) {
                Icon(
                    if (s.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Повтор",
                    tint = if (s.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(
                enabled = track != null,
                onClick = { track?.let { vm.toggleFavorite(it) } }
            ) {
                Icon(
                    if (track?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Избранное",
                    tint = if (track?.isFavorite == true) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                enabled = s.mediaId != null,
                onClick = { showSpeedLab = true }
            ) {
                Icon(Icons.Default.Tune, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Speed Lab")
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showSpeedLab) {
        AlertDialog(
            onDismissRequest = { showSpeedLab = false },
            title = { Text("Speed Lab") },
            text = {
                Column {
                    Text("Скорость · ${"%.2f".format(s.speed)}×", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = s.speed.coerceIn(0.5f, 2f),
                        onValueChange = vm.player::setSpeed,
                        valueRange = 0.5f..2f
                    )
                    Text("Высота тона · ${"%.2f".format(s.pitch)}×", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = s.pitch.coerceIn(0.5f, 2f),
                        onValueChange = vm.player::setPitch,
                        valueRange = 0.5f..2f
                    )
                    Text(
                        "Скорость и высота тона меняются независимо.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TextButton(onClick = { vm.player.setSpeedAndPitch(0.8f, 0.85f) }) { Text("Slowed") }
                        TextButton(onClick = vm.player::resetSpeedLab) { Text("Original") }
                        TextButton(onClick = { vm.player.setSpeedAndPitch(1.25f, 1.25f) }) { Text("Speed Up") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedLab = false }) { Text("Готово") }
            }
        )
    }
}
