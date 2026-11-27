package com.nexus.music.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY addedAt DESC, id DESC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE source = 'local' ORDER BY addedAt DESC, id DESC")
    fun observeLocal(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY title COLLATE NOCASE")
    fun observeFavorites(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE source = 'local' AND (title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%' OR album LIKE '%' || :q || '%') ORDER BY title COLLATE NOCASE")
    fun search(q: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks")
    suspend fun getAll(): List<TrackEntity>

    @Query("SELECT uri FROM tracks WHERE source = 'local'")
    suspend fun allUris(): List<String>

    @Query("SELECT * FROM tracks WHERE source = :source AND sourceId = :sourceId LIMIT 1")
    suspend fun getBySourceId(source: String, sourceId: String): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(track: TrackEntity): Long

    @Query("UPDATE tracks SET title = :title, artist = :artist, album = :album, artistId = COALESCE(:artistId, artistId), durationMs = :durationMs, artworkUri = :artworkUri, sourceUrl = :sourceUrl WHERE source = 'soundcloud' AND sourceId = :sourceId")
    suspend fun updateSoundCloudMetadata(
        sourceId: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        artworkUri: String?,
        sourceUrl: String,
        artistId: String?
    )

    @Query("UPDATE tracks SET album = '' WHERE source = 'soundcloud' AND album = 'SoundCloud'")
    suspend fun clearFakeSoundCloudAlbums()

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tracks: List<TrackEntity>): List<Long>

    @Query("UPDATE tracks SET uri = :uri, durationMs = :duration WHERE id = :id")
    suspend fun updateCatalogAudio(id: Long, uri: String, duration: Long)

    @Query("UPDATE tracks SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE tracks SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("UPDATE tracks SET downloadedPath = :path WHERE id = :id AND uri = :uri")
    suspend fun setDownloadedForUri(id: Long, uri: String, path: String?): Int

    @Query("UPDATE tracks SET uri = :uri WHERE id = :id AND source = 'local'")
    suspend fun relink(id: Long, uri: String)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Dao
interface PlaylistDao {
    @Query("SELECT p.id AS id, p.name AS name, (SELECT COUNT(*) FROM playlist_tracks pt JOIN tracks t ON t.id = pt.trackId WHERE pt.playlistId = p.id AND t.source IN ('local', 'soundcloud')) AS trackCount FROM playlists p ORDER BY p.createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT t.* FROM tracks t INNER JOIN playlist_tracks pt ON pt.trackId = t.id WHERE pt.playlistId = :playlistId ORDER BY pt.position")
    fun observeTracks(playlistId: Long): Flow<List<TrackEntity>>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert
    suspend fun insertLink(link: PlaylistTrackEntity)

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun contains(playlistId: Long, trackId: Long): Int

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: Long)
}

data class TrackListens(val trackId: Long, val listens: Long)

@Dao
interface HistoryDao {
    @Query("SELECT trackId, COUNT(*) AS listens FROM history GROUP BY trackId")
    fun observeCounts(): Flow<List<TrackListens>>

    @Query("""
        SELECT t.*, h.starts, h.lastPlayed
        FROM tracks t JOIN (
            SELECT trackId, COUNT(*) AS starts, MAX(playedAt) AS lastPlayed
            FROM history GROUP BY trackId
            HAVING COUNT(*) >= :minimum AND MAX(playedAt) <= :cutoff
        ) h ON h.trackId = t.id
        WHERE t.source IN ('local', 'soundcloud')
        ORDER BY h.starts DESC, h.lastPlayed ASC, t.id ASC LIMIT 240
    """)
    fun forgotten(cutoff: Long, minimum: Long): Flow<List<ForgottenTrack>>

    @Insert
    suspend fun insert(item: HistoryEntity)

    @Query("SELECT t.* FROM history h INNER JOIN tracks t ON t.id = h.trackId GROUP BY t.id ORDER BY MAX(h.playedAt) DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TrackEntity>>

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
abstract class QueueDao {
    @Query("SELECT * FROM play_queue ORDER BY position")
    abstract suspend fun getAll(): List<QueueEntity>

    @Query("DELETE FROM play_queue")
    abstract suspend fun clear()

    @Insert
    abstract suspend fun insertAll(items: List<QueueEntity>)

    @Transaction
    open suspend fun replace(items: List<QueueEntity>) {
        clear()
        insertAll(items)
    }
}
