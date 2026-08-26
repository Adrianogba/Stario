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

package adrianogba.stario.launcher.ui.dialogs

import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.PowerManager
import android.util.Log
import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.SearchEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.FloatRange
import adrianogba.stario.launcher.themes.ThemedActivity

class DialogBackgroundDimmingController private constructor(
    private val activity: ThemedActivity
) : SharedPreferences.OnSharedPreferenceChangeListener {

    private val background: Drawable = ColorDrawable(
        activity.getAttributeData(com.google.android.material.R.attr.colorSurfaceContainer)
    )
    private val powerManager = activity.getSystemService(PowerManager::class.java)
    private val settings: SharedPreferences = activity.applicationContext.getSettings()

    private val batterySaverReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            invalidateLowSpec()
        }
    }

    private var dimmingController: DimmingController? = null
    private var hasLimitedResources = false
    private var window: Window? = null

    init {
        background.alpha = 0
    }

    private fun hook(dialog: Dialog): Boolean {
        val window = dialog.window

        if (window == null) {
            Log.w(TAG, "hook() was called too early. Are you sure the window is available?")

            return false
        }

        val original = window.callback

        if (original is WindowDimmingCallbackWrapper) {
            Log.w(TAG, "hook() has already been called before on this window. Ignoring...")

            return false
        }

        this.window = window
        window.callback = WindowDimmingCallbackWrapper(original)

        if (window.decorView.isAttachedToWindow) {
            attach()
        }

        return true
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (LOW_SPEC_KEY == key) {
            invalidateLowSpec()
        }
    }

    private fun invalidateLowSpec() {
        hasLimitedResources =
            settings.getBoolean(LOW_SPEC_KEY, false) || powerManager.isPowerSaveMode

        if (window == null) {
            return
        }

        dimmingController?.invalidate()
    }

    private fun attach() {
        invalidateLowSpec()

        window?.let {
            it.setBackgroundDrawable(background)
            it.setDimAmount(0f)
            it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        activity.registerReceiver(
            batterySaverReceiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        )
        settings.registerOnSharedPreferenceChangeListener(this)
    }

    private fun detach() {
        activity.unregisterReceiver(batterySaverReceiver)
        settings.unregisterOnSharedPreferenceChangeListener(this)
    }

    interface DimmingController {
        fun setFactor(@FloatRange(from = 0.0, to = 1.0) factor: Float)

        fun invalidate()
    }

    private inner class WindowDimmingController(
        private val blur: Boolean,
        private val multiplier: Float
    ) : DimmingController {

        private var lastBlurStep = -1
        private var lastFactor = 0f

        override fun setFactor(factor: Float) {
            val window = window ?: return

            val scaled = factor * multiplier

            updateAlpha(scaled)
            lastFactor = scaled

            if (!blur || hasLimitedResources) {
                return
            }

            val blurRadius = (MAX_BLUR_SIZE * scaled).toInt()

            if (blurRadius != lastBlurStep) {
                window.setBackgroundBlurRadius(blurRadius)
                lastBlurStep = blurRadius
            }
        }

        override fun invalidate() {
            val window = window ?: return

            updateAlpha(lastFactor)

            if (!blur || hasLimitedResources) {
                window.setBackgroundBlurRadius(0)

                lastBlurStep = -1
            }
        }

        private fun updateAlpha(factor: Float) {
            val max = if (hasLimitedResources) MAX_BACKGROUND_ALPHA_LOW_SPEC
            else MAX_BACKGROUND_ALPHA

            background.alpha = (max * factor).toInt()
        }
    }

    /**
     * Intercepts the window's attach and detach so the dimming can register and
     * unregister with it, and hands everything else straight to the callback
     * that was already there.
     */
    inner class WindowDimmingCallbackWrapper(
        private val base: Window.Callback?
    ) : Window.Callback {

        override fun onAttachedToWindow() {
            attach()

            base?.onAttachedToWindow()
        }

        override fun onDetachedFromWindow() {
            detach()

            base?.onDetachedFromWindow()
        }

        // forward everything else to base

        override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
            base?.dispatchGenericMotionEvent(event) ?: false

        override fun dispatchKeyEvent(event: KeyEvent): Boolean =
            base?.dispatchKeyEvent(event) ?: false

        override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean =
            base?.dispatchKeyShortcutEvent(event) ?: false

        override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean =
            base?.dispatchPopulateAccessibilityEvent(event) ?: false

        override fun dispatchTouchEvent(event: MotionEvent): Boolean =
            base?.dispatchTouchEvent(event) ?: false

        override fun dispatchTrackballEvent(event: MotionEvent): Boolean =
            base?.dispatchTrackballEvent(event) ?: false

        override fun onActionModeFinished(mode: ActionMode?) {
            base?.onActionModeFinished(mode)
        }

        override fun onActionModeStarted(mode: ActionMode?) {
            base?.onActionModeStarted(mode)
        }

        override fun onContentChanged() {
            base?.onContentChanged()
        }

        override fun onCreatePanelMenu(index: Int, menu: Menu): Boolean =
            base?.onCreatePanelMenu(index, menu) ?: false

        override fun onCreatePanelView(index: Int): View? = base?.onCreatePanelView(index)

        override fun onMenuItemSelected(index: Int, item: MenuItem): Boolean =
            base?.onMenuItemSelected(index, item) ?: false

        override fun onMenuOpened(index: Int, menu: Menu): Boolean =
            base?.onMenuOpened(index, menu) ?: false

        override fun onPanelClosed(index: Int, menu: Menu) {
            base?.onPanelClosed(index, menu)
        }

        override fun onPreparePanel(index: Int, view: View?, menu: Menu): Boolean =
            base?.onPreparePanel(index, view, menu) ?: false

        override fun onSearchRequested(): Boolean = base?.onSearchRequested() ?: false

        override fun onSearchRequested(event: SearchEvent?): Boolean =
            base?.onSearchRequested(event) ?: false

        override fun onWindowAttributesChanged(params: WindowManager.LayoutParams?) {
            base?.onWindowAttributesChanged(params)
        }

        override fun onWindowFocusChanged(changed: Boolean) {
            base?.onWindowFocusChanged(changed)
        }

        override fun onWindowStartingActionMode(callback: ActionMode.Callback?): ActionMode? =
            base?.onWindowStartingActionMode(callback)

        override fun onWindowStartingActionMode(
            callback: ActionMode.Callback?, index: Int
        ): ActionMode? = base?.onWindowStartingActionMode(callback, index)
    }

    companion object {
        const val LOW_SPEC_KEY = "DialogBackgroundBlurController.LOW_SPEC"

        private const val TAG = "DialogBackgroundBlurController"
        private const val MAX_BACKGROUND_ALPHA_LOW_SPEC = 240
        private const val MAX_BACKGROUND_ALPHA = 190
        private const val MAX_BLUR_SIZE = 100

        @JvmStatic
        @JvmOverloads
        fun attach(
            activity: ThemedActivity,
            dialog: Dialog,
            blur: Boolean,
            multiplier: Float = 1f
        ): DimmingController? {
            val controller = DialogBackgroundDimmingController(activity)

            if (!controller.hook(dialog)) {
                return null
            }

            val dimmingController = controller.WindowDimmingController(blur, multiplier)
            controller.dimmingController = dimmingController

            return dimmingController
        }
    }
}
