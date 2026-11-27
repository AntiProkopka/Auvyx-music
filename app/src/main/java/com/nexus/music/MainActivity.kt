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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nexus.music.ui.LaunchFilm
import java.util.concurrent.atomic.AtomicBoolean
import com.nexus.music.ui.NexusRoot
import com.nexus.music.ui.theme.NexusTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        val coldLaunch = firstActivity.compareAndSet(true, false) && savedInstanceState == null

        vm.player.connect()

        setContent {
            val theme by vm.themeMode.collectAsStateWithLifecycle()
            val style by vm.visualStyle.collectAsStateWithLifecycle()
            val accent by vm.accent.collectAsStateWithLifecycle()
            NexusTheme(themeMode = theme, style = style, accent = accent) {
                var showLaunch by remember { mutableStateOf(coldLaunch) }
                Box(Modifier.fillMaxSize()) {
                    if (!showLaunch) NexusRoot(vm)
                    AnimatedVisibility(visible = showLaunch, exit = fadeOut(tween(220))) {
                        LaunchFilm { showLaunch = false }
                    }
                }
                LaunchedEffect(showLaunch) {
                    if (!showLaunch && Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
    }

    private companion object {
        val firstActivity = AtomicBoolean(true)
    }

    override fun onStart() {
        super.onStart()
        vm.player.connect()
    }
}
