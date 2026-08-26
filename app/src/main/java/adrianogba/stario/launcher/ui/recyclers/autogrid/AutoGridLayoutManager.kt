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

package adrianogba.stario.launcher.ui.recyclers.autogrid

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.ui.recyclers.async.AsyncRecyclerAdapter
import adrianogba.stario.launcher.ui.recyclers.managers.AccurateScrollComputeGridLayoutManager
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class AutoGridLayoutManager(
    context: Context,
    spanCount: Int
) : AccurateScrollComputeGridLayoutManager(context, spanCount) {

    private var adapter: RecyclerView.Adapter<*>? = null
    private var recyclerView: RecyclerView? = null

    // Named apart from centerItems(), which it used to share a name with.
    private var shouldCenterItems = true
    private var actualSpanCount = spanCount

    private val layoutListener = object : View.OnLayoutChangeListener {
        private var width = 0

        override fun onLayoutChange(
            view: View, left: Int, top: Int, right: Int, bottom: Int,
            oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
        ) {
            if (view.measuredWidth != width) {
                width = view.measuredWidth

                centerItems()
            }
        }
    }

    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = centerItems()

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = centerItems()

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = centerItems()
    }

    override fun setSpanCount(spanCount: Int) {
        this.actualSpanCount = max(spanCount, 1)

        centerItems()
    }

    override fun onAttachedToWindow(view: RecyclerView) {
        recyclerView = view
        view.addOnLayoutChangeListener(layoutListener)

        super.onAttachedToWindow(view)
    }

    override fun onDetachedFromWindow(view: RecyclerView, recycler: RecyclerView.Recycler) {
        super.onDetachedFromWindow(view, recycler)

        recyclerView?.removeOnLayoutChangeListener(layoutListener)
        recyclerView = null
        setAdapter(null)
    }

    fun setAdapter(adapter: RecyclerView.Adapter<*>?) {
        this.adapter?.unregisterAdapterDataObserver(observer)

        this.adapter = adapter

        if (adapter != null) {
            adapter.registerAdapterDataObserver(observer)
            centerItems()
        }
    }

    private fun centerItems() {
        val recycler = recyclerView
        val adapter = this.adapter

        if (recycler == null || adapter == null) {
            super.setSpanCount(actualSpanCount)

            return
        }

        if (!shouldCenterItems) {
            super.setSpanCount(actualSpanCount)

            clearHorizontalMargins(recycler)
        } else {
            val rawCount = if (adapter is AsyncRecyclerAdapter<*>) {
                adapter.getTotalItemCount()
            } else {
                adapter.itemCount
            }
            val itemCount = getBalancedSpanCount(rawCount, actualSpanCount)

            if (itemCount < actualSpanCount) {
                super.setSpanCount(itemCount)

                val marginLayoutParams =
                    recycler.layoutParams as ViewGroup.MarginLayoutParams

                val margin = ((recycler.measuredWidth +
                        marginLayoutParams.leftMargin + marginLayoutParams.rightMargin -
                        recycler.paddingLeft - recycler.paddingRight) *
                        (1f - itemCount / actualSpanCount.toFloat()) / 2).toInt()

                marginLayoutParams.leftMargin = max(0, margin)
                marginLayoutParams.rightMargin = max(0, margin)
                recycler.layoutParams = marginLayoutParams
            } else {
                super.setSpanCount(actualSpanCount)

                clearHorizontalMargins(recycler)
            }
        }

        recycler.post { recyclerView?.requestLayout() }
    }

    private fun clearHorizontalMargins(recycler: RecyclerView) {
        val marginLayoutParams = recycler.layoutParams as ViewGroup.MarginLayoutParams

        marginLayoutParams.leftMargin = 0
        marginLayoutParams.rightMargin = 0
        recycler.layoutParams = marginLayoutParams
    }

    private fun getBalancedSpanCount(itemCount: Int, spanCount: Int): Int {
        if (itemCount <= 0 || spanCount <= 0) {
            return 1
        }

        val rows = ceil(itemCount.toDouble() / spanCount).toInt()

        return min(ceil(itemCount.toDouble() / rows).toInt(), spanCount)
    }

    fun setCenterItems(value: Boolean) {
        this.shouldCenterItems = value

        centerItems()
    }
}
