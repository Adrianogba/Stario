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

package adrianogba.stario.launcher.ui.keyboard

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.PopupWindow
import adrianogba.stario.launcher.ui.Measurements
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max

class KeyboardHeightProvider(private val activity: Activity) : PopupWindow(activity) {
    private val observers = CopyOnWriteArrayList<KeyboardHeightListener>()
    private val popupView: View = LinearLayout(activity)

    private val listener = ViewTreeObserver.OnGlobalLayoutListener {
        notifyKeyboardHeightChanged(getKeyboardHeight())
        // on API 29, the GlobalLayoutListener fires before
        // PopupWindow finishes the resizing. Add a post
        // call just to be safe
        popupView.post { notifyKeyboardHeightChanged(getKeyboardHeight()) }
    }

    private val parentView: View

    private var oldHeight = Int.MIN_VALUE

    init {
        popupView.layoutParams = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        popupView.background = ColorDrawable(0)

        contentView = popupView

        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        inputMethodMode = INPUT_METHOD_NEEDED

        parentView = activity.findViewById(android.R.id.content)

        width = 0
        height = ViewGroup.LayoutParams.MATCH_PARENT
    }

    fun start() {
        if (!isShowing && parentView.windowToken != null) {
            setBackgroundDrawable(ColorDrawable(0))
            showAtLocation(parentView, Gravity.NO_GRAVITY, 0, 0)

            parentView.viewTreeObserver.addOnGlobalLayoutListener(listener)

            notifyKeyboardHeightChanged(getKeyboardHeight(), true)
        }
    }

    fun close() {
        dismiss()

        parentView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
    }

    fun addKeyboardHeightListener(observer: KeyboardHeightListener?) {
        if (observer != null) {
            observers.add(observer)
        }
    }

    fun removeKeyboardHeightListener(observer: KeyboardHeightListener?) {
        if (observer != null) {
            observers.remove(observer)
        }
    }

    private fun notifyKeyboardHeightChanged(height: Int, force: Boolean = false) {
        if (oldHeight == height && !force) {
            return
        }

        oldHeight = height

        for (observer in observers) {
            observer.onKeyboardHeightChanged(height)
        }
    }

    fun getKeyboardHeight(): Int {
        if (!isShowing) {
            return 0
        }

        val windowInsets = parentView.rootWindowInsets ?: return 0

        val insets = windowInsets.getInsets(WindowInsets.Type.ime())

        return max(0, insets.bottom - Measurements.getNavHeight())
    }

    fun interface KeyboardHeightListener {
        fun onKeyboardHeightChanged(height: Int)
    }
}
