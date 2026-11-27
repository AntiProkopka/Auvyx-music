package com.nexus.music.data.settings

import android.content.Context
import com.nexus.music.playback.SixBandEqualizer
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.nexusDataStore: DataStore<Preferences> by preferencesDataStore(name = "nexus_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val CATALOG_PAGES = stringPreferencesKey("catalog_pages")
        val DISLIKED = stringSetPreferencesKey("disliked_tracks")
        val THEME = stringPreferencesKey("theme_mode")
        val SORT = stringPreferencesKey("library_sort")
        val FOLDERS = stringSetPreferencesKey("folders")
        val QUEUE_INDEX = intPreferencesKey("queue_index")
        val QUEUE_POSITION = longPreferencesKey("queue_position")
        val SHUFFLE = booleanPreferencesKey("shuffle")
        val REPEAT = intPreferencesKey("repeat_mode")
        val SPEED = floatPreferencesKey("playback_speed")
        val PITCH = floatPreferencesKey("playback_pitch")
        val NICKNAME = stringPreferencesKey("user_nickname")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_60 = floatPreferencesKey("eq_60")
        val EQ_150 = floatPreferencesKey("eq_150")
        val EQ_400 = floatPreferencesKey("eq_400")
        val EQ_1000 = floatPreferencesKey("eq_1000")
        val EQ_2400 = floatPreferencesKey("eq_2400")
        val EQ_15000 = floatPreferencesKey("eq_15000")
    }

    private val store get() = context.nexusDataStore

    val themeMode: Flow<String> = store.data.map { prefs ->
        when (prefs[Keys.THEME]) {
            "light" -> "light"
            "dark" -> "dark"
            else -> "system"
        }
    }
    private val styleKey = stringPreferencesKey("interface_21")
    private val accentKey = stringPreferencesKey("glass_accent")
    private val photoKey = stringPreferencesKey("glass_photo")
    private val qualityKey = stringPreferencesKey("glass_quality")
    private val blurKey = intPreferencesKey("glass_blur")
    private val labelsKey = booleanPreferencesKey("recent_labels")
    private val followsKey = stringPreferencesKey("followed_artists")
    private val queriesKey = stringPreferencesKey("search_history")
    private val openedKey = stringPreferencesKey("search_tracks")

    val visualStyle = store.data.map { if (it[styleKey] == "material") "material" else "classic" }
    suspend fun setStyle(style: String) {
        require(style == "classic" || style == "material")
        store.edit { it[styleKey] = style }
    }
    val glassQuality = store.data.map { prefs ->
        GlassQuality.entries.find { it.name == prefs[qualityKey] } ?: GlassQuality.Ultra
    }
    suspend fun setGlassQuality(quality: GlassQuality) {
        store.edit { it[qualityKey] = quality.name }
    }

    val accent = store.data.map { prefs -> GlassAccent.entries.find { it.name == prefs[accentKey] } ?: GlassAccent.Sunset }
    val photo = store.data.map { it[photoKey].orEmpty() }
    val blur = store.data.map { (it[blurKey] ?: 32).coerceIn(8, 56) }
    val recentLabels = store.data.map { it[labelsKey] ?: true }
    suspend fun selectAppearance(accent: GlassAccent, photo: String) {
        store.edit { it[accentKey] = accent.name; it[photoKey] = photo }
    }
    suspend fun setBlur(value: Int) { store.edit { it[blurKey] = value.coerceIn(8, 56) } }
    suspend fun setLabels(value: Boolean) { store.edit { it[labelsKey] = value } }

    val followed = store.data.map { FollowedArtist.read(it[followsKey] ?: "[]") }
    suspend fun toggleFollow(artist: FollowedArtist) {
        store.edit { prefs ->
            val artists = FollowedArtist.read(prefs[followsKey] ?: "[]")
            val next = if (artists.any { it.key == artist.key }) artists.filterNot { it.key == artist.key } else artists + artist
            prefs[followsKey] = JSONArray(next.map { it.json() }).toString()
        }
    }

    val queries = store.data.map { orderedStrings(it[queriesKey]) }
    val openedTracks = store.data.map { orderedStrings(it[openedKey]) }
    suspend fun rememberSearch(query: String, uri: String? = null) {
        val clean = query.trim().take(256)
        store.edit { prefs ->
            if (clean.isNotBlank()) prefs[queriesKey] = JSONArray((listOf(clean) + orderedStrings(prefs[queriesKey])
                .filterNot { it.equals(clean, true) }).take(10)).toString()
            if (uri != null) prefs[openedKey] = JSONArray((listOf(uri) + orderedStrings(prefs[openedKey])).distinct().take(20)).toString()
        }
    }
    suspend fun clearSearch() { store.edit { it.remove(queriesKey); it.remove(openedKey) } }
    val crossfade = store.data.map { it[intPreferencesKey("crossfade_seconds")] ?: 0 }
    suspend fun setCrossfade(value: Int) { store.edit { it[intPreferencesKey("crossfade_seconds")] = value.coerceIn(0,12) } }

    val sortMode: Flow<String> = store.data.map { it[Keys.SORT] ?: "added" }
    val folders: Flow<Set<String>> = store.data.map { it[Keys.FOLDERS] ?: emptySet() }
    val queueIndex: Flow<Int> = store.data.map { it[Keys.QUEUE_INDEX] ?: 0 }
    val queuePosition: Flow<Long> = store.data.map { it[Keys.QUEUE_POSITION] ?: 0L }
    val shuffle: Flow<Boolean> = store.data.map { it[Keys.SHUFFLE] ?: false }
    val repeatMode: Flow<Int> = store.data.map { it[Keys.REPEAT] ?: 0 }
    val speed: Flow<Float> = store.data.map { it[Keys.SPEED] ?: 1f }
    val pitch: Flow<Float> = store.data.map { it[Keys.PITCH] ?: 1f }
    val nickname: Flow<String?> = store.data.map { it[Keys.NICKNAME] }
    val equalizerEnabled: Flow<Boolean> = store.data.map { it[Keys.EQ_ENABLED] ?: true }
    val equalizerPreset: Flow<String> = store.data.map { it[Keys.EQ_PRESET] ?: "bass_boost" }
    val equalizerGains: Flow<List<Float>> = store.data.map { prefs ->
        val gains = listOf(
            prefs[Keys.EQ_60] ?: DEFAULT_EQ_GAINS[0],
            prefs[Keys.EQ_150] ?: DEFAULT_EQ_GAINS[1],
            prefs[Keys.EQ_400] ?: DEFAULT_EQ_GAINS[2],
            prefs[Keys.EQ_1000] ?: DEFAULT_EQ_GAINS[3],
            prefs[Keys.EQ_2400] ?: DEFAULT_EQ_GAINS[4],
            prefs[Keys.EQ_15000] ?: DEFAULT_EQ_GAINS[5]
        )
        if (prefs[Keys.EQ_PRESET] == "treble" && gains == listOf(-1f, 0f, 0f, 0f, 2f, 4f)) {
            SixBandEqualizer.TREBLE_BOOST
        } else gains
    }

    val catalogPages = store.data.map { prefs ->
        val data = JSONObject(prefs[Keys.CATALOG_PAGES] ?: "{}")
        data.keys().asSequence().associateWith { data.getString(it) }
    }

    suspend fun toggleCatalogPage(url: String, title: String) {
        store.edit { prefs ->
            val pages = JSONObject(prefs[Keys.CATALOG_PAGES] ?: "{}")
            if (pages.has(url)) pages.remove(url) else pages.put(url, title.take(256))
            prefs[Keys.CATALOG_PAGES] = pages.toString()
        }
    }

    private val rediscoverHiddenKey = stringSetPreferencesKey("rediscover_hidden")
    val rediscoverHidden = store.data.map { it[rediscoverHiddenKey].orEmpty() }
    suspend fun hideRediscover(id: Long) {
        store.edit { it[rediscoverHiddenKey] = it[rediscoverHiddenKey].orEmpty() + id.toString() }
    }
    suspend fun resetRediscoverHidden() { store.edit { it.remove(rediscoverHiddenKey) } }

    val dislikedTracks: Flow<Set<String>> = store.data.map { it[Keys.DISLIKED].orEmpty() }

    suspend fun toggleDisliked(id: String) {
        store.edit {
            val previous = it[Keys.DISLIKED].orEmpty()
            it[Keys.DISLIKED] = if (id in previous) previous - id else previous + id
        }
    }

    fun lyrics(id: String): Flow<String> =
        store.data.map { it[stringPreferencesKey("lyrics_$id")].orEmpty() }

    suspend fun saveLyrics(id: String, content: String) {
        store.edit { it[stringPreferencesKey("lyrics_$id")] = content }
    }

    suspend fun saveLyricsIfMissing(id: String, content: String) {
        val key = stringPreferencesKey("lyrics_$id")
        store.edit { if (it[key].isNullOrBlank()) it[key] = content }
    }

    suspend fun backupSettings(): JSONObject = JSONObject().apply {
        for ((key, value) in store.data.first().asMap()) {
            put(key.name, if (value is Set<*>) JSONArray(value) else value)
        }
    }

    suspend fun restoreSettings(data: JSONObject, ids: Map<Long, Long>, queueSize: Int) {
        store.edit { prefs ->
            for (key in data.keys()) {
                when (key) {
                    "interface_21", "glass_quality", "glass_accent", "glass_photo", "theme_mode", "library_sort", "user_nickname", "eq_preset", "visual_style" ->
                        prefs[stringPreferencesKey(key)] = data.getString(key)
                    "glass_blur" -> prefs[blurKey] = data.getInt(key).coerceIn(8, 56)
                    "followed_artists" -> {
                        val raw = data.getString(key)
                        FollowedArtist.read(raw)
                        prefs[followsKey] = raw
                    }
                    "search_history", "search_tracks" -> prefs[stringPreferencesKey(key)] =
                        JSONArray(orderedStrings(data.getString(key)).distinct().take(if (key == "search_history") 10 else 20)).toString()
                    "catalog_pages" -> prefs[Keys.CATALOG_PAGES] = JSONObject(data.getString(key)).toString()
                    "folders", "disliked_tracks", "rediscover_hidden" -> {
                        val array = data.getJSONArray(key)
                        prefs[stringSetPreferencesKey(key)] = (0 until array.length()).mapNotNull {
                            val value = array.getString(it)
                            if (key == "rediscover_hidden" || (key == "disliked_tracks" && value.toLongOrNull() != null))
                                ids[value.toLongOrNull()]?.toString() else value
                        }.toSet()
                    }
                    "recent_labels", "shuffle", "eq_enabled" -> prefs[booleanPreferencesKey(key)] = data.getBoolean(key)
                    "queue_index" -> if (queueSize > 0) prefs[Keys.QUEUE_INDEX] = data.getInt(key).coerceIn(0, queueSize - 1)
                    "queue_position" -> if (queueSize > 0) prefs[Keys.QUEUE_POSITION] = data.getLong(key).coerceAtLeast(0)
                    "repeat_mode" -> prefs[Keys.REPEAT] = data.getInt(key).also { require(it in 0..2) }
                    "crossfade_seconds" -> prefs[intPreferencesKey(key)] = data.getInt(key).also { require(it in 0..12) }
                    "playback_speed", "playback_pitch" -> prefs[floatPreferencesKey(key)] =
                        data.getDouble(key).toFloat().also { require(it in 0.5f..2f) }
                    "eq_60","eq_150","eq_400","eq_1000","eq_2400","eq_15000" ->
                        prefs[floatPreferencesKey(key)] = data.getDouble(key).toFloat().also { require(it in -12f..12f) }
                    else -> if (key.startsWith("lyrics_")) {
                        ids[key.removePrefix("lyrics_").toLongOrNull()]?.let { id ->
                            prefs[stringPreferencesKey("lyrics_$id")] = data.getString(key)
                        }
                    }
                }
            }
        }
    }

    suspend fun setThemeMode(value: String) { store.edit { it[Keys.THEME] = value } }
    suspend fun setSortMode(value: String) { store.edit { it[Keys.SORT] = value } }
    suspend fun addFolder(uri: String) { store.edit { it[Keys.FOLDERS] = (it[Keys.FOLDERS] ?: emptySet()) + uri } }

    suspend fun setPlayback(index: Int, positionMs: Long) {
        store.edit {
            it[Keys.QUEUE_INDEX] = index
            it[Keys.QUEUE_POSITION] = positionMs
        }
    }

    suspend fun setShuffle(value: Boolean) { store.edit { it[Keys.SHUFFLE] = value } }
    suspend fun setRepeatMode(value: Int) { store.edit { it[Keys.REPEAT] = value } }

    suspend fun setPlaybackParameters(speed: Float, pitch: Float) {
        store.edit {
            it[Keys.SPEED] = speed.coerceIn(0.5f, 2f)
            it[Keys.PITCH] = pitch.coerceIn(0.5f, 2f)
        }
    }

    suspend fun setNickname(value: String) {
        val clean = value.trim().take(32)
        store.edit { it[Keys.NICKNAME] = clean }
    }

    suspend fun setEqualizerEnabled(value: Boolean) {
        store.edit { it[Keys.EQ_ENABLED] = value }
    }

    suspend fun setEqualizerPreset(preset: String, gains: List<Float>) {
        if (gains.size != 6) return
        store.edit {
            it[Keys.EQ_PRESET] = preset
            it[Keys.EQ_60] = gains[0].coerceIn(-12f, 12f)
            it[Keys.EQ_150] = gains[1].coerceIn(-12f, 12f)
            it[Keys.EQ_400] = gains[2].coerceIn(-12f, 12f)
            it[Keys.EQ_1000] = gains[3].coerceIn(-12f, 12f)
            it[Keys.EQ_2400] = gains[4].coerceIn(-12f, 12f)
            it[Keys.EQ_15000] = gains[5].coerceIn(-12f, 12f)
        }
    }

    /** Сбрасывает только внешний вид; медиатека, никнейм и звук остаются без изменений. */
    suspend fun resetUiSettings() {
        store.edit {
            it.remove(styleKey)
            it.remove(accentKey)
            it.remove(photoKey)
            it.remove(blurKey)
            it.remove(qualityKey)
            it.remove(labelsKey)
            it.remove(Keys.THEME)
            it.remove(Keys.SORT)
        }
    }

    companion object {
        val DEFAULT_EQ_GAINS = listOf(5f, 3f, 0f, 1f, 0f, 2f)
    }
}
