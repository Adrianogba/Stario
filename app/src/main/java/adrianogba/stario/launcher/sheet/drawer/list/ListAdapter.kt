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

package adrianogba.stario.launcher.sheet.drawer.list

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.apps.LauncherApplication
import adrianogba.stario.launcher.apps.ProfileApplicationManager
import adrianogba.stario.launcher.apps.interfaces.LauncherApplicationListener
import adrianogba.stario.launcher.preferences.Vibrations
import adrianogba.stario.launcher.sheet.drawer.RecyclerApplicationAdapter
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.icons.AdaptiveIconView
import adrianogba.stario.launcher.ui.recyclers.FastScroller
import adrianogba.stario.launcher.ui.utils.animation.Animation
import kotlin.math.min

class ListAdapter(
    activity: ThemedActivity,
    private val applicationManager: ProfileApplicationManager
) : RecyclerApplicationAdapter(activity),
    FastScroller.OnPopupViewUpdate,
    FastScroller.OnPopupViewReset {

    private var recyclerView: RecyclerView? = null
    private var oldScrollerPosition = -1

    private val listener = object : LauncherApplicationListener {
        override fun onHidden(application: LauncherApplication?) = postRemoval()

        override fun onRemoved(application: LauncherApplication?) = postRemoval()

        override fun onInserted(application: LauncherApplication?) = postInsertion(application)

        override fun onShowed(application: LauncherApplication?) = postInsertion(application)

        override fun onUpdated(application: LauncherApplication?) {
            recyclerView?.post {
                notifyItemChanged(applicationManager.indexOf(application))
            }
        }

        private fun postRemoval() {
            recyclerView?.post {
                notifyItemRemovedInternal()
                approximateRecyclerHeight()
            }
        }

        private fun postInsertion(application: LauncherApplication?) {
            recyclerView?.post {
                notifyItemInsertedInternal(applicationManager.indexOf(application))
                approximateRecyclerHeight()
            }
        }
    }

    /**
     * Saving and restoring the layout manager's state around the notify keeps
     * the list from jumping to the top when an item comes or goes.
     */
    private fun notifyItemRemovedInternal() {
        val manager = recyclerView?.layoutManager ?: return

        val state = manager.onSaveInstanceState()
        notifyItemRangeRemoved(0, itemCount)
        manager.onRestoreInstanceState(state)
    }

    private fun notifyItemInsertedInternal(position: Int) {
        val manager = recyclerView?.layoutManager ?: return

        val state = manager.onSaveInstanceState()
        notifyItemInserted(position)
        manager.onRestoreInstanceState(state)
    }

    override fun onUpdate(index: Int, textView: TextView) {
        removeLimit()

        val position = min(index, applicationManager.size - 1)

        if (oldScrollerPosition != position) {
            Vibrations.getInstance().vibrate()

            val layoutManager = recyclerView?.layoutManager

            if (layoutManager != null) {
                scaleTo(layoutManager.findViewByPosition(position), AdaptiveIconView.MAX_SCALE)
                scaleTo(layoutManager.findViewByPosition(oldScrollerPosition), 1f)
            }
        }

        oldScrollerPosition = position

        val label = applicationManager.get(position)?.label

        if (!label.isNullOrEmpty()) {
            textView.text = label[0].toString().uppercase()
        }
    }

    private fun scaleTo(view: View?, scale: Float) {
        view?.animate()
            ?.scaleX(scale)
            ?.scaleY(scale)
            ?.setDuration(Animation.MEDIUM.duration.toLong())
    }

    override fun onReset(index: Int) {
        val layoutManager = recyclerView?.layoutManager ?: return

        val currentView = layoutManager.findViewByPosition(oldScrollerPosition)
        oldScrollerPosition = -1

        currentView?.animate()
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.setDuration(Animation.MEDIUM.duration.toLong())
            ?.setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    currentView.scaleX = 1f
                    currentView.scaleY = 1f
                }
            })
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        this.recyclerView = recyclerView

        applicationManager.addApplicationListener(listener)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)

        applicationManager.removeApplicationListener(listener)

        this.recyclerView = null
    }

    override fun getApplication(index: Int): LauncherApplication? =
        applicationManager.get(index)

    override fun allowApplicationStateEditing(): Boolean = true

    override fun getItemId(position: Int): Long =
        applicationManager.get(position)?.info?.packageName?.hashCode()?.toLong() ?: -1

    override fun getTotalItemCount(): Int = applicationManager.size
}
