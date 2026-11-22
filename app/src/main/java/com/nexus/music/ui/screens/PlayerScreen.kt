package com.nexus.music.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.media.AudioDeviceInfo
import android.media.AudioManager
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.playback.PlaybackAudioRouting
import com.nexus.music.ui.components.EmptyHint
import com.nexus.music.ui.components.formatTime
import com.nexus.music.ui.components.ArtworkImage
import com.nexus.music.ui.components.RemoteArtworkImage

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: AppViewModel, onClose: () -> Unit) {
    val s by vm.player.state.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val cloudFavorites by vm.soundCloudFavorites.collectAsStateWithLifecycle()
    val pos by vm.player.position.collectAsStateWithLifecycle()
    val eqGains by vm.equalizerGains.collectAsStateWithLifecycle()
    val eqEnabled by vm.equalizerEnabled.collectAsStateWithLifecycle()
    val eqPreset by vm.equalizerPreset.collectAsStateWithLifecycle()
    val nickname by vm.nickname.collectAsStateWithLifecycle()
    var showQueue by remember { mutableStateOf(false) }
    var showSpeedLab by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showOutput by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        showOutput = true
    }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val track = remember(tracks, s.mediaId) { tracks.firstOrNull { it.id.toString() == s.mediaId } }
    val isCloudTrack = s.mediaId?.startsWith("soundcloud:") == true
    val cloudId = s.mediaId?.substringAfter(':')?.toLongOrNull()
    val isFavorite = if (isCloudTrack) cloudFavorites.any { it.trackId == cloudId } else track?.isFavorite == true
    val artworkUri = track?.artworkUri ?: s.artworkUri
    val duration = s.durationMs.coerceAtLeast(1L)
    val sliderValue = if (dragging) dragValue else pos.toFloat().coerceIn(0f, duration.toFloat())

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Свернуть") }
            Text(
                text = if (showQueue) "Очередь" else "Сейчас играет",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showQueue = !showQueue }) {
                Icon(Icons.Default.LibraryMusic, contentDescription = "Очередь", tint = if (showQueue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }

        if (showQueue) {
            if (s.queue.isEmpty()) EmptyHint("Очередь пуста.")
            else LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(s.queue) { index, item ->
                    val isCurrent = index == s.currentIndex
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { vm.player.playQueueIndex(index) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified)
                            Text(item.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.player.removeFromQueue(index) }) { Icon(Icons.Default.Close, contentDescription = "Убрать из очереди") }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.fillMaxWidth(0.88f).aspectRatio(1f).clip(RoundedCornerShape(30.dp))
                        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer))),
                    contentAlignment = Alignment.Center
                ) {
                    if (!artworkUri.isNullOrBlank()) {
                        if (artworkUri.startsWith("https://", ignoreCase = true)) {
                            RemoteArtworkImage(artworkUri, "Обложка ${s.title}", Modifier.fillMaxSize())
                        } else {
                            ArtworkImage(artworkUri, "Обложка ${s.title}", Modifier.fillMaxSize())
                        }
                    } else {
                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(96.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(s.title.ifBlank { "Ничего не играет" }, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOf(s.artist, s.album).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Slider(
            value = sliderValue,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { vm.player.seekTo(dragValue.toLong()); dragging = false },
            valueRange = 0f..duration.toFloat()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(sliderValue.toLong()), style = MaterialTheme.typography.bodySmall)
            Text(formatTime(s.durationMs), style = MaterialTheme.typography.bodySmall)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.player.toggleShuffle() }) {
                Icon(Icons.Default.Shuffle, contentDescription = "Перемешать", tint = if (s.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { vm.player.previous() }) { Icon(Icons.Default.SkipPrevious, contentDescription = "Предыдущий", modifier = Modifier.size(36.dp)) }
            FilledIconButton(onClick = { vm.player.togglePlayPause() }, modifier = Modifier.size(72.dp)) {
                Icon(if (s.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (s.isPlaying) "Пауза" else "Играть", modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = { vm.player.next() }) { Icon(Icons.Default.SkipNext, contentDescription = "Следующий", modifier = Modifier.size(36.dp)) }
            IconButton(onClick = { vm.player.cycleRepeat() }) {
                Icon(
                    if (s.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Повтор",
                    tint = if (s.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = s.mediaId != null, onClick = { vm.toggleCurrentFavorite() }) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Убрать из избранного" else "В избранное",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(enabled = s.mediaId != null, onClick = { showSpeedLab = true }) {
                Icon(Icons.Default.Tune, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Speed Lab")
            }
            FilledTonalButton(enabled = true, onClick = { showEqualizer = true }) { Text("EQ") }
            IconButton(onClick = {
                if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    showOutput = true
                }
            }) {
                Icon(Icons.Default.Speaker, contentDescription = "Вывод звука")
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
                    Slider(value = s.speed.coerceIn(0.5f, 2f), onValueChange = vm.player::setSpeed, valueRange = 0.5f..2f)
                    Text("Высота тона · ${"%.2f".format(s.pitch)}×", style = MaterialTheme.typography.titleSmall)
                    Slider(value = s.pitch.coerceIn(0.5f, 2f), onValueChange = vm.player::setPitch, valueRange = 0.5f..2f)
                    Text("Скорость и высота тона меняются независимо.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { vm.player.setSpeedAndPitch(0.8f, 0.85f) }) { Text("Slowed") }
                        TextButton(onClick = vm.player::resetSpeedLab) { Text("Original") }
                        TextButton(onClick = { vm.player.setSpeedAndPitch(1.25f, 1.25f) }) { Text("Speed Up") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSpeedLab = false }) { Text("Готово") } }
        )
    }

    if (showEqualizer) {
        EqualizerDialog(
            gains = eqGains,
            enabled = eqEnabled,
            preset = eqPreset,
            onEnabledChange = vm::setEqualizerEnabled,
            onPreset = vm::setEqualizerPreset,
            onBandChange = vm::setEqualizerBand,
            onDismiss = { showEqualizer = false }
        )
    }

    if (showOutput) {
        AudioOutputDialog(
            nickname = nickname,
            onSelect = { device -> PlaybackAudioRouting.select(device); showOutput = false },
            onDismiss = { showOutput = false }
        )
    }
}

@Composable
private fun EqualizerDialog(
    gains: List<Float>,
    enabled: Boolean,
    preset: String,
    onEnabledChange: (Boolean) -> Unit,
    onPreset: (String) -> Unit,
    onBandChange: (Int, Float) -> Unit,
    onDismiss: () -> Unit
) {
    val names = listOf("60 Hz", "150 Hz", "400 Hz", "1 kHz", "2.4 kHz", "15 kHz")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("6-band EQ") },
        text = {
            Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enabled, onCheckedChange = onEnabledChange)
                    Text("Эквалайзер включён")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    FilterChip(selected = preset == "bass_boost", onClick = { onPreset("bass_boost") }, label = { Text("Bass Boost") })
                    FilterChip(selected = preset == "default", onClick = { onPreset("default") }, label = { Text("Default") })
                    FilterChip(selected = preset == "treble", onClick = { onPreset("treble") }, label = { Text("Treble") })
                    FilterChip(selected = preset == "vocal", onClick = { onPreset("vocal") }, label = { Text("Vocal") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    FilterChip(selected = preset == "custom", onClick = { onPreset("custom") }, label = { Text("Custom") })
                }
                names.forEachIndexed { index, name ->
                    val value = gains.getOrElse(index) { 0f }
                    Text("$name · ${if (value > 0) "+" else ""}${"%.1f".format(value)} dB", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = value.coerceIn(-12f, 12f),
                        onValueChange = { onBandChange(index, it) },
                        valueRange = -12f..12f,
                        enabled = enabled
                    )
                }
                Text("Диапазон ±12 dB. Обработка применяется к декодированному PCM-звуку.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } }
    )
}

@Composable
private fun AudioOutputDialog(
    nickname: String?,
    onSelect: (AudioDeviceInfo?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val outputs = remember(nickname) {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isSink && it.type in SUPPORTED_OUTPUT_TYPES }
            .sortedWith(compareBy<AudioDeviceInfo> { if (it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) 0 else 1 }.thenBy { it.id })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Устройство вывода") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Системный выбор устройства")
                }
                outputs.forEach { device ->
                    TextButton(onClick = { onSelect(device) }, modifier = Modifier.fillMaxWidth()) {
                        Text(audioDeviceLabel(device, nickname), modifier = Modifier.fillMaxWidth())
                    }
                }
                if (outputs.isEmpty()) {
                    Text("Подключённые устройства вывода не найдены.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

private val SUPPORTED_OUTPUT_TYPES = setOf(
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_HDMI,
    AudioDeviceInfo.TYPE_LINE_ANALOG,
    AudioDeviceInfo.TYPE_LINE_DIGITAL,
    AudioDeviceInfo.TYPE_DOCK,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER
)

private fun audioDeviceLabel(device: AudioDeviceInfo, nickname: String?): String {
    if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
        val owner = nickname?.takeIf { it.isNotBlank() } ?: "Phone"
        return if (owner == "Phone") owner else "$owner's phone"
    }
    val typeLabel = when (device.type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Проводные наушники"
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB-аудио"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Линейный выход"
        AudioDeviceInfo.TYPE_DOCK -> "Док-станция"
        else -> "Аудиоустройство"
    }
    val name = runCatching { device.productName.toString().takeIf { it.isNotBlank() } }.getOrNull()
    return if (name.isNullOrBlank() || name == "0") typeLabel else "$name · $typeLabel"
}
