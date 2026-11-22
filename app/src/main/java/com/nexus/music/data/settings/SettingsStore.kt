package com.nexus.music.data.settings

import android.content.Context
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

private val Context.nexusDataStore: DataStore<Preferences> by preferencesDataStore(name = "nexus_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
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
        listOf(
            prefs[Keys.EQ_60] ?: DEFAULT_EQ_GAINS[0],
            prefs[Keys.EQ_150] ?: DEFAULT_EQ_GAINS[1],
            prefs[Keys.EQ_400] ?: DEFAULT_EQ_GAINS[2],
            prefs[Keys.EQ_1000] ?: DEFAULT_EQ_GAINS[3],
            prefs[Keys.EQ_2400] ?: DEFAULT_EQ_GAINS[4],
            prefs[Keys.EQ_15000] ?: DEFAULT_EQ_GAINS[5]
        )
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
        if (clean.isNotEmpty()) store.edit { it[Keys.NICKNAME] = clean }
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
            it.remove(Keys.THEME)
            it.remove(Keys.SORT)
        }
    }

    companion object {
        val DEFAULT_EQ_GAINS = listOf(5f, 3f, 0f, 1f, 0f, 2f)
    }
}
