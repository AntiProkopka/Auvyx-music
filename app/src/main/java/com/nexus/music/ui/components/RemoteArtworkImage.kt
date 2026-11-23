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
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    RemoteArtworkBitmap(rememberRemoteArtwork(imageUrl), contentDescription, modifier)
}

@Composable
internal fun rememberRemoteArtwork(imageUrl: String?): Bitmap? = key(imageUrl) {
    produceState<Bitmap?>(null) {
        value = withContext(Dispatchers.IO) {
            if (imageUrl.isNullOrBlank() || !imageUrl.startsWith("https://", true)) null
            else artworkCandidates(imageUrl).firstNotNullOfOrNull { candidate ->
                runCatching { loadArtwork(candidate) }.getOrNull()
            }
        }
    }.value
}

@Composable
internal fun RemoteArtworkBitmap(bitmap: Bitmap?, contentDescription: String?, modifier: Modifier) {
    if (bitmap != null) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = contentDescription, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
internal fun rememberArtworkAccent(bitmap: Bitmap?, fallback: Color): Color? {
    val accent = key(bitmap) {
        produceState<Color?>(null) {
            if (bitmap != null) {
                value = withContext(Dispatchers.Default) { representativeArtworkColor(bitmap) }
            }
        }.value
    }
    return if (bitmap == null) null else accent ?: fallback
}

internal fun representativeArtworkColor(bitmap: Bitmap): Color? {
    val mini = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
    try {
        val totals = FloatArray(12)
        val saturation = FloatArray(12)
        val brightness = FloatArray(12)
        val hsv = FloatArray(3)
        var neutralBrightness = 0f
        var visiblePixels = 0
        for (y in 0 until mini.height) for (x in 0 until mini.width) {
            val pixel = mini.getPixel(x, y)
            if (android.graphics.Color.alpha(pixel) < 128) continue
            android.graphics.Color.colorToHSV(pixel, hsv)
            neutralBrightness += hsv[2]
            visiblePixels++
            if (hsv[1] < .24f || hsv[2] < .22f) continue
            val bucket = ((hsv[0] / 30f).toInt()).coerceIn(0, 11)
            val weight = hsv[1] * hsv[1] * hsv[2]
            totals[bucket] += weight
            saturation[bucket] += hsv[1] * weight
            brightness[bucket] += hsv[2] * weight
        }
        val best = totals.indices.maxByOrNull(totals::get) ?: return null
        if (visiblePixels == 0) return null
        if (totals[best] < .2f) {
            val gray = (neutralBrightness / visiblePixels).coerceIn(.45f, .85f)
            return Color(gray, gray, gray)
        }
        val color = android.graphics.Color.HSVToColor(floatArrayOf(
            best * 30f + 15f,
            (saturation[best] / totals[best]).coerceIn(.40f, .85f),
            (brightness[best] / totals[best]).coerceIn(.50f, .90f)
        ))
        return Color(color)
    } finally {
        if (mini !== bitmap) mini.recycle()
    }
}
