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

package adrianogba.stario.launcher.sheet.drawer.category.list

import android.annotation.SuppressLint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.apps.Category
import adrianogba.stario.launcher.apps.LauncherApplication
import adrianogba.stario.launcher.sheet.drawer.RecyclerApplicationAdapter
import adrianogba.stario.launcher.themes.ThemedActivity
import java.util.function.Supplier
import kotlin.math.min

class FolderListItemAdapter(activity: ThemedActivity) : RecyclerApplicationAdapter(activity) {

    private var listener: Category.CategoryItemListener? = null
    private var recyclerView: RecyclerView? = null
    private var category: Category? = null

    @SuppressLint("NotifyDataSetChanged")
    fun setCategory(category: Category) {
        if (this.category === category) {
            return
        }

        listener?.let { this.category!!.removeCategoryItemListener(it) }

        this.category = category

        notifyDataSetChanged()

        val listener = object : Category.CategoryItemListener {
            private fun refresh() {
                recyclerView?.post { notifyDataSetChanged() }
            }

            override fun onInserted(application: LauncherApplication?) = refresh()

            override fun onRemoved(application: LauncherApplication?) = refresh()

            override fun onUpdated(application: LauncherApplication?) = refresh()

            override fun onSwapped(index1: Int, index2: Int) = refresh()
        }
        this.listener = listener

        category.addCategoryItemListener(listener)
    }

    protected inner class FolderItemViewHolder : ApplicationViewHolder() {
        // super cannot be reached from inside a lambda, so it is bounced
        // through this instead. Still resolved lazily, on click.
        private fun superOnClickListener(): View.OnClickListener? = super.getOnClickListener()

        override fun getOnClickListener(): View.OnClickListener {
            return View.OnClickListener { view ->
                val position = absoluteAdapterPosition

                if (position == RecyclerView.NO_POSITION) {
                    return@OnClickListener
                }

                if (position >= SOFT_LIMIT && (category?.size ?: 0) >= HARD_LIMIT) {
                    var folder = itemView.parent as View

                    while (folder.parent !is RecyclerView) {
                        folder = folder.parent as View
                    }

                    folder.callOnClick()
                } else {
                    superOnClickListener()?.onClick(view)
                }
            }
        }

        override fun getOnLongClickListener(): View.OnLongClickListener? = null
    }

    override fun getHolderSupplier(viewType: Int): Supplier<ApplicationViewHolder> {
        return Supplier { FolderItemViewHolder() }
    }

    override fun getLayout(viewType: Int): Int = R.layout.folder_item

    override fun getApplication(index: Int): LauncherApplication? {
        return category?.get(index) ?: LauncherApplication.FALLBACK_APP
    }

    override fun allowApplicationStateEditing(): Boolean = false

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        val category = this.category
        val listener = this.listener

        if (category != null && listener != null) {
            category.removeCategoryItemListener(listener)
        }

        this.recyclerView = null
    }

    override fun getTotalItemCount(): Int {
        val category = this.category ?: return 0

        return min(category.size, HARD_LIMIT)
    }

    companion object {
        const val SOFT_LIMIT: Int = 3
        const val HARD_LIMIT: Int = 5
    }
}
