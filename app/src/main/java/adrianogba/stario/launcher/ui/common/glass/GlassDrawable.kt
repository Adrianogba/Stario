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

package adrianogba.stario.launcher.ui.common.glass

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import kotlin.math.min

/**
 * The glass treatment as a Drawable, so anything with a background can wear it.
 *
 * [GlassSurfaceView] does the same job but needs Compose and a slot in the
 * layout, which is fine for the two home screen widgets and hopeless for every
 * dialog, menu and bar in the app. Neither one refracts: the wallpaper cannot
 * be read (see [WallpaperSource]), and a dialog's own backdrop is already
 * blurred by the window behind it. What is left is what actually makes glass
 * read as glass at rest, and all of it is paintable:
 *
 * - a translucent body, lit from the top, so the surface has depth rather than
 *   being one flat wash
 * - a specular rim that is bright where light would land and nearly gone on the
 *   opposite edge, which is the single strongest cue
 * - a dark inner edge along the bottom, which reads as thickness
 */
class GlassDrawable(
    private val tint: Int,
    /** Eight values, the same shape [Path.addRoundRect] takes. */
    private val radii: FloatArray,
    private val rimWidth: Float,
    private val alpha: Float = DEFAULT_ALPHA
) : Drawable() {

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = rimWidth
    }
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = rimWidth * 2f
    }

    private val bodyRect = RectF()
    private val rimRect = RectF()
    private val innerRect = RectF()

    private val bodyPath = Path()
    private val rimPath = Path()
    private val innerPath = Path()

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()

        if (width <= 0f || height <= 0f) {
            return
        }

        bodyRect.set(bounds)
        rimRect.set(bodyRect)
        rimRect.inset(rimWidth / 2f, rimWidth / 2f)
        innerRect.set(bodyRect)
        innerRect.inset(rimWidth * 1.5f, rimWidth * 1.5f)

        // Clamped so a capsule stays a capsule when the caller asks for a
        // radius larger than the surface can hold.
        val limit = min(bodyRect.width(), bodyRect.height()) / 2f
        val clamped = FloatArray(radii.size) { min(radii[it], limit) }

        bodyPath.rewind()
        bodyPath.addRoundRect(bodyRect, clamped, Path.Direction.CW)

        rimPath.rewind()
        rimPath.addRoundRect(rimRect, clamped, Path.Direction.CW)

        innerPath.rewind()
        innerPath.addRoundRect(innerRect, clamped, Path.Direction.CW)

        // Glass catches more light along its upper edge than its lower one. A
        // flat fill at this alpha reads as a translucent rectangle; the
        // gradient is what makes the same alpha read as a pane with body.
        bodyPaint.shader = LinearGradient(
            0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(),
            withAlpha(tint, (alpha * TOP_LIGHT).coerceAtMost(1f)),
            withAlpha(tint, alpha * BOTTOM_LIGHT),
            Shader.TileMode.CLAMP
        )

        // Diagonal so the rim is brightest at the top left and all but gone at
        // the bottom right, the way a lit edge actually falls off.
        rimPaint.shader = LinearGradient(
            bounds.left.toFloat(), bounds.top.toFloat(),
            bounds.right.toFloat(), bounds.bottom.toFloat(),
            intArrayOf(
                withAlpha(Color.WHITE, RIM_BRIGHT),
                withAlpha(Color.WHITE, RIM_MID),
                withAlpha(Color.WHITE, RIM_DIM)
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )

        innerPaint.shader = LinearGradient(
            0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(),
            withAlpha(Color.BLACK, 0f),
            withAlpha(Color.BLACK, INNER_SHADOW),
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        if (bodyRect.isEmpty) {
            return
        }

        canvas.drawPath(bodyPath, bodyPaint)
        canvas.drawPath(innerPath, innerPaint)
        canvas.drawPath(rimPath, rimPaint)
    }

    override fun setAlpha(alpha: Int) {
        bodyPaint.alpha = alpha
        rimPaint.alpha = alpha
        innerPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bodyPaint.colorFilter = colorFilter
        rimPaint.colorFilter = colorFilter
        innerPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val DEFAULT_ALPHA = 0.34f

        const val TOP_LIGHT = 1.35f
        const val BOTTOM_LIGHT = 0.75f

        const val RIM_BRIGHT = 0.55f
        const val RIM_MID = 0.10f
        const val RIM_DIM = 0.28f

        const val INNER_SHADOW = 0.16f

        fun withAlpha(color: Int, alpha: Float): Int =
            ColorUtils.setAlphaComponent(color, (alpha * 255).toInt().coerceIn(0, 255))
    }
}
