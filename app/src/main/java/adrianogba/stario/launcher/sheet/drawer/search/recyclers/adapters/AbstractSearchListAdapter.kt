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

package adrianogba.stario.launcher.sheet.drawer.search.recyclers.adapters

import android.annotation.SuppressLint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.sheet.drawer.search.Searchable
import adrianogba.stario.launcher.sheet.drawer.search.recyclers.OnVisibilityChangeListener

abstract class AbstractSearchListAdapter<V : RecyclerView.ViewHolder> :
    RecyclerView.Adapter<V>(), Searchable {

    private var listener: OnVisibilityChangeListener? = null
    private var recyclerView: RecyclerView? = null

    fun invalidateRecyclerVisibility() {
        val recycler = recyclerView ?: return

        if (itemCount == 0 && recycler.visibility != View.GONE) {
            listener?.onPreChange(recycler, View.GONE)

            recycler.visibility = View.GONE

            recycler.post { listener?.onChange(recycler, View.GONE) }
        } else if (itemCount > 0 && recycler.visibility != View.VISIBLE) {
            listener?.onPreChange(recycler, View.VISIBLE)

            recycler.visibility = View.VISIBLE

            recycler.post { listener?.onChange(recycler, View.VISIBLE) }
        }
    }

    fun setOnVisibilityChangeListener(listener: OnVisibilityChangeListener?) {
        this.listener = listener
    }

    @SuppressLint("NotifyDataSetChanged")
    fun notifyInternal() {
        val runnable = Runnable {
            notifyDataSetChanged()
            invalidateRecyclerVisibility()
        }

        val recycler = recyclerView

        if (recycler != null && recycler.isAnimating) {
            recycler.post(runnable)
        } else {
            runnable.run()
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView

        invalidateRecyclerVisibility()

        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        invalidateRecyclerVisibility()

        this.recyclerView = null
        super.onDetachedFromRecyclerView(recyclerView)
    }
}
