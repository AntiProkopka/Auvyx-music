package com.nexus.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.nexus.music.ui.NexusRoot
import com.nexus.music.ui.theme.NexusTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        vm.player.connect()

        setContent {
            val theme by vm.themeMode.collectAsStateWithLifecycle()
            val style by vm.visualStyle.collectAsStateWithLifecycle()
            val accent by vm.accent.collectAsStateWithLifecycle()
            NexusTheme(themeMode = theme, style = style, accent = accent) {
                NexusRoot(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.player.connect()
    }
}
