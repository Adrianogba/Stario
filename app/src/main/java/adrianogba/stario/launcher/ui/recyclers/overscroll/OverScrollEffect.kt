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

package adrianogba.stario.launcher.ui.recyclers.overscroll

import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.EdgeEffect
import androidx.annotation.IntDef
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.utils.UiUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class OverScrollEffect<V> @JvmOverloads constructor(
    private val view: V,
    @Edge private var edges: Int,
    private var pivot: Int = PIVOT_UNSPECIFIED
) : EdgeEffect(view.context) where V : View, V : OverScroll {

    @IntDef(flag = true, value = [PULL_EDGE_TOP, PULL_EDGE_BOTTOM])
    @Retention(AnnotationRetention.SOURCE)
    annotation class Edge

    private val overScrollListeners = ArrayList<OnOverScrollListener>()
    private val bounds = Rect()

    private var receivedMoveEvent = false
    private var isCanvasCaptured = false
    private var oldState: OverScrollState? = null
    private var state = OverScrollState.IDLE
    private var initialTouchY = -1f

    /**
     * Was an ObjectDelegate<Float>, which meant a boxed nullable float behind
     * every read. A property with a setter does the same job.
     */
    private var factor: Float = 0f
        set(value) {
            field = value

            notifyOverScrolled(value)
            view.invalidate()
        }

    private val touchSlop: Float
    private val maxFlingVelocity: Float
    private val animation: SpringAnimation

    init {
        val configuration = ViewConfiguration.get(view.context)
        touchSlop = configuration.scaledTouchSlop.toFloat()
        maxFlingVelocity = configuration.scaledMaximumFlingVelocity.toFloat()

        val spring = SpringForce()
        spring.finalPosition = 0f
        spring.stiffness = SPRING_STIFFNESS
        spring.dampingRatio = SPRING_DAMPING_RATIO

        // Animation behaves better with bigger numbers, so artificially increase the
        // factor by an arbitrary value when animating
        animation = SpringAnimation(Any(), object : FloatPropertyCompat<Any>("") {
            override fun getValue(`object`: Any): Float = factor * SPRING_FACTOR_MULTIPLIER

            override fun setValue(`object`: Any, value: Float) {
                factor = value / SPRING_FACTOR_MULTIPLIER
            }
        })
        animation.spring = spring
        animation.addEndListener { _, canceled, _, _ ->
            if (!canceled) {
                view.postOnAnimation { finish() }
            }
        }

        view.addOverScrollContract { canvas -> prepareCanvas(canvas) }
    }

    private fun prepareCanvas(canvas: Canvas): Boolean {
        if (state == OverScrollState.IDLE || !view.tryCaptureOverScroll(this)) {
            isCanvasCaptured = false

            return false
        }

        if (pivot == PIVOT_UNSPECIFIED) {
            if (!view.canScrollVertically(1)) {
                if (view.canScrollVertically(-1)) {
                    pivot = PIVOT_BOTTOM
                }
            } else if (!view.canScrollVertically(-1)) {
                pivot = PIVOT_TOP
            }
        }

        val maxTranslation = min(
            (canvas.height * TRANSLATE_MULTIPLIER).toInt(),
            Measurements.dpToPx(MAX_TRANSLATION_DP)
        )

        val factorValue = min(1f, factor)
        val easedFactor = 1 - (1 - factorValue).pow(3f)
        val translationY = easedFactor * maxTranslation
        val scaleY = 1 + factorValue.pow(0.8f) * SCALE_MULTIPLIER

        if (pivot == PIVOT_BOTTOM) {
            canvas.translate(0f, -translationY)
            canvas.scale(1f, scaleY, bounds.centerX().toFloat(), bounds.height().toFloat())
        } else if (pivot == PIVOT_TOP) {
            canvas.translate(0f, translationY)
            canvas.scale(1f, scaleY, bounds.centerX().toFloat(), 0f)
        } else {
            isCanvasCaptured = false

            return false
        }

        isCanvasCaptured = true

        return true
    }

    fun setPullEdges(@Edge edges: Int) {
        this.edges = edges
        factor = 0f
    }

    override fun setSize(width: Int, height: Int) {
        super.setSize(width, height)

        bounds.set(0, 0, width, height)
        factor = 0f
    }

    override fun isFinished(): Boolean = state == OverScrollState.IDLE

    override fun finish() {
        if (animation.isRunning) {
            animation.cancel()
        }

        view.releaseOverScroll(this)

        state = OverScrollState.IDLE
        notifyStateChange(state)

        super.finish()
    }

    override fun getDistance(): Float {
        if (!receivedMoveEvent || !isPullAllowed()) {
            return 0f
        }

        return factor
    }

    private fun isPullAllowed(): Boolean = when (pivot) {
        PIVOT_TOP -> (edges and PULL_EDGE_TOP) == PULL_EDGE_TOP
        PIVOT_BOTTOM -> (edges and PULL_EDGE_BOTTOM) == PULL_EDGE_BOTTOM
        else -> false
    }

    override fun onPullDistance(deltaDistance: Float, displacement: Float): Float {
        if (!receivedMoveEvent || !isPullAllowed() || !UiUtils.areAnimationsOn()) {
            return 0f
        }

        // The original had a second guard here returning deltaDistance when the
        // pull was not allowed for the current pivot. The check above already
        // covers that, so it never ran.
        val newFactor = deltaDistance + factor
        val delta = max(0f, newFactor) - factor

        onPull(delta)

        if (newFactor <= 0) {
            finish()
        }

        return delta
    }

    override fun onPull(deltaDistance: Float) {
        if (!receivedMoveEvent || !isPullAllowed()) {
            return
        }

        if (state == OverScrollState.IDLE && !view.tryCaptureOverScroll(this)) {
            return
        }

        state = OverScrollState.OVER_SCROLLING
        notifyStateChange(state)

        if (animation.isRunning) {
            animation.cancel()
        }

        factor += deltaDistance
    }

    override fun onRelease() {
        if (animation.isRunning) {
            return
        }

        if (factor == 0f) {
            finish()

            return
        }

        state = OverScrollState.SETTLING
        notifyStateChange(state)

        animation.setStartVelocity(0f)
            .setStartValue(factor * SPRING_FACTOR_MULTIPLIER)
            .start()
    }

    override fun onAbsorb(velocity: Int) {
        if (!UiUtils.areAnimationsOn()) {
            return
        }

        if (state == OverScrollState.IDLE && !view.tryCaptureOverScroll(this)) {
            return
        }

        state = OverScrollState.SETTLING
        notifyStateChange(state)

        if (animation.isRunning) {
            animation.cancel()
        }

        val fraction = velocity / maxFlingVelocity
        val pulled = factor > 0

        animation.setStartVelocity(
            if (pulled) velocity * VELOCITY_MULTIPLIER
            else velocity * VELOCITY_MULTIPLIER_FLING * (1 - fraction)
        )
            .setStartValue(
                if (pulled) factor * SPRING_FACTOR_MULTIPLIER
                else fraction.pow(1.7f) * SPRING_FACTOR_MULTIPLIER / 2
            )
            .start()
    }

    override fun draw(canvas: Canvas): Boolean = isCanvasCaptured && !isFinished()

    private fun notifyStateChange(state: OverScrollState) {
        if (oldState == state || !isPullAllowed()) {
            return
        }

        val edge = if (pivot == PIVOT_TOP) PULL_EDGE_TOP else PULL_EDGE_BOTTOM

        for (listener in overScrollListeners) {
            listener.onOverScrollStateChanged(edge, state)
        }

        oldState = state
    }

    private fun notifyOverScrolled(factor: Float) {
        if (pivot == PIVOT_UNSPECIFIED) {
            return
        }

        val edge = if (pivot == PIVOT_TOP) PULL_EDGE_TOP else PULL_EDGE_BOTTOM

        for (listener in overScrollListeners) {
            listener.onOverScrolled(edge, factor * factor)
        }
    }

    fun addOnOverScrollListener(listener: OnOverScrollListener?) {
        if (listener != null) {
            overScrollListeners.add(listener)
        }
    }

    fun removeOnOverScrollListener(listener: OnOverScrollListener?) {
        if (listener != null) {
            overScrollListeners.remove(listener)
        }
    }

    internal fun onTouchEvent(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> initialTouchY = event.rawY

            MotionEvent.ACTION_MOVE -> {
                receivedMoveEvent = true

                if (pivot == PIVOT_TOP || pivot == PIVOT_BOTTOM) {
                    return
                }

                if (initialTouchY == -1f) {
                    initialTouchY = event.rawY

                    return
                }

                val dy = event.rawY - initialTouchY

                if (abs(dy) >= touchSlop && !view.canScrollVertically(if (dy > 0) 1 else -1)) {
                    initialTouchY = -1f
                    pivot = if (dy > 0) PIVOT_TOP else PIVOT_BOTTOM
                }
            }

            MotionEvent.ACTION_UP -> {
                receivedMoveEvent = false
                initialTouchY = -1f
            }

            MotionEvent.ACTION_CANCEL -> {
                view.releaseOverScroll(this)
                receivedMoveEvent = false
                initialTouchY = -1f
            }
        }
    }

    enum class OverScrollState {
        IDLE,
        OVER_SCROLLING,
        SETTLING
    }

    interface OnOverScrollListener {
        fun onOverScrollStateChanged(@Edge edge: Int, state: OverScrollState) {
        }

        fun onOverScrolled(@Edge edge: Int, factor: Float) {
        }
    }

    companion object {
        const val PULL_EDGE_TOP = 0b01
        const val PULL_EDGE_BOTTOM = 0b10

        internal const val PIVOT_TOP = 0b10
        internal const val PIVOT_BOTTOM = 0b01

        private const val PIVOT_UNSPECIFIED = 0b00

        private const val VELOCITY_MULTIPLIER = 3f
        private const val VELOCITY_MULTIPLIER_FLING = 1.2f
        private const val SPRING_FACTOR_MULTIPLIER = 1000f
        private const val SPRING_STIFFNESS = 300f
        private const val SPRING_DAMPING_RATIO = 1f
        private const val SCALE_MULTIPLIER = 0.05f
        private const val TRANSLATE_MULTIPLIER = 0.1f
        private const val MAX_TRANSLATION_DP = 100f
    }
}
