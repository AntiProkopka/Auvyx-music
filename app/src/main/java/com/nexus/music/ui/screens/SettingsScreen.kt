package com.nexus.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.ui.components.SectionTitle

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val theme by vm.themeMode.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        Text(
            "Настройки",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

        SectionTitle("Внешний вид")
        listOf("system" to "Как в системе", "light" to "Светлая", "dark" to "Тёмная").forEach { (key, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.setTheme(key) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = theme == key, onClick = { vm.setTheme(key) })
                Text(label)
            }
        }

        SectionTitle("Медиатека")
        Text(
            "Треков: ${tracks.size}\nПодключённых папок: ${folders.size}",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        OutlinedButton(
            onClick = { vm.refreshLibrary() },
            modifier = Modifier.padding(horizontal = 16.dp)
        ) { Text("Пересканировать папки") }

        SectionTitle("Данные")
        OutlinedButton(
            onClick = { vm.clearHistory() },
            modifier = Modifier.padding(horizontal = 16.dp)
        ) { Text("Очистить историю прослушивания") }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.resetSettings() },
            modifier = Modifier.padding(horizontal = 16.dp)
        ) { Text("Сбросить настройки внешнего вида") }

        SectionTitle("О приложении")
        Text(
            "NEXUS Music 0.3.0 · Фундамент\nЛокальная медиатека, плеер, очередь, плейлисты, сохранение данных.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
}
