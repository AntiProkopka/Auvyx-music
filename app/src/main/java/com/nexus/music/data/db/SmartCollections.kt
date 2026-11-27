package com.nexus.music.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "smart_collections")
data class SmartCollection(@PrimaryKey val id: String, val name: String, val matchAll: Boolean, val limit: Int)

@Entity(tableName = "collection_conditions", primaryKeys = ["collectionId", "position"], foreignKeys = [
    ForeignKey(SmartCollection::class, ["id"], ["collectionId"], onDelete = ForeignKey.CASCADE)
])
data class CollectionCondition(val collectionId: String, val position: Int, val kind: String, val argument: String)

@Dao
abstract class SmartCollectionDao {
    @Query("SELECT * FROM smart_collections ORDER BY name COLLATE NOCASE, id")
    abstract fun lists(): Flow<List<SmartCollection>>
    @Query("SELECT * FROM collection_conditions WHERE collectionId = :id ORDER BY position")
    abstract fun conditions(id: String): Flow<List<CollectionCondition>>
    @Upsert abstract suspend fun upsert(collection: SmartCollection)
    @Query("DELETE FROM collection_conditions WHERE collectionId = :id")
    abstract suspend fun clearConditions(id: String)
    @Insert abstract suspend fun insertConditions(conditions: List<CollectionCondition>)
    @Delete abstract suspend fun delete(collection: SmartCollection)

    @Transaction
    open suspend fun save(collection: SmartCollection, rules: List<CollectionCondition>) {
        require(collection.name.isNotBlank() && collection.name.length <= 120 && collection.limit in 1..500)
        require(rules.size in 1..20 && rules.all { it.collectionId == collection.id && validCondition(it) })
        upsert(collection)
        clearConditions(collection.id)
        insertConditions(rules.mapIndexed { index, rule -> rule.copy(position = index) })
    }
}

internal enum class CollectionField(val label: String, val hint: String) {
    LOCAL("Локальные треки", ""), SOUNDCLOUD("SoundCloud", ""), FAVORITE("Избранное", ""),
    ARTIST("Исполнитель содержит", "Имя"), ADDED("Добавлен за последние дни", "Дни"),
    LISTENS("Запусков не меньше", "Количество"), TAG("Тег", ""), MOMENT("Есть момент", "")
}

internal fun validCondition(rule: CollectionCondition): Boolean =
    when (rule.kind) {
        "LOCAL", "SOUNDCLOUD", "FAVORITE", "MOMENT" -> rule.argument.isEmpty()
        "TAG" -> runCatching { java.util.UUID.fromString(rule.argument).toString() == rule.argument }.getOrDefault(false)
        "ARTIST" -> rule.argument.isNotBlank() && rule.argument.length <= 120
        "ADDED" -> rule.argument.toIntOrNull()?.let { it in 1..3650 } == true
        "LISTENS" -> rule.argument.toIntOrNull()?.let { it in 1..1_000_000 } == true
        else -> false
    }

internal fun collectionTracks(
    collection: SmartCollection,
    rules: List<CollectionCondition>,
    tracks: List<TrackEntity>,
    tags: List<TrackTag>,
    momentTracks: Set<Long>,
    listens: Map<Long, Long>,
    now: Long
): List<TrackEntity> {
    if (rules.isEmpty() || rules.any { !validCondition(it) }) return emptyList()
    val tagsByTrack = tags.groupBy({ it.trackId }, { it.tagId })
    return tracks.asSequence().filter { track ->
        fun compare(rule: CollectionCondition) = when (rule.kind) {
            "LOCAL" -> track.source == "local"
            "SOUNDCLOUD" -> track.source == "soundcloud"
            "FAVORITE" -> track.isFavorite
            "ARTIST" -> track.artist.contains(rule.argument, ignoreCase = true)
            "ADDED" -> track.addedAt in (now - rule.argument.toLong() * DAY_MILLIS)..now
            "LISTENS" -> (listens[track.id] ?: 0) >= rule.argument.toLong()
            "MOMENT" -> track.id in momentTracks
            "TAG" -> rule.argument in tagsByTrack[track.id].orEmpty()
            else -> false
        }
        if (collection.matchAll) rules.all(::compare) else rules.any(::compare)
    }.take(collection.limit).toList()
}
