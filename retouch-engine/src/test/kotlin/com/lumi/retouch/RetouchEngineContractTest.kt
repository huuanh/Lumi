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
            portraitRelight = 0.2f,
            transparentBackground = true,
            healPoints = listOf(HealPoint(x = 0.5f, y = 0.5f))
        )

        val metadata = recipe.toShareText()

        assertTrue(metadata.contains(PortraitPipeline.VERSION))
        assertTrue(metadata.contains("transform > base color > skin retouch > face warp > makeup > final tone"))
        assertTrue(metadata.contains("\"cutoutStudio\": true"))
        assertTrue(metadata.contains("\"bodyTune\": 0.18"))
        assertTrue(metadata.contains("\"portraitRelight\": 0.2"))
        assertTrue(metadata.contains("\"transparentBackground\": true"))
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
                    "matteRefine": 0.78,
                    "portraitRelight": 0.22,
                    "catchlight": 0.18,
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
        assertEquals(0.78f, recipe.matteRefine)
        assertEquals(0.22f, recipe.portraitRelight)
        assertEquals(0.18f, recipe.catchlight)
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
                "remotePresetConfig": true,
                "projectSessions": true,
                "batchExport": true,
                "transparentCutout": true,
                "analyticsEvents": true,
                "monetizationGates": false
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
        assertEquals(true, config.flags.projectSessions)
        assertEquals(true, config.flags.batchExport)
        assertEquals(true, config.flags.transparentCutout)
        assertEquals(true, config.flags.analyticsEvents)
        assertEquals(false, config.flags.monetizationGates)
    }

    @Test
    fun recipeCodecRoundTripsPhaseSixAndWorkflowFields() {
        val recipe = EditRecipe(
            portraitRelight = 0.24f,
            catchlight = 0.18f,
            underEyeLift = 0.14f,
            transparentBackground = true,
            matteRefine = 0.82f,
            healBrushRadius = 0.04f,
            healBrushStrength = 0.64f,
            watermarkEnabled = true,
            exportQuality = 88,
            healPoints = listOf(HealPoint(0.4f, 0.5f, 0.04f, 0.64f, HealMode.Clone))
        )

        val decoded = RecipeCodec.decode(RecipeCodec.encode(recipe))

        assertEquals(0.24f, decoded.portraitRelight)
        assertEquals(0.18f, decoded.catchlight)
        assertEquals(0.14f, decoded.underEyeLift)
        assertEquals(true, decoded.transparentBackground)
        assertEquals(0.82f, decoded.matteRefine)
        assertEquals(0.04f, decoded.healBrushRadius)
        assertEquals(0.64f, decoded.healBrushStrength)
        assertEquals(true, decoded.watermarkEnabled)
        assertEquals(88, decoded.exportQuality)
        assertEquals(1, decoded.healPoints.size)
        assertEquals(HealMode.Clone, decoded.healPoints.single().mode)
    }

    @Test
    fun projectSessionKeepsRestorableProjectMetadata() {
        val project = ProjectSession(
            id = "last-project",
            originalUri = "content://image/1",
            imageName = "Portrait",
            recipeJson = RecipeCodec.encode(EditRecipe(exposure = 0.1f)),
            thumbnailChecksum = 42L,
            exportHistory = listOf("PNG lumi.png"),
            updatedAtMillis = 123L
        )

        assertEquals("Portrait", project.imageName)
        assertTrue(project.recipeJson.contains("exposure"))
        assertEquals(1, project.exportHistory.size)
    }
}
