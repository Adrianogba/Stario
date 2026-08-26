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

package adrianogba.stario.launcher.ui.common.lock

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import adrianogba.stario.launcher.BuildConfig
import adrianogba.stario.launcher.activities.launcher.Launcher
import adrianogba.stario.launcher.services.AccessibilityService
import adrianogba.stario.launcher.ui.common.grid.DynamicGridLayout

class LockDetector(context: Context, attrs: AttributeSet?) : DynamicGridLayout(context, attrs) {
    private val detector: DoubleTapDetector
    private var shouldIntercept: Boolean

    init {
        if (context !is Launcher) {
            throw RuntimeException("LockDetector needs the Launcher context. (Is this view used in an activity other than Launcher.java?)")
        }

        detector = DoubleTapDetector(context)
        shouldIntercept = false
        isHapticFeedbackEnabled = false
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        shouldIntercept = false

        if (detector.onTouchEvent(event)) {
            shouldIntercept = true
            requestDisallowInterceptTouchEvent(true)

            return true
        }

        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return shouldIntercept || super.onTouchEvent(event)
    }

    private inner class DoubleTapDetector(launcher: Launcher) {
        private val activity: Activity = launcher
        private val preferences: SharedPreferences = launcher.applicationContext.getSettings()

        private var lastEventTime: Long = NOT_REGISTERED

        fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_DOWN) {
                return false
            }

            val currentTime = System.currentTimeMillis()

            if (lastEventTime > 0 &&
                currentTime - lastEventTime < ViewConfiguration.getDoubleTapTimeout()
            ) {
                if (isAccessibilitySettingsOn(activity) &&
                    preferences.getBoolean(PREFERENCE_ENTRY, false)
                ) {
                    if (!preferences.getBoolean(LEGACY_ANIMATION, false)) {
                        getClosingAnimationView()
                            .closeTo(event.rawX, event.rawY) { sleep(activity) }
                    } else {
                        sleep(activity)
                    }

                    lastEventTime = NOT_REGISTERED
                    return true
                }

                lastEventTime = NOT_REGISTERED
                return false
            }

            lastEventTime = currentTime
            return false
        }

        private fun getClosingAnimationView(): ClosingAnimationView {
            var view: View? = this@LockDetector

            while (view != null) {
                if (view is ClosingAnimationView) {
                    return view
                }

                val parent = view.parent

                view = if (parent is View) parent else null
            }

            throw RuntimeException(
                "This view must be a child of " + ClosingAnimationView::class.java.name
            )
        }

        private fun sleep(context: Context) {
            val manager =
                context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

            @Suppress("DEPRECATION")
            val event = AccessibilityEvent.obtain()

            event.eventType = AccessibilityEvent.TYPE_ANNOUNCEMENT
            event.className = javaClass.name
            event.action = AccessibilityService.LOCK

            activity.setShowWhenLocked(true)
            manager.sendAccessibilityEvent(event)
        }

        private fun isAccessibilitySettingsOn(context: Context): Boolean {
            var accessibilityEnabled = 0

            val service = BuildConfig.APPLICATION_ID + "/" +
                    AccessibilityService::class.java.canonicalName
            try {
                accessibilityEnabled = Settings.Secure.getInt(
                    context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED
                )
            } catch (exception: Settings.SettingNotFoundException) {
                Log.e(
                    "", "Error finding setting, default accessibility to not found: " +
                            exception.message
                )
            }

            val stringColonSplitter = TextUtils.SimpleStringSplitter(':')

            if (accessibilityEnabled == 1) {
                val settingValue = Settings.Secure.getString(
                    context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )

                if (settingValue != null) {
                    stringColonSplitter.setString(settingValue)

                    while (stringColonSplitter.hasNext()) {
                        val accessibilityService = stringColonSplitter.next()
                        if (accessibilityService.equals(service, ignoreCase = true)) {
                            return true
                        }
                    }
                }
            }

            return false
        }
    }

    companion object {
        const val PREFERENCE_ENTRY = "com.stario.LockDetector.LOCK"
        const val LEGACY_ANIMATION = "com.stario.LockDetector.LEGACY_LOCK_ANIMATION"

        private const val NOT_REGISTERED = -1L
    }
}
