package com.nexus.music.ui.screens

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SliderState
import androidx.compose.material3.VerticalSlider
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.music.ui.components.AddToPlaylistDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.nexus.music.ui.theme.*
import com.nexus.music.ui.sharedCover
import com.nexus.music.R
import com.nexus.music.ui.components.BrandIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.media.AudioAttributes
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import com.nexus.music.ui.components.ExpressiveFilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.nexus.music.ui.components.ExpressiveSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.data.db.availableAlbumTitle
import com.nexus.music.data.local.OfflineAudio
import com.nexus.music.playback.SixBandEqualizer
import com.nexus.music.playback.PlaybackAudioRouting
import com.nexus.music.ui.components.EmptyHint
import com.nexus.music.ui.components.formatTime
import com.nexus.music.ui.components.LocalArtworkImage
import com.nexus.music.ui.components.rememberLocalArtwork
import com.nexus.music.ui.components.RemoteArtworkBitmap
import com.nexus.music.ui.components.rememberRemoteArtwork
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import com.nexus.music.playback.LyricsRepository
import com.nexus.music.ui.components.rememberArtworkAccent

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: AppViewModel, onClose: () -> Unit) {
    val style = com.nexus.music.ui.theme.LocalStyle.current
    val s by vm.player.state.collectAsStateWithLifecycle()
    val tracks by vm.knownTracks.collectAsStateWithLifecycle()
    val eqGains by vm.equalizerGains.collectAsStateWithLifecycle()
    val eqEnabled by vm.equalizerEnabled.collectAsStateWithLifecycle()
    val eqPreset by vm.equalizerPreset.collectAsStateWithLifecycle()
    val nickname by vm.nickname.collectAsStateWithLifecycle()
    var showQueue by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }
    var showTrackMenu by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<com.nexus.music.data.db.TrackEntity?>(null) }
    val offlineTransfers by vm.offlineTransfers.collectAsStateWithLifecycle()
    var showPlaylist by remember { mutableStateOf(false) }
    val disliked by vm.dislikedTracks.collectAsStateWithLifecycle()
    var showOutput by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        showOutput = true
    }
    val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
    val mediaStream = AudioManager.STREAM_MUSIC
    val maxVolume = audioManager.getStreamMaxVolume(mediaStream).coerceAtLeast(1)
    var volume by remember(audioManager) { mutableStateOf(audioManager.getStreamVolume(mediaStream).toFloat()) }
    fun setVolume(value: Float) {
        volume = value.roundToInt().coerceIn(0, maxVolume).toFloat()
        audioManager.setStreamVolume(mediaStream, volume.toInt(), 0)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var currentOutput by remember { mutableStateOf<AudioDeviceInfo?>(null) }
    LaunchedEffect(lifecycle, audioManager, showOutput, s.isPlaying) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                volume = audioManager.getStreamVolume(mediaStream).toFloat()
                currentOutput = mediaOutput(audioManager, s.isPlaying)
                delay(500)
            }
        }
    }
    BackHandler(showQueue) { showQueue = false }
    val track = remember(tracks, s.mediaId) { tracks.firstOrNull { it.id.toString() == s.mediaId } }
    val isFavorite = track?.isFavorite == true
    val artworkUri = track?.artworkUri ?: s.artworkUri
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val compact = (config.screenHeightDp < 520 || config.fontScale > 1.5f) && !showQueue
    val scroll = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize()
        .then(if (compact) Modifier.verticalScroll(scroll) else Modifier)
        .padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (showQueue) showQueue = false else onClose() }) {
                BrandIcon(if (showQueue) R.drawable.glass_close else R.drawable.glass_chevrondown, if (showQueue) Icons.Default.Close else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (showQueue) "Закрыть очередь" else "Свернуть")
            }
            Text(
                text = if (showQueue) "Очередь" else "сейчас играет",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showQueue = !showQueue }) {
                BrandIcon(R.drawable.glass_queue, Icons.Default.LibraryMusic, contentDescription = "Очередь", tint = if (showQueue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }

        if (showQueue) {
            if (s.queue.isEmpty()) EmptyHint("Очередь пуста.")
            else LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(s.queue) { index, item ->
                    val isCurrent = index == s.currentIndex
                    val rowHeight = with(LocalDensity.current) { 60.dp.toPx() }
                    var dragOffset by remember(index, item) { mutableFloatStateOf(0f) }
                    Row(
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                            .zIndex(if (dragOffset != 0f) 1f else 0f)
                            .graphicsLayer { translationY = dragOffset }
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCurrent || dragOffset != 0f) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                            .clickable { vm.player.playQueueIndex(index) }
                            .padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(item.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Перетащить трек",
                            modifier = Modifier.size(44.dp).pointerInput(index, s.queue, rowHeight) {
                                detectVerticalDragGestures(
                                    onDragStart = { dragOffset = 0f },
                                    onDragCancel = { dragOffset = 0f },
                                    onDragEnd = {
                                        val target = (index + (dragOffset / rowHeight).roundToInt())
                                            .coerceIn(s.queue.indices)
                                        vm.player.moveQueueItem(index, target)
                                        dragOffset = 0f
                                    }
                                ) { change, amount ->
                                    change.consume()
                                    dragOffset = (dragOffset + amount).coerceIn(
                                        -index * rowHeight, (s.queue.lastIndex - index) * rowHeight
                                    )
                                }
                            }.padding(10.dp)
                        )
                        IconButton(onClick = { vm.player.removeFromQueue(index) }) { BrandIcon(R.drawable.glass_close, Icons.Default.Close, contentDescription = "Убрать из очереди") }
                    }
                }
            }
            return@Column
        }
        val remoteArtwork = artworkUri?.startsWith("https://", ignoreCase = true) == true
        val artwork = if (remoteArtwork) rememberRemoteArtwork(artworkUri) else rememberLocalArtwork(artworkUri)
        val primary = MaterialTheme.colorScheme.primary
        val accent = rememberArtworkAccent(artwork)
        val glowColor by animateColorAsState(
            targetValue = accent?.let { if (isGlass) androidx.compose.ui.graphics.lerp(primary, it, .66f).copy(alpha = .38f) else it.copy(alpha = .48f) }
                ?: primary.copy(alpha = 0f),
            animationSpec = tween(durationMillis = 420),
            label = "Artwork glow"
        )
        val lamp = LocalLamp.current
        val glass = isGlass
        val artworkWidthFraction = .86f
        BoxWithConstraints(
            modifier = (if (compact) Modifier.height(160.dp) else Modifier.weight(1f))
                .fillMaxWidth()
                .drawWithCache {
                    val color = glowColor
                    val radius = minOf(size.width * artworkWidthFraction, size.height) * .83f
                    val halo = if (color.alpha > 0f && radius > 0f) Brush.radialGradient(
                                0f to color,
                                .58f to color,
                                .78f to color.copy(alpha = color.alpha * .45f),
                                1f to color.copy(alpha = 0f),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = radius
                        ) else null
                    onDrawBehind { if (halo != null) drawCircle(halo, radius) }
                },
            contentAlignment = Alignment.Center
        ) {
                val artworkSize = minOf(maxWidth * artworkWidthFraction, maxHeight)
                if (glass) ArtworkReflection(artwork, Modifier.size(artworkSize))
                Box(
                    modifier = Modifier.size(artworkSize).then(if (glass) Modifier.onGloballyPositioned {
                        lamp.value = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)
                    } else Modifier).aspectRatio(1f).sharedCover().clip(RoundedCornerShape(if (style == "classic") 28.dp else 30.dp))
                        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer))),
                    contentAlignment = Alignment.Center
                ) {
                    val artModifier = Modifier.fillMaxSize()
                    if (!artworkUri.isNullOrBlank()) {
                        if (remoteArtwork) {
                            RemoteArtworkBitmap(artwork, "Обложка ${s.title}", artModifier)
                        } else {
                            LocalArtworkImage(artwork, "Обложка ${s.title}", artModifier)
                        }
                    } else {
                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(96.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    track?.let { Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)) { com.nexus.music.ui.components.PlatformArtworkBadge(it.source) } }
                }
        }

        Spacer(Modifier.height(8.dp))
        Text(s.title.ifBlank { "Ничего не играет" }, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val albumLabel = availableAlbumTitle(s.album, track?.source)
        if (s.artist.isNotBlank() || albumLabel != null) Row(
            modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
        ) {
            if (s.artist.isNotBlank()) Text(
                text = s.artist,
                modifier = Modifier.weight(1f, fill = false).then(
                    if (track != null && s.artist.isNotBlank()) Modifier.clickable { vm.openArtist(track) }
                    else Modifier
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (track != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (albumLabel != null) {
                Text(if (s.artist.isBlank()) albumLabel else " · $albumLabel", modifier = Modifier.weight(1f, fill = false).then(if (track != null) Modifier.clickable { vm.openAlbum(track) } else Modifier), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = s.mediaId != null, onClick = { vm.toggleCurrentFavorite() }) {
                BrandIcon(if (isFavorite) R.drawable.glass_heartfill else R.drawable.glass_favorite, if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Убрать из избранного" else "В избранное",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(enabled = s.mediaId != null, onClick = {
                s.mediaId?.let(vm::toggleDisliked)
            }) {
                BrandIcon(R.drawable.glass_brokenheart, Icons.Default.HeartBroken, "Не нравится",
                    tint = if (s.mediaId in disliked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(enabled = track != null, onClick = { showTrackMenu = true }) {
                    BrandIcon(R.drawable.glass_more, Icons.Default.MoreVert, "Действия с треком")
                }
                DropdownMenu(
                    expanded = showTrackMenu,
                    onDismissRequest = { showTrackMenu = false },
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    if (track?.source in listOf("soundcloud","local")) DropdownMenuItem(text = { Text("Исполнитель") }, onClick = {
                        track?.let(vm::openArtist)
                        showTrackMenu = false
                    })
                    track?.let { selected ->
                        val transfer = offlineTransfers[selected.id]
                        val offlineStatus = when {
                            transfer != null && transfer.total > 0 ->
                                "Копирование: ${(100.0 * transfer.bytes / transfer.total).toInt().coerceIn(0, 100)}%"
                            transfer != null -> "Копирование: ${transfer.bytes / 1024} КБ"
                            selected.downloadedPath != null -> "Доступен офлайн"
                            OfflineAudio.canSave(selected) -> "Офлайн-копии нет"
                            selected.source == "soundcloud" -> "В auvyx офлайн-копии SoundCloud недоступны"
                            else -> "Офлайн-сохранение недоступно"
                        }
                        DropdownMenuItem(text = { Text(offlineStatus) }, enabled = false, onClick = {})
                        when {
                            transfer != null -> DropdownMenuItem(
                                text = { Text("Отменить копирование") }, onClick = { vm.cancelOffline(selected) }
                            )
                            selected.downloadedPath != null && selected.source == "local" -> DropdownMenuItem(
                                text = { Text("Удалить офлайн-копию") }, onClick = { vm.removeOffline(selected); showTrackMenu = false }
                            )
                            OfflineAudio.canSave(selected) -> DropdownMenuItem(
                                text = { Text("Сохранить офлайн") }, onClick = { vm.saveOffline(selected); showTrackMenu = false }
                            )
                        }
                        if (com.nexus.music.AppViewModel.AlbumTarget.from(selected) != null) {
                            DropdownMenuItem(text = { Text("Альбом") }, onClick = {
                                vm.openAlbum(selected)
                                showTrackMenu = false
                            })
                        }
                        DropdownMenuItem(text = { Text("Инструменты трека") }, onClick = {
                            vm.trackToolsTarget.value = selected
                            showTrackMenu = false
                        })
                        DropdownMenuItem(text = { Text("Удалить из медиатеки") }, onClick = {
                            deleteTarget = selected
                            showTrackMenu = false
                        })
                    }
                    DropdownMenuItem(text = { Text("В плейлист…") }, onClick = {
                        showTrackMenu = false
                        showPlaylist = true
                    })
                    DropdownMenuItem(text = { Text("Играть следующим") }, onClick = {
                        track?.let(vm.player::playNext)
                        showTrackMenu = false
                    })
                    DropdownMenuItem(text = { Text("Остановить воспроизведение") }, onClick = {
                        vm.player.stop()
                        showTrackMenu = false
                    })
                    DropdownMenuItem(text = { Text("Добавить в очередь") }, onClick = {
                        track?.let(vm.player::enqueue)
                        showTrackMenu = false
                    })
                }
            }
        }

        key(s.mediaId) { PlaybackProgress(vm, s.durationMs, s.canSeek) }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).frosted(MaterialTheme.shapes.extraLarge, GlassRole.Transport),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.player.toggleShuffle() }) {
                BrandIcon(R.drawable.glass_shuffle, Icons.Default.Shuffle, contentDescription = "Перемешать", tint = if (s.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { vm.player.previous() }) { BrandIcon(R.drawable.glass_previous, Icons.Default.SkipPrevious, contentDescription = "Предыдущий", modifier = Modifier.size(36.dp)) }
            val playCorner by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (s.showPause) 20.dp else 32.dp,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "Play button shape"
            )
            FilledIconButton(
                onClick = { vm.player.togglePlayPause() },
                modifier = Modifier.size(64.dp),
                colors = if (isGlass) androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary
                ) else androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(),
                shapes = androidx.compose.material3.IconButtonDefaults.shapes(
                    shape = RoundedCornerShape(playCorner),
                    pressedShape = MaterialTheme.shapes.large
                )
            ) {
                BrandIcon(if (s.showPause) R.drawable.glass_pause else R.drawable.glass_play, if (s.showPause) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (s.showPause) "Пауза" else "Играть", modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = { vm.player.next() }) { BrandIcon(R.drawable.glass_next, Icons.Default.SkipNext, contentDescription = "Следующий", modifier = Modifier.size(36.dp)) }
            IconButton(onClick = { vm.player.cycleRepeat() }) {
                BrandIcon(if (s.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.glass_repeatone else R.drawable.glass_repeat, if (s.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Повтор",
                    tint = if (s.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { setVolume(volume - 1) }, modifier = Modifier.size(36.dp)) {
                BrandIcon(R.drawable.glass_volumelow, Icons.Default.VolumeDown, "Тише")
            }
            ExpressiveSlider(value = volume, onValueChange = ::setVolume, valueRange = 0f..maxVolume.toFloat(),
                modifier = Modifier.weight(1f))
            IconButton(onClick = { setVolume(volume + 1) }, modifier = Modifier.size(36.dp)) {
                BrandIcon(R.drawable.glass_volume, Icons.Default.VolumeUp, "Громче")
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = s.mediaId != null, onClick = { panel = PlayerPanel.Lyrics }) {
                BrandIcon(R.drawable.glass_lyrics, Icons.Default.Lyrics, "Текст песни")
            }
            val outputLabel = currentOutput?.let { audioDeviceLabel(it, nickname) } ?: "Системный вывод"
            TextButton(modifier = Modifier.weight(1f), onClick = {
                if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                } else showOutput = true
            }) {
                if (isGlass) Icon(painterResource(outputIcon(currentOutput)), null, Modifier.size(20.dp))
                Text(outputLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BrandIcon(R.drawable.glass_chevrondown, Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = { panel = PlayerPanel.Sound }) {
                BrandIcon(R.drawable.glass_equalizer, Icons.Default.Tune, "Эквалайзер и Speed Lab")
            }
        }
        if (isGlass && s.mediaId != null) {
            LyricsPreview(vm, s.mediaId.orEmpty()) { panel = PlayerPanel.Lyrics }
        }
        Spacer(Modifier.height(6.dp))
    }

    deleteTarget?.let { selected ->
        AlertDialog(
            modifier = Modifier.frosted(MaterialTheme.shapes.extraLarge, GlassRole.Panel),
            containerColor = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить из медиатеки?") },
            text = { Text("«${selected.title}» будет удалён из медиатеки auvyx. Исходный файл останется на устройстве.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteTrack(selected); deleteTarget = null }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена") } }
        )
    }
    if (showPlaylist && track != null) {
        AddToPlaylistDialog(vm, track, onDismiss = { showPlaylist = false })
    }
    if (panel == PlayerPanel.Lyrics && s.mediaId != null) {
        key(s.mediaId) {
            TrackLyricsSheet(vm, s.mediaId.orEmpty(), s.title, s.artist, s.durationMs, vm.player.position.collectAsStateWithLifecycle().value,
                track?.takeIf { it.source == "soundcloud" }?.sourceUrl) { if (panel == PlayerPanel.Lyrics) panel = null }
        }
    }
    if (panel == PlayerPanel.Sound) {
        SoundToolsSheet(vm, eqGains, eqEnabled, eqPreset, s.speed, s.pitch) { if (panel == PlayerPanel.Sound) panel = null }
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
private fun PlaybackProgress(vm: AppViewModel, durationMs: Long, canSeek: Boolean) {
    val pos by vm.player.position.collectAsStateWithLifecycle()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val duration = durationMs.coerceAtLeast(1L)
    val sliderValue = if (dragging) dragValue else pos.toFloat().coerceIn(0f, duration.toFloat())

    ExpressiveSlider(
        value = sliderValue,
        enabled = canSeek,
        onValueChange = { dragging = true; dragValue = it },
        onValueChangeFinished = { vm.player.seekTo(dragValue.toLong()); dragging = false },
        valueRange = 0f..duration.toFloat()
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTime(sliderValue.toLong()), style = MaterialTheme.typography.bodySmall)
        Text(formatTime(durationMs), style = MaterialTheme.typography.bodySmall)
    }

}

@Composable
private fun LyricsPreview(vm: AppViewModel, id: String, onOpen: () -> Unit) {
    val pos by vm.player.position.collectAsStateWithLifecycle()
    val text by remember(id) { vm.lyrics(id) }.collectAsStateWithLifecycle(initialValue = "")
    val lines = remember(text) { com.nexus.music.playback.timedLyrics(text) }
    val preview = lines.getOrNull(com.nexus.music.playback.activeLyricIndex(lines, pos))?.second
        ?: lines.firstOrNull()?.second ?: text.lineSequence().firstOrNull()
    if (!preview.isNullOrBlank()) TextButton(onClick = onOpen) {
        Text(preview, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private enum class PlayerPanel { Sound, Lyrics }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerBottomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val sheet = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        shape = MaterialTheme.shapes.extraLarge,
        // The sheet owns its surface. A clip on the outer modifier stays above
        // Material's moving anchor and can cut the sheet in its translated position.
        dragHandle = if (isGlass) null else ({ androidx.compose.material3.BottomSheetDefaults.DragHandle() }),
        containerColor = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.90f).frosted(MaterialTheme.shapes.extraLarge, GlassRole.Panel)) {
            if (isGlass) Box(Modifier.align(Alignment.CenterHorizontally)) { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SoundToolsSheet(
    vm: AppViewModel,
    gains: List<Float>,
    enabled: Boolean,
    preset: String,
    speed: Float,
    pitch: Float,
    onDismiss: () -> Unit
) {
    PlayerBottomSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().weight(1f).testTag("sound-tools-scroll")
            .verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Эквалайзер", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = vm::setEqualizerEnabled)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("default" to "Default", "bass_boost" to "Bass Boosted", "treble" to "Treble Bass",
                    "vocal" to "Vocal Boost", "custom" to "Custom").forEach { (key, label) ->
                    ExpressiveFilterChip(selected = preset == key, onClick = { vm.setEqualizerPreset(key) }, label = { Text(label) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("60", "150", "400", "1k", "2.4k", "15k").forEachIndexed { index, name ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        val gain = gains.getOrElse(index) { 0f }.coerceIn(-12f, 12f)
                        val slider = remember { SliderState(gain, 0, -12f..12f) }
                        slider.value = gain
                        VerticalSlider(
                            state = slider,
                            onValueChange = { vm.setEqualizerBand(index, it) },
                            modifier = Modifier.height(126.dp).fillMaxWidth().semantics {
                                contentDescription = "${SixBandEqualizer.FREQUENCIES[index].toInt()} Гц"
                            },
                            enabled = enabled,
                            topToBottom = false
                        )
                        Text(name, style = MaterialTheme.typography.labelSmall)
                        Text("%+.1f".format(gain), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Speed Lab", style = MaterialTheme.typography.titleLarge)
            Text("Скорость · %.2f×".format(speed))
            ExpressiveSlider(value = speed.coerceIn(0.5f, 2f), onValueChange = vm.player::setSpeed, valueRange = 0.5f..2f)
            Text("Тон · %.2f×".format(pitch))
            ExpressiveSlider(value = pitch.coerceIn(0.5f, 2f), onValueChange = vm.player::setPitch, valueRange = 0.5f..2f)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { vm.player.setSpeedAndPitch(0.8f, 0.85f) }) { Text("Slowed") }
                TextButton(onClick = vm.player::resetSpeedLab) { Text("Original") }
                TextButton(onClick = { vm.player.setSpeedAndPitch(1.25f, 1.25f) }) { Text("Speed Up") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackLyricsSheet(vm: AppViewModel, mediaId: String, title: String, artist: String,
                             durationMs: Long, position: Long, soundCloudPermalink: String?, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    // Null means DataStore has not produced its first value yet: never overwrite a saved LRC.
    val content by remember(mediaId) { vm.lyrics(mediaId).map { it as String? } }
        .collectAsStateWithLifecycle(initialValue = null)
    var lookupState by remember(mediaId) { mutableStateOf("idle") }
    var lookupAttempt by remember(mediaId) { mutableIntStateOf(0) }
    val hasReadSavedLyrics = content != null
    LaunchedEffect(mediaId, hasReadSavedLyrics, lookupAttempt, soundCloudPermalink) {
        if (hasReadSavedLyrics && content.isNullOrBlank()) {
            lookupState = "loading"
            try {
                val result = LyricsRepository.findForTrack(title, artist, durationMs, soundCloudPermalink)
                if (result != null) {
                    vm.saveDiscoveredLyrics(mediaId, result)
                    lookupState = "found"
                } else lookupState = "missing"
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                lookupState = "failed"
            }
        }
    }
    var error by remember(mediaId) { mutableStateOf(false) }
    var requestedId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val owner = requestedId
        if (uri != null && owner != null) scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { input ->
                        val buffer = CharArray(65_537)
                        var count = 0
                        while (count < buffer.size) {
                            val read = input.read(buffer, count, buffer.size - count)
                            if (read < 0) break
                            count += read
                        }
                        check(count < buffer.size)
                        String(buffer, 0, count).trim().also { check(it.isNotEmpty()) }
                    }
                }.getOrNull()
            }
            error = loaded == null
            if (loaded != null) vm.saveLyrics(owner, loaded)
        }
    }
    val lyrics = content.orEmpty()
    PlayerBottomSheet(onDismiss) {
        LyricsContent(lyrics, position, modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (error) Text("Не удалось прочитать файл текста.")
            if (lyrics.isBlank()) when (lookupState) {
                "missing" -> Text("Текст песни не найден.")
                "failed" -> Text("Не удалось загрузить текст песни.")
                else -> Text("Ищем текст песни…")
            }
            if (lyrics.isBlank() && lookupState in listOf("missing", "failed")) {
                TextButton(onClick = { lookupAttempt++ }) { Text("Повторить поиск") }
            }
            TextButton(onClick = {
                requestedId = mediaId
                picker.launch(arrayOf("text/*", "application/octet-stream"))
            }) { Text("Импортировать LRC или TXT") }
        }
    }
}

@Composable
internal fun LyricsContent(
    lyrics: String,
    position: Long,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null
) {
    val timed = remember(lyrics) { com.nexus.music.playback.timedLyrics(lyrics) }
    val plain = remember(lyrics, timed) { if (timed.isEmpty() && lyrics.isNotBlank()) lyrics.lines() else emptyList() }
    val active = com.nexus.music.playback.activeLyricIndex(timed, position)
    val listState = rememberLazyListState()
    var follow by remember(lyrics) { mutableStateOf(true) }
    val manualScroll = remember(timed) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f && timed.isNotEmpty()) follow = false
                return Offset.Zero
            }
        }
    }
    val headerCount = if (header == null) 0 else 1
    LaunchedEffect(active, follow, lyrics) {
        if (follow && timed.isNotEmpty()) {
            val index = if (active >= 0) active + headerCount else 0
            if (listState.layoutInfo.visibleItemsInfo.none { item ->
                item.index == index && item.offset >= listState.layoutInfo.viewportStartOffset &&
                    item.offset + item.size <= listState.layoutInfo.viewportEndOffset
            }) listState.scrollToItem(index)
        }
    }
    Column(modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).testTag("lyrics-scroll").nestedScroll(manualScroll),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp)
        ) {
            if (header != null) item { header() }
            items(plain) { line -> Text(line, modifier = Modifier.padding(vertical = 3.dp)) }
            itemsIndexed(timed) { index, line ->
                val highlighted = index == active
                Text(
                    line.second,
                    modifier = Modifier.padding(vertical = 7.dp).semantics { selected = highlighted },
                    color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!follow) TextButton(onClick = { follow = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text("К текущей строке")
        }
    }
}

@Composable
private fun AudioOutputDialog(
    nickname: String?,
    onSelect: (AudioDeviceInfo?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    var revision by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.DisposableEffect(audioManager) {
        val callback = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(devices: Array<AudioDeviceInfo>) { revision++ }
            override fun onAudioDevicesRemoved(devices: Array<AudioDeviceInfo>) {
                if (devices.any { it.id == PlaybackAudioRouting.preferredDeviceId }) PlaybackAudioRouting.select(null)
                revision++
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }
    val outputs = remember(revision) {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isSink && it.type in SUPPORTED_OUTPUT_TYPES }
            .sortedWith(compareBy<AudioDeviceInfo> { if (it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) 0 else 1 }.thenBy { it.id })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.frosted(MaterialTheme.shapes.extraLarge, GlassRole.Panel),
        containerColor = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Устройство вывода") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Системный выбор устройства")
                }
                outputs.forEach { device ->
                    TextButton(onClick = { onSelect(device) }, modifier = Modifier.fillMaxWidth()) {
                        if (isGlass) Icon(painterResource(outputIcon(device)), null, Modifier.size(22.dp))
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
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_HDMI,
    AudioDeviceInfo.TYPE_LINE_ANALOG,
    AudioDeviceInfo.TYPE_LINE_DIGITAL,
    AudioDeviceInfo.TYPE_DOCK,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER
)

private fun outputIcon(device: AudioDeviceInfo?): Int = when (device?.type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> R.drawable.glass_phone
    AudioDeviceInfo.TYPE_BLE_HEADSET -> R.drawable.glass_earbuds
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_SPEAKER -> R.drawable.glass_bluetooth
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET -> R.drawable.glass_headphones
    else -> R.drawable.glass_audio_output
}

private fun audioDeviceLabel(device: AudioDeviceInfo, nickname: String?): String {
    if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
        val owner = nickname?.trim()?.takeIf { it.isNotEmpty() }
        return if (owner == null) "Телефон" else "$owner's phone"
    }
    val typeLabel = when (device.type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
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
    return if (name.isNullOrBlank() || name == "0") typeLabel else name
}

private fun mediaOutput(manager: AudioManager, playing: Boolean): AudioDeviceInfo? {
    val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    if (Build.VERSION.SDK_INT >= 31 && playing) {
        val active = runCatching {
            manager.activePlaybackConfigurations.filter { it.audioAttributes.usage == AudioAttributes.USAGE_MEDIA }
                .mapNotNull { it.audioDeviceInfo }.distinctBy { it.id }
        }.getOrNull()
        active?.singleOrNull()?.let { return it }
    }
    if (Build.VERSION.SDK_INT >= 33) {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val route = runCatching { manager.getAudioDevicesForAttributes(attributes).firstOrNull() }
            .getOrNull() ?: return null
        return outputs.firstOrNull { it.type == route.type && it.address == route.address }
    }
    return outputs.filter { it.type in SUPPORTED_OUTPUT_TYPES }.singleOrNull()
}
