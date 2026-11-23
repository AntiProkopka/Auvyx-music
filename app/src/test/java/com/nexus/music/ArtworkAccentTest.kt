package com.nexus.music

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import com.nexus.music.ui.components.representativeArtworkColor
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkAccentTest {
    @Test fun derivesAccentFromArtworkAndIgnoresTransparentPixels() {
        val artwork = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        try {
            assertNull(representativeArtworkColor(artwork))
            artwork.eraseColor(Color.RED)
            val red = requireNotNull(representativeArtworkColor(artwork))
            assertTrue(red.red > red.blue)
            artwork.eraseColor(Color.BLUE)
            val blue = requireNotNull(representativeArtworkColor(artwork))
            assertTrue(blue.blue > blue.red)
            artwork.eraseColor(Color.GRAY)
            assertNotNull(representativeArtworkColor(artwork))
        } finally {
            artwork.recycle()
        }
    }
}
