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

package adrianogba.stario.launcher.ui.common.text

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatTextView
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.utils.animation.Animation

class PulsingTextView : AppCompatTextView {
    // Nullable rather than lateinit: the View superclass constructor can call
    // invalidate() before these are assigned, which the original relied on.
    private var animator: ValueAnimator? = null
    private var gradientPaint: Paint? = null

    private var pulsating = true
    private var evenColors: IntArray? = null
    private var oddColors: IntArray? = null
    private var offset = 0f

    constructor(context: Context) : super(context) {
        setup()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        setup()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr) {
        setup()
    }

    private fun setup() {
        offset = 0f
        pulsating = true

        val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        gradientPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        this.gradientPaint = gradientPaint

        val animator = ValueAnimator.ofFloat(0f, 0f)
            .setDuration(Animation.SUSTAINED.duration.toLong())
        animator.interpolator = LinearInterpolator()
        animator.repeatCount = ValueAnimator.INFINITE
        this.animator = animator
    }

    fun setPulsating(pulsating: Boolean) {
        this.pulsating = pulsating

        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        animator?.start()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()

        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        post { calculateGradients() }
    }

    override fun invalidate() {
        animator?.let { offset = it.animatedFraction * 2f }

        super.invalidate()
    }

    private fun calculateGradients() {
        val colors = ArrayList<Int>()
        val fadeLength = Measurements.dpToPx(FADE_LENGTH.toFloat())
        var width = this.width

        while (width > -fadeLength) {
            colors.add(Color.BLACK)
            colors.add(Color.argb(0.5f, 0f, 0f, 0f))

            width -= fadeLength
        }

        val evenColors = IntArray(colors.size)
        val oddColors = IntArray(colors.size)

        for (index in 0 until colors.size - 1) {
            val color = colors[index]

            evenColors[index] = color
            oddColors[index + 1] = color
        }

        val lastColor = colors[colors.size - 1]
        evenColors[colors.size - 1] = lastColor
        oddColors[0] = lastColor

        this.evenColors = evenColors
        this.oddColors = oddColors
    }

    override fun onDraw(canvas: Canvas) {
        if (!pulsating) {
            super.onDraw(canvas)

            return
        }

        if (visibility != VISIBLE) {
            super.onDraw(canvas)

            return
        }

        if (evenColors != null) {
            initGradient()

            val count = canvas.saveLayer(
                0f, 0f, width.toFloat(), height.toFloat(), null
            )
            super.onDraw(canvas)

            canvas.drawRect(
                0f, 0f, width.toFloat(), height.toFloat(), gradientPaint!!
            )

            canvas.restoreToCount(count)
        }

        invalidate()
    }

    private fun initGradient() {
        val gradient = LinearGradient(
            0f, 0f, width.toFloat(), height * 0.3f,
            if (offset.toInt() % 2 == 0) evenColors!! else oddColors!!,
            generatePositions(), Shader.TileMode.CLAMP
        )
        gradientPaint!!.shader = gradient
    }

    private fun generatePositions(): FloatArray {
        val fadeLength = Measurements.dpToPx(FADE_LENGTH.toFloat()).toFloat()
        val positions = FloatArray(evenColors!!.size)

        val interval = (1f + fadeLength / width) / positions.size

        for (index in positions.indices) {
            positions[index] = interval * ((index - 1) + offset % 1)
        }

        return positions
    }

    private companion object {
        private const val FADE_LENGTH = 50
    }
}
