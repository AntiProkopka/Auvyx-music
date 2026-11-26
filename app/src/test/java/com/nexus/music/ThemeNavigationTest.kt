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

    @Test fun graphicsPresetPersistsAndKeepsLowAvailable() = kotlinx.coroutines.runBlocking {
        val settings = (compose.activity.application as NexusApp).settings
        org.junit.Assert.assertEquals(com.nexus.music.data.settings.GlassQuality.Ultra, settings.glassQuality.first())
        settings.setGlassQuality(com.nexus.music.data.settings.GlassQuality.Medium)
        settings.setStyle("material")
        org.junit.Assert.assertEquals("material", settings.visualStyle.first())
        org.junit.Assert.assertEquals(com.nexus.music.data.settings.GlassQuality.Medium, settings.glassQuality.first())
        settings.setGlassQuality(com.nexus.music.data.settings.GlassQuality.High)
        org.junit.Assert.assertEquals("glass", settings.visualStyle.first())
        org.junit.Assert.assertEquals(com.nexus.music.data.settings.GlassQuality.High, settings.glassQuality.first())
    }

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
        compose.onNodeWithText("auvyx Glass UI").performScrollTo().performClick()
        compose.onNodeWithText("Ultra").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("High").performScrollTo().performClick()
        compose.waitUntil(5000) {
            kotlinx.coroutines.runBlocking { settings.glassQuality.first().name } == "High"
        }
        val accents = compose.onNodeWithTag("appearance-accents")
        for ((index, accent) in com.nexus.music.data.settings.GlassAccent.entries.withIndex()) {
            accents.performScrollToIndex(index)
            compose.onNodeWithContentDescription(accent.title).performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.OnClick)
            compose.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper(20, java.util.concurrent.TimeUnit.MILLISECONDS)
                kotlinx.coroutines.runBlocking { settings.accent.first() } == accent
            }
            compose.waitForIdle()
            compose.onNodeWithContentDescription(accent.title).assertIsSelected()
        }
        compose.onNodeWithText("Material 3 Expressive").performScrollTo().performClick()
        compose.onNodeWithContentDescription("По умолчанию").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Неон").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Стекло").performScrollTo().assertIsDisplayed()
        listOf("Системное оформление", "Стеклянное оформление", "Студия").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
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

    @Test
    @Config(qualifiers = "w320dp-h480dp")
    fun soundPanelRemainsScrollableAndDismissibleOnSmallScreen() {
        val app = compose.activity.application as NexusApp
        val track = seedTracks(app).first()
        compose.runOnUiThread {
            compose.activity.setContent {
                val vm: AppViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                val density = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.6f)
                ) {
                    NexusTheme("dark", "glass") { com.nexus.music.ui.screens.PlayerScreen(vm) {} }
                }
            }
            app.playerController.playTrack(track)
        }
        compose.waitUntil(15000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(20, java.util.concurrent.TimeUnit.MILLISECONDS)
            app.playerController.state.value.mediaId == track.id.toString() &&
                app.playerController.state.value.canSeek
        }
        compose.runOnUiThread { app.playerController.pause() }
        repeat(2) { opening ->
            compose.onNodeWithContentDescription("Эквалайзер и Speed Lab").performScrollTo()
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            compose.onNodeWithText("Эквалайзер").assertIsDisplayed()
            compose.onNodeWithText("Vocal Boost").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Custom").performScrollTo().assertIsDisplayed()
            for (frequency in listOf(60, 150, 400, 1000, 2400, 15000)) {
                compose.onNodeWithContentDescription("$frequency Гц").performScrollTo().assertIsDisplayed()
            }
            if (opening == 0) {
                compose.onNodeWithContentDescription("60 Гц").performScrollTo()
                    .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(7f) }
                compose.waitUntil(5000) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper(20, java.util.concurrent.TimeUnit.MILLISECONDS)
                    kotlinx.coroutines.runBlocking { app.settings.equalizerGains.first().first() } == 7f
                }
            } else compose.onNodeWithText("Custom").performScrollTo().assertIsSelected()
            compose.onNodeWithText("Speed Up").performScrollTo().assertIsDisplayed()
            compose.runOnUiThread { org.robolectric.shadows.ShadowDialog.getLatestDialog().onBackPressed() }
            compose.onNodeWithText("Эквалайзер").assertDoesNotExist()
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp")
    fun plainLyricsPanelKeepsLastLineReachable() {
        val app = compose.activity.application as NexusApp
        kotlinx.coroutines.runBlocking {
            app.settings.saveLyrics("100", (1..44).joinToString("\n") { "Verse $it" })
        }
        val visible = androidx.compose.runtime.mutableStateOf(true)
        compose.runOnUiThread {
            compose.activity.setContent {
                val vm: AppViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                val density = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.6f)
                ) {
                    NexusTheme("dark", "glass") {
                        if (visible.value) com.nexus.music.ui.screens.TrackLyricsSheet(
                            vm, "100", "A long track name that wraps on a small display",
                            "Test artist", 12000L, 0L, null
                        ) { visible.value = false }
                    }
                }
            }
        }
        compose.waitUntil(5000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(20, java.util.concurrent.TimeUnit.MILLISECONDS)
            compose.onAllNodesWithText("Verse 1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Импортировать LRC или TXT").assertIsDisplayed()
        compose.onNodeWithTag("lyrics-scroll").performScrollToIndex(44)
        compose.onNodeWithText("Verse 44").assertIsDisplayed()
        compose.runOnUiThread { org.robolectric.shadows.ShadowDialog.getLatestDialog().onBackPressed() }
        compose.onNodeWithTag("lyrics-scroll").assertDoesNotExist()
    }

    @Test fun timedLyricHighlightFollowsPositionInBothDirections() {
        val position = mutableLongStateOf(2_000L)
        compose.runOnUiThread {
            compose.activity.setContent {
                NexusTheme(themeMode = "light", style = "material") {
                    LyricsContent(
                        "[00:01.00]First verse\n[00:04.00]Second verse\n" +
                            (5..50).joinToString("\n") { "[00:%02d.00]Verse %d".format(java.util.Locale.ROOT, it, it) },
                        position.longValue
                    )
                }
            }
        }
        compose.onNodeWithText("First verse").assertIsSelected()
        compose.runOnIdle { position.longValue = 4_500L }
        compose.onNodeWithText("Second verse").assertIsSelected()
        compose.runOnIdle { position.longValue = 2_000L }
        compose.onNodeWithText("First verse").assertIsSelected()
        val list = compose.onNodeWithTag("lyrics-scroll")
        list.performTouchInput { swipeUp() }
        compose.onNodeWithText("К текущей строке").assertIsDisplayed()
        list.performScrollToIndex(47)
        compose.runOnIdle { position.longValue = 4_500L }
        compose.onNodeWithText("Verse 50").assertIsDisplayed()
        compose.onNodeWithText("К текущей строке").performClick()
        compose.onNodeWithText("Second verse").assertIsSelected().assertIsDisplayed()
    }

}
