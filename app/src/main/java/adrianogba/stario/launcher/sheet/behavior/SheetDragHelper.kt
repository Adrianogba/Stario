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

// Modification of ViewDragHelper

package adrianogba.stario.launcher.sheet.behavior

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.Interpolator
import android.widget.OverScroller
import androidx.annotation.Px
import androidx.core.view.ViewCompat
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * ViewDragHelper is a utility class for writing custom ViewGroups. It offers a number
 * of useful operations and state tracking for allowing a user to drag and reposition
 * views within their parent ViewGroup.
 *
 * Apps should use [SheetDragHelper.create] to get a new instance.
 * This will allow VDH to use internal compatibility implementations for different
 * platform versions.
 */
class SheetDragHelper private constructor(
    context: Context,
    private val mParentView: ViewGroup,
    private val mCallback: Callback
) {

    // Current drag state; idle, dragging or settling
    private var mDragState = 0

    /**
     * The minimum distance in pixels that the user must travel to initiate a drag.
     */
    // Distance to travel before a drag may begin
    @get:Px
    val touchSlop: Int

    // Last known position/pointer tracking
    private var mActivePointerId = INVALID_POINTER
    private var mInitialMotionX: FloatArray? = null
    private var mInitialMotionY: FloatArray? = null
    private var mLastMotionX: FloatArray? = null
    private var mLastMotionY: FloatArray? = null
    private var mPointersDown = 0

    private var mVelocityTracker: VelocityTracker? = null
    private val mMaxVelocity: Float
    private val mMinVelocity: Float

    private val mScroller: OverScroller

    private var mCapturedView: View? = null

    private val mSetIdleRunnable = Runnable { setDragState(STATE_IDLE) }

    init {
        val vc = ViewConfiguration.get(context)

        touchSlop = vc.scaledTouchSlop
        mMaxVelocity = vc.scaledMaximumFlingVelocity.toFloat()
        mMinVelocity = vc.scaledMinimumFlingVelocity.toFloat()
        mScroller = OverScroller(context, sInterpolator)
    }

    /**
     * A Callback is used as a communication channel with the ViewDragHelper back to the
     * parent view using it. `on*`methods are invoked on siginficant events and several
     * accessor methods are expected to provide the ViewDragHelper with more information
     * about the state of the parent view upon request. The callback also makes decisions
     * governing the range and draggability of child views.
     */
    abstract class Callback {

        /**
         * Called when the drag state changes. See the `STATE_*` constants
         * for more information.
         *
         * @param state The new drag state
         */
        open fun onViewDragStateChanged(state: Int) {
        }

        /**
         * Called when the captured view's position changes as the result of a drag or settle.
         *
         * @param changedView View whose position changed
         * @param left        New X coordinate of the left edge of the view
         * @param top         New Y coordinate of the top edge of the view
         * @param dx          Change in X position from the last call
         * @param dy          Change in Y position from the last call
         */
        open fun onViewPositionChanged(
            changedView: View, left: Int, top: Int, @Px dx: Int,
            @Px dy: Int
        ) {
        }

        /**
         * Called when a child view is captured for dragging or settling. The ID of the pointer
         * currently dragging the captured view is supplied. If activePointerId is
         * identified as [INVALID_POINTER] the capture is programmatic instead of
         * pointer-initiated.
         *
         * @param capturedChild   Child view that was captured
         * @param activePointerId Pointer id tracking the child capture
         */
        open fun onViewCaptured(capturedChild: View, activePointerId: Int) {
        }

        /**
         * Called when the child view is no longer being actively dragged.
         * The fling velocity is also supplied, if relevant. The velocity values may
         * be clamped to system minimums or maximums.
         *
         * Calling code may decide to fling or otherwise release the view to let it
         * settle into place. If the Callback invokes one of these methods, the
         * ViewDragHelper will enter [STATE_SETTLING] and the view capture will not
         * fully end until it comes to a complete stop. If neither of these methods is
         * invoked before `onViewReleased` returns, the view will stop in place and the
         * ViewDragHelper will return to [STATE_IDLE].
         *
         * @param releasedChild The captured child view now being released
         * @param xvel          X velocity of the pointer as it left the screen in pixels per second.
         * @param yvel          Y velocity of the pointer as it left the screen in pixels per second.
         */
        open fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
        }

        /**
         * Called to determine the Z-order of child views.
         *
         * @param index the ordered position to query for
         * @return index of the view that should be ordered at position `index`
         */
        open fun getOrderedChildIndex(index: Int): Int {
            return index
        }

        /**
         * Return the magnitude of a draggable child view's horizontal range of motion in pixels.
         * This method should return 0 for views that cannot move horizontally.
         *
         * @param child Child view to check
         * @return range of horizontal motion in pixels
         */
        open fun getViewHorizontalDragRange(child: View): Int {
            return 0
        }

        /**
         * Return the magnitude of a draggable child view's vertical range of motion in pixels.
         * This method should return 0 for views that cannot move vertically.
         *
         * @param child Child view to check
         * @return range of vertical motion in pixels
         */
        open fun getViewVerticalDragRange(child: View): Int {
            return 0
        }

        /**
         * Called when the user's input indicates that they want to capture the given child view
         * with the pointer indicated by pointerId. The callback should return true if the user
         * is permitted to drag the given view with the indicated pointer.
         *
         * ViewDragHelper may call this method multiple times for the same view even if
         * the view is already captured; this indicates that a new pointer is trying to take
         * control of the view.
         *
         * If this method returns true, a call to [onViewCaptured] will follow if the
         * capture is successful.
         *
         * @param child     Child the user is attempting to capture
         * @param pointerId ID of the pointer attempting the capture
         * @return true if capture should be allowed, false otherwise
         */
        abstract fun tryCaptureView(child: View, pointerId: Int): Boolean

        /**
         * Restrict the motion of the dragged child view along the horizontal axis.
         * The default implementation does not allow horizontal motion; the extending
         * class must override this method and provide the desired clamping.
         *
         * @param child Child view being dragged
         * @param left  Attempted motion along the X axis
         * @param dx    Proposed change in position for left
         * @return The new clamped position for left
         */
        open fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
            return 0
        }

        /**
         * Restrict the motion of the dragged child view along the vertical axis.
         * The default implementation does not allow vertical motion; the extending
         * class must override this method and provide the desired clamping.
         *
         * @param child Child view being dragged
         * @param top   Attempted motion along the Y axis
         * @param dy    Proposed change in position for top
         * @return The new clamped position for top
         */
        open fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
            return 0
        }
    }

    /**
     * Capture a specific child view for dragging within the parent. The callback will be notified
     * but [Callback.tryCaptureView] will not be asked permission to capture this view.
     *
     * @param childView       Child view to capture
     * @param activePointerId ID of the pointer that is dragging the captured child view
     */
    fun captureChildView(childView: View, activePointerId: Int) {
        require(childView.parent === mParentView) {
            ("captureChildView: parameter must be a descendant "
                    + "of the ViewDragHelper's tracked parent view (" + mParentView + ")")
        }

        mCapturedView = childView
        mActivePointerId = activePointerId
        mCallback.onViewCaptured(childView, activePointerId)
        setDragState(STATE_DRAGGING)
    }

    /**
     * The result of a call to this method is equivalent to
     * [processTouchEvent] receiving an ACTION_CANCEL event.
     */
    fun cancel() {
        mActivePointerId = INVALID_POINTER
        clearMotionHistory()

        mVelocityTracker?.let {
            it.recycle()
            mVelocityTracker = null
        }
    }

    /**
     * [cancel], but also abort all motion in progress and snap to the end of any
     * animation.
     */
    fun abort() {
        cancel()

        if (mDragState == STATE_SETTLING) {
            val oldX = mScroller.currX
            val oldY = mScroller.currY
            mScroller.abortAnimation()
            val newX = mScroller.currX
            val newY = mScroller.currY
            mCallback.onViewPositionChanged(mCapturedView!!, newX, newY, newX - oldX, newY - oldY)
        }

        setDragState(STATE_IDLE)
    }

    /**
     * Animate the view `child` to the given (left, top) position.
     * If this method returns true, the caller should invoke [continueSettling]
     * on each subsequent frame to continue the motion until it returns false. If this method
     * returns false there is no further work to do to complete the movement.
     *
     * @param child     Child view to capture and animate
     * @param finalLeft Final left position of child
     * @param finalTop  Final top position of child
     * @return true if animation should continue through [continueSettling] calls
     */
    fun smoothSlideViewTo(child: View, finalLeft: Int, finalTop: Int): Boolean {
        val velocityTracker = mVelocityTracker

        if (velocityTracker != null) {
            return smoothSlideViewTo(
                child, finalLeft, finalTop,
                velocityTracker.getXVelocity(mActivePointerId).toInt(),
                velocityTracker.getYVelocity(mActivePointerId).toInt()
            )
        }

        return smoothSlideViewTo(child, finalLeft, finalTop, 0, 0)
    }

    /**
     * Animate the view `child` to the given (left, top) position.
     * If this method returns true, the caller should invoke [continueSettling]
     * on each subsequent frame to continue the motion until it returns false. If this method
     * returns false there is no further work to do to complete the movement.
     *
     * @param child     Child view to capture and animate
     * @param finalLeft Final left position of child
     * @param finalTop  Final top position of child
     * @param xvel      Horizontal velocity
     * @param yvel      Vertical velocity
     * @return true if animation should continue through [continueSettling] calls
     */
    fun smoothSlideViewTo(child: View, finalLeft: Int, finalTop: Int, xvel: Int, yvel: Int): Boolean {
        mCapturedView = child
        mActivePointerId = INVALID_POINTER

        val continueSliding = forceSettleCapturedViewAt(finalLeft, finalTop, xvel, yvel)
        if (!continueSliding && mDragState == STATE_IDLE && mCapturedView != null) {
            // If we're in an IDLE state to begin with and aren't moving anywhere, we
            // end up having a non-null capturedView with an IDLE dragState
            mCapturedView = null
        }

        return continueSliding
    }

    /**
     * Settle the captured view at the given (left, top) position.
     *
     * @param finalLeft Target left position for the captured view
     * @param finalTop  Target top position for the captured view
     * @param xvel      Horizontal velocity
     * @param yvel      Vertical velocity
     * @return true if animation should continue through [continueSettling] calls
     */
    private fun forceSettleCapturedViewAt(finalLeft: Int, finalTop: Int, xvel: Int, yvel: Int): Boolean {
        val capturedView = mCapturedView!!
        val startLeft = capturedView.left
        val startTop = capturedView.top
        val dx = finalLeft - startLeft
        val dy = finalTop - startTop

        if (dx == 0 && dy == 0) {
            // Nothing to do. Send callbacks, be done.
            mScroller.abortAnimation()
            setDragState(STATE_IDLE)
            return false
        }

        val duration = computeSettleDuration(capturedView, dx, dy, xvel, yvel)
        mScroller.startScroll(startLeft, startTop, dx, dy, duration)

        setDragState(STATE_SETTLING)
        return true
    }

    private fun computeSettleDuration(child: View, dx: Int, dy: Int, xvel: Int, yvel: Int): Int {
        val clampedXVel = clampMag(xvel, mMinVelocity.toInt(), mMaxVelocity.toInt())
        val clampedYVel = clampMag(yvel, mMinVelocity.toInt(), mMaxVelocity.toInt())
        val absDx = abs(dx)
        val absDy = abs(dy)
        val absXVel = abs(clampedXVel)
        val absYVel = abs(clampedYVel)
        val addedVel = absXVel + absYVel
        val addedDistance = absDx + absDy

        val xweight = if (clampedXVel != 0) absXVel.toFloat() / addedVel
        else absDx.toFloat() / addedDistance
        val yweight = if (clampedYVel != 0) absYVel.toFloat() / addedVel
        else absDy.toFloat() / addedDistance

        val xduration = computeAxisDuration(dx, clampedXVel, mCallback.getViewHorizontalDragRange(child))
        val yduration = computeAxisDuration(dy, clampedYVel, mCallback.getViewVerticalDragRange(child))

        return (xduration * xweight + yduration * yweight).toInt()
    }

    private fun computeAxisDuration(delta: Int, velocity: Int, motionRange: Int): Int {
        if (delta == 0) {
            return 0
        }

        val width = mParentView.width
        val halfWidth = width / 2
        val distanceRatio = min(1f, abs(delta).toFloat() / width)
        val distance = halfWidth + halfWidth * distanceInfluenceForSnapDuration(distanceRatio)

        val duration: Int
        val absVelocity = abs(velocity)
        if (absVelocity != 0) {
            duration = 4 * (1000 * abs(distance / absVelocity)).roundToInt()
        } else {
            val range = abs(delta).toFloat() / motionRange
            duration = ((range + 1) * BASE_SETTLE_DURATION).toInt()
        }
        return min(duration, MAX_SETTLE_DURATION)
    }

    /**
     * Clamp the magnitude of value for absMin and absMax.
     * If the value is below the minimum, it will be clamped to zero.
     * If the value is above the maximum, it will be clamped to the maximum.
     *
     * @param value  Value to clamp
     * @param absMin Absolute value of the minimum significant value to return
     * @param absMax Absolute value of the maximum value to return
     * @return The clamped value with the same sign as `value`
     */
    private fun clampMag(value: Int, absMin: Int, absMax: Int): Int {
        val absValue = abs(value)
        if (absValue < absMin) return 0
        if (absValue > absMax) return if (value > 0) absMax else -absMax
        return value
    }

    /**
     * Clamp the magnitude of value for absMin and absMax.
     * If the value is below the minimum, it will be clamped to zero.
     * If the value is above the maximum, it will be clamped to the maximum.
     *
     * @param value  Value to clamp
     * @param absMin Absolute value of the minimum significant value to return
     * @param absMax Absolute value of the maximum value to return
     * @return The clamped value with the same sign as `value`
     */
    private fun clampMag(value: Float, absMin: Float, absMax: Float): Float {
        val absValue = abs(value)
        if (absValue < absMin) return 0f
        if (absValue > absMax) return if (value > 0) absMax else -absMax
        return value
    }

    private fun distanceInfluenceForSnapDuration(f: Float): Float {
        var value = f
        value -= 0.5f // center the values about 0.
        value *= 0.3f * Math.PI.toFloat() / 2.0f
        return sin(value.toDouble()).toFloat()
    }

    /**
     * Move the captured settling view by the appropriate amount for the current time.
     * If `continueSettling` returns true, the caller should call it again
     * on the next frame to continue.
     *
     * @param deferCallbacks true if state callbacks should be deferred via posted message.
     *                       Set this to true if you are calling this method from
     *                       [android.view.View.computeScroll] or similar methods
     *                       invoked as part of layout or drawing.
     * @return true if settle is still in progress
     */
    fun continueSettling(deferCallbacks: Boolean): Boolean {
        if (mDragState == STATE_SETTLING) {
            val capturedView = mCapturedView!!

            var keepGoing = mScroller.computeScrollOffset()
            val x = mScroller.currX
            val y = mScroller.currY
            val dx = x - capturedView.left
            val dy = y - capturedView.top

            if (dx != 0) {
                ViewCompat.offsetLeftAndRight(capturedView, dx)
            }
            if (dy != 0) {
                ViewCompat.offsetTopAndBottom(capturedView, dy)
            }

            if (dx != 0 || dy != 0) {
                mCallback.onViewPositionChanged(capturedView, x, y, dx, dy)
            }

            if (keepGoing && x == mScroller.finalX && y == mScroller.finalY) {
                // Close enough. The interpolator/scroller might think we're still moving
                // but the user sure doesn't.
                mScroller.abortAnimation()
                keepGoing = false
            }

            if (!keepGoing) {
                if (deferCallbacks) {
                    mParentView.post(mSetIdleRunnable)
                } else {
                    setDragState(STATE_IDLE)
                }
            }
        }

        return mDragState == STATE_SETTLING
    }

    /**
     * Like all callback events this must happen on the UI thread, but release
     * involves some extra semantics.
     */
    private fun dispatchViewReleased(xvel: Float, yvel: Float) {
        mCallback.onViewReleased(mCapturedView!!, xvel, yvel)

        if (mDragState == STATE_DRAGGING) {
            // onViewReleased didn't call a method that would have changed this. Go idle.
            setDragState(STATE_IDLE)
        }
    }

    private fun clearMotionHistory() {
        val initialMotionX = mInitialMotionX ?: return

        initialMotionX.fill(0f)
        mInitialMotionY!!.fill(0f)
        mLastMotionX!!.fill(0f)
        mLastMotionY!!.fill(0f)
        mPointersDown = 0
    }

    private fun clearMotionHistory(pointerId: Int) {
        val initialMotionX = mInitialMotionX
        if (initialMotionX == null || !isPointerDown(pointerId)) {
            return
        }

        initialMotionX[pointerId] = 0f
        mInitialMotionY!![pointerId] = 0f
        mLastMotionX!![pointerId] = 0f
        mLastMotionY!![pointerId] = 0f
        mPointersDown = mPointersDown and (1 shl pointerId).inv()
    }

    private fun ensureMotionHistorySizeForId(pointerId: Int) {
        val initialMotionX = mInitialMotionX

        if (initialMotionX == null || initialMotionX.size <= pointerId) {
            val imx = FloatArray(pointerId + 1)
            val imy = FloatArray(pointerId + 1)
            val lmx = FloatArray(pointerId + 1)
            val lmy = FloatArray(pointerId + 1)

            if (initialMotionX != null) {
                System.arraycopy(initialMotionX, 0, imx, 0, initialMotionX.size)
                System.arraycopy(mInitialMotionY!!, 0, imy, 0, mInitialMotionY!!.size)
                System.arraycopy(mLastMotionX!!, 0, lmx, 0, mLastMotionX!!.size)
                System.arraycopy(mLastMotionY!!, 0, lmy, 0, mLastMotionY!!.size)
            }

            mInitialMotionX = imx
            mInitialMotionY = imy
            mLastMotionX = lmx
            mLastMotionY = lmy
        }
    }

    private fun saveInitialMotion(x: Float, y: Float, pointerId: Int) {
        ensureMotionHistorySizeForId(pointerId)
        mLastMotionX!![pointerId] = x
        mInitialMotionX!![pointerId] = x
        mLastMotionY!![pointerId] = y
        mInitialMotionY!![pointerId] = y
        mPointersDown = mPointersDown or (1 shl pointerId)
    }

    private fun saveLastMotion(ev: MotionEvent) {
        val pointerCount = ev.pointerCount
        for (i in 0 until pointerCount) {
            val pointerId = ev.getPointerId(i)
            // If pointer is invalid then skip saving on ACTION_MOVE.
            if (!isValidPointerForActionMove(pointerId)) {
                continue
            }
            val x = ev.getX(i)
            val y = ev.getY(i)
            mLastMotionX!![pointerId] = x
            mLastMotionY!![pointerId] = y
        }
    }

    /**
     * Check if the given pointer ID represents a pointer that is currently down (to the best
     * of the ViewDragHelper's knowledge).
     *
     * The state used to report this information is populated by the methods
     * [shouldInterceptTouchEvent] or [processTouchEvent]. If one of these methods has not
     * been called for all relevant MotionEvents to track, the information reported
     * by this method may be stale or incorrect.
     *
     * @param pointerId pointer ID to check; corresponds to IDs provided by MotionEvent
     * @return true if the pointer with the given ID is still down
     */
    fun isPointerDown(pointerId: Int): Boolean {
        return (mPointersDown and (1 shl pointerId)) != 0
    }

    // Public because Kotlin has no package private and this used to be package private
    fun setDragState(state: Int) {
        mParentView.removeCallbacks(mSetIdleRunnable)
        if (mDragState != state) {
            mDragState = state
            mCallback.onViewDragStateChanged(state)
            if (mDragState == STATE_IDLE) {
                mCapturedView = null
            }
        }
    }

    /**
     * Attempt to capture the view with the given pointer ID. The callback will be involved.
     * This will put us into the "dragging" state. If we've already captured this view with
     * this pointer this method will immediately return true without consulting the callback.
     *
     * @param toCapture View to capture
     * @param pointerId Pointer to capture with
     * @return true if capture was successful
     */
    // Public because Kotlin has no package private and this used to be package private
    fun tryCaptureViewForDrag(toCapture: View?, pointerId: Int): Boolean {
        if (toCapture === mCapturedView && mActivePointerId == pointerId) {
            // Already done!
            return true
        }
        if (toCapture != null && mCallback.tryCaptureView(toCapture, pointerId)) {
            mActivePointerId = pointerId
            captureChildView(toCapture, pointerId)
            return true
        }
        return false
    }

    /**
     * Check if this event as provided to the parent view's onInterceptTouchEvent should
     * cause the parent to intercept the touch event stream.
     *
     * @param ev MotionEvent provided to onInterceptTouchEvent
     * @return true if the parent view should return true from onInterceptTouchEvent
     */
    fun shouldInterceptTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        val actionIndex = ev.actionIndex

        if (action == MotionEvent.ACTION_DOWN) {
            // Reset things for a new event stream, just in case we didn't get
            // the whole previous stream.
            cancel()
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
        mVelocityTracker!!.addMovement(ev)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val x = ev.x
                val y = ev.y
                val pointerId = ev.getPointerId(0)
                saveInitialMotion(x, y, pointerId)

                val toCapture = findTopChildUnder(x.toInt(), y.toInt())

                // Catch a settling view if possible.
                if (toCapture === mCapturedView && mDragState == STATE_SETTLING) {
                    tryCaptureViewForDrag(toCapture, pointerId)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerId = ev.getPointerId(actionIndex)
                val x = ev.getX(actionIndex)
                val y = ev.getY(actionIndex)

                saveInitialMotion(x, y, pointerId)

                // A ViewDragHelper can only manipulate one view at a time.
                if (mDragState == STATE_SETTLING) {
                    // Catch a settling view if possible.
                    val toCapture = findTopChildUnder(x.toInt(), y.toInt())
                    if (toCapture === mCapturedView) {
                        tryCaptureViewForDrag(toCapture, pointerId)
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val initialMotionX = mInitialMotionX
                val initialMotionY = mInitialMotionY

                if (initialMotionX != null && initialMotionY != null) {
                    // First to cross a touch slop over a draggable view wins. Also report edge drags.
                    val pointerCount = ev.pointerCount
                    for (i in 0 until pointerCount) {
                        val pointerId = ev.getPointerId(i)

                        // If pointer is invalid then skip the ACTION_MOVE.
                        if (!isValidPointerForActionMove(pointerId)) continue

                        val x = ev.getX(i)
                        val y = ev.getY(i)
                        val dx = x - initialMotionX[pointerId]
                        val dy = y - initialMotionY[pointerId]

                        val toCapture = findTopChildUnder(x.toInt(), y.toInt())
                        val pastSlop = checkTouchSlop(toCapture, dx, dy)
                        if (pastSlop) {
                            // check the callback's
                            // getView[Horizontal|Vertical]DragRange methods to know
                            // if you can move at all along an axis, then see if it
                            // would clamp to the same value. If you can't move at
                            // all in every dimension with a nonzero range, bail.
                            val target = toCapture!!
                            val oldLeft = target.left
                            val targetLeft = oldLeft + dx.toInt()
                            val newLeft = mCallback.clampViewPositionHorizontal(
                                target,
                                targetLeft, dx.toInt()
                            )
                            val oldTop = target.top
                            val targetTop = oldTop + dy.toInt()
                            val newTop = mCallback.clampViewPositionVertical(
                                target, targetTop,
                                dy.toInt()
                            )
                            val hDragRange = mCallback.getViewHorizontalDragRange(target)
                            val vDragRange = mCallback.getViewVerticalDragRange(target)
                            if ((hDragRange == 0 || (hDragRange > 0 && newLeft == oldLeft))
                                && (vDragRange == 0 || (vDragRange > 0 && newTop == oldTop))
                            ) {
                                break
                            }
                        }

                        if (mDragState == STATE_DRAGGING) {
                            // Callback might have started an edge drag
                            break
                        }

                        if (pastSlop && tryCaptureViewForDrag(toCapture, pointerId)) {
                            break
                        }
                    }
                    saveLastMotion(ev)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = ev.getPointerId(actionIndex)
                clearMotionHistory(pointerId)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancel()
            }
        }

        return mDragState == STATE_DRAGGING
    }

    /**
     * Process a touch event received by the parent view. This method will dispatch callback events
     * as needed before returning. The parent view's onTouchEvent implementation should call this.
     *
     * @param ev The touch event received by the parent view
     */
    fun processTouchEvent(ev: MotionEvent) {
        val action = ev.actionMasked
        val actionIndex = ev.actionIndex

        if (action == MotionEvent.ACTION_DOWN) {
            // Reset things for a new event stream, just in case we didn't get
            // the whole previous stream.
            cancel()
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
        mVelocityTracker!!.addMovement(ev)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val x = ev.x
                val y = ev.y
                val pointerId = ev.getPointerId(0)
                val toCapture = findTopChildUnder(x.toInt(), y.toInt())

                saveInitialMotion(x, y, pointerId)

                // Since the parent is already directly processing this touch event,
                // there is no reason to delay for a slop before dragging.
                // Start immediately if possible.
                tryCaptureViewForDrag(toCapture, pointerId)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerId = ev.getPointerId(actionIndex)
                val x = ev.getX(actionIndex)
                val y = ev.getY(actionIndex)

                saveInitialMotion(x, y, pointerId)

                // A ViewDragHelper can only manipulate one view at a time.
                if (mDragState == STATE_IDLE) {
                    // If we're idle we can do anything! Treat it like a normal down event.

                    val toCapture = findTopChildUnder(x.toInt(), y.toInt())
                    tryCaptureViewForDrag(toCapture, pointerId)
                } else if (isCapturedViewUnder(x.toInt(), y.toInt())) {
                    // We're still tracking a captured view. If the same view is under this
                    // point, we'll swap to controlling it with this pointer instead.
                    // (This will still work if we're "catching" a settling view.)

                    tryCaptureViewForDrag(mCapturedView, pointerId)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (mDragState == STATE_DRAGGING) {
                    // If pointer is invalid then skip the ACTION_MOVE.
                    if (isValidPointerForActionMove(mActivePointerId)) {
                        val index = ev.findPointerIndex(mActivePointerId)
                        val x = ev.getX(index)
                        val y = ev.getY(index)
                        val idx = (x - mLastMotionX!![mActivePointerId]).toInt()
                        val idy = (y - mLastMotionY!![mActivePointerId]).toInt()

                        val capturedView = mCapturedView!!
                        dragTo(capturedView.left + idx, capturedView.top + idy, idx, idy)

                        saveLastMotion(ev)
                    }
                } else {
                    // Check to see if any pointer is now over a draggable view.
                    val pointerCount = ev.pointerCount
                    for (i in 0 until pointerCount) {
                        val pointerId = ev.getPointerId(i)

                        // If pointer is invalid then skip the ACTION_MOVE.
                        if (!isValidPointerForActionMove(pointerId)) continue

                        val x = ev.getX(i)
                        val y = ev.getY(i)
                        val dx = x - mInitialMotionX!![pointerId]
                        val dy = y - mInitialMotionY!![pointerId]

                        if (mDragState == STATE_DRAGGING) {
                            // Callback might have started an edge drag.
                            break
                        }

                        val toCapture = findTopChildUnder(x.toInt(), y.toInt())
                        if (checkTouchSlop(toCapture, dx, dy)
                            && tryCaptureViewForDrag(toCapture, pointerId)
                        ) {
                            break
                        }
                    }
                    saveLastMotion(ev)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = ev.getPointerId(actionIndex)
                if (mDragState == STATE_DRAGGING && pointerId == mActivePointerId) {
                    // Try to find another pointer that's still holding on to the captured view.
                    var newActivePointer = INVALID_POINTER
                    val pointerCount = ev.pointerCount
                    for (i in 0 until pointerCount) {
                        val id = ev.getPointerId(i)
                        if (id == mActivePointerId) {
                            // This one's going away, skip.
                            continue
                        }

                        val x = ev.getX(i)
                        val y = ev.getY(i)
                        if (findTopChildUnder(x.toInt(), y.toInt()) === mCapturedView
                            && tryCaptureViewForDrag(mCapturedView, id)
                        ) {
                            newActivePointer = mActivePointerId
                            break
                        }
                    }

                    if (newActivePointer == INVALID_POINTER) {
                        // We didn't find another pointer still touching the view, release it.
                        releaseViewForPointerUp()
                    }
                }
                clearMotionHistory(pointerId)
            }

            MotionEvent.ACTION_UP -> {
                if (mDragState == STATE_DRAGGING) {
                    releaseViewForPointerUp()
                }
                cancel()
            }

            MotionEvent.ACTION_CANCEL -> {
                if (mDragState == STATE_DRAGGING) {
                    dispatchViewReleased(0f, 0f)
                }
                cancel()
            }
        }
    }

    /**
     * Check if we've crossed a reasonable touch slop for the given child view.
     * If the child cannot be dragged along the horizontal or vertical axis, motion
     * along that axis will not count toward the slop check.
     *
     * @param child Child to check
     * @param dx    Motion since initial position along X axis
     * @param dy    Motion since initial position along Y axis
     * @return true if the touch slop has been crossed
     */
    private fun checkTouchSlop(child: View?, dx: Float, dy: Float): Boolean {
        if (child == null) {
            return false
        }

        val checkHorizontal = mCallback.getViewHorizontalDragRange(child) > 0
        val checkVertical = mCallback.getViewVerticalDragRange(child) > 0

        if (checkHorizontal && checkVertical) {
            return dx * dx + dy * dy > (touchSlop * touchSlop).toFloat()
        } else if (checkHorizontal) {
            return abs(dx) > touchSlop
        } else if (checkVertical) {
            return abs(dy) > touchSlop
        }
        return false
    }

    private fun releaseViewForPointerUp() {
        val velocityTracker = mVelocityTracker!!

        velocityTracker.computeCurrentVelocity(1000, mMaxVelocity)
        val xvel = clampMag(
            velocityTracker.getXVelocity(mActivePointerId),
            mMinVelocity, mMaxVelocity
        )
        val yvel = clampMag(
            velocityTracker.getYVelocity(mActivePointerId),
            mMinVelocity, mMaxVelocity
        )
        dispatchViewReleased(xvel, yvel)
    }

    private fun dragTo(left: Int, top: Int, dx: Int, dy: Int) {
        val capturedView = mCapturedView!!

        var clampedX = left
        var clampedY = top
        val oldLeft = capturedView.left
        val oldTop = capturedView.top
        if (dx != 0) {
            clampedX = mCallback.clampViewPositionHorizontal(capturedView, left, dx)
            ViewCompat.offsetLeftAndRight(capturedView, clampedX - oldLeft)
        }
        if (dy != 0) {
            clampedY = mCallback.clampViewPositionVertical(capturedView, top, dy)
            ViewCompat.offsetTopAndBottom(capturedView, clampedY - oldTop)
        }

        if (dx != 0 || dy != 0) {
            val clampedDx = clampedX - oldLeft
            val clampedDy = clampedY - oldTop
            mCallback.onViewPositionChanged(
                capturedView, clampedX, clampedY,
                clampedDx, clampedDy
            )
        }
    }

    /**
     * Determine if the currently captured view is under the given point in the
     * parent view's coordinate system. If there is no captured view this method
     * will return false.
     *
     * @param x X position to test in the parent's coordinate system
     * @param y Y position to test in the parent's coordinate system
     * @return true if the captured view is under the given point, false otherwise
     */
    fun isCapturedViewUnder(x: Int, y: Int): Boolean {
        return isViewUnder(mCapturedView, x, y)
    }

    /**
     * Determine if the supplied view is under the given point in the
     * parent view's coordinate system.
     *
     * @param view Child view of the parent to hit test
     * @param x    X position to test in the parent's coordinate system
     * @param y    Y position to test in the parent's coordinate system
     * @return true if the supplied view is under the given point, false otherwise
     */
    fun isViewUnder(view: View?, x: Int, y: Int): Boolean {
        if (view == null) {
            return false
        }

        return x >= view.left
                && x < view.right
                && y >= view.top
                && y < view.bottom
    }

    /**
     * Find the topmost child under the given point within the parent view's coordinate system.
     * The child order is determined using [Callback.getOrderedChildIndex].
     *
     * @param x X position to test in the parent's coordinate system
     * @param y Y position to test in the parent's coordinate system
     * @return The topmost child view under (x, y) or null if none found.
     */
    fun findTopChildUnder(x: Int, y: Int): View? {
        val childCount = mParentView.childCount
        for (i in childCount - 1 downTo 0) {
            val child = mParentView.getChildAt(mCallback.getOrderedChildIndex(i))
            if (x >= child.left && x < child.right
                && y >= child.top && y < child.bottom
            ) {
                return child
            }
        }
        return null
    }

    private fun isValidPointerForActionMove(pointerId: Int): Boolean {
        if (!isPointerDown(pointerId)) {
            Log.e(
                TAG, "Ignoring pointerId=" + pointerId + " because ACTION_DOWN was not received "
                        + "for this pointer before ACTION_MOVE. It likely happened because "
                        + " ViewDragHelper did not receive all the events in the event stream."
            )
            return false
        }
        return true
    }

    companion object {
        private const val TAG = "ViewDragHelper"

        /**
         * A null/invalid pointer ID.
         */
        const val INVALID_POINTER = -1

        /**
         * A view is not currently being dragged or animating as a result of a fling/snap.
         */
        const val STATE_IDLE = 0

        /**
         * A view is currently being dragged. The position is currently changing as a result
         * of user input or simulated user input.
         */
        const val STATE_DRAGGING = 1

        /**
         * A view is currently settling into place as a result of a fling or
         * predefined non-interactive motion.
         */
        const val STATE_SETTLING = 2

        private const val BASE_SETTLE_DURATION = 256 // ms
        private const val MAX_SETTLE_DURATION = 600 // ms

        /**
         * Interpolator defining the animation curve for mScroller
         */
        private val sInterpolator = Interpolator { input ->
            val t = input - 1.0f
            t * t * t * t * t + 1.0f
        }

        /**
         * Factory method to create a new ViewDragHelper.
         *
         * @param forParent Parent view to monitor
         * @param cb        Callback to provide information and receive events
         * @return a new ViewDragHelper instance
         */
        @JvmStatic
        fun create(forParent: ViewGroup, cb: Callback): SheetDragHelper {
            return SheetDragHelper(forParent.context, forParent, cb)
        }
    }
}
