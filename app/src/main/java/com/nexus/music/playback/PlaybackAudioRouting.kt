package com.nexus.music.playback

import android.media.AudioDeviceInfo
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/** Keeps the selected output route connected to the app's Media3 player. */
@UnstableApi
object PlaybackAudioRouting {
    @Volatile private var player: ExoPlayer? = null
    @Volatile private var preferredDevice: AudioDeviceInfo? = null

    fun attach(exoPlayer: ExoPlayer) {
        player = exoPlayer
        exoPlayer.setPreferredAudioDevice(preferredDevice)
    }

    fun detach(exoPlayer: ExoPlayer) {
        if (player === exoPlayer) player = null
    }

    fun select(device: AudioDeviceInfo?) {
        preferredDevice = device
        player?.setPreferredAudioDevice(device)
    }
}
