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
    @Test fun officialAlbumHasTheSelectedTrackAndPreservesThePublishedOrder() = runBlocking {
        assumeTrue(System.getenv("AUVYX_LIVE_SC") == "1")
        val song = SoundCloudRepository.searchTracks("Coldplay Yellow").first {
            it.title == "Yellow" && it.albumTitle == "Parachutes" && it.credit == "Coldplay"
        }
        val album = requireNotNull(SoundCloudRepository.album(song.id, song.permalinkUrl))
        assertEquals("Parachutes", album.title)
        assertEquals("Coldplay", album.artist)
        assertEquals("2000-07-10", album.releaseDate)
        assertEquals(10, album.tracks.size)
        assertEquals(song.id, album.tracks[4].id)
        assertTrue(album.tracks.any { it.title == "Trouble" })
        assertNotNull(album.artworkUrl)
        assertTrue(album.durationMs > 0)
    }

    @Test fun artistAndReleaseSearchOpenRealCatalogs() = runBlocking {
        assumeTrue(System.getenv("AUVYX_LIVE_SC") == "1")
        val artist = SoundCloudRepository.searchArtists("ODESZA").first { it.name.equals("ODESZA", true) }
        val catalog = SoundCloudRepository.artistReleases(artist.id)
        assertTrue(catalog.isNotEmpty())
        assertTrue(catalog.all { it.owner == artist.id })
        assertEquals(catalog.size, catalog.distinctBy { it.id }.size)
        val release = SoundCloudRepository.searchReleases("Coldplay Parachutes").first { it.title.equals("Parachutes", true) }
        val album = requireNotNull(SoundCloudRepository.releaseAlbum(release.id))
        assertEquals(release.title, album.title)
        assertTrue(album.tracks.any { it.title.equals("Yellow", true) })
    }

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
