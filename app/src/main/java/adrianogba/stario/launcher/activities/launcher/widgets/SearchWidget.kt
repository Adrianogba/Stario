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

package adrianogba.stario.launcher.activities.launcher.widgets

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.preferences.Vibrations
import adrianogba.stario.launcher.sheet.SheetsFocusController
import adrianogba.stario.launcher.sheet.drawer.dialog.ApplicationsDialog
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.common.grid.DraggableGridItem
import adrianogba.stario.launcher.ui.common.grid.DynamicGridLayout
import adrianogba.stario.launcher.ui.utils.LayoutSizeObserver
import adrianogba.stario.launcher.ui.utils.UiUtils

class SearchWidget(private val activity: ThemedActivity) {
    private val preferences: SharedPreferences = activity.applicationContext.getSettings()

    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var layoutData: DynamicGridLayout.ItemLayoutData? = null
    private var gridItem: DraggableGridItem? = null
    private var isAttached = false

    @SuppressLint("ClickableViewAccessibility")
    fun attach(container: DynamicGridLayout) {
        if (gridItem != null) {
            return
        }

        val gridItem = DraggableGridItem(activity)
        this.gridItem = gridItem
        gridItem.itemId = SEARCH_TAG

        val layoutData = DynamicGridLayout.ItemLayoutData(SEARCH_TAG, 0, 0, 1, 1)
        this.layoutData = layoutData
        layoutData.maxColSpan = 1
        layoutData.maxRowSpan = 1

        val root = activity.layoutInflater.inflate(R.layout.home_search, gridItem, false)

        val background = root.findViewById<View>(R.id.background)
        LayoutSizeObserver.attach(
            background, LayoutSizeObserver.WIDTH or LayoutSizeObserver.HEIGHT,
            object : LayoutSizeObserver.OnChange {
                override fun onChange(view: View, watchFlags: Int) {
                    background.pivotX = background.width / 2f
                    background.pivotY = background.height / 2f
                }
            })
        background.pivotX = background.width / 2f
        background.pivotY = background.height / 2f

        val rotate = ObjectAnimator.ofFloat(background, View.ROTATION, 0f, 360f)
        rotate.duration = 50000
        rotate.repeatCount = ValueAnimator.INFINITE
        rotate.interpolator = LinearInterpolator()
        rotate.start()

        root.setOnTouchListener(SheetsFocusController.createClickTouchListener {
            Vibrations.getInstance().vibrate()

            @Suppress("DEPRECATION")
            LocalBroadcastManager.getInstance(activity)
                .sendBroadcastSync(Intent(ApplicationsDialog.INTENT_LAUNCH_SEARCH))
        })

        gridItem.addView(root)

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (SEARCH_WIDGET_KEY == key) {
                updateContainerState(
                    container, sharedPreferences.getBoolean(SEARCH_WIDGET_KEY, true)
                )
            }
        }
        this.listener = listener
        preferences.registerOnSharedPreferenceChangeListener(listener)

        updateContainerState(container, preferences.getBoolean(SEARCH_WIDGET_KEY, true))
    }

    private fun updateContainerState(container: DynamicGridLayout, shouldBeVisible: Boolean) {
        UiUtils.post {
            if (shouldBeVisible && !isAttached) {
                container.addItem(gridItem, layoutData)
                isAttached = true
            } else if (!shouldBeVisible && isAttached) {
                container.removeItem(gridItem)
                isAttached = false
            }
        }
    }

    fun detach() {
        listener?.let { preferences.unregisterOnSharedPreferenceChangeListener(it) }
    }

    companion object {
        const val SEARCH_WIDGET_KEY: String = "com.stario.HOMESCREEN_SEARCH_WIDGET_VISIBLE"

        private const val SEARCH_TAG = "SearchWidget"
    }
}
