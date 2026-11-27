package com.nexus.music.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.nexus.music.playback.SoundOverrides
import com.nexus.music.playback.readSoundBands
import com.nexus.music.playback.soundPresets
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "track_sound_profiles", foreignKeys = [
    ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE)
])
data class TrackSoundProfile(
    @PrimaryKey val trackId: Long,
    val enabled: Boolean,
    val eqEnabled: Boolean?,
    val preset: String?,
    val bands: String?,
    val speed: Float?,
    val pitch: Float?
) {
    internal fun overrides(): SoundOverrides {
        require((preset == null) == (bands == null))
        require(preset == null || preset in soundPresets)
        require(speed == null || speed in 0.5f..2f)
        require(pitch == null || pitch in 0.5f..2f)
        return SoundOverrides(eqEnabled, preset, bands?.let(::readSoundBands), speed, pitch)
    }
}

@Dao
interface TrackSoundProfileDao {
    @Query("SELECT * FROM track_sound_profiles WHERE enabled = 1")
    fun enabledProfiles(): Flow<List<TrackSoundProfile>>

    @Query("SELECT * FROM track_sound_profiles WHERE trackId = :id")
    fun profile(id: Long): Flow<TrackSoundProfile?>

    @Upsert suspend fun put(profile: TrackSoundProfile)

    @Delete suspend fun delete(profile: TrackSoundProfile)
}
