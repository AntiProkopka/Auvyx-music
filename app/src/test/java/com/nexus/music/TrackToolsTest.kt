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
            tools.putTag(TrackTag(a, canonicalTag("  Ночной   микс  ")))
            tools.putTag(TrackTag(a, canonicalTag("НОЧНОЙ микс")))
            assertEquals(listOf(TrackTag(a, "ночной микс")), tools.tags().first())
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
}
