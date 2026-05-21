package com.lumi.retouch

import android.content.res.AssetManager
import org.json.JSONArray
import org.json.JSONObject

data class PresetPack(
    val version: String,
    val presets: List<TemplatePreset>
)

object PresetPackLoader {
    private const val DEFAULT_PATH = "presets/portrait_presets.json"

    fun loadFromAssets(assets: AssetManager, path: String = DEFAULT_PATH): PresetPack {
        return runCatching {
            assets.open(path).bufferedReader().use { parse(it.readText()) }
        }.getOrElse {
            fallbackPack()
        }
    }

    fun parse(json: String): PresetPack {
        val root = JSONObject(json)
        val version = root.optString("version", "builtin")
        val presets = root.getJSONArray("presets").toTemplatePresets()
        require(presets.isNotEmpty()) { "Preset pack must include at least one preset" }
        require(presets.map { it.id }.toSet().size == presets.size) { "Preset ids must be unique" }
        return PresetPack(version = version, presets = presets)
    }

    fun fallbackPack(): PresetPack = PresetPack(version = "builtin", presets = templatePresets)

    private fun JSONArray.toTemplatePresets(): List<TemplatePreset> {
        return List(length()) { index ->
            val item = getJSONObject(index)
            TemplatePreset(
                id = item.getString("id"),
                title = item.getString("title"),
                subtitle = item.optString("subtitle"),
                recipe = item.getJSONObject("recipe").toRecipe()
            )
        }
    }

    private fun JSONObject.toRecipe(): EditRecipe {
        return EditRecipe(
            rotationDegrees = optInt("rotationDegrees", 0),
            flipHorizontal = optBoolean("flipHorizontal", false),
            cropMode = enumValue<CropMode>("cropMode", CropMode.Original),
            exposure = optFloat("exposure"),
            contrast = optFloat("contrast"),
            saturation = optFloat("saturation"),
            warmth = optFloat("warmth"),
            sharpen = optFloat("sharpen"),
            vignette = optFloat("vignette"),
            skinSmooth = optFloat("skinSmooth"),
            blemishSoften = optFloat("blemishSoften"),
            eyeBright = optFloat("eyeBright"),
            teethWhite = optFloat("teethWhite"),
            faceSlim = optFloat("faceSlim"),
            eyeScale = optFloat("eyeScale"),
            noseSlim = optFloat("noseSlim"),
            lipPlump = optFloat("lipPlump"),
            bodyTune = optFloat("bodyTune"),
            lipOpacity = optFloat("lipOpacity"),
            blushOpacity = optFloat("blushOpacity"),
            contour = optFloat("contour"),
            eyeShadow = optFloat("eyeShadow"),
            eyeLine = optFloat("eyeLine"),
            makeupStrength = optFloat("makeupStrength", 1f),
            lipPrecision = optFloat("lipPrecision", 0.5f),
            blushSpread = optFloat("blushSpread", 0.5f),
            lipColor = optColor("lipColor", 0xFFB63B4A.toInt()),
            filterLook = enumValue<FilterLook>("filterLook", FilterLook.Clean),
            cutoutStudio = optBoolean("cutoutStudio", false),
            studioBackdrop = enumValue<StudioBackdrop>("studioBackdrop", StudioBackdrop.SoftGray),
            studioStrength = optFloat("studioStrength", 1f),
            matteRefine = optFloat("matteRefine", 0.55f),
            transparentBackground = optBoolean("transparentBackground", false),
            portraitRelight = optFloat("portraitRelight"),
            catchlight = optFloat("catchlight"),
            underEyeLift = optFloat("underEyeLift"),
            healBrushRadius = optFloat("healBrushRadius", 0.025f),
            healBrushStrength = optFloat("healBrushStrength", 0.72f),
            watermarkEnabled = optBoolean("watermarkEnabled", false),
            exportQuality = optInt("exportQuality", 94).coerceIn(70, 100)
        )
    }

    private fun JSONObject.optFloat(name: String, fallback: Float = 0f): Float {
        return if (has(name)) optDouble(name).toFloat() else fallback
    }

    private fun JSONObject.optColor(name: String, fallback: Int): Int {
        if (!has(name)) return fallback
        val raw = optString(name)
        if (raw.isBlank()) return fallback
        val hex = raw.removePrefix("#")
        return runCatching {
            val normalized = if (hex.length == 6) "FF$hex" else hex
            normalized.toLong(16).toInt()
        }.getOrDefault(fallback)
    }

    private inline fun <reified T : Enum<T>> JSONObject.enumValue(name: String, fallback: T): T {
        if (!has(name)) return fallback
        val raw = optString(name)
        return enumValues<T>().firstOrNull {
            it.name.equals(raw, ignoreCase = true) || enumLabel(it).equals(raw, ignoreCase = true)
        } ?: fallback
    }

    private fun enumLabel(value: Enum<*>): String {
        return when (value) {
            is FilterLook -> value.label
            is CropMode -> value.label
            is StudioBackdrop -> value.label
            else -> value.name
        }
    }
}
