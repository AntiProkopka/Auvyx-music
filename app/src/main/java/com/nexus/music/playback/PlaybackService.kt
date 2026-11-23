package com.nexus.music.playback

import kotlinx.coroutines.*
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.nexus.music.NexusApp
import com.nexus.music.data.soundcloud.SoundCloudRepository

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var crossfade: LocalCrossfade? = null
    private var session: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()

        val mediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(playbackSource(this))

        val equalizer = (application as NexusApp).equalizer
        val player = ExoPlayer.Builder(this)
            .setRenderersFactory(AuvyxRenderersFactory(this, equalizer.audioProcessor))
            .setMediaSourceFactory(CatalogMediaFactory(mediaSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        crossfade = LocalCrossfade(player) {
            ExoPlayer.Builder(this)
                .setRenderersFactory(AuvyxRenderersFactory(this, equalizer.newProcessor()))
                .setMediaSourceFactory(mediaSourceFactory)
                .setAudioAttributes(player.audioAttributes, false)
                .build().also { PlaybackAudioRouting.routeOverlay(it) }
        }
        val settings = (application as NexusApp).settings
        serviceScope.launch { settings.crossfade.collect { crossfade?.seconds = it } }
        serviceScope.launch { settings.equalizerEnabled.collect(equalizer::setEnabled) }
        serviceScope.launch { settings.equalizerGains.collect(equalizer::setGains) }
        exoPlayer = player
        PlaybackAudioRouting.attach(player)
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        crossfade?.release()
        exoPlayer?.let { PlaybackAudioRouting.detach(it) }
        session?.run {
            player.release()
            release()
        }
        exoPlayer = null
        session = null
        super.onDestroy()
    }
}
