package com.nexus.music.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val artworkCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

private fun bestArtworkUrl(raw: String): String =
    raw.replace(Regex("-(?:small|badge|t\\d+x\\d+|large)(?=\\.)"), "-t500x500")

private fun loadArtwork(url: String): Bitmap? {
    artworkCache.get(url)?.let { return it }
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "AUVYX Android Music Player")
    }
    return try {
        connection.inputStream.use { input -> BitmapFactory.decodeStream(input) }
            ?.also { artworkCache.put(url, it) }
    } finally {
        connection.disconnect()
    }
}

@Composable
fun RemoteArtworkImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val bitmap = produceState<Bitmap?>(null, imageUrl) {
        value = withContext(Dispatchers.IO) {
            if (imageUrl.isNullOrBlank() || !imageUrl.startsWith("https://", true)) null
            else {
                val highResolution = bestArtworkUrl(imageUrl)
                runCatching { loadArtwork(highResolution) }.getOrNull()
                    ?: if (highResolution != imageUrl) runCatching { loadArtwork(imageUrl) }.getOrNull() else null
            }
        }
    }.value

    if (bitmap != null) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = contentDescription, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
        }
    }
}
