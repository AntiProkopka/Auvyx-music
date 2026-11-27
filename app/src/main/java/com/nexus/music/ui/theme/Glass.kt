package com.nexus.music.ui.theme

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
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
internal val isGlass: Boolean @Composable get() = LocalStyle.current == "glass"

@Composable
internal fun Modifier.frosted(shape: Shape, overPage: Boolean = false): Modifier {
    if (!isGlass) return this
    val quality = LocalGlassQuality.current
    val source = LocalBackdrop.current
    val colors = MaterialTheme.colorScheme
    val smoked = lerp(colors.background, colors.surfaceContainerHigh, if (overPage) .57f else .35f)
    val tint = smoked.copy(alpha = if (overPage) .67f else .55f)
    val clipped = clip(shape)
    if (source == null || quality == GlassQuality.Medium) {
        return clipped.background(smoked.copy(alpha = if (overPage) .92f else .80f))
    }
    val highlightState = LocalArtLight.current
    val lamp = LocalLamp.current
    val origin = remember { mutableStateOf(Offset.Unspecified) }
    val blur = when (quality) {
        GlassQuality.High -> if (overPage) 15.dp else 9.dp
        GlassQuality.Ultra -> if (overPage) 26.dp else 16.dp
        GlassQuality.Low, GlassQuality.Medium -> 0.dp
    }
    return clipped.then(
        if (quality == GlassQuality.Ultra && overPage) Modifier.onGloballyPositioned { origin.value = it.positionInRoot() }
        else Modifier
    ).hazeEffect(source, HazeStyle(
        backgroundColor = colors.background,
        tint = HazeTint(tint),
        noiseFactor = 0f,
        blurRadius = blur,
        fallbackTint = HazeTint(smoked.copy(alpha = .88f))
    )) {
        canDrawArea = { it.zIndex == 0f || overPage }
    }.drawWithContent {
        val highlight = highlightState.value
        if (quality == GlassQuality.Ultra && overPage && highlight.alpha > 0 && origin.value != Offset.Unspecified && lamp.value != Offset.Unspecified) {
            val center = lamp.value - origin.value
            val radius = 244.dp.toPx()
            if (center.x in -radius..(size.width + radius) && center.y in -radius..(size.height + radius)) {
                drawRect(Brush.radialGradient(listOf(highlight.copy(alpha=.16f * highlight.alpha),
                    highlight.copy(alpha=0f)), center, radius))
            }
        }
        drawContent()
    }
}

@Composable
internal fun artworkBitmap(uri: String?): Bitmap? =
    if (uri?.startsWith("https://", true) == true) rememberRemoteArtwork(uri) else rememberLocalArtwork(uri)

@Composable
internal fun ArtTile(uri: String?, description: String?, modifier: Modifier) =
    LocalArtworkImage(artworkBitmap(uri), description, modifier.clip(RoundedCornerShape(18.dp)))

@Composable
internal fun HaloArtwork(uri: String?, description: String?, modifier: Modifier) {
    val bitmap = artworkBitmap(uri)
    val fallback = MaterialTheme.colorScheme.primary
    val color by animateColorAsState(rememberArtworkAccent(bitmap, fallback) ?: fallback, tween(550))
    Box(modifier.background(Brush.radialGradient(listOf(color.copy(alpha = .65f), color.copy(alpha = 0f)))).padding(12.dp)) {
        LocalArtworkImage(bitmap, description, Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)))
    }
}

@Composable
internal fun AtmosphericLight(fullPlayer: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val mist = MaterialTheme.colorScheme.tertiary
    val quality = LocalGlassQuality.current
    val artState = LocalArtLight.current
    val lamp = LocalLamp.current
    Canvas(Modifier.fillMaxSize()) {
        fun pool(color: Color, location: Offset, radius: Float, opacity: Float) {
            if (radius <= 0f) return
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = color.alpha * opacity),
                color.copy(alpha = color.alpha * opacity * .40f), color.copy(alpha = 0f)), location, radius), radius, location)
        }
        pool(accent, Offset(size.width * .1f, size.height * .17f), size.width * 1.15f, .18f)
        if (quality != GlassQuality.Medium) pool(mist, Offset(size.width * 1.05f, size.height * .54f), size.width * .94f, .10f)
        val art = artState.value
        val center = if (fullPlayer) Offset(size.width * .5f, size.height * .34f) else lamp.value
        if (center != Offset.Unspecified) {
            val opacity = when (quality) {
                GlassQuality.Low -> .08f
                GlassQuality.Medium -> .14f
                GlassQuality.High -> .22f
                GlassQuality.Ultra -> .32f
            }
            pool(lerp(accent, art, .63f).copy(alpha = art.alpha), center,
                size.width * (if (fullPlayer) 1.25f else .82f), opacity)
        }
    }
}
