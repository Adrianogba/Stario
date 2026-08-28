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

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.animation.OvershootInterpolator
import androidx.core.graphics.ColorUtils
import adrianogba.stario.launcher.ui.utils.animation.Animation
import kotlin.math.PI
import kotlin.math.sin

/**
 * The two halves of a Liquid Glass switch, as drawables.
 *
 * Apple's switch is not a tinted Material switch. It is a flat coloured track
 * with a pane of glass riding on it, and the pane is the whole character of the
 * control: it is wide rather than round, the track's colour carries through it,
 * and it deforms as it travels the way a drop of water does when it is dragged.
 *
 * Supplying drawables rather than swapping the widget keeps everything the
 * switch already does: the thumb travel, the row that forwards its clicks, the
 * listener each screen already attached, and the accessibility node.
 */
object GlassSwitchDrawables {

    /**
     * The track the pane rides on. A flat capsule that takes the accent colour
     * when checked, which is all iOS puts here; every optical cue lives in the
     * thumb above it.
     */
    class Track(
        private val offColor: Int,
        private val onColor: Int,
        private val rimWidth: Float,
        private val trackWidth: Int,
        private val trackHeight: Int
    ) : Drawable() {

        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = rimWidth
        }

        private val bodyRect = RectF()
        private val rimRect = RectF()

        private var animator: ValueAnimator? = null
        private var fraction = 0f

        override fun isStateful(): Boolean = true

        override fun onStateChange(state: IntArray): Boolean {
            val target = if (state.any { it == android.R.attr.state_checked }) 1f else 0f

            if (target == fraction && animator == null) {
                return false
            }

            animator?.cancel()
            animator = ValueAnimator.ofFloat(fraction, target).apply {
                duration = Animation.MEDIUM.duration.toLong()
                addUpdateListener {
                    fraction = it.animatedValue as Float
                    invalidateSelf()
                }
                start()
            }

            return true
        }

        override fun onBoundsChange(bounds: android.graphics.Rect) {
            bodyRect.set(bounds)
            rimRect.set(bodyRect)
            rimRect.inset(rimWidth / 2f, rimWidth / 2f)

            // Dark along the top lip and light along the bottom, which is how a
            // channel cut into a surface catches the light.
            rimPaint.shader = LinearGradient(
                0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(),
                ColorUtils.setAlphaComponent(Color.BLACK, (0.20f * 255).toInt()),
                ColorUtils.setAlphaComponent(Color.WHITE, (0.24f * 255).toInt()),
                Shader.TileMode.CLAMP
            )
        }

        override fun draw(canvas: Canvas) {
            if (bodyRect.isEmpty) {
                return
            }

            val radius = bodyRect.height() / 2f

            bodyPaint.color = ColorUtils.blendARGB(offColor, onColor, fraction)
            canvas.drawRoundRect(bodyRect, radius, radius, bodyPaint)

            val rimRadius = rimRect.height() / 2f
            canvas.drawRoundRect(rimRect, rimRadius, rimRadius, rimPaint)
        }

        override fun setAlpha(alpha: Int) {
            bodyPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            bodyPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        // SwitchCompat sizes the whole control from the track, so unlike a
        // background drawable this one cannot leave its size unset.
        override fun getIntrinsicWidth(): Int = trackWidth

        override fun getIntrinsicHeight(): Int = trackHeight
    }

    /**
     * The pane of glass that rides the track, and the part that carries the
     * whole effect.
     *
     * Four things are stacked here, and all four are visible at rest:
     *
     * - a soft glow of the track colour spilling past the outline
     * - the pane itself, near white and lit from the top
     * - an inner core, inset from the edge, carrying the track colour. This is
     *   what a lens does to whatever is under it: the content pulls towards the
     *   middle and leaves a clear margin at the rim
     * - a rim that goes brilliant white where the curve would catch the light,
     *   top and bottom, and saturates towards the track colour in between
     *
     * The motion is the part people notice without being able to name it. The
     * pane behaves like a drop of water being dragged: it stretches along its
     * direction of travel with the tail lagging behind the leading edge, is at
     * its longest halfway across, and pulls back round as it arrives. The
     * drawable therefore reserves more width than it paints at rest, and that
     * slack is where the stretch goes.
     */
    class Thumb(
        private val offColor: Int,
        private val onColor: Int,
        private val width: Int,
        private val height: Int,
        private val rimWidth: Float,
        private val glowRadius: Float
    ) : Drawable() {

        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.TRANSPARENT
        }
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = rimWidth * 2f
        }

        private val fullBounds = RectF()
        private val bodyRect = RectF()
        private val coreRect = RectF()
        private val rimRect = RectF()

        private var animator: ValueAnimator? = null
        private var fraction = 0f

        /** Peaks mid travel and is gone at both ends. */
        private var stretch = 0f

        /** Which way the pane is heading, so the tail lags on the right side. */
        private var heading = 1f

        override fun isStateful(): Boolean = true

        override fun onStateChange(state: IntArray): Boolean {
            val target = if (state.any { it == android.R.attr.state_checked }) 1f else 0f

            if (target == fraction && animator == null) {
                return false
            }

            heading = if (target > fraction) 1f else -1f

            animator?.cancel()
            animator = ValueAnimator.ofFloat(fraction, target).apply {
                duration = Animation.MEDIUM.duration.toLong()
                interpolator = OvershootInterpolator(OVERSHOOT)

                addUpdateListener {
                    fraction = it.animatedValue as Float
                    stretch = sin(it.animatedFraction * PI).toFloat()

                    layout()
                    invalidateSelf()
                }
                start()
            }

            return true
        }

        override fun onBoundsChange(bounds: android.graphics.Rect) {
            fullBounds.set(bounds)

            layout()
        }

        private fun layout() {
            if (fullBounds.isEmpty) {
                return
            }

            val reserved = fullBounds.width() - glowRadius * 2f
            val resting = reserved / (1f + STRETCH)
            val drawn = resting * (1f + STRETCH * stretch)

            // The leading edge keeps its place and the tail is what gives, so
            // the pane trails behind itself rather than growing from the middle.
            val slack = reserved - drawn
            val left = fullBounds.left + glowRadius + if (heading > 0f) slack else 0f

            bodyRect.set(
                left, fullBounds.top + glowRadius,
                left + drawn, fullBounds.bottom - glowRadius
            )

            rimRect.set(bodyRect)
            rimRect.inset(rimWidth, rimWidth)

            coreRect.set(bodyRect)
            coreRect.inset(bodyRect.width() * CORE_INSET, bodyRect.height() * CORE_INSET)

            shade()
        }

        private fun shade() {
            val tint = ColorUtils.blendARGB(offColor, onColor, fraction.coerceIn(0f, 1f))

            bodyPaint.shader = LinearGradient(
                0f, bodyRect.top, 0f, bodyRect.bottom,
                ColorUtils.blendARGB(tint, Color.WHITE, 0.88f),
                ColorUtils.blendARGB(tint, Color.WHITE, 0.62f),
                Shader.TileMode.CLAMP
            )

            corePaint.shader = LinearGradient(
                0f, coreRect.top, 0f, coreRect.bottom,
                ColorUtils.blendARGB(tint, Color.WHITE, 0.34f),
                ColorUtils.blendARGB(tint, Color.WHITE, 0.16f),
                Shader.TileMode.CLAMP
            )

            rimPaint.shader = LinearGradient(
                0f, rimRect.top, 0f, rimRect.bottom,
                intArrayOf(
                    ColorUtils.setAlphaComponent(Color.WHITE, (0.95f * 255).toInt()),
                    ColorUtils.blendARGB(tint, Color.WHITE, 0.05f),
                    ColorUtils.setAlphaComponent(Color.WHITE, (0.95f * 255).toInt())
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )

            glowPaint.setShadowLayer(
                glowRadius, 0f, glowRadius / 3f,
                ColorUtils.setAlphaComponent(tint, (0.45f * 255).toInt())
            )
        }

        override fun draw(canvas: Canvas) {
            if (bodyRect.isEmpty) {
                return
            }

            val radius = bodyRect.height() / 2f
            canvas.drawRoundRect(bodyRect, radius, radius, glowPaint)
            canvas.drawRoundRect(bodyRect, radius, radius, bodyPaint)

            val coreRadius = coreRect.height() / 2f
            canvas.drawRoundRect(coreRect, coreRadius, coreRadius, corePaint)

            val rimRadius = rimRect.height() / 2f
            canvas.drawRoundRect(rimRect, rimRadius, rimRadius, rimPaint)
        }

        override fun setAlpha(alpha: Int) {
            bodyPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            bodyPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        // Room for the glow, and for the slack the stretch runs into.
        override fun getIntrinsicWidth(): Int =
            (width * (1f + STRETCH) + glowRadius * 2f).toInt()

        override fun getIntrinsicHeight(): Int = height + (glowRadius * 2).toInt()

        private companion object {
            /** How much longer the pane gets at the midpoint of its travel. */
            const val STRETCH = 0.22f

            /** How far in from the rim the refracted core sits. */
            const val CORE_INSET = 0.14f

            const val OVERSHOOT = 1.6f
        }
    }
}
