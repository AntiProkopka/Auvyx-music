package com.nexus.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.math.roundToLong

@Composable
internal fun TrackRangePicker(
    duration: Long,
    start: Long,
    end: Long?,
    onChange: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        if (end == null) {
            Slider(
                value = start.toFloat(),
                onValueChange = { value ->
                    val first = value.roundToLong().coerceIn(0L, duration - 1)
                    onChange(first, first + 1)
                },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            RangeSlider(
                value = start.toFloat()..end.toFloat(),
                onValueChange = { value ->
                    val first = value.start.roundToLong().coerceIn(0L, duration - 1)
                    val last = value.endInclusive.roundToLong().coerceIn(first + 1, duration)
                    onChange(first, last)
                },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(0), style = MaterialTheme.typography.labelSmall)
            Text(formatTime(duration), style = MaterialTheme.typography.labelSmall)
        }
        Text("Начало · ${formatTime(start)}")
        if (end != null) {
            Text("Конец · ${formatTime(end)}   Длительность · ${formatTime(end - start)}")
        }
    }
}
