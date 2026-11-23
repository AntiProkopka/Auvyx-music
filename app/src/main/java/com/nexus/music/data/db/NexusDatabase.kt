package com.nexus.music.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        HistoryEntity::class,
        QueueEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun queueDao(): QueueDao
    companion object {
        fun create(context: Context): NexusDatabase =
            Room.databaseBuilder(context.applicationContext, NexusDatabase::class.java, "nexus.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("UPDATE tracks SET sourceId = uri WHERE source = 'local' AND sourceId IS NULL")
                database.execSQL("""
                    INSERT OR IGNORE INTO tracks(uri,title,artist,album,durationMs,addedAt,isFavorite,artworkUri,source,sourceId,sourceUrl)
                    SELECT 'auvyx-sc://track/' || trackId,title,artist,'SoundCloud',durationMs,playedAt,0,artworkUrl,'soundcloud',CAST(trackId AS TEXT),permalinkUrl
                    FROM soundcloud_history ORDER BY playedAt DESC
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO history(trackId,playedAt)
                    SELECT t.id,h.playedAt FROM soundcloud_history h JOIN tracks t
                    ON t.source='soundcloud' AND t.sourceId=CAST(h.trackId AS TEXT)
                    WHERE NOT EXISTS (SELECT 1 FROM history existing WHERE existing.trackId=t.id AND existing.playedAt=h.playedAt)
                """.trimIndent())
                database.execSQL("DROP TABLE soundcloud_favorites")
                database.execSQL("DROP TABLE soundcloud_history")
            }
        }

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tracks ADD COLUMN artworkUri TEXT")
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS soundcloud_favorites (" +
                        "trackId INTEGER NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, " +
                        "durationMs INTEGER NOT NULL, artworkUrl TEXT, permalinkUrl TEXT NOT NULL, " +
                        "addedAt INTEGER NOT NULL, PRIMARY KEY(trackId))"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS soundcloud_history (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, trackId INTEGER NOT NULL, " +
                        "title TEXT NOT NULL, artist TEXT NOT NULL, durationMs INTEGER NOT NULL, " +
                        "artworkUrl TEXT, permalinkUrl TEXT NOT NULL, playedAt INTEGER NOT NULL)"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_soundcloud_history_trackId ON soundcloud_history(trackId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_soundcloud_history_playedAt ON soundcloud_history(playedAt)")
            }
        }

        /**
         * Imports SoundCloud favourites and listening history into the shared track/history
         * tables. Legacy tables are retained so the migration is non-destructive.
         */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tracks ADD COLUMN source TEXT NOT NULL DEFAULT 'local'")
                database.execSQL("ALTER TABLE tracks ADD COLUMN sourceId TEXT")
                database.execSQL("ALTER TABLE tracks ADD COLUMN sourceUrl TEXT")
                database.execSQL("ALTER TABLE tracks ADD COLUMN artistId TEXT")
                database.execSQL("ALTER TABLE tracks ADD COLUMN downloadedPath TEXT")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_tracks_source_sourceId ON tracks(source, sourceId)"
                )
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO tracks
                    (uri, title, artist, album, durationMs, addedAt, isFavorite, artworkUri,
                     source, sourceId, sourceUrl, artistId, downloadedPath)
                    SELECT
                     'auvyx-sc://track/' || CAST(trackId AS TEXT),
                     title, artist, 'SoundCloud', durationMs, addedAt, 1, artworkUrl,
                     'soundcloud', CAST(trackId AS TEXT), permalinkUrl, NULL, NULL
                    FROM soundcloud_favorites
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO tracks
                    (uri, title, artist, album, durationMs, addedAt, isFavorite, artworkUri,
                     source, sourceId, sourceUrl, artistId, downloadedPath)
                    SELECT
                     'auvyx-sc://track/' || CAST(h.trackId AS TEXT),
                     h.title, h.artist, 'SoundCloud', h.durationMs, h.playedAt, 0, h.artworkUrl,
                     'soundcloud', CAST(h.trackId AS TEXT), h.permalinkUrl, NULL, NULL
                    FROM soundcloud_history h
                    WHERE h.id = (
                        SELECT MAX(h2.id) FROM soundcloud_history h2 WHERE h2.trackId = h.trackId
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO history (trackId, playedAt)
                    SELECT t.id, h.playedAt
                    FROM soundcloud_history h
                    INNER JOIN tracks t
                      ON t.source = 'soundcloud' AND t.sourceId = CAST(h.trackId AS TEXT)
                    ORDER BY h.playedAt ASC
                    """.trimIndent()
                )
            }
        }
    }
}
