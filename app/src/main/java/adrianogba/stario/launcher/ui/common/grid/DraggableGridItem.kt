/*
 * Copyright (C) 2026 Răzvan Albu
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

package adrianogba.stario.launcher.ui.common.grid

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.Measurements
import kotlin.math.max
import kotlin.math.min

class DraggableGridItem(context: Context) : FrameLayout(context) {
    private val borderPaint: Paint
    private val handlePaint: Paint

    private var isVisualResizeEnabled = false
    private var alphaAnimator: ValueAnimator? = null
    private var visualHeight = 0f
    private var visualWidth = 0f
    private var borderAlpha = 0f

    // Read and written from DynamicGridLayout, which is still Java, so these stay fields.
    @JvmField
    var itemId: String? = null

    @JvmField
    var minColSpan = 1

    @JvmField
    var minWidth = -1

    @JvmField
    var maxColSpan = -1

    @JvmField
    var maxWidth = -1

    @JvmField
    var minRowSpan = 1

    @JvmField
    var minHeight = -1

    @JvmField
    var maxRowSpan = -1

    @JvmField
    var maxHeight = -1

    // Package private in Java. Kotlin has no package private, and DynamicGridLayout
    // needs to reach this, so it is public.
    var isResizingActive = false
        set(value) {
            field = value

            animateBorderAlpha(if (value) IDLE_ALPHA else 0f)
        }

    init {
        if (context !is ThemedActivity) {
            throw RuntimeException("Parent activity is not of type ThemedActivity.")
        }

        val color = context.getAttributeData(com.google.android.material.R.attr.colorPrimaryFixed)

        borderPaint = Paint()
        borderPaint.color = color
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = Measurements.dpToPx(STROKE_WIDTH_DP).toFloat()

        handlePaint = Paint()
        handlePaint.color = color
        handlePaint.style = Paint.Style.STROKE
        handlePaint.strokeCap = Paint.Cap.ROUND
        handlePaint.strokeWidth = Measurements.dpToPx(HANDLE_STROKE_WIDTH_DP).toFloat()

        setWillNotDraw(false)

        val padding = Measurements.dpToPx(PADDING_DP)
        setPadding(padding, padding, padding, padding)
    }

    // Package private in Java, see the note on isResizingActive.
    fun animateToState(state: Byte) {
        if (!isResizingActive) {
            return
        }

        when (state) {
            STATE_IDLE -> animateBorderAlpha(IDLE_ALPHA)
            STATE_ACTIVE -> animateBorderAlpha(ACTIVE_ALPHA)
            STATE_INACTIVE -> animateBorderAlpha(0f)
        }
    }

    private fun animateBorderAlpha(targetAlpha: Float) {
        alphaAnimator?.cancel()

        val animator = ValueAnimator.ofFloat(borderAlpha, targetAlpha)
        animator.duration = ALPHA_TRANSITION_DURATION
        animator.addUpdateListener { animation ->
            borderAlpha = animation.animatedValue as Float

            invalidate()
        }

        alphaAnimator = animator
        animator.start()
    }

    fun setVisualResizeBounds(width: Float, height: Float) {
        this.isVisualResizeEnabled = true
        this.visualWidth = width
        this.visualHeight = height

        invalidate()
    }

    fun animateVisualResize(targetW: Float, targetH: Float, endAction: Runnable?) {
        val anim = ValueAnimator.ofFloat(0f, 1f)
        val startW = currentVisualWidth()
        val startH = currentVisualHeight()

        isVisualResizeEnabled = true

        anim.duration = 200L
        anim.addUpdateListener { animation ->
            val frac = animation.animatedFraction
            visualWidth = startW + (targetW - startW) * frac
            visualHeight = startH + (targetH - startH) * frac

            invalidate()
        }

        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                isVisualResizeEnabled = false

                endAction?.run()
            }
        })

        anim.start()
    }

    // Package private in Java, see the note on isResizingActive.
    fun isResizeHandleTouched(x: Float, y: Float): Boolean {
        if (!isResizingActive) {
            return false
        }

        val handleSize = Measurements.dpToPx(HANDLE_SIZE_DP)

        val width = currentVisualWidth()
        val height = currentVisualHeight()

        val resizeW = canResizeWidth()
        val resizeH = canResizeHeight()

        if (resizeW && resizeH) {
            return x > width - handleSize && y > height - handleSize
        }

        if (resizeW) {
            return x > width - handleSize &&
                    y > height / 2f - handleSize / 2f &&
                    y < height / 2f + handleSize / 2f
        }

        if (resizeH) {
            return y > height - handleSize &&
                    x > width / 2f - handleSize / 2f &&
                    x < width / 2f + handleSize / 2f
        }

        return false
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)

        if (childCount > 1) {
            throw IllegalStateException("DraggableGridItem can host only one direct child.")
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (parent !is DynamicGridLayout) {
            throw IllegalStateException(
                "DraggableGridItem can only be added to a DynamicGridLayout."
            )
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        if (borderAlpha <= 0) {
            return
        }

        val cornerSize = Measurements.dpToPx(CORNER_RADIUS_DP)
        val strokeWidth = Measurements.dpToPx(HANDLE_STROKE_WIDTH_DP)

        val currentWidth = currentVisualWidth()
        val currentHeight = currentVisualHeight()

        val baseAlpha = 255
        borderPaint.alpha = (baseAlpha * borderAlpha).toInt()
        handlePaint.alpha = (baseAlpha * min(1f, borderAlpha / IDLE_ALPHA)).toInt()

        val rect = RectF(
            strokeWidth / 2f,
            strokeWidth / 2f,
            currentWidth - strokeWidth / 2f,
            currentHeight - strokeWidth / 2f
        )

        canvas.drawRoundRect(rect, cornerSize.toFloat(), cornerSize.toFloat(), borderPaint)

        val resizeW = canResizeWidth()
        val resizeH = canResizeHeight()

        if (!resizeW && !resizeH) {
            return
        }

        if (resizeW && resizeH) {
            val handleRect = RectF(
                currentWidth - 2 * cornerSize - strokeWidth / 2f,
                currentHeight - 2 * cornerSize - strokeWidth / 2f,
                currentWidth - strokeWidth / 2f,
                currentHeight - strokeWidth / 2f
            )

            canvas.drawArc(handleRect, 20f, 50f, false, handlePaint)

            return
        }

        if (resizeW) {
            canvas.drawLine(
                currentWidth - strokeWidth / 2f,
                currentHeight / 2f - cornerSize / 2f,
                currentWidth - strokeWidth / 2f,
                currentHeight / 2f + cornerSize / 2f,
                handlePaint
            )

            return
        }

        canvas.drawLine(
            currentWidth / 2f - cornerSize / 2f,
            currentHeight - strokeWidth / 2f,
            currentWidth / 2f + cornerSize / 2f,
            currentHeight - strokeWidth / 2f,
            handlePaint
        )
    }

    private fun currentVisualWidth(): Float =
        if (isVisualResizeEnabled) visualWidth else width.toFloat()

    private fun currentVisualHeight(): Float =
        if (isVisualResizeEnabled) visualHeight else height.toFloat()

    private fun canResizeWidth(): Boolean {
        val grid = parent as DynamicGridLayout
        val cellWidth = grid.getCellWidth()

        val minComputedWidth = computeMinimumSize(minWidth, minColSpan, cellWidth)
        val maxComputedWidth = computeMaximumSize(maxWidth, maxColSpan, cellWidth, grid.width)

        if (maxComputedWidth - minComputedWidth < cellWidth) {
            return false
        }

        if (maxColSpan > 0) {
            return min(maxColSpan, grid.getColumnCount()) > minColSpan
        }

        return grid.getColumnCount() > minColSpan
    }

    private fun canResizeHeight(): Boolean {
        val grid = parent as DynamicGridLayout
        val cellHeight = grid.getCellHeight()

        val minComputedHeight = computeMinimumSize(minHeight, minRowSpan, cellHeight)
        val maxComputedHeight = computeMaximumSize(maxHeight, maxRowSpan, cellHeight, grid.height)

        if (maxComputedHeight - minComputedHeight < cellHeight) {
            return false
        }

        if (maxRowSpan > 0) {
            return min(maxRowSpan, grid.getRowCount()) > minRowSpan
        }

        return grid.getRowCount() > minRowSpan
    }

    // Unused, exactly as in the Java version. Kept so behaviour stays identical.
    @Suppress("unused")
    private fun canResize(): Boolean = canResizeWidth() || canResizeHeight()

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = isResizingActive

    companion object {
        const val STATE_ACTIVE: Byte = 4
        const val STATE_INACTIVE: Byte = 2
        const val STATE_IDLE: Byte = 1

        private const val HANDLE_STROKE_WIDTH_DP = 6f
        private const val HANDLE_SIZE_DP = 40f
        private const val CORNER_RADIUS_DP = 20f
        private const val STROKE_WIDTH_DP = 2f
        private const val ALPHA_TRANSITION_DURATION = 200L
        private const val IDLE_ALPHA = 0.4f
        private const val ACTIVE_ALPHA = 1.0f
        private const val PADDING_DP = 5f

        private fun computeMinimumSize(explicit: Int, span: Int, cellSize: Int): Int {
            if (explicit > 0) {
                if (span > 0) {
                    return max(explicit, span * cellSize)
                }

                return explicit
            }

            if (span > 0) {
                return span * cellSize
            }

            return 0
        }

        private fun computeMaximumSize(
            explicit: Int, span: Int,
            cellSize: Int, fallback: Int
        ): Int {
            if (explicit > 0) {
                if (span > 0) {
                    return min(explicit, span * cellSize)
                }

                return explicit
            }

            if (span > 0) {
                return span * cellSize
            }

            return fallback
        }
    }
}
