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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.EdgeEffect
import androidx.recyclerview.widget.RecyclerView

open class OverScrollRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RecyclerView(context, attrs, defStyle), OverScroll {

    private val overScrollListeners = ArrayList<OverScrollEffect.OnOverScrollListener>()
    private val edgeEffects = ArrayList<OverScrollEffect<OverScrollRecyclerView>>()
    private val onScrollListeners = ArrayList<OnScrollListener>()
    private val contracts = ArrayList<OverScroll.OverScrollContract>()

    private var overScrollOwner: OverScrollEffect<*>? = null
    private var isLayoutPending = false
    private var touching = false

    // A property, not a setter plus a getter: BottomSheetBehavior reads it as
    // scroll.overscrollPullEdges and Java still sees the accessor pair.
    @OverScrollEffect.Edge
    var overscrollPullEdges: Int =
        OverScrollEffect.PULL_EDGE_BOTTOM or OverScrollEffect.PULL_EDGE_TOP
        set(@OverScrollEffect.Edge value) {
            field = value

            for (effect in edgeEffects) {
                effect.setPullEdges(value)
            }
        }

    init {
        super.setEdgeEffectFactory(object : EdgeEffectFactory() {
            override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
                if (view !is OverScroll) {
                    return EdgeEffect(view.context)
                }

                val effect = when (direction) {
                    DIRECTION_TOP -> OverScrollEffect(
                        this@OverScrollRecyclerView, overscrollPullEdges, OverScrollEffect.PIVOT_TOP
                    )

                    DIRECTION_BOTTOM -> OverScrollEffect(
                        this@OverScrollRecyclerView, overscrollPullEdges, OverScrollEffect.PIVOT_BOTTOM
                    )

                    else -> OverScrollEffect(this@OverScrollRecyclerView, overscrollPullEdges)
                }

                for (listener in overScrollListeners) {
                    effect.addOnOverScrollListener(listener)
                }

                edgeEffects.add(effect)

                return effect
            }
        })

        addOnLayoutChangeListener { _, left, top, right, bottom,
                                    oldLeft, oldTop, oldRight, oldBottom ->
            if (!isLayoutPending) {
                return@addOnLayoutChangeListener
            }

            val sizeChanged = (bottom - top) != (oldBottom - oldTop) ||
                    (right - left) != (oldRight - oldLeft)

            if (sizeChanged) {
                isLayoutPending = false

                if (touching) {
                    superStopNestedScroll()
                }
            }
        }
    }

    // super is unreachable from inside a lambda in Kotlin, and the layout
    // listener above needs it. This is the whole reason the method exists.
    private fun superStopNestedScroll() {
        super.stopNestedScroll()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.action
        touching = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL

        return super.onTouchEvent(event)
    }

    /*
     * If the async adapter updates and triggers a layout pass,
     * RecyclerView usually calls stopNestedScroll(). This kills the drag gesture,
     * making the BottomSheet seem "not responsive".
     *
     * A workaround for THIS specific scenario:
     * - Block stopNestedScroll() if a layout is pending.
     * - Use an OnLayoutChangeListener to detect when the dimensions actually change.
     * - Release the scroll lock only after the layout is stable and the user lets go.
     *
     * This is HIGHLY EXPERIMENTAL AND MAY BREAK, revert this if issues occur.
     * Consecutive stopNestedScroll() without calling startNestedScroll() beforehand
     * **SHOULD** be fine (famous last words)
     */
    override fun stopNestedScroll() {
        if (isLayoutRequested) {
            isLayoutPending = true

            return
        }

        super.stopNestedScroll()
    }

    override fun tryCaptureOverScroll(effect: OverScrollEffect<*>): Boolean {
        val owner = overScrollOwner

        if (owner == null || owner == effect) {
            overScrollOwner = effect

            return true
        }

        return false
    }

    override fun releaseOverScroll(effect: OverScrollEffect<*>) {
        if (overScrollOwner == effect) {
            overScrollOwner = null
        }
    }

    fun addOnOverScrollListener(listener: OverScrollEffect.OnOverScrollListener?) {
        if (listener == null) {
            return
        }

        overScrollListeners.add(listener)

        for (effect in edgeEffects) {
            effect.addOnOverScrollListener(listener)
        }
    }

    fun removeOnOverScrollListener(listener: OverScrollEffect.OnOverScrollListener?) {
        if (listener == null) {
            return
        }

        overScrollListeners.remove(listener)

        for (effect in edgeEffects) {
            effect.removeOnOverScrollListener(listener)
        }
    }

    override fun canScrollHorizontally(direction: Int): Boolean = false

    override fun addOnScrollListener(listener: OnScrollListener) {
        onScrollListeners.add(listener)

        super.addOnScrollListener(listener)
    }

    override fun removeOnScrollListener(listener: OnScrollListener) {
        onScrollListeners.remove(listener)

        super.removeOnScrollListener(listener)
    }

    override fun scrollToPosition(position: Int) {
        for (listener in onScrollListeners) {
            listener.onScrolled(this, 0, 0)
        }

        super.scrollToPosition(position)
    }

    override fun addOverScrollContract(contract: OverScroll.OverScrollContract) {
        contracts.add(contract)
    }

    override fun setEdgeEffectFactory(edgeEffectFactory: EdgeEffectFactory) {
        // override to disable external custom edge effects
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        for (effect in edgeEffects) {
            effect.onTouchEvent(event)
        }

        return super.dispatchTouchEvent(event)
    }

    override fun dispatchDraw(canvas: Canvas) {
        for (contract in contracts) {
            if (contract.prepare(canvas)) {
                break
            }
        }

        super.dispatchDraw(canvas)
    }
}
