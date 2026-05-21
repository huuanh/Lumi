package com.lumi.retouch

import android.content.res.AssetManager
import org.json.JSONObject

data class FeatureFlags(
    val gpuPreview: Boolean = true,
    val studioSegmentation: Boolean = true,
    val poseBodyTune: Boolean = true,
    val localHeal: Boolean = true,
    val benchmarkPanel: Boolean = true,
    val remotePresetConfig: Boolean = false,
    val projectSessions: Boolean = true,
    val batchExport: Boolean = true,
    val transparentCutout: Boolean = true,
    val analyticsEvents: Boolean = true,
    val monetizationGates: Boolean = false
)

data class FeatureConfig(
    val version: String,
    val presetPackPath: String,
    val flags: FeatureFlags
)

object FeatureConfigLoader {
    private const val DEFAULT_PATH = "config/feature_flags.json"
    private const val DEFAULT_PRESET_PATH = "presets/portrait_presets.json"

    fun loadFromAssets(assets: AssetManager, path: String = DEFAULT_PATH): FeatureConfig {
        return runCatching {
            assets.open(path).bufferedReader().use { parse(it.readText()) }
        }.getOrElse {
            fallbackConfig()
        }
    }

    fun parse(json: String): FeatureConfig {
        val root = JSONObject(json)
        val flags = root.optJSONObject("flags") ?: JSONObject()
        return FeatureConfig(
            version = root.optString("version", "builtin-flags"),
            presetPackPath = root.optString("presetPackPath", DEFAULT_PRESET_PATH).ifBlank { DEFAULT_PRESET_PATH },
            flags = FeatureFlags(
                gpuPreview = flags.optBoolean("gpuPreview", true),
                studioSegmentation = flags.optBoolean("studioSegmentation", true),
                poseBodyTune = flags.optBoolean("poseBodyTune", true),
                localHeal = flags.optBoolean("localHeal", true),
                benchmarkPanel = flags.optBoolean("benchmarkPanel", true),
                remotePresetConfig = flags.optBoolean("remotePresetConfig", false),
                projectSessions = flags.optBoolean("projectSessions", true),
                batchExport = flags.optBoolean("batchExport", true),
                transparentCutout = flags.optBoolean("transparentCutout", true),
                analyticsEvents = flags.optBoolean("analyticsEvents", true),
                monetizationGates = flags.optBoolean("monetizationGates", false)
            )
        )
    }

    fun fallbackConfig(): FeatureConfig {
        return FeatureConfig(
            version = "builtin-flags",
            presetPackPath = DEFAULT_PRESET_PATH,
            flags = FeatureFlags()
        )
    }
}
