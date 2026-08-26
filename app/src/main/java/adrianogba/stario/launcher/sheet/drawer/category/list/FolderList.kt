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
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.apps.CategoryManager
import adrianogba.stario.launcher.preferences.Vibrations
import adrianogba.stario.launcher.sheet.drawer.DrawerPage
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.recyclers.autogrid.AutoGridLayoutManager
import adrianogba.stario.launcher.ui.utils.LayoutSizeObserver
import kotlin.math.max
import kotlin.math.min

class FolderList : DrawerPage() {

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        postponeEnterTransition()

        val rootView = super.onCreateView(inflater, container, savedInstanceState)

        val manager = AutoGridLayoutManager(activity, 1)
        LayoutSizeObserver.attach(rootView, LayoutSizeObserver.WIDTH,
            object : LayoutSizeObserver.OnChange {
                override fun onChange(view: View, watchFlags: Int, rect: Rect) {
                    val width = rect.width()

                    val columns = if (width < Measurements.dpToPx(350f)) {
                        1
                    } else if (width < Measurements.dpToPx(380f)) {
                        2
                    } else {
                        max(1, width / Measurements.dpToPx(190f))
                    }

                    manager.spanCount = columns
                }
            })

        val drawer = this.drawer

        drawer.layoutManager = manager
        drawer.itemAnimator = null

        val adapter = FolderListAdapter(activity, this)

        drawer.adapter = adapter

        val callback: ItemTouchHelper.Callback = object : ItemTouchHelper.Callback() {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val state = manager.onSaveInstanceState()
                val result = adapter.move(viewHolder, target)
                manager.onRestoreInstanceState(state)

                if (result) {
                    Vibrations.getInstance().vibrate()
                }

                return false
            }

            override fun onSelectedChanged(
                viewHolder: RecyclerView.ViewHolder?, actionState: Int
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    viewHolder.itemView.forceLayout()
                    adapter.focus(viewHolder)

                    drawer.itemAnimator = DefaultItemAnimator()

                    Vibrations.getInstance().vibrate()

                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                }
            }

            override fun interpolateOutOfBoundsScroll(
                recyclerView: RecyclerView, viewSize: Int,
                viewSizeOutOfBounds: Int, totalSize: Int, msSinceStartScroll: Long
            ): Int {
                return min(
                    super.interpolateOutOfBoundsScroll(
                        recyclerView, viewSize, viewSizeOutOfBounds,
                        totalSize, msSinceStartScroll
                    ), 100
                )
            }

            override fun getMovementFlags(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
            ): Int {
                return makeFlag(
                    ItemTouchHelper.ACTION_STATE_DRAG,
                    ItemTouchHelper.DOWN or ItemTouchHelper.UP or
                            ItemTouchHelper.START or ItemTouchHelper.END
                )
            }

            override fun clearView(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
            ) {
                adapter.reset(viewHolder)

                drawer.itemAnimator?.isRunning { drawer.itemAnimator = null }

                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            }

            override fun isItemViewSwipeEnabled(): Boolean = false
        }

        val itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(drawer)

        drawer.post { startPostponedEnterTransition() }
        CategoryManager.getInstance().addOnReadyListener { showLayout() }

        return rootView
    }

    override fun getLayoutResID(): Int = R.layout.drawer_folder_list
}
