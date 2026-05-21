package com.lumi.retouch

import android.graphics.Bitmap
import android.os.SystemClock

data class BenchmarkResult(
    val cpuMs: Long,
    val gpuMs: Long?,
    val cpuChecksum: Long,
    val gpuChecksum: Long?,
    val status: String
)

object RetouchBenchmark {
    fun runPreviewBenchmark(
        source: Bitmap,
        recipe: EditRecipe,
        faceAnchors: List<FaceAnchor>,
        faceMeshes: List<FaceMeshAnchor>,
        personMask: PersonSegmentationMask?,
        bodyPose: BodyPoseAnchor?,
        runGpu: Boolean
    ): BenchmarkResult {
        val preparedRecipe = RetouchEngine.withoutTransform(recipe)
        val transformed = RetouchEngine.transformOnly(source, recipe)

        val cpuStart = SystemClock.elapsedRealtime()
        val cpu = RetouchEngine.processPrepared(
            source = transformed,
            recipe = preparedRecipe,
            faceAnchors = faceAnchors,
            faceMeshes = faceMeshes,
            personMask = personMask,
            bodyPose = bodyPose
        )
        val cpuMs = (SystemClock.elapsedRealtime() - cpuStart).coerceAtLeast(0)
        val cpuChecksum = checksum(cpu)

        val gpuData = if (runGpu) {
            runCatching {
                val gpuStart = SystemClock.elapsedRealtime()
                val gpu = RetouchEngine.renderPreviewPrepared(
                    transformedSource = transformed,
                    preparedRecipe = preparedRecipe,
                    faceAnchors = faceAnchors,
                    faceMeshes = faceMeshes,
                    personMask = personMask,
                    bodyPose = bodyPose,
                    gpuEnabled = true
                ).bitmap
                val gpuMs = (SystemClock.elapsedRealtime() - gpuStart).coerceAtLeast(0)
                gpuMs to checksum(gpu)
            }.getOrNull()
        } else {
            null
        }

        val status = buildString {
            append("Bench CPU ${cpuMs}ms")
            gpuData?.let { append(" / GPU ${it.first}ms") }
            append(" c")
            append(cpuChecksum.toString(16).takeLast(6))
            gpuData?.let {
                append(" g")
                append(it.second.toString(16).takeLast(6))
            }
        }
        return BenchmarkResult(
            cpuMs = cpuMs,
            gpuMs = gpuData?.first,
            cpuChecksum = cpuChecksum,
            gpuChecksum = gpuData?.second,
            status = status
        )
    }

    fun checksum(bitmap: Bitmap): Long {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return 0L
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var hash = 1125899906842597L
        val step = (pixels.size / 4096).coerceAtLeast(1)
        var index = 0
        while (index < pixels.size) {
            hash = hash * 31 + pixels[index].toLong()
            index += step
        }
        hash = hash * 31 + width
        hash = hash * 31 + height
        return hash
    }
}
