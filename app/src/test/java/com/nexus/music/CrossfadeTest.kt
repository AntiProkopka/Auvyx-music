package com.nexus.music

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.nexus.music.playback.LocalCrossfade
import com.nexus.music.playback.skipFadeProgress
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class CrossfadeTest {
    // Controllable playheads let the test measure the mix at exact media times.
    private class Deck(val current: MediaItem, val next: MediaItem) {
        var position = 0L
        var playing = true
        var playbackState = Player.STATE_READY
        var seekable = true
        var parameters = PlaybackParameters.DEFAULT
        var volume = 1f
        var released = false
        val player = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { _, m, args ->
            when (m.name) {
                "getApplicationLooper" -> Looper.getMainLooper()
                "isPlaying","getPlayWhenReady" -> playing
                "getPlayerError" -> null
                "getCurrentMediaItemIndex" -> 0
                "getNextMediaItemIndex" -> 1
                "getCurrentMediaItem" -> current
                "getMediaItemAt" -> next
                "getCurrentPosition" -> position
                "getDuration" -> 10000L
                "getRepeatMode" -> Player.REPEAT_MODE_OFF
                "getPlaybackState" -> playbackState
                "isCurrentMediaItemLive" -> false
                "isCurrentMediaItemSeekable" -> seekable
                "getPlaybackParameters" -> parameters
                "setPlaybackParameters" -> { parameters = args[0] as PlaybackParameters; null }
                "setVolume" -> { volume = args[0] as Float; null }
                "play" -> { playing = true; null }
                "release" -> { released = true; null }
                "setMediaItem", "prepare", "addListener", "removeListener" -> null
                else -> error(m.name)
            }
        } as ExoPlayer
    }

    @Test fun skipFadeMultipliesTrackAttenuationAndRestoresIt() {
        val item = MediaItem.Builder().setMediaId("track").setUri("content://test/track").build()
        val deck = Deck(item, item)
        val mix = LocalCrossfade(deck.player) { error("No crossfade requested") }
        try {
            mix.setAttenuation(mapOf("track" to 0.63f))
            val progress = ::skipFadeProgress
            assertEquals(0f, progress(0L), 0f)
            assertEquals(0.5f, progress(24L), 0.001f)
            assertEquals(1f, progress(48L), 0f)
            mix.setSkipGain(1f - progress(24L))
            assertEquals(0.315f, deck.volume, 0.001f)
            mix.setSkipGain(0f)
            assertEquals(0f, deck.volume, 0f)
            mix.setSkipGain(1f)
            assertEquals(0.63f, deck.volume, 0.001f)
        } finally { mix.release() }
    }

    @Test fun overlappingLocalDecksShareSpeedAndReleaseOnPause() {
        val a = MediaItem.fromUri("content://test/a")
        val b = MediaItem.fromUri("content://test/b")
        val main = Deck(a,b).apply { position=6000; parameters=PlaybackParameters(1.25f,1.25f) }
        val overlay = Deck(b,a).apply { playing=false }
        val mix = LocalCrossfade(main.player) { overlay.player }
        try {
            mix.seconds=4
            shadowOf(Looper.getMainLooper()).idle()
            main.position=8000
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40))
            assertTrue(overlay.playing)
            assertEquals(main.parameters,overlay.parameters)
            assertEquals(0.5f,main.volume,0.002f)
            assertEquals(main.volume,overlay.volume,0.002f)
            main.playing=false
            mix.onIsPlayingChanged(false)
            assertTrue(overlay.released)
            assertEquals(1f,main.volume,0f)
        } finally { mix.release() }
    }

    @Test fun onDemandStreamOverlapsAndReleasesIfIncomingStreamBuffers() {
        val a = MediaItem.fromUri("auvyx-sc://track/41")
        val b = MediaItem.fromUri("auvyx-sc://track/42")
        val main = Deck(a, b).apply { position = 6000 }
        val overlay = Deck(b, a).apply { playing = false }
        val mix = LocalCrossfade(main.player) { overlay.player }
        try {
            mix.seconds = 4
            shadowOf(Looper.getMainLooper()).idle()
            main.position = 8000
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40))
            assertTrue(overlay.playing)
            assertEquals(0.5f, overlay.volume, 0.002f)
            assertEquals(1f, main.volume + overlay.volume, 0f)
            overlay.playbackState = Player.STATE_BUFFERING
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40))
            assertTrue(overlay.released)
            assertEquals(1f, main.volume, 0f)
        } finally { mix.release() }
    }

    @Test fun stopReleasesPreloadedIncomingTrack() {
        val a = MediaItem.fromUri("content://test/a")
        val b = MediaItem.fromUri("auvyx-sc://track/42")
        val main = Deck(a, b).apply { position = 4000 }
        val overlay = Deck(b, a).apply { playing = false; playbackState = Player.STATE_BUFFERING }
        val mix = LocalCrossfade(main.player) { overlay.player }
        try {
            mix.seconds = 4
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(overlay.playing)
            assertFalse(overlay.released)
            main.playing = false
            mix.onPlaybackStateChanged(Player.STATE_IDLE)
            assertTrue(overlay.released)
            assertEquals(1f, main.volume, 0f)
        } finally { mix.release() }
    }
}
