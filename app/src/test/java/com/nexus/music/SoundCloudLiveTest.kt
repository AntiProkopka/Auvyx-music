package com.nexus.music

import android.net.Uri
import com.nexus.music.data.soundcloud.SoundCloudRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SoundCloudLiveTest {
    @Test fun publicCatalogProfileAndStream() = runBlocking {
        assumeTrue(System.getenv("AUVYX_LIVE_SC") == "1")
        for (query in listOf("Монеточка", "ODESZA")) {
            val results = SoundCloudRepository.searchTracks(query)
            val song = results.first { it.artist.contains(query, true) && it.full }
            val profile = SoundCloudRepository.artist(song.artistId, song.permalinkUrl)
            assertTrue(profile.name.isNotBlank())
            val page = SoundCloudRepository.artistTracks(profile.id, null)
            assertTrue(page.tracks.any { it.artistId == profile.id })
            val uri = Uri.Builder().scheme("auvyx-sc").authority("track").appendQueryParameter("url", song.permalinkUrl).build()
            val hls = URL(SoundCloudRepository.resolvePlaybackUri(uri).toString())
            val lines = hls.openConnection().apply { connectTimeout=15000; readTimeout=15000 }.getInputStream().bufferedReader().use { it.readLines() }
            assertEquals("#EXTM3U", lines.first())
            assertTrue(lines.any { it.startsWith("#EXTINF") || it.startsWith("#EXT-X-STREAM-INF") })
        }
    }
}
