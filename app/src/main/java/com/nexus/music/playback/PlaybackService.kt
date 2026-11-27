package com.nexus.music.playback

import kotlinx.coroutines.*
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
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
        val app = application as NexusApp
        val settings = app.settings
        val profiles = app.soundProfiles
        serviceScope.launch { settings.crossfade.collect { crossfade?.seconds = it } }
        serviceScope.launch {
            profiles.effective.collect { effective ->
                equalizer.setEnabled(effective.eqEnabled)
                equalizer.setGains(effective.gains)
                val params = PlaybackParameters(effective.speed, effective.pitch)
                if (player.playbackParameters != params) player.setPlaybackParameters(params)
            }
        }
        serviceScope.launch {
            app.database.soundProfiles().enabledProfiles().collect { rows ->
                profiles.replaceProfiles(rows.mapNotNull { row ->
                    runCatching { row.trackId to row.overrides() }.getOrNull()
                }.toMap())
            }
        }
        val tools = app.database.trackTools()
        val trackPlayback = TrackPlaybackTools(player, serviceScope, app.activeMoment) { app.activeMoment.value = null }
        player.addListener(trackPlayback)
        var skips = emptyMap<Long, List<com.nexus.music.data.db.SkipRange>>()
        fun updateRanges() {
            trackPlayback.ranges = skips[player.currentMediaItem?.mediaId?.toLongOrNull()].orEmpty()
        }
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                profiles.select(mediaItem?.mediaId?.toLongOrNull())
                updateRanges()
            }
        })
        crossfade?.canMixTrack = { id ->
            val trackId = id.toLongOrNull()
            trackId !in skips && !profiles.hasProfile(trackId) && app.activeMoment.value?.trackId?.toString() != id
        }
        serviceScope.launch { tools.allActiveRanges().collect { rows -> skips = rows.groupBy { it.trackId }; updateRanges() } }
        serviceScope.launch { tools.gains().collect { rows ->
            crossfade?.setAttenuation(rows.associate { it.trackId.toString() to attenuation(it.decibels) })
        } }
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
        (application as NexusApp).soundProfiles.select(null)
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
