/*
 * Copyright (C) 2025 Răzvan Albu
 * Copyright (C) 2026 Adriano Pontes
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package adrianogba.stario.launcher.ui.common

import android.content.Context
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView
import androidx.annotation.RawRes
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.themes.ThemedActivity
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.Random
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface
import kotlin.math.ceil

// Based on the JS implementation from
// Stripe and Kevin Hufnagl
//
// Shader loading generated with Gemini 2.5 Pro
//
// https://gist.github.com/jordienr/64bcf75f8b08641f205bd6a1a0d4ce1d
// https://stripe.com
// https://kevinhufnagl.com

class GradientView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr), TextureView.SurfaceTextureListener {

    private val themeColors = IntArray(4)

    private var renderThread: RenderThread? = null

    init {
        val activity = context as? ThemedActivity
            ?: throw RuntimeException("GradientView must be in a ThemedActivity.")

        surfaceTextureListener = this
        isOpaque = false

        themeColors[0] =
            activity.getAttributeData(com.google.android.material.R.attr.colorSurfaceContainer)
        themeColors[1] =
            activity.getAttributeData(com.google.android.material.R.attr.colorSecondaryContainer)
        themeColors[2] =
            activity.getAttributeData(com.google.android.material.R.attr.colorPrimaryContainer)
        themeColors[3] =
            activity.getAttributeData(com.google.android.material.R.attr.colorSurfaceContainerHigh)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        val thread = RenderThread(surface, context, themeColors)
        renderThread = thread

        thread.updateSize(width, height)
        thread.start()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        renderThread?.updateSize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        val thread = renderThread

        if (thread != null) {
            thread.stopRendering()

            try {
                thread.join()
            } catch (exception: InterruptedException) {
                Log.e(TAG, "Failed to stop render thread", exception)
            }
        }

        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }

    private class RenderThread(
        private val surfaceTexture: SurfaceTexture,
        private val context: Context,
        private val themeColors: IntArray
    ) : Thread() {

        private val projectionMatrix = FloatArray(16)
        private val modelViewMatrix = FloatArray(16)
        private val noiseSeeds = FloatArray(3)

        @Volatile
        private var running = true
        private var sizeChanged = false
        private var width = 0
        private var height = 0

        private lateinit var eglDisplay: EGLDisplay
        private lateinit var eglContext: EGLContext
        private lateinit var eglSurface: EGLSurface
        private lateinit var egl: EGL10

        private lateinit var indexBuffer: ShortBuffer
        private lateinit var vertexBuffer: FloatBuffer
        private lateinit var uvNormBuffer: FloatBuffer
        private lateinit var uvBuffer: FloatBuffer
        private var startTime = 0L
        private var indexCount = 0
        private var program = 0

        init {
            val baseSeed = Random().nextFloat() * 200.0f

            for (index in 0 until 3) {
                noiseSeeds[index] = baseSeed + 10.0f * (index + 1)
            }
        }

        fun updateSize(width: Int, height: Int) {
            sizeChanged = true

            this.height = height
            this.width = width
        }

        fun stopRendering() {
            running = false
        }

        override fun run() {
            initEGL()
            initGL()

            while (running) {
                if (sizeChanged) {
                    GLES20.glViewport(0, 0, width, height)
                    Matrix.orthoM(
                        projectionMatrix,
                        0, -1f, 1f, -1f, 1f, -10f, 10f
                    )
                    Matrix.setIdentityM(modelViewMatrix, 0)
                    generatePlaneMesh(
                        ceil(width * 0.06).toInt(), ceil(height * 0.16).toInt()
                    )

                    sizeChanged = false
                }

                drawFrame()

                if (!egl.eglSwapBuffers(eglDisplay, eglSurface)) {
                    Log.e(TAG, "Buffer swap failed")
                }
            }

            destroyEGL()
        }

        private fun initEGL() {
            egl = EGLContext.getEGL() as EGL10
            eglDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
            egl.eglInitialize(eglDisplay, null)

            val configAttribs = intArrayOf(
                EGL10.EGL_RENDERABLE_TYPE, 4, // ES2
                EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_NONE
            )

            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            egl.eglChooseConfig(eglDisplay, configAttribs, configs, 1, numConfigs)

            eglContext = egl.eglCreateContext(
                eglDisplay, configs[0], EGL10.EGL_NO_CONTEXT,
                intArrayOf(0x3098, 2, EGL10.EGL_NONE)
            )
            eglSurface = egl.eglCreateWindowSurface(eglDisplay, configs[0], surfaceTexture, null)
            egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        private fun initGL() {
            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(
                program,
                loadShader(GLES20.GL_VERTEX_SHADER, readShader(context, R.raw.gradient_vert))
            )
            GLES20.glAttachShader(
                program,
                loadShader(GLES20.GL_FRAGMENT_SHADER, readShader(context, R.raw.gradient_frag))
            )
            GLES20.glLinkProgram(program)

            startTime = System.currentTimeMillis()

            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glDisable(GLES20.GL_CULL_FACE)
        }

        private fun drawFrame() {
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            GLES20.glUseProgram(program)

            val timeParam = (System.currentTimeMillis() - startTime).toFloat()

            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uTime"), timeParam)
            GLES20.glUniformMatrix4fv(
                GLES20.glGetUniformLocation(program, "uProjectionMatrix"),
                1, false, projectionMatrix, 0
            )
            GLES20.glUniformMatrix4fv(
                GLES20.glGetUniformLocation(program, "uModelViewMatrix"),
                1, false, modelViewMatrix, 0
            )
            GLES20.glUniform2f(
                GLES20.glGetUniformLocation(program, "uResolution"),
                width.toFloat(), height.toFloat()
            )
            GLES20.glUniform1fv(
                GLES20.glGetUniformLocation(program, "uNoiseSeeds"), 3, noiseSeeds, 0
            )

            bindColor("uBaseColor", themeColors[0])
            bindColor("uColor1", themeColors[1])
            bindColor("uColor2", themeColors[2])
            bindColor("uColor3", themeColors[3])

            bindAttribute("aPosition", vertexBuffer, 3)
            bindAttribute("aUv", uvBuffer, 2)
            bindAttribute("aUvNorm", uvNormBuffer, 2)

            GLES20.glDrawElements(
                GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indexBuffer
            )
        }

        private fun generatePlaneMesh(xSegCount: Int, ySegCount: Int) {
            val vertexCount = (xSegCount + 1) * (ySegCount + 1)

            val vertices = FloatArray(vertexCount * 3)
            val uvs = FloatArray(vertexCount * 2)
            val uvNorms = FloatArray(vertexCount * 2)
            val indices = ShortArray(xSegCount * ySegCount * 6)

            var vIdx = 0
            var uIdx = 0

            for (y in 0..ySegCount) {
                for (x in 0..xSegCount) {
                    val xN = x.toFloat() / xSegCount
                    val yN = y.toFloat() / ySegCount

                    vertices[vIdx++] = xN * 2.0f - 1.0f
                    vertices[vIdx++] = yN * 2.0f - 1.0f
                    vertices[vIdx++] = 0.0f
                    uvs[uIdx] = xN
                    uvs[uIdx + 1] = 1.0f - yN
                    uvNorms[uIdx] = xN * 2.0f - 1.0f
                    uvNorms[uIdx + 1] = 1.0f - yN * 2.0f
                    uIdx += 2
                }
            }

            var iIdx = 0

            for (y in 0 until ySegCount) {
                for (x in 0 until xSegCount) {
                    val tl = (y * (xSegCount + 1) + x).toShort()
                    val tr = (tl + 1).toShort()
                    val bl = ((y + 1) * (xSegCount + 1) + x).toShort()
                    val br = (bl + 1).toShort()

                    indices[iIdx++] = tl
                    indices[iIdx++] = bl
                    indices[iIdx++] = tr
                    indices[iIdx++] = tr
                    indices[iIdx++] = bl
                    indices[iIdx++] = br
                }
            }

            indexCount = indices.size
            vertexBuffer = createFloatBuffer(vertices)
            uvBuffer = createFloatBuffer(uvs)
            uvNormBuffer = createFloatBuffer(uvNorms)
            indexBuffer = createShortBuffer(indices)
        }

        private fun bindColor(name: String, color: Int) {
            GLES20.glUniform3f(
                GLES20.glGetUniformLocation(program, name),
                Color.red(color) / 255f, Color.green(color) / 255f, Color.blue(color) / 255f
            )
        }

        private fun bindAttribute(name: String, buffer: FloatBuffer, size: Int) {
            val location = GLES20.glGetAttribLocation(program, name)

            if (location != -1) {
                GLES20.glEnableVertexAttribArray(location)
                GLES20.glVertexAttribPointer(
                    location, size, GLES20.GL_FLOAT, false, 0, buffer
                )
            }
        }

        private fun createFloatBuffer(coordinates: FloatArray): FloatBuffer {
            val floatBuffer = ByteBuffer.allocateDirect(coordinates.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            floatBuffer.put(coordinates).position(0)

            return floatBuffer
        }

        private fun createShortBuffer(coordinates: ShortArray): ShortBuffer {
            val shortBuffer = ByteBuffer.allocateDirect(coordinates.size * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
            shortBuffer.put(coordinates).position(0)

            return shortBuffer
        }

        private fun loadShader(type: Int, code: String): Int {
            val shader = GLES20.glCreateShader(type)

            GLES20.glShaderSource(shader, code)
            GLES20.glCompileShader(shader)

            return shader
        }

        private fun readShader(context: Context, @RawRes resId: Int): String {
            try {
                BufferedReader(
                    InputStreamReader(context.resources.openRawResource(resId))
                ).use { reader ->
                    // Trailing newline on every line, last one included: the
                    // original did that and GLSL preprocessor directives want it.
                    return reader.lineSequence().joinToString("\n", postfix = "\n")
                }
            } catch (exception: IOException) {
                Log.e(TAG, "readShader: ", exception)

                return ""
            }
        }

        private fun destroyEGL() {
            egl.eglMakeCurrent(
                eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT
            )
            egl.eglDestroySurface(eglDisplay, eglSurface)
            egl.eglDestroyContext(eglDisplay, eglContext)
            egl.eglTerminate(eglDisplay)
        }
    }

    private companion object {
        const val TAG = "GradientView"
    }
}
