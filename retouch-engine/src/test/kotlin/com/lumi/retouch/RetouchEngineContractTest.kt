package com.lumi.retouch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetouchEngineContractTest {
    @Test
    fun templateIdsStayUnique() {
        val ids = templatePresets.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.contains("shape-studio"))
    }

    @Test
    fun shareMetadataIncludesPipelineAndPremiumFields() {
        val recipe = EditRecipe(
            cutoutStudio = true,
            bodyTune = 0.18f,
            healPoints = listOf(HealPoint(x = 0.5f, y = 0.5f))
        )

        val metadata = recipe.toShareText()

        assertTrue(metadata.contains(PortraitPipeline.VERSION))
        assertTrue(metadata.contains("transform > base color > skin retouch > face warp > makeup > final tone"))
        assertTrue(metadata.contains("\"cutoutStudio\": true"))
        assertTrue(metadata.contains("\"bodyTune\": 0.18"))
        assertTrue(metadata.contains("\"healPoints\": 1"))
    }

    @Test
    fun withoutTransformKeepsRetouchValuesOnly() {
        val recipe = EditRecipe(
            rotationDegrees = 90,
            flipHorizontal = true,
            cropMode = CropMode.Square,
            skinSmooth = 0.35f,
            lipOpacity = 0.22f
        )

        val prepared = RetouchEngine.withoutTransform(recipe)

        assertEquals(0, prepared.rotationDegrees)
        assertEquals(false, prepared.flipHorizontal)
        assertEquals(CropMode.Original, prepared.cropMode)
        assertEquals(0.35f, prepared.skinSmooth)
        assertEquals(0.22f, prepared.lipOpacity)
    }

    @Test
    fun presetPackParserKeepsPremiumFields() {
        val pack = PresetPackLoader.parse(
            """
            {
              "version": "test-pack",
              "presets": [
                {
                  "id": "shape-studio",
                  "title": "Shape studio",
                  "subtitle": "Profile tone",
                  "recipe": {
                    "filterLook": "CoolWhite",
                    "studioBackdrop": "SoftGray",
                    "cutoutStudio": true,
                    "studioStrength": 0.68,
                    "bodyTune": 0.18,
                    "lipColor": "#FF8F2A40"
                  }
                }
              ]
            }
            """.trimIndent()
        )

        val recipe = pack.presets.single().recipe

        assertEquals("test-pack", pack.version)
        assertEquals(FilterLook.CoolWhite, recipe.filterLook)
        assertEquals(StudioBackdrop.SoftGray, recipe.studioBackdrop)
        assertEquals(true, recipe.cutoutStudio)
        assertEquals(0.68f, recipe.studioStrength)
        assertEquals(0.18f, recipe.bodyTune)
        assertEquals(0xFF8F2A40.toInt(), recipe.lipColor)
    }

    @Test
    fun featureConfigParserKeepsFlagsAndPresetPath() {
        val config = FeatureConfigLoader.parse(
            """
            {
              "version": "test-config",
              "presetPackPath": "presets/test.json",
              "flags": {
                "gpuPreview": false,
                "studioSegmentation": true,
                "poseBodyTune": false,
                "localHeal": true,
                "benchmarkPanel": true,
                "remotePresetConfig": true
              }
            }
            """.trimIndent()
        )

        assertEquals("test-config", config.version)
        assertEquals("presets/test.json", config.presetPackPath)
        assertEquals(false, config.flags.gpuPreview)
        assertEquals(true, config.flags.studioSegmentation)
        assertEquals(false, config.flags.poseBodyTune)
        assertEquals(true, config.flags.localHeal)
        assertEquals(true, config.flags.benchmarkPanel)
        assertEquals(true, config.flags.remotePresetConfig)
    }
}
