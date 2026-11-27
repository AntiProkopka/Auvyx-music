package com.nexus.music.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.data.db.TrackEntity
import com.nexus.music.data.db.TrackSoundProfile
import com.nexus.music.playback.SixBandEqualizer
import org.json.JSONArray

@Composable
internal fun SoundProfileDialog(vm: AppViewModel, track: TrackEntity, close: () -> Unit) {
    val saved by remember(track.id) { vm.soundProfiles.profile(track.id) }.collectAsStateWithLifecycle(null)
    val effective by vm.soundSettings.collectAsStateWithLifecycle()
    val profile = saved
    var edit by remember { mutableStateOf(false) }
    var copyCurrent by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    if (edit) {
        val values = remember { if (copyCurrent) effective else null }
        val original = remember { profile }
        var hasEq by remember { mutableStateOf(values != null || original?.preset != null) }
        var eqEnabled by remember { mutableStateOf(values?.eqEnabled ?: original?.eqEnabled) }
        var preset by remember { mutableStateOf(values?.preset ?: original?.preset ?: "custom") }
        var gains by remember { mutableStateOf(values?.gains ?: original?.overrides()?.gains ?: effective.gains) }
        var speed by remember { mutableStateOf(values?.speed ?: original?.speed) }
        var pitch by remember { mutableStateOf(values?.pitch ?: original?.pitch) }
        AlertDialog(onDismissRequest = { edit = false }, title = { Text("Звуковой профиль трека") }, text = {
            Column(Modifier.heightIn(max = 370.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(track.title, style = MaterialTheme.typography.titleSmall)
                Text("Поля с наследованием используют общие настройки. Громкость задаётся отдельно в меню трека.")
                Text("Эквалайзер")
                Row {
                    listOf(null to "Общий", true to "Вкл.", false to "Выкл.").forEach { (value, label) ->
                        FilterChip(selected = eqEnabled == value, onClick = { eqEnabled = value }, label = { Text(label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Свои настройки полос", Modifier.weight(1f))
                    Switch(hasEq, { hasEq = it })
                }
                if (hasEq) {
                    TextButton(onClick = { gains = SixBandEqualizer.BASS_BOOST; preset = "bass_boost" }) {
                        Text("Bass Boosted")
                    }
                    gains.forEachIndexed { index, gain ->
                        Text("${SixBandEqualizer.FREQUENCIES[index].toInt()} Гц · %+.1f дБ".format(gain))
                        Slider(gain, { next ->
                            gains = gains.toMutableList().also { it[index] = next }
                            preset = "custom"
                        }, valueRange = -12f..12f)
                    }
                }
                ProfileValue("Скорость", speed, effective.speed) { speed = it }
                ProfileValue("Тон", pitch, effective.pitch) { pitch = it }
            }
        }, confirmButton = {
            TextButton(onClick = {
                val row = TrackSoundProfile(track.id, original?.enabled ?: true, eqEnabled,
                    preset.takeIf { hasEq }, gains.takeIf { hasEq }?.let { JSONArray(it).toString() }, speed, pitch)
                vm.saveTrackTools { row.overrides(); vm.soundProfiles.put(row); edit = false }
            }, enabled = hasEq || eqEnabled != null || speed != null || pitch != null) { Text("Сохранить") }
        }, dismissButton = { TextButton(onClick = { edit = false }) { Text("Отмена") } })
        return
    }
    AlertDialog(onDismissRequest = close, title = { Text("Звуковой профиль трека") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(track.title)
            Text(when {
                profile == null -> "Действуют общие настройки."
                profile.enabled -> "При воспроизведении применяются параметры трека."
                else -> "Профиль сохранён. Действуют общие настройки."
            })
            TextButton(onClick = { copyCurrent = true; edit = true }) { Text("Сохранить текущие настройки") }
            TextButton(onClick = { copyCurrent = false; edit = true }) { Text(if (profile == null) "Создать" else "Редактировать") }
            if (profile != null) {
                TextButton(onClick = { vm.saveTrackTools { vm.soundProfiles.put(profile.copy(enabled = !profile.enabled)) } }) {
                    Text(if (profile.enabled) "Использовать общие настройки" else "Включить профиль трека")
                }
                TextButton(onClick = { delete = true }) { Text("Удалить профиль") }
            }
        }
    }, confirmButton = { TextButton(onClick = close) { Text("Готово") } })
    if (delete && profile != null) AlertDialog(onDismissRequest = { delete = false },
        title = { Text("Удалить настройки трека?") },
        confirmButton = { TextButton(onClick = {
            vm.saveTrackTools { vm.soundProfiles.delete(profile); delete = false }
        }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text("Отмена") } })
}

@Composable
private fun ProfileValue(label: String, value: Float?, inherited: Float, onChange: (Float?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (value == null) "$label · общие настройки" else "$label · %.2f×".format(value), Modifier.weight(1f))
        Switch(value != null, { onChange(if (it) inherited else null) })
    }
    if (value != null) Slider(value, onChange, valueRange = 0.5f..2f)
}
