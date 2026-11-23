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

private val artworkCache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

private fun artworkCandidates(raw: String): List<String> {
    val clean = raw.substringBefore('?')
    val query = raw.substringAfter('?', "").takeIf { raw.contains('?') }?.let { "?$it" }.orEmpty()
    val large = clean.replace(Regex("-(?:small|badge|t\\d+x\\d+|large|original)(?=\\.)"), "-t500x500") + query
    val medium = clean.replace(Regex("-(?:small|badge|original)(?=\\.)"), "-large") + query
    return listOf(large, medium, raw).distinct()
}

private fun loadArtwork(url: String): Bitmap? {
    artworkCache.get(url)?.let { return it }
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 12_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "AUVYX Android Music Player")
        setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
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
            else artworkCandidates(imageUrl).firstNotNullOfOrNull { candidate ->
                runCatching { loadArtwork(candidate) }.getOrNull()
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

/** Extract one representative saturated colour only when artwork changes, never per frame. */
@Composable
fun rememberArtworkAccent(artworkUri: String?, fallback: androidx.compose.ui.graphics.Color): androidx.compose.runtime.State<androidx.compose.ui.graphics.Color> {
    val context = androidx.compose.ui.platform.LocalContext.current
    return produceState(initialValue = fallback, artworkUri, fallback) {
        value = fallback
        if (artworkUri.isNullOrBlank()) return@produceState
        val found = withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = if (artworkUri.startsWith("https://", true)) {
                    artworkCandidates(artworkUri).firstNotNullOfOrNull { candidate ->
                        runCatching { loadArtwork(candidate) }.getOrNull()
                    }
                } else {
                    context.contentResolver.openInputStream(android.net.Uri.parse(artworkUri))?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 8 })
                    }
                }
                bitmap?.let(::vibrantArtworkColor)
            }.getOrNull()
        }
        if (found != null) value = found
    }
}

private fun vibrantArtworkColor(bitmap: Bitmap): androidx.compose.ui.graphics.Color? {
    val mini = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
    try {
        val totals = FloatArray(12)
        val saturation = FloatArray(12)
        val brightness = FloatArray(12)
        val hsv = FloatArray(3)
        for (y in 0 until mini.height) for (x in 0 until mini.width) {
            android.graphics.Color.colorToHSV(mini.getPixel(x, y), hsv)
            if (hsv[1] < .24f || hsv[2] < .22f) continue
            val bucket = ((hsv[0] / 30f).toInt()).coerceIn(0, 11)
            val weight = hsv[1] * hsv[1] * hsv[2]
            totals[bucket] += weight
            saturation[bucket] += hsv[1] * weight
            brightness[bucket] += hsv[2] * weight
        }
        val best = totals.indices.maxByOrNull(totals::get) ?: return null
        if (totals[best] < .2f) return null
        val color = android.graphics.Color.HSVToColor(floatArrayOf(
            best * 30f + 15f,
            (saturation[best] / totals[best]).coerceIn(.40f, .85f),
            (brightness[best] / totals[best]).coerceIn(.50f, .90f)
        ))
        return androidx.compose.ui.graphics.Color(color)
    } finally {
        if (mini !== bitmap) mini.recycle()
    }
}
