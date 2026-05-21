package com.lumi.retouch

import org.json.JSONArray
import org.json.JSONObject

object RecipeCodec {
    fun encode(recipe: EditRecipe): String {
        return JSONObject()
            .put("rotationDegrees", recipe.rotationDegrees)
            .put("flipHorizontal", recipe.flipHorizontal)
            .put("cropMode", recipe.cropMode.name)
            .put("exposure", recipe.exposure)
            .put("contrast", recipe.contrast)
            .put("saturation", recipe.saturation)
            .put("warmth", recipe.warmth)
            .put("sharpen", recipe.sharpen)
            .put("vignette", recipe.vignette)
            .put("skinSmooth", recipe.skinSmooth)
            .put("blemishSoften", recipe.blemishSoften)
            .put("eyeBright", recipe.eyeBright)
            .put("teethWhite", recipe.teethWhite)
            .put("faceSlim", recipe.faceSlim)
            .put("eyeScale", recipe.eyeScale)
            .put("noseSlim", recipe.noseSlim)
            .put("lipPlump", recipe.lipPlump)
            .put("bodyTune", recipe.bodyTune)
            .put("lipOpacity", recipe.lipOpacity)
            .put("blushOpacity", recipe.blushOpacity)
            .put("contour", recipe.contour)
            .put("eyeShadow", recipe.eyeShadow)
            .put("eyeLine", recipe.eyeLine)
            .put("makeupStrength", recipe.makeupStrength)
            .put("lipPrecision", recipe.lipPrecision)
            .put("blushSpread", recipe.blushSpread)
            .put("lipColor", recipe.lipColor)
            .put("filterLook", recipe.filterLook.name)
            .put("cutoutStudio", recipe.cutoutStudio)
            .put("studioBackdrop", recipe.studioBackdrop.name)
            .put("studioStrength", recipe.studioStrength)
            .put("matteRefine", recipe.matteRefine)
            .put("transparentBackground", recipe.transparentBackground)
            .put("portraitRelight", recipe.portraitRelight)
            .put("catchlight", recipe.catchlight)
            .put("underEyeLift", recipe.underEyeLift)
            .put("healBrushRadius", recipe.healBrushRadius)
            .put("healBrushStrength", recipe.healBrushStrength)
            .put("watermarkEnabled", recipe.watermarkEnabled)
            .put("exportQuality", recipe.exportQuality)
            .put("healPoints", JSONArray(recipe.healPoints.map {
                JSONObject()
                    .put("x", it.x)
                    .put("y", it.y)
                    .put("radius", it.radius)
                    .put("strength", it.strength)
                    .put("mode", it.mode.name)
            }))
            .toString()
    }

    fun decode(json: String): EditRecipe {
        val root = JSONObject(json)
        val points = root.optJSONArray("healPoints")
        return EditRecipe(
            rotationDegrees = root.optInt("rotationDegrees", 0),
            flipHorizontal = root.optBoolean("flipHorizontal", false),
            cropMode = enumValue(root.optString("cropMode"), CropMode.Original),
            exposure = root.optFloat("exposure"),
            contrast = root.optFloat("contrast"),
            saturation = root.optFloat("saturation"),
            warmth = root.optFloat("warmth"),
            sharpen = root.optFloat("sharpen"),
            vignette = root.optFloat("vignette"),
            skinSmooth = root.optFloat("skinSmooth"),
            blemishSoften = root.optFloat("blemishSoften"),
            eyeBright = root.optFloat("eyeBright"),
            teethWhite = root.optFloat("teethWhite"),
            faceSlim = root.optFloat("faceSlim"),
            eyeScale = root.optFloat("eyeScale"),
            noseSlim = root.optFloat("noseSlim"),
            lipPlump = root.optFloat("lipPlump"),
            bodyTune = root.optFloat("bodyTune"),
            lipOpacity = root.optFloat("lipOpacity"),
            blushOpacity = root.optFloat("blushOpacity"),
            contour = root.optFloat("contour"),
            eyeShadow = root.optFloat("eyeShadow"),
            eyeLine = root.optFloat("eyeLine"),
            makeupStrength = root.optFloat("makeupStrength", 1f),
            lipPrecision = root.optFloat("lipPrecision", 0.5f),
            blushSpread = root.optFloat("blushSpread", 0.5f),
            lipColor = root.optInt("lipColor", 0xFFB63B4A.toInt()),
            filterLook = enumValue(root.optString("filterLook"), FilterLook.Clean),
            cutoutStudio = root.optBoolean("cutoutStudio", false),
            studioBackdrop = enumValue(root.optString("studioBackdrop"), StudioBackdrop.SoftGray),
            studioStrength = root.optFloat("studioStrength", 1f),
            matteRefine = root.optFloat("matteRefine", 0.55f),
            transparentBackground = root.optBoolean("transparentBackground", false),
            portraitRelight = root.optFloat("portraitRelight"),
            catchlight = root.optFloat("catchlight"),
            underEyeLift = root.optFloat("underEyeLift"),
            healBrushRadius = root.optFloat("healBrushRadius", 0.025f),
            healBrushStrength = root.optFloat("healBrushStrength", 0.72f),
            watermarkEnabled = root.optBoolean("watermarkEnabled", false),
            exportQuality = root.optInt("exportQuality", 94).coerceIn(70, 100),
            healPoints = List(points?.length() ?: 0) { index ->
                val item = points!!.getJSONObject(index)
                HealPoint(
                    x = item.optFloat("x"),
                    y = item.optFloat("y"),
                    radius = item.optFloat("radius", 0.025f),
                    strength = item.optFloat("strength", 0.72f),
                    mode = enumValue(item.optString("mode"), HealMode.Heal)
                )
            }
        )
    }

    private fun JSONObject.optFloat(name: String, fallback: Float = 0f): Float {
        return if (has(name)) optDouble(name).toFloat() else fallback
    }

    private inline fun <reified T : Enum<T>> enumValue(raw: String, fallback: T): T {
        return enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: fallback
    }
}
