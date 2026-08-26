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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.FrameLayout
import adrianogba.stario.launcher.R
import kotlin.math.min

// modification of https://github.com/bosphere/Android-FadingEdgeLayout to support corner fade rounding
class FadingEdgeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var fadeTop = false
    private var fadeBottom = false
    private var fadeLeft = false
    private var fadeRight = false

    private var gradientSizeTop: Int
    private var gradientSizeBottom: Int
    private var gradientSizeLeft: Int
    private var gradientSizeRight: Int
    private var rounded = false

    private val gradientPaintTop = createFadePaint()
    private val gradientPaintBottom = createFadePaint()
    private val gradientPaintLeft = createFadePaint()
    private val gradientPaintRight = createFadePaint()

    private val cornerPaint = createFadePaint()

    private val gradientRectTop = Rect()
    private val gradientRectBottom = Rect()
    private val gradientRectLeft = Rect()
    private val gradientRectRight = Rect()

    private var gradientDirtyFlags = 0

    init {
        val defaultSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, DEFAULT_GRADIENT_SIZE_DP, resources.displayMetrics
        ).toInt()

        if (attrs == null) {
            gradientSizeTop = defaultSize
            gradientSizeBottom = defaultSize
            gradientSizeLeft = defaultSize
            gradientSizeRight = defaultSize
        } else {
            val array = context.obtainStyledAttributes(attrs, R.styleable.FadingEdgeLayout, 0, 0)

            val flags = array.getInt(R.styleable.FadingEdgeLayout_edge, 0)

            fadeTop = (flags and FADE_EDGE_TOP) == FADE_EDGE_TOP
            fadeBottom = (flags and FADE_EDGE_BOTTOM) == FADE_EDGE_BOTTOM
            fadeLeft = (flags and FADE_EDGE_LEFT) == FADE_EDGE_LEFT
            fadeRight = (flags and FADE_EDGE_RIGHT) == FADE_EDGE_RIGHT

            gradientSizeTop = array.getDimensionPixelSize(
                R.styleable.FadingEdgeLayout_size_top, defaultSize
            )
            gradientSizeBottom = array.getDimensionPixelSize(
                R.styleable.FadingEdgeLayout_size_bottom, defaultSize
            )
            gradientSizeLeft = array.getDimensionPixelSize(
                R.styleable.FadingEdgeLayout_size_left, defaultSize
            )
            gradientSizeRight = array.getDimensionPixelSize(
                R.styleable.FadingEdgeLayout_size_right, defaultSize
            )
            rounded = array.getBoolean(R.styleable.FadingEdgeLayout_is_rounded, false)

            if (fadeTop && gradientSizeTop > 0) gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_TOP
            if (fadeLeft && gradientSizeLeft > 0) gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_LEFT
            if (fadeBottom && gradientSizeBottom > 0) gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_BOTTOM
            if (fadeRight && gradientSizeRight > 0) gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_RIGHT

            array.recycle()
        }
    }

    private fun createFadePaint(): Paint {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)

        return paint
    }

    fun setFadeSizes(top: Int, left: Int, bottom: Int, right: Int) {
        if (gradientSizeTop != top) {
            gradientSizeTop = top
            gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_TOP
        }
        if (gradientSizeLeft != left) {
            gradientSizeLeft = left
            gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_LEFT
        }
        if (gradientSizeBottom != bottom) {
            gradientSizeBottom = bottom
            gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_BOTTOM
        }
        if (gradientSizeRight != right) {
            gradientSizeRight = right
            gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_RIGHT
        }

        if (gradientDirtyFlags != 0) {
            invalidate()
        }
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        if (paddingLeft != left) {
            gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_LEFT
        }
        if (paddingTop != top) {
            gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_TOP
        }
        if (paddingRight != right) {
            gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_RIGHT
        }
        if (paddingBottom != bottom) {
            gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_BOTTOM
        }

        super.setPadding(left, top, right, bottom)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        if (w != oldw) {
            gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_LEFT or DIRTY_FLAG_RIGHT
        }

        if (h != oldh) {
            gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_TOP or DIRTY_FLAG_BOTTOM
        }

        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        if (changed) {
            gradientDirtyFlags = gradientDirtyFlags or
                    DIRTY_FLAG_TOP or DIRTY_FLAG_BOTTOM or DIRTY_FLAG_LEFT or DIRTY_FLAG_RIGHT
            invalidate()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val width = width
        val height = height

        if (visibility == GONE || width == 0 || height == 0 ||
            !(fadeTop || fadeBottom || fadeLeft || fadeRight)
        ) {
            super.dispatchDraw(canvas)

            return
        }

        if ((gradientDirtyFlags and DIRTY_FLAG_TOP) != 0) {
            gradientDirtyFlags = gradientDirtyFlags and DIRTY_FLAG_TOP.inv()
            initTopGradient()
        }
        if ((gradientDirtyFlags and DIRTY_FLAG_LEFT) != 0) {
            gradientDirtyFlags = gradientDirtyFlags and DIRTY_FLAG_LEFT.inv()
            initLeftGradient()
        }
        if ((gradientDirtyFlags and DIRTY_FLAG_BOTTOM) != 0) {
            gradientDirtyFlags = gradientDirtyFlags and DIRTY_FLAG_BOTTOM.inv()
            initBottomGradient()
        }
        if ((gradientDirtyFlags and DIRTY_FLAG_RIGHT) != 0) {
            gradientDirtyFlags = gradientDirtyFlags and DIRTY_FLAG_RIGHT.inv()
            initRightGradient()
        }

        val count = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.dispatchDraw(canvas)

        // Corner radii, so the straight edges stop short of a rounded corner and
        // the radial gradient below takes over there.
        val topLeft = cornerRadius(fadeTop && fadeLeft, gradientSizeTop, gradientSizeLeft)
        val topRight = cornerRadius(fadeTop && fadeRight, gradientSizeTop, gradientSizeRight)
        val bottomLeft = cornerRadius(fadeBottom && fadeLeft, gradientSizeBottom, gradientSizeLeft)
        val bottomRight =
            cornerRadius(fadeBottom && fadeRight, gradientSizeBottom, gradientSizeRight)

        drawEdge(canvas, fadeTop, gradientSizeTop, gradientRectTop, gradientPaintTop) {
            it.left += topLeft
            it.right -= topRight
        }
        drawEdge(canvas, fadeBottom, gradientSizeBottom, gradientRectBottom, gradientPaintBottom) {
            it.left += bottomLeft
            it.right -= bottomRight
        }
        drawEdge(canvas, fadeLeft, gradientSizeLeft, gradientRectLeft, gradientPaintLeft) {
            it.top += topLeft
            it.bottom -= bottomLeft
        }
        drawEdge(canvas, fadeRight, gradientSizeRight, gradientRectRight, gradientPaintRight) {
            it.top += topRight
            it.bottom -= bottomRight
        }

        drawCorner(canvas, topLeft, right = false, bottom = false)
        drawCorner(canvas, topRight, right = true, bottom = false)
        drawCorner(canvas, bottomLeft, right = false, bottom = true)
        drawCorner(canvas, bottomRight, right = true, bottom = true)

        canvas.restoreToCount(count)
    }

    private fun cornerRadius(bothEdgesFade: Boolean, first: Int, second: Int): Int =
        if (rounded && bothEdgesFade) min(first, second) else 0

    private inline fun drawEdge(
        canvas: Canvas, fade: Boolean, size: Int, source: Rect, paint: Paint,
        inset: (Rect) -> Unit
    ) {
        if (!fade || size <= 0) {
            return
        }

        val rect = Rect(source)
        inset(rect)

        if (rect.left < rect.right && rect.top < rect.bottom) {
            canvas.drawRect(rect, paint)
        }
    }

    /**
     * Fades the quarter of the corner box between the centre point and the two
     * padded edges, so the two straight gradients meet without a hard seam.
     */
    private fun drawCorner(canvas: Canvas, radius: Int, right: Boolean, bottom: Boolean) {
        if (radius <= 0) {
            return
        }

        val cx = (if (right) width - paddingRight - radius else paddingLeft + radius).toFloat()
        val cy = (if (bottom) height - paddingBottom - radius else paddingTop + radius).toFloat()

        cornerPaint.shader = RadialGradient(
            cx, cy, radius.toFloat(), FADE_COLORS_REVERSE, null, Shader.TileMode.CLAMP
        )

        canvas.drawRect(
            if (right) cx else paddingLeft.toFloat(),
            if (bottom) cy else paddingTop.toFloat(),
            if (right) (width - paddingRight).toFloat() else cx,
            if (bottom) (height - paddingBottom).toFloat() else cy,
            cornerPaint
        )
    }

    private fun initTopGradient() {
        val actualHeight = height - paddingTop - paddingBottom
        val size = min(gradientSizeTop, actualHeight)
        val l = paddingLeft
        val t = paddingTop
        val r = width - paddingRight
        val b = t + size

        gradientRectTop.set(l, t, r, b)
        gradientPaintTop.shader = LinearGradient(
            l.toFloat(), t.toFloat(), l.toFloat(), b.toFloat(),
            FADE_COLORS, null, Shader.TileMode.CLAMP
        )
    }

    private fun initLeftGradient() {
        val actualWidth = width - paddingLeft - paddingRight
        val size = min(gradientSizeLeft, actualWidth)
        val l = paddingLeft
        val t = paddingTop
        val r = l + size
        val b = height - paddingBottom

        gradientRectLeft.set(l, t, r, b)
        gradientPaintLeft.shader = LinearGradient(
            l.toFloat(), t.toFloat(), r.toFloat(), t.toFloat(),
            FADE_COLORS, null, Shader.TileMode.CLAMP
        )
    }

    private fun initBottomGradient() {
        val actualHeight = height - paddingTop - paddingBottom
        val size = min(gradientSizeBottom, actualHeight)
        val l = paddingLeft
        val t = paddingTop + actualHeight - size
        val r = width - paddingRight
        val b = t + size

        gradientRectBottom.set(l, t, r, b)
        gradientPaintBottom.shader = LinearGradient(
            l.toFloat(), t.toFloat(), l.toFloat(), b.toFloat(),
            FADE_COLORS_REVERSE, null, Shader.TileMode.CLAMP
        )
    }

    private fun initRightGradient() {
        val actualWidth = width - paddingLeft - paddingRight
        val size = min(gradientSizeRight, actualWidth)
        val l = paddingLeft + actualWidth - size
        val t = paddingTop
        val r = l + size
        val b = height - paddingBottom

        gradientRectRight.set(l, t, r, b)
        gradientPaintRight.shader = LinearGradient(
            l.toFloat(), t.toFloat(), r.toFloat(), t.toFloat(),
            FADE_COLORS_REVERSE, null, Shader.TileMode.CLAMP
        )
    }

    companion object {
        // Mirrors the edge enum in attrs.xml
        const val FADE_EDGE_TOP = 1
        const val FADE_EDGE_BOTTOM = 2
        const val FADE_EDGE_LEFT = 4
        const val FADE_EDGE_RIGHT = 8

        private const val DEFAULT_GRADIENT_SIZE_DP = 80f

        private const val DIRTY_FLAG_TOP = 1
        private const val DIRTY_FLAG_BOTTOM = 2
        private const val DIRTY_FLAG_LEFT = 4
        private const val DIRTY_FLAG_RIGHT = 8

        private val FADE_COLORS = intArrayOf(Color.TRANSPARENT, Color.BLACK)
        private val FADE_COLORS_REVERSE = intArrayOf(Color.BLACK, Color.TRANSPARENT)
    }
}
