/*
 * Copyright (C) 2026 Răzvan Albu
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

package adrianogba.stario.launcher.activities.launcher.widgets.pins.dialog

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.RelativeLayout
import androidx.annotation.IntRange
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.apps.Category
import adrianogba.stario.launcher.apps.LauncherApplication
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.dialogs.PersistentFullscreenDialog
import adrianogba.stario.launcher.ui.utils.HomeWatcher
import adrianogba.stario.launcher.ui.utils.UiUtils
import adrianogba.stario.launcher.ui.utils.animation.Animation
import kotlin.math.max
import kotlin.math.min

class PinnedAppsGroupDialog(
    private val activity: ThemedActivity,
    private val transitionListener: TransitionListener?
) : PersistentFullscreenDialog(activity, activity.getThemeResourceId(), true) {
    private val sourceLayoutChangeListener: View.OnLayoutChangeListener
    private val categoryChangeListener: Category.CategoryItemListener
    private val adapter = PinnedAppsGroupDialogRecyclerAdapter(activity)
    private val manager = GridLayoutManager(activity, 1)
    private val homeWatcher = HomeWatcher(activity)

    private var recyclerContainer: RelativeLayout? = null
    private var container: RelativeLayout? = null
    private var previousOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var allowDismissal = false
    private var recycler: RecyclerView? = null
    private var category: Category? = null
    private var source: View? = null
    private var skip = 0

    init {
        categoryChangeListener = object : Category.CategoryItemListener {
            private fun update() {
                if (isShowing) {
                    dismiss()
                }
            }

            override fun onInserted(application: LauncherApplication?) {
                update()
            }

            override fun onRemoved(application: LauncherApplication?) {
                update()
            }

            override fun onUpdated(application: LauncherApplication?) {
                update()
            }

            override fun onSwapped(index1: Int, index2: Int) {
                update()
            }
        }

        sourceLayoutChangeListener =
            View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                updateRecyclerPositionInContainer(view)
            }

        val lifecycle = activity.lifecycle
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                dismiss(false)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                lifecycle.removeObserver(this)
            }
        })

        homeWatcher.setOnHomePressedListener { dismiss() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.pinned_apps_dialog)

        val container = findViewById<RelativeLayout>(R.id.container)
        this.container = container

        if (container != null) {
            UiUtils.Notch.applyNotchMargin(container, UiUtils.Notch.Treatment.CENTER)
            container.setOnClickListener { dismiss() }
            Measurements.addNavListener { value ->
                val params = container.layoutParams as ViewGroup.MarginLayoutParams

                params.bottomMargin = value
                container.requestLayout()
            }
            Measurements.addStatusBarListener { value ->
                val params = container.layoutParams as ViewGroup.MarginLayoutParams

                params.topMargin = value
                container.requestLayout()
            }

            val recyclerContainer =
                container.findViewById<RelativeLayout>(R.id.recycler_container)
            this.recyclerContainer = recyclerContainer
            val recycler = container.findViewById<RecyclerView>(R.id.recycler)
            this.recycler = recycler

            recyclerContainer.clipToOutline = true

            // this will always update the approximation when we show the dialog,
            // no need for another call
            adapter.setRecyclerHeightApproximationListener { invalidateRecycler() }

            recycler.layoutManager = manager
            recycler.itemAnimator = null
            recycler.adapter = adapter
        } else {
            dismiss()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        homeWatcher.startWatch()
    }

    override fun onDetachedFromWindow() {
        homeWatcher.stopWatch()

        super.onDetachedFromWindow()
    }

    fun setCategory(category: Category?) {
        this.category?.removeCategoryItemListener(categoryChangeListener)

        this.category = category

        this.category?.addCategoryItemListener(categoryChangeListener)

        if (isShowing) {
            adapter.updateDataSnapshot(category, skip)
            manager.spanCount = invalidateRecycler()
        }
    }

    /**
     * Invalidates the recycler layout and calculates the span count.
     *
     * @return layout manager span count
     */
    private fun invalidateRecycler(): Int {
        var size = getItemCount()

        size = if (size < 3) {
            max(1, size)
        } else if (size < 5) {
            2
        } else {
            3
        }

        val recycler = this.recycler
        if (recycler == null || container == null) {
            return size
        }

        val recyclerWidth = size * Measurements.dpToPx(ITEM_SIZE_DP) +
                recycler.paddingLeft + recycler.paddingRight

        val recyclerHeight = min(
            Measurements.dpToPx(Measurements.HEADER_SIZE_DP.toFloat()),
            adapter.approximateRecyclerHeight()
        ) + recycler.paddingBottom + recycler.paddingTop

        val params = recycler.layoutParams

        val changed = params.width != recyclerWidth || params.height != recyclerHeight

        params.width = recyclerWidth
        params.height = recyclerHeight

        if (changed) {
            recycler.layoutParams = params
        }

        recycler.post { updateRecyclerPositionInContainer(source) }

        return size
    }

    private fun getItemCount(): Int {
        val category = this.category ?: return 0

        return max(0, category.size - skip)
    }

    private fun updateRecyclerPositionInContainer(view: View?) {
        val recyclerContainer = this.recyclerContainer
        val recycler = this.recycler
        val container = this.container

        if (recyclerContainer == null || recycler == null ||
            container == null || view == null
        ) {
            return
        }

        if (container.width == 0 || container.height == 0) {
            return
        }

        val sourceLoc = IntArray(2)
        val containerLoc = IntArray(2)
        view.getLocationOnScreen(sourceLoc)
        container.getLocationOnScreen(containerLoc)

        val relativeSourceX = sourceLoc[0] - containerLoc[0]
        val relativeSourceY = sourceLoc[1] - containerLoc[1]

        val sourceCenterX = relativeSourceX + view.width / 2
        val sourceCenterY = relativeSourceY + view.height / 2

        val containerWidth = container.width
        val containerHeight = container.height

        val recyclerWidth = recycler.layoutParams.width
        val recyclerHeight = recycler.layoutParams.height

        if (recyclerWidth == 0 || recyclerHeight == 0) {
            return
        }

        val targetX = (sourceCenterX * (1 - CENTER_PIVOT_WEIGHT) +
                containerWidth / 2f * CENTER_PIVOT_WEIGHT).toInt()
        val targetY = (sourceCenterY * (1 - CENTER_PIVOT_WEIGHT) +
                containerHeight / 2f * CENTER_PIVOT_WEIGHT).toInt()

        var finalLeft = targetX - recyclerWidth / 2
        var finalTop = targetY - recyclerHeight / 2

        val padding = Measurements.getDefaultPadding()
        finalLeft = max(padding, min(finalLeft, containerWidth - recyclerWidth - padding))
        finalTop = max(padding, min(finalTop, containerHeight - recyclerHeight - padding))

        val params = recyclerContainer.layoutParams as ViewGroup.MarginLayoutParams
        if (params.leftMargin != finalLeft || params.topMargin != finalTop) {
            params.leftMargin = finalLeft
            params.topMargin = finalTop

            recyclerContainer.pivotX =
                max(0, min(sourceCenterX - finalLeft, recyclerWidth)).toFloat()
            recyclerContainer.pivotY =
                max(0, min(sourceCenterY - finalTop, recyclerHeight)).toFloat()
            recyclerContainer.layoutParams = params
        }
    }

    fun show(@IntRange(from = 0) skip: Int, source: View?) {
        val category = this.category

        if (isShowing || category == null || category.size <= skip) {
            return
        }

        previousOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        this.skip = max(0, skip)
        adapter.updateDataSnapshot(category, skip)
        manager.spanCount = invalidateRecycler()

        this.source = source
        if (source != null) {
            source.addOnLayoutChangeListener(sourceLayoutChangeListener)
            sourceLayoutChangeListener.onLayoutChange(
                source,
                0, 0, 0, 0, 0, 0, 0, 0
            )
        }

        allowDismissal = true
        super.showDialog()

        val window = window
        window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

        UiUtils.post {
            recyclerContainer!!.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(Animation.MEDIUM.duration.toLong())
                .setUpdateListener { valueAnimator ->
                    setDimmingFactor(valueAnimator.animatedFraction)
                }
                .setInterpolator(DecelerateInterpolator(2.5f))
        }
    }

    override fun dismiss() {
        dismiss(true)
    }

    fun dismiss(animate: Boolean) {
        if (!allowDismissal) {
            return
        }

        val window = window ?: return

        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

        allowDismissal = false
        if (animate) {
            recyclerContainer!!.animate()
                .scaleX(SCALE_FACTOR)
                .scaleY(SCALE_FACTOR)
                .alpha(0f)
                .setDuration(Animation.MEDIUM.duration.toLong())
                .setInterpolator(AccelerateInterpolator(2f))
                .setUpdateListener { valueAnimator ->
                    setDimmingFactor(1f - valueAnimator.animatedFraction)
                }
                .withEndAction {
                    if (!allowDismissal) {
                        window.decorView.post {
                            if (!activity.isDestroyed) {
                                activity.requestedOrientation = previousOrientation
                                superDismiss()
                            }
                        }

                        source?.removeOnLayoutChangeListener(sourceLayoutChangeListener)
                    }
                }
        } else {
            recyclerContainer!!.scaleX = SCALE_FACTOR
            recyclerContainer!!.scaleY = SCALE_FACTOR
            recyclerContainer!!.alpha = 0f

            setDimmingFactor(0f)

            if (!activity.isDestroyed) {
                activity.requestedOrientation = previousOrientation
                superDismiss()
            }

            source?.removeOnLayoutChangeListener(sourceLayoutChangeListener)
        }
    }

    // super is unreachable from inside a lambda, so bounce the call through here
    private fun superDismiss() {
        super.dismiss()
    }

    override fun setDimmingFactor(factor: Float) {
        super.setDimmingFactor(factor)

        transitionListener?.onProgressFraction(factor)
    }

    fun interface TransitionListener {
        fun onProgressFraction(factor: Float)
    }

    private companion object {
        private const val CENTER_PIVOT_WEIGHT = 0.2f
        private const val SCALE_FACTOR = 0.75f
        private const val ITEM_SIZE_DP = 90f
    }
}
