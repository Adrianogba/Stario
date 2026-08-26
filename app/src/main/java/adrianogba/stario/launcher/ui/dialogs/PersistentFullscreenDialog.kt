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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.annotation.FloatRange
import androidx.appcompat.app.AppCompatDialog
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.utils.UiUtils

open class PersistentFullscreenDialog(
    private val activity: ThemedActivity,
    theme: Int,
    private val blur: Boolean
) : AppCompatDialog(activity, getThemeResId(activity, theme)) {

    private var dimmingController: DialogBackgroundDimmingController.DimmingController? = null

    @JvmField
    protected var listener: OnBackPressed? = null

    init {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportRequestWindowFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val window = window

        if (window != null) {
            dimmingController = DialogBackgroundDimmingController.attach(activity, this, blur)

            window.setWindowAnimations(0)
            window.setFormat(PixelFormat.TRANSLUCENT)
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            UiUtils.makeSysUITransparent(window)
        }
    }

    open fun setDimmingFactor(@FloatRange(from = 0.0, to = 1.0) factor: Float) {
        dimmingController?.setFactor(factor)
    }

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        val listener = this.listener

        if (listener == null || listener.onPressed()) {
            super.onBackPressed()
        }
    }

    override fun show() {
        // Disable default show behaviour
    }

    override fun hide() {
        window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

        super.hide()
    }

    protected open fun superShow(): Boolean {
        if (activity.hasWindowFocus()) {
            super.show()

            return true
        }

        return false
    }

    open fun showDialog(): Boolean {
        if (activity.hasWindowFocus()) {
            super.show()

            window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

            return true
        }

        return false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return activity.isTouchEnabled() && super.dispatchTouchEvent(ev)
    }

    open fun setOnBackPressed(listener: OnBackPressed?) {
        this.listener = listener
    }

    fun interface OnBackPressed {
        fun onPressed(): Boolean
    }

    private companion object {
        @JvmStatic
        private fun getThemeResId(context: Context, themeId: Int): Int {
            // reuse the bottomSheetDialog theme
            if (themeId != 0) {
                return themeId
            }

            val outValue = TypedValue()

            return if (context.theme.resolveAttribute(
                    com.google.android.material.R.attr.bottomSheetDialogTheme, outValue, true
                )
            ) {
                outValue.resourceId
            } else {
                com.google.android.material.R.style.Theme_Design_Light_BottomSheetDialog
            }
        }
    }
}
