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

package adrianogba.stario.launcher.themes

import android.animation.ValueAnimator
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.PaintDrawable
import android.os.Bundle
import android.transition.Transition
import android.transition.TransitionListenerAdapter
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.core.app.ActivityCompat
import adrianogba.stario.launcher.Stario
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.back.BackEvent
import adrianogba.stario.launcher.ui.back.BackEventType
import adrianogba.stario.launcher.ui.back.BackGestureEventBus
import adrianogba.stario.launcher.ui.common.AnimatedInsetDrawable
import adrianogba.stario.launcher.ui.utils.UiUtils
import adrianogba.stario.launcher.ui.utils.animation.Animation
import java.util.Arrays
import java.util.HashMap

abstract class ThemedActivity : AppCompatActivity() {
    private val activityResultListeners = HashMap<Int, OnActivityResult>()
    private val requestPermissionResultListeners = HashMap<Int, OnPermissionRequestResult>()

    private var uiInsetBackgroundAnimator: AnimatedInsetDrawable? = null
    private var roundedCornerBackground: PaintDrawable? = null
    private var themePreferences: SharedPreferences? = null
    private var backgroundAnimator: ValueAnimator? = null
    private var windowBackground: ColorDrawable? = null
    private var allowTouches = true
    private var backgroundColor = 0
    private var currentTheme: Theme? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val themePreferences = applicationContext.getSharedPreferences(Entry.THEME)
        this.themePreferences = themePreferences

        //default theme if it doesn't exist
        if (!themePreferences.contains(THEME)) {
            themePreferences.edit().putString(THEME, Theme.THEME_DYNAMIC.toString()).apply()
        }

        //night mode flags
        val nightModeFlags =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkModeOn = nightModeFlags == Configuration.UI_MODE_NIGHT_YES ||
                themePreferences.getBoolean(FORCE_DARK, false)

        // Theme.from rejects null, exactly as the Java version's intrinsics check did
        val theme = Theme.from(themePreferences.getString(THEME, Theme.THEME_BLUE.toString())!!)
        this.currentTheme = theme
        setTheme(if (isDarkModeOn) theme.darkResourceID else theme.lightResourceID)

        backgroundColor = getAttributeData(com.google.android.material.R.attr.colorSurface)

        val roundedCornerBackground = PaintDrawable(backgroundColor)
        this.roundedCornerBackground = roundedCornerBackground
        uiInsetBackgroundAnimator = AnimatedInsetDrawable(roundedCornerBackground)
        roundedCornerBackground.setCornerRadius(Measurements.dpToPx(30f).toFloat())

        val window: Window? = window

        if (window != null) {
            UiUtils.setWindowTransitions(window)
            UiUtils.makeSysUITransparent(window)

            if (isOpaque) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                windowBackground = ColorDrawable(Color.TRANSPARENT)
            } else {
                window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)

                windowBackground = ColorDrawable(backgroundColor)
                windowBackground!!.alpha = 0
            }

            window.setBackgroundDrawable(windowBackground)
            window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)

            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }

        onBackPressedDispatcher.addCallback(this,
            object : OnBackPressedCallback(true) {
                private val cubicBezier: Easing = CubicBezierEasing(0.43f, 0.1f, -0.2f, 1f)

                private var startingWindowBackgroundAlpha = if (isOpaque) 255 else 0
                private var initialized = false

                private fun handleInit(): Boolean {
                    if (initialized) {
                        return true
                    }

                    val animator = backgroundAnimator
                    if (animator != null && animator.isRunning) {
                        animator.pause()
                    }

                    val root = root
                    if (root != null) {
                        root.animate().cancel()

                        startingWindowBackgroundAlpha = if (isActivityTransitionRunning)
                            (if (isOpaque) 255 else 0) else windowBackground!!.alpha

                        this.initialized = true
                        return true
                    }

                    return false
                }

                override fun handleOnBackStarted(backEvent: BackEventCompat) {
                    if (isAffectedByBackGesture && !isActivityTransitionRunning) {
                        handleInit()
                    }
                }

                override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                    if (isAffectedByBackGesture && handleInit()) {
                        val progress = cubicBezier.transform(backEvent.progress)

                        if (!isActivityTransitionRunning) {
                            val root = root

                            if (root != null) {
                                windowBackground!!.alpha =
                                    ((1f - progress * 2).coerceAtLeast(0f) * 255).toInt()

                                root.pivotY = backEvent.touchY / 1.3f
                                var deltaX = 0f
                                if (backEvent.swipeEdge == BackEventCompat.EDGE_LEFT) {
                                    deltaX = backEvent.touchX
                                } else if (backEvent.swipeEdge == BackEventCompat.EDGE_RIGHT) {
                                    deltaX = backEvent.touchX - root.width
                                }
                                root.pivotX = deltaX * 0.3f + root.width / 2f

                                root.scaleX = 1f - progress * 0.15f
                                root.translationY = (root.height / 10f) * progress
                                root.scaleY = 1f - progress * 0.15f

                                val expProgress = Math.pow(progress.toDouble(), 0.3).toFloat()
                                roundedCornerBackground!!.setCornerRadius(
                                    Measurements.dpToPx(10f) +
                                            expProgress * Measurements.dpToPx(20f)
                                )
                                uiInsetBackgroundAnimator!!.setInsets(
                                    0,
                                    (Measurements.getSysUIHeight() * expProgress).toInt(),
                                    0, (Measurements.getNavHeight() * expProgress).toInt()
                                )
                            }
                        }

                        BackGestureEventBus.getInstance().postEvent(
                            BackEvent(
                                BackEventType.BACK_PROGRESS,
                                progress,
                                this@ThemedActivity.javaClass
                            )
                        )
                    }
                }

                override fun handleOnBackCancelled() {
                    if (!initialized) {
                        return
                    }

                    if (isAffectedByBackGesture) {
                        if (!isActivityTransitionRunning) {
                            val root = root

                            if (root != null) {
                                val alpha = windowBackground!!.alpha

                                root.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(Animation.MEDIUM.duration.toLong())
                                    .setUpdateListener { animation ->
                                        val fraction = animation.animatedFraction

                                        windowBackground!!.alpha = alpha +
                                                ((startingWindowBackgroundAlpha - alpha) * fraction).toInt()
                                    }
                                    .withEndAction {
                                        windowBackground!!.alpha = startingWindowBackgroundAlpha
                                    }
                            }
                        }

                        BackGestureEventBus.getInstance().postEvent(
                            BackEvent(
                                BackEventType.BACK_CANCELLED,
                                origin = this@ThemedActivity.javaClass
                            )
                        )
                    }

                    initialized = false
                }

                override fun handleOnBackPressed() {
                    if (initialized) {
                        val previous = backgroundAnimator
                        if (previous != null && previous.isRunning) {
                            previous.pause()
                        }

                        val animator = ValueAnimator.ofInt(windowBackground!!.alpha, 0)
                        backgroundAnimator = animator
                        animator.duration = Animation.BRIEF.duration.toLong()
                        animator.addUpdateListener {
                            windowBackground!!.alpha = animator.animatedValue as Int
                        }
                        animator.start()
                    }

                    if (isAffectedByBackGesture) {
                        finishAfterTransition()

                        BackGestureEventBus.getInstance().postEvent(
                            BackEvent(
                                BackEventType.BACK_COMPLETED,
                                origin = this@ThemedActivity.javaClass
                            )
                        )
                    }

                    initialized = false
                }
            })

        super.onCreate(savedInstanceState)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        val window: Window? = window

        if (window != null && isOpaque) {
            root!!.background = uiInsetBackgroundAnimator

            if (isActivityTransitionRunning) {
                val previous = backgroundAnimator
                if (previous != null && previous.isRunning) {
                    previous.pause()
                }

                val animator = ValueAnimator.ofFloat(30f, 0f)
                backgroundAnimator = animator
                animator.interpolator = AccelerateInterpolator(2f)
                animator.duration = (Animation.EXTENDED.duration *
                        Measurements.getTransitionAnimationScale() /
                        Measurements.getAnimatorDurationScale()).toInt().toLong()
                animator.addUpdateListener { animation ->
                    roundedCornerBackground!!.setCornerRadius(
                        Measurements.dpToPx(animation.animatedValue as Float).toFloat()
                    )
                }
                animator.start()

                val transition = window.enterTransition
                transition.addListener(object : TransitionListenerAdapter() {
                    override fun onTransitionEnd(transition: Transition) {
                        assignActualBackgroundColor(window)
                        transition.removeListener(this)
                    }

                    override fun onTransitionCancel(transition: Transition) {
                        assignActualBackgroundColor(window)
                        transition.removeListener(this)
                    }
                })
            } else {
                assignActualBackgroundColor(window)
            }
        }
    }

    private fun assignActualBackgroundColor(window: Window) {
        roundedCornerBackground!!.setCornerRadius(Measurements.dpToPx(0f).toFloat())

        val background = ColorDrawable(backgroundColor)
        windowBackground = background

        // If the window background, when set, is completely opaque (alpha 255),
        // The window will treat every alpha value for the wallpaper as black
        background.alpha = 0
        window.setBackgroundDrawable(background)
        background.alpha = 255
    }

    override fun onResume() {
        val window: Window? = window

        if (window != null) {
            UiUtils.makeSysUITransparent(window)
        }

        super.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()

        activityResultListeners.clear()
        requestPermissionResultListeners.clear()
    }

    override fun onConfigurationChanged(configuration: Configuration) {
        if (Measurements.wereTaken()) {
            Measurements.remeasure(root)
        }

        super.onConfigurationChanged(configuration)
    }

    protected open val root: ViewGroup?
        get() = (findViewById<ViewGroup>(android.R.id.content)).getChildAt(0) as ViewGroup?

    override fun getApplicationContext(): Stario = super.getApplicationContext() as Stario

    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
        throw RuntimeException("Use the application context to retrieve shared preferences.")
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean =
        allowTouches && super.dispatchTouchEvent(ev)

    open fun setTouchEnabled(enabled: Boolean) {
        this.allowTouches = enabled
    }

    open fun isTouchEnabled(): Boolean = allowTouches

    open fun getThemeType(): Theme? = currentTheme

    open fun getThemeResourceId(): Int {
        val nightModeFlags =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkModeOn = nightModeFlags == Configuration.UI_MODE_NIGHT_YES ||
                themePreferences!!.getBoolean(FORCE_DARK, false)

        return if (isDarkModeOn) currentTheme!!.darkResourceID else currentTheme!!.lightResourceID
    }

    open fun getAttributeData(@AttrRes attr: Int): Int = getAttributeData(currentTheme!!, attr)

    open fun getAttributeData(@AttrRes attr: Int, forceDark: Boolean): Int =
        getAttributeData(currentTheme!!, attr, forceDark)

    open fun getAttributeData(theme: Theme, @AttrRes attr: Int): Int =
        getAttributeData(theme, attr, false)

    open fun getAttributeData(theme: Theme, @AttrRes attr: Int, forceDark: Boolean): Int {
        val typedValue = TypedValue()

        val wrappedTheme = getThemeFor(theme, forceDark)
        wrappedTheme.resolveAttribute(attr, typedValue, true)

        return typedValue.data
    }

    open fun getTheme(forceDark: Boolean): Resources.Theme = getThemeFor(currentTheme!!, forceDark)

    private fun getThemeFor(theme: Theme, forceDark: Boolean): Resources.Theme {
        val wrapper: ContextThemeWrapper

        val nightModeFlags =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkModeOn = nightModeFlags == Configuration.UI_MODE_NIGHT_YES ||
                themePreferences!!.getBoolean(FORCE_DARK, false)
        wrapper = if (isDarkModeOn || forceDark) {
            ContextThemeWrapper(applicationContext, theme.darkResourceID)
        } else {
            ContextThemeWrapper(applicationContext, theme.lightResourceID)
        }

        return wrapper.theme
    }

    protected abstract val isOpaque: Boolean

    protected abstract val isAffectedByBackGesture: Boolean

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        for (resultListener in activityResultListeners.values) {
            resultListener?.onResult(resultCode, data)
        }
    }

    open fun addOnActivityResultListener(
        configurationCode: Int,
        listener: OnActivityResult?
    ): Boolean {
        if (listener != null) {
            return activityResultListeners.putIfAbsent(configurationCode, listener) == null
        }

        return false
    }

    open fun removeOnActivityResultListener(configurationCode: Int) {
        activityResultListeners.remove(configurationCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val listener = requestPermissionResultListeners.remove(requestCode)
        listener?.onResult(grantResults)
    }

    open fun requestPermissions(
        permissions: Array<String>,
        listener: OnPermissionRequestResult
    ) {
        var granted = true

        for (permission in permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                granted = false
            }
        }

        if (!granted) {
            requestCode = (requestCode + 1) % 1_000_000

            ActivityCompat.requestPermissions(this, permissions, requestCode)
            requestPermissionResultListeners[requestCode] = listener
        } else {
            val results = IntArray(permissions.size)
            Arrays.fill(results, PackageManager.PERMISSION_GRANTED)

            listener.onResult(results)
        }
    }

    fun interface OnActivityResult {
        fun onResult(resultCode: Int, intent: Intent?)
    }

    fun interface OnPermissionRequestResult {
        fun onResult(grantResults: IntArray)
    }

    companion object {
        const val THEME: String = "com.stario.THEME"
        const val FORCE_DARK: String = "com.stario.FORCE_DARK"

        // Stores a SurfaceStyle name. Absent means MATERIAL, which is what
        // every install has been getting until now.
        const val SURFACE_STYLE: String = "com.stario.SURFACE_STYLE"

        private var requestCode = 0
    }
}
