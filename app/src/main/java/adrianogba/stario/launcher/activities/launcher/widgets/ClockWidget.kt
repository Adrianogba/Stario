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

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.content.SharedPreferences
import android.provider.AlarmClock
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.preferences.Vibrations
import adrianogba.stario.launcher.sheet.SheetsFocusController
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.common.grid.DraggableGridItem
import adrianogba.stario.launcher.ui.common.grid.DynamicGridLayout
import adrianogba.stario.launcher.ui.utils.UiUtils

class ClockWidget(private val activity: ThemedActivity) {
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
        gridItem.itemId = CLOCK_TAG

        val layoutData = DynamicGridLayout.ItemLayoutData(CLOCK_TAG, 0, 0, 2, 2)
        this.layoutData = layoutData
        layoutData.minColSpan = 2
        layoutData.minRowSpan = 1

        val root = activity.layoutInflater.inflate(R.layout.home_clock, gridItem, false)
        root.isHapticFeedbackEnabled = false
        root.setOnTouchListener(SheetsFocusController.createClickTouchListener {
            Vibrations.getInstance().vibrate()

            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)

            @Suppress("DEPRECATION")
            if (intent.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(
                    intent,
                    ActivityOptions.makeScaleUpAnimation(
                        root, 0, 0, root.measuredWidth, root.measuredHeight
                    ).toBundle()
                )
            }
        })

        gridItem.addView(root)

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (CLOCK_WIDGET_KEY == key) {
                updateContainerState(
                    container, sharedPreferences.getBoolean(CLOCK_WIDGET_KEY, true)
                )
            }
        }
        this.listener = listener
        preferences.registerOnSharedPreferenceChangeListener(listener)

        updateContainerState(container, preferences.getBoolean(CLOCK_WIDGET_KEY, true))
    }

    private fun updateContainerState(container: DynamicGridLayout, shouldBeVisible: Boolean) {
        UiUtils.post {
            if (shouldBeVisible && !isAttached) {
                container.addItem(gridItem!!, layoutData)
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
        const val CLOCK_WIDGET_KEY: String = "com.stario.HOMESCREEN_CLOCK_WIDGET_VISIBLE"

        private const val CLOCK_TAG = "StylizedClockGlance"
    }
}
