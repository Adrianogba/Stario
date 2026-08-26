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

// reference https://github.com/quiph/RecyclerView-FastScroller/blob/master/recyclerviewfastscroller/src/main/java/com/qtalk/recyclerviewfastscroller/RecyclerViewFastScroller.kt

package adrianogba.stario.launcher.ui.recyclers

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.ui.utils.animation.Animation
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FastScroller @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    private val popup: TextView
    private val trackRight: View
    private val trackLeft: View

    private val swipeSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var recyclerView: RecyclerView? = null

    // Null until the first touch, then the raw coordinates the gesture started
    // from. Set to Float.MIN_VALUE once the gesture has committed to an axis.
    private var x: Float? = null
    private var y: Float? = null

    private var alphaLeft = 0f
    private var alphaRight = 0f
    private var currentPosition = -1
    private var lastPositionScrolled = -1
    private var trackLength = 0
    private var popupHeight = 0
    private var isEngaged = false

    init {
        inflate(context, R.layout.fastscroller_popup, this)
        popup = findViewById(R.id.fast_scroller_pop_up)

        inflate(context, R.layout.fastscroller_track_thumb, this)
        trackRight = findViewById(R.id.track_right)
        trackLeft = findViewById(R.id.track_left)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        trackLength = trackRight.height
        popupHeight = popup.height
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)

        updateTrackWidth()
    }

    fun setTopOffset(topOffset: Int) {
        (trackRight.layoutParams as MarginLayoutParams).topMargin = topOffset
        (trackLeft.layoutParams as MarginLayoutParams).topMargin = topOffset
    }

    fun setBottomOffset(bottomOffset: Int) {
        (trackRight.layoutParams as MarginLayoutParams).bottomMargin = bottomOffset
        (trackLeft.layoutParams as MarginLayoutParams).bottomMargin = bottomOffset
    }

    override fun onDetachedFromWindow() {
        detachFastScrollerFromRecyclerView()

        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun detachFastScrollerFromRecyclerView() {
        popup.setOnTouchListener(null)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onFinishInflate() {
        super.onFinishInflate()

        // two tracks + popup view which are inflated before everything else
        if (childCount <= 3) {
            return
        }

        val child = getChildAt(3)

        // move to top
        removeView(child)
        addView(child, 0)

        val recyclerView = findRecycler(child)
        this.recyclerView = recyclerView

        if (recyclerView != null) {
            updateTrackWidth()
        }

        post {
            val listener = OnTouchListener { view, event -> onTrackTouch(view, event) }

            trackLeft.setOnTouchListener(listener)
            trackRight.setOnTouchListener(listener)

            invalidate()
        }
    }

    private fun onTrackTouch(view: View, event: MotionEvent): Boolean {
        val recyclerView = recyclerView

        if (recyclerView == null || recyclerView.adapter == null) {
            if (event.action == MotionEvent.ACTION_UP ||
                event.action == MotionEvent.ACTION_CANCEL
            ) {
                x = null
                y = null
            }

            return true
        }

        val startX = x
        val startY = y

        if ((startX == null && startY == null) || event.action == MotionEvent.ACTION_DOWN) {
            x = event.rawX
            y = event.rawY

            (view as ViewGroup).requestDisallowInterceptTouchEvent(true)

            return true
        }

        val absDeltaX = abs(event.rawX - (startX ?: 0f))
        val absDeltaY = abs(event.rawY - (startY ?: 0f))

        if (absDeltaX > absDeltaY && absDeltaX >= swipeSlop) {
            x = Float.MIN_VALUE

            if (event.action == MotionEvent.ACTION_UP ||
                event.action == MotionEvent.ACTION_CANCEL
            ) {
                releaseGesture(view, recyclerView)
            }

            return false
        }

        if (absDeltaY > absDeltaX && absDeltaY >= swipeSlop) {
            y = Float.MIN_VALUE

            recyclerView.stopScroll()

            return onVerticalDrag(view, event, recyclerView)
        }

        return true
    }

    private fun onVerticalDrag(
        view: View, event: MotionEvent, recyclerView: RecyclerView
    ): Boolean {
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                if (!isEngaged) {
                    isEngaged = true

                    animatePopupVisibility(true, view == trackRight)
                }

                val currentRelativePos = event.y

                moveViewToRelativePositionWithBounds(
                    currentRelativePos - popupHeight.toFloat() / 2
                )

                val itemCount = recyclerView.adapter?.itemCount ?: 0

                updateTextInPopup(
                    min(itemCount - 1, computePositionForOffsetAndScroll(currentRelativePos))
                )

                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                releaseGesture(view, recyclerView)

                return superOnTouchEvent(event)
            }

            else -> return false
        }
    }

    private fun releaseGesture(view: View, recyclerView: RecyclerView) {
        isEngaged = false

        post { animatePopupVisibility(false, view == trackRight) }

        (recyclerView.adapter as? OnPopupViewReset)?.onReset(currentPosition)

        x = null
        y = null
    }

    // super is unreachable from inside a lambda in Kotlin, and the touch
    // handling above needs the RelativeLayout implementation.
    @SuppressLint("ClickableViewAccessibility")
    private fun superOnTouchEvent(event: MotionEvent): Boolean = super.onTouchEvent(event)

    private fun findRecycler(view: View?): RecyclerView? {
        if (view is RecyclerView) {
            return view
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val recycler = findRecycler(view.getChildAt(index))

                if (recycler != null) {
                    return recycler
                }
            }
        }

        return null
    }

    private fun computePositionForOffsetAndScroll(rawPosition: Float): Int {
        val recyclerView = recyclerView ?: return 0

        val layoutManager = recyclerView.layoutManager
        val itemCount = recyclerView.adapter?.itemCount ?: 0

        val newOffset = max(min(rawPosition / trackLength, 1f), 0f)

        if (layoutManager !is LinearLayoutManager) {
            val position = (newOffset * itemCount).toInt()

            safeScrollToPosition(position)

            return position
        }

        val last = if (layoutManager.reverseLayout) {
            itemCount - (newOffset * itemCount).toInt()
        } else {
            (newOffset * itemCount).toInt()
        }

        val position = min(itemCount, max(0, last))

        safeScrollToPosition(min(itemCount, position))

        return position
    }

    private fun safeScrollToPosition(position: Int) {
        val recyclerView = recyclerView

        if (position != lastPositionScrolled && recyclerView != null) {
            recyclerView.scrollToPosition(position)

            lastPositionScrolled = position
        }
    }

    private fun updateTextInPopup(position: Int) {
        val recyclerView = recyclerView

        if (recyclerView == null || currentPosition == position) {
            return
        }

        val adapter = recyclerView.adapter

        if (adapter == null || position < 0 || position >= adapter.itemCount) {
            return
        }

        currentPosition = position

        val update = adapter as? OnPopupViewUpdate

        if (update != null) {
            update.onUpdate(position, popup)
        } else {
            popup.visibility = GONE
        }
    }

    private fun alignPopupLayout(rightSide: Boolean) {
        val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

        if (rightSide) {
            params.addRule(LEFT_OF, trackRight.id)

            post { popup.x = trackRight.x - popup.width }
        } else {
            params.addRule(RIGHT_OF, trackLeft.id)

            post { popup.x = trackLeft.x + trackLeft.width }
        }

        popup.layoutParams = params
    }

    fun animateVisibility(makeVisible: Boolean) {
        animateVisibility(makeVisible, false, 0.2f, 0f)
        animateVisibility(makeVisible, true, 0.2f, 0f)
    }

    private fun animateVisibility(
        makeVisible: Boolean, rightSide: Boolean, maxAlpha: Float, minAlpha: Float
    ) {
        val alpha = if (makeVisible) maxAlpha else minAlpha

        if (rightSide) {
            if (alphaRight != alpha) {
                trackRight.animate()
                    .alpha(alpha)
                    .setDuration(Animation.SHORT.duration.toLong())

                alphaRight = alpha
            }
        } else {
            if (alphaLeft != alpha) {
                trackLeft.animate()
                    .alpha(alpha)
                    .setDuration(Animation.SHORT.duration.toLong())

                alphaLeft = alpha
            }
        }
    }

    private fun animatePopupVisibility(makeVisible: Boolean, rightSide: Boolean) {
        val scaleFactor = if (makeVisible) 1f else 0.5f
        val alpha = if (makeVisible) 1f else 0f

        alignPopupLayout(rightSide)
        popup.pivotX = popup.width * (if (rightSide) 0.75f else 0.25f)

        popup.animate()
            .scaleX(scaleFactor)
            .scaleY(scaleFactor)
            .alpha(alpha)
            .setDuration(Animation.MEDIUM.duration.toLong())

        animateVisibility(makeVisible, rightSide, 0.6f, 0.2f)
    }

    private fun moveViewToRelativePositionWithBounds(offset: Float) {
        val bounded = if (offset.isNaN()) 0f else offset

        popup.y = trackRight.y +
                min(max(bounded, 0f), (trackLength - popupHeight).toFloat())
    }

    private fun updateTrackWidth() {
        val recyclerView = recyclerView

        if (recyclerView == null || recyclerView.measuredHeight < measuredHeight) {
            trackLeft.visibility = GONE
            trackRight.visibility = GONE

            return
        }

        trackLeft.layoutParams.width = recyclerView.paddingLeft
        trackRight.layoutParams.width = recyclerView.paddingRight
        trackLeft.visibility = VISIBLE
        trackRight.visibility = VISIBLE

        invalidate()
    }

    fun interface OnPopupViewReset {
        fun onReset(position: Int)
    }

    fun interface OnPopupViewUpdate {
        fun onUpdate(index: Int, textView: TextView)
    }
}
