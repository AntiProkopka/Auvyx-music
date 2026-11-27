package com.nexus.music.data.backup

import com.nexus.music.data.db.*
import com.nexus.music.playback.MIN_TRACK_GAIN
import org.json.JSONObject
import java.util.UUID

internal val trackToolTables = listOf("track_gain", "skip_ranges", "skip_disabled", "track_moments", "track_tags", "track_links")

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
        tools.putMoment(TrackMoment(id, track, start, end, name))
    }
    for (row in rows("track_tags")) tools.putTag(TrackTag(row.track(), canonicalTag(row.getString("tag"))))
    for (row in rows("track_links")) {
        val first = row.track("firstId")
        val second = row.track("secondId")
        require(first != second)
        tools.putLink(TrackLink(minOf(first, second), maxOf(first, second), row.shortText("label"), row.getLong("createdAt")))
    }
}
