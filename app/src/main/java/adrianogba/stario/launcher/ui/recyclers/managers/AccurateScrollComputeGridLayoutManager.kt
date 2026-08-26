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

package adrianogba.stario.launcher.ui.recyclers.managers

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.ceil

open class AccurateScrollComputeGridLayoutManager(context: Context, spanCount: Int) :
    GridLayoutManager(context, spanCount, RecyclerView.VERTICAL, false) {

    private var canScroll = true

    override fun supportsPredictiveItemAnimations(): Boolean = false

    override fun setOrientation(orientation: Int) {
        if (orientation != RecyclerView.VERTICAL) {
            throw RuntimeException("This layout manager supports only vertical orientation.")
        }

        super.setOrientation(orientation)
    }

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int {
        if (childCount == 0) {
            return 0
        }

        val position = findFirstVisibleItemPosition()

        if (position != RecyclerView.NO_POSITION) {
            val view = findViewByPosition(position)

            if (view != null) {
                val topMargin = (view.layoutParams as ViewGroup.MarginLayoutParams).topMargin
                val rows = ceil((position / spanCount.toFloat()).toDouble())

                return (-(view.top - topMargin - view.height * rows - paddingTop)).toInt()
            }
        }

        return 0
    }

    override fun computeVerticalScrollRange(state: RecyclerView.State): Int {
        if (childCount == 0) {
            return 0
        }

        val position = findFirstVisibleItemPosition()

        if (position != RecyclerView.NO_POSITION) {
            val view = findViewByPosition(position)

            val rows = ceil((itemCount / spanCount.toFloat()).toDouble()).toInt()

            if (view != null) {
                return view.height * rows
            }
        }

        return 0
    }

    override fun computeVerticalScrollExtent(state: RecyclerView.State): Int {
        return height - paddingTop - paddingBottom
    }

    override fun canScrollHorizontally(): Boolean = false

    override fun canScrollVertically(): Boolean = canScroll && super.canScrollVertically()

    fun setScrollEnabled(enabled: Boolean) {
        this.canScroll = enabled
    }
}
