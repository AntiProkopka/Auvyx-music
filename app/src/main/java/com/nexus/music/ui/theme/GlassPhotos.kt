package com.nexus.music.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.data.settings.GlassAccent
import com.nexus.music.data.settings.GlassQuality
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val photoCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

internal val LocalPhotos = staticCompositionLocalOf<Map<GlassAccent, List<String>>> { emptyMap() }

@Composable
internal fun loadPhotoCatalog(): Map<GlassAccent, List<String>> {
    val assets = LocalContext.current.assets
    return produceState(emptyMap<GlassAccent, List<String>>(), assets) {
        value = withContext(Dispatchers.IO) {
            GlassAccent.entries.associateWith { group ->
                val folder = "wallpapers/" + group.photoFolder
                assets.list(folder).orEmpty().filter {
                    it.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "webp")
                }.sorted().map { "$folder/$it" }
            }
        }
    }.value
}

@Composable
internal fun groupPhoto(vm: AppViewModel, alternate: Boolean = false): String? {
    val accent by vm.accent.collectAsStateWithLifecycle()
    val selected by vm.photo.collectAsStateWithLifecycle()
    val photos = LocalPhotos.current[accent].orEmpty()
    val savedName = selected.substringAfterLast('/').substringBeforeLast('.')
    val index = photos.indexOfFirst {
        it == selected || it.substringAfterLast('/').substringBeforeLast('.') == savedName
    }.coerceAtLeast(0)
    return if (alternate && photos.size > 1) photos[(index + 1) % photos.size] else photos.getOrNull(index)
}

@Composable
internal fun GlassPhoto(path: String?, modifier: Modifier, small: Boolean = false) {
    BoxWithConstraints(modifier) {
        val assets = LocalContext.current.assets
        val density = LocalDensity.current
        val width = with(density) { maxWidth.roundToPx() }.coerceIn(1, if (small) 256 else 1600)
        val height = with(density) { maxHeight.roundToPx() }.coerceIn(1, if (small) 256 else 2000)
        val bitmap by produceState<Bitmap?>(null, assets, path, width, height) {
            value = withContext(Dispatchers.IO) {
                if (path == null) return@withContext null
                val key = "$path/${width}x$height"
                photoCache.get(key) ?: try {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
                    if (options.outWidth <= 0 || options.outHeight <= 0) return@withContext null
                    var sample = 1
                    while ((options.outWidth / (sample * 2) >= width && options.outHeight / (sample * 2) >= height) ||
                        maxOf(options.outWidth, options.outHeight) / sample > 2400
                    ) {
                        sample *= 2
                    }
                    options.inJustDecodeBounds = false
                    options.inSampleSize = sample
                    assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
                        ?.also { photoCache.put(key, it) }
                } catch (_: IOException) {
                    null
                }
            }
        }
        Crossfade(bitmap, Modifier.fillMaxSize(), tween(450), label = "Wallpaper") { current ->
            current?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
    }
}

@Composable
internal fun GlassWallpaper(vm: AppViewModel) {
    val blur by vm.blur.collectAsStateWithLifecycle()
    val quality = LocalGlassQuality.current
    val radius = when (quality) {
        GlassQuality.Low -> blur * .25f
        GlassQuality.Medium -> blur * .42f
        GlassQuality.High -> blur * .72f
        GlassQuality.Ultra -> blur.toFloat()
    }
    GlassPhoto(groupPhoto(vm), Modifier.fillMaxSize().blur(radius.dp, BlurredEdgeTreatment.Rectangle))
}
