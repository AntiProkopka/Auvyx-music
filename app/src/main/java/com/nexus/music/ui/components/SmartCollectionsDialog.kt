package com.nexus.music.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
internal fun SmartCollectionsDialog(vm: AppViewModel, close: () -> Unit) {
    val collections by remember { vm.smartCollections.lists() }.collectAsStateWithLifecycle(null)
    var selected by remember { mutableStateOf<SmartCollection?>(null) }
    var edit by remember { mutableStateOf<SmartCollection?>(null) }
    var remove by remember { mutableStateOf<SmartCollection?>(null) }
    val current = selected
    if (current == null) ToolList("Умные коллекции", close) {
        item {
            TextButton(onClick = { edit = SmartCollection(UUID.randomUUID().toString(), "", true, 100) }) {
                Text("Новая умная коллекция")
            }
            when {
                collections == null -> CircularProgressIndicator()
                collections.orEmpty().isEmpty() -> Text("Укажите условия, чтобы собирать треки из вашей медиатеки автоматически.")
            }
        }
        items(collections.orEmpty(), key = { it.id }) { list ->
            TextButton(onClick = { selected = list }) { Text(list.name) }
        }
    } else CollectionPreview(vm, current, edit = { edit = current }, remove = { remove = current }) { selected = null }

    edit?.let { list ->
        val rules by remember(list.id) { vm.smartCollections.conditions(list.id) }.collectAsStateWithLifecycle(null)
        val tags by remember { vm.trackTools.catalog() }.collectAsStateWithLifecycle(emptyList())
        rules?.let { present ->
            key(list.id) {
                CollectionEditor(list, present, tags, close = { edit = null }) { saved, conditions ->
                    vm.saveTrackTools {
                        vm.smartCollections.save(saved, conditions)
                        edit = null
                        selected = saved
                    }
                }
            }
        }
    }
    remove?.let { list ->
        AlertDialog(onDismissRequest = { remove = null }, title = { Text("Удалить «${list.name}»?") },
            text = { Text("Треки останутся в медиатеке.") },
            confirmButton = { TextButton(onClick = {
                vm.saveTrackTools { vm.smartCollections.delete(list); selected = null; remove = null }
            }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { remove = null }) { Text("Отмена") } })
    }
}

@Composable
private fun CollectionPreview(vm: AppViewModel, list: SmartCollection, edit: () -> Unit, remove: () -> Unit, close: () -> Unit) {
    val tracks by vm.knownTracks.collectAsStateWithLifecycle()
    val listens by vm.listenCounts.collectAsStateWithLifecycle()
    val tags by remember { vm.trackTools.tags() }.collectAsStateWithLifecycle(emptyList())
    val catalog by remember { vm.trackTools.catalog() }.collectAsStateWithLifecycle(emptyList())
    val moments by remember { vm.trackTools.moments() }.collectAsStateWithLifecycle(emptyList())
    val rules by remember(list.id) { vm.smartCollections.conditions(list.id) }.collectAsStateWithLifecycle(null)
    val now by produceState(System.currentTimeMillis(), list.id) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis()
        }
    }
    val matches by produceState<List<TrackEntity>?>(null, list, arrayOf(tracks, listens, tags, moments, rules, now).toList()) {
        val current = rules ?: return@produceState
        value = withContext(Dispatchers.Default) {
            collectionTracks(list, current, tracks, tags, moments.mapTo(hashSetOf()) { it.trackId }, listens, now)
        }
    }
    val snapshot = matches.orEmpty()
    val tagNames = remember(catalog) { catalog.associate { it.id to it.name } }
    ToolList(list.name, close) {
        item {
            Text(if (list.matchAll) "Все условия" else "Любое условие")
            rules.orEmpty().forEach { Text(describeCondition(it, tagNames), style = MaterialTheme.typography.bodySmall) }
            when {
                matches == null -> CircularProgressIndicator()
                snapshot.isEmpty() -> Text("Ни один трек не подходит. Можно отредактировать правила.")
                else -> {
                    Text("Треков: ${snapshot.size}")
                    Row {
                        TextButton(onClick = { vm.player.playTracks(snapshot, 0) }) { Text("Играть") }
                        TextButton(onClick = { vm.player.playTracks(snapshot, 0, shuffle = true) }) { Text("Перемешать") }
                    }
                    TextButton(onClick = { vm.savePlaylistSnapshot(list.name, snapshot) }) { Text("Сохранить как плейлист") }
                }
            }
            Row {
                TextButton(onClick = edit) { Text("Правила") }
                TextButton(onClick = remove) { Text("Удалить") }
            }
        }
        items(snapshot, key = { it.id }) { track ->
            TextButton(onClick = { vm.playTrack(track) }) { Text("${track.title} · ${track.artist}") }
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
    list: SmartCollection,
    initial: List<CollectionCondition>,
    tags: List<MusicalTag>,
    close: () -> Unit,
    save: (SmartCollection, List<CollectionCondition>) -> Unit
) {
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
                onClick = { save(list.copy(name = name.trim(), limit = requireNotNull(max), matchAll = all), rules) }) {
                Text("Сохранить")
            }
        },
        dismissButton = { TextButton(onClick = close) { Text("Отмена") } })
}
