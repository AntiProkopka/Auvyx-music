package com.nexus.music.playback

import android.media.AudioDeviceInfo
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/** Keeps the selected output route connected to the app's Media3 player. */
@UnstableApi
object PlaybackAudioRouting {
    @Volatile private var player: ExoPlayer? = null
    @Volatile private var preferredDevice: AudioDeviceInfo? = null

    val preferredDeviceId: Int? get() = preferredDevice?.id

    fun attach(exoPlayer: ExoPlayer) {
        player = exoPlayer
        exoPlayer.setPreferredAudioDevice(preferredDevice)
    }

    fun detach(exoPlayer: ExoPlayer) {
        if (player === exoPlayer) player = null
    }

    private var overlay = java.lang.ref.WeakReference<ExoPlayer>(null)
    fun routeOverlay(player: ExoPlayer) {
        overlay = java.lang.ref.WeakReference(player)
        player.setPreferredAudioDevice(preferredDevice)
    }

    fun clearOverlay() { overlay.clear() }

    fun select(device: AudioDeviceInfo?) {
        preferredDevice = device
        player?.setPreferredAudioDevice(device)
        overlay.get()?.setPreferredAudioDevice(device)
    }
}
