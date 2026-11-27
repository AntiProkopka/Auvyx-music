package com.nexus.music.ui

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nexus.music.R
import kotlinx.coroutines.delay

@Composable
internal fun LaunchFilm(onFinished: () -> Unit) {
    val finish = rememberUpdatedState(onFinished)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    BackHandler { finish.value() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) finish.value()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Leave the user in the app even if the platform decoder stalls.
    LaunchedEffect(Unit) {
        delay(5500)
        finish.value()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xff020003)),
        contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, maxHeight * 9f / 16f)
        AndroidView(
            factory = { context -> LaunchFilmView(context) { finish.value() } },
            modifier = Modifier.size(width, width * 16f / 9f),
            onRelease = { it.close() }
        )
    }
}

private class LaunchFilmView(
    context: android.content.Context,
    private val finish: () -> Unit
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        val film = MediaPlayer()
        player = film
        try {
            val resource = Uri.parse("android.resource://${context.packageName}/${R.raw.auvyx_launch_v10}")
            film.setDataSource(context, resource)
            Surface(texture).useSurface { film.setSurface(it) }
            film.setOnPreparedListener { it.start() }
            film.setOnCompletionListener { finish() }
            film.setOnErrorListener { _, _, _ -> finish(); true }
            film.prepareAsync()
        } catch (_: Exception) {
            close()
            finish()
        }
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        close()
        return true
    }

    fun close() {
        player?.release()
        player = null
    }
}

private inline fun Surface.useSurface(block: (Surface) -> Unit) {
    try {
        block(this)
    } finally {
        release()
    }
}
