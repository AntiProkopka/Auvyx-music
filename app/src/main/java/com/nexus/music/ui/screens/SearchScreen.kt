package com.nexus.music.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.data.db.TrackEntity
import com.nexus.music.ui.components.AddToPlaylistDialog
import com.nexus.music.ui.components.EmptyHint
import com.nexus.music.ui.components.TrackRow
import com.nexus.music.ui.components.trackMenu

@Composable
fun SearchScreen(vm: AppViewModel) {
    var source by rememberSaveable { mutableStateOf("local") }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = source == "local", onClick = { source = "local" }, label = { Text("На устройстве") })
            FilterChip(selected = source == "soundcloud", onClick = { source = "soundcloud" }, label = { Text("SoundCloud") })
        }
        if (source == "local") LocalLibrarySearch(vm) else SoundCloudSearch()
    }
}

@Composable
private fun LocalLibrarySearch(vm: AppViewModel) {
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val current by vm.player.state.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<TrackEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.searchQuery.value = it },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            label = { Text("Название, исполнитель или альбом") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.searchQuery.value = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Очистить")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (query.isBlank()) {
            EmptyHint("Поиск по трекам, которые добавлены в локальную медиатеку.")
        } else if (results.isEmpty()) {
            EmptyHint("По этому запросу ничего не найдено.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(results, key = { _, t -> t.id }) { index, t ->
                    TrackRow(
                        track = t,
                        isCurrent = current.mediaId == t.id.toString(),
                        menuItems = trackMenu(vm, t) { pending = it },
                        onClick = { vm.player.playTracks(results, index) }
                    )
                }
            }
        }
    }
    pending?.let { AddToPlaylistDialog(vm, it) { pending = null } }
}

@Composable
private fun SoundCloudSearch() {
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    var activeUrl by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SoundCloud", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    "Вставь публичную ссылку на трек или плейлист. Воспроизведение идёт через официальный встроенный плеер SoundCloud.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Button(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://soundcloud.com")))
                }) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                    Text("Найти музыку")
                }
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it; error = null },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            label = { Text("Ссылка на SoundCloud") },
            placeholder = { Text("https://soundcloud.com/artist/track") }
        )
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val candidate = input.trim()
                val uri = runCatching { Uri.parse(candidate) }.getOrNull()
                val host = uri?.host?.lowercase()
                val validHost = host == "soundcloud.com" || host == "www.soundcloud.com" ||
                    host == "m.soundcloud.com" || host == "on.soundcloud.com"
                val path = uri?.path.orEmpty().trim('/')
                val validPath = path.contains('/') || (host == "on.soundcloud.com" && path.isNotBlank())
                if (uri != null && uri.scheme.equals("https", ignoreCase = true) && validHost && validPath) {
                    activeUrl = uri.toString()
                    error = null
                } else {
                    error = "Нужна публичная HTTPS-ссылка на трек или плейлист SoundCloud."
                }
            }, enabled = input.isNotBlank()) { Text("Открыть плеер") }
            androidx.compose.material3.OutlinedButton(onClick = { input = ""; activeUrl = ""; error = null }) {
                Text("Сбросить")
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp))
        }

        if (activeUrl.isBlank()) {
            EmptyHint("После выбора трека в SoundCloud скопируй его ссылку и вставь сюда. Треки с отключённым встраиванием могут не воспроизводиться.")
        } else {
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                SoundCloudWidget(url = activeUrl, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun SoundCloudWidget(url: String, modifier: Modifier = Modifier) {
    val playerUrl = remember(url) {
        Uri.parse("https://w.soundcloud.com/player/").buildUpon()
            .appendQueryParameter("url", url)
            .appendQueryParameter("color", "#6750A4")
            .appendQueryParameter("auto_play", "false")
            .appendQueryParameter("hide_related", "true")
            .appendQueryParameter("show_comments", "false")
            .appendQueryParameter("show_user", "true")
            .appendQueryParameter("visual", "true")
            .build().toString()
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                tag = playerUrl
                loadDataWithBaseURL(
                    "https://w.soundcloud.com/",
                    soundCloudEmbedHtml(playerUrl),
                    "text/html", "UTF-8", null
                )
            }
        },
        update = { webView ->
            if (webView.tag != playerUrl) {
                webView.tag = playerUrl
                webView.loadDataWithBaseURL(
                    "https://w.soundcloud.com/",
                    soundCloudEmbedHtml(playerUrl),
                    "text/html", "UTF-8", null
                )
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        }
    )
}

private fun soundCloudEmbedHtml(playerUrl: String): String {
    val htmlSafePlayerUrl = playerUrl.replace("&", "&amp;")
    return """
    <!doctype html>
    <html><head><meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>
      html,body{margin:0;padding:0;width:100%;height:100%;background:transparent;overflow:hidden}
      iframe{border:0;width:100%;height:100%;min-height:420px;border-radius:20px}
    </style></head>
    <body><iframe allow="autoplay" scrolling="no" src="$htmlSafePlayerUrl"></iframe></body></html>
""".trimIndent()
}
