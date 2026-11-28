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
import com.nexus.music.data.db.*

@Composable
internal fun SmartCollectionEditor(vm: AppViewModel, list: SmartCollection, close: () -> Unit, saved: () -> Unit) {
    val rules by remember(list.id) { vm.smartCollections.conditions(list.id) }.collectAsStateWithLifecycle(null)
    val tags by remember { vm.trackTools.catalog() }.collectAsStateWithLifecycle(emptyList())
    rules?.let { present ->
        key(list.id) {
            CollectionEditor(vm, list, present, tags, close) { collection, conditions ->
                vm.saveTrackTools {
                    vm.smartCollections.save(collection, conditions)
                    saved()
                }
            }
        }
    }
}

private fun describeCondition(rule: CollectionCondition, tags: Map<String, String>): String {
    val label = CollectionField.entries.firstOrNull { it.name == rule.kind }?.label ?: "Неизвестное условие"
    return if (rule.argument.isEmpty()) label else "$label · " +
        if (rule.kind == "TAG") tags[rule.argument].orEmpty().ifBlank { "Тег удалён" } else rule.argument
}

@Composable
private fun CollectionEditor(
    vm: AppViewModel,
    list: SmartCollection,
    initial: List<CollectionCondition>,
    tags: List<MusicalTag>,
    close: () -> Unit,
    save: (SmartCollection, List<CollectionCondition>) -> Unit
) {
    var cover by remember { mutableStateOf(list.coverUri) }
    val coverPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.keepCollectionCover(it) }?.let { cover = it } }
    var name by remember { mutableStateOf(list.name) }
    var all by remember { mutableStateOf(list.matchAll) }
    var limit by remember { mutableStateOf(list.limit.toString()) }
    var rules by remember { mutableStateOf(initial) }
    var field by remember { mutableStateOf<CollectionField?>(null) }
    var argument by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    fun append(value: CollectionField, text: String) {
        val condition = CollectionCondition(list.id, rules.size, value.name, text.trim())
        if (rules.size < 20 && validCondition(condition)) {
            rules += condition
            field = null
            argument = ""
        }
    }
    AlertDialog(onDismissRequest = close, title = { Text("Правила коллекции") },
        text = {
            Column(Modifier.heightIn(max = 410.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it.take(120) }, label = { Text("Название") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    cover?.let { com.nexus.music.ui.theme.ArtTile(it, "Обложка", Modifier.size(44.dp)) }
                    TextButton(onClick = { coverPicker.launch(arrayOf("image/*")) }) { Text("Обложка") }
                    if (cover != null) TextButton(onClick = { cover = null }) { Text("Сбросить") }
                }
                OutlinedTextField(limit, { limit = it.take(3) }, label = { Text("Максимум треков · 1–500") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (all) "Все условия" else "Любое условие", Modifier.weight(1f))
                    Switch(all, { all = it })
                }
                val tagNames = tags.associate { it.id to it.name }
                rules.forEachIndexed { index, rule ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(describeCondition(rule, tagNames), Modifier.weight(1f))
                        TextButton(onClick = { rules = rules.filterIndexed { i, _ -> i != index } }) { Text("Убрать") }
                    }
                }
                Box {
                    TextButton(enabled = rules.size < 20, onClick = { menu = true }) { Text("Добавить условие") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        CollectionField.entries.forEach { value ->
                            DropdownMenuItem(text = { Text(value.label) }, onClick = {
                                menu = false
                                field = value
                                argument = ""
                                if (value.hint.isEmpty() && value != CollectionField.TAG) append(value, "")
                            })
                        }
                    }
                }
                val active = field
                if (active == CollectionField.TAG) {
                    if (tags.isEmpty()) Text("Сначала создайте тег в меню трека.")
                    tags.forEach { tag -> TextButton(onClick = { append(active, tag.id) }) { Text(tag.name) } }
                } else if (active != null) {
                    OutlinedTextField(argument, { argument = it.take(120) }, label = { Text(active.hint) }, singleLine = true)
                    TextButton(enabled = validCondition(CollectionCondition(list.id, 0, active.name, argument.trim())),
                        onClick = { append(active, argument) }) { Text("Добавить") }
                }
            }
        },
        confirmButton = {
            val max = limit.toIntOrNull()
            TextButton(enabled = name.isNotBlank() && rules.isNotEmpty() && max != null && max in 1..500,
                onClick = { save(list.copy(name = name.trim(), limit = requireNotNull(max), matchAll = all, coverUri = cover), rules) }) {
                Text("Сохранить")
            }
        },
        dismissButton = { TextButton(onClick = close) { Text("Отмена") } })
}
