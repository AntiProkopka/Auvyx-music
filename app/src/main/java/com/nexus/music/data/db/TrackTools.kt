package com.nexus.music.data.db

import androidx.room.*
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Entity(tableName = "track_gain", foreignKeys = [
    ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE)
])
data class TrackGain(@PrimaryKey val trackId: Long, val decibels: Float)

@Entity(tableName = "skip_ranges", primaryKeys = ["trackId", "startMs", "endMs"], foreignKeys = [
    ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE)
])
data class SkipRange(val trackId: Long, val startMs: Long, val endMs: Long, val name: String, val enabled: Boolean)

@Entity(tableName = "skip_disabled", foreignKeys = [
    ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE)
])
data class SkipDisabled(@PrimaryKey val trackId: Long)

@Entity(tableName = "track_moments", foreignKeys = [
    ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("trackId")])
data class TrackMoment(@PrimaryKey val id: String, val trackId: Long, val startMs: Long, val endMs: Long?, val name: String)

@Entity(tableName = "tags", indices = [Index(value = ["normalized"], unique = true)])
data class MusicalTag(@PrimaryKey val id: String, val name: String, val normalized: String)

@Entity(tableName = "track_tags", primaryKeys = ["trackId", "tagId"], foreignKeys = [
    ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(MusicalTag::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("tagId")])
data class TrackTag(val trackId: Long, val tagId: String)

@Entity(tableName = "track_links", primaryKeys = ["firstId", "secondId"], foreignKeys = [
    ForeignKey(TrackEntity::class, ["id"], ["firstId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(TrackEntity::class, ["id"], ["secondId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("secondId")])
data class TrackLink(val firstId: Long, val secondId: Long, val label: String, val createdAt: Long) {
    fun other(trackId: Long): Long = if (firstId == trackId) secondId else firstId
}

@Dao
abstract class TrackToolsDao {
    @Query("SELECT * FROM track_gain")
    abstract fun gains(): Flow<List<TrackGain>>
    @Query("SELECT * FROM track_gain WHERE trackId = :id")
    abstract fun gain(id: Long): Flow<TrackGain?>
    @Upsert abstract suspend fun putGain(row: TrackGain)

    @Query("SELECT * FROM skip_ranges WHERE enabled = 1 AND trackId NOT IN (SELECT trackId FROM skip_disabled)")
    abstract fun allActiveRanges(): Flow<List<SkipRange>>
    @Query("SELECT * FROM skip_ranges WHERE trackId = :id ORDER BY startMs, endMs")
    abstract fun ranges(id: Long): Flow<List<SkipRange>>
    @Query("SELECT * FROM skip_disabled WHERE trackId = :id")
    abstract fun skipDisabled(id: Long): Flow<SkipDisabled?>
    fun activeRanges(id: Long): Flow<List<SkipRange>> =
        combine(ranges(id), skipDisabled(id)) { rows, disabled ->
            if (disabled == null) rows.filter { it.enabled } else emptyList()
        }
    @Upsert abstract suspend fun putRange(row: SkipRange)
    @Delete abstract suspend fun deleteRange(row: SkipRange)
    @Upsert abstract suspend fun disableSkip(row: SkipDisabled)
    @Delete abstract suspend fun enableSkip(row: SkipDisabled)
    @Transaction
    open suspend fun replaceRange(previous: SkipRange?, next: SkipRange) {
        if (previous != null) deleteRange(previous)
        putRange(next)
    }

    @Query("SELECT * FROM track_moments ORDER BY trackId, startMs, id")
    abstract fun moments(): Flow<List<TrackMoment>>
    @Query("SELECT * FROM track_moments WHERE trackId = :id ORDER BY startMs, id")
    abstract fun moments(id: Long): Flow<List<TrackMoment>>
    @Query("SELECT * FROM track_moments WHERE id = :id")
    abstract suspend fun momentById(id: String): TrackMoment?
    @Upsert abstract suspend fun putMoment(row: TrackMoment)
    @Delete abstract suspend fun deleteMoment(row: TrackMoment)

    @Query("SELECT * FROM tags ORDER BY normalized")
    abstract fun catalog(): Flow<List<MusicalTag>>
    @Query("SELECT * FROM track_tags")
    abstract fun tags(): Flow<List<TrackTag>>
    @Query("SELECT tags.* FROM tags JOIN track_tags ON tags.id = track_tags.tagId WHERE trackId = :id ORDER BY normalized")
    abstract fun tags(id: Long): Flow<List<MusicalTag>>
    @Query("SELECT * FROM tags WHERE normalized = :name")
    abstract suspend fun findTag(name: String): MusicalTag?
    @Query("SELECT * FROM tags WHERE id = :id")
    abstract suspend fun tagById(id: String): MusicalTag?
    @Query("SELECT COUNT(*) FROM track_tags WHERE trackId = :id")
    abstract suspend fun tagCount(id: Long): Int
    @Query("SELECT EXISTS(SELECT 1 FROM track_tags WHERE trackId = :track AND tagId = :tag)")
    abstract suspend fun hasTag(track: Long, tag: String): Boolean
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun putTag(row: TrackTag)
    @Insert abstract suspend fun createTag(row: MusicalTag)
    @Update abstract suspend fun updateTag(row: MusicalTag)
    @Delete abstract suspend fun deleteTag(row: TrackTag)
    @Delete abstract suspend fun deleteTag(row: MusicalTag)
    @Transaction
    open suspend fun assignTag(track: Long, name: String) {
        val normalized = canonicalTag(name)
        val tag = findTag(normalized) ?: MusicalTag(java.util.UUID.randomUUID().toString(),
            tagDisplayName(name), normalized).also { createTag(it) }
        require(hasTag(track, tag.id) || tagCount(track) < 32) { "У трека уже 32 тега" }
        putTag(TrackTag(track, tag.id))
    }

    @Transaction
    open suspend fun renameTag(tag: MusicalTag, name: String) {
        val normalized = canonicalTag(name)
        require(findTag(normalized).let { it == null || it.id == tag.id }) { "Такой тег уже есть" }
        updateTag(tag.copy(name = tagDisplayName(name), normalized = normalized))
    }

    @Query("SELECT * FROM track_links WHERE firstId = :id OR secondId = :id ORDER BY createdAt, firstId, secondId")
    abstract fun links(id: Long): Flow<List<TrackLink>>
    @Upsert abstract suspend fun putLink(row: TrackLink)
    @Delete abstract suspend fun deleteLink(row: TrackLink)
}

internal fun tagDisplayName(text: String): String =
    java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC).trim()
        .replace(Regex("[\\p{Z}\\s]+"), " ")
        .also { require(it.length in 1..40) { "Укажите тег длиной до 40 символов" } }

internal fun canonicalTag(text: String): String = tagDisplayName(text).lowercase(Locale.ROOT)

internal fun taggedTrackIds(rows: List<TrackTag>, tags: Set<String>, matchAll: Boolean): Set<Long> {
    if (tags.isEmpty()) return emptySet()
    val matches = rows.asSequence().filter { it.tagId in tags }.groupingBy { it.trackId }.eachCount()
    return if (matchAll) matches.filterValues { it == tags.size }.keys else matches.keys
}

internal fun validateRange(start: Long, end: Long?, duration: Long?) {
    require(start >= 0 && (end == null || end > start)) { "Конец должен быть позже начала" }
    require(duration == null || (start < duration && (end == null || end <= duration))) {
        "Метка выходит за длительность трека"
    }
}
