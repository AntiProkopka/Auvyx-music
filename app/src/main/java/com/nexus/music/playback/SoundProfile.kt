package com.nexus.music.playback

import com.nexus.music.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.json.JSONArray

internal data class SoundSettings(
    val eqEnabled: Boolean = true,
    val preset: String = "bass_boost",
    val gains: List<Float> = SettingsStore.DEFAULT_EQ_GAINS,
    val speed: Float = 1f,
    val pitch: Float = 1f
) {
    fun apply(overrides: SoundOverrides?) = if (overrides == null) this else copy(
        eqEnabled = overrides.eqEnabled ?: eqEnabled,
        preset = overrides.preset ?: preset,
        gains = overrides.gains ?: gains,
        speed = overrides.speed ?: speed,
        pitch = overrides.pitch ?: pitch
    )
}

internal data class SoundOverrides(
    val eqEnabled: Boolean? = null,
    val preset: String? = null,
    val gains: List<Float>? = null,
    val speed: Float? = null,
    val pitch: Float? = null
)

internal val soundPresets = setOf("default", "bass_boost", "treble", "vocal", "custom")

internal fun readSoundBands(encoded: String): List<Float> {
    val array = JSONArray(encoded)
    require(array.length() == SixBandEqualizer.BAND_COUNT)
    return List(array.length()) { array.getDouble(it).toFloat() }.also { values ->
        require(values.all { it.isFinite() && it in -12f..12f })
    }
}

internal class SoundProfileResolver(scope: CoroutineScope, settings: SettingsStore) {
    private var base = SoundSettings()
    private var trackId: Long? = null
    private var profiles = emptyMap<Long, SoundOverrides>()
    private var temporary = SoundOverrides()
    private val _effective = MutableStateFlow(base)
    val effective = _effective.asStateFlow()
    private val _active = MutableStateFlow<Long?>(null)
    val active = _active.asStateFlow()

    init {
        scope.launch {
            settings.soundSettings.distinctUntilChanged().collect { saved -> updateBase { saved } }
        }
    }

    fun updateBase(update: SoundSettings.() -> SoundSettings) {
        base = base.update()
        resolve()
    }

    fun updateTemporary(update: SoundOverrides.() -> SoundOverrides): Boolean {
        if (_active.value == null) return false
        temporary = temporary.update()
        resolve()
        return true
    }

    fun replaceProfiles(values: Map<Long, SoundOverrides>) {
        profiles = values
        resolve()
    }

    fun select(id: Long?) {
        if (trackId == id) return
        trackId = id
        temporary = SoundOverrides()
        resolve()
    }

    fun hasProfile(id: Long?) = id in profiles

    private fun resolve() {
        val profile = profiles[trackId]
        _active.value = trackId.takeIf { profile != null }
        if (profile == null) temporary = SoundOverrides()
        _effective.value = base.apply(profile).apply(temporary)
    }
}
