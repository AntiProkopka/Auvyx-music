package com.nexus.music.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["source", "sourceId"], unique = true)
    ]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val addedAt: Long,
    val isFavorite: Boolean = false,
    val artworkUri: String? = null,
    @ColumnInfo(defaultValue = "'local'") val source: String = "local",
    val sourceId: String? = null,
    val sourceUrl: String? = null,
    val artistId: String? = null,
    val downloadedPath: String? = null
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

@Entity(
    tableName = "playlist_tracks",
    foreignKeys = [
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("playlistId"), Index("trackId")]
)
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val trackId: Long,
    val position: Int
)

@Entity(
    tableName = "history",
    foreignKeys = [ForeignKey(entity = TrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("trackId")]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val playedAt: Long
)

@Entity(tableName = "play_queue")
data class QueueEntity(
    @PrimaryKey val position: Int,
    val trackId: Long
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val trackCount: Int
)

internal val TrackEntity.hasEnabledSource: Boolean
    get() = source == "local" || source == "soundcloud"

internal fun availableAlbumTitle(title: String, source: String?): String? = title.trim().takeIf {
    it.isNotEmpty() && !it.equals("Неизвестный альбом", ignoreCase = true) &&
        !(source == "soundcloud" && it.equals("SoundCloud", ignoreCase = true))
}
