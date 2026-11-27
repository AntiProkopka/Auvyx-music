package com.nexus.music.ui.theme

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.nexus.music.ui.components.*
import com.nexus.music.data.settings.GlassQuality
import dev.chrisbanes.haze.*

internal val LocalGlassQuality = staticCompositionLocalOf { GlassQuality.Ultra }
internal val LocalBackdrop = staticCompositionLocalOf<HazeState?> { null }
internal val LocalArtLight = staticCompositionLocalOf<State<Color>> { mutableStateOf(Color.Transparent) }
internal val LocalLamp = staticCompositionLocalOf { mutableStateOf(Offset.Unspecified) }
internal val isGlass: Boolean @Composable get() = LocalStyle.current == "classic"

internal enum class GlassRole(val smoke: Color, val opacity: Float, val blurDp: Int, val readsPage: Boolean = false) {
    Row(Color(0xff181721), .25f, 0),
    Card(Color(0xff201d29), .71f, 16),
    Field(Color(0xff181621), .76f, 18),
    Transport(Color(0xff211c29), .78f, 24),
    Mini(Color(0xff211d29), .82f, 30, true),
    Dock(Color(0xff17161f), .86f, 28, true),
    Panel(Color(0xff201c28), .89f, 36, true)
}

@Composable
internal fun Modifier.frosted(shape: Shape, role: GlassRole = GlassRole.Card): Modifier {
    if (!isGlass) return this
    val quality = LocalGlassQuality.current
    val source = LocalBackdrop.current
    val accent = MaterialTheme.colorScheme.primary
    val base = role.smoke
    val live = source != null && role.blurDp > 0
    val material = if (live) {
        val style = remember(role, quality) {
            HazeStyle(backgroundColor = base, tint = HazeTint(base.copy(alpha = role.opacity)),
                blurRadius = (role.blurDp * if (quality == GlassQuality.High) .72f else 1f).dp,
                noiseFactor = 0f, fallbackTint = HazeTint(base.copy(alpha = .91f)))
        }
        Modifier.hazeEffect(source, style) {
            // The base and page are sibling recordings. A page child may sample only the base.
            canDrawArea = { it.zIndex == 0f || role.readsPage }
            inputScale = HazeInputScale.Fixed(if (quality == GlassQuality.Ultra) .55f else .35f)
        }
    } else Modifier.background(base.copy(alpha = if (role == GlassRole.Row) role.opacity else (role.opacity + .09f).coerceAtMost(.97f)))

    val lamp = LocalLamp.current
    val art = LocalArtLight.current
    val receivesLight = quality.liveBackdrop && (role == GlassRole.Mini || role == GlassRole.Dock || role == GlassRole.Transport)
    val origin = remember { mutableStateOf(Offset.Unspecified) }
    return clip(shape).then(material)
        .then(if (receivesLight) Modifier.onGloballyPositioned { origin.value = it.positionInRoot() } else Modifier)
        .drawWithCache {
            val film = Brush.linearGradient(listOf(
                lerp(Color.White, accent, .28f).copy(alpha = if (role == GlassRole.Row) .018f else .065f),
                Color.Transparent, Color.Black.copy(alpha = .11f)))
            val center = if (receivesLight && lamp.value != Offset.Unspecified && origin.value != Offset.Unspecified)
                lamp.value - origin.value else Offset.Unspecified
            val spill = if (center != Offset.Unspecified) {
                val color = art.value
                val intensity = when (role) {
                    GlassRole.Mini -> .29f
                    GlassRole.Dock -> .14f
                    else -> .18f
                }
                Brush.radialGradient(listOf(color.copy(alpha = color.alpha * intensity), Color.Transparent),
                    center, (if (quality == GlassQuality.Ultra) 216.dp else 144.dp).toPx())
            } else null
            onDrawBehind {
                if (quality != GlassQuality.Low) drawRect(film)
                if (spill != null) drawRect(spill)
            }
        }
}

@Composable
internal fun artworkBitmap(uri: String?): Bitmap? =
    if (uri?.startsWith("https://", true) == true) rememberRemoteArtwork(uri) else rememberLocalArtwork(uri)

@Composable
internal fun ArtTile(uri: String?, description: String?, modifier: Modifier) =
    LocalArtworkImage(artworkBitmap(uri), description, modifier.clip(RoundedCornerShape(18.dp)))

@Composable
internal fun ArtworkReflection(bitmap: Bitmap?, modifier: Modifier) {
    val quality = LocalGlassQuality.current
    if (bitmap == null || !quality.liveBackdrop || android.os.Build.VERSION.SDK_INT < 31) return
    Image(bitmap.asImageBitmap(), null, modifier
        .alpha(if (quality == GlassQuality.Ultra) .70f else .46f)
        .blur(if (quality == GlassQuality.Ultra) 22.dp else 14.dp, BlurredEdgeTreatment.Unbounded)
        .clip(RoundedCornerShape(28.dp)), contentScale = ContentScale.Crop)
}

@Composable
internal fun HaloArtwork(uri: String?, description: String?, modifier: Modifier) {
    val bitmap = artworkBitmap(uri)
    Box(modifier.padding(10.dp)) {
        ArtworkReflection(bitmap, Modifier.matchParentSize())
        LocalArtworkImage(bitmap, description, Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)))
    }
}

@Composable
internal fun AtmosphericLight(fullPlayer: Boolean) {
    val quality = LocalGlassQuality.current
    val accent = animateColorAsState(MaterialTheme.colorScheme.primary, tween(450), label = "Theme light")
    val mist = MaterialTheme.colorScheme.tertiary
    val art = LocalArtLight.current
    val lamp = LocalLamp.current
    Box(Modifier.fillMaxSize().drawWithCache {
        val radius = size.width.coerceAtLeast(1f)
        val theme = Brush.radialGradient(listOf(accent.value.copy(alpha = .13f), Color.Transparent),
            Offset(size.width * .10f, size.height * .16f), radius * 1.24f)
        val bounce = if (quality == GlassQuality.Ultra) Brush.radialGradient(
            listOf(mist.copy(alpha = .075f), Color.Transparent),
            Offset(size.width * 1.08f, size.height * .58f), radius) else null
        val center = lamp.value
        val local = if (center != Offset.Unspecified && quality != GlassQuality.Low) {
            val color = art.value
            val opacity = if (quality.liveBackdrop) .32f else .15f
            Brush.radialGradient(listOf(color.copy(alpha = color.alpha * opacity), Color.Transparent),
                center, radius * if (fullPlayer) .91f else .70f)
        } else null
        onDrawBehind {
            drawRect(theme)
            if (bounce != null) drawRect(bounce)
            if (local != null) drawRect(local)
        }
    })
}
