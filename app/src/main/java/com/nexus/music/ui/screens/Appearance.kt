package com.nexus.music.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.R
import com.nexus.music.data.settings.GlassAccent
import com.nexus.music.ui.theme.*
import com.nexus.music.ui.components.ExpressiveSlider

@Composable
internal fun Appearance(vm: AppViewModel) {
    val style by vm.visualStyle.collectAsStateWithLifecycle()
    val accent by vm.accent.collectAsStateWithLifecycle()
    val labels by vm.recentLabels.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().frosted(MaterialTheme.shapes.large).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Внешний вид", style = MaterialTheme.typography.titleLarge)
        listOf("glass" to "AUVYX Glass UI", "material" to "Material 3 Expressive").forEach { (id, name) ->
            Row(Modifier.fillMaxWidth().selectable(style == id, role = Role.RadioButton) { vm.setStyle(id) },
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(style == id, null, Modifier.padding(9.dp))
                Text(name)
            }
        }
        if (!isGlass) return@Column
        Text(accent.title, style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(GlassAccent.entries) { entry ->
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(entry.light))
                    .selectable(accent == entry) { vm.selectAppearance(entry) }.semantics { contentDescription = entry.title },
                    contentAlignment = Alignment.Center) {
                    if (accent == entry) Icon(painterResource(R.drawable.glass_check), null, tint = Color.Black)
                }
            }
        }
        val photos = LocalPhotos.current.flatMap { (group, paths) -> paths.map { group to it } }
        if (photos.isNotEmpty()) {
            val selected = groupPhoto(vm)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(photos, key = { it.second }) { (group, path) ->
                    GlassPhoto(path, Modifier.size(88.dp, 58.dp).clip(RoundedCornerShape(12.dp)).selectable(selected == path) {
                        vm.selectAppearance(group, path)
                    }.semantics { contentDescription = group.title + " " + path.substringAfterLast('/') }, small = true)
                }
            }
            val blur by vm.blur.collectAsStateWithLifecycle()
            Text("Размытие фона · $blur")
            ExpressiveSlider(blur.toFloat(), { vm.setBlur(it.toInt()) }, valueRange = 8f..56f)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Подписи Recently Played", Modifier.weight(1f))
            Switch(labels, vm::setRecentLabels)
        }
        Text("Графика · Ultra", style = MaterialTheme.typography.labelLarge)
    }
}
