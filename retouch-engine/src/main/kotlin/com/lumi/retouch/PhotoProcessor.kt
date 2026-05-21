package com.lumi.retouch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object PhotoProcessor {
    fun process(
        source: Bitmap,
        recipe: EditRecipe,
        faceAnchors: List<FaceAnchor> = emptyList(),
        faceMeshes: List<FaceMeshAnchor> = emptyList(),
        personMask: PersonSegmentationMask? = null,
        bodyPose: BodyPoseAnchor? = null
    ): Bitmap {
        val working = applyTransform(source, recipe)
        return processPrepared(working, recipe.withoutTransform(), faceAnchors, faceMeshes, personMask, bodyPose)
    }

    fun processPrepared(
        source: Bitmap,
        recipe: EditRecipe,
        faceAnchors: List<FaceAnchor> = emptyList(),
        faceMeshes: List<FaceMeshAnchor> = emptyList(),
        personMask: PersonSegmentationMask? = null,
        bodyPose: BodyPoseAnchor? = null
    ): Bitmap {
        val working = source.copy(Bitmap.Config.ARGB_8888, true)
        val filtered = applyColor(working, recipe)
        return processEffectsPrepared(filtered, recipe, faceAnchors, faceMeshes, personMask, bodyPose)
    }

    fun processEffectsPrepared(
        colorPrepared: Bitmap,
        recipe: EditRecipe,
        faceAnchors: List<FaceAnchor> = emptyList(),
        faceMeshes: List<FaceMeshAnchor> = emptyList(),
        personMask: PersonSegmentationMask? = null,
        bodyPose: BodyPoseAnchor? = null,
        skipSkinRetouch: Boolean = false,
        skipMeshWarp: Boolean = false,
        skipMeshMakeup: Boolean = false,
        skipVignette: Boolean = false
    ): Bitmap {
        if (recipe.cutoutStudio) {
            applyStudioCutout(colorPrepared, recipe, faceAnchors, faceMeshes, personMask)
        }
        if (!skipSkinRetouch && (recipe.skinSmooth > 0f || recipe.blemishSoften > 0f)) {
            applySkinRetouch(colorPrepared, recipe, faceMeshes)
        }
        if (recipe.healPoints.isNotEmpty()) {
            applyLocalHeal(colorPrepared, recipe.healPoints)
        }
        if (!skipMeshWarp && faceMeshes.isNotEmpty() && recipe.hasMeshWarp()) {
            applyFaceMeshWarp(colorPrepared, recipe, faceMeshes)
        }
        if (recipe.bodyTune > 0f && bodyPose != null) {
            applyBodyPoseWarp(colorPrepared, recipe, bodyPose)
        }
        if (!skipMeshMakeup && faceMeshes.isNotEmpty() && recipe.hasMeshMakeup()) {
            applyMeshMakeup(colorPrepared, recipe, faceMeshes)
        }
        if (recipe.portraitRelight > 0f || recipe.underEyeLift > 0f || recipe.catchlight > 0f) {
            applyPortraitRelight(colorPrepared, recipe, faceAnchors, faceMeshes)
        }

        val canvas = Canvas(colorPrepared)
        if (recipe.eyeBright > 0f || recipe.teethWhite > 0f) {
            drawFaceHighlights(canvas, colorPrepared.width, colorPrepared.height, recipe, faceAnchors)
        }
        if (faceMeshes.isEmpty() && recipe.hasMeshMakeup()) {
            drawMakeup(canvas, colorPrepared.width, colorPrepared.height, recipe, faceAnchors)
        }
        if (faceMeshes.isEmpty() && (recipe.faceSlim > 0f || recipe.bodyTune > 0f)) {
            drawReshapeGuide(canvas, colorPrepared.width, colorPrepared.height, recipe, faceAnchors)
        }
        if (!skipVignette && recipe.vignette > 0f) {
            drawVignette(canvas, colorPrepared.width, colorPrepared.height, recipe.vignette)
        }
        if (recipe.watermarkEnabled) {
            drawWatermark(canvas, colorPrepared.width, colorPrepared.height)
        }

        return colorPrepared
    }

    fun transformOnly(source: Bitmap, recipe: EditRecipe): Bitmap = applyTransform(source, recipe)

    private fun EditRecipe.withoutTransform(): EditRecipe {
        return copy(rotationDegrees = 0, flipHorizontal = false, cropMode = CropMode.Original)
    }

    fun createDemoBitmap(width: Int = 1200, height: Int = 1600): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        bg.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(Color.rgb(238, 231, 219), Color.rgb(183, 204, 211), Color.rgb(236, 197, 172)),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(51, 49, 45)
        canvas.drawOval(RectF(width * .27f, height * .14f, width * .73f, height * .5f), paint)
        paint.color = Color.rgb(224, 174, 146)
        canvas.drawOval(RectF(width * .32f, height * .22f, width * .68f, height * .56f), paint)
        paint.color = Color.rgb(35, 33, 31)
        canvas.drawCircle(width * .43f, height * .37f, 18f, paint)
        canvas.drawCircle(width * .57f, height * .37f, 18f, paint)
        paint.color = Color.rgb(173, 76, 78)
        canvas.drawOval(RectF(width * .43f, height * .46f, width * .57f, height * .49f), paint)
        paint.color = Color.rgb(63, 82, 88)
        canvas.drawRoundRect(RectF(width * .22f, height * .58f, width * .78f, height * .98f), 80f, 80f, paint)
        paint.color = Color.argb(65, 255, 255, 255)
        canvas.drawCircle(width * .72f, height * .2f, width * .18f, paint)
        return bitmap
    }

    private fun applyColor(source: Bitmap, recipe: EditRecipe): Bitmap {
        val matrix = ColorMatrix()
        matrix.postConcat(saturationMatrix(1f + recipe.saturation + filterSaturation(recipe.filterLook)))
        matrix.postConcat(contrastMatrix(1f + recipe.contrast + filterContrast(recipe.filterLook)))
        matrix.postConcat(brightnessMatrix((recipe.exposure + filterExposure(recipe.filterLook)) * 255f))
        matrix.postConcat(warmthMatrix(recipe.warmth + filterWarmth(recipe.filterLook)))

        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(source, 0f, 0f, paint)

        applyLookGrade(output, recipe.filterLook)
        if (recipe.sharpen > 0f) {
            drawClarityOverlay(canvas, output.width, output.height, recipe.sharpen)
        }
        return output
    }

    private fun applyLookGrade(bitmap: Bitmap, look: FilterLook) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (index in pixels.indices) {
            pixels[index] = PortraitLut.apply(pixels[index], look)
        }
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun applyTransform(source: Bitmap, recipe: EditRecipe): Bitmap {
        val cropped = cropBitmap(source, recipe.cropMode)
        val matrix = Matrix().apply {
            if (recipe.flipHorizontal) {
                preScale(-1f, 1f, cropped.width / 2f, cropped.height / 2f)
            }
            postRotate(recipe.rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
            .copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun cropBitmap(source: Bitmap, cropMode: CropMode): Bitmap {
        if (cropMode == CropMode.Original) return source.copy(Bitmap.Config.ARGB_8888, true)
        val targetRatio = when (cropMode) {
            CropMode.Original -> source.width.toFloat() / source.height.toFloat()
            CropMode.Square -> 1f
            CropMode.Portrait -> 4f / 5f
            CropMode.Story -> 9f / 16f
        }
        val currentRatio = source.width.toFloat() / source.height.toFloat()
        val cropWidth: Int
        val cropHeight: Int
        if (currentRatio > targetRatio) {
            cropHeight = source.height
            cropWidth = (cropHeight * targetRatio).toInt()
        } else {
            cropWidth = source.width
            cropHeight = (cropWidth / targetRatio).toInt()
        }
        val left = ((source.width - cropWidth) / 2).coerceAtLeast(0)
        val top = ((source.height - cropHeight) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)
    }

    private fun applyStudioCutout(
        bitmap: Bitmap,
        recipe: EditRecipe,
        faceAnchors: List<FaceAnchor>,
        faceMeshes: List<FaceMeshAnchor>,
        personMask: PersonSegmentationMask?
    ) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val strength = recipe.studioStrength.coerceIn(0f, 1f)

        for (y in 0 until height) {
            val fy = y / height.toFloat()
            for (x in 0 until width) {
                val index = y * width + x
                val mlMask = personMask?.confidenceAt(x.toFloat(), y.toFloat(), width, height)
                val heuristicMask = subjectMask(x.toFloat(), y.toFloat(), width, height, faceAnchors, faceMeshes)
                val subject = if (mlMask != null) {
                    max(mlMask, heuristicMask * .42f)
                } else {
                    heuristicMask
                }
                val refinedSubject = refineSubjectMask(subject, heuristicMask, recipe.matteRefine)
                val replace = ((1f - refinedSubject) * strength).coerceIn(0f, 1f)
                if (replace <= 0.01f) continue
                val edge = (1f - kotlin.math.abs(refinedSubject - .5f) * 2f).coerceIn(0f, 1f)
                if (recipe.transparentBackground) {
                    val alpha = (refinedSubject * 255f).roundToInt().coerceIn(0, 255)
                    pixels[index] = (pixels[index] and 0x00FFFFFF) or (alpha shl 24)
                } else {
                    val bg = studioBackgroundColor(x / width.toFloat(), fy, recipe.studioBackdrop)
                    val decontaminated = if (edge > 0f) colorDecontaminate(pixels[index], bg, edge * recipe.matteRefine) else pixels[index]
                    val edgeLift = edge * .035f * strength * recipe.matteRefine.coerceIn(0f, 1f)
                    val mixed = blendColors(decontaminated, bg, replace)
                    pixels[index] = if (edgeLift > 0f) blendColors(mixed, Color.WHITE, edgeLift) else mixed
                }
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun refineSubjectMask(subject: Float, heuristic: Float, matteRefine: Float): Float {
        val refine = matteRefine.coerceIn(0f, 1f)
        val combined = max(subject, heuristic * (.24f + refine * .28f))
        val contrast = smoothstep(.08f + refine * .04f, .86f - refine * .12f, combined)
        return (combined * (1f - refine * .45f) + contrast * refine * .45f).coerceIn(0f, 1f)
    }

    private fun colorDecontaminate(color: Int, background: Int, amount: Float): Int {
        val a = amount.coerceIn(0f, .45f)
        val r = (Color.red(color) + (Color.red(color) - Color.red(background)) * a).roundToInt().coerceIn(0, 255)
        val g = (Color.green(color) + (Color.green(color) - Color.green(background)) * a).roundToInt().coerceIn(0, 255)
        val b = (Color.blue(color) + (Color.blue(color) - Color.blue(background)) * a).roundToInt().coerceIn(0, 255)
        return Color.argb(Color.alpha(color), r, g, b)
    }

    private fun subjectMask(
        x: Float,
        y: Float,
        width: Int,
        height: Int,
        faceAnchors: List<FaceAnchor>,
        faceMeshes: List<FaceMeshAnchor>
    ): Float {
        var mask = 0f
        faceMeshes.forEach { mesh ->
            mask = max(mask, portraitSubjectMask(x, y, mesh.bounds, width, height))
        }
        faceAnchors.forEach { face ->
            mask = max(mask, portraitSubjectMask(x, y, face.bounds, width, height))
        }
        if (mask > 0f) return mask.coerceIn(0f, 1f)

        val fallbackHead = ellipseCoverage(
            x,
            y,
            AnchorPoint(width * .5f, height * .34f),
            width * .25f,
            height * .23f,
            .78f,
            1.08f
        )
        val fallbackTorso = ellipseCoverage(
            x,
            y,
            AnchorPoint(width * .5f, height * .86f),
            width * .45f,
            height * .46f,
            .7f,
            1.08f
        )
        return max(fallbackHead, fallbackTorso).coerceIn(0f, 1f)
    }

    private fun portraitSubjectMask(x: Float, y: Float, face: RectF, width: Int, height: Int): Float {
        val faceWidth = face.width().coerceAtLeast(width * .08f)
        val faceHeight = face.height().coerceAtLeast(height * .08f)
        val head = ellipseCoverage(
            x,
            y,
            AnchorPoint(face.centerX(), face.top + faceHeight * .46f),
            faceWidth * .86f,
            faceHeight * .82f,
            .9f,
            1.14f
        )
        val hair = ellipseCoverage(
            x,
            y,
            AnchorPoint(face.centerX(), face.top + faceHeight * .28f),
            faceWidth * 1.02f,
            faceHeight * .62f,
            .88f,
            1.16f
        )
        val neck = ellipseCoverage(
            x,
            y,
            AnchorPoint(face.centerX(), face.bottom + faceHeight * .15f),
            faceWidth * .34f,
            faceHeight * .32f,
            .58f,
            1.04f
        )
        val torsoCenterY = (face.bottom + faceHeight * 1.6f).coerceAtMost(height * .88f)
        val torso = ellipseCoverage(
            x,
            y,
            AnchorPoint(face.centerX(), torsoCenterY),
            (faceWidth * 1.95f).coerceAtMost(width * .56f),
            (faceHeight * 2.25f).coerceAtMost(height * .68f),
            .78f,
            1.12f
        )
        val shoulderY = face.bottom + faceHeight * .55f
        val shoulder = if (y >= face.bottom - faceHeight * .12f) {
            val progress = ((y - shoulderY) / (height - shoulderY).coerceAtLeast(1f)).coerceIn(0f, 1f)
            val halfWidth = (faceWidth * (1.05f + progress * 2.25f)).coerceAtMost(width * .58f)
            val distance = abs(x - face.centerX())
            val horizontal = when {
                distance <= halfWidth * .86f -> 1f
                distance >= halfWidth * 1.12f -> 0f
                else -> 1f - smoothstep(halfWidth * .86f, halfWidth * 1.12f, distance)
            }
            val vertical = (1f - progress * .12f).coerceIn(.78f, 1f)
            horizontal * vertical
        } else {
            0f
        }
        return max(max(head, hair), max(neck, max(torso, shoulder))).coerceIn(0f, 1f)
    }

    private fun ellipseCoverage(
        x: Float,
        y: Float,
        center: AnchorPoint,
        radiusX: Float,
        radiusY: Float,
        hardEdge: Float,
        softEdge: Float
    ): Float {
        if (radiusX <= 0f || radiusY <= 0f) return 0f
        val dx = (x - center.x) / radiusX
        val dy = (y - center.y) / radiusY
        val distance = sqrt(dx * dx + dy * dy)
        return when {
            distance <= hardEdge -> 1f
            distance >= softEdge -> 0f
            else -> 1f - smoothstep(hardEdge, softEdge, distance)
        }
    }

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0).coerceAtLeast(.0001f)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun studioBackgroundColor(x: Float, y: Float, backdrop: StudioBackdrop): Int {
        val top: Int
        val bottom: Int
        val accent: Int
        when (backdrop) {
            StudioBackdrop.SoftGray -> {
                top = Color.rgb(247, 247, 244)
                bottom = Color.rgb(218, 224, 224)
                accent = Color.rgb(255, 255, 255)
            }
            StudioBackdrop.WarmCream -> {
                top = Color.rgb(252, 241, 226)
                bottom = Color.rgb(224, 204, 184)
                accent = Color.rgb(255, 249, 239)
            }
            StudioBackdrop.CoolWhite -> {
                top = Color.rgb(248, 252, 255)
                bottom = Color.rgb(210, 224, 235)
                accent = Color.rgb(255, 255, 255)
            }
            StudioBackdrop.Peach -> {
                top = Color.rgb(255, 230, 216)
                bottom = Color.rgb(224, 170, 157)
                accent = Color.rgb(255, 245, 237)
            }
            StudioBackdrop.Slate -> {
                top = Color.rgb(58, 68, 70)
                bottom = Color.rgb(22, 29, 31)
                accent = Color.rgb(89, 106, 108)
            }
        }
        val base = blendColors(top, bottom, y.coerceIn(0f, 1f))
        val vignette = (1f - sqrt((x - .42f) * (x - .42f) + (y - .28f) * (y - .28f)) * 1.6f).coerceIn(0f, 1f)
        return blendColors(base, accent, vignette * .28f)
    }

    private fun applySkinRetouch(bitmap: Bitmap, recipe: EditRecipe, faceMeshes: List<FaceMeshAnchor>) {
        val blend = (recipe.skinSmooth * .62f + recipe.blemishSoften * .28f).coerceIn(0f, .68f)
        if (blend <= 0f) return

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val sourcePixels = pixels.copyOf()
        val regions = if (faceMeshes.isEmpty()) {
            listOf(Rect(0, 0, bitmap.width, bitmap.height))
        } else {
            faceMeshes.map { it.retouchRegion(bitmap.width, bitmap.height) }
        }

        regions.forEach { region ->
            for (y in region.top until region.bottom) {
                for (x in region.left until region.right) {
                    val index = y * bitmap.width + x
                    val original = sourcePixels[index]
                    val skin = FaceMaskUtils.skinWeight(original)
                    val faceMask = if (faceMeshes.isEmpty()) {
                        1f
                    } else {
                        faceMeshes.maxOf { FaceMaskUtils.faceSkinMask(x.toFloat(), y.toFloat(), it) }
                    }
                    if (skin <= 0f || faceMask <= 0f) continue
                    val tZone = if (faceMeshes.isEmpty()) {
                        0f
                    } else {
                        faceMeshes.maxOf { FaceMaskUtils.tZoneMask(x.toFloat(), y.toFloat(), it) }
                    }
                    val localTexture = localDetailWeight(sourcePixels, bitmap.width, bitmap.height, x, y)
                    val blemish = blemishWeight(sourcePixels, bitmap.width, bitmap.height, x, y, original)
                    val textureGuard = if (blemish > 0f) 1f else localTexture
                    val tZoneGuard = 1f - tZone * .28f
                    val weight = (skin * faceMask * textureGuard * tZoneGuard).coerceIn(0f, 1f)
                    if (weight > 0f) {
                        val smoothAmount = recipe.skinSmooth * .58f * weight
                        val blemishAmount = recipe.blemishSoften * (.2f * weight + .75f * blemish * skin * faceMask)
                        val smoothColor = smoothPixelAt(sourcePixels, bitmap.width, bitmap.height, x, y)
                        val mixed = blendColors(original, smoothColor, (smoothAmount + blemishAmount * .28f).coerceIn(0f, .72f))
                        pixels[index] = if (recipe.blemishSoften > 0f) reduceRedSpots(mixed, blemishAmount) else mixed
                    }
                }
            }
        }
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun applyLocalHeal(bitmap: Bitmap, points: List<HealPoint>) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val sourcePixels = pixels.copyOf()
        points.take(80).forEach { point ->
            val cx = (point.x.coerceIn(0f, 1f) * (width - 1)).roundToInt()
            val cy = (point.y.coerceIn(0f, 1f) * (height - 1)).roundToInt()
            val radius = (point.radius.coerceIn(.008f, .08f) * min(width, height)).roundToInt().coerceAtLeast(4)
            val patch = ringAverageColor(sourcePixels, width, height, cx, cy, radius)
            val left = (cx - radius).coerceAtLeast(0)
            val right = (cx + radius).coerceAtMost(width - 1)
            val top = (cy - radius).coerceAtLeast(0)
            val bottom = (cy + radius).coerceAtMost(height - 1)
            for (y in top..bottom) {
                for (x in left..right) {
                    val dx = x - cx
                    val dy = y - cy
                    val distance = sqrt((dx * dx + dy * dy).toFloat()) / radius
                    if (distance > 1f) continue
                    val index = y * width + x
                    val feather = (1f - distance).coerceIn(0f, 1f)
                    val amount = feather * feather * point.strength.coerceIn(0f, 1f)
                    val localSmooth = smoothPixelAt(sourcePixels, width, height, x, y)
                    val replacement = when (point.mode) {
                        HealMode.Heal -> blendColors(localSmooth, patch, .64f)
                        HealMode.Clone -> patch
                        HealMode.Restore -> sourcePixels[index]
                        HealMode.Erase -> blendColors(pixels[index], localSmooth, .72f)
                    }
                    pixels[index] = blendColors(pixels[index], replacement, amount)
                }
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun EditRecipe.hasMeshWarp(): Boolean {
        return faceSlim > 0f || eyeScale > 0f || noseSlim > 0f || lipPlump > 0f || bodyTune > 0f
    }

    private fun EditRecipe.hasMeshMakeup(): Boolean {
        return makeupStrength > 0f && (lipOpacity > 0f || blushOpacity > 0f || contour > 0f || eyeShadow > 0f || eyeLine > 0f)
    }

    private fun applyFaceMeshWarp(bitmap: Bitmap, recipe: EditRecipe, faceMeshes: List<FaceMeshAnchor>) {
        val width = bitmap.width
        val height = bitmap.height
        val sourcePixels = IntArray(width * height).also {
            bitmap.getPixels(it, 0, width, 0, 0, width, height)
        }
        val outputPixels = sourcePixels.copyOf()

        faceMeshes.forEach { mesh ->
            val region = mesh.warpRegion(width, height)
            for (y in region.top until region.bottom) {
                for (x in region.left until region.right) {
                    val mapped = mapFacePoint(x.toFloat(), y.toFloat(), mesh, recipe)
                    if (mapped.x != x.toFloat() || mapped.y != y.toFloat()) {
                        outputPixels[y * width + x] = sampleBilinear(sourcePixels, width, height, mapped.x, mapped.y)
                    }
                }
            }
        }
        bitmap.setPixels(outputPixels, 0, width, 0, 0, width, height)
    }

    private fun applyMeshMakeup(bitmap: Bitmap, recipe: EditRecipe, faceMeshes: List<FaceMeshAnchor>) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height).also {
            bitmap.getPixels(it, 0, width, 0, 0, width, height)
        }

        faceMeshes.forEach { mesh ->
            val region = mesh.warpRegion(width, height)
            for (y in region.top until region.bottom) {
                for (x in region.left until region.right) {
                    val index = y * width + x
                    var color = pixels[index]

                    val makeupScale = recipe.makeupStrength.coerceIn(0f, 1.4f)
                    if (recipe.lipOpacity > 0f && makeupScale > 0f) {
                        val lipMask = FaceMaskUtils.lipMask(x.toFloat(), y.toFloat(), mesh, recipe.lipPrecision)
                        if (lipMask > 0f) {
                            val adaptive = FaceMaskUtils.makeupAdaptiveStrength(color, preferSkin = false)
                            color = naturalLipTint(color, recipe.lipColor, recipe.lipOpacity * makeupScale * lipMask * adaptive)
                        }
                    }

                    if (recipe.blushOpacity > 0f && makeupScale > 0f) {
                        val blushMask = FaceMaskUtils.blushMask(x.toFloat(), y.toFloat(), mesh, recipe.blushSpread) *
                            FaceMaskUtils.skinWeight(color) *
                            FaceMaskUtils.makeupAdaptiveStrength(color, preferSkin = true)
                        if (blushMask > 0f) {
                            color = softLightTint(color, Color.rgb(226, 92, 104), recipe.blushOpacity * makeupScale * blushMask * .55f)
                        }
                    }

                    if (recipe.contour > 0f && makeupScale > 0f) {
                        val cheekContour = FaceMaskUtils.contourMask(x.toFloat(), y.toFloat(), mesh) * FaceMaskUtils.skinWeight(color)
                        val noseMask = FaceMaskUtils.noseMask(x.toFloat(), y.toFloat(), mesh)
                        val amount = recipe.contour * makeupScale * maxOf(cheekContour * .45f, noseMask * .16f) *
                            FaceMaskUtils.makeupAdaptiveStrength(color, preferSkin = true)
                        if (amount > 0f) {
                            color = contourShade(color, amount)
                        }
                    }

                    if (recipe.eyeShadow > 0f && makeupScale > 0f) {
                        val shadowMask = FaceMaskUtils.eyeShadowMask(x.toFloat(), y.toFloat(), mesh) *
                            FaceMaskUtils.makeupAdaptiveStrength(color, preferSkin = false)
                        if (shadowMask > 0f) {
                            color = eyeShadowTint(color, recipe.eyeShadow * makeupScale * shadowMask * .38f)
                        }
                    }

                    if (recipe.eyeLine > 0f && makeupScale > 0f) {
                        val lineMask = FaceMaskUtils.eyeLineMask(x.toFloat(), y.toFloat(), mesh)
                        if (lineMask > 0f) {
                            color = eyeLineTint(color, recipe.eyeLine * makeupScale * lineMask * .36f)
                        }
                    }

                    pixels[index] = color
                }
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun applyBodyPoseWarp(bitmap: Bitmap, recipe: EditRecipe, pose: BodyPoseAnchor) {
        if (pose.confidence < .35f) return
        val width = bitmap.width
        val height = bitmap.height
        val sourcePixels = IntArray(width * height).also {
            bitmap.getPixels(it, 0, width, 0, 0, width, height)
        }
        val outputPixels = sourcePixels.copyOf()
        val shoulderCenter = AnchorPoint(
            (pose.leftShoulder.x + pose.rightShoulder.x) / 2f,
            (pose.leftShoulder.y + pose.rightShoulder.y) / 2f
        )
        val hipCenter = AnchorPoint(
            (pose.leftHip.x + pose.rightHip.x) / 2f,
            (pose.leftHip.y + pose.rightHip.y) / 2f
        )
        val centerX = (shoulderCenter.x + hipCenter.x) / 2f
        val top = (min(pose.leftShoulder.y, pose.rightShoulder.y) - height * .04f).roundToInt().coerceIn(0, height - 1)
        val bottom = (max(pose.leftHip.y, pose.rightHip.y) + height * .22f).roundToInt().coerceIn(1, height)
        val shoulderWidth = abs(pose.rightShoulder.x - pose.leftShoulder.x).coerceAtLeast(width * .16f)
        val hipWidth = abs(pose.rightHip.x - pose.leftHip.x).coerceAtLeast(shoulderWidth * .55f)
        val regionHalf = max(shoulderWidth, hipWidth) * .88f
        val left = (centerX - regionHalf).roundToInt().coerceIn(0, width - 1)
        val right = (centerX + regionHalf).roundToInt().coerceIn(1, width)

        for (y in top until bottom) {
            val vertical = ((y - top) / (bottom - top).toFloat()).coerceIn(0f, 1f)
            val waistBias = 1f - abs(vertical - .54f) / .54f
            val shoulderGuard = if (vertical < .22f) vertical / .22f else 1f
            for (x in left until right) {
                val dx = x - centerX
                val horizontal = (1f - abs(dx) / regionHalf).coerceIn(0f, 1f)
                val weight = horizontal * horizontal * waistBias.coerceIn(0f, 1f) * shoulderGuard.coerceIn(0f, 1f)
                if (weight <= 0f) continue
                val strength = recipe.bodyTune.coerceIn(0f, 1f) * .16f * weight
                val sx = centerX + dx * (1f + strength)
                outputPixels[y * width + x] = sampleBilinear(sourcePixels, width, height, sx, y.toFloat())
            }
        }
        bitmap.setPixels(outputPixels, 0, width, 0, 0, width, height)
    }

    private fun applyPortraitRelight(
        bitmap: Bitmap,
        recipe: EditRecipe,
        faceAnchors: List<FaceAnchor>,
        faceMeshes: List<FaceMeshAnchor>
    ) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height).also {
            bitmap.getPixels(it, 0, width, 0, 0, width, height)
        }
        val regions = if (faceMeshes.isNotEmpty()) {
            faceMeshes.map { it.bounds }
        } else if (faceAnchors.isNotEmpty()) {
            faceAnchors.map { it.bounds }
        } else {
            listOf(RectF(width * .28f, height * .16f, width * .72f, height * .58f))
        }
        regions.forEach { bounds ->
            val left = (bounds.left - bounds.width() * .18f).roundToInt().coerceIn(0, width - 1)
            val right = (bounds.right + bounds.width() * .18f).roundToInt().coerceIn(1, width)
            val top = (bounds.top - bounds.height() * .14f).roundToInt().coerceIn(0, height - 1)
            val bottom = (bounds.bottom + bounds.height() * .2f).roundToInt().coerceIn(1, height)
            val cheekLight = AnchorPoint(bounds.centerX() - bounds.width() * .12f, bounds.top + bounds.height() * .54f)
            val bridgeLight = AnchorPoint(bounds.centerX(), bounds.top + bounds.height() * .38f)
            val jawShadow = AnchorPoint(bounds.centerX(), bounds.bottom - bounds.height() * .02f)
            for (y in top until bottom) {
                for (x in left until right) {
                    val index = y * width + x
                    val color = pixels[index]
                    val skin = FaceMaskUtils.skinWeight(color)
                    if (skin <= 0f) continue
                    val relight = recipe.portraitRelight.coerceIn(0f, 1f)
                    val cheek = FaceMaskUtils.ellipseWeight(x.toFloat(), y.toFloat(), cheekLight, bounds.width() * .36f, bounds.height() * .28f)
                    val bridge = FaceMaskUtils.ellipseWeight(x.toFloat(), y.toFloat(), bridgeLight, bounds.width() * .12f, bounds.height() * .34f)
                    val jaw = FaceMaskUtils.ellipseWeight(x.toFloat(), y.toFloat(), jawShadow, bounds.width() * .46f, bounds.height() * .18f)
                    var adjusted = color
                    val lift = (cheek * .08f + bridge * .1f) * relight * skin
                    if (lift > 0f) adjusted = blendColors(adjusted, Color.WHITE, lift.coerceIn(0f, .18f))
                    val shadow = jaw * relight * skin * .1f
                    if (shadow > 0f) adjusted = contourShade(adjusted, shadow.coerceIn(0f, .16f))
                    val underEye = recipe.underEyeLift.coerceIn(0f, 1f) *
                        underEyeMask(x.toFloat(), y.toFloat(), bounds) * skin
                    if (underEye > 0f) adjusted = blendColors(adjusted, Color.rgb(255, 238, 222), underEye * .16f)
                    pixels[index] = adjusted
                }
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        if (recipe.catchlight > 0f) {
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.argb((recipe.catchlight.coerceIn(0f, 1f) * 92).roundToInt(), 255, 255, 245)
            faceMeshes.forEach { mesh ->
                listOfNotNull(eyeCenter(mesh, left = true), eyeCenter(mesh, left = false)).forEach { eye ->
                    val radius = mesh.bounds.width() * .018f
                    canvas.drawCircle(eye.x - radius * .35f, eye.y - radius * .35f, radius, paint)
                }
            }
            if (faceMeshes.isEmpty()) {
                faceAnchors.forEach { face ->
                    val radius = face.bounds.width() * .018f
                    listOfNotNull(face.leftEye, face.rightEye).forEach { eye ->
                        canvas.drawCircle(eye.x - radius * .35f, eye.y - radius * .35f, radius, paint)
                    }
                }
            }
        }
    }

    private fun underEyeMask(x: Float, y: Float, face: RectF): Float {
        val left = AnchorPoint(face.left + face.width() * .36f, face.top + face.height() * .43f)
        val right = AnchorPoint(face.right - face.width() * .36f, face.top + face.height() * .43f)
        return max(
            FaceMaskUtils.ellipseWeight(x, y, left, face.width() * .12f, face.height() * .055f),
            FaceMaskUtils.ellipseWeight(x, y, right, face.width() * .12f, face.height() * .055f)
        )
    }

    private fun mapFacePoint(x: Float, y: Float, mesh: FaceMeshAnchor, recipe: EditRecipe): AnchorPoint {
        var sx = x
        var sy = y
        val faceCenter = AnchorPoint(mesh.bounds.centerX(), mesh.bounds.centerY())

        if (recipe.faceSlim > 0f) {
            val lowerFaceCenter = AnchorPoint(faceCenter.x, mesh.bounds.top + mesh.bounds.height() * .64f)
            val radius = mesh.bounds.width() * .62f
            val dy = abs(y - lowerFaceCenter.y) / (mesh.bounds.height() * .46f)
            val verticalWeight = (1f - dy).coerceIn(0f, 1f)
            val dx = sx - lowerFaceCenter.x
            val distanceWeight = (1f - abs(dx) / radius).coerceIn(0f, 1f)
            val strength = recipe.faceSlim * .18f * verticalWeight * distanceWeight
            sx = lowerFaceCenter.x + dx * (1f + strength)
        }

        if (recipe.noseSlim > 0f) {
            val noseTop = mesh.point(168) ?: mesh.point(6) ?: faceCenter
            val noseTip = mesh.point(1) ?: mesh.point(4) ?: faceCenter
            val noseCenter = AnchorPoint((noseTop.x + noseTip.x) / 2f, (noseTop.y + noseTip.y) / 2f)
            val radiusX = mesh.bounds.width() * .13f
            val radiusY = mesh.bounds.height() * .2f
            val weight = FaceMaskUtils.ellipseWeight(sx, sy, noseCenter, radiusX, radiusY)
            val dx = sx - noseCenter.x
            sx = noseCenter.x + dx * (1f + recipe.noseSlim * .3f * weight)
        }

        if (recipe.eyeScale > 0f) {
            listOfNotNull(eyeCenter(mesh, left = true), eyeCenter(mesh, left = false)).forEach { eye ->
                val radius = mesh.bounds.width() * .12f
                val weight = circleWeight(sx, sy, eye, radius)
                if (weight > 0f) {
                    sx = eye.x + (sx - eye.x) * (1f - recipe.eyeScale * .22f * weight)
                    sy = eye.y + (sy - eye.y) * (1f - recipe.eyeScale * .22f * weight)
                }
            }
        }

        if (recipe.lipPlump > 0f) {
            val mouth = mouthCenter(mesh)
            if (mouth != null) {
                val weight = FaceMaskUtils.ellipseWeight(sx, sy, mouth, mesh.bounds.width() * .18f, mesh.bounds.height() * .08f)
                if (weight > 0f) {
                    sx = mouth.x + (sx - mouth.x) * (1f - recipe.lipPlump * .12f * weight)
                    sy = mouth.y + (sy - mouth.y) * (1f - recipe.lipPlump * .18f * weight)
                }
            }
        }

        if (recipe.bodyTune > 0f) {
            val chin = mesh.point(152) ?: AnchorPoint(faceCenter.x, mesh.bounds.bottom)
            val weight = FaceMaskUtils.ellipseWeight(sx, sy, chin, mesh.bounds.width() * .24f, mesh.bounds.height() * .16f)
            sy += mesh.bounds.height() * .045f * recipe.bodyTune * weight
        }

        return AnchorPoint(sx, sy)
    }

    private fun drawFaceHighlights(canvas: Canvas, width: Int, height: Int, recipe: EditRecipe, faceAnchors: List<FaceAnchor>) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (faceAnchors.isNotEmpty()) {
            faceAnchors.forEach { face ->
                val eyeRadius = face.bounds.width() * .045f
                paint.color = Color.argb((recipe.eyeBright * 118).toInt(), 255, 255, 245)
                listOfNotNull(face.leftEye, face.rightEye).forEach { eye ->
                    canvas.drawOval(RectF(eye.x - eyeRadius, eye.y - eyeRadius * .58f, eye.x + eyeRadius, eye.y + eyeRadius * .58f), paint)
                }
                val mouth = mouthRect(face, .16f, .055f)
                if (mouth != null) {
                    paint.color = Color.argb((recipe.teethWhite * 96).toInt(), 255, 255, 255)
                    canvas.drawRoundRect(mouth, mouth.height() * .35f, mouth.height() * .35f, paint)
                }
            }
        } else {
            paint.color = Color.argb((recipe.eyeBright * 120).toInt(), 255, 255, 245)
            canvas.drawOval(RectF(width * .39f, height * .35f, width * .47f, height * .39f), paint)
            canvas.drawOval(RectF(width * .53f, height * .35f, width * .61f, height * .39f), paint)
            paint.color = Color.argb((recipe.teethWhite * 110).toInt(), 255, 255, 255)
            canvas.drawRoundRect(RectF(width * .45f, height * .445f, width * .55f, height * .465f), 12f, 12f, paint)
        }
    }

    private fun drawMakeup(canvas: Canvas, width: Int, height: Int, recipe: EditRecipe, faceAnchors: List<FaceAnchor>) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val makeupScale = recipe.makeupStrength.coerceIn(0f, 1.4f)
        if (makeupScale <= 0f) return
        if (faceAnchors.isNotEmpty()) {
            faceAnchors.forEach { face ->
                paint.color = Color.argb((recipe.blushOpacity * makeupScale * 92).toInt().coerceIn(0, 255), 225, 86, 96)
                val cheekRadius = face.bounds.width() * .09f
                listOfNotNull(face.leftCheek, face.rightCheek).forEach { cheek ->
                    canvas.drawOval(
                        RectF(cheek.x - cheekRadius, cheek.y - cheekRadius * .62f, cheek.x + cheekRadius, cheek.y + cheekRadius * .62f),
                        paint
                    )
                }
                mouthRect(face, .22f, .08f)?.let { mouth ->
                    paint.color = setAlpha(recipe.lipColor, (recipe.lipOpacity * makeupScale * 170).toInt())
                    canvas.drawOval(mouth, paint)
                }
                paint.color = Color.argb((recipe.contour * makeupScale * 74).toInt().coerceIn(0, 255), 84, 54, 42)
                paint.strokeWidth = max(3f, face.bounds.width() * .015f)
                paint.style = Paint.Style.STROKE
                canvas.drawArc(leftJawRect(face), 100f, 58f, false, paint)
                canvas.drawArc(rightJawRect(face), 22f, 58f, false, paint)
                paint.style = Paint.Style.FILL
            }
        } else {
            paint.color = Color.argb((recipe.blushOpacity * makeupScale * 95).toInt().coerceIn(0, 255), 225, 86, 96)
            canvas.drawOval(RectF(width * .33f, height * .4f, width * .45f, height * .47f), paint)
            canvas.drawOval(RectF(width * .55f, height * .4f, width * .67f, height * .47f), paint)
            paint.color = setAlpha(recipe.lipColor, (recipe.lipOpacity * makeupScale * 180).toInt())
            canvas.drawOval(RectF(width * .42f, height * .455f, width * .58f, height * .495f), paint)
            paint.color = Color.argb((recipe.contour * makeupScale * 80).toInt().coerceIn(0, 255), 84, 54, 42)
            paint.strokeWidth = width * .015f
            paint.style = Paint.Style.STROKE
            canvas.drawArc(RectF(width * .29f, height * .25f, width * .46f, height * .55f), 92f, 74f, false, paint)
            canvas.drawArc(RectF(width * .54f, height * .25f, width * .71f, height * .55f), 14f, 74f, false, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawReshapeGuide(canvas: Canvas, width: Int, height: Int, recipe: EditRecipe, faceAnchors: List<FaceAnchor>) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.argb(((recipe.faceSlim + recipe.bodyTune) * 40).toInt().coerceIn(0, 70), 20, 20, 20)
        paint.strokeWidth = max(3f, width * .006f)
        paint.style = Paint.Style.STROKE
        if (faceAnchors.isNotEmpty()) {
            faceAnchors.forEach { face ->
                val path = Path()
                path.moveTo(face.bounds.left + face.bounds.width() * .16f, face.bounds.top + face.bounds.height() * .38f)
                path.quadTo(face.bounds.left + face.bounds.width() * (.28f + recipe.faceSlim * .06f), face.bounds.bottom, face.bounds.centerX(), face.bounds.bottom + face.bounds.height() * .04f)
                path.quadTo(face.bounds.left + face.bounds.width() * (.72f - recipe.faceSlim * .06f), face.bounds.bottom, face.bounds.right - face.bounds.width() * .16f, face.bounds.top + face.bounds.height() * .38f)
                canvas.drawPath(path, paint)
            }
        } else {
            val path = Path()
            path.moveTo(width * .33f, height * .34f)
            path.quadTo(width * (.38f + recipe.faceSlim * .02f), height * .55f, width * .5f, height * .58f)
            path.quadTo(width * (.62f - recipe.faceSlim * .02f), height * .55f, width * .67f, height * .34f)
            canvas.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawVignette(canvas: Canvas, width: Int, height: Int, amount: Float) {
        val radius = max(width, height) * .72f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            width / 2f,
            height / 2f,
            radius,
            intArrayOf(Color.TRANSPARENT, Color.argb((amount * 165).toInt(), 0, 0, 0)),
            floatArrayOf(.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawClarityOverlay(canvas: Canvas, width: Int, height: Int, amount: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.argb((amount * 34).toInt(), 255, 255, 255)
        paint.strokeWidth = 1f
        val step = max(24, min(width, height) / 24)
        var y = 0
        while (y < height) {
            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), paint)
            y += step
        }
    }

    private fun drawWatermark(canvas: Canvas, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = max(18f, width * .026f)
        paint.color = Color.argb(138, 255, 255, 255)
        val x = width - max(14f, width * .025f)
        val y = height - max(16f, height * .025f)
        canvas.drawText("Lumi", x + 1f, y + 1f, paint.apply { color = Color.argb(96, 0, 0, 0) })
        paint.color = Color.argb(158, 255, 255, 255)
        canvas.drawText("Lumi", x, y, paint)
    }

    private fun brightnessMatrix(value: Float): ColorMatrix {
        return ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, value,
                0f, 1f, 0f, 0f, value,
                0f, 0f, 1f, 0f, value,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    private fun contrastMatrix(value: Float): ColorMatrix {
        val translate = (-.5f * value + .5f) * 255f
        return ColorMatrix(
            floatArrayOf(
                value, 0f, 0f, 0f, translate,
                0f, value, 0f, 0f, translate,
                0f, 0f, value, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    private fun saturationMatrix(value: Float): ColorMatrix = ColorMatrix().apply { setSaturation(value) }

    private fun warmthMatrix(value: Float): ColorMatrix {
        val red = value * 35f
        val blue = -value * 35f
        return ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, red,
                0f, 1f, 0f, 0f, value * 8f,
                0f, 0f, 1f, 0f, blue,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    private fun portraitCleanGrade(color: Int): Int {
        val r = toneCurve(Color.red(color), shadows = .01f, mid = .025f, highlights = .01f)
        val g = toneCurve(Color.green(color), shadows = .008f, mid = .018f, highlights = .008f)
        val b = toneCurve(Color.blue(color), shadows = .012f, mid = .012f, highlights = .018f)
        return Color.argb(Color.alpha(color), r, g, b)
    }

    private fun filmGrade(color: Int): Int {
        val l = luma(color) / 255f
        val fade = ((1f - l) * 16f).roundToInt()
        val r = toneCurve(Color.red(color) + 8 + fade, shadows = .045f, mid = -.015f, highlights = -.035f)
        val g = toneCurve(Color.green(color) + 3 + fade / 2, shadows = .032f, mid = -.01f, highlights = -.02f)
        val b = toneCurve(Color.blue(color) - 6 + fade, shadows = .055f, mid = -.02f, highlights = -.012f)
        return Color.argb(Color.alpha(color), r, g, b)
    }

    private fun creamGrade(color: Int): Int {
        val l = luma(color) / 255f
        val warm = (10f + l * 8f).roundToInt()
        val r = toneCurve(Color.red(color) + warm, shadows = .018f, mid = .035f, highlights = .02f)
        val g = toneCurve(Color.green(color) + 5, shadows = .012f, mid = .025f, highlights = .018f)
        val b = toneCurve(Color.blue(color) - 8, shadows = .02f, mid = -.008f, highlights = -.025f)
        return Color.argb(Color.alpha(color), r, g, b)
    }

    private fun neoGrade(color: Int): Int {
        val l = luma(color) / 255f
        val cool = (8f + (1f - l) * 8f).roundToInt()
        val r = toneCurve(Color.red(color) - 6, shadows = -.018f, mid = .012f, highlights = .02f)
        val g = toneCurve(Color.green(color) + 4, shadows = .004f, mid = .02f, highlights = .024f)
        val b = toneCurve(Color.blue(color) + cool, shadows = .028f, mid = .035f, highlights = .018f)
        return Color.argb(Color.alpha(color), r, g, b)
    }

    private fun toneCurve(value: Int, shadows: Float, mid: Float, highlights: Float): Int {
        val x = (value.coerceIn(0, 255) / 255f)
        val shadowWeight = (1f - x).coerceIn(0f, 1f)
        val highlightWeight = x.coerceIn(0f, 1f)
        val midWeight = (1f - abs(x - .5f) * 2f).coerceIn(0f, 1f)
        val adjusted = x + shadows * shadowWeight + mid * midWeight + highlights * highlightWeight
        return (adjusted.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
    }

    private fun filterExposure(filter: FilterLook) = when (filter) {
        FilterLook.Clean -> 0.01f
        FilterLook.Film -> -0.04f
        FilterLook.Cream -> 0.04f
        FilterLook.Korean -> 0.05f
        FilterLook.CoolWhite -> 0.07f
        FilterLook.WarmPortrait -> 0.04f
        FilterLook.Neo -> 0.01f
    }

    private fun filterContrast(filter: FilterLook) = when (filter) {
        FilterLook.Clean -> 0.02f
        FilterLook.Film -> 0.1f
        FilterLook.Cream -> -0.03f
        FilterLook.Korean -> -0.02f
        FilterLook.CoolWhite -> -0.04f
        FilterLook.WarmPortrait -> 0.05f
        FilterLook.Neo -> 0.14f
    }

    private fun filterSaturation(filter: FilterLook) = when (filter) {
        FilterLook.Clean -> 0.01f
        FilterLook.Film -> -0.1f
        FilterLook.Cream -> 0.02f
        FilterLook.Korean -> -0.02f
        FilterLook.CoolWhite -> -0.08f
        FilterLook.WarmPortrait -> 0.05f
        FilterLook.Neo -> 0.13f
    }

    private fun filterWarmth(filter: FilterLook) = when (filter) {
        FilterLook.Clean -> 0f
        FilterLook.Film -> 0.06f
        FilterLook.Cream -> 0.12f
        FilterLook.Korean -> -0.02f
        FilterLook.CoolWhite -> -0.14f
        FilterLook.WarmPortrait -> 0.14f
        FilterLook.Neo -> -0.12f
    }

    private fun setAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun mouthRect(face: FaceAnchor, widthFactor: Float, heightFactor: Float): RectF? {
        val center = face.mouthCenter ?: return null
        val w = face.bounds.width() * widthFactor
        val h = face.bounds.height() * heightFactor
        return RectF(center.x - w / 2f, center.y - h / 2f, center.x + w / 2f, center.y + h / 2f)
    }

    private fun leftJawRect(face: FaceAnchor): RectF {
        return RectF(
            face.bounds.left + face.bounds.width() * .04f,
            face.bounds.top + face.bounds.height() * .3f,
            face.bounds.left + face.bounds.width() * .42f,
            face.bounds.bottom + face.bounds.height() * .08f
        )
    }

    private fun rightJawRect(face: FaceAnchor): RectF {
        return RectF(
            face.bounds.right - face.bounds.width() * .42f,
            face.bounds.top + face.bounds.height() * .3f,
            face.bounds.right - face.bounds.width() * .04f,
            face.bounds.bottom + face.bounds.height() * .08f
        )
    }

    private fun eyeCenter(mesh: FaceMeshAnchor, left: Boolean): AnchorPoint? {
        val indices = if (left) listOf(33, 133, 159, 145) else listOf(362, 263, 386, 374)
        val points = indices.mapNotNull { mesh.point(it) }
        if (points.isEmpty()) return null
        return AnchorPoint(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
    }

    private fun mouthCenter(mesh: FaceMeshAnchor): AnchorPoint? {
        val points = listOf(61, 291, 13, 14, 0, 17).mapNotNull { mesh.point(it) }
        if (points.isEmpty()) return null
        return AnchorPoint(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
    }

    private fun FaceMeshAnchor.warpRegion(width: Int, height: Int): Rect {
        val expandX = bounds.width() * .28f
        val expandTop = bounds.height() * .18f
        val expandBottom = bounds.height() * .28f
        return Rect(
            (bounds.left - expandX).toInt().coerceIn(0, width - 1),
            (bounds.top - expandTop).toInt().coerceIn(0, height - 1),
            (bounds.right + expandX).toInt().coerceIn(1, width),
            (bounds.bottom + expandBottom).toInt().coerceIn(1, height)
        )
    }

    private fun FaceMeshAnchor.retouchRegion(width: Int, height: Int): Rect {
        val expandX = bounds.width() * .08f
        val expandY = bounds.height() * .08f
        return Rect(
            (bounds.left - expandX).toInt().coerceIn(0, width - 1),
            (bounds.top - expandY).toInt().coerceIn(0, height - 1),
            (bounds.right + expandX).toInt().coerceIn(1, width),
            (bounds.bottom + expandY).toInt().coerceIn(1, height)
        )
    }

    private fun circleWeight(x: Float, y: Float, center: AnchorPoint, radius: Float): Float {
        if (radius <= 0f) return 0f
        val dx = x - center.x
        val dy = y - center.y
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        val normalized = (distance / radius).coerceIn(0f, 1f)
        val falloff = 1f - normalized
        return falloff * falloff
    }

    private fun edgePreservingSmooth(pixels: IntArray, width: Int, height: Int): IntArray {
        val output = pixels.copyOf()
        val radius = 2
        for (y in 0 until height) {
            for (x in 0 until width) {
                val center = pixels[y * width + x]
                val centerLuma = luma(center)
                var totalWeight = 0f
                var red = 0f
                var green = 0f
                var blue = 0f
                for (dy in -radius..radius) {
                    val sy = (y + dy).coerceIn(0, height - 1)
                    for (dx in -radius..radius) {
                        val sx = (x + dx).coerceIn(0, width - 1)
                        val sample = pixels[sy * width + sx]
                        val spatial = if (dx == 0 && dy == 0) 1.25f else 1f / (1f + abs(dx) + abs(dy))
                        val range = (1f - abs(luma(sample) - centerLuma) / 72f).coerceIn(0f, 1f)
                        val weight = spatial * range * range
                        totalWeight += weight
                        red += Color.red(sample) * weight
                        green += Color.green(sample) * weight
                        blue += Color.blue(sample) * weight
                    }
                }
                if (totalWeight > 0f) {
                    output[y * width + x] = Color.argb(
                        Color.alpha(center),
                        (red / totalWeight).roundToInt().coerceIn(0, 255),
                        (green / totalWeight).roundToInt().coerceIn(0, 255),
                        (blue / totalWeight).roundToInt().coerceIn(0, 255)
                    )
                }
            }
        }
        return output
    }

    private fun smoothPixelAt(pixels: IntArray, width: Int, height: Int, x: Int, y: Int): Int {
        val center = pixels[y * width + x]
        val centerLuma = luma(center)
        var totalWeight = 0f
        var red = 0f
        var green = 0f
        var blue = 0f
        for (dy in -2..2) {
            val sy = (y + dy).coerceIn(0, height - 1)
            for (dx in -2..2) {
                val sx = (x + dx).coerceIn(0, width - 1)
                val sample = pixels[sy * width + sx]
                val spatial = if (dx == 0 && dy == 0) 1.25f else 1f / (1f + abs(dx) + abs(dy))
                val range = (1f - abs(luma(sample) - centerLuma) / 72f).coerceIn(0f, 1f)
                val weight = spatial * range * range
                totalWeight += weight
                red += Color.red(sample) * weight
                green += Color.green(sample) * weight
                blue += Color.blue(sample) * weight
            }
        }
        return if (totalWeight > 0f) {
            Color.argb(
                Color.alpha(center),
                (red / totalWeight).roundToInt().coerceIn(0, 255),
                (green / totalWeight).roundToInt().coerceIn(0, 255),
                (blue / totalWeight).roundToInt().coerceIn(0, 255)
            )
        } else {
            center
        }
    }

    private fun ringAverageColor(pixels: IntArray, width: Int, height: Int, cx: Int, cy: Int, radius: Int): Int {
        var red = 0f
        var green = 0f
        var blue = 0f
        var totalWeight = 0f
        val inner = radius * 1.25f
        val outer = radius * 2.25f
        val left = (cx - outer.roundToInt()).coerceAtLeast(0)
        val right = (cx + outer.roundToInt()).coerceAtMost(width - 1)
        val top = (cy - outer.roundToInt()).coerceAtLeast(0)
        val bottom = (cy + outer.roundToInt()).coerceAtMost(height - 1)
        for (y in top..bottom) {
            for (x in left..right) {
                val distance = sqrt(((x - cx) * (x - cx) + (y - cy) * (y - cy)).toFloat())
                if (distance < inner || distance > outer) continue
                val color = pixels[y * width + x]
                val weight = FaceMaskUtils.skinWeight(color).coerceAtLeast(.25f)
                red += Color.red(color) * weight
                green += Color.green(color) * weight
                blue += Color.blue(color) * weight
                totalWeight += weight
            }
        }
        if (totalWeight <= 0f) return smoothPixelAt(pixels, width, height, cx, cy)
        return Color.rgb(
            (red / totalWeight).roundToInt().coerceIn(0, 255),
            (green / totalWeight).roundToInt().coerceIn(0, 255),
            (blue / totalWeight).roundToInt().coerceIn(0, 255)
        )
    }

    private fun localDetailWeight(pixels: IntArray, width: Int, height: Int, x: Int, y: Int): Float {
        val center = luma(pixels[y * width + x])
        val left = luma(pixels[y * width + (x - 1).coerceAtLeast(0)])
        val right = luma(pixels[y * width + (x + 1).coerceAtMost(width - 1)])
        val top = luma(pixels[(y - 1).coerceAtLeast(0) * width + x])
        val bottom = luma(pixels[(y + 1).coerceAtMost(height - 1) * width + x])
        val edge = max(abs(center - left), max(abs(center - right), max(abs(center - top), abs(center - bottom))))
        return (1f - (edge - 10f).coerceAtLeast(0f) / 56f).coerceIn(.18f, 1f)
    }

    private fun blemishWeight(pixels: IntArray, width: Int, height: Int, x: Int, y: Int, color: Int): Float {
        val redExcess = max(0, Color.red(color) - ((Color.green(color) + Color.blue(color)) / 2) - 18) / 70f
        val center = luma(color)
        var neighborhood = 0f
        var count = 0
        for (dy in -2..2) {
            val sy = (y + dy).coerceIn(0, height - 1)
            for (dx in -2..2) {
                if (dx == 0 && dy == 0) continue
                val sx = (x + dx).coerceIn(0, width - 1)
                neighborhood += luma(pixels[sy * width + sx])
                count++
            }
        }
        val localAverage = neighborhood / count.coerceAtLeast(1)
        val spotContrast = ((localAverage - center) / 38f).coerceIn(0f, 1f)
        return max(redExcess.coerceIn(0f, 1f), spotContrast * .75f).coerceIn(0f, 1f)
    }

    private fun luma(color: Int): Float {
        return .299f * Color.red(color) + .587f * Color.green(color) + .114f * Color.blue(color)
    }

    private fun sampleBilinear(pixels: IntArray, width: Int, height: Int, x: Float, y: Float): Int {
        val clampedX = x.coerceIn(0f, (width - 1).toFloat())
        val clampedY = y.coerceIn(0f, (height - 1).toFloat())
        val x0 = clampedX.toInt().coerceIn(0, width - 1)
        val y0 = clampedY.toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val tx = clampedX - x0
        val ty = clampedY - y0
        val c00 = pixels[y0 * width + x0]
        val c10 = pixels[y0 * width + x1]
        val c01 = pixels[y1 * width + x0]
        val c11 = pixels[y1 * width + x1]
        return Color.argb(
            lerp(lerp(Color.alpha(c00), Color.alpha(c10), tx), lerp(Color.alpha(c01), Color.alpha(c11), tx), ty),
            lerp(lerp(Color.red(c00), Color.red(c10), tx), lerp(Color.red(c01), Color.red(c11), tx), ty),
            lerp(lerp(Color.green(c00), Color.green(c10), tx), lerp(Color.green(c01), Color.green(c11), tx), ty),
            lerp(lerp(Color.blue(c00), Color.blue(c10), tx), lerp(Color.blue(c01), Color.blue(c11), tx), ty)
        )
    }

    private fun lerp(start: Int, end: Int, amount: Float): Int {
        return (start + (end - start) * amount).roundToInt().coerceIn(0, 255)
    }

    private fun blendColors(a: Int, b: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        val r = (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt()
        val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt()
        val blue = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
        return Color.argb(Color.alpha(a), r.coerceIn(0, 255), g.coerceIn(0, 255), blue.coerceIn(0, 255))
    }

    private fun naturalLipTint(color: Int, tint: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        val lipBase = softLightTint(color, tint, t * .72f)
        val r = Color.red(lipBase)
        val g = Color.green(lipBase)
        val b = Color.blue(lipBase)
        val luminance = (.299f * r + .587f * g + .114f * b).coerceIn(0f, 255f)
        val sat = 1f + t * .22f
        return Color.argb(
            Color.alpha(color),
            (luminance + (r - luminance) * sat).roundToInt().coerceIn(0, 255),
            (luminance + (g - luminance) * sat).roundToInt().coerceIn(0, 255),
            (luminance + (b - luminance) * sat).roundToInt().coerceIn(0, 255)
        )
    }

    private fun softLightTint(color: Int, tint: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        return Color.argb(
            Color.alpha(color),
            softLightChannel(Color.red(color), Color.red(tint), t),
            softLightChannel(Color.green(color), Color.green(tint), t),
            softLightChannel(Color.blue(color), Color.blue(tint), t)
        )
    }

    private fun softLightChannel(base: Int, blend: Int, amount: Float): Int {
        val b = base / 255f
        val s = blend / 255f
        val soft = if (s < .5f) {
            b - (1f - 2f * s) * b * (1f - b)
        } else {
            b + (2f * s - 1f) * (kotlin.math.sqrt(b) - b)
        }
        return ((b + (soft - b) * amount) * 255f).roundToInt().coerceIn(0, 255)
    }

    private fun contourShade(color: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        val shade = Color.rgb(84, 58, 48)
        val tinted = softLightTint(color, shade, t)
        return Color.argb(
            Color.alpha(color),
            (Color.red(tinted) * (1f - t * .12f)).roundToInt().coerceIn(0, 255),
            (Color.green(tinted) * (1f - t * .1f)).roundToInt().coerceIn(0, 255),
            (Color.blue(tinted) * (1f - t * .06f)).roundToInt().coerceIn(0, 255)
        )
    }

    private fun eyeShadowTint(color: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        val shadow = Color.rgb(103, 71, 65)
        val tinted = softLightTint(color, shadow, t)
        return Color.argb(
            Color.alpha(color),
            (Color.red(tinted) * (1f - t * .05f)).roundToInt().coerceIn(0, 255),
            (Color.green(tinted) * (1f - t * .045f)).roundToInt().coerceIn(0, 255),
            (Color.blue(tinted) * (1f - t * .025f)).roundToInt().coerceIn(0, 255)
        )
    }

    private fun eyeLineTint(color: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        val liner = Color.rgb(42, 34, 32)
        val tinted = softLightTint(color, liner, t)
        return Color.argb(
            Color.alpha(color),
            (Color.red(tinted) * (1f - t * .18f)).roundToInt().coerceIn(0, 255),
            (Color.green(tinted) * (1f - t * .17f)).roundToInt().coerceIn(0, 255),
            (Color.blue(tinted) * (1f - t * .15f)).roundToInt().coerceIn(0, 255)
        )
    }

    private fun reduceRedSpots(color: Int, amount: Float): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val excessRed = max(0, r - ((g + b) / 2) - 24)
        if (excessRed == 0) return color
        val correction = (excessRed * amount.coerceIn(0f, 1f)).toInt()
        return Color.argb(
            Color.alpha(color),
            (r - correction).coerceIn(0, 255),
            (g + correction / 5).coerceIn(0, 255),
            b
        )
    }
}
