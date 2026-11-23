package com.nexus.music.ui.components

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
    Slider(
        state = state,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        track = { SliderDefaults.Track(it, enabled = enabled, colors = colors, drawStopIndicator = null) }
    )
}
