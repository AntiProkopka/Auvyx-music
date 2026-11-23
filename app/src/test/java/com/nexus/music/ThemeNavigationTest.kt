package com.nexus.music

import androidx.compose.ui.test.*
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

    @Test fun stylesKeepLibraryReachable() {
        val settings = (compose.activity.application as NexusApp).settings
        if (kotlinx.coroutines.runBlocking { settings.nickname.first() } == null) {
            compose.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                compose.onAllNodesWithText("Пропустить").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Пропустить").performClick()
        }
        compose.onNodeWithTag("nav-settings").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("По умолчанию").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Неон").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Стекло").performScrollTo().assertIsDisplayed()
        for ((style,key) in listOf("HyperOS" to "hyper", "Liquid Glass" to "glass", "Studio · LANE" to "lane", "Material 3" to "material")) {
            compose.onNode(hasText(style) and hasClickAction()).performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            compose.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                kotlinx.coroutines.runBlocking { settings.visualStyle.first() } == key
            }
            compose.onNodeWithTag("nav-library").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            compose.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                compose.onAllNodesWithText("Офлайн").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("nav-settings").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        }
    }

    @Test fun sessionLoadsLocalAudioAndProcessesQueueCommands() {
        val app = compose.activity.application as NexusApp
        val tracks = kotlinx.coroutines.runBlocking {
            (1..2).map { n ->
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
        val controller = app.playerController
        compose.runOnUiThread { controller.playTracks(tracks,0) }
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
    }
}
