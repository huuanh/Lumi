package com.lumi.retouch

import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object FaceMaskUtils {
    private val outerLipIndices = listOf(61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 409, 270, 269, 267, 0, 37, 39, 40, 185)
    private val mouthIndices = listOf(61, 291, 13, 14, 0, 17)
    private val leftCheekIndices = listOf(50, 101, 205, 187)
    private val rightCheekIndices = listOf(280, 330, 425, 411)
    private val noseIndices = listOf(1, 4, 5, 6, 168)
    private val leftEyeIndices = listOf(33, 160, 158, 133, 153, 144)
    private val rightEyeIndices = listOf(362, 385, 387, 263, 373, 380)
    private val leftUpperEyeIndices = listOf(33, 160, 158, 133)
    private val rightUpperEyeIndices = listOf(362, 385, 387, 263)

    fun lipMask(x: Float, y: Float, mesh: FaceMeshAnchor, precision: Float = 0.5f): Float {
        val mouth = averagePoint(mesh, mouthIndices) ?: return 0f
        val expansion = (1f - precision.coerceIn(0f, 1f)) * .035f
        val feather = (.012f + (1f - precision.coerceIn(0f, 1f)) * .018f).coerceIn(.01f, .035f)
        val outerLip = outerLipIndices.mapNotNull { mesh.point(it) }
        val polygonMask = if (outerLip.size >= 8 && pointInPolygon(x, y, outerLip)) {
            val edgeDistance = distanceToPolygonEdge(x, y, outerLip)
            (edgeDistance / (mesh.bounds.width() * feather)).coerceIn(.35f, 1f)
        } else {
            0f
        }
        val base = if (polygonMask > 0f) {
            polygonMask
        } else {
            ellipseWeight(x, y, mouth, mesh.bounds.width() * (.15f + expansion), mesh.bounds.height() * (.06f + expansion * .65f))
        }
        val upper = mesh.point(13)
        val lower = mesh.point(14)
        val gate = if (upper != null && lower != null) {
            val top = upper.y - mesh.bounds.height() * (.022f + expansion)
            val bottom = lower.y + mesh.bounds.height() * (.03f + expansion)
            if (y in top..bottom) 1f else .25f
        } else {
            1f
        }
        return (base * gate).coerceIn(0f, 1f)
    }

    fun blushMask(x: Float, y: Float, mesh: FaceMeshAnchor, spread: Float = 0.5f): Float {
        val left = averagePoint(mesh, leftCheekIndices)
        val right = averagePoint(mesh, rightCheekIndices)
        val spreadValue = spread.coerceIn(0f, 1f)
        val radiusX = mesh.bounds.width() * (.1f + spreadValue * .12f)
        val radiusY = mesh.bounds.height() * (.065f + spreadValue * .08f)
        return max(
            left?.let { ellipseWeight(x, y, it, radiusX, radiusY) } ?: 0f,
            right?.let { ellipseWeight(x, y, it, radiusX, radiusY) } ?: 0f
        )
    }

    fun contourMask(x: Float, y: Float, mesh: FaceMeshAnchor): Float {
        val left = AnchorPoint(mesh.bounds.left + mesh.bounds.width() * .31f, mesh.bounds.top + mesh.bounds.height() * .62f)
        val right = AnchorPoint(mesh.bounds.right - mesh.bounds.width() * .31f, mesh.bounds.top + mesh.bounds.height() * .62f)
        return max(
            ellipseWeight(x, y, left, mesh.bounds.width() * .16f, mesh.bounds.height() * .18f),
            ellipseWeight(x, y, right, mesh.bounds.width() * .16f, mesh.bounds.height() * .18f)
        )
    }

    fun noseMask(x: Float, y: Float, mesh: FaceMeshAnchor): Float {
        val nose = averagePoint(mesh, noseIndices) ?: return 0f
        return ellipseWeight(x, y, nose, mesh.bounds.width() * .08f, mesh.bounds.height() * .23f)
    }

    fun tZoneMask(x: Float, y: Float, mesh: FaceMeshAnchor): Float {
        val nose = averagePoint(mesh, noseIndices) ?: AnchorPoint(mesh.bounds.centerX(), mesh.bounds.centerY())
        val forehead = AnchorPoint(mesh.bounds.centerX(), mesh.bounds.top + mesh.bounds.height() * .22f)
        val bridge = AnchorPoint(nose.x, mesh.bounds.top + mesh.bounds.height() * .42f)
        val foreheadMask = ellipseWeight(x, y, forehead, mesh.bounds.width() * .18f, mesh.bounds.height() * .13f)
        val bridgeMask = ellipseWeight(x, y, bridge, mesh.bounds.width() * .08f, mesh.bounds.height() * .24f)
        val noseMask = ellipseWeight(x, y, nose, mesh.bounds.width() * .1f, mesh.bounds.height() * .17f)
        return max(foreheadMask, max(bridgeMask, noseMask)).coerceIn(0f, 1f)
    }

    fun eyeShadowMask(x: Float, y: Float, mesh: FaceMeshAnchor): Float {
        return max(
            singleEyeShadowMask(x, y, mesh, leftEyeIndices),
            singleEyeShadowMask(x, y, mesh, rightEyeIndices)
        )
    }

    fun eyeLineMask(x: Float, y: Float, mesh: FaceMeshAnchor): Float {
        return max(
            singleEyeLineMask(x, y, mesh, leftUpperEyeIndices),
            singleEyeLineMask(x, y, mesh, rightUpperEyeIndices)
        )
    }

    fun eyeAreaMask(x: Float, y: Float, mesh: FaceMeshAnchor): Float {
        val left = averagePoint(mesh, leftEyeIndices)
        val right = averagePoint(mesh, rightEyeIndices)
        val radiusX = mesh.bounds.width() * .11f
        val radiusY = mesh.bounds.height() * .06f
        return max(
            left?.let { ellipseWeight(x, y, it, radiusX, radiusY) } ?: 0f,
            right?.let { ellipseWeight(x, y, it, radiusX, radiusY) } ?: 0f
        )
    }

    fun faceSkinMask(x: Float, y: Float, mesh: FaceMeshAnchor): Float {
        val center = AnchorPoint(mesh.bounds.centerX(), mesh.bounds.top + mesh.bounds.height() * .5f)
        val faceOval = ellipseWeight(x, y, center, mesh.bounds.width() * .48f, mesh.bounds.height() * .62f)
        val featureBlock = max(
            max(lipMask(x, y, mesh, precision = .78f), eyeAreaMask(x, y, mesh)),
            noseMask(x, y, mesh) * .18f
        )
        return (faceOval * (1f - featureBlock.coerceIn(0f, .92f))).coerceIn(0f, 1f)
    }

    fun averagePoint(mesh: FaceMeshAnchor, indices: List<Int>): AnchorPoint? {
        val points = indices.mapNotNull { mesh.point(it) }
        if (points.isEmpty()) return null
        return AnchorPoint(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
    }

    fun makeupAdaptiveStrength(color: Int, preferSkin: Boolean): Float {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val luminance = (.299f * r + .587f * g + .114f * b) / 255f
        val midTone = when {
            luminance < .18f -> .55f
            luminance > .88f -> .62f
            luminance in .35f.. .72f -> 1f
            else -> .82f
        }
        val chroma = (max(r, max(g, b)) - min(r, min(g, b))).toFloat() / 255f
        val textureGuard = (1f - (chroma - .38f).coerceAtLeast(0f) * .7f).coerceIn(.55f, 1f)
        val skinGate = if (preferSkin) (.65f + skinWeight(color) * .35f) else 1f
        return (midTone * textureGuard * skinGate).coerceIn(.35f, 1f)
    }

    fun skinWeight(color: Int): Float {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val maxChannel = max(r, max(g, b))
        val minChannel = min(r, min(g, b))
        val likelySkin = r > 70 && g > 40 && b > 25 && r > g && g >= b && maxChannel - minChannel > 15
        if (!likelySkin) return 0f
        val warmBalance = ((r - b).toFloat() / 120f).coerceIn(0f, 1f)
        val notTooRed = (1f - max(0, r - g - 85) / 80f).coerceIn(0f, 1f)
        val luminance = (.299f * r + .587f * g + .114f * b) / 255f
        val lightWeight = if (luminance < .16f || luminance > .94f) .35f else 1f
        return (warmBalance * notTooRed * lightWeight).coerceIn(0f, 1f)
    }

    fun ellipseWeight(x: Float, y: Float, center: AnchorPoint, radiusX: Float, radiusY: Float): Float {
        if (radiusX <= 0f || radiusY <= 0f) return 0f
        val dx = (x - center.x) / radiusX
        val dy = (y - center.y) / radiusY
        val distance = sqrt(dx * dx + dy * dy).coerceIn(0f, 1f)
        val falloff = 1f - distance
        return falloff * falloff
    }

    private fun singleEyeShadowMask(x: Float, y: Float, mesh: FaceMeshAnchor, indices: List<Int>): Float {
        val center = averagePoint(mesh, indices) ?: return 0f
        val shadowCenter = AnchorPoint(center.x, center.y - mesh.bounds.height() * .036f)
        val base = ellipseWeight(
            x = x,
            y = y,
            center = shadowCenter,
            radiusX = mesh.bounds.width() * .105f,
            radiusY = mesh.bounds.height() * .052f
        )
        val verticalGate = when {
            y < center.y - mesh.bounds.height() * .078f -> 0f
            y > center.y + mesh.bounds.height() * .004f -> 0f
            else -> 1f
        }
        return (base * verticalGate).coerceIn(0f, 1f)
    }

    private fun singleEyeLineMask(x: Float, y: Float, mesh: FaceMeshAnchor, indices: List<Int>): Float {
        val upper = indices.mapNotNull { mesh.point(it) }
        if (upper.size < 2) return 0f
        var best = Float.MAX_VALUE
        for (index in 0 until upper.lastIndex) {
            best = min(best, distanceToSegment(x, y, upper[index], upper[index + 1]))
        }
        val lineWidth = (mesh.bounds.height() * .0065f).coerceAtLeast(1.2f)
        val weight = (1f - best / lineWidth).coerceIn(0f, 1f)
        val center = averagePoint(mesh, indices) ?: return weight
        val bandGate = if (y < center.y - mesh.bounds.height() * .038f || y > center.y + mesh.bounds.height() * .014f) 0f else 1f
        return (weight * weight * weight * bandGate).coerceIn(0f, 1f)
    }

    private fun pointInPolygon(x: Float, y: Float, polygon: List<AnchorPoint>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            val denominator = (pj.y - pi.y).takeIf { abs(it) > .0001f } ?: .0001f
            val intersects = ((pi.y > y) != (pj.y > y)) && (x < (pj.x - pi.x) * (y - pi.y) / denominator + pi.x)
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    private fun distanceToPolygonEdge(x: Float, y: Float, polygon: List<AnchorPoint>): Float {
        var best = Float.MAX_VALUE
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            best = min(best, distanceToSegment(x, y, polygon[j], polygon[i]))
            j = i
        }
        return best
    }

    private fun distanceToSegment(x: Float, y: Float, a: AnchorPoint, b: AnchorPoint): Float {
        val vx = b.x - a.x
        val vy = b.y - a.y
        val wx = x - a.x
        val wy = y - a.y
        val lengthSquared = vx * vx + vy * vy
        if (lengthSquared <= .0001f) {
            val dx = x - a.x
            val dy = y - a.y
            return sqrt(dx * dx + dy * dy)
        }
        val t = ((wx * vx + wy * vy) / lengthSquared).coerceIn(0f, 1f)
        val px = a.x + t * vx
        val py = a.y + t * vy
        val dx = x - px
        val dy = y - py
        return sqrt(dx * dx + dy * dy)
    }
}
