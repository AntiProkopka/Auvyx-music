package com.nexus.music.data.backup

import com.nexus.music.data.db.*
import com.nexus.music.playback.MIN_TRACK_GAIN
import org.json.JSONObject
import java.util.UUID

internal val trackToolTables = listOf("track_gain", "skip_ranges", "skip_disabled", "track_moments", "track_tags", "tags", "track_links",
    "collection_conditions", "smart_collections", "track_sound_profiles")

internal suspend fun restoreTrackTools(tables: JSONObject, ids: Map<Long, Long>, db: NexusDatabase) {
    val tools = db.trackTools()
    fun rows(name: String): List<JSONObject> {
        if (!tables.has(name)) return emptyList()
        val array = tables.getJSONArray(name)
        return List(array.length(), array::getJSONObject)
    }
    fun JSONObject.track(key: String = "trackId") = ids.getValue(getLong(key))
    fun JSONObject.shortText(key: String, length: Int = 120) = getString(key).trim().also { require(it.length <= length) }

    for (row in rows("track_gain")) {
        val gain = row.getDouble("decibels").toFloat()
        require(gain.isFinite() && gain in MIN_TRACK_GAIN..0f)
        tools.putGain(TrackGain(row.track(), gain))
    }
    for (row in rows("track_sound_profiles")) {
        fun boolean(key: String) = row.getInt(key).also { require(it in 0..1) } == 1
        val profile = TrackSoundProfile(row.track(), boolean("enabled"),
            if (row.isNull("eqEnabled")) null else boolean("eqEnabled"),
            if (row.isNull("preset")) null else row.shortText("preset"),
            if (row.isNull("bands")) null else row.shortText("bands"),
            if (row.isNull("speed")) null else row.getDouble("speed").toFloat(),
            if (row.isNull("pitch")) null else row.getDouble("pitch").toFloat())
        profile.overrides()
        db.soundProfiles().put(profile)
    }
    val importedRanges = rows("skip_ranges").map { row ->
        val start = row.getLong("startMs")
        val end = row.getLong("endMs")
        validateRange(start, end, null)
        val enabled = row.getInt("enabled")
        require(enabled in 0..1)
        SkipRange(row.track(), start, end, row.shortText("name"), enabled == 1)
    }
    for (row in importedRanges) tools.putRange(row)
    for (row in rows("skip_disabled")) tools.disableSkip(SkipDisabled(row.track()))
    for (row in rows("track_moments")) {
        val id = UUID.fromString(row.getString("id")).toString()
        val start = row.getLong("startMs")
        val end = if (row.isNull("endMs")) null else row.getLong("endMs")
        validateRange(start, end, null)
        val name = row.shortText("name")
        require(name.isNotBlank())
        val track = row.track()
        require(tools.momentById(id)?.trackId.let { it == null || it == track })
        tools.putMoment(TrackMoment(id, track, start, end, name, row.optLong("createdAt").coerceAtLeast(0L)))
    }
    val tags = mutableMapOf<String, String>()
    for (row in rows("tags")) {
        val id = UUID.fromString(row.getString("id")).toString()
        require(id !in tags)
        val name = tagDisplayName(row.getString("name"))
        val key = canonicalTag(name)
        val known = tools.tagById(id) ?: tools.findTag(key)
        val resolved = known ?: MusicalTag(id, name, key).also { tools.createTag(it) }
        tags[id] = resolved.id
    }
    for (row in rows("track_tags")) {
        val track = row.track()
        val tag = tags.getValue(UUID.fromString(row.getString("tagId")).toString())
        require(tools.hasTag(track, tag) || tools.tagCount(track) < 32)
        tools.putTag(TrackTag(track, tag))
    }
    for (row in rows("track_links")) {
        val first = row.track("firstId")
        val second = row.track("secondId")
        require(first != second)
        tools.putLink(TrackLink(minOf(first, second), maxOf(first, second), row.shortText("label"), row.getLong("createdAt")))
    }
    val conditions = rows("collection_conditions").groupBy { it.getString("collectionId") }.toMutableMap()
    for (row in rows("smart_collections")) {
        val id = UUID.fromString(row.getString("id")).toString()
        val mode = row.getInt("matchAll")
        require(mode in 0..1)
        val collection = SmartCollection(id, row.shortText("name"), mode == 1, row.getInt("limit"))
        val rules = conditions.remove(id).orEmpty().sortedBy { it.getInt("position") }.map { item ->
            val kind = item.getString("kind")
            val argument = item.shortText("argument")
            CollectionCondition(id, item.getInt("position"), kind, if (kind == "TAG") tags[argument] ?: argument else argument)
        }
        db.smartCollections().save(collection, rules)
    }
    require(conditions.isEmpty())

}
