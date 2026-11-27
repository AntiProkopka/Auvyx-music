package com.nexus.music

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.room.Room
import com.nexus.music.data.backup.*
import com.nexus.music.data.db.*
import com.nexus.music.data.local.OfflineAudio
import com.nexus.music.data.settings.SettingsStore
import com.nexus.music.playback.SixBandEqualizer
import com.nexus.music.playback.timedLyrics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LibraryRegressionTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun memoryDb() = Room.inMemoryDatabaseBuilder(context, NexusDatabase::class.java).allowMainThreadQueries().build()
    private fun track(n: Int) = TrackEntity(uri = "content://docs/$n", sourceId = "doc-$n", title="Song $n",
        artist="Artist", album="Album", durationMs=1000L, addedAt=n.toLong())

    @Test fun backupMergesRepeatedlyAndRollsBackOnInvalidLink() = runBlocking {
        val source = memoryDb()
        val dest = memoryDb()
        val settings = SettingsStore(context)
        val file = File(context.cacheDir, "export.json")
        val uri = Uri.fromFile(file)
        try {
            val local = source.trackDao().insertOrIgnore(track(1).copy(isFavorite = true))
            val cloud = source.trackDao().insertOrIgnore(track(2).copy(uri="auvyx-sc://track/77",source="soundcloud",sourceId="77"))
            val list = source.playlistDao().insertPlaylist(PlaylistEntity(name="Mixed", createdAt=2L))
            listOf(cloud,local).forEachIndexed { i,id ->
                source.playlistDao().insertLink(PlaylistTrackEntity(playlistId=list,trackId=id,position=i))
            }
            source.historyDao().insert(HistoryEntity(trackId=cloud, playedAt=10L))
            source.queueDao().replace(listOf(QueueEntity(0,cloud),QueueEntity(1,local)))
            settings.setPlayback(1,753)
            settings.setNickname("Alpha")
            settings.saveLyrics(local.toString(), "lyrics")
            LibraryExport.write(context.contentResolver,uri,source,settings)
            settings.setNickname("Changed")
            dest.trackDao().insertOrIgnore(track(3))
            repeat(2) { LibraryImport.read(context.contentResolver,uri,dest,settings) }
            val restored = dest.playlistDao().observePlaylists().first().single()
            assertEquals(listOf("soundcloud","local"), dest.playlistDao().observeTracks(restored.id).first().map { it.source })
            assertEquals(3,dest.trackDao().getAll().size)
            val fav = dest.trackDao().observeFavorites().first().single()
            assertEquals("lyrics",settings.lyrics(fav.id.toString()).first())
            assertEquals("Alpha",settings.nickname.first())
            assertEquals(753L,settings.queuePosition.first())
            assertEquals(fav.id,dest.queueDao().getAll()[1].trackId)
            assertEquals("soundcloud",dest.historyDao().observeRecent(5).first().single().source)
            val bad = JSONObject(file.readText())
            bad.getJSONObject("tables").getJSONArray("playlists").getJSONObject(0).put("name","Rejected")
            bad.getJSONObject("tables").getJSONArray("playlist_tracks").getJSONObject(0).put("trackId",9999)
            file.writeText(bad.toString())
            assertTrue(runCatching { LibraryImport.read(context.contentResolver,uri,dest,settings) }.isFailure)
            assertEquals(listOf("Mixed"), dest.playlistDao().observePlaylists().first().map { it.name })
        } finally { source.close(); dest.close() }
    }

    @Test fun versionFourUpgradeKeepsSharedIdentityQueueAndHistory() = runBlocking {
        val db = NexusDatabase.create(context)
        val id = db.trackDao().insertOrIgnore(track(1).copy(sourceId=null,isFavorite=true))
        db.queueDao().replace(listOf(QueueEntity(0,id)))
        db.historyDao().insert(HistoryEntity(trackId=id,playedAt=7L))
        val list = db.playlistDao().insertPlaylist(PlaylistEntity(name="Keep",createdAt=3L))
        db.playlistDao().insertLink(PlaylistTrackEntity(playlistId=list,trackId=id,position=0))
        db.close()
        context.openOrCreateDatabase("nexus.db",Context.MODE_PRIVATE,null).use { old ->
            old.execSQL("CREATE TABLE soundcloud_history(id INTEGER PRIMARY KEY, trackId INTEGER,title TEXT,artist TEXT,durationMs INTEGER,artworkUrl TEXT,permalinkUrl TEXT,playedAt INTEGER)")
            old.execSQL("CREATE TABLE soundcloud_favorites(trackId INTEGER PRIMARY KEY)")
            old.execSQL("INSERT INTO soundcloud_history VALUES(1,77,'Song','Artist',1500,NULL,'https://soundcloud.com/a/b',9)")
            old.version=4
        }
        val upgraded = NexusDatabase.create(context)
        try {
            assertEquals(listOf(QueueEntity(0,id)),upgraded.queueDao().getAll())
            assertEquals(id, upgraded.playlistDao().observeTracks(list).first().single().id)
            assertEquals("content://docs/1", upgraded.trackDao().observeFavorites().first().single().sourceId)
            assertEquals(listOf("soundcloud","local"), upgraded.historyDao().observeRecent(5).first().map { it.source })
        } finally { upgraded.close() }
    }

    @Test fun versionOneDatabaseMigratesThroughAllFourSteps() = runBlocking {
        val db = NexusDatabase.create(context)
        db.trackDao().getAll()
        db.close()
        context.openOrCreateDatabase("nexus.db",Context.MODE_PRIVATE,null).use { old ->
            old.execSQL("DROP TABLE tracks")
            old.execSQL("CREATE TABLE tracks(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,uri TEXT NOT NULL,title TEXT NOT NULL,artist TEXT NOT NULL,album TEXT NOT NULL,durationMs INTEGER NOT NULL,addedAt INTEGER NOT NULL,isFavorite INTEGER NOT NULL)")
            old.execSQL("CREATE UNIQUE INDEX index_tracks_uri ON tracks(uri)")
            old.execSQL("INSERT INTO tracks VALUES(19,'content://docs/a','Audio','Name','Album',1000,1,1)")
            old.execSQL("INSERT INTO play_queue VALUES(0,19)")
            old.version = 1
        }
        val current = NexusDatabase.create(context)
        try {
            assertEquals("content://docs/a",current.trackDao().observeFavorites().first().single().sourceId)
            assertEquals(19L,current.queueDao().getAll().single().trackId)
        } finally { current.close() }
    }

    @Test fun allSavedStylesAndPlaybackSettingsSurviveNewReader() = runBlocking {
        val store = SettingsStore(context)
        for (style in listOf("material", "classic")) {
            store.setStyle(style)
            assertEquals(style, SettingsStore(context).visualStyle.first())
        }
        store.setPlayback(3, 12001L)
        store.setPlaybackParameters(1.25f, 1.25f)
        store.setCrossfade(7)
        val saved = SettingsStore(context)
        assertEquals(3,saved.queueIndex.first())
        assertEquals(12001L,saved.queuePosition.first())
        assertEquals(1.25f,saved.pitch.first())
        assertEquals(1.25f,saved.speed.first())
        assertEquals(7,saved.crossfade.first())
    }

    @Test fun appearanceAndSearchSurviveReopenAndBackup() = runBlocking {
        val store = SettingsStore(context)
        val artist = com.nexus.music.data.settings.FollowedArtist("One", 9, true, null, null)
        store.toggleFollow(artist)
        for (accent in com.nexus.music.data.settings.GlassAccent.entries) {
            store.selectAppearance(accent, "photo")
            assertEquals(accent, SettingsStore(context).accent.first())
        }
        store.setBlur(45)
        store.setLabels(false)
        repeat(23) { store.rememberSearch("term $it", "uri/$it") }
        store.rememberSearch("TERM 7", "uri/7")
        val backup = store.backupSettings()
        store.resetUiSettings()
        store.toggleFollow(artist)
        store.clearSearch()
        store.restoreSettings(backup, emptyMap(), 0)
        val saved = SettingsStore(context)
        assertEquals(artist, saved.followed.first().single())
        assertEquals(10, saved.queries.first().size)
        assertEquals("TERM 7", saved.queries.first().first())
        assertEquals(20, saved.openedTracks.first().size)
        assertEquals("uri/7", saved.openedTracks.first().first())
        assertEquals("photo", saved.photo.first())
        assertEquals(45, saved.blur.first())
        assertFalse(saved.recentLabels.first())
        saved.toggleFollow(artist)
        assertTrue(saved.followed.first().isEmpty())
    }

    @Test fun localCopyIsPlayableAsBytesAfterReopenWithoutDocumentProvider() = runBlocking {
        val data = ByteArray(32768) { (it % 127).toByte() }
        val doc = Uri.parse("content://test-offline/song.wav")
        val original = File(context.cacheDir, "original.wav").apply { writeBytes(data) }
        var opens = 0
        val provider = object : android.content.ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = "audio/wav"
            override fun openFile(uri: Uri, mode: String): android.os.ParcelFileDescriptor {
                opens++
                return android.os.ParcelFileDescriptor.open(original, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            }
            override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, order: String?): android.database.Cursor? = null
            override fun insert(uri: Uri, values: android.content.ContentValues?): Uri? = null
            override fun update(uri: Uri, values: android.content.ContentValues?, selection: String?, args: Array<String>?) = 0
            override fun delete(uri: Uri, selection: String?, args: Array<String>?) = 0
        }
        provider.attachInfo(context,android.content.pm.ProviderInfo().apply { authority="test-offline" })
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("test-offline",provider)
        val db = NexusDatabase.create(context)
        val id = db.trackDao().insertOrIgnore(track(9).copy(uri=doc.toString()))
        val t = db.trackDao().getByIds(listOf(id)).single()
        val manager = OfflineAudio(context,db.trackDao(),this)
        val failure = kotlinx.coroutines.CompletableDeferred<String>()
        manager.save(t) { failure.complete(it) }
        kotlinx.coroutines.withTimeout(6000) {
            while (db.trackDao().getByIds(listOf(id)).single().downloadedPath == null) {
                if (failure.isCompleted) error(failure.await())
                kotlinx.coroutines.delay(10)
            }
        }
        val initialOpens = opens
        manager.save(t) { failure.complete(it) }
        kotlinx.coroutines.delay(100)
        assertEquals(initialOpens, opens)
        db.close()
        original.delete()
        val reopened = NexusDatabase.create(context)
        try {
            val saved = reopened.trackDao().getByIds(listOf(id)).single()
            val source = com.nexus.music.playback.playbackSource(context).createDataSource()
            val spec = androidx.media3.datasource.DataSpec.Builder().setUri(doc).setPosition(1024).build()
            val result = ByteArray(data.size - 1024)
            try {
                source.open(spec)
                var done = 0
                while (done < result.size) {
                    val count = source.read(result, done, result.size - done)
                    require(count > 0)
                    done += count
                }
                assertArrayEquals(data.copyOfRange(1024,data.size),result)
            } finally { source.close() }
            OfflineAudio(context,reopened.trackDao(),this).remove(saved)
            assertNull(reopened.trackDao().getByIds(listOf(id)).single().downloadedPath)
            assertFalse(File(saved.downloadedPath!!).exists())
        } finally { reopened.close() }
    }

    @Test fun lyricTimestampsAndSourceKeys() {
        assertEquals(listOf(1000L to "One",2250L to "One"), timedLyrics("[offset:100]\n[00:01.10][00:02.350]One"))
        assertTrue(timedLyrics("plain TXT").isEmpty())
        assertEquals(OfflineAudio.file(context,"content://docs/1"), OfflineAudio.file(context,"content://docs/1"))
        assertNotEquals(OfflineAudio.file(context,"content://docs/2"),OfflineAudio.file(context,"content://docs/1"))
    }

    @Test fun discoveredLyricsDoNotReplaceAnImport() = runBlocking {
        val settings = SettingsStore(context)
        settings.saveLyrics("2000000001", "Imported lyrics")
        settings.saveLyricsIfMissing("2000000001", "Discovered lyrics")
        settings.saveLyricsIfMissing("2000000002", "[00:01.20]Discovered lyrics")
        assertEquals("Imported lyrics", settings.lyrics("2000000001").first())
        assertEquals("[00:01.20]Discovered lyrics", settings.lyrics("2000000002").first())
    }

    @Test fun albumMembershipUsesOnlyRealLocalMetadata() {
        val local = track(1)
        val album = requireNotNull(AppViewModel.AlbumTarget.from(local))
        assertTrue(album.contains(track(2).copy(album = " album ", artist = "ARTIST")))
        assertFalse(album.contains(track(3).copy(artist = "Other artist")))
        assertNull(AppViewModel.AlbumTarget.from(local.copy(album = "")))
        val cloud = local.copy(album = "SoundCloud", source = "soundcloud", sourceId = "99", sourceUrl = "https://soundcloud.com/u/t")
        assertEquals("", AppViewModel.AlbumTarget.from(cloud)?.title)
        assertFalse(album.contains(cloud))
        assertFalse(OfflineAudio.canSave(local.copy(source = "soundcloud")))
    }

    @Test fun deletingLibraryEntryLeavesOriginalFileIntact() = runBlocking {
        val db = memoryDb()
        val file = File(context.cacheDir, "source.wav").apply { writeText("original audio") }
        try {
            val id = db.trackDao().insertOrIgnore(track(1).copy(uri = Uri.fromFile(file).toString()))
            val library = com.nexus.music.data.local.LibraryRepository(context, db, SettingsStore(context))
            library.deleteTracks(listOf(id))
            assertTrue(db.trackDao().getByIds(listOf(id)).isEmpty())
            assertEquals("original audio", file.readText())
        } finally {
            db.close()
        }
    }

    @Test fun bassPresetPreservesRelativeBoostWithoutClippingLoudBass() {
        val equalizer = SixBandEqualizer()
        fun render(values: ShortArray): List<Short> {
            val processor = equalizer.newProcessor()
            processor.configure(AudioFormat(48000, 1, C.ENCODING_PCM_16BIT))
            processor.flush()
            val buffer = ByteBuffer.allocateDirect(values.size * 2).order(ByteOrder.nativeOrder())
            values.forEach { buffer.putShort(it) }
            buffer.flip()
            processor.queueInput(buffer)
            val result = processor.output.order(ByteOrder.nativeOrder())
            return List(result.remaining() / 2) { result.short }
        }

        fun samples(frequency: Int, amplitude: Int) = ShortArray(48000) {
            (sin(2 * PI * frequency * it / 48000) * amplitude).toInt().toShort()
        }

        val bass = samples(60, 32750)
        equalizer.setEnabled(false)
        assertEquals(bass.toList(), render(bass))
        equalizer.setEnabled(true)
        val bassPeak = render(bass).drop(12000).max()
        val midPeak = render(samples(1000, 32750)).drop(12000).max()
        assertTrue(bassPeak > 27500)
        assertTrue(bassPeak > midPeak * 1.4)
        for (frequency in listOf(35, 60, 85, 150)) {
            val output = render(samples(frequency, 32750)).drop(12000)
            assertTrue(output.all { kotlin.math.abs(it.toInt()) < 31500 })
        }
        equalizer.setGains(SixBandEqualizer.DEFAULT)
        assertEquals(bass.toList(), render(bass))
    }

    @Test fun treblePresetCorrectsSavedLegacyBassBand() = kotlinx.coroutines.runBlocking {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val settings = com.nexus.music.data.settings.SettingsStore(app)
        settings.setEqualizerPreset("treble", listOf(-1f, 0f, 0f, 0f, 2f, 4f))
        assertEquals(listOf(0f, 0f, 0f, 0f, 2f, 4f), settings.equalizerGains.first())
        settings.setEqualizerPreset("bass_boost", SixBandEqualizer.BASS_BOOST)
        assertEquals(SixBandEqualizer.BASS_BOOST, settings.equalizerGains.first())
    }

    @Test fun lyricsFollowForwardAndBackwardSeekAndHandleMissingText() {
        val lines = com.nexus.music.playback.timedLyrics("[00:02.00]First\n[00:04.00]Second\n[00:08.00]Third")
        fun active(position: Long) = com.nexus.music.playback.activeLyricIndex(lines, position)
        assertEquals(-1, active(0))
        assertEquals(0, active(2000))
        assertEquals(2, active(9000))
        assertEquals(1, active(4500))
        assertEquals(-1, active(1000))
        assertEquals(-1, com.nexus.music.playback.activeLyricIndex(emptyList(), 2000))
    }
}
