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

package adrianogba.stario.launcher.ui.common.scrollers

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.PreEventNestedScrollView

open class BottomNestedScrollView : PreEventNestedScrollView {
    private var nestedScrolling = false

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
        rotation = 180f

        nestedScrolling = false
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        super.setPadding(left, bottom, right, top)
    }

    override fun getPaddingBottom(): Int = super.getPaddingTop()

    override fun getPaddingTop(): Int = super.getPaddingBottom()

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams?) {
        child.rotation = child.rotation + 180

        super.addView(child, index, params)
    }

    override fun onNestedScroll(
        target: View, dxConsumed: Int, dyConsumed: Int,
        dxUnconsumed: Int, dyUnconsumed: Int
    ) {
        super.onNestedScroll(target, dxConsumed, -dyConsumed, dxUnconsumed, -dyUnconsumed)
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
        nestedScrolling = true

        super.onNestedPreScroll(target, dx, dy, consumed)
    }

    override fun dispatchNestedPreScroll(
        dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?, type: Int
    ): Boolean {
        val result: Boolean

        if (!nestedScrolling) {
            result = super.dispatchNestedPreScroll(dx, -dy, consumed, offsetInWindow, type)

            if (consumed != null) {
                consumed[1] = -consumed[1]
            }

            if (offsetInWindow != null) {
                offsetInWindow[1] = -offsetInWindow[1]
            }
        } else {
            result = super.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)
        }

        nestedScrolling = false

        return result
    }

    override fun canScrollVertically(direction: Int): Boolean {
        return super.canScrollVertically(-direction)
    }
}
