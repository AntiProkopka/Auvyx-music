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
        SoundCloudFavoriteEntity::class,
        SoundCloudHistoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun queueDao(): QueueDao
    abstract fun soundCloudFavoriteDao(): SoundCloudFavoriteDao
    abstract fun soundCloudHistoryDao(): SoundCloudHistoryDao

    companion object {
        fun create(context: Context): NexusDatabase =
            Room.databaseBuilder(context.applicationContext, NexusDatabase::class.java, "nexus.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tracks ADD COLUMN artworkUri TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
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
    }
}
