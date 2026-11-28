package com.nexus.music

import androidx.room.Room
import com.nexus.music.data.db.*
import com.nexus.music.playback.attenuation
import com.nexus.music.playback.mergedRanges
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class TrackToolsTest {
    @Test fun overlappingRangesAreMergedAndCannotConsumeTheWholeTrack() {
        fun range(start: Long, end: Long) = SkipRange(1, start, end, "", true)
        assertEquals(listOf(0L until 1_900), mergedRanges(listOf(range(200, 1_200), range(0, 500),
            range(1_200, 1_900), range(1_910, 3_000).copy(enabled = false)), 2_000))
        assertTrue(mergedRanges(listOf(range(0, 1_000), range(1_000, 2_000)), 2_000).isEmpty())
        assertTrue(mergedRanges(listOf(range(0, 4_000)), 2_000).isEmpty())
    }

    @Test fun finiteRepeatUsesBaseModeForFinalPassAndRestoresEncodedEntry() {
        val repeat = com.nexus.music.playback.FiniteRepeat(listOf("7", "7", "8"), 1, 3, 3,
            androidx.media3.common.Player.REPEAT_MODE_OFF)
        val next = repeat.afterRepeat()!!
        assertEquals(2, next.iteration)
        assertEquals(next, com.nexus.music.playback.FiniteRepeat.decode(next.encode()))
        val last = next.afterRepeat()!!
        assertEquals(3, last.iteration)
        assertEquals(1, last.index)
        assertEquals(androidx.media3.common.Player.REPEAT_MODE_OFF, last.playerMode)
        assertNull(last.afterRepeat())
        val single = last.copy(queue = listOf("7"), index = 0, restoreMode = androidx.media3.common.Player.REPEAT_MODE_ALL)
        assertEquals(androidx.media3.common.Player.REPEAT_MODE_OFF, single.playerMode)
    }

    @Test fun gainsAttenuateWithoutExceedingUnity() {
        assertEquals(.630957f, attenuation(-4f), .0001f)
        assertEquals(1f, attenuation(0f))
        assertEquals(1f, attenuation(6f))
        assertEquals(1f, attenuation(Float.NaN))
    }

    @Test fun tagsLinksAndEditedRangesUseStableTrackIdentity() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), NexusDatabase::class.java).build()
        try {
            val tracks = db.trackDao()
            fun track(uri: String) = TrackEntity(uri = uri, title = "Same title", artist = "Artist", album = "", addedAt = 1, durationMs = 2_000)
            val a = tracks.insertOrIgnore(track("content://test/a"))
            val b = tracks.insertOrIgnore(track("content://test/b"))
            val tools = db.trackTools()
            assertNotEquals(a, b)
            tools.assignTag(a, "  Ночной   микс  ")
            tools.assignTag(a, "НОЧНОЙ микс")
            tools.assignTag(b, "Ночной микс")
            tools.assignTag(b, "Фонк")
            val night = tools.findTag("ночной микс")!!
            val phonk = tools.findTag("фонк")!!
            assertEquals("Ночной микс", night.name)
            assertEquals(setOf(b), taggedTrackIds(tools.tags().first(), setOf(night.id, phonk.id), true))
            assertEquals(setOf(a, b), taggedTrackIds(tools.tags().first(), setOf(night.id, phonk.id), false))
            tools.selectTag(a, phonk.id, true)
            tools.selectTag(a, phonk.id, true)
            assertEquals(setOf(night.id, phonk.id), tools.tags(a).first().map { it.id }.toSet())
            tools.selectTag(a, phonk.id, false)
            assertEquals(listOf(night.id), tools.tags(a).first().map { it.id })
            assertEquals(2, tools.catalog().first().size)
            tools.renameTag(night, "Ночь")
            assertEquals(night.id, tools.findTag("ночь")!!.id)
            tools.deleteTag(TrackTag(a, night.id))
            assertEquals(night.id, tools.findTag("ночь")!!.id)
            val link = TrackLink(a, b, "Атмосфера", 1)
            tools.putLink(link)
            assertEquals(a, tools.links(b).first().single().other(b))
            assertEquals(b, tools.links(a).first().single().other(a))
            tools.deleteLink(link)
            assertTrue(tools.links(a).first().isEmpty())
            val range = SkipRange(a, 10, 500, "Вступление", true)
            tools.putRange(range)
            tools.replaceRange(range, range.copy(startMs = 200))
            assertEquals(200L, tools.activeRanges(a).first().single().startMs)
            tools.disableSkip(SkipDisabled(a))
            assertTrue(tools.allActiveRanges().first().isEmpty())
            tools.enableSkip(SkipDisabled(a))
            assertEquals(1, tools.allActiveRanges().first().size)
        } finally {
            db.close()
        }
    }
    @Test fun forgottenTracksRespectFrequencyAndMostRecentStart() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), NexusDatabase::class.java).build()
        try {
            fun item(n: Int) = TrackEntity(uri = "content://test/$n", title = "Song $n", artist = "Artist",
                album = "", durationMs = 1000, addedAt = 1)
            val forgotten = db.trackDao().insertOrIgnore(item(1))
            val recent = db.trackDao().insertOrIgnore(item(2))
            val rare = db.trackDao().insertOrIgnore(item(3))
            val now = java.time.Instant.parse("2026-11-28T08:00:00Z").toEpochMilli()
            val cutoff = now - REDISCOVER_DAYS * DAY_MILLIS
            assertTrue(db.historyDao().forgotten(cutoff, REDISCOVER_MIN_STARTS).first().isEmpty())
            (0L..2L).forEach { offset ->
                val time = cutoff - offset * DAY_MILLIS
                db.historyDao().insert(HistoryEntity(trackId = forgotten, playedAt = time))
                db.historyDao().insert(HistoryEntity(trackId = recent, playedAt = time))
            }
            db.historyDao().insert(HistoryEntity(trackId = recent, playedAt = cutoff + 1))
            db.historyDao().insert(HistoryEntity(trackId = rare, playedAt = cutoff - DAY_MILLIS))
            val rows = db.historyDao().forgotten(cutoff, REDISCOVER_MIN_STARTS).first()
            assertEquals(forgotten, rows.single().track.id)
            assertEquals(3L, rows.single().starts)
            assertEquals(cutoff, rows.single().lastPlayed)
            db.historyDao().insert(HistoryEntity(trackId = forgotten, playedAt = now))
            assertTrue(db.historyDao().forgotten(cutoff, REDISCOVER_MIN_STARTS).first().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test fun collectionCombinesTagAndFavoriteAndRejectsUnknownRules() {
        val id = "sample"
        fun item(n: Long, favorite: Boolean) = TrackEntity(id = n, uri = "content://test/$n",
            title = "Song $n", artist = "Artist", album = "", addedAt = 1, durationMs = 1000, isFavorite = favorite)
        val tag = java.util.UUID.randomUUID().toString()
        val list = SmartCollection(id, "Подборка", true, 50)
        val rules = listOf(CollectionCondition(id, 0, "FAVORITE", ""), CollectionCondition(id, 1, "TAG", tag))
        val tracks = listOf(item(1, true), item(2, true), item(3, false))
        val links = listOf(TrackTag(1, tag), TrackTag(3, tag))
        fun matching(collection: SmartCollection, conditions: List<CollectionCondition>, tags: List<TrackTag>) =
            collectionTracks(collection, conditions, tracks, tags, emptySet(), emptyMap(), 100).map { it.id }
        assertEquals(listOf(1L), matching(list, rules, links))
        assertEquals(listOf(1L, 2L, 3L), matching(list.copy(matchAll = false), rules, links))
        assertTrue(matching(list, rules, emptyList()).isEmpty())
        assertTrue(matching(list, listOf(rules[0].copy(kind = "unknown")), links).isEmpty())
    }

}
