package com.nexus.music

import android.app.Application
import com.nexus.music.playback.LyricsRepository
import com.nexus.music.playback.activeLyricIndex
import com.nexus.music.playback.timedLyrics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LyricsLiveTest {
    @Test fun fetchesTimestampedLinesForARealTrack() = runBlocking {
        assumeTrue(System.getenv("AUVYX_LIVE_LYRICS") == "1")
        val lyrics = requireNotNull(LyricsRepository.find("Yellow", "Coldplay", 269_000))
        val lines = timedLyrics(lyrics)
        assertTrue(lines.size > 10)
        assertTrue(activeLyricIndex(lines, 100_000) > activeLyricIndex(lines, 30_000))
    }
}
