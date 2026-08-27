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

package adrianogba.stario.launcher.sheet.drawer.category.folder

import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Transition
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.apps.Category
import adrianogba.stario.launcher.apps.CategoryManager
import adrianogba.stario.launcher.apps.popup.RenameCategoryDialog
import adrianogba.stario.launcher.preferences.Vibrations
import adrianogba.stario.launcher.sheet.drawer.RecyclerApplicationAdapter
import adrianogba.stario.launcher.sheet.drawer.search.ListDrawerPage
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.recyclers.autogrid.AutoGridLayoutManager
import adrianogba.stario.launcher.ui.utils.LayoutSizeObserver
import java.lang.reflect.Method
import java.util.UUID
import kotlin.math.min

class Folder : ListDrawerPage() {

    private var itemTouchHelper: ItemTouchHelper? = null
    private var listener: OnCreateListener? = null
    private var adapter: FolderAdapter? = null
    private var identifier: UUID? = null

    private val categoryListener = object : CategoryManager.CategoryListener {
        override fun onChanged(category: Category?) {
            val identifier = identifier ?: return

            title.text = CategoryManager.getInstance().getCategoryName(identifier)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val rootView = super.onCreateView(inflater, container, savedInstanceState)

        val manager = AutoGridLayoutManager(activity, 1)

        LayoutSizeObserver.attach(
            rootView, LayoutSizeObserver.WIDTH,
            object : LayoutSizeObserver.OnChange {
                override fun onChange(view: View, watchFlags: Int, rect: Rect) {
                    manager.spanCount = min(6, rect.width() / Measurements.dpToPx(90f))
                }
            })

        drawer.layoutManager = manager
        drawer.itemAnimator = null

        drawer.addOnScrollListener(ForceTransitionToEnd(rootView))

        val helper = ItemTouchHelper(DragCallback(manager))
        itemTouchHelper = helper
        helper.attachToRecyclerView(drawer)

        listener?.onCreate()

        return rootView
    }

    /**
     * Scrolling while the shared element transition is still running leaves the
     * icons stranded mid-flight, so the transition is forced to its end state.
     * Transition.forceToEnd is not public API, hence the reflection, and the
     * valid flag stops it retrying once the lookup has failed.
     */
    private inner class ForceTransitionToEnd(
        private val rootView: View
    ) : RecyclerView.OnScrollListener() {

        private var method: Method? = null
        private var valid = true

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            val tester = enterTransition

            if (tester !is Transition || !valid) {
                return
            }

            try {
                val resolved = method ?: Transition::class.java
                    .getDeclaredMethod("forceToEnd", ViewGroup::class.java)
                    .also {
                        it.isAccessible = true
                        method = it
                    }

                resolved.invoke(tester, rootView)
            } catch (exception: SecurityException) {
                valid = false
            } catch (exception: NoSuchMethodException) {
                valid = false
            } catch (exception: IllegalAccessError) {
                valid = false
            } catch (exception: Exception) {
                Log.e("Folder", "onScrollStateChanged: ", exception)
            }
        }
    }

    private inner class DragCallback(
        private val manager: AutoGridLayoutManager
    ) : ItemTouchHelper.Callback() {

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val state = manager.onSaveInstanceState()
            val result = adapter?.move(viewHolder, target) == true
            manager.onRestoreInstanceState(state)

            if (result) {
                Vibrations.getInstance().vibrate()
            }

            // Deliberately false. The move has already been applied above and
            // reported through notifyItemMoved.
            return false
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || viewHolder == null) {
                return
            }

            viewHolder.itemView.forceLayout()
            (viewHolder as RecyclerApplicationAdapter.ApplicationViewHolder).hideLabel()

            drawer.itemAnimator = DefaultItemAnimator()

            Vibrations.getInstance().vibrate()

            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        }

        override fun clearView(
            recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
        ) {
            (viewHolder as RecyclerApplicationAdapter.ApplicationViewHolder).showLabel()

            drawer.itemAnimator?.isRunning { drawer.itemAnimator = null }

            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        override fun interpolateOutOfBoundsScroll(
            recyclerView: RecyclerView, viewSize: Int,
            viewSizeOutOfBounds: Int, totalSize: Int, msSinceStartScroll: Long
        ): Int = min(
            super.interpolateOutOfBoundsScroll(
                recyclerView, viewSize, viewSizeOutOfBounds, totalSize, msSinceStartScroll
            ),
            100
        )

        override fun getMovementFlags(
            recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
        ): Int = makeFlag(
            ItemTouchHelper.ACTION_STATE_DRAG,
            ItemTouchHelper.DOWN or ItemTouchHelper.UP or
                    ItemTouchHelper.START or ItemTouchHelper.END
        )

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        }

        override fun isLongPressDragEnabled(): Boolean = false

        override fun isItemViewSwipeEnabled(): Boolean = false
    }

    override fun hideInflatedLayout(): Boolean = false

    fun updateCategory(identifier: UUID) {
        postponeEnterTransition()

        listener = OnCreateListener {
            this.identifier = identifier

            val adapter = FolderAdapter(activity, identifier, itemTouchHelper!!)
            this.adapter = adapter
            drawer.adapter = adapter

            drawer.post {
                drawer.scrollToPosition(0)
                updateTitleTransforms(drawer)

                title.text = CategoryManager.getInstance().getCategoryName(identifier)
                title.setOnClickListener {
                    RenameCategoryDialog(activity, identifier).show()
                }

                startPostponedEnterTransition()
            }
        }
    }

    override fun onStart() {
        CategoryManager.getInstance().addOnCategoryUpdateListener(categoryListener)

        super.onStart()
    }

    override fun onStop() {
        CategoryManager.getInstance().removeOnCategoryUpdateListener(categoryListener)

        super.onStop()
    }

    private fun interface OnCreateListener {
        fun onCreate()
    }

    override fun getLayoutResID(): Int = R.layout.drawer_folder
}
