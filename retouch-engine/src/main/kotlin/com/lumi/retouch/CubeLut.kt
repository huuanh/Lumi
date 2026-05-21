package com.lumi.retouch

import android.content.res.AssetManager
import android.graphics.Color
import kotlin.math.roundToInt

data class CubeLut(
    val size: Int,
    private val colors: IntArray
) {
    fun apply(color: Int): Int {
        val r = Color.red(color) / 255f * (size - 1)
        val g = Color.green(color) / 255f * (size - 1)
        val b = Color.blue(color) / 255f * (size - 1)
        val r0 = r.toInt().coerceIn(0, size - 1)
        val g0 = g.toInt().coerceIn(0, size - 1)
        val b0 = b.toInt().coerceIn(0, size - 1)
        val r1 = (r0 + 1).coerceAtMost(size - 1)
        val g1 = (g0 + 1).coerceAtMost(size - 1)
        val b1 = (b0 + 1).coerceAtMost(size - 1)
        val tr = r - r0
        val tg = g - g0
        val tb = b - b0

        val c00 = mix(sample(r0, g0, b0), sample(r1, g0, b0), tr)
        val c10 = mix(sample(r0, g1, b0), sample(r1, g1, b0), tr)
        val c01 = mix(sample(r0, g0, b1), sample(r1, g0, b1), tr)
        val c11 = mix(sample(r0, g1, b1), sample(r1, g1, b1), tr)
        val c0 = mix(c00, c10, tg)
        val c1 = mix(c01, c11, tg)
        val out = mix(c0, c1, tb)
        return Color.argb(Color.alpha(color), Color.red(out), Color.green(out), Color.blue(out))
    }

    private fun sample(r: Int, g: Int, b: Int): Int = colors[b * size * size + g * size + r]

    private fun mix(a: Int, b: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).roundToInt().coerceIn(0, 255),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).roundToInt().coerceIn(0, 255),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).roundToInt().coerceIn(0, 255)
        )
    }
}

object CubeLutLoader {
    private val lutAssets = mapOf(
        FilterLook.Clean to "luts/clean.cube",
        FilterLook.Film to "luts/film.cube",
        FilterLook.Cream to "luts/cream.cube",
        FilterLook.Korean to "luts/korean.cube",
        FilterLook.CoolWhite to "luts/cool_white.cube",
        FilterLook.WarmPortrait to "luts/warm_portrait.cube",
        FilterLook.Neo to "luts/neo.cube"
    )

    fun installFromAssets(assets: AssetManager) {
        lutAssets.forEach { (look, path) ->
            runCatching {
                assets.open(path).bufferedReader().use { parse(it.readText()) }
            }.onSuccess { cube ->
                PortraitLut.install(look, cube)
            }
        }
    }

    fun parse(text: String): CubeLut {
        var size = 0
        val values = mutableListOf<Int>()
        text.lineSequence().forEach { raw ->
            val line = raw.substringBefore("#").trim()
            if (line.isBlank()) return@forEach
            val parts = line.split(Regex("\\s+"))
            when (parts.first()) {
                "TITLE", "DOMAIN_MIN", "DOMAIN_MAX" -> Unit
                "LUT_3D_SIZE" -> size = parts.getOrNull(1)?.toIntOrNull() ?: 0
                else -> if (parts.size >= 3) {
                    val r = (parts[0].toFloat().coerceIn(0f, 1f) * 255f).roundToInt()
                    val g = (parts[1].toFloat().coerceIn(0f, 1f) * 255f).roundToInt()
                    val b = (parts[2].toFloat().coerceIn(0f, 1f) * 255f).roundToInt()
                    values.add(Color.rgb(r, g, b))
                }
            }
        }
        require(size >= 2) { "Invalid LUT size" }
        require(values.size == size * size * size) { "Expected ${size * size * size} LUT values, found ${values.size}" }
        return CubeLut(size, values.toIntArray())
    }
}
