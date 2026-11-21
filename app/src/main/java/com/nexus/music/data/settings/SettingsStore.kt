package com.nexus.music.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
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
    }

    private val store get() = context.nexusDataStore

    val themeMode: Flow<String> = store.data.map { it[Keys.THEME] ?: "hyperos" }
    val sortMode: Flow<String> = store.data.map { it[Keys.SORT] ?: "added" }
    val folders: Flow<Set<String>> = store.data.map { it[Keys.FOLDERS] ?: emptySet() }
    val queueIndex: Flow<Int> = store.data.map { it[Keys.QUEUE_INDEX] ?: 0 }
    val queuePosition: Flow<Long> = store.data.map { it[Keys.QUEUE_POSITION] ?: 0L }
    val shuffle: Flow<Boolean> = store.data.map { it[Keys.SHUFFLE] ?: false }
    val repeatMode: Flow<Int> = store.data.map { it[Keys.REPEAT] ?: 0 }
    val speed: Flow<Float> = store.data.map { it[Keys.SPEED] ?: 1f }
    val pitch: Flow<Float> = store.data.map { it[Keys.PITCH] ?: 1f }

    suspend fun setThemeMode(value: String) {
        store.edit { it[Keys.THEME] = value }
    }

    suspend fun setSortMode(value: String) {
        store.edit { it[Keys.SORT] = value }
    }

    suspend fun addFolder(uri: String) {
        store.edit { it[Keys.FOLDERS] = (it[Keys.FOLDERS] ?: emptySet()) + uri }
    }

    suspend fun setPlayback(index: Int, positionMs: Long) {
        store.edit {
            it[Keys.QUEUE_INDEX] = index
            it[Keys.QUEUE_POSITION] = positionMs
        }
    }

    suspend fun setShuffle(value: Boolean) {
        store.edit { it[Keys.SHUFFLE] = value }
    }

    suspend fun setRepeatMode(value: Int) {
        store.edit { it[Keys.REPEAT] = value }
    }

    suspend fun setPlaybackParameters(speed: Float, pitch: Float) {
        store.edit {
            it[Keys.SPEED] = speed.coerceIn(0.5f, 2f)
            it[Keys.PITCH] = pitch.coerceIn(0.5f, 2f)
        }
    }

    /** Сбрасывает только внешний вид; медиатека, папки и очередь не затрагиваются. */
    suspend fun resetUiSettings() {
        store.edit {
            it.remove(Keys.THEME)
            it.remove(Keys.SORT)
        }
    }
}
