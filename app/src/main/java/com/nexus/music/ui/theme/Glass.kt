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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
internal val isGlass: Boolean @Composable get() = LocalStyle.current != "material"
internal val usesGlassV4: Boolean @Composable get() = LocalStyle.current == "glass"

internal enum class GlassRole(val smoke: Color, val opacity: Float, val blurDp: Int, val readsPage: Boolean = false) {
    Row(Color(0xff181721), .25f, 0),
    Card(Color(0xff201d29), .71f, 16),
    Field(Color(0xff181621), .76f, 18),
    Transport(Color(0xff211c29), .78f, 24),
    Mini(Color(0xff211d29), .82f, 30, true),
    Dock(Color(0xff17161f), .86f, 28, true),
    Panel(Color(0xff201c28), .89f, 36, true)
}

private data class GlassFinish(val top: Long, val middle: Long, val bottom: Long, val density: Float)

private val GlassRole.finish: GlassFinish
    get() = when (this) {
        GlassRole.Row -> GlassFinish(0xff292138, 0xff211c30, 0xff111321, .12f)
        GlassRole.Mini -> GlassFinish(0xff33283f, 0xff201b30, 0xff171827, .56f)
        GlassRole.Dock -> GlassFinish(0xff292338, 0xff1d1a2b, 0xff141622, .59f)
        GlassRole.Transport -> GlassFinish(0xff37293d, 0xff211c30, 0xff171827, .61f)
        GlassRole.Field -> GlassFinish(0xff252137, 0xff1d192a, 0xff131523, .66f)
        GlassRole.Panel -> GlassFinish(0xff30253c, 0xff211c30, 0xff131725, .78f)
        GlassRole.Card -> GlassFinish(0xff292138, 0xff211c30, 0xff111321, .63f)
    }

@Composable
internal fun Modifier.frosted(shape: Shape, role: GlassRole = GlassRole.Card): Modifier {
    if (!isGlass) return this
    val quality = LocalGlassQuality.current
    val source = LocalBackdrop.current
    val accent = MaterialTheme.colorScheme.primary
    val base = role.smoke
    val v4 = usesGlassV4
    val finish = role.finish
    val live = source != null && role.blurDp > 0 && (quality.liveBackdrop || role == GlassRole.Dock || role == GlassRole.Mini)
    val depth = when (quality) {
        GlassQuality.Low -> 0f
        GlassQuality.Medium -> .46f
        GlassQuality.High -> .76f
        GlassQuality.Ultra -> 1f
    }
    val material = if (live) {
        val style = remember(role, quality, v4) {
            HazeStyle(backgroundColor = base, tint = HazeTint(if (v4) Color.Transparent else base.copy(alpha = role.opacity)),
                blurRadius = (role.blurDp * depth).dp,
                noiseFactor = 0f, fallbackTint = HazeTint(if (v4) Color.Transparent else base.copy(alpha = .91f)))
        }
        Modifier.hazeEffect(source, style) {
            // The base and page are sibling recordings. A page child may sample only the base.
            canDrawArea = { it.zIndex == 0f || role.readsPage }
            inputScale = HazeInputScale.Fixed(if (quality == GlassQuality.Ultra) .55f else .35f)
        }
    } else if (v4) Modifier else Modifier.background(base.copy(alpha = if (role == GlassRole.Row) role.opacity else (role.opacity + .09f).coerceAtMost(.97f)))

    val lamp = LocalLamp.current
    val art = LocalArtLight.current
    val receivesLight = if (v4) role != GlassRole.Row && quality != GlassQuality.Low else
        quality.liveBackdrop && (role == GlassRole.Mini || role == GlassRole.Dock || role == GlassRole.Transport)
    val origin = remember { mutableStateOf(Offset.Unspecified) }
    return clip(shape).then(material)
        .then(if (receivesLight) Modifier.onGloballyPositioned { origin.value = it.positionInRoot() } else Modifier)
        .drawWithCache {
            val opacity = finish.density + if (!live && role != GlassRole.Row) .12f else 0f
            val film = if (v4) Brush.verticalGradient(
                0f to Color(finish.top).copy(alpha = opacity),
                .44f to Color(finish.middle).copy(alpha = (opacity + .06f).coerceAtMost(.96f)),
                1f to Color(finish.bottom).copy(alpha = (opacity + .12f).coerceAtMost(.98f))
            ) else Brush.linearGradient(listOf(
                lerp(Color.White, accent, .28f).copy(alpha = if (role == GlassRole.Row) .018f else .065f),
                Color.Transparent, Color.Black.copy(alpha = .11f)))
            val center = if (receivesLight && lamp.value != Offset.Unspecified && origin.value != Offset.Unspecified)
                lamp.value - origin.value else Offset.Unspecified
            val spill = if (center != Offset.Unspecified) {
                val color = art.value
                val intensity = when (role) {
                    GlassRole.Mini -> .29f
                    GlassRole.Dock -> .14f
                    else -> if (v4) .16f else .18f
                }
                Brush.radialGradient(listOf(color.copy(alpha = color.alpha * intensity), Color.Transparent),
                    center, (if (quality == GlassQuality.Ultra) 216.dp else 144.dp).toPx())
            } else null
            val rim = if (v4 && quality.liveBackdrop && role != GlassRole.Row && role != GlassRole.Mini) {
                val sourceX = if (center == Offset.Unspecified) size.width * .23f else center.x
                val color = if (center == Offset.Unspecified || art.value.alpha < .1f) accent else art.value
                Brush.radialGradient(
                    listOf(lerp(color, Color.White, .34f).copy(alpha = .24f * depth), Color.Transparent),
                    Offset(sourceX.coerceIn(0f, size.width), 0f), (size.width * .70f).coerceAtLeast(1f)
                )
            } else null
            val bounce = if (v4 && quality == GlassQuality.Ultra && role != GlassRole.Row) Brush.radialGradient(
                listOf(lerp(Color(0xff8095db), accent, .20f).copy(alpha = .13f), Color.Transparent),
                Offset(size.width * .87f, size.height * 1.03f), (size.width * .45f).coerceAtLeast(1f)
            ) else null
            onDrawBehind {
                if (v4 || quality != GlassQuality.Low) drawRect(film)
                if (spill != null) drawRect(spill)
                if (bounce != null) drawRect(bounce)
                if (rim != null) drawRect(rim, size = androidx.compose.ui.geometry.Size(size.width, .8.dp.toPx()))
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
    if (bitmap == null) return
    val v4 = usesGlassV4
    val tone = rememberArtworkAccent(bitmap)
    val ambient = tone ?: MaterialTheme.colorScheme.primary
    if (v4) Box(modifier.drawWithCache {
        val radius = (size.minDimension * .82f).coerceAtLeast(1f)
        val halo = Brush.radialGradient(listOf(ambient.copy(alpha = .42f), ambient.copy(alpha = .17f), Color.Transparent),
            Offset(size.width / 2f, size.height / 2f), radius)
        onDrawBehind { drawCircle(halo, radius) }
    })
    if (!quality.liveBackdrop || android.os.Build.VERSION.SDK_INT < 31) return
    Image(bitmap.asImageBitmap(), null, modifier
        .graphicsLayer {
            alpha = if (quality == GlassQuality.Ultra) .70f else .46f
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }
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
