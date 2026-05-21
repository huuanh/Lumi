package com.lumi.retouch

import android.graphics.Bitmap
import android.os.SystemClock

data class EngineRenderResult(
    val bitmap: Bitmap,
    val status: String,
    val gpuFailed: Boolean = false
)

object RetouchEngine {
    fun createDemoBitmap(): Bitmap = PhotoProcessor.createDemoBitmap()

    fun transformOnly(source: Bitmap, recipe: EditRecipe): Bitmap {
        return PhotoProcessor.transformOnly(source, recipe)
    }

    fun process(
        source: Bitmap,
        recipe: EditRecipe,
        faceAnchors: List<FaceAnchor> = emptyList(),
        faceMeshes: List<FaceMeshAnchor> = emptyList(),
        personMask: PersonSegmentationMask? = null,
        bodyPose: BodyPoseAnchor? = null
    ): Bitmap {
        return PhotoProcessor.process(source, recipe, faceAnchors, faceMeshes, personMask, bodyPose)
    }

    fun processPrepared(
        source: Bitmap,
        recipe: EditRecipe,
        faceAnchors: List<FaceAnchor> = emptyList(),
        faceMeshes: List<FaceMeshAnchor> = emptyList(),
        personMask: PersonSegmentationMask? = null,
        bodyPose: BodyPoseAnchor? = null
    ): Bitmap {
        return PhotoProcessor.processPrepared(source, recipe, faceAnchors, faceMeshes, personMask, bodyPose)
    }

    fun renderPreviewPrepared(
        transformedSource: Bitmap,
        preparedRecipe: EditRecipe,
        faceAnchors: List<FaceAnchor>,
        faceMeshes: List<FaceMeshAnchor>,
        personMask: PersonSegmentationMask?,
        bodyPose: BodyPoseAnchor?,
        gpuEnabled: Boolean
    ): EngineRenderResult {
        val startMs = SystemClock.elapsedRealtime()
        var gpuFailed = false
        val gpuColor = if (gpuEnabled) {
            runCatching { GpuPreviewRenderer.renderColorGrade(transformedSource, preparedRecipe, faceMeshes) }
                .onFailure { gpuFailed = true }
                .getOrNull()
        } else {
            null
        }
        val bitmap = if (gpuColor != null) {
            val gpuMakeup = preparedRecipe.hasPreviewGpuMakeup(faceMeshes)
            PhotoProcessor.processEffectsPrepared(
                colorPrepared = gpuColor,
                recipe = preparedRecipe,
                faceAnchors = faceAnchors,
                faceMeshes = faceMeshes,
                personMask = personMask,
                bodyPose = bodyPose,
                skipSkinRetouch = preparedRecipe.hasPreviewGpuSkin(faceMeshes),
                skipMeshWarp = preparedRecipe.hasPreviewGpuWarp(faceMeshes),
                skipMeshMakeup = gpuMakeup,
                skipVignette = preparedRecipe.vignette > 0f
            )
        } else {
            PhotoProcessor.processPrepared(transformedSource, preparedRecipe, faceAnchors, faceMeshes, personMask, bodyPose)
        }
        val elapsedMs = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(0)
        val status = if (gpuColor != null) {
            val effects = mutableListOf("tone")
            if (preparedRecipe.hasPreviewGpuSkin(faceMeshes)) effects += "skin"
            if (preparedRecipe.hasPreviewGpuWarp(faceMeshes)) effects += "warp"
            if (preparedRecipe.hasPreviewGpuMakeup(faceMeshes)) effects += "makeup"
            if (preparedRecipe.cutoutStudio) effects += if (personMask != null) "seg" else "studio"
            if (preparedRecipe.bodyTune > 0f && bodyPose != null) effects += "pose"
            if (preparedRecipe.sharpen > 0f || preparedRecipe.vignette > 0f) effects += "final"
            "GPU ${effects.joinToString("+")} ${elapsedMs}ms"
        } else {
            "CPU preview ${elapsedMs}ms"
        }
        return EngineRenderResult(bitmap = bitmap, status = status, gpuFailed = gpuFailed)
    }

    fun withoutTransform(recipe: EditRecipe): EditRecipe {
        return recipe.copy(rotationDegrees = 0, flipHorizontal = false, cropMode = CropMode.Original)
    }

    private fun EditRecipe.hasPreviewGpuWarp(meshes: List<FaceMeshAnchor>): Boolean {
        return meshes.isNotEmpty() && (faceSlim > 0f || eyeScale > 0f || noseSlim > 0f || lipPlump > 0f || bodyTune > 0f)
    }

    private fun EditRecipe.hasPreviewGpuMakeup(meshes: List<FaceMeshAnchor>): Boolean {
        return meshes.isNotEmpty() && makeupStrength > 0f && (lipOpacity > 0f || blushOpacity > 0f || contour > 0f || eyeShadow > 0f || eyeLine > 0f)
    }

    private fun EditRecipe.hasPreviewGpuSkin(meshes: List<FaceMeshAnchor>): Boolean {
        return meshes.isNotEmpty() && (skinSmooth > 0f || blemishSoften > 0f)
    }
}
