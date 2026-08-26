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

package adrianogba.stario.launcher.ui.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.RelativeLayout
import android.widget.RemoteViews
import adrianogba.stario.launcher.ui.utils.animation.Animation
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class WidgetHostView(
    context: Context,
    params: RelativeLayout.LayoutParams
) : RoundedWidgetHost(context, params) {

    private val moveSlop: Float = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private var pendingCheckForLongPress: CheckForLongPress? = null
    private var hasPerformedLongPress = false

    private var downX = 0f
    private var downY = 0f

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)

        enableChildrenNestedScrolling(this)
    }

    private fun enableChildrenNestedScrolling(view: View) {
        view.isNestedScrollingEnabled = true
        view.overScrollMode = OVER_SCROLL_NEVER

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                enableChildrenNestedScrolling(view.getChildAt(index))
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            downX = ev.rawX
            downY = ev.rawY

            postCheckForLongClick()
        } else if (ev.action == MotionEvent.ACTION_UP ||
            ev.action == MotionEvent.ACTION_CANCEL
        ) {
            removeCheck()
        }

        return false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
            removeCheck()
        } else {
            if (hasPerformedLongPress) {
                removeCheck()

                return super.dispatchTouchEvent(ev)
            } else if (ev.action == MotionEvent.ACTION_MOVE &&
                (abs(downX - ev.rawX) >= moveSlop || abs(downY - ev.rawY) >= moveSlop)
            ) {
                removeCheck()
            }
        }

        return super.dispatchTouchEvent(ev)
    }

    private fun removeCheck() {
        val pending = pendingCheckForLongPress ?: return

        removeCallbacks(pending)

        pendingCheckForLongPress = null

        if (!hasPerformedLongPress) {
            animate().scaleY(1f)
                .scaleX(1f)
                .alpha(1f)
                .setDuration(Animation.SHORT.duration.toLong())
        }
    }

    override fun getDescendantFocusability(): Int = ViewGroup.FOCUS_BLOCK_DESCENDANTS

    private fun postCheckForLongClick() {
        hasPerformedLongPress = false

        val pending = CheckForLongPress()
        pendingCheckForLongPress = pending

        postDelayed(pending, ViewConfiguration.getLongPressTimeout().toLong())

        animate().scaleY(STARTING_SCALE)
            .scaleX(STARTING_SCALE)
            .alpha(0.7f)
            .setInterpolator(PathInterpolator(0.5f, 0f, 0.2f, 1f))
            .setDuration(ViewConfiguration.getLongPressTimeout().toLong())
    }

    override fun performLongClick(): Boolean {
        val value = !hasPerformedLongPress && super.performLongClick()

        if (value) {
            hasPerformedLongPress = true

            pendingCheckForLongPress?.let { removeCallbacks(it) }
        }

        return value
    }

    override fun cancelLongPress() {
        super.cancelLongPress()

        removeCheck()
    }

    private inner class CheckForLongPress : Runnable {
        private val originalWindowAttachCount = windowAttachCount

        override fun run() {
            if (parent != null && hasWindowFocus() &&
                originalWindowAttachCount == windowAttachCount &&
                !hasPerformedLongPress
            ) {
                performLongClick()
            }
        }
    }

    private companion object {
        private const val STARTING_SCALE = 0.9f
    }
}
