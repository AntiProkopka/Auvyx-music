package com.nexus.music

import android.app.Application
import android.util.Log
import com.nexus.music.data.db.NexusDatabase
import com.nexus.music.data.local.LibraryRepository
import com.nexus.music.data.settings.SettingsStore
import com.nexus.music.playback.PlayerController
import com.nexus.music.playback.SixBandEqualizer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class NexusApp : Application() {
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, e ->
            Log.e("AUVYX", "Unhandled coroutine error", e)
        }
    )

    val equalizer = SixBandEqualizer()
    val database: NexusDatabase by lazy { NexusDatabase.create(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val library: LibraryRepository by lazy { LibraryRepository(this, database, settings) }
    val offline by lazy { com.nexus.music.data.local.OfflineAudio(this, database.trackDao(), appScope) }
    val playerController: PlayerController by lazy {
        PlayerController(this, database, settings, appScope)
    }
}
