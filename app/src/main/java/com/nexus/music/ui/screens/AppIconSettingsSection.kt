package com.nexus.music.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nexus.music.LauncherIconManager
import com.nexus.music.R

@Composable
fun AppIconSettingsSection() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(LauncherIconManager.current(context)) }

    val options = listOf(
        Triple("1", "Вариант 1", R.mipmap.auvyx_icon_1),
        Triple("2", "Вариант 2", R.mipmap.auvyx_icon_2),
        Triple("3", "Вариант 3", R.mipmap.auvyx_icon_3)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column {
            ListItem(
                headlineContent = { Text("Иконка приложения") },
                supportingContent = { Text("Выбери один из трёх вариантов auvyx") },
                leadingContent = { androidx.compose.material3.Icon(Icons.Default.Apps, contentDescription = null) }
            )
            options.forEach { (id, label, iconRes) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    RadioButton(
                        selected = selected == id,
                        onClick = {
                            LauncherIconManager.apply(context, id)
                            selected = id
                        }
                    )
                    Image(
                        painter = painterResource(iconRes),
                        contentDescription = label,
                        modifier = Modifier.padding(start = 8.dp, end = 12.dp)
                            .size(48.dp)
                            .clip(RoundedCornerShape(11.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
