package com.nexus.music.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.data.db.*
import com.nexus.music.playback.MIN_TRACK_GAIN
import com.nexus.music.playback.mergedRanges
import java.util.UUID

private enum class Tool(val label: String) {
    Gain("Индивидуальная громкость"), Skip("Пропуск фрагментов"), Moments("Моменты"),
    Tags("Музыкальные теги"), Links("Связанные композиции"), Rediscover("Когда-то на повторе"),
    SoundProfile("Звуковой профиль трека")
}

@Composable
internal fun TrackToolsHost(vm: AppViewModel) {
    val track by vm.trackToolsTarget.collectAsStateWithLifecycle()
    track?.let { selected ->
        key(selected.id) {
            var tool by remember { mutableStateOf<Tool?>(null) }
            val close = { vm.trackToolsTarget.value = null }
            if (tool == null) AlertDialog(
                onDismissRequest = close,
                title = { Text(selected.title) },
                text = { Column { Tool.entries.filterNot { it == Tool.Rediscover }.forEach { item -> TextButton(onClick = { tool = item }) { Text(item.label) } } } },
                confirmButton = { TextButton(onClick = close) { Text("Закрыть") } }
            )
            when (tool) {
                Tool.Gain -> GainDialog(vm, selected) { tool = null }
                Tool.Skip -> RangeDialog(vm, selected, moments = false) { tool = null }
                Tool.Moments -> RangeDialog(vm, selected, moments = true) { tool = null }
                Tool.Tags -> TagsDialog(vm, selected) { tool = null }
                Tool.Links -> LinksDialog(vm, selected) { tool = null }
                Tool.SoundProfile -> SoundProfileDialog(vm, selected) { tool = null }
                Tool.Rediscover, null -> Unit
            }
        }
    }
}

@Composable
private fun GainDialog(vm: AppViewModel, track: TrackEntity, close: () -> Unit) {
    val saved by remember(track.id) { vm.trackTools.gain(track.id) }.collectAsStateWithLifecycle(null)
    var draft by remember { mutableStateOf<Float?>(null) }
    val gain = draft ?: saved?.decibels ?: 0f
    AlertDialog(
        onDismissRequest = close,
        title = { Text(Tool.Gain.label) },
        text = {
            Column {
                Text("Ослабление трека · ${gain.toInt()} дБ")
                ExpressiveSlider(gain, { draft = it.toInt().toFloat() }, valueRange = MIN_TRACK_GAIN..0f, steps = 23)
                TextButton(onClick = { draft = 0f }) { Text("Сбросить") }
            }
        },
        confirmButton = { TextButton(onClick = {
            vm.saveTrackTools { vm.trackTools.putGain(TrackGain(track.id, gain)); close() }
        }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = close) { Text("Отмена") } }
    )
}

@Composable
private fun TagsDialog(vm: AppViewModel, track: TrackEntity, close: () -> Unit) {
    val rows by remember(track.id) { vm.trackTools.tags(track.id) }.collectAsStateWithLifecycle(emptyList())
    var input by remember { mutableStateOf("") }
    ToolList(Tool.Tags.label, close) {
        item {
            OutlinedTextField(input, { input = it.take(40) }, label = { Text("Тег") }, singleLine = true)
            TextButton(enabled = input.isNotBlank(), onClick = {
                val entered = input
                vm.saveTrackTools { vm.trackTools.assignTag(track.id, entered); input = "" }
            }) { Text("Добавить тег") }
            if (rows.isEmpty()) Text("Добавьте тег, чтобы найти этот трек в разделе тегов медиатеки.")
        }
        items(rows, key = { it.id }) { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.name, Modifier.weight(1f))
                TextButton(onClick = { vm.saveTrackTools { vm.trackTools.deleteTag(TrackTag(track.id, row.id)) } }) { Text("Убрать") }
            }
        }
    }
}

@Composable
private fun LinksDialog(vm: AppViewModel, track: TrackEntity, close: () -> Unit) {
    val tracks by vm.knownTracks.collectAsStateWithLifecycle()
    val rows by remember(track.id) { vm.trackTools.links(track.id) }.collectAsStateWithLifecycle(emptyList())
    val byId = remember(tracks) { tracks.associateBy { it.id } }
    val links = remember(rows) { rows.associateBy { it.other(track.id) } }
    var query by remember { mutableStateOf("") }
    var edit by remember { mutableStateOf<TrackEntity?>(null) }
    ToolList(Tool.Links.label, close) {
        if (rows.isEmpty()) item { Text("Найдите композицию из медиатеки и создайте взаимную связь.") }
        items(rows, key = { it.other(track.id) }) { row ->
            val other = byId[row.other(track.id)]
            if (other != null) {
                ListItem(headlineContent = { Text(other.title) }, supportingContent = {
                    Text(listOf(other.artist, row.label).filter { it.isNotBlank() }.joinToString(" · "))
                })
                Row {
                    TextButton(onClick = { vm.playTrack(other) }) { Text("Играть") }
                    TextButton(onClick = { edit = other }) { Text("Подпись") }
                    TextButton(onClick = { vm.saveTrackTools { vm.trackTools.deleteLink(row) } }) { Text("Убрать") }
                }
            }
        }
        item { OutlinedTextField(query, { query = it }, label = { Text("Найти в медиатеке") }, singleLine = true) }
        items(if (query.isBlank()) emptyList() else tracks.filter {
            it.id != track.id && it.id !in links && (it.title.contains(query, true) || it.artist.contains(query, true))
        }.take(40), key = { it.id }) { candidate ->
            TextButton(onClick = { edit = candidate }) { Text("${candidate.title} · ${candidate.artist}") }
        }
    }
    edit?.let { other ->
        val existing = links[other.id]
        var label by remember(other.id) { mutableStateOf(existing?.label.orEmpty()) }
        AlertDialog(onDismissRequest = { edit = null }, title = { Text(other.title) },
            text = { OutlinedTextField(label, { label = it.take(120) }, label = { Text("Подпись связи (необязательно)") }) },
            confirmButton = { TextButton(onClick = {
                val saved = TrackLink(minOf(track.id, other.id), maxOf(track.id, other.id), label.trim(),
                    existing?.createdAt ?: System.currentTimeMillis())
                vm.saveTrackTools { vm.trackTools.putLink(saved); edit = null }
            }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Отмена") } })
    }
}

private data class RangeDraft(val range: SkipRange? = null, val moment: TrackMoment? = null) {
    val momentId: String = moment?.id ?: UUID.randomUUID().toString()
}

@Composable
private fun RangeDialog(vm: AppViewModel, track: TrackEntity, moments: Boolean, close: () -> Unit) {
    val player by vm.player.state.collectAsStateWithLifecycle()
    val activeMoment by vm.activeMoment.collectAsStateWithLifecycle()
    val current = player.mediaId == track.id.toString()
    val duration = (if (current && player.durationMs > 0) player.durationMs else track.durationMs).takeIf { it > 0 }
    val canEdit = duration != null && if (current) player.canSeek else track.source == "local"
    val ranges by remember(track.id) { vm.trackTools.ranges(track.id) }.collectAsStateWithLifecycle(emptyList())
    val saved by remember(track.id) { vm.trackTools.moments(track.id) }.collectAsStateWithLifecycle(emptyList())
    val disabled by remember(track.id) { vm.trackTools.skipDisabled(track.id) }.collectAsStateWithLifecycle(null)
    var draft by remember { mutableStateOf<RangeDraft?>(null) }

    ToolList(if (moments) Tool.Moments.label else Tool.Skip.label, close) {
        item {
            if (!canEdit) Text("Редактирование доступно при известной длительности и поддержке перемотки. Для онлайн-трека сначала откройте его в плеере.")
            duration?.let { length ->
                val marks = if (moments) saved.map { it.startMs to it.endMs }
                else ranges.filter { it.enabled }.map { it.startMs to it.endMs }
                TrackTimeScale(length, marks)
            }
            TextButton(enabled = canEdit, onClick = { draft = RangeDraft() }) { Text("Добавить") }
            if (!moments) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Автопропуск для трека", Modifier.weight(1f))
                Switch(disabled == null, onCheckedChange = { value -> vm.saveTrackTools {
                    if (value) vm.trackTools.enableSkip(SkipDisabled(track.id)) else vm.trackTools.disableSkip(SkipDisabled(track.id))
                } })
            }
            if (activeMoment?.trackId == track.id) TextButton(onClick = vm.player::stopMoment) { Text("Закончить повтор момента") }
            if (moments && saved.isEmpty()) Text("Сохраните позицию или диапазон, чтобы вернуться к нему позже.")
            if (!moments && ranges.isEmpty()) Text("Отметьте начало и конец первого фрагмента.")
        }
        if (moments) items(saved, key = { it.id }) { row ->
            Text(row.name)
            Text(mark(row.startMs, row.endMs), style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(enabled = canEdit, onClick = { vm.player.playMoment(track, row, false); close() }) { Text("Играть") }
                if (row.endMs != null) TextButton(enabled = canEdit, onClick = { vm.player.playMoment(track, row, true); close() }) { Text("Повторять") }
                TextButton(enabled = canEdit, onClick = { draft = RangeDraft(moment = row) }) { Text("Изменить") }
                TextButton(onClick = { vm.saveTrackTools {
                    if (activeMoment?.id == row.id) vm.player.stopMoment()
                    vm.trackTools.deleteMoment(row)
                } }) { Text("Удалить") }
            }
        } else items(ranges, key = { "${it.startMs}:${it.endMs}" }) { row ->
            Text(row.name.ifBlank { "Фрагмент" })
            Text(mark(row.startMs, row.endMs), style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(enabled = canEdit, onClick = { draft = RangeDraft(range = row) }) { Text("Изменить") }
                TextButton(onClick = { vm.saveTrackTools { vm.trackTools.deleteRange(row) } }) { Text("Удалить") }
                Spacer(Modifier.weight(1f))
                Switch(row.enabled, enabled = canEdit, onCheckedChange = { enabled ->
                    val next = row.copy(enabled = enabled)
                    vm.saveTrackTools {
                        require(!enabled || mergedRanges(ranges - row + next, requireNotNull(duration)).isNotEmpty()) {
                            "Оставьте часть трека для воспроизведения"
                        }
                        vm.trackTools.putRange(next)
                    }
                })
            }
        }
    }

    draft?.let { value ->
        var name by remember(value) { mutableStateOf(value.range?.name ?: value.moment?.name.orEmpty()) }
        var start by remember(value) { mutableStateOf((value.range?.startMs ?: value.moment?.startMs ?: if (current) vm.player.position.value else 0).toString()) }
        var end by remember(value) { mutableStateOf((value.range?.endMs ?: value.moment?.endMs)?.toString().orEmpty()) }
        var error by remember(value) { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { draft = null }, title = { Text(if (moments) "Момент" else "Фрагмент") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it.take(120) }, label = { Text("Название") }, singleLine = true)
                    TimeField(start, { start = it }, "Начало, мс", if (current) ({ start = vm.player.position.value.toString() }) else null)
                    TimeField(end, { end = it }, if (moments) "Конец, мс (необязательно)" else "Конец, мс",
                        if (current) ({ end = vm.player.position.value.toString() }) else null)
                    duration?.let { length ->
                        TrackTimeScale(length, listOfNotNull(start.toLongOrNull()?.let { it to end.toLongOrNull() }))
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(enabled = canEdit, onClick = {
                val first = start.toLongOrNull()
                val last = end.toLongOrNull()
                val problem = runCatching {
                    require(first != null && (last != null || (moments && end.isBlank()))) { "Введите время в миллисекундах" }
                    validateRange(first, last, duration)
                    if (!moments && value.range?.enabled != false) {
                        val next = SkipRange(track.id, first, requireNotNull(last), name.trim(), true)
                        require(mergedRanges(ranges.filter { it != value.range } + next, requireNotNull(duration)).isNotEmpty()) {
                            "Оставьте часть трека для воспроизведения"
                        }
                    }
                }.exceptionOrNull()
                if (problem != null) error = problem.message
                else vm.saveTrackTools {
                    if (moments) vm.trackTools.putMoment(TrackMoment(value.momentId, track.id, requireNotNull(first),
                        last, name.trim().ifBlank { "Момент" }, value.moment?.createdAt ?: System.currentTimeMillis()))
                    else vm.trackTools.replaceRange(value.range, SkipRange(track.id, requireNotNull(first),
                        requireNotNull(last), name.trim(), value.range?.enabled ?: true))
                    draft = null
                }
            }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { draft = null }) { Text("Отмена") } })
    }
}

@Composable
private fun TimeField(value: String, edit: (String) -> Unit, label: String, fromPosition: (() -> Unit)?) {
    OutlinedTextField(value, { edit(it.take(12)) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    fromPosition?.let { TextButton(onClick = it) { Text("Из текущей позиции") } }
}

@Composable
private fun TrackTimeScale(duration: Long, marks: List<Pair<Long, Long?>>) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val y = size.height / 2
            fun position(time: Long) = (time.toDouble() / duration).coerceIn(0.0, 1.0).toFloat() * size.width
            drawLine(scheme.surfaceVariant, Offset(0f, y), Offset(size.width, y), 4.dp.toPx(), StrokeCap.Round)
            for ((start, end) in marks) {
                if (start !in 0..duration) continue
                val origin = Offset(position(start), y)
                if (end != null && end > start) {
                    drawLine(scheme.primary, origin, Offset(position(end), y), 5.dp.toPx(), StrokeCap.Round)
                } else drawCircle(scheme.primary, 3.dp.toPx(), origin)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(0), style = MaterialTheme.typography.labelSmall)
            Text(formatTime(duration), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun mark(start: Long, end: Long?): String {
    fun display(ms: Long) = "${formatTime(ms)}.${(ms % 1000).toString().padStart(3, '0')}"
    return display(start) + (end?.let { " – ${display(it)}" } ?: "")
}

@Composable
internal fun ToolList(title: String, close: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(title) },
        text = { LazyColumn(Modifier.fillMaxWidth().heightIn(max = 390.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content) },
        confirmButton = { TextButton(onClick = close) { Text("Готово") } })
}

@Composable
internal fun LibraryTrackTools(vm: AppViewModel) {
    var section by remember { mutableStateOf<Tool?>(null) }
    val close = { section = null }
    var smart by remember { mutableStateOf(false) }
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        TextButton(onClick = { section = Tool.Tags }) { Text("Теги") }
        TextButton(onClick = { section = Tool.Moments }) { Text("Моменты") }
        TextButton(onClick = { section = Tool.Rediscover }) { Text(Tool.Rediscover.label) }
        TextButton(onClick = { smart = true }) { Text("Умные коллекции") }
    }
    if (smart) SmartCollectionsDialog(vm) { smart = false }
    if (section == null) return
    if (section == Tool.Rediscover) {
        RediscoverDialog(vm, close)
        return
    }
    val tracks by vm.knownTracks.collectAsStateWithLifecycle()
    val byId = remember(tracks) { tracks.associateBy { it.id } }
    if (section == Tool.Tags) {
        val rows by remember { vm.trackTools.tags() }.collectAsStateWithLifecycle(emptyList())
        val catalog by remember { vm.trackTools.catalog() }.collectAsStateWithLifecycle(emptyList())
        var filter by remember { mutableStateOf("") }
        var selected by remember { mutableStateOf(emptySet<String>()) }
        var all by remember { mutableStateOf(true) }
        var rename by remember { mutableStateOf<MusicalTag?>(null) }
        var remove by remember { mutableStateOf<MusicalTag?>(null) }
        val matches = remember(rows, selected, all, tracks) {
            val ids = taggedTrackIds(rows, selected, all)
            tracks.filter { it.id in ids }
        }
        ToolList("Музыкальные теги", close) {
            item {
                OutlinedTextField(filter, { filter = it }, label = { Text("Поиск тега") })
                if (catalog.isEmpty()) Text("Добавьте теги через меню трека.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (all) "Все выбранные теги" else "Любой выбранный тег", Modifier.weight(1f))
                    Switch(all, { all = it })
                }
            }
            items(catalog.filter { it.name.contains(filter, true) }, key = { it.id }) { tag ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(tag.id in selected, { checked -> selected = if (checked) selected + tag.id else selected - tag.id })
                    Text(tag.name, Modifier.weight(1f))
                    TextButton(onClick = { rename = tag }) { Text("Имя") }
                    TextButton(onClick = { remove = tag }) { Text("Удалить") }
                }
            }
            if (selected.isNotEmpty()) item { Text("Треки · ${matches.size}") }
            items(matches, key = { it.id }) { track ->
                TextButton(onClick = { vm.playTrack(track); close() }) { Text(track.title) }
            }
        }
        rename?.let { tag -> NameDialog("Переименовать тег", tag.name, "Сохранить", onConfirm = { name ->
            vm.saveTrackTools { vm.trackTools.renameTag(tag, name); rename = null }
        }, onDismiss = { rename = null }) }
        remove?.let { tag -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("Удалить тег «${tag.name}»?") },
            text = { Text("Тег будет снят с треков: ${rows.count { it.tagId == tag.id }}.") },
            confirmButton = { TextButton(onClick = {
                vm.saveTrackTools { vm.trackTools.deleteTag(tag); selected = selected - tag.id; remove = null }
            }) { Text("Удалить") } }, dismissButton = { TextButton(onClick = { remove = null }) { Text("Отмена") } }) }

    } else {
        val rows by remember { vm.trackTools.moments() }.collectAsStateWithLifecycle(emptyList())
        val player by vm.player.state.collectAsStateWithLifecycle()
        var editing by remember { mutableStateOf<TrackEntity?>(null) }
        val date = remember { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM) }
        ToolList("Коллекция моментов", close) {
            if (rows.isEmpty()) item { Text("Сохраните момент через меню трека.") }
            items(rows.sortedByDescending { it.createdAt }, key = { it.id }) { row ->
                val track = byId[row.trackId]
                if (track == null) {
                    Text("${row.name} · Трек недоступен")
                } else {
                    val current = player.mediaId == track.id.toString()
                    val seekable = if (current) player.canSeek else track.source == "local"
                    val duration = if (current && player.durationMs > 0) player.durationMs else track.durationMs
                    val canPlay = seekable && row.startMs < duration && (row.endMs == null || row.endMs <= duration)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val modifier = Modifier.size(44.dp)
                        if (track.source == "soundcloud") RemoteArtworkImage(track.artworkUri, null, modifier)
                        else ArtworkImage(track.artworkUri, null, modifier)
                        Column {
                            Text(row.name, style = MaterialTheme.typography.titleSmall)
                            Text(track.title, style = MaterialTheme.typography.bodyMedium)
                            val timestamp = if (row.createdAt > 0) " · ${date.format(java.util.Date(row.createdAt))}" else ""
                            Text(mark(row.startMs, row.endMs) + timestamp, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (!canPlay) Text("Для запуска нужна доступная дорожка с перемоткой и подходящей длительностью.",
                        style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(enabled = canPlay, onClick = { vm.player.playMoment(track, row, false); close() }) { Text("Играть") }
                        if (row.endMs != null) TextButton(enabled = canPlay, onClick = {
                            vm.player.playMoment(track, row, true); close()
                        }) { Text("Повторять") }
                        TextButton(onClick = { editing = track }) { Text("Действия") }
                    }
                }
            }
        }
        editing?.let { RangeDialog(vm, it, moments = true) { editing = null } }
    }
}


@Composable
private fun RediscoverDialog(vm: AppViewModel, close: () -> Unit) {
    val rows by remember(vm) { vm.forgottenTracks() }.collectAsStateWithLifecycle(null)
    val candidates = rows.orEmpty()
    var naming by remember { mutableStateOf(false) }
    val date = remember { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM) }
    ToolList(Tool.Rediscover.label, close) {
        item {
            when {
                rows == null -> CircularProgressIndicator()
                candidates.isEmpty() -> Text("Здесь появятся треки, которые запускались хотя бы три раза и давно не звучали.")
                else -> {
                    Text("Треки из истории, которые не запускались минимум 30 дней.")
                    TextButton(onClick = { vm.player.playTracks(candidates.map { it.track }, 0); close() }) { Text("Играть подборку") }
                    TextButton(onClick = { naming = true }) { Text("Сохранить плейлист") }
                }
            }
        }
        items(candidates, key = { it.track.id }) { row ->
            Text(row.track.title)
            Text("Запусков: ${row.starts} · ${date.format(java.util.Date(row.lastPlayed))}",
                style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = { vm.playTrack(row.track); close() }) { Text("Играть") }
                TextButton(onClick = { vm.hideForgotten(row.track.id) }) { Text("Скрыть") }
            }
        }
        item { TextButton(onClick = vm::showHiddenForgotten) { Text("Вернуть скрытые предложения") } }
    }
    if (naming) NameDialog("Сохранить подборку", Tool.Rediscover.label, "Сохранить", onConfirm = {
        vm.savePlaylistSnapshot(it, candidates.map { row -> row.track })
        naming = false
    }, onDismiss = { naming = false })
}
