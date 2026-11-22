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

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY title COLLATE NOCASE")
    fun observeFavorites(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%' OR album LIKE '%' || :q || '%' ORDER BY title COLLATE NOCASE")
    fun search(q: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks")
    suspend fun getAll(): List<TrackEntity>

    @Query("SELECT uri FROM tracks")
    suspend fun allUris(): List<String>

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tracks: List<TrackEntity>): List<Long>

    @Query("UPDATE tracks SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Dao
interface PlaylistDao {
    @Query("SELECT p.id AS id, p.name AS name, (SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = p.id) AS trackCount FROM playlists p ORDER BY p.createdAt DESC")
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

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(item: HistoryEntity)

    @Query("SELECT t.* FROM history h INNER JOIN tracks t ON t.id = h.trackId GROUP BY t.id ORDER BY MAX(h.playedAt) DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TrackEntity>>

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface SoundCloudFavoriteDao {
    @Query("SELECT * FROM soundcloud_favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<SoundCloudFavoriteEntity>>

    @Query("SELECT * FROM soundcloud_favorites WHERE trackId = :trackId LIMIT 1")
    suspend fun getById(trackId: Long): SoundCloudFavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SoundCloudFavoriteEntity)

    @Query("DELETE FROM soundcloud_favorites WHERE trackId = :trackId")
    suspend fun delete(trackId: Long)
}

@Dao
interface SoundCloudHistoryDao {
    @Insert
    suspend fun insert(item: SoundCloudHistoryEntity)

    @Query("SELECT * FROM soundcloud_history WHERE id IN (SELECT MAX(id) FROM soundcloud_history GROUP BY trackId) ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SoundCloudHistoryEntity>>

    @Query("DELETE FROM soundcloud_history")
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
