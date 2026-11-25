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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.data.settings.GlassAccent
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
                val folder = "wallpapers/" + group.name
                assets.list(folder).orEmpty().filter { it.endsWith(".webp") }.sorted().map { "$folder/$it" }
            }
        }
    }.value
}

@Composable
internal fun groupPhoto(vm: AppViewModel, alternate: Boolean = false): String? {
    val accent by vm.accent.collectAsStateWithLifecycle()
    val selected by vm.photo.collectAsStateWithLifecycle()
    val photos = LocalPhotos.current[accent].orEmpty()
    val index = photos.indexOf(selected).coerceAtLeast(0)
    return if (alternate && photos.size > 1) photos[(index + 1) % photos.size] else photos.getOrNull(index)
}

@Composable
internal fun GlassPhoto(path: String?, modifier: Modifier, small: Boolean = false) {
    val assets = LocalContext.current.assets
    val bitmap by produceState<Bitmap?>(null, path, small) {
        value = withContext(Dispatchers.IO) {
            path?.let {
                val key = "$path/$small"
                photoCache.get(key) ?: assets.open(path).use { stream ->
                    BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = if (small) 4 else 1 })
                }?.also { photoCache.put(key, it) }
            }
        }
    }
    Crossfade(bitmap, modifier, tween(450), label = "Wallpaper") { current ->
        current?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
internal fun GlassWallpaper(vm: AppViewModel) {
    val blur by vm.blur.collectAsStateWithLifecycle()
    GlassPhoto(groupPhoto(vm), Modifier.fillMaxSize().blur(blur.dp, BlurredEdgeTreatment.Rectangle), small = true)
}
