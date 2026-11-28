package com.nexus.music

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import com.nexus.music.ui.screens.LyricsContent
import com.nexus.music.ui.theme.NexusTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        settings.setGlassQuality(com.nexus.music.data.settings.GlassQuality.Low)
        org.junit.Assert.assertEquals("material", settings.visualStyle.first())
        settings.setStyle("classic")
        org.junit.Assert.assertEquals(com.nexus.music.data.settings.GlassQuality.Low, settings.glassQuality.first())
        settings.setGlassQuality(com.nexus.music.data.settings.GlassQuality.High)
        org.junit.Assert.assertEquals("classic", settings.visualStyle.first())
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
        compose.onNodeWithText("auvyx Classic").performScrollTo().performClick()
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
                compose.onAllNodesWithContentDescription(accent.title).fetchSemanticsNodes().singleOrNull()
                    ?.config?.getOrNull(SemanticsProperties.Selected) == true
            }
            org.junit.Assert.assertEquals(accent, kotlinx.coroutines.runBlocking { settings.accent.first() })
        }
        compose.onNodeWithText("Material 3 Expressive").performScrollTo().performClick()
        compose.waitUntil(5000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(20, java.util.concurrent.TimeUnit.MILLISECONDS)
            kotlinx.coroutines.runBlocking { settings.visualStyle.first() } == "material"
        }
        compose.waitForIdle()
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
        compose.runOnUiThread { controller.startFiniteRepeat(3) }
        awaitState { controller.finiteRepeat.value?.iteration == 1 &&
            controller.state.value.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE }
        compose.runOnUiThread { controller.next() }
        awaitState { controller.finiteRepeat.value == null &&
            controller.state.value.repeatMode != androidx.media3.common.Player.REPEAT_MODE_ONE }

        val moment = com.nexus.music.data.db.TrackMoment("session-moment", tracks[1].id, 2500L, 6500L, "Test")
        compose.runOnUiThread { controller.playMoment(tracks[1], moment, true) }
        awaitState { controller.state.value.mediaId == tracks[1].id.toString() &&
            controller.position.value in 2500L..6499L && app.activeMoment.value == moment }
        compose.runOnUiThread { controller.next() }
        awaitState { app.activeMoment.value == null }

        kotlinx.coroutines.runBlocking {
            app.database.soundProfiles().put(com.nexus.music.data.db.TrackSoundProfile(
                tracks[1].id, true, false, null, null, .8f, null))
        }
        compose.runOnUiThread { controller.playTrack(tracks[1]) }
        awaitState { controller.state.value.speed == .8f && controller.state.value.pitch == 1.25f }
        compose.runOnUiThread { controller.setSpeed(1.1f) }
        awaitState { controller.state.value.speed == 1.1f }
        compose.runOnUiThread { controller.playTrack(tracks[0]) }
        awaitState { controller.state.value.speed == 1.25f && app.soundProfiles.effective.value.eqEnabled }
    }

    @Test fun finiteRepeatFinishesThreePassesThroughMediaSession() {
        val app = compose.activity.application as NexusApp
        val tracks = seedTracks(app, durationMs = 1600)
        val player = app.playerController
        val passes = mutableListOf<Int>()
        var finished = false
        val observation = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        observation.launch {
            player.finiteRepeat.collect { repeat ->
                if (repeat != null) passes += repeat.remaining
                else if (passes.isNotEmpty()) finished = true
            }
        }
        try {
            fun await(check: () -> Boolean) {
                try {
                    compose.waitUntil(15_000) {
                        org.robolectric.shadows.ShadowLooper.idleMainLooper(30, java.util.concurrent.TimeUnit.MILLISECONDS)
                        check()
                    }
                } catch (e: ComposeTimeoutException) {
                    throw AssertionError("Passes $passes, finished $finished, position ${player.position.value}, ${player.state.value}", e)
                }
            }
            compose.runOnUiThread { player.playTracks(tracks.take(2), 0); player.pause() }
            await { player.state.value.canSeek && !player.state.value.showPause }
            compose.runOnUiThread { player.startFiniteRepeat(3); player.seekTo(450L) }
            await { player.position.value == 450L }
            org.junit.Assert.assertEquals(listOf(3), passes)
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            compose.runOnUiThread { player.playTrack(tracks[0]) }
            await { finished && player.state.value.mediaId == tracks[1].id.toString() }
            org.junit.Assert.assertEquals(listOf(3, 2, 1), passes)
            org.junit.Assert.assertEquals(androidx.media3.common.Player.REPEAT_MODE_OFF, player.state.value.repeatMode)
        } finally {
            observation.cancel()
        }
    }

    @Test fun selectionPreviewStopsAtEndWithoutSkippingOrAdvancingQueue() {
        val app = compose.activity.application as NexusApp
        val tracks = seedTracks(app)
        val player = app.playerController
        fun await(check: () -> Boolean) = compose.waitUntil(15_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(20, java.util.concurrent.TimeUnit.MILLISECONDS)
            check()
        }
        kotlinx.coroutines.runBlocking {
            app.database.trackTools().putRange(com.nexus.music.data.db.SkipRange(tracks[0].id, 1000L, 9000L, "", true))
        }
        compose.runOnUiThread { player.playTracks(tracks.take(2), 0) }
        await { player.state.value.canSeek }
        compose.runOnUiThread { player.previewRange(tracks[0], "preview", 2_000L, 3_000L) }
        await { player.rangePreview.value == null && !player.state.value.isPlaying }
        org.junit.Assert.assertEquals(tracks[0].id.toString(), player.state.value.mediaId)
        org.junit.Assert.assertTrue("Preview stopped at ${player.position.value}", player.position.value in 2_900L..4_200L)
        org.junit.Assert.assertEquals(2, player.state.value.queue.size)
    }

    @Test fun smartPlaylistUpdatesAnOpenSubscriptionWhenFavoriteChanges() {
        val app = compose.activity.application as NexusApp
        val track = seedTracks(app).first()
        val id = "17eeec74-032c-47ea-b593-3b6c605ecb35"
        kotlinx.coroutines.runBlocking {
            app.database.smartCollections().save(
                com.nexus.music.data.db.SmartCollection(id, "Избранное автоматически", true, 10),
                listOf(com.nexus.music.data.db.CollectionCondition(id, 0, "FAVORITE", ""))
            )
        }
        val results = kotlinx.coroutines.flow.MutableStateFlow<List<Long>?>(null)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        compose.runOnUiThread {
            val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[AppViewModel::class.java]
            scope.launch { vm.smartPlaylistTracks(id).collect { tracks -> results.value = tracks.map { it.id } } }
        }
        fun await(expected: List<Long>) = compose.waitUntil(10_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(20, java.util.concurrent.TimeUnit.MILLISECONDS)
            results.value == expected
        }
        try {
            await(emptyList())
            kotlinx.coroutines.runBlocking { app.database.trackDao().setFavorite(track.id, true) }
            await(listOf(track.id))
            kotlinx.coroutines.runBlocking { app.database.trackDao().setFavorite(track.id, false) }
            await(emptyList())
        } finally {
            scope.cancel()
        }
    }

    private fun seedTracks(app: NexusApp, durationMs: Long = 12_000L) = kotlinx.coroutines.runBlocking {
        (1..3).map { n ->
            val uri = "content://audio-test/$n.wav"
            val copy = com.nexus.music.data.local.OfflineAudio.file(app,uri)
            copy.parentFile!!.mkdirs()
            val dataSize = (48_000 * 2 * durationMs / 1_000).toInt()
            val wav = java.nio.ByteBuffer.allocate(44 + dataSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            wav.put("RIFF".toByteArray()).putInt(dataSize + 36).put("WAVEfmt ".toByteArray())
                .putInt(16).putShort(1).putShort(1).putInt(48000).putInt(96000)
                .putShort(2).putShort(16).put("data".toByteArray()).putInt(dataSize)
            copy.writeBytes(wav.array())
            val t = com.nexus.music.data.db.TrackEntity(uri=uri,title="Audio $n",artist="Test",album="",durationMs=durationMs,addedAt=n.toLong())
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
                    NexusTheme("dark", "classic") { com.nexus.music.ui.screens.PlayerScreen(vm) {} }
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
                    NexusTheme("dark", "classic") {
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
