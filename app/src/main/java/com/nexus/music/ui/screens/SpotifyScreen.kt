package com.nexus.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.nexus.music.BuildConfig
import com.nexus.music.data.catalog.SpotifyPlayback
import com.nexus.music.data.catalog.SpotifyItem
import com.nexus.music.data.catalog.SpotifyWebApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The bearer token stays in memory and is never written to saved state or disk.
internal class SpotifySession {
    var token by mutableStateOf<String?>(null)
}

@Composable
internal fun SpotifyScreen(session: SpotifySession, query: String, onClose: () -> Unit) {
    var type by rememberSaveable { mutableStateOf("track") }
    val clientId = BuildConfig.SPOTIFY_CLIENT_ID
    var playback by remember(session.token) { mutableStateOf<SpotifyPlayback?>(null) }
    var signingIn by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf<SpotifyItem?>(null) }
    var parentArtist by remember { mutableStateOf<SpotifyItem?>(null) }
    val token = session.token
    var playbackError by remember(token) { mutableStateOf<String?>(null) }
    var playbackBusy by remember(token) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var offset by remember(query, type, page, token) { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    var results by remember(query, type, page, token) { mutableStateOf(emptyList<SpotifyItem>()) }
    var hasMore by remember(query, type, page, token) { mutableStateOf(false) }
    var error by remember(query, type, page, token) { mutableStateOf<String?>(null) }
    var loading by remember(query, type, page, token) { mutableStateOf(false) }

    fun control(action: suspend (String) -> Unit) {
        val account = token ?: return
        if (playbackBusy) return
        playbackBusy = true
        playbackError = null
        scope.launch {
            try {
                action(account)
                delay(550)
                playback = SpotifyWebApi.playback(account)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                playbackError = e.message ?: "Команда Spotify не выполнена."
            } finally {
                playbackBusy = false
            }
        }
    }

    LaunchedEffect(token) {
        if (token == null) return@LaunchedEffect
        while (true) {
            try {
                if (!playbackBusy) playback = SpotifyWebApi.playback(token)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Catalog access can remain available when playback access is denied.
            }
            delay(5000)
        }
    }

    fun back() {
        if (page == null) onClose()
        else {
            page = parentArtist
            parentArtist = null
        }
    }
    BackHandler { back() }

    LaunchedEffect(query, type, page, token, offset, retry) {
        if (token == null || (page == null && query.isBlank())) return@LaunchedEffect
        loading = true
        error = null
        try {
            if (offset == 0 && page == null) delay(350)
            val (items, more) = page?.let { SpotifyWebApi.collection(token, it, offset) }
                ?: SpotifyWebApi.search(token, query.trim(), type, offset)
            results = ((if (offset == 0) emptyList() else results) + items).distinctBy { it.id }
            hasMore = more
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Не удалось загрузить каталог Spotify."
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row {
            if (page != null) TextButton(onClick = { back() }) { Text("Назад") }
            if (token != null) TextButton(onClick = { session.token = null }) { Text("Выйти из Spotify") }
        }
        if (token == null) {
            if (clientId.isBlank()) {
                Text("Spotify не подключён в этой сборке.")
                TextButton(onClick = { uriHandler.openUri("https://open.spotify.com/") }) { Text("Открыть Spotify") }
                return@Column
            }
            Button(enabled = !signingIn, onClick = {
                signingIn = true
                loginError = null
                scope.launch {
                    try {
                        session.token = SpotifyWebApi.authorize(clientId.trim(), uriHandler::openUri)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        loginError = e.message ?: "Вход не удался."
                    } finally {
                        signingIn = false
                    }
                }
            }) { Text(if (signingIn) "Ожидание входа…" else "Войти в Spotify") }
            loginError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } else {
            val current = page
            if (current == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("track" to "Треки", "artist" to "Артисты", "album" to "Альбомы").forEach { (key, title) ->
                        FilterChip(type == key, { type = key }, label = { Text(title) })
                    }
                }
            } else {
                Text(current.title, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { uriHandler.openUri("https://open.spotify.com/${current.type}/${current.id}") }) {
                    Text("Открыть в Spotify")
                }
            }
            playback?.let { state ->
                Text("Spotify · ${state.title}")
                Row {
                    TextButton(enabled = !playbackBusy, onClick = { control { SpotifyWebApi.command(it, "previous") } }) { Text("Назад") }
                    TextButton(enabled = !playbackBusy, onClick = {
                        control { SpotifyWebApi.command(it, if (state.playing) "pause" else "play") }
                    }) { Text(if (state.playing) "Пауза" else "Играть") }
                    TextButton(enabled = !playbackBusy, onClick = { control { SpotifyWebApi.command(it, "next") } }) { Text("Далее") }
                }
                if (state.durationMs > 0) {
                    var seek by remember(state.title) { mutableStateOf<Float?>(null) }
                    Slider(
                        value = seek ?: (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f),
                        enabled = !playbackBusy,
                        onValueChange = { seek = it },
                        onValueChangeFinished = {
                            seek?.let { fraction -> control { SpotifyWebApi.command(it, "seek", (fraction * state.durationMs).toLong()) } }
                            seek = null
                        }
                    )
                }
            }
            playbackError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(Modifier.fillMaxWidth()) {
                items(results, key = { it.id }) { item ->
                    ListItem(
                        headlineContent = { Text(item.title) },
                        supportingContent = { Text(item.subtitle.ifBlank { if (item.type == "artist") "Исполнитель" else "Spotify" }) },
                        modifier = Modifier.clickable {
                            if (item.type == "track") {
                                control { SpotifyWebApi.play(it, item) }
                            } else {
                                parentArtist = page?.takeIf { it.type == "artist" }
                                page = item
                            }
                        },
                        trailingContent = if (item.type == "track") ({
                            TextButton(enabled = !playbackBusy, onClick = {
                                control { SpotifyWebApi.enqueue(it, item) }
                            }) { Text("В очередь") }
                        }) else null
                    )
                }
                item {
                    when {
                        error != null -> {
                            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { retry++ }) { Text("Повторить") }
                        }
                        loading -> CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp))
                        hasMore -> TextButton(onClick = { offset += 10 }) { Text("Ещё") }
                        results.isEmpty() && (query.isNotBlank() || page != null) -> Text("Результатов нет")
                        query.isBlank() && page == null -> Text("Введи название трека, артиста или альбома.")
                    }
                }
            }
        }
    }
}
