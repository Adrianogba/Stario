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

package adrianogba.stario.launcher.activities.launcher.widgets.pins

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.activities.launcher.widgets.pins.dialog.PinnedAppsGroupDialog
import adrianogba.stario.launcher.apps.Category
import adrianogba.stario.launcher.apps.CategoryManager
import adrianogba.stario.launcher.apps.LauncherApplication
import adrianogba.stario.launcher.preferences.Vibrations
import adrianogba.stario.launcher.sheet.drawer.RecyclerApplicationAdapter
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.recyclers.async.InflationType
import adrianogba.stario.launcher.ui.utils.UiUtils
import java.util.UUID
import java.util.function.Supplier
import kotlin.math.min

class PinnedAppsAdapter(
    private val activity: ThemedActivity,
    private val settings: SharedPreferences,
    private val popUpShowListener: OnPopUpShowListener?,
    private val transitionListener: PinnedAppsGroupDialog.TransitionListener?
) : RecyclerApplicationAdapter(activity, false, InflationType.SYNCED) {

    private var category: Category? = null
    private var itemCount = 0

    private val sharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (PinnedCategory.PINNED_CATEGORY == key) {
                load()
            }
        }

    private val categoryManagerChangeListener = object : CategoryManager.CategoryListener {
        override fun onRemoved(category: Category?) {
            if (category == this@PinnedAppsAdapter.category) {
                load()
            }
        }
    }

    private val categoryChangeListener = object : Category.CategoryItemListener {
        @SuppressLint("NotifyDataSetChanged")
        override fun onInserted(application: LauncherApplication?) = notifyDataSetChanged()

        @SuppressLint("NotifyDataSetChanged")
        override fun onRemoved(application: LauncherApplication?) = notifyDataSetChanged()

        @SuppressLint("NotifyDataSetChanged")
        override fun onUpdated(application: LauncherApplication?) = notifyDataSetChanged()

        @SuppressLint("NotifyDataSetChanged")
        override fun onSwapped(index1: Int, index2: Int) = notifyDataSetChanged()
    }

    private fun load() {
        category?.removeCategoryItemListener(categoryChangeListener)

        category = try {
            CategoryManager.getInstance().get(
                UUID.fromString(settings.getString(PinnedCategory.PINNED_CATEGORY, ""))
            )
        } catch (exception: IllegalArgumentException) {
            null
        }

        val category = category

        if (category != null) {
            category.addCategoryItemListener(categoryChangeListener)
        } else {
            resetSharedPreferences()
        }

        UiUtils.post { notifyDataSetChangedInternal() }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyDataSetChangedInternal() = notifyDataSetChanged()

    private fun resetSharedPreferences() {
        settings.edit()
            .remove(PinnedCategory.PINNED_CATEGORY)
            .remove(PinnedCategory.PINNED_CATEGORY_VISIBLE)
            .apply()
    }

    /**
     * The last tile when the category holds more than fits. Tapping it opens
     * the rest in a dialog rather than launching anything.
     */
    inner class PinnedGroupViewHolder(viewType: Int) : ApplicationViewHolder(viewType) {
        private var group: RecyclerView? = null

        override fun onInflated() {
            super.onInflated()

            group = itemView.findViewById(R.id.group)
        }

        override fun getOnClickListener(): View.OnClickListener = View.OnClickListener {
            Vibrations.getInstance().vibrate()

            val index = bindingAdapterPosition
            if (index == RecyclerView.NO_POSITION) {
                return@OnClickListener
            }

            val dialog = PinnedAppsGroupDialog(activity, transitionListener)
            dialog.setCategory(category)

            dialog.show(index, group)
        }

        override fun getOnLongClickListener(): View.OnLongClickListener? = null

        internal fun bindGroup() {
            group?.layoutManager = GridLayoutManager(activity, 2)
            group?.adapter = PinnedAppsGroupAdapter(activity, category, itemCount - 1)
        }
    }

    inner class PinnedApplicationViewHolder(viewType: Int) : ApplicationViewHolder(viewType) {
        override fun getOnLongClickListener(): View.OnLongClickListener? {
            val listener = super.getOnLongClickListener()

            return View.OnLongClickListener { view ->
                popUpShowListener?.onShow()

                listener?.onLongClick(view) ?: true
            }
        }
    }

    override fun onBind(viewHolder: ApplicationViewHolder, index: Int) {
        super.onBind(viewHolder, index)

        if (viewHolder is PinnedGroupViewHolder) {
            viewHolder.setIcon(null)
            viewHolder.bindGroup()
        }
    }

    override fun getItemViewType(position: Int): Int {
        val category = category

        if (position == itemCount - 1 && category != null &&
            category.size - position - 1 > 0
        ) {
            return GROUP_VIEW_TYPE
        }

        return super.getItemViewType(position)
    }

    override fun getLayout(viewType: Int): Int {
        if (viewType == GROUP_VIEW_TYPE) {
            return R.layout.pinned_application_group
        }

        return super.getLayout(ONLY_ICON_LAYOUT)
    }

    override fun getHolderSupplier(viewType: Int): Supplier<ApplicationViewHolder> {
        if (viewType == GROUP_VIEW_TYPE) {
            return Supplier { PinnedGroupViewHolder(viewType) }
        }

        return Supplier { PinnedApplicationViewHolder(viewType) }
    }

    override fun getApplication(index: Int): LauncherApplication? = category?.get(index)

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        settings.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
        CategoryManager.getInstance()
            .addOnCategoryUpdateListener(categoryManagerChangeListener)

        load()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)

        settings.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
        CategoryManager.getInstance()
            .removeOnCategoryUpdateListener(categoryManagerChangeListener)

        category?.removeCategoryItemListener(categoryChangeListener)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setMaxItemCount(count: Int) {
        this.itemCount = count

        notifyDataSetChanged()
    }

    override fun getTotalItemCount(): Int = min(category?.size ?: 0, itemCount)

    override fun allowApplicationStateEditing(): Boolean = true

    fun interface OnPopUpShowListener {
        fun onShow()
    }

    private companion object {
        const val GROUP_VIEW_TYPE = 2
    }
}
