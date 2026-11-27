package com.nexus.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.data.settings.FollowedArtist
import com.nexus.music.data.soundcloud.SoundCloudRepository
import com.nexus.music.data.soundcloud.SoundCloudTrack
import com.nexus.music.ui.LocalBottomBarPadding
import com.nexus.music.ui.theme.*
import kotlinx.coroutines.CancellationException

@Composable
internal fun GlassArtist(vm: AppViewModel, target: AppViewModel.ArtistTarget, onOpenPlayer: () -> Unit) {
    val library by vm.tracks.collectAsStateWithLifecycle()
    val local = remember(library, target) {
        library.filter { it.source == "local" && it.artist.trim().equals(target.name.trim(), true) }
    }
    val followed by vm.followed.collectAsStateWithLifecycle()
    val counts by vm.listenCounts.collectAsStateWithLifecycle()
    var profile by remember(target) { mutableStateOf<SoundCloudRepository.Artist?>(null) }
    var cloud by remember(target) { mutableStateOf(emptyList<SoundCloudTrack>()) }
    var releases by remember(target) { mutableStateOf(emptyList<SoundCloudRepository.Release>()) }
    var complete by remember(target) { mutableStateOf(!target.cloud) }
    var error by remember(target) { mutableStateOf<String?>(null) }
    var releaseError by remember(target) { mutableStateOf(false) }
    var retry by remember(target) { mutableIntStateOf(0) }
    LaunchedEffect(target, retry) {
        if (!target.cloud) return@LaunchedEffect
        error = null
        complete = false
        try {
            val artist = profile ?: SoundCloudRepository.artist(target.owner, target.permalink)
            profile = artist
            var cursor: String? = null
            val seen = mutableSetOf<String?>()
            val catalog = linkedMapOf<Long, SoundCloudTrack>()
            do {
                check(seen.add(cursor)) { "SoundCloud повторил страницу каталога" }
                val page = SoundCloudRepository.artistTracks(artist.id, cursor)
                page.tracks.forEach { catalog[it.id] = it }
                cloud = catalog.values.toList()
                cursor = page.next
            } while (cursor != null)
            complete = true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.localizedMessage ?: "Каталог недоступен" }
    }
    LaunchedEffect(profile?.id, retry) {
        val owner = profile?.id ?: return@LaunchedEffect
        releaseError = false
        try { releases = SoundCloudRepository.artistReleases(owner) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { releaseError = true }
    }
    val avatar = profile?.avatar ?: if (target.cloud) null else local.firstNotNullOfOrNull { it.artworkUri }
    val artist = FollowedArtist(profile?.name ?: target.name, profile?.id ?: target.owner, target.cloud, target.permalink, avatar)
    val playing by vm.player.state.collectAsStateWithLifecycle()
    val known by vm.knownTracks.collectAsStateWithLifecycle()
    val current = known.firstOrNull { it.id.toString() == playing.mediaId }
    val cloudPopular = remember(cloud) { cloud.filter { it.plays != null }.sortedByDescending { it.plays } }
    val localPopular = remember(local, counts) { local.filter { counts.containsKey(it.id) }.sortedByDescending { counts[it.id] } }
    val active = if (target.cloud) current?.source == "soundcloud" && artist.owner != null && current.artistId == artist.owner.toString()
        else local.any { it.id == current?.id }
    val playable = if (target.cloud) cloud.any { it.full } else local.isNotEmpty()
    fun play(shuffle: Boolean) {
        if (target.cloud) {
            val songs = (cloudPopular + cloud).distinctBy { it.id }.filter { it.full }
            vm.playSoundCloud(if (shuffle) songs.shuffled() else songs, 0)
        } else {
            val songs = (localPopular + local).distinctBy { it.id }
            vm.player.playTracks(if (shuffle) songs.shuffled() else songs, 0)
        }
    }
    val songs = if (target.cloud) cloud.map { track ->
        CoverItem(track.id.toString(), track.title, null, track.artworkUrl) {
            if (track.full) { vm.playTrack(track); onOpenPlayer() } else vm.messages.tryEmit("Недоступна полная версия")
        }
    } else local.map { track ->
        CoverItem(track.id.toString(), track.title, null, track.artworkUri) { vm.playTrack(track); onOpenPlayer() }
    }
    val bySong = songs.associateBy { it.key }
    val top = if (target.cloud) cloudPopular.take(10).mapNotNull { bySong[it.id.toString()] } else
        localPopular.take(10).mapNotNull { bySong[it.id.toString()] }
    fun releaseItem(r: SoundCloudRepository.Release) = CoverItem(r.id.toString(), r.title, null, r.artwork) { vm.openRelease(r) }
    val singles = releases.filter { it.type.equals("single", true) }.map(::releaseItem)
    val albums = if (target.cloud) releases.filterNot { it.type.equals("single", true) }.map(::releaseItem)
        else local.mapNotNull { track -> AppViewModel.AlbumTarget.from(track)?.let {
            CoverItem(it.title.lowercase(), it.title, null, track.artworkUri) { vm.openAlbum(track) }
        } }.distinctBy { it.key }

    LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomBarPadding.current + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = { vm.artistTarget.value = null }) { Text("← Назад") } }
        item { Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HaloArtwork(avatar, artist.name, Modifier.size(216.dp).align(androidx.compose.ui.Alignment.CenterHorizontally))
            Text(artist.name, style = MaterialTheme.typography.headlineLarge)
            profile?.description?.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 6) }
            TextButton(onClick = { vm.toggleFollow(artist) }, enabled = !target.cloud || artist.owner != null) {
                Text(if (followed.any { it.key == artist.key }) "Отписаться" else "Подписаться")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = complete && playable, onClick = { if (active && playing.showPause) vm.player.pause() else play(false) }) {
                    Text(if (active && playing.showPause) "Пауза" else "Слушать")
                }
                TextButton(enabled = complete && playable, onClick = { play(true) }) { Text("Перемешать") }
            }
            if (!complete && error == null) LinearProgressIndicator()
            if (error != null || releaseError) TextButton(onClick = { retry++ }) { Text("Повторить загрузку каталога") }
        } }
        item { Text("Популярные песни", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge) }
        if (top.isEmpty()) item { Text("Нет данных о прослушиваниях", Modifier.padding(horizontal = 16.dp)) }
        items(top, key = { it.key }) { song ->
            ListItem(modifier = Modifier.clickable(onClick = song.onClick), headlineContent = { Text(song.title) },
                leadingContent = { ArtTile(song.artwork, null, Modifier.size(48.dp)) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
        }
        item { CoverCarousel("Все песни", songs, empty = "Песни не найдены") }
        item { CoverCarousel("Синглы", singles, empty = "Нет данных о синглах") }
        item { CoverCarousel("Альбомы и другие релизы", albums, empty = "Нет данных о релизах") }
    }
}
