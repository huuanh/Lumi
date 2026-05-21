package com.lumi.retouch

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoldenImageRegressionTest {
    @Test
    fun portraitLutGoldenColorsStayStable() {
        val source = Color.rgb(120, 90, 70)

        assertEquals(Color.rgb(129, 95, 75), PortraitLut.apply(source, FilterLook.Clean))
        assertEquals(Color.rgb(136, 100, 80), PortraitLut.apply(source, FilterLook.Film))
        assertEquals(Color.rgb(135, 107, 97), PortraitLut.apply(source, FilterLook.CoolWhite))
    }

    @Test
    fun benchmarkChecksumIsStableForFixedBitmap() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                bitmap.setPixel(x, y, Color.rgb(32 + x * 20, 48 + y * 16, 72 + (x + y) * 7))
            }
        }

        assertEquals(5620745446450007005L, RetouchBenchmark.checksum(bitmap))
    }

    @Test
    fun cpuPreviewPipelineProducesNonBlankGoldenBitmap() {
        val source = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                source.setPixel(x, y, Color.rgb(70 + x * 3, 60 + y * 4, 90 + ((x + y) % 10)))
            }
        }

        val result = RetouchEngine.process(
            source = source,
            recipe = EditRecipe(
                exposure = 0.05f,
                contrast = 0.08f,
                saturation = 0.04f,
                warmth = 0.03f,
                filterLook = FilterLook.WarmPortrait
            )
        )

        assertEquals(24, result.width)
        assertEquals(24, result.height)
        assertTrue(RetouchBenchmark.checksum(result) != RetouchBenchmark.checksum(source))
    }
}
