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

package adrianogba.stario.launcher.sheet.behavior.top

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.math.MathUtils
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import adrianogba.stario.launcher.sheet.behavior.SheetBehavior
import adrianogba.stario.launcher.sheet.behavior.SheetDragHelper
import adrianogba.stario.launcher.ui.Measurements
import kotlin.math.abs

class TopSheetBehavior<V : View>(
    context: Context,
    attrs: AttributeSet?
) : SheetBehavior<V>(context, attrs) {

    private var rememberInterceptResult: Boolean? = null
    private var flung = false
    private var initialX = 0

    override fun calculateCollapsedOffset() {
        collapsedOffset = expandedOffset - Measurements.dpToPx(COLLAPSED_DELTA_DP.toFloat())
    }

    override fun calculateExpandedOffset() {
        val sheet = viewRef?.get()

        if (sheet != null) {
            expandedOffset = parentHeight - sheet.measuredHeight
        }
    }

    override fun dispatchOnSlide(child: V) {
        dispatchOnSlide(child.top)
    }

    override fun getPositionInParent(child: V): Int {
        return child.top
    }

    override fun offset(child: V, offset: Int) {
        ViewCompat.offsetTopAndBottom(child, offset)
    }

    override fun stopNestedScrollLogic(child: V) {
        if (flung) {
            return
        }

        if (child.top == expandedOffset) {
            setStateInternal(STATE_EXPANDED)
            return
        }

        val top: Int
        val targetState: Int
        val currentTop = child.top

        if (lastNestedScroll < 0) {
            top = expandedOffset
            targetState = STATE_EXPANDED
        } else if (lastNestedScroll == 0) {
            if (currentTop > collapsedOffset * 0.5 +
                expandedOffset * 0.5
            ) {
                top = expandedOffset
                targetState = STATE_EXPANDED
            } else {
                top = collapsedOffset
                targetState = STATE_COLLAPSED
            }
        } else {
            top = collapsedOffset
            targetState = STATE_COLLAPSED
        }

        settleChildTo(child, targetState, child.left, top)
    }

    override fun nestedPreFlingLogic(child: V, xvel: Float, yvel: Float): Boolean {
        val currentTop = child.top

        if (state == STATE_DRAGGING) {
            if (yvel > 0 && currentTop < expandedOffset) {
                settleChildTo(child, STATE_EXPANDED, child.left, expandedOffset, 0, yvel.toInt())

                flung = true
            } else if (yvel < 0 && currentTop > collapsedOffset) {
                settleChildTo(child, STATE_COLLAPSED, child.left, collapsedOffset, 0, yvel.toInt())

                flung = true
            }
        }

        return flung
    }

    override fun nestedPreScrollLogic(
        child: V,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray
    ) {
        val currentTop = child.top
        val newTop = currentTop - dy

        if (dy > 0) { // Upward
            if (!target.canScrollVertically(1)) {
                if (newTop >= collapsedOffset) {
                    if (!draggable) {
                        // Prevent dragging
                        return
                    }

                    consumed[1] = dy
                    ViewCompat.offsetTopAndBottom(child, -dy)
                    setStateInternal(STATE_DRAGGING)
                } else {
                    consumed[1] = currentTop - collapsedOffset
                    ViewCompat.offsetTopAndBottom(child, -consumed[1])
                    setStateInternal(STATE_COLLAPSED)
                }
            }
        } else if (dy < 0) { // Downward
            if (newTop > expandedOffset) {
                consumed[1] = currentTop - expandedOffset
                ViewCompat.offsetTopAndBottom(child, -consumed[1])
                setStateInternal(STATE_EXPANDED)
            } else {
                if (!draggable) {
                    // Prevent dragging
                    return
                }

                consumed[1] = dy
                ViewCompat.offsetTopAndBottom(child, -dy)
                setStateInternal(STATE_DRAGGING)
            }
        }

        lastNestedScroll = dy
    }

    override fun startNestedScrollLogic(axes: Int): Boolean {
        flung = false

        return (axes and ViewCompat.SCROLL_AXIS_VERTICAL) != 0
    }

    override fun touchEventLogic(child: V, event: MotionEvent) {
        val helper = dragHelper

        if (helper != null && abs(initial - event.y) > helper.touchSlop) {
            helper.captureChildView(child, event.getPointerId(event.actionIndex))
        }
    }

    override fun interceptTouchEventLogic(
        parent: CoordinatorLayout,
        child: V,
        event: MotionEvent
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID

                // Reset the ignore flag
                if (ignoreEvents) {
                    ignoreEvents = false
                    return false
                }
            }

            MotionEvent.ACTION_DOWN -> {
                initialX = event.x.toInt()
                initial = event.y.toInt()
                rememberInterceptResult = null

                // Only intercept nested scrolling events here if the view not being moved by the
                // ViewDragHelper.
                if (state != STATE_SETTLING) {
                    val scroll = nestedScrollingChildRef?.get()

                    if (scroll == null || parent.isPointInChildBounds(scroll, initialX, initial)) {
                        activePointerId = event.getPointerId(event.actionIndex)
                    }
                }

                ignoreEvents = activePointerId == MotionEvent.INVALID_POINTER_ID
                        && !parent.isPointInChildBounds(child, initialX, initial)
            }
        }

        val absX = abs(initialX - event.x)
        val absY = abs(initial - event.y)

        val helper = dragHelper
        if (helper != null) {
            if (rememberInterceptResult == null &&
                (absX > helper.touchSlop || absY > helper.touchSlop)
            ) {
                rememberInterceptResult = absX < absY
            }

            return rememberInterceptResult == true &&
                    initial - event.y > 0 && absY > helper.touchSlop
        }

        return false
    }

    override fun instantiateDragCallback(): SheetDragHelper.Callback {
        return object : SheetDragHelper.Callback() {

            override fun tryCaptureView(child: View, pointerId: Int): Boolean {
                if (capture) {
                    return true
                }

                if (state == STATE_DRAGGING) {
                    return false
                }

                if (state == STATE_EXPANDED && activePointerId == pointerId) {
                    val scroll = nestedScrollingChildRef?.get()

                    if (scroll != null && scroll.visibility == View.VISIBLE
                        && scroll.canScrollVertically(1)
                    ) {
                        return false
                    }
                }

                return viewRef?.get() === child
            }

            override fun onViewPositionChanged(
                changedView: View, left: Int, top: Int, dx: Int, dy: Int
            ) {
                dispatchOnSlide(top)
            }

            override fun onViewDragStateChanged(state: Int) {
                if (state == ViewDragHelper.STATE_DRAGGING && draggable) {
                    setStateInternal(STATE_DRAGGING)
                }
            }

            override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
                val top: Int
                val targetState: Int

                if (yvel == 0f ||
                    Measurements.dpToPx(
                        ViewConfiguration.getMinimumFlingVelocity() +
                                (ViewConfiguration.getMaximumFlingVelocity() -
                                        ViewConfiguration.getMinimumFlingVelocity()) / 10f
                    ) > abs(yvel)
                ) {
                    val currentTop = releasedChild.top

                    if (currentTop > collapsedOffset / 2) {
                        top = expandedOffset
                        targetState = STATE_EXPANDED
                    } else {
                        top = collapsedOffset
                        targetState = STATE_COLLAPSED
                    }
                } else if (yvel < 0) { // Moving Down
                    top = collapsedOffset
                    targetState = STATE_COLLAPSED
                } else { // Moving Up
                    top = expandedOffset
                    targetState = STATE_EXPANDED
                }

                settleChildTo(releasedChild, targetState, releasedChild.left, top)
            }

            override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
                return MathUtils.clamp(
                    top, collapsedOffset, expandedOffset
                )
            }

            override fun getViewVerticalDragRange(child: View): Int {
                return -collapsedOffset
            }
        }
    }

    override fun isFullySettled(sheet: V, state: Int): Boolean {
        if (state == STATE_COLLAPSED && sheet.top == collapsedOffset) {
            return true
        }

        if (state == STATE_EXPANDED && sheet.top == expandedOffset) {
            return true
        }

        return false
    }

    override fun settleToState(child: View, state: Int, animate: Boolean) {
        val top: Int = when (state) {
            STATE_COLLAPSED -> collapsedOffset
            STATE_EXPANDED -> expandedOffset
            else -> throw IllegalArgumentException("Illegal state argument: $state")
        }

        if (animate) {
            settleChildTo(child, state, child.left, top)
        } else {
            moveChildTo(child, state, child.left, top)
        }
    }
}
