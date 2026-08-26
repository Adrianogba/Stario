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
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.apps.Category
import adrianogba.stario.launcher.apps.CategoryManager
import adrianogba.stario.launcher.preferences.Vibrations
import adrianogba.stario.launcher.sheet.drawer.category.Categories
import adrianogba.stario.launcher.sheet.drawer.category.folder.Folder
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.icons.AdaptiveIconView
import adrianogba.stario.launcher.ui.recyclers.async.AsyncRecyclerAdapter
import adrianogba.stario.launcher.ui.utils.UiUtils
import adrianogba.stario.launcher.ui.utils.animation.Animation
import adrianogba.stario.launcher.ui.utils.animation.FragmentTransition
import adrianogba.stario.launcher.ui.utils.animation.SharedElementTransition
import java.util.function.Supplier

class FolderListAdapter(
    private val activity: ThemedActivity,
    private val folderList: FolderList
) : AsyncRecyclerAdapter<FolderListAdapter.ViewHolder>(activity) {

    private val categoryManager: CategoryManager = CategoryManager.getInstance()
    private val folder = Folder()

    private val listener = object : CategoryManager.CategoryListener {
        private var preparedRemovalIndex = -1

        override fun onCreated(category: Category?) {
            notifyAt(category) { notifyItemInserted(it) }
        }

        override fun onChanged(category: Category?) {
            notifyAt(category) { notifyItemChanged(it) }
        }

        override fun onPrepareRemoval(category: Category?) {
            if (preparedRemovalIndex < 0 && category != null) {
                preparedRemovalIndex = categoryManager.indexOf(category)
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onRemoved(category: Category?) {
            if (preparedRemovalIndex < 0) {
                UiUtils.post { notifyDataSetChanged() }

                return
            }

            val index = preparedRemovalIndex
            preparedRemovalIndex = -1

            UiUtils.post { notifyItemRemoved(index) }
        }

        private inline fun notifyAt(category: Category?, crossinline notify: (Int) -> Unit) {
            val index = if (category == null) -1 else categoryManager.indexOf(category)

            if (index >= 0) {
                UiUtils.post { notify(index) }
            }
        }
    }

    init {
        setHasStableIds(true)
    }

    fun move(
        viewHolder: RecyclerView.ViewHolder, targetHolder: RecyclerView.ViewHolder
    ): Boolean {
        var position = viewHolder.bindingAdapterPosition
        val target = targetHolder.bindingAdapterPosition

        if (position == target ||
            position == RecyclerView.NO_POSITION ||
            target == RecyclerView.NO_POSITION
        ) {
            return false
        }

        while (position != target) {
            val newTarget = position - if (position - target > 0) 1 else -1

            categoryManager.swap(position, newTarget)
            notifyItemMoved(position, newTarget)

            position = newTarget
        }

        return true
    }

    fun focus(holder: RecyclerView.ViewHolder) {
        if (holder !is ViewHolder) {
            return
        }

        holder.itemView.bringToFront()

        holder.itemView.animate()
            .scaleY(TARGET_SCALE)
            .scaleX(TARGET_SCALE)
            .translationZ(TARGET_ELEVATION)
            .setDuration(Animation.MEDIUM.duration.toLong())
        holder.category?.animate()
            ?.alpha(0f)
            ?.setDuration(Animation.MEDIUM.duration.toLong())
    }

    fun reset(holder: RecyclerView.ViewHolder) {
        if (holder !is ViewHolder) {
            return
        }

        holder.itemView.animate()
            .scaleY(1f)
            .scaleX(1f)
            .translationZ(0f)
            .setDuration(Animation.MEDIUM.duration.toLong())
        holder.category?.animate()
            ?.alpha(1f)
            ?.setDuration(Animation.MEDIUM.duration.toLong())
    }

    inner class ViewHolder : AsyncViewHolder() {
        internal var category: TextView? = null
        internal var recycler: RecyclerView? = null
        internal var adapter: FolderListItemAdapter? = null

        override fun onInflated() {
            category = itemView.findViewById(R.id.category)

            val recycler = itemView.findViewById<RecyclerView>(R.id.items)
            this.recycler = recycler

            recycler.layoutManager = createManager()
            recycler.itemAnimator = null

            val adapter = FolderListItemAdapter(activity)
            this.adapter = adapter

            recycler.adapter = adapter

            itemView.isHapticFeedbackEnabled = false
            recycler.isHapticFeedbackEnabled = false
        }

        private fun createManager(): GridLayoutManager {
            val gridLayoutManager = object : GridLayoutManager(activity, 4) {
                override fun supportsPredictiveItemAnimations(): Boolean = false
            }

            gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    val adapter = adapter

                    return if (position < FolderListItemAdapter.SOFT_LIMIT ||
                        (adapter != null && adapter.itemCount < FolderListItemAdapter.HARD_LIMIT)
                    ) 2 else 1
                }
            }

            return gridLayoutManager
        }

        fun updateCategory(category: Category) {
            adapter?.setCategory(category)
        }
    }

    override fun onBind(viewHolder: ViewHolder, index: Int) {
        val category = categoryManager.get(index)

        viewHolder.category?.text = categoryManager.getCategoryName(category.identifier)

        val clickListener = View.OnClickListener { openFolder(viewHolder, category) }

        viewHolder.itemView.setOnClickListener(clickListener)
        viewHolder.recycler?.setOnClickListener(clickListener)

        viewHolder.updateCategory(category)
    }

    private fun openFolder(viewHolder: ViewHolder, category: Category) {
        if (folder.isAdded || folder.isRemoving) {
            return
        }

        val layoutManager = viewHolder.recycler?.layoutManager ?: return
        val adapter = viewHolder.adapter ?: return

        Vibrations.getInstance().vibrate()

        val excluded = ArrayList<View>()

        val transaction = folderList.parentFragmentManager.beginTransaction()

        var position = 0
        while (position < adapter.itemCount &&
            position < FolderListItemAdapter.HARD_LIMIT
        ) {
            // findViewByPosition can return null for a position that is not
              // laid out. The original added it to the exclusion list anyway,
              // which the transitions would then walk into.
            val group = layoutManager.findViewByPosition(position)

            if (group != null) {
                excluded.add(group)
            }

            val icon = findIcon(group)
            if (icon != null) {
                transaction.addSharedElement(icon, icon.transitionName)
                excluded.add(icon)
            }

            position++
        }

        if (UiUtils.areAnimationsOn() && UiUtils.areTransitionsOn()) {
            folder.sharedElementEnterTransition = SharedElementTransition(excluded)
            folder.enterTransition = FragmentTransition(true, excluded)

            folderList.exitTransition = FragmentTransition(false, excluded)
            folderList.reenterTransition = FragmentTransition(true)
        } else {
            folder.sharedElementEnterTransition = null
            folder.enterTransition = null

            folderList.exitTransition = null
            folderList.reenterTransition = null
        }

        folder.sharedElementReturnTransition = null

        transaction.setReorderingAllowed(true)
        transaction.addToBackStack(Categories.FOLDER_STACK_ID)

        transaction.hide(folderList)
            .add(R.id.categories, folder)
        transaction.commit()

        folder.updateCategory(category.identifier)
    }

    private fun findIcon(view: View?): AdaptiveIconView? {
        if (view is AdaptiveIconView) {
            return view
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val icon = findIcon(view.getChildAt(index))

                if (icon != null) {
                    return icon
                }
            }
        }

        return null
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        categoryManager.addOnCategoryUpdateListener(listener)

        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        categoryManager.removeOnCategoryUpdateListener(listener)

        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun getLayout(viewType: Int): Int = R.layout.folder

    override fun getHolderSupplier(viewType: Int): Supplier<ViewHolder> = Supplier { ViewHolder() }

    override fun getItemId(position: Int): Long =
        categoryManager.get(position).identifier.hashCode().toLong()

    override fun getTotalItemCount(): Int = categoryManager.size()

    private companion object {
        const val TARGET_ELEVATION = 10f
        const val TARGET_SCALE = 0.9f
    }
}
