package com.nexus.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.nexus.music.AppViewModel
import com.nexus.music.data.catalog.CatalogKind
import com.nexus.music.data.catalog.NativeCatalog
import com.nexus.music.data.catalog.YouTubeMusicCatalog
import com.nexus.music.data.db.TrackEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal data class CatalogResults(val tracks: List<TrackEntity> = emptyList(), val status: String = "", val retry: (() -> Unit)? = null)

@Composable
internal fun catalogResults(query: String, enabled: Boolean): CatalogResults {
    var attempt by remember { mutableIntStateOf(0) }
    var rows by remember(query, enabled, attempt) { mutableStateOf(CatalogResults(status = "Поиск…")) }
    LaunchedEffect(query, enabled, attempt) {
        if (!enabled || query.isBlank()) return@LaunchedEffect
        delay(400)
        try {
            val tracks = YouTubeMusicCatalog.search(query.trim(), CatalogKind.Songs).map(NativeCatalog::youtube)
            rows = CatalogResults(tracks, if (tracks.isEmpty()) "Совпадений нет" else "")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { rows = CatalogResults(status = "Ошибка поиска", retry = { attempt++ }) }
    }
    return rows
}

internal fun LazyListScope.catalogRows(result: CatalogResults, short: Boolean) {
    item { Text("YouTube Music", Modifier.padding(start = 16.dp, top = 12.dp), style = MaterialTheme.typography.titleSmall) }
    if (result.status.isNotBlank()) item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(result.status, Modifier.padding(horizontal = 16.dp))
            result.retry?.let { TextButton(onClick = it) { Text("Повторить") } }
        }
    }
    val tracks = if (short) result.tracks.take(4) else result.tracks
    items(tracks, key = { "youtube-${it.sourceId}" }) { track ->
        val uriHandler = LocalUriHandler.current
        val url = track.sourceUrl
        ListItem(
            headlineContent = { Text(track.title) },
            supportingContent = { Text("${track.artist} · Открыть в YouTube Music") },
            modifier = Modifier.clickable(enabled = url != null) { url?.let(uriHandler::openUri) }
        )
    }
}

@Composable
internal fun CatalogPage(vm: AppViewModel, url: String, initialTitle: String, onClose: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    val uri = android.net.Uri.parse(url)
    val handler = LocalUriHandler.current
    val supported = uri.scheme == "https" && uri.host in
        setOf("open.spotify.com", "www.youtube.com", "music.youtube.com")
    val destination = if (uri.host == "open.spotify.com") "Spotify" else "YouTube Music"
    val externalUrl = when {
        uri.host == "open.spotify.com" -> url.replace("/embed/", "/")
        uri.host == "www.youtube.com" && uri.path?.startsWith("/embed/") == true ->
            "https://music.youtube.com/watch?v=${android.net.Uri.encode(uri.lastPathSegment.orEmpty())}"
        else -> url
    }
    Column(Modifier.padding(16.dp)) {
        Text(initialTitle, style = MaterialTheme.typography.titleLarge)
        if (supported) TextButton(onClick = { handler.openUri(externalUrl) }) { Text("Открыть в $destination") }
        TextButton(onClick = onClose) { Text("Назад") }
        TextButton(onClick = { vm.toggleCatalogPage(url, initialTitle); onClose() }) { Text("Удалить закладку") }
    }
}
