package com.nexus.music.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.ui.components.ExpressiveSlider
import com.nexus.music.ui.components.NameDialog

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val theme by vm.themeMode.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val nickname by vm.nickname.collectAsStateWithLifecycle()
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importLibrary(uri)
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportLibrary(uri)
    }
    var editingNickname by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("auvyx", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Музыка, собранная вокруг тебя.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("PERSONAL MUSIC PLAYER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column {
                ListItem(
                    headlineContent = { Text("Профиль плеера") },
                    supportingContent = { Text("${nickname?.ifBlank { "Имя не задано" } ?: "Имя не задано"} · используется в названии встроенного динамика") },
                    leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) }
                )
                OutlinedButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = { editingNickname = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Изменить никнейм")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Оформление") },

                    leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) }
                )
                val style by vm.visualStyle.collectAsStateWithLifecycle()
                listOf("material" to "Material 3", "hyper" to "HyperOS", "glass" to "Liquid Glass", "lane" to "Studio · LANE").forEach { (id, name) ->
                    Row(Modifier.fillMaxWidth().selectable(selected = style == id, onClick = { vm.setStyle(id) }).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = style == id, onClick = null)
                        Text(name)
                    }
                }
                listOf("system" to "Как в системе", "light" to "Светлая", "dark" to "Тёмная").forEach { (key, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = theme == key, onClick = { vm.setTheme(key) })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        val fade by vm.crossfade.collectAsStateWithLifecycle()
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Кроссфейд · " + if (fade == 0) "выключен" else "$fade с")
            ExpressiveSlider(value = fade.toFloat(), onValueChange = { vm.setCrossfade(kotlin.math.round(it).toInt()) },
                valueRange = 0f..12f, steps = 11)
            Text("Плавный переход между поддерживаемыми треками", style = MaterialTheme.typography.bodySmall)
        }
        AppIconSettingsSection()

        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column {
                ListItem(
                    headlineContent = { Text("Медиатека") },
                    supportingContent = { Text("${tracks.size} треков · ${folders.size} подключённых папок") },
                    leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) }
                )
                OutlinedButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = { vm.refreshLibrary() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("Пересканировать папки")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Данные", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = { backupPicker.launch("auvyx-backup.json") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Экспорт медиатеки и настроек")
                }
                OutlinedButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = { restorePicker.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }) {
                    Text("Объединить с резервной копией")
                }
                Text("История хранит локальные треки и прослушивания SoundCloud.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = { vm.clearHistory() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Text("Очистить историю прослушивания")
                }
                Button(shapes = androidx.compose.material3.ButtonDefaults.shapes(), onClick = { vm.resetSettings() }, modifier = Modifier.fillMaxWidth()) { Text("Сбросить настройки интерфейса") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("О приложении", style = MaterialTheme.typography.titleMedium)
                Text("auvyx · Alpha 2.0", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Локальная медиатека, SoundCloud и единый движок воспроизведения на Media3. В Alpha 2.0 добавлены собственный 6-полосный эквалайзер и выбор аудиовыхода.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (editingNickname) {
        NameDialog(
            title = "Никнейм плеера",
            initial = nickname.orEmpty(),
            confirmLabel = "Сохранить",
            onConfirm = { vm.setNickname(it); editingNickname = false },
            onDismiss = { editingNickname = false }
        )
    }
}
