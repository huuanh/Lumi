package com.lumi.retouch

import android.graphics.Color
import kotlin.math.roundToInt

object PortraitLut {
    private const val SIZE = 17
    private val generatedCubes: Map<FilterLook, IntArray> = FilterLook.entries.associateWith { look ->
        IntArray(SIZE * SIZE * SIZE) { index ->
            val r = index % SIZE
            val g = (index / SIZE) % SIZE
            val b = index / (SIZE * SIZE)
            val color = Color.rgb(
                (r * 255f / (SIZE - 1)).roundToInt(),
                (g * 255f / (SIZE - 1)).roundToInt(),
                (b * 255f / (SIZE - 1)).roundToInt()
            )
            grade(color, look)
        }
    }
    private val assetCubes: MutableMap<FilterLook, CubeLut> = mutableMapOf()

    fun install(look: FilterLook, cube: CubeLut) {
        assetCubes[look] = cube
    }

    fun apply(color: Int, look: FilterLook): Int {
        assetCubes[look]?.let { return it.apply(color) }
        val cube = generatedCubes.getValue(look)
        val r = Color.red(color) / 255f * (SIZE - 1)
        val g = Color.green(color) / 255f * (SIZE - 1)
        val b = Color.blue(color) / 255f * (SIZE - 1)
        val r0 = r.toInt().coerceIn(0, SIZE - 1)
        val g0 = g.toInt().coerceIn(0, SIZE - 1)
        val b0 = b.toInt().coerceIn(0, SIZE - 1)
        val r1 = (r0 + 1).coerceAtMost(SIZE - 1)
        val g1 = (g0 + 1).coerceAtMost(SIZE - 1)
        val b1 = (b0 + 1).coerceAtMost(SIZE - 1)
        val tr = r - r0
        val tg = g - g0
        val tb = b - b0

        fun sample(ri: Int, gi: Int, bi: Int): Int = cube[bi * SIZE * SIZE + gi * SIZE + ri]

        val c00 = mix(sample(r0, g0, b0), sample(r1, g0, b0), tr)
        val c10 = mix(sample(r0, g1, b0), sample(r1, g1, b0), tr)
        val c01 = mix(sample(r0, g0, b1), sample(r1, g0, b1), tr)
        val c11 = mix(sample(r0, g1, b1), sample(r1, g1, b1), tr)
        val c0 = mix(c00, c10, tg)
        val c1 = mix(c01, c11, tg)
        val out = mix(c0, c1, tb)
        return Color.argb(Color.alpha(color), Color.red(out), Color.green(out), Color.blue(out))
    }

    private fun grade(color: Int, look: FilterLook): Int {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        val l = .299f * r + .587f * g + .114f * b
        val graded = when (look) {
            FilterLook.Clean -> Triple(
                tone(r, .012f, .024f, .01f),
                tone(g, .008f, .018f, .008f),
                tone(b, .012f, .012f, .018f)
            )
            FilterLook.Film -> {
                val fade = (1f - l) * .065f
                Triple(
                    tone(r + .032f + fade, .046f, -.014f, -.036f),
                    tone(g + .012f + fade * .5f, .034f, -.01f, -.02f),
                    tone(b - .024f + fade, .056f, -.02f, -.012f)
                )
            }
            FilterLook.Cream -> Triple(
                tone(r + .04f + l * .032f, .018f, .035f, .02f),
                tone(g + .02f, .012f, .026f, .018f),
                tone(b - .032f, .02f, -.008f, -.026f)
            )
            FilterLook.Korean -> Triple(
                tone(r + .026f, .022f, .042f, .022f),
                tone(g + .018f, .018f, .036f, .018f),
                tone(b + .006f, .018f, .02f, .028f)
            )
            FilterLook.CoolWhite -> Triple(
                tone(r - .018f, .025f, .048f, .035f),
                tone(g + .006f, .024f, .045f, .032f),
                tone(b + .038f, .032f, .052f, .04f)
            )
            FilterLook.WarmPortrait -> Triple(
                tone(r + .05f, .02f, .034f, .026f),
                tone(g + .02f, .012f, .024f, .018f),
                tone(b - .018f, .014f, .004f, -.01f)
            )
            FilterLook.Neo -> Triple(
                tone(r - .024f, -.018f, .012f, .02f),
                tone(g + .016f, .004f, .02f, .024f),
                tone(b + .032f + (1f - l) * .032f, .028f, .035f, .018f)
            )
        }
        return Color.rgb(
            (graded.first.coerceIn(0f, 1f) * 255f).roundToInt(),
            (graded.second.coerceIn(0f, 1f) * 255f).roundToInt(),
            (graded.third.coerceIn(0f, 1f) * 255f).roundToInt()
        )
    }

    private fun tone(value: Float, shadows: Float, mid: Float, highlights: Float): Float {
        val x = value.coerceIn(0f, 1f)
        val shadowWeight = (1f - x).coerceIn(0f, 1f)
        val highlightWeight = x.coerceIn(0f, 1f)
        val midWeight = (1f - kotlin.math.abs(x - .5f) * 2f).coerceIn(0f, 1f)
        return x + shadows * shadowWeight + mid * midWeight + highlights * highlightWeight
    }

    private fun mix(a: Int, b: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).roundToInt().coerceIn(0, 255),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).roundToInt().coerceIn(0, 255),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).roundToInt().coerceIn(0, 255)
        )
    }
}
