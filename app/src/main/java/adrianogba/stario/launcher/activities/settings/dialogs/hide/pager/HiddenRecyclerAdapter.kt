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

package adrianogba.stario.launcher.activities.settings.dialogs.hide.pager

import android.annotation.SuppressLint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.apps.LauncherApplication
import adrianogba.stario.launcher.apps.ProfileApplicationManager
import adrianogba.stario.launcher.apps.interfaces.LauncherApplicationListener
import adrianogba.stario.launcher.sheet.drawer.RecyclerApplicationAdapter
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.icons.AdaptiveIconView
import adrianogba.stario.launcher.ui.recyclers.async.InflationType
import java.util.function.Supplier

class HiddenRecyclerAdapter(
    activity: ThemedActivity,
    private val applicationManager: ProfileApplicationManager?
) : RecyclerApplicationAdapter(activity, InflationType.ASYNC) {
    private val listener: LauncherApplicationListener
    private var recyclerView: RecyclerView? = null

    init {
        listener = object : LauncherApplicationListener {
            override fun onInserted(application: LauncherApplication?) {
                recyclerView!!.post {
                    notifyItemInsertedInternal(applicationManager!!.indexOf(application))
                    approximateRecyclerHeight()
                }
            }

            override fun onRemoved(application: LauncherApplication?) {
                recyclerView!!.post {
                    notifyItemRemovedInternal()
                    approximateRecyclerHeight()
                }
            }

            override fun onUpdated(application: LauncherApplication?) {
                recyclerView!!.post {
                    notifyItemChanged(applicationManager!!.indexOf(application))
                }
            }
        }

        setHasStableIds(true)
    }

    inner class HiddenViewHolder : ApplicationViewHolder() {
        var icon: AdaptiveIconView? = null
            private set

        @SuppressLint("ClickableViewAccessibility")
        override fun onInflated() {
            super.onInflated()

            icon = itemView.findViewById(R.id.icon)
        }

        override fun getOnClickListener(): View.OnClickListener = View.OnClickListener {
            val index = bindingAdapterPosition
            if (index == RecyclerView.NO_POSITION) {
                return@OnClickListener
            }

            val application = getApplication(index)

            if (applicationManager!!.isVisibleToUser(application)) {
                applicationManager!!.hideApplication(application)
            } else {
                applicationManager!!.showApplication(application)
            }

            notifyItemChanged(index)
        }

        override fun getOnLongClickListener(): View.OnLongClickListener? = null
    }

    override fun onBind(viewHolder: ApplicationViewHolder, index: Int) {
        super.onBind(viewHolder, index)

        val hidden = !applicationManager!!.isVisibleToUser(getApplication(index))
        val hiddenViewHolder = viewHolder as HiddenViewHolder

        hiddenViewHolder.icon!!.alpha = if (hidden) HIDDEN_ALPHA else 1f
        hiddenViewHolder.icon!!.scaleX = if (hidden) HIDDEN_SCALE else 1f
        hiddenViewHolder.icon!!.scaleY = if (hidden) HIDDEN_SCALE else 1f
        hiddenViewHolder.icon!!.setGrayscale(hidden)
    }

    override fun getHolderSupplier(viewType: Int): Supplier<ApplicationViewHolder> =
        Supplier { HiddenViewHolder() }

    private fun notifyItemRemovedInternal() {
        val recyclerView = this.recyclerView ?: return
        val manager = recyclerView.layoutManager ?: return

        val state = manager.onSaveInstanceState()
        notifyItemRangeRemoved(0, itemCount)
        manager.onRestoreInstanceState(state)
    }

    private fun notifyItemInsertedInternal(position: Int) {
        val recyclerView = this.recyclerView ?: return
        val manager = recyclerView.layoutManager ?: return

        val state = manager.onSaveInstanceState()
        notifyItemInserted(position)
        manager.onRestoreInstanceState(state)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        this.recyclerView = recyclerView

        applicationManager!!.addApplicationListener(listener)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)

        applicationManager!!.removeApplicationListener(listener)

        this.recyclerView = null
    }

    override fun getApplication(index: Int): LauncherApplication? =
        if (applicationManager != null) applicationManager.get(index, true)
        else null

    override fun getTotalItemCount(): Int = applicationManager!!.actualSize

    override fun allowApplicationStateEditing(): Boolean = false

    private companion object {
        private const val TAG = "HiddenRecyclerAdapter"
        private const val HIDDEN_ALPHA = 0.3f
        private const val HIDDEN_SCALE = 0.9f
    }
}
