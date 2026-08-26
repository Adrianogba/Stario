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

package adrianogba.stario.launcher.ui.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.transition.TransitionSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.material.transition.platform.MaterialSharedAxis
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.utils.animation.Animation
import kotlin.math.max

object UiUtils {
    private val UIHandler = Handler(Looper.getMainLooper())

    @JvmStatic
    @Suppress("DEPRECATION")
    fun enforceLightSystemUI(window: Window) {
        val decor = window.decorView

        var flags = decor.systemUiVisibility
        flags = flags and (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR).inv()
        decor.systemUiVisibility = flags
    }

    @JvmStatic
    fun setWindowTransitions(window: Window) {
        window.setAllowEnterTransitionOverlap(true)
        window.setAllowReturnTransitionOverlap(true)

        val transition = TransitionSet()
        transition.addTransition(MaterialSharedAxis(MaterialSharedAxis.Z, true))
        transition.setInterpolator(FastOutSlowInInterpolator())
        transition.setDuration(Animation.EXTENDED.duration.toLong())

        transition.excludeTarget(R.id.navigation_bar_contrast, true)
        transition.excludeTarget(R.id.status_bar_contrast, true)

        window.enterTransition = transition
        window.exitTransition = transition
        window.reenterTransition = transition
        window.returnTransition = transition
    }

    @JvmStatic
    fun makeSysUITransparent(window: Window) {
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarDividerColor = Color.TRANSPARENT

        window.clearFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        )

        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false

        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    // https://stackoverflow.com/a/54198408
    @JvmStatic
    fun expandStatusBar(context: Context?) {
        if (context == null) {
            return
        }

        try {
            @SuppressLint("WrongConstant")
            val service = context.getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")

            statusBarManager.getMethod("expandNotificationsPanel").invoke(service)
        } catch (exception: Exception) {
            Log.e("UiUtils", "expandStatusBar: Could not expand the status bar.", exception)
        }
    }

    @JvmStatic
    fun areAnimationsOn(): Boolean = Measurements.getAnimatorDurationScale() > 0

    @JvmStatic
    fun areTransitionsOn(): Boolean = Measurements.getTransitionAnimationScale() > 0

    @JvmStatic
    fun areWindowAnimationsOn(): Boolean = Measurements.getWindowAnimationScale() > 0

    @JvmStatic
    fun isKeyboardVisible(view: View?): Boolean {
        if (view != null) {
            val insets = ViewCompat.getRootWindowInsets(view)

            if (insets != null) {
                return insets.isVisible(WindowInsetsCompat.Type.ime())
            }
        }

        return false
    }

    @JvmStatic
    fun hideKeyboard(view: View) {
        val inputMethodManager =
            view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        view.clearFocus()
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    @JvmStatic
    fun showKeyboard(view: View) {
        val inputMethodManager =
            view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        view.requestFocus()
        inputMethodManager.showSoftInput(view, 0)
    }

    @JvmStatic
    fun post(runnable: Runnable) {
        UIHandler.post(runnable)
    }

    @JvmStatic
    fun removeUICallback(runnable: Runnable) {
        UIHandler.removeCallbacks(runnable)
    }

    @JvmStatic
    fun postDelayed(runnable: Runnable, delay: Long) {
        UIHandler.postDelayed(runnable, delay)
    }

    @JvmStatic
    fun loopOnUIThread(runnable: Runnable, period: Long, condition: Condition) {
        UIHandler.post(object : Runnable {
            override fun run() {
                if (condition.evaluate()) {
                    runnable.run()

                    UIHandler.postDelayed(this, period)
                }
            }
        })
    }

    @JvmStatic
    fun unwrapContext(context: Context): Activity? {
        var current = context

        while (current !is Activity && current is ContextWrapper) {
            current = current.baseContext
        }

        if (current is Activity) {
            return current
        }

        return null
    }

    @JvmStatic
    fun roundViewGroup(view: ViewGroup, radiusDp: Int) {
        view.clipChildren = true
        view.clipToOutline = true

        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(
                    Rect(0, 0, view.measuredWidth, view.measuredHeight),
                    Measurements.dpToPx(radiusDp.toFloat()).toFloat()
                )
            }
        }
    }

    fun interface Condition {
        fun evaluate(): Boolean
    }

    object Notch {
        @JvmStatic
        @JvmOverloads
        fun applyNotchMargin(
            view: View,
            treatment: Treatment = Treatment.DEFAULT,
            listener: OnNotchMarginApplied? = null
        ) {
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, compatInset ->
                val cutoutInsets = compatInset.getInsets(WindowInsetsCompat.Type.displayCutout())
                val navigationInsets =
                    compatInset.getInsets(WindowInsetsCompat.Type.navigationBars())

                val params = view.layoutParams as ViewGroup.MarginLayoutParams

                when (treatment) {
                    Treatment.CENTER -> {
                        val margin = max(
                            cutoutInsets.left + navigationInsets.left,
                            cutoutInsets.right + navigationInsets.right
                        )
                        params.leftMargin = margin
                        params.rightMargin = margin
                    }

                    Treatment.INVERSE -> {
                        params.leftMargin = cutoutInsets.right + navigationInsets.right
                        params.rightMargin = cutoutInsets.left + navigationInsets.left
                    }

                    else -> { // DEFAULT
                        params.leftMargin = cutoutInsets.left + navigationInsets.left
                        params.rightMargin = cutoutInsets.right + navigationInsets.right
                    }
                }

                view.layoutParams = params

                listener?.onApplied()

                compatInset
            }

            if (view.isAttachedToWindow) {
                ViewCompat.requestApplyInsets(view)
            } else {
                view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(view: View) {
                        ViewCompat.requestApplyInsets(view)
                        view.removeOnAttachStateChangeListener(this)
                    }

                    override fun onViewDetachedFromWindow(view: View) {
                        view.removeOnAttachStateChangeListener(this)
                    }
                })
            }
        }

        enum class Treatment {
            DEFAULT,
            CENTER,
            INVERSE
        }

        fun interface OnNotchMarginApplied {
            fun onApplied()
        }
    }
}
