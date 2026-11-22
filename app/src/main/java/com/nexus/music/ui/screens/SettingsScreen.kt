package com.nexus.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.nexus.music.ui.components.NameDialog

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val theme by vm.themeMode.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val nickname by vm.nickname.collectAsStateWithLifecycle()
    var editingNickname by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("auvyx", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Музыка, собранная вокруг тебя.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("PERSONAL MUSIC PLAYER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column {
                ListItem(
                    headlineContent = { Text("Профиль плеера") },
                    supportingContent = { Text("${nickname ?: "Имя не задано"} · используется в названии встроенного динамика") },
                    leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) }
                )
                OutlinedButton(onClick = { editingNickname = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Изменить никнейм")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Оформление") },
                    supportingContent = { Text("Material 3") },
                    leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) }
                )
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

        AppIconSettingsSection()

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column {
                ListItem(
                    headlineContent = { Text("Медиатека") },
                    supportingContent = { Text("${tracks.size} треков · ${folders.size} подключённых папок") },
                    leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) }
                )
                OutlinedButton(onClick = { vm.refreshLibrary() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("Пересканировать папки")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Данные", style = MaterialTheme.typography.titleMedium)
                Text("История хранит локальные треки и прослушивания SoundCloud.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { vm.clearHistory() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Text("Очистить историю прослушивания")
                }
                Button(onClick = { vm.resetSettings() }, modifier = Modifier.fillMaxWidth()) { Text("Сбросить настройки интерфейса") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
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
