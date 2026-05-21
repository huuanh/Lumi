package com.lumi.retouch

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max

object GpuPreviewRenderer {
    fun renderColorGrade(source: Bitmap, recipe: EditRecipe, faceMeshes: List<FaceMeshAnchor> = emptyList()): Bitmap {
        val session = EglSession(source.width, source.height)
        return try {
            session.render(source, recipe, faceMeshes.firstOrNull())
        } finally {
            session.release()
        }
    }

    private class EglSession(private val width: Int, private val height: Int) {
        private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var context: EGLContext = EGL14.EGL_NO_CONTEXT
        private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
        private var program = 0
        private var texture = 0

        private val vertexBuffer = floatBuffer(
            floatArrayOf(
                -1f, -1f, 0f, 1f,
                1f, -1f, 1f, 1f,
                -1f, 1f, 0f, 0f,
                1f, 1f, 1f, 0f
            )
        )

        init {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) { "EGL init failed" }

            val configs = arrayOfNulls<EGLConfig>(1)
            val configCount = IntArray(1)
            val attribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            check(EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, configCount, 0) && configCount[0] > 0) {
                "No EGL config"
            }
            val config = configs[0] ?: error("Missing EGL config")

            context = EGL14.eglCreateContext(
                display,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                0
            )
            check(context != EGL14.EGL_NO_CONTEXT) { "EGL context failed" }

            surface = EGL14.eglCreatePbufferSurface(
                display,
                config,
                intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE),
                0
            )
            check(surface != EGL14.EGL_NO_SURFACE) { "EGL surface failed" }
            check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "EGL make current failed" }

            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            texture = createTexture()
        }

        fun render(source: Bitmap, recipe: EditRecipe, faceMesh: FaceMeshAnchor?): Bitmap {
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, source, 0)
            checkGlError("Upload preview texture")

            GLES20.glUseProgram(program)
            val positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            val texHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
            check(positionHandle >= 0 && texHandle >= 0) { "Preview shader attributes missing" }
            val stride = 4 * 4

            vertexBuffer.position(0)
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, stride, vertexBuffer)
            vertexBuffer.position(2)
            GLES20.glEnableVertexAttribArray(texHandle)
            GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, stride, vertexBuffer)

            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uExposure"), recipe.exposure + recipe.filterExposure())
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uContrast"), recipe.contrast + recipe.filterContrast())
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uSaturation"), recipe.saturation + recipe.filterSaturation())
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uWarmth"), recipe.warmth + recipe.filterWarmth())
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uLook"), recipe.filterLook.ordinal)
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uTexelSize"), 1f / width.toFloat(), 1f / height.toFloat())
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uSharpen"), recipe.sharpen)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uVignette"), recipe.vignette)
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uSkinRetouch"), recipe.skinSmooth, recipe.blemishSoften)
            GLES20.glUniform4fv(GLES20.glGetUniformLocation(program, "uFaceBounds"), 1, normalizedBounds(faceMesh), 0)
            GLES20.glUniform2fv(GLES20.glGetUniformLocation(program, "uLeftEye"), 1, normalizedPoint(faceMesh, listOf(33, 133, 159, 145)), 0)
            GLES20.glUniform2fv(GLES20.glGetUniformLocation(program, "uRightEye"), 1, normalizedPoint(faceMesh, listOf(362, 263, 386, 374)), 0)
            GLES20.glUniform2fv(GLES20.glGetUniformLocation(program, "uNose"), 1, normalizedPoint(faceMesh, listOf(1, 4, 5, 6, 168)), 0)
            GLES20.glUniform2fv(GLES20.glGetUniformLocation(program, "uMouth"), 1, normalizedPoint(faceMesh, listOf(61, 291, 13, 14, 0, 17)), 0)
            GLES20.glUniform2fv(GLES20.glGetUniformLocation(program, "uLeftCheek"), 1, normalizedPoint(faceMesh, listOf(50, 101, 205, 187)), 0)
            GLES20.glUniform2fv(GLES20.glGetUniformLocation(program, "uRightCheek"), 1, normalizedPoint(faceMesh, listOf(280, 330, 425, 411)), 0)
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uWarpStrength"), recipe.faceSlim, recipe.eyeScale)
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uFeatureWarp"), recipe.noseSlim, recipe.lipPlump)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uBodyTune"), recipe.bodyTune)
            GLES20.glUniform4fv(GLES20.glGetUniformLocation(program, "uLipColor"), 1, normalizedColor(recipe.lipColor), 0)
            GLES20.glUniform4f(
                GLES20.glGetUniformLocation(program, "uMakeup"),
                recipe.lipOpacity,
                recipe.blushOpacity,
                recipe.contour,
                recipe.makeupStrength
            )
            GLES20.glUniform4f(
                GLES20.glGetUniformLocation(program, "uEyeMakeup"),
                recipe.eyeShadow,
                recipe.eyeLine,
                recipe.lipPrecision,
                recipe.blushSpread
            )
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uHasFace"), if (faceMesh != null) 1 else 0)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            checkGlError("Render preview shader")
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(texHandle)

            val buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
            checkGlError("Read preview pixels")
            return rgbaBufferToBitmap(buffer, width, height)
        }

        fun release() {
            if (texture != 0) GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
            if (program != 0) GLES20.glDeleteProgram(program)
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                EGL14.eglReleaseThread()
            }
        }

        private fun createTexture(): Int {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            return ids[0]
        }

        private fun normalizedBounds(mesh: FaceMeshAnchor?): FloatArray {
            if (mesh == null) return floatArrayOf(0f, 0f, 0f, 0f)
            return floatArrayOf(
                mesh.bounds.left / width,
                mesh.bounds.top / height,
                mesh.bounds.width() / width,
                mesh.bounds.height() / height
            )
        }

        private fun normalizedPoint(mesh: FaceMeshAnchor?, indices: List<Int>): FloatArray {
            val points = mesh?.let { m -> indices.mapNotNull { m.point(it) } }.orEmpty()
            if (points.isEmpty()) return floatArrayOf(0f, 0f)
            return floatArrayOf(
                points.map { it.x }.average().toFloat() / width,
                points.map { it.y }.average().toFloat() / height
            )
        }

        private fun normalizedColor(color: Int): FloatArray {
            return floatArrayOf(
                ((color shr 16) and 0xFF) / 255f,
                ((color shr 8) and 0xFF) / 255f,
                (color and 0xFF) / 255f,
                max(0f, minOf(1f, ((color ushr 24) and 0xFF) / 255f))
            )
        }
    }

    private fun rgbaBufferToBitmap(buffer: ByteBuffer, width: Int, height: Int): Bitmap {
        buffer.rewind()
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val dstY = height - 1 - y
            for (x in 0 until width) {
                val r = buffer.get().toInt() and 0xFF
                val g = buffer.get().toInt() and 0xFF
                val b = buffer.get().toInt() and 0xFF
                val a = buffer.get().toInt() and 0xFF
                pixels[dstY * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
            .copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun checkGlError(stage: String) {
        val error = GLES20.glGetError()
        check(error == GLES20.GL_NO_ERROR) { "$stage failed with GL error 0x${Integer.toHexString(error)}" }
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(program) }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(shader) }
        return shader
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }
    }

    private fun EditRecipe.filterExposure() = when (filterLook) {
        FilterLook.Clean -> 0.01f
        FilterLook.Film -> -0.04f
        FilterLook.Cream -> 0.04f
        FilterLook.Korean -> 0.05f
        FilterLook.CoolWhite -> 0.07f
        FilterLook.WarmPortrait -> 0.04f
        FilterLook.Neo -> 0.01f
    }

    private fun EditRecipe.filterContrast() = when (filterLook) {
        FilterLook.Clean -> 0.02f
        FilterLook.Film -> 0.1f
        FilterLook.Cream -> -0.03f
        FilterLook.Korean -> -0.02f
        FilterLook.CoolWhite -> -0.04f
        FilterLook.WarmPortrait -> 0.05f
        FilterLook.Neo -> 0.14f
    }

    private fun EditRecipe.filterSaturation() = when (filterLook) {
        FilterLook.Clean -> 0.01f
        FilterLook.Film -> -0.1f
        FilterLook.Cream -> 0.02f
        FilterLook.Korean -> -0.02f
        FilterLook.CoolWhite -> -0.08f
        FilterLook.WarmPortrait -> 0.05f
        FilterLook.Neo -> 0.13f
    }

    private fun EditRecipe.filterWarmth() = when (filterLook) {
        FilterLook.Clean -> 0f
        FilterLook.Film -> 0.06f
        FilterLook.Cream -> 0.12f
        FilterLook.Korean -> -0.02f
        FilterLook.CoolWhite -> -0.14f
        FilterLook.WarmPortrait -> 0.14f
        FilterLook.Neo -> -0.12f
    }

    private const val VERTEX_SHADER = """
        attribute vec2 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = vec4(aPosition, 0.0, 1.0);
            vTexCoord = aTexCoord;
        }
    """

    private const val FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uExposure;
        uniform float uContrast;
        uniform float uSaturation;
        uniform float uWarmth;
        uniform int uLook;
        uniform vec2 uTexelSize;
        uniform float uSharpen;
        uniform float uVignette;
        uniform vec2 uSkinRetouch;
        uniform int uHasFace;
        uniform vec4 uFaceBounds;
        uniform vec2 uLeftEye;
        uniform vec2 uRightEye;
        uniform vec2 uNose;
        uniform vec2 uMouth;
        uniform vec2 uLeftCheek;
        uniform vec2 uRightCheek;
        uniform vec2 uWarpStrength;
        uniform vec2 uFeatureWarp;
        uniform float uBodyTune;
        uniform vec4 uLipColor;
        uniform vec4 uMakeup;
        uniform vec4 uEyeMakeup;
        varying vec2 vTexCoord;

        float luma(vec3 color) {
            return dot(color, vec3(0.299, 0.587, 0.114));
        }

        float toneCurve(float value, float shadows, float mid, float highlights) {
            float x = clamp(value, 0.0, 1.0);
            float shadowWeight = clamp(1.0 - x, 0.0, 1.0);
            float highlightWeight = clamp(x, 0.0, 1.0);
            float midWeight = clamp(1.0 - abs(x - 0.5) * 2.0, 0.0, 1.0);
            return clamp(x + shadows * shadowWeight + mid * midWeight + highlights * highlightWeight, 0.0, 1.0);
        }

        vec3 applyLook(vec3 color) {
            float l = luma(color);
            if (uLook == 1) {
                float fade = (1.0 - l) * 0.0627;
                return vec3(
                    toneCurve(color.r + 0.031 + fade, 0.045, -0.015, -0.035),
                    toneCurve(color.g + 0.012 + fade * 0.5, 0.032, -0.010, -0.020),
                    toneCurve(color.b - 0.024 + fade, 0.055, -0.020, -0.012)
                );
            } else if (uLook == 2) {
                float warm = 0.039 + l * 0.031;
                return vec3(
                    toneCurve(color.r + warm, 0.018, 0.035, 0.020),
                    toneCurve(color.g + 0.020, 0.012, 0.025, 0.018),
                    toneCurve(color.b - 0.031, 0.020, -0.008, -0.025)
                );
            } else if (uLook == 3) {
                return vec3(
                    toneCurve(color.r + 0.026, 0.022, 0.042, 0.022),
                    toneCurve(color.g + 0.018, 0.018, 0.036, 0.018),
                    toneCurve(color.b + 0.006, 0.018, 0.020, 0.028)
                );
            } else if (uLook == 4) {
                return vec3(
                    toneCurve(color.r - 0.018, 0.025, 0.048, 0.035),
                    toneCurve(color.g + 0.006, 0.024, 0.045, 0.032),
                    toneCurve(color.b + 0.038, 0.032, 0.052, 0.040)
                );
            } else if (uLook == 5) {
                return vec3(
                    toneCurve(color.r + 0.050, 0.020, 0.034, 0.026),
                    toneCurve(color.g + 0.020, 0.012, 0.024, 0.018),
                    toneCurve(color.b - 0.018, 0.014, 0.004, -0.010)
                );
            } else if (uLook == 6) {
                float cool = 0.031 + (1.0 - l) * 0.031;
                return vec3(
                    toneCurve(color.r - 0.024, -0.018, 0.012, 0.020),
                    toneCurve(color.g + 0.016, 0.004, 0.020, 0.024),
                    toneCurve(color.b + cool, 0.028, 0.035, 0.018)
                );
            }
            return vec3(
                toneCurve(color.r, 0.010, 0.025, 0.010),
                toneCurve(color.g, 0.008, 0.018, 0.008),
                toneCurve(color.b, 0.012, 0.012, 0.018)
            );
        }

        float ellipseWeight(vec2 p, vec2 center, vec2 radius) {
            vec2 d = (p - center) / max(radius, vec2(0.0001));
            float dist = clamp(length(d), 0.0, 1.0);
            float falloff = 1.0 - dist;
            return falloff * falloff;
        }

        vec2 warpCoord(vec2 coord) {
            if (uHasFace == 0) return coord;
            vec2 faceCenter = uFaceBounds.xy + uFaceBounds.zw * vec2(0.5, 0.5);
            vec2 lowerCenter = vec2(faceCenter.x, uFaceBounds.y + uFaceBounds.w * 0.64);
            vec2 p = coord;

            if (uWarpStrength.x > 0.0) {
                float dy = abs(p.y - lowerCenter.y) / max(uFaceBounds.w * 0.46, 0.0001);
                float vertical = clamp(1.0 - dy, 0.0, 1.0);
                float dx = p.x - lowerCenter.x;
                float distance = clamp(1.0 - abs(dx) / max(uFaceBounds.z * 0.62, 0.0001), 0.0, 1.0);
                float strength = uWarpStrength.x * 0.18 * vertical * distance;
                p.x = lowerCenter.x + dx * (1.0 + strength);
            }

            if (uFeatureWarp.x > 0.0) {
                float w = ellipseWeight(p, uNose, vec2(uFaceBounds.z * 0.08, uFaceBounds.w * 0.20));
                p.x = uNose.x + (p.x - uNose.x) * (1.0 + uFeatureWarp.x * 0.25 * w);
            }

            if (uWarpStrength.y > 0.0) {
                float wl = ellipseWeight(p, uLeftEye, vec2(uFaceBounds.z * 0.12, uFaceBounds.w * 0.075));
                p = mix(p, uLeftEye + (p - uLeftEye) * (1.0 - uWarpStrength.y * 0.20 * wl), wl);
                float wr = ellipseWeight(p, uRightEye, vec2(uFaceBounds.z * 0.12, uFaceBounds.w * 0.075));
                p = mix(p, uRightEye + (p - uRightEye) * (1.0 - uWarpStrength.y * 0.20 * wr), wr);
            }

            if (uFeatureWarp.y > 0.0) {
                float w = ellipseWeight(p, uMouth, vec2(uFaceBounds.z * 0.18, uFaceBounds.w * 0.08));
                p = uMouth + (p - uMouth) * (1.0 - uFeatureWarp.y * 0.15 * w);
            }

            if (uBodyTune > 0.0) {
                vec2 chin = vec2(faceCenter.x, uFaceBounds.y + uFaceBounds.w);
                float w = ellipseWeight(p, chin, vec2(uFaceBounds.z * 0.24, uFaceBounds.w * 0.16));
                p.y += uFaceBounds.w * 0.045 * uBodyTune * w;
            }
            return clamp(p, vec2(0.0), vec2(1.0));
        }

        vec3 softLight(vec3 base, vec3 blend, float amount) {
            vec3 low = base - (1.0 - 2.0 * blend) * base * (1.0 - base);
            vec3 high = base + (2.0 * blend - 1.0) * (sqrt(max(base, vec3(0.0))) - base);
            vec3 soft = mix(low, high, step(vec3(0.5), blend));
            return mix(base, soft, clamp(amount, 0.0, 1.0));
        }

        float skinWeight(vec3 color) {
            float mx = max(color.r, max(color.g, color.b));
            float mn = min(color.r, min(color.g, color.b));
            float likely = step(0.275, color.r) * step(0.155, color.g) * step(0.095, color.b) *
                step(color.g, color.r) * step(color.b, color.g) * step(0.06, mx - mn);
            float warm = clamp((color.r - color.b) / 0.47, 0.0, 1.0);
            float notRed = 1.0 - clamp((color.r - color.g - 0.33) / 0.31, 0.0, 1.0);
            float lum = luma(color);
            float light = smoothstep(0.12, 0.22, lum) * (1.0 - smoothstep(0.9, 0.98, lum));
            return likely * warm * notRed * light;
        }

        float faceSkinMask(vec2 coord) {
            if (uHasFace == 0) return 1.0;
            vec2 faceCenter = vec2(uFaceBounds.x + uFaceBounds.z * 0.5, uFaceBounds.y + uFaceBounds.w * 0.5);
            float faceOval = ellipseWeight(coord, faceCenter, vec2(uFaceBounds.z * 0.48, uFaceBounds.w * 0.62));
            float lipBlock = ellipseWeight(coord, uMouth, vec2(uFaceBounds.z * 0.17, uFaceBounds.w * 0.07));
            float eyeBlock = max(
                ellipseWeight(coord, uLeftEye, vec2(uFaceBounds.z * 0.12, uFaceBounds.w * 0.065)),
                ellipseWeight(coord, uRightEye, vec2(uFaceBounds.z * 0.12, uFaceBounds.w * 0.065))
            );
            float noseKeep = ellipseWeight(coord, uNose, vec2(uFaceBounds.z * 0.1, uFaceBounds.w * 0.18)) * 0.18;
            return clamp(faceOval * (1.0 - clamp(max(max(lipBlock, eyeBlock), noseKeep), 0.0, 0.92)), 0.0, 1.0);
        }

        vec3 applySkinRetouch(vec2 coord, vec2 sampleCoord, vec3 color) {
            float amount = max(uSkinRetouch.x, uSkinRetouch.y * 0.65);
            if (amount <= 0.0) return color;
            float mask = skinWeight(color) * faceSkinMask(coord);
            if (mask <= 0.0) return color;
            vec2 radius = uTexelSize * vec2(2.0, 2.0);
            vec3 blur = color * 1.25;
            blur += texture2D(uTexture, clamp(sampleCoord + vec2(radius.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
            blur += texture2D(uTexture, clamp(sampleCoord - vec2(radius.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
            blur += texture2D(uTexture, clamp(sampleCoord + vec2(0.0, radius.y), vec2(0.0), vec2(1.0))).rgb;
            blur += texture2D(uTexture, clamp(sampleCoord - vec2(0.0, radius.y), vec2(0.0), vec2(1.0))).rgb;
            blur += texture2D(uTexture, clamp(sampleCoord + radius, vec2(0.0), vec2(1.0))).rgb * 0.7;
            blur += texture2D(uTexture, clamp(sampleCoord - radius, vec2(0.0), vec2(1.0))).rgb * 0.7;
            blur /= 6.65;
            float detail = clamp(length(color - blur) * 2.4, 0.0, 1.0);
            float textureGuard = 1.0 - detail * 0.55;
            float t = clamp((uSkinRetouch.x * 0.44 + uSkinRetouch.y * 0.26) * mask * textureGuard, 0.0, 0.58);
            vec3 smoothed = mix(color, blur, t);
            float redExcess = max(0.0, color.r - (color.g + color.b) * 0.5 - 0.07);
            smoothed.r -= redExcess * uSkinRetouch.y * mask * 0.45;
            smoothed.g += redExcess * uSkinRetouch.y * mask * 0.08;
            return clamp(smoothed, 0.0, 1.0);
        }

        float bandGate(float value, float low, float high) {
            return step(low, value) * step(value, high);
        }

        vec3 applyMakeup(vec2 coord, vec3 color) {
            if (uHasFace == 0 || uMakeup.w <= 0.0) return color;
            float makeup = clamp(uMakeup.w, 0.0, 1.4);
            float lipPrecision = clamp(uEyeMakeup.z, 0.0, 1.0);
            float blushSpread = clamp(uEyeMakeup.w, 0.0, 1.0);

            float lip = ellipseWeight(
                coord,
                uMouth,
                vec2(uFaceBounds.z * (0.15 + (1.0 - lipPrecision) * 0.035), uFaceBounds.w * (0.055 + (1.0 - lipPrecision) * 0.025))
            );
            float lipBand = bandGate(coord.y, uMouth.y - uFaceBounds.w * 0.035, uMouth.y + uFaceBounds.w * 0.048);
            lip *= lipBand;
            if (lip > 0.0 && uMakeup.x > 0.0) {
                float strength = uMakeup.x * makeup * lip * 0.72;
                color = softLight(color, uLipColor.rgb, strength);
                float lum = luma(color);
                color = mix(vec3(lum), color, 1.0 + strength * 0.18);
            }

            float blushL = ellipseWeight(coord, uLeftCheek, vec2(uFaceBounds.z * (0.10 + blushSpread * 0.12), uFaceBounds.w * (0.065 + blushSpread * 0.08)));
            float blushR = ellipseWeight(coord, uRightCheek, vec2(uFaceBounds.z * (0.10 + blushSpread * 0.12), uFaceBounds.w * (0.065 + blushSpread * 0.08)));
            float blush = max(blushL, blushR);
            if (blush > 0.0 && uMakeup.y > 0.0) {
                color = softLight(color, vec3(0.886, 0.361, 0.408), uMakeup.y * makeup * blush * 0.35);
            }

            float cheekContour = max(
                ellipseWeight(coord, vec2(uFaceBounds.x + uFaceBounds.z * 0.31, uFaceBounds.y + uFaceBounds.w * 0.62), vec2(uFaceBounds.z * 0.16, uFaceBounds.w * 0.18)),
                ellipseWeight(coord, vec2(uFaceBounds.x + uFaceBounds.z * 0.69, uFaceBounds.y + uFaceBounds.w * 0.62), vec2(uFaceBounds.z * 0.16, uFaceBounds.w * 0.18))
            );
            float noseContour = ellipseWeight(coord, uNose, vec2(uFaceBounds.z * 0.08, uFaceBounds.w * 0.23));
            float contour = max(cheekContour * 0.45, noseContour * 0.16);
            if (contour > 0.0 && uMakeup.z > 0.0) {
                float amount = uMakeup.z * makeup * contour;
                color = softLight(color, vec3(0.329, 0.227, 0.188), amount);
                color *= 1.0 - amount * 0.08;
            }

            float eyeShadow = max(
                ellipseWeight(coord, uLeftEye + vec2(0.0, -uFaceBounds.w * 0.036), vec2(uFaceBounds.z * 0.105, uFaceBounds.w * 0.052)),
                ellipseWeight(coord, uRightEye + vec2(0.0, -uFaceBounds.w * 0.036), vec2(uFaceBounds.z * 0.105, uFaceBounds.w * 0.052))
            );
            float eyeBand = max(
                bandGate(coord.y, uLeftEye.y - uFaceBounds.w * 0.078, uLeftEye.y + uFaceBounds.w * 0.004),
                bandGate(coord.y, uRightEye.y - uFaceBounds.w * 0.078, uRightEye.y + uFaceBounds.w * 0.004)
            );
            if (eyeShadow > 0.0 && uEyeMakeup.x > 0.0) {
                color = softLight(color, vec3(0.404, 0.278, 0.255), uEyeMakeup.x * makeup * eyeShadow * eyeBand * 0.28);
            }

            float eyeLine = max(
                ellipseWeight(coord, uLeftEye + vec2(0.0, -uFaceBounds.w * 0.006), vec2(uFaceBounds.z * 0.105, uFaceBounds.w * 0.013)),
                ellipseWeight(coord, uRightEye + vec2(0.0, -uFaceBounds.w * 0.006), vec2(uFaceBounds.z * 0.105, uFaceBounds.w * 0.013))
            );
            if (eyeLine > 0.0 && uEyeMakeup.y > 0.0) {
                float amount = uEyeMakeup.y * makeup * eyeLine * 0.24;
                color = mix(color, vec3(0.165, 0.133, 0.125), amount);
            }
            return clamp(color, 0.0, 1.0);
        }

        vec3 applyFinalTone(vec2 coord, vec2 sampleCoord, vec3 color) {
            if (uSharpen > 0.0) {
                vec3 left = texture2D(uTexture, clamp(sampleCoord - vec2(uTexelSize.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                vec3 right = texture2D(uTexture, clamp(sampleCoord + vec2(uTexelSize.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                vec3 top = texture2D(uTexture, clamp(sampleCoord - vec2(0.0, uTexelSize.y), vec2(0.0), vec2(1.0))).rgb;
                vec3 bottom = texture2D(uTexture, clamp(sampleCoord + vec2(0.0, uTexelSize.y), vec2(0.0), vec2(1.0))).rgb;
                vec3 blur = (left + right + top + bottom) * 0.25;
                color = clamp(color + (color - blur) * uSharpen * 0.42, 0.0, 1.0);
            }
            if (uVignette > 0.0) {
                float d = distance(coord, vec2(0.5, 0.5));
                float vignette = smoothstep(0.34, 0.78, d) * uVignette;
                color *= 1.0 - vignette * 0.62;
            }
            return color;
        }

        void main() {
            vec2 sampleCoord = warpCoord(vTexCoord);
            vec4 src = texture2D(uTexture, sampleCoord);
            vec3 color = src.rgb;
            float gray = luma(color);
            color = mix(vec3(gray), color, 1.0 + uSaturation);
            color = (color - 0.5) * (1.0 + uContrast) + 0.5;
            color += vec3(uExposure);
            color += vec3(uWarmth * 0.137, uWarmth * 0.031, -uWarmth * 0.137);
            color = applyLook(clamp(color, 0.0, 1.0));
            color = applySkinRetouch(vTexCoord, sampleCoord, color);
            color = applyMakeup(vTexCoord, color);
            color = applyFinalTone(vTexCoord, sampleCoord, color);
            gl_FragColor = vec4(clamp(color, 0.0, 1.0), src.a);
        }
    """
}
