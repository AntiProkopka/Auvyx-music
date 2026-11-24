package com.nexus.music

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.test.*
import com.nexus.music.ui.screens.LyricsContent
import com.nexus.music.ui.theme.NexusTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ThemeNavigationTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    private val service = object : org.junit.rules.ExternalResource() {
        private lateinit var controller: org.robolectric.android.controller.ServiceController<com.nexus.music.playback.PlaybackService>
        override fun before() {
            val app = org.robolectric.RuntimeEnvironment.getApplication()
            org.robolectric.Shadows.shadowOf(app.packageManager).setPackagesForUid(android.os.Process.myUid(),app.packageName)
            controller = org.robolectric.Robolectric.buildService(com.nexus.music.playback.PlaybackService::class.java).create()
            val name = android.content.ComponentName(app, com.nexus.music.playback.PlaybackService::class.java)
            val intent = android.content.Intent(androidx.media3.session.MediaSessionService.SERVICE_INTERFACE).setComponent(name)
            org.robolectric.Shadows.shadowOf(app).setComponentNameAndServiceForBindService(name,controller.get().onBind(intent))
        }
        override fun after() {
            (org.robolectric.RuntimeEnvironment.getApplication() as NexusApp).appScope.cancel()
            controller.destroy()
        }
    }
    @get:Rule val rules: org.junit.rules.TestRule = org.junit.rules.RuleChain.outerRule(service).around(compose)

    @Test fun expressiveDockKeepsLibraryAndColorModesReachable() {
        val settings = (compose.activity.application as NexusApp).settings
        if (kotlinx.coroutines.runBlocking { settings.nickname.first() } == null) {
            compose.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper(20, java.util.concurrent.TimeUnit.MILLISECONDS)
                compose.onAllNodesWithText("Пропустить").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Пропустить").performClick()
        }
        compose.onNodeWithTag("nav-settings").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("По умолчанию").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Неон").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Стекло").performScrollTo().assertIsDisplayed()
        listOf("HyperOS", "Liquid Glass", "Studio · LANE").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.onNodeWithText("Цветовой режим").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Как в системе").assertExists()
        compose.onNodeWithText("Светлая").assertExists()
        compose.onNodeWithText("Тёмная").assertExists()
        compose.onNodeWithTag("bottom-dock").assertIsDisplayed()
        compose.onNodeWithTag("nav-settings").assertIsSelected()
        compose.onNodeWithTag("nav-search").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.onNodeWithText("SoundCloud").assertExists()
        val results = compose.onNodeWithTag("search-results").fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithTag("bottom-dock").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("Search must draw behind the dock", results.bottom >= dock.bottom && results.top < dock.top)
        compose.onNodeWithText("Spotify").assertDoesNotExist()
        compose.onNodeWithText("YouTube Music").assertDoesNotExist()
        compose.onNodeWithTag("nav-library").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.onNodeWithText("Офлайн").assertExists()
        compose.onNodeWithText("Каталоги").assertDoesNotExist()
        compose.onNodeWithTag("nav-library").assertIsSelected()
        compose.onNodeWithTag("nav-home").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.onNodeWithTag("nav-home").assertIsSelected()
    }

    @Test fun sessionLoadsLocalAudioAndProcessesQueueCommands() {
        val app = compose.activity.application as NexusApp
        val tracks = seedTracks(app)
        val controller = app.playerController
        compose.runOnUiThread { controller.playTracks(tracks.take(2),0) }
        fun awaitState(check: () -> Boolean) {
            try {
                compose.waitUntil(15000) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper(20, java.util.concurrent.TimeUnit.MILLISECONDS)
                    check()
                }
            } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
                throw AssertionError("MediaSession state: ${controller.state.value}, position ${controller.position.value}", e)
            }
        }
        awaitState { controller.state.value.durationMs == 12000L && controller.state.value.isPlaying }
        compose.runOnUiThread { controller.pause(); controller.seekTo(1500L); controller.next() }
        awaitState { controller.state.value.title == "Audio 2" && !controller.state.value.isPlaying && controller.state.value.canSeek }
        compose.runOnUiThread { controller.seekTo(2000L) }
        awaitState { controller.position.value == 2000L }
        compose.runOnUiThread { controller.setSpeedAndPitch(1.25f,1.25f) }
        awaitState { controller.state.value.pitch == 1.25f && controller.position.value == 2000L }
        compose.runOnUiThread { controller.moveQueueItem(1,0); controller.toggleShuffle(); controller.cycleRepeat() }
        awaitState { controller.state.value.queue.firstOrNull()?.title == "Audio 2" && controller.state.value.shuffle }

        compose.runOnUiThread { controller.playTrack(tracks[0]) }
        awaitState { controller.state.value.mediaId == tracks[0].id.toString() && controller.state.value.showPause }
        val queued = controller.state.value.queue
        org.junit.Assert.assertEquals(listOf("Audio 2", "Audio 1"), queued.map { it.title })
        compose.runOnUiThread { controller.togglePlayPause() }
        awaitState { !controller.state.value.showPause }
        compose.runOnUiThread { controller.playTrack(tracks[0]) }
        awaitState { controller.state.value.showPause }
        org.junit.Assert.assertEquals(queued, controller.state.value.queue)
        compose.runOnUiThread { controller.playTrack(tracks[2]) }
        awaitState {
            controller.state.value.mediaId == tracks[2].id.toString() && controller.state.value.queue.size == 3
        }
        org.junit.Assert.assertEquals(listOf("Audio 2", "Audio 1", "Audio 3"), controller.state.value.queue.map { it.title })
        compose.runOnUiThread { controller.playNext(tracks[1]); controller.enqueue(tracks[0]) }
        awaitState { controller.state.value.queue.size == 5 }
        org.junit.Assert.assertEquals(listOf("Audio 2", "Audio 1"), controller.state.value.queue.takeLast(2).map { it.title })
    }

    private fun seedTracks(app: NexusApp) = kotlinx.coroutines.runBlocking {
        (1..3).map { n ->
            val uri = "content://audio-test/$n.wav"
            val copy = com.nexus.music.data.local.OfflineAudio.file(app,uri)
            copy.parentFile!!.mkdirs()
            val dataSize = 48000 * 2 * 12
            val wav = java.nio.ByteBuffer.allocate(44 + dataSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            wav.put("RIFF".toByteArray()).putInt(dataSize + 36).put("WAVEfmt ".toByteArray())
                .putInt(16).putShort(1).putShort(1).putInt(48000).putInt(96000)
                .putShort(2).putShort(16).put("data".toByteArray()).putInt(dataSize)
            copy.writeBytes(wav.array())
            val t = com.nexus.music.data.db.TrackEntity(uri=uri,title="Audio $n",artist="Test",album="",durationMs=12000L,addedAt=n.toLong())
            t.copy(id=app.database.trackDao().insertOrIgnore(t))
        }
    }

    @Test fun timedLyricHighlightFollowsPositionInBothDirections() {
        val position = mutableLongStateOf(2_000L)
        compose.runOnUiThread {
            compose.activity.setContent {
                NexusTheme(themeMode = "light", style = "material") {
                    LyricsContent("[00:01.00]First verse\n[00:04.00]Second verse", position.longValue)
                }
            }
        }
        compose.onNodeWithText("First verse").assertIsSelected()
        compose.runOnIdle { position.longValue = 4_500L }
        compose.onNodeWithText("Second verse").assertIsSelected()
        compose.runOnIdle { position.longValue = 2_000L }
        compose.onNodeWithText("First verse").assertIsSelected()
    }

}
