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
        QueueEntity::class,
        TrackGain::class,
        SkipRange::class,
        SkipDisabled::class,
        TrackMoment::class,
        MusicalTag::class,
        TrackTag::class,
        TrackLink::class,
        SmartCollection::class,
        CollectionCondition::class
    ],
    version = 8,
    exportSchema = false
)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun queueDao(): QueueDao
    abstract fun trackTools(): TrackToolsDao
    abstract fun smartCollections(): SmartCollectionDao
    companion object {
        fun create(context: Context): NexusDatabase =
            Room.databaseBuilder(context.applicationContext, NexusDatabase::class.java, "nexus.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                    MIGRATION_6_7, MIGRATION_7_8)
                .build()

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE track_moments ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE smart_collections (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, matchAll INTEGER NOT NULL, `limit` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE collection_conditions (collectionId TEXT NOT NULL, position INTEGER NOT NULL, kind TEXT NOT NULL, argument TEXT NOT NULL, PRIMARY KEY(collectionId,position), FOREIGN KEY(collectionId) REFERENCES smart_collections(id) ON DELETE CASCADE)")
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val track = "FOREIGN KEY(trackId) REFERENCES tracks(id) ON DELETE CASCADE"
                db.execSQL("CREATE TABLE track_gain (trackId INTEGER NOT NULL PRIMARY KEY, decibels REAL NOT NULL, $track)")
                db.execSQL("CREATE TABLE skip_ranges (trackId INTEGER NOT NULL, startMs INTEGER NOT NULL, endMs INTEGER NOT NULL, name TEXT NOT NULL, enabled INTEGER NOT NULL, PRIMARY KEY(trackId,startMs,endMs), $track)")
                db.execSQL("CREATE TABLE skip_disabled (trackId INTEGER NOT NULL PRIMARY KEY, $track)")
                db.execSQL("CREATE TABLE track_moments (id TEXT NOT NULL PRIMARY KEY, trackId INTEGER NOT NULL, startMs INTEGER NOT NULL, endMs INTEGER, name TEXT NOT NULL, $track)")
                db.execSQL("CREATE INDEX index_track_moments_trackId ON track_moments(trackId)")
                db.execSQL("CREATE TABLE tags (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, normalized TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX index_tags_normalized ON tags(normalized)")
                db.execSQL("CREATE TABLE track_tags (trackId INTEGER NOT NULL, tagId TEXT NOT NULL, PRIMARY KEY(trackId,tagId), $track, FOREIGN KEY(tagId) REFERENCES tags(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX index_track_tags_tagId ON track_tags(tagId)")
                db.execSQL("CREATE TABLE track_links (firstId INTEGER NOT NULL, secondId INTEGER NOT NULL, label TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(firstId,secondId), FOREIGN KEY(firstId) REFERENCES tracks(id) ON DELETE CASCADE, FOREIGN KEY(secondId) REFERENCES tracks(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX index_track_links_secondId ON track_links(secondId)")
            }
        }

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
