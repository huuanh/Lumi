package com.lumi.retouch

import androidx.annotation.ColorInt
import android.graphics.RectF
import android.net.Uri

enum class ToolPanel(val title: String) {
    Transform("Transform"),
    Adjust("Adjust"),
    Beauty("Beauty"),
    Makeup("Makeup"),
    Templates("Templates"),
    Export("Export")
}

enum class FilterLook(val label: String) {
    Clean("Clean"),
    Film("Film"),
    Cream("Cream"),
    Korean("Korean"),
    CoolWhite("Cool white"),
    WarmPortrait("Warm portrait"),
    Neo("Neo")
}

enum class CropMode(val label: String) {
    Original("Original"),
    Square("1:1"),
    Portrait("4:5"),
    Story("9:16")
}

enum class DebugOverlayMode(val label: String) {
    Off("Debug off"),
    Mesh("Mesh"),
    Masks("Masks")
}

enum class ExportFormat(val label: String, val mimeType: String, val extension: String) {
    Png("PNG", "image/png", "png"),
    Jpeg("JPEG", "image/jpeg", "jpg")
}

enum class StudioBackdrop(val label: String) {
    SoftGray("Soft gray"),
    WarmCream("Warm cream"),
    CoolWhite("Cool white"),
    Peach("Peach"),
    Slate("Slate")
}

enum class DeviceTier(val label: String) {
    Low("Low"),
    Mid("Mid"),
    High("High")
}

enum class HealMode(val label: String) {
    Heal("Heal"),
    Clone("Clone"),
    Restore("Restore"),
    Erase("Erase")
}

data class EditHistoryItem(
    val title: String,
    val recipe: EditRecipe
)

data class HealPoint(
    val x: Float,
    val y: Float,
    val radius: Float = 0.025f,
    val strength: Float = 0.72f,
    val mode: HealMode = HealMode.Heal
)

data class EditRecipe(
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val cropMode: CropMode = CropMode.Original,
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val sharpen: Float = 0f,
    val vignette: Float = 0f,
    val skinSmooth: Float = 0f,
    val blemishSoften: Float = 0f,
    val eyeBright: Float = 0f,
    val teethWhite: Float = 0f,
    val faceSlim: Float = 0f,
    val eyeScale: Float = 0f,
    val noseSlim: Float = 0f,
    val lipPlump: Float = 0f,
    val bodyTune: Float = 0f,
    val lipOpacity: Float = 0f,
    val blushOpacity: Float = 0f,
    val contour: Float = 0f,
    val eyeShadow: Float = 0f,
    val eyeLine: Float = 0f,
    val makeupStrength: Float = 1f,
    val lipPrecision: Float = 0.5f,
    val blushSpread: Float = 0.5f,
    @ColorInt val lipColor: Int = 0xFFB63B4A.toInt(),
    val filterLook: FilterLook = FilterLook.Clean,
    val cutoutStudio: Boolean = false,
    val studioBackdrop: StudioBackdrop = StudioBackdrop.SoftGray,
    val studioStrength: Float = 1f,
    val matteRefine: Float = 0.55f,
    val transparentBackground: Boolean = false,
    val portraitRelight: Float = 0f,
    val catchlight: Float = 0f,
    val underEyeLift: Float = 0f,
    val healBrushRadius: Float = 0.025f,
    val healBrushStrength: Float = 0.72f,
    val watermarkEnabled: Boolean = false,
    val exportQuality: Int = 94,
    val healPoints: List<HealPoint> = emptyList()
) {
    fun toShareText(): String {
        return """
            {
              "exposure": $exposure,
              "rotationDegrees": $rotationDegrees,
              "flipHorizontal": $flipHorizontal,
              "cropMode": "${cropMode.label}",
              "contrast": $contrast,
              "saturation": $saturation,
              "warmth": $warmth,
              "skinSmooth": $skinSmooth,
              "faceSlim": $faceSlim,
              "eyeScale": $eyeScale,
              "noseSlim": $noseSlim,
              "lipPlump": $lipPlump,
              "bodyTune": $bodyTune,
              "lipOpacity": $lipOpacity,
              "blushOpacity": $blushOpacity,
              "eyeShadow": $eyeShadow,
              "eyeLine": $eyeLine,
              "makeupStrength": $makeupStrength,
              "lipPrecision": $lipPrecision,
              "blushSpread": $blushSpread,
              "filterLook": "${filterLook.label}",
              "studioBackdrop": "${studioBackdrop.label}",
              "studioStrength": $studioStrength,
              "matteRefine": $matteRefine,
              "transparentBackground": $transparentBackground,
              "portraitRelight": $portraitRelight,
              "catchlight": $catchlight,
              "underEyeLift": $underEyeLift,
              "healBrushRadius": $healBrushRadius,
              "healBrushStrength": $healBrushStrength,
              "watermarkEnabled": $watermarkEnabled,
              "exportQuality": $exportQuality,
              "healPoints": ${healPoints.size},
              "presetVersion": "${PortraitPipeline.VERSION}",
              "pipelineStages": "${PortraitPipeline.stages.joinToString(" > ") { it.label }}",
              "cutoutStudio": $cutoutStudio
            }
        """.trimIndent()
    }
}

data class ExportedAsset(
    val uri: Uri,
    val displayName: String,
    val format: ExportFormat,
    val presetMetadata: String
)

data class TemplatePreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val recipe: EditRecipe
)

data class MakeupPreset(
    val id: String,
    val title: String,
    val lipColor: Int,
    val apply: (EditRecipe) -> EditRecipe
)

val makeupPresets = listOf(
    MakeupPreset(
        id = "natural",
        title = "Natural",
        lipColor = 0xFFB65A5A.toInt()
    ) {
        it.copy(
            lipColor = 0xFFB65A5A.toInt(),
            makeupStrength = 1f,
            lipOpacity = 0.28f,
            lipPrecision = 0.72f,
            blushOpacity = 0.16f,
            blushSpread = 0.42f,
            contour = 0.08f,
            eyeShadow = 0.08f,
            eyeLine = 0.06f,
            eyeBright = 0.16f,
            eyeScale = 0.08f
        )
    },
    MakeupPreset(
        id = "fresh",
        title = "Fresh",
        lipColor = 0xFFE05F6B.toInt()
    ) {
        it.copy(
            lipColor = 0xFFE05F6B.toInt(),
            makeupStrength = 1f,
            lipOpacity = 0.38f,
            lipPrecision = 0.62f,
            blushOpacity = 0.28f,
            blushSpread = 0.58f,
            contour = 0.05f,
            eyeShadow = 0.1f,
            eyeLine = 0.08f,
            eyeBright = 0.22f,
            eyeScale = 0.1f,
            warmth = 0.06f
        )
    },
    MakeupPreset(
        id = "glam",
        title = "Glam",
        lipColor = 0xFF8F2A40.toInt()
    ) {
        it.copy(
            lipColor = 0xFF8F2A40.toInt(),
            makeupStrength = 1f,
            lipOpacity = 0.56f,
            lipPrecision = 0.8f,
            lipPlump = 0.18f,
            blushOpacity = 0.22f,
            blushSpread = 0.48f,
            contour = 0.26f,
            eyeShadow = 0.22f,
            eyeLine = 0.16f,
            eyeBright = 0.22f,
            eyeScale = 0.18f,
            noseSlim = 0.14f
        )
    },
    MakeupPreset(
        id = "cool",
        title = "Cool",
        lipColor = 0xFF8E4968.toInt()
    ) {
        it.copy(
            lipColor = 0xFF8E4968.toInt(),
            makeupStrength = 1f,
            lipOpacity = 0.42f,
            lipPrecision = 0.72f,
            blushOpacity = 0.18f,
            blushSpread = 0.36f,
            contour = 0.18f,
            eyeShadow = 0.16f,
            eyeLine = 0.12f,
            eyeBright = 0.2f,
            saturation = 0.03f,
            warmth = -0.08f
        )
    },
    MakeupPreset(
        id = "warm",
        title = "Warm",
        lipColor = 0xFFCF6F52.toInt()
    ) {
        it.copy(
            lipColor = 0xFFCF6F52.toInt(),
            makeupStrength = 1f,
            lipOpacity = 0.44f,
            lipPrecision = 0.64f,
            blushOpacity = 0.26f,
            blushSpread = 0.54f,
            contour = 0.12f,
            eyeShadow = 0.18f,
            eyeLine = 0.1f,
            eyeBright = 0.18f,
            warmth = 0.12f,
            saturation = 0.05f
        )
    }
)

data class FaceAnchor(
    val bounds: RectF,
    val leftEye: AnchorPoint? = null,
    val rightEye: AnchorPoint? = null,
    val noseBase: AnchorPoint? = null,
    val mouthLeft: AnchorPoint? = null,
    val mouthRight: AnchorPoint? = null,
    val mouthBottom: AnchorPoint? = null,
    val leftCheek: AnchorPoint? = null,
    val rightCheek: AnchorPoint? = null,
    val smilingProbability: Float? = null,
    val leftEyeOpenProbability: Float? = null,
    val rightEyeOpenProbability: Float? = null
) {
    val mouthCenter: AnchorPoint?
        get() {
            val left = mouthLeft
            val right = mouthRight
            val bottom = mouthBottom
            return when {
                left != null && right != null -> AnchorPoint((left.x + right.x) / 2f, (left.y + right.y) / 2f)
                bottom != null -> bottom
                else -> null
            }
        }
}

data class AnchorPoint(val x: Float, val y: Float)

data class FaceMeshAnchor(
    val bounds: RectF,
    val points: List<AnchorPoint>
) {
    fun point(index: Int): AnchorPoint? = points.getOrNull(index)
}

data class PersonSegmentationMask(
    val width: Int,
    val height: Int,
    val confidences: FloatArray
) {
    fun confidenceAt(x: Float, y: Float, targetWidth: Int, targetHeight: Int): Float {
        if (width <= 0 || height <= 0 || targetWidth <= 0 || targetHeight <= 0 || confidences.isEmpty()) return 0f
        val sx = (x / targetWidth.toFloat() * (width - 1)).coerceIn(0f, (width - 1).toFloat())
        val sy = (y / targetHeight.toFloat() * (height - 1)).coerceIn(0f, (height - 1).toFloat())
        val x0 = sx.toInt().coerceIn(0, width - 1)
        val y0 = sy.toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val tx = sx - x0
        val ty = sy - y0
        val c00 = sample(x0, y0)
        val c10 = sample(x1, y0)
        val c01 = sample(x0, y1)
        val c11 = sample(x1, y1)
        val top = c00 + (c10 - c00) * tx
        val bottom = c01 + (c11 - c01) * tx
        return (top + (bottom - top) * ty).coerceIn(0f, 1f)
    }

    fun refinedForStudio(): PersonSegmentationMask {
        if (width <= 2 || height <= 2 || confidences.size != width * height) return this
        val blurred = FloatArray(confidences.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0f
                var weight = 0f
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = (x + dx).coerceIn(0, width - 1)
                        val ny = (y + dy).coerceIn(0, height - 1)
                        val w = when {
                            dx == 0 && dy == 0 -> 4f
                            dx == 0 || dy == 0 -> 2f
                            else -> 1f
                        }
                        sum += confidences[ny * width + nx].coerceIn(0f, 1f) * w
                        weight += w
                    }
                }
                blurred[y * width + x] = sum / weight.coerceAtLeast(1f)
            }
        }

        val refined = FloatArray(confidences.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val center = blurred[y * width + x]
                var minNeighbor = 1f
                var maxNeighbor = 0f
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = (x + dx).coerceIn(0, width - 1)
                        val ny = (y + dy).coerceIn(0, height - 1)
                        val v = blurred[ny * width + nx]
                        minNeighbor = kotlin.math.min(minNeighbor, v)
                        maxNeighbor = kotlin.math.max(maxNeighbor, v)
                    }
                }
                val edge = (maxNeighbor - minNeighbor).coerceIn(0f, 1f)
                val matte = when {
                    center >= .92f -> 1f
                    center <= .035f -> 0f
                    else -> smoothstep(.12f, .82f, center)
                }
                val edgeFeather = if (edge > .18f) {
                    center * .42f + matte * .58f
                } else {
                    matte
                }
                refined[y * width + x] = edgeFeather.coerceIn(0f, 1f)
            }
        }
        return copy(confidences = refined)
    }

    private fun sample(x: Int, y: Int): Float {
        return confidences.getOrNull(y * width + x)?.coerceIn(0f, 1f) ?: 0f
    }

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0).coerceAtLeast(.0001f)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

data class BodyPoseAnchor(
    val leftShoulder: AnchorPoint,
    val rightShoulder: AnchorPoint,
    val leftHip: AnchorPoint,
    val rightHip: AnchorPoint,
    val confidence: Float
)

data class DeviceCapabilityProfile(
    val tier: DeviceTier,
    val gpuPreview: Boolean,
    val maxPreviewSide: Int,
    val maxExportSide: Int,
    val notes: String
)

data class ProjectSession(
    val id: String,
    val originalUri: String,
    val imageName: String,
    val recipeJson: String,
    val thumbnailChecksum: Long,
    val exportHistory: List<String>,
    val updatedAtMillis: Long
)

val templatePresets = listOf(
    TemplatePreset(
        id = "daily-clean",
        title = "Daily clean",
        subtitle = "Bright skin, controlled contrast",
        recipe = EditRecipe(exposure = 0.08f, contrast = 0.08f, saturation = 0.06f, skinSmooth = 0.22f)
    ),
    TemplatePreset(
        id = "portrait-soft",
        title = "Portrait soft",
        subtitle = "Beauty retouch and light makeup",
        recipe = EditRecipe(
            exposure = 0.05f,
            warmth = 0.08f,
            skinSmooth = 0.42f,
            blemishSoften = 0.35f,
            eyeBright = 0.24f,
            eyeScale = 0.18f,
            lipPlump = 0.18f,
            lipOpacity = 0.36f,
            blushOpacity = 0.2f,
            filterLook = FilterLook.Korean
        )
    ),
    TemplatePreset(
        id = "street-snap",
        title = "Street snap",
        subtitle = "Film color, texture, social-ready",
        recipe = EditRecipe(
            contrast = 0.22f,
            saturation = 0.18f,
            warmth = -0.08f,
            sharpen = 0.25f,
            vignette = 0.28f,
            filterLook = FilterLook.Film
        )
    ),
    TemplatePreset(
        id = "cool-white",
        title = "Cool white",
        subtitle = "Clear white skin and soft lips",
        recipe = EditRecipe(
            exposure = 0.08f,
            contrast = 0.04f,
            saturation = -0.03f,
            warmth = -0.1f,
            skinSmooth = 0.28f,
            blemishSoften = 0.2f,
            eyeBright = 0.18f,
            lipOpacity = 0.24f,
            blushOpacity = 0.1f,
            filterLook = FilterLook.CoolWhite
        )
    ),
    TemplatePreset(
        id = "warm-portrait",
        title = "Warm portrait",
        subtitle = "Warm skin tone and gentle makeup",
        recipe = EditRecipe(
            exposure = 0.06f,
            contrast = 0.08f,
            saturation = 0.04f,
            warmth = 0.1f,
            skinSmooth = 0.24f,
            blemishSoften = 0.18f,
            lipOpacity = 0.28f,
            blushOpacity = 0.18f,
            filterLook = FilterLook.WarmPortrait
        )
    ),
    TemplatePreset(
        id = "commerce-cutout",
        title = "Commerce cutout",
        subtitle = "Subject lift and studio background",
        recipe = EditRecipe(
            exposure = 0.12f,
            contrast = 0.06f,
            skinSmooth = 0.16f,
            sharpen = 0.18f,
            cutoutStudio = true,
            studioBackdrop = StudioBackdrop.WarmCream
        )
    ),
    TemplatePreset(
        id = "profile-headshot",
        title = "Profile headshot",
        subtitle = "Clean face, cool white studio",
        recipe = EditRecipe(
            exposure = 0.1f,
            contrast = 0.07f,
            saturation = -0.04f,
            warmth = -0.06f,
            skinSmooth = 0.26f,
            blemishSoften = 0.2f,
            eyeBright = 0.22f,
            teethWhite = 0.12f,
            sharpen = 0.16f,
            filterLook = FilterLook.CoolWhite,
            cutoutStudio = true,
            studioBackdrop = StudioBackdrop.CoolWhite,
            studioStrength = 0.72f
        )
    ),
    TemplatePreset(
        id = "product-portrait",
        title = "Product portrait",
        subtitle = "Warm commerce tone and cutout",
        recipe = EditRecipe(
            exposure = 0.12f,
            contrast = 0.1f,
            saturation = 0.03f,
            warmth = 0.06f,
            skinSmooth = 0.18f,
            blemishSoften = 0.16f,
            lipOpacity = 0.18f,
            blushOpacity = 0.08f,
            sharpen = 0.22f,
            filterLook = FilterLook.WarmPortrait,
            cutoutStudio = true,
            studioBackdrop = StudioBackdrop.WarmCream,
            studioStrength = 0.84f
        )
    ),
    TemplatePreset(
        id = "studio-slate",
        title = "Studio slate",
        subtitle = "Dark editorial profile look",
        recipe = EditRecipe(
            exposure = 0.03f,
            contrast = 0.2f,
            saturation = -0.02f,
            warmth = -0.04f,
            skinSmooth = 0.2f,
            blemishSoften = 0.14f,
            eyeBright = 0.16f,
            contour = 0.12f,
            sharpen = 0.2f,
            filterLook = FilterLook.Neo,
            cutoutStudio = true,
            studioBackdrop = StudioBackdrop.Slate,
            studioStrength = 0.76f
        )
    ),
    TemplatePreset(
        id = "shape-studio",
        title = "Shape studio",
        subtitle = "Profile tone with subtle body tune",
        recipe = EditRecipe(
            exposure = 0.08f,
            contrast = 0.09f,
            saturation = -0.02f,
            warmth = -0.03f,
            skinSmooth = 0.22f,
            blemishSoften = 0.18f,
            eyeBright = 0.18f,
            faceSlim = 0.12f,
            noseSlim = 0.08f,
            bodyTune = 0.18f,
            contour = 0.1f,
            sharpen = 0.18f,
            filterLook = FilterLook.CoolWhite,
            cutoutStudio = true,
            studioBackdrop = StudioBackdrop.SoftGray,
            studioStrength = 0.68f
        )
    )
)
