package com.nexus.music.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nexus.music.ui.theme.usesGlassV4
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun ExpressiveFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        shapes = FilterChipDefaults.shapes(
            shape = MaterialTheme.shapes.medium,
            selectedShape = MaterialTheme.shapes.large,
            pressedShape = MaterialTheme.shapes.small
        ),
        border = null
    )
}

@Composable
fun ExpressiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val state = remember(steps, valueRange) { SliderState(value, steps, valueRange) }
    state.value = value
    val colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.outline)
    val glass = usesGlassV4
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Slider(
        state = state,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interaction,
        thumb = { if (glass) GlassSliderThumb(enabled) else SliderDefaults.Thumb(interaction, colors = colors, enabled = enabled) },
        track = {
            if (glass) GlassSliderTrack(it, enabled, valueRange)
            else SliderDefaults.Track(it, enabled = enabled, colors = colors, drawStopIndicator = null)
        }
    )
}


@Composable
internal fun GlassSliderThumb(enabled: Boolean, vertical: Boolean = false) {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else .40f)
    Box((if (vertical) Modifier.size(16.dp, 5.dp) else Modifier.size(10.dp))
        .background(color, CircleShape))
}

@Composable
internal fun GlassSliderTrack(
    state: SliderState,
    enabled: Boolean,
    range: ClosedFloatingPointRange<Float>,
    vertical: Boolean = false
) {
    val accent = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else .35f)
    val rail = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) .40f else .20f)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val fraction = ((state.value - range.start) / (range.endInclusive - range.start))
        .takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    Canvas(if (vertical) Modifier.fillMaxHeight().width(4.dp) else Modifier.fillMaxWidth().height(4.dp)) {
        val start = if (vertical) Offset(center.x, size.height) else Offset(if (rtl) size.width else 0f, center.y)
        val end = if (vertical) Offset(center.x, 0f) else Offset(if (rtl) 0f else size.width, center.y)
        drawLine(rail, start, end, 4.dp.toPx(), StrokeCap.Round)
        if (fraction > 0f) drawLine(accent, start, start + (end - start) * fraction, 4.dp.toPx(), StrokeCap.Round)
    }
}
