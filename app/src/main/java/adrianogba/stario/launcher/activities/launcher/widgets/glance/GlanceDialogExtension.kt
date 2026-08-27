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

package adrianogba.stario.launcher.activities.launcher.widgets.glance

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Dialog
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import androidx.annotation.FloatRange
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.DialogFragment
import adrianogba.stario.launcher.preferences.Vibrations
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.common.glance.GlanceConstraintLayout
import adrianogba.stario.launcher.ui.dialogs.PersistentFullscreenDialog
import adrianogba.stario.launcher.ui.utils.HomeWatcher
import adrianogba.stario.launcher.ui.utils.animation.Animation
import java.util.concurrent.CopyOnWriteArrayList

abstract class GlanceDialogExtension protected constructor() : DialogFragment(), GlanceExtension {
    private val listeners = CopyOnWriteArrayList<TransitionListener>()

    private val layoutChangeListener =
        View.OnLayoutChangeListener { _, left, top, right, bottom,
                                      oldLeft, oldTop, oldRight, oldBottom ->
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                recalculatePosition()
            }
        }

    // Public field rather than a property: Java subclasses read it directly and a
    // Kotlin property would generate a getActivity() clashing with Fragment's.
    @JvmField
    protected var activity: ThemedActivity? = null

    private var persistentDialog: PersistentFullscreenDialog? = null
    private var container: GlanceConstraintLayout? = null
    private var targetTranslationY = 0f
    private var homeWatcher: HomeWatcher? = null
    private var isHiding = false
    private var glance: Glance? = null

    override fun getDialog(): PersistentFullscreenDialog? {
        return persistentDialog
    }

    open fun attach(glance: Glance) {
        this.glance = glance

        show(glance.getActivity().supportFragmentManager, tag)
        glance.attachViewExtension(getViewExtensionPreview(), View.OnClickListener { show() })
    }

    override fun onAttach(context: Context) {
        val themedActivity = context as? ThemedActivity
            ?: throw RuntimeException("Parent activity is not of type ThemedActivity.")

        activity = themedActivity

        val homeWatcher = HomeWatcher(themedActivity)
        homeWatcher.setOnHomePressedListener { hide() }
        homeWatcher.startWatch()
        this.homeWatcher = homeWatcher

        // Cast back to Context on purpose: after the check above context is smart cast
        // to ThemedActivity, which would pick the deprecated onAttach(Activity) overload
        // instead of the onAttach(Context) the Java version called.
        @Suppress("USELESS_CAST")
        super.onAttach(context as Context)
    }

    override fun onDetach() {
        homeWatcher!!.stopWatch()

        super.onDetach()
    }

    override fun dismiss() {
        hide()
    }

    override fun onStop() {
        hide(false)

        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            dismissAllowingStateLoss()
        }

        super.onCreate(null)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = PersistentFullscreenDialog(activity!!, theme, true)
        dialog.setOnBackPressed {
            hide()

            false
        }

        persistentDialog = dialog

        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = ConstraintLayout(activity!!)
        root.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val expanded = inflateExpanded(inflater, root)
        this.container = expanded
        expanded.addOnLayoutChangeListener(layoutChangeListener)
        root.addView(expanded)

        updateScalingInternal(0f)
        root.setOnClickListener { hide() }

        return root
    }

    override fun onDestroyView() {
        container?.removeOnLayoutChangeListener(layoutChangeListener)

        super.onDestroyView()
    }

    private fun recalculatePosition() {
        val anchor = glance!!.getRootView()
        val dialog = persistentDialog

        if (anchor != null && dialog != null && dialog.isShowing) {
            val location = IntArray(2)
            anchor.getLocationInWindow(location)
            updateLayout(location, anchor.width, anchor.height)

            val container = this.container!!

            if (!isHiding && container.scaleY == 1f) {
                container.translationY = targetTranslationY
            }
        }
    }

    private fun updateLayout(location: IntArray, width: Int, height: Int) {
        val container = this.container!!
        val params = container.layoutParams as ConstraintLayout.LayoutParams
        val window = persistentDialog!!.window ?: return

        val decorView = window.decorView
        val screenHeight = decorView.measuredHeight
        val expandedHeight = container.measuredHeight

        val sysUIHeight = Measurements.getSysUIHeight()
        val navHeight = Measurements.getNavHeight()
        val safeAreaBottom = screenHeight - navHeight

        container.setMaxRadius(height / 2f)
        params.width = width
        targetTranslationY = 0f

        var gravity = Gravity.BOTTOM
        if (expandedHeight > (location[1] - sysUIHeight)) {
            gravity = Gravity.TOP
        }

        if (gravity == Gravity.BOTTOM) {
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            params.topToTop = ConstraintLayout.LayoutParams.UNSET
            params.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID

            params.bottomMargin = screenHeight - location[1] - height
            params.leftMargin = location[0]
            container.pivotY = expandedHeight.toFloat()

            val topEdge = location[1] + height - expandedHeight
            if (topEdge < sysUIHeight) {
                targetTranslationY = (sysUIHeight - topEdge).toFloat()
            }
        } else {
            params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID

            params.topMargin = location[1]
            params.leftMargin = location[0]
            container.pivotY = 0f

            val bottomEdge = location[1] + expandedHeight
            if (bottomEdge > safeAreaBottom) {
                targetTranslationY = -(bottomEdge - safeAreaBottom).toFloat()
            }
        }

        container.layoutParams = params
    }

    protected open fun show() {
        val dialog = persistentDialog

        if (dialog != null && isEnabled() &&
            !dialog.isShowing && dialog.showDialog()
        ) {
            Vibrations.getInstance().vibrate()
            isHiding = false

            container!!.post(ExpandRunnable())

            activity!!.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        }
    }

    protected open fun hide(animate: Boolean) {
        val dialog = persistentDialog

        if (dialog != null && dialog.isShowing) {
            if (animate) {
                hide()
            } else {
                collapseAndHideDialog()
            }

            activity!!.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    protected open fun hide() {
        hideInternal(false)
    }

    protected open fun urgentHide() {
        hideInternal(true)
    }

    protected open fun isShowing(): Boolean {
        val dialog = persistentDialog

        return dialog != null && dialog.isShowing
    }

    private fun collapseAndHideDialog() {
        updateScalingInternal(0f)

        container!!.post { persistentDialog!!.hide() }
    }

    private fun hideInternal(force: Boolean) {
        if (!isShowing()) {
            return
        }

        isHiding = true

        val container = this.container!!

        if (container.scaleY == 1f || force) {
            val targetScale = glance!!.getHeight() / container.measuredHeight

            container.animate()
                .scaleY(targetScale)
                .translationY(0f)
                .setInterpolator(PathInterpolator(X1, Y1, X2, Y2))
                .setDuration(Animation.MEDIUM.duration.toLong())
                .setUpdateListener { animation ->
                    val fraction = 1f - (animation.animatedValue as Float)

                    updateScalingInternal(fraction)
                }.setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: Animator) {
                        collapseAndHideDialog()
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        collapseAndHideDialog()
                    }
                })

            activity!!.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun updateScalingInternal(@FloatRange(from = 0.0, to = 1.0) fraction: Float) {
        val container = this.container!!
        val scale = 1f / container.scaleY

        if (!scale.isNaN() && scale != Float.POSITIVE_INFINITY) {
            updateScaling(fraction, scale)
        }

        container.setRadiusPercentage(1f - fraction)
        persistentDialog!!.setDimmingFactor(fraction)

        for (listener in listeners) {
            listener.onProgressFraction(fraction)
        }

        if (fraction == 0f) {
            container.visibility = View.INVISIBLE
        } else {
            container.visibility = View.VISIBLE
        }

        container.invalidate()
    }

    // Public rather than package-private: Glance is Kotlin and calls this from the
    // same package, and Kotlin has no package visibility.
    @Suppress("DEPRECATION")
    fun updateSheetSystemUI(lightMode: Boolean) {
        val dialog = persistentDialog ?: return
        val window = dialog.window

        if (window != null) {
            val decor = window.decorView

            if (lightMode) {
                decor.systemUiVisibility = decor.systemUiVisibility and
                        (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR).inv()
            } else {
                decor.systemUiVisibility = decor.systemUiVisibility or
                        (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
            }
        }
    }

    fun addTransitionListener(listener: TransitionListener?) {
        if (listener != null) {
            listeners.add(listener)
        }
    }

    fun removeTransitionListener(listener: TransitionListener?) {
        if (listener == null) {
            return
        }

        listeners.remove(listener)
    }

    private inner class ExpandRunnable : Runnable {
        override fun run() {
            if (isHiding || !isAdded) {
                return
            }

            recalculatePosition()

            val container = this@GlanceDialogExtension.container!!
            val scale = glance!!.getHeight() / container.measuredHeight

            if (scale >= 0 && !scale.isInfinite()) {
                container.scaleY = scale
                container.translationY = 0f

                container.post {
                    container.animate()
                        .scaleY(1f)
                        .translationY(targetTranslationY)
                        .setInterpolator(PathInterpolator(X1, Y1, X2, Y2))
                        .setDuration(Animation.LONG.duration.toLong())
                        .setUpdateListener { animation ->
                            val fraction = animation.animatedFraction

                            updateScalingInternal(fraction)
                        }
                        .setListener(object : AnimatorListenerAdapter() {
                            private var canceled = false

                            override fun onAnimationCancel(animation: Animator) {
                                canceled = true
                            }

                            override fun onAnimationEnd(animation: Animator) {
                                if (!canceled) {
                                    updateScalingInternal(1f)
                                }
                            }
                        })
                }
            } else {
                container.post(this)
            }
        }
    }

    fun interface TransitionListener {
        fun onProgressFraction(fraction: Float)
    }

    abstract fun getTAG(): String

    protected abstract fun inflateExpanded(
        inflater: LayoutInflater,
        container: ConstraintLayout
    ): GlanceConstraintLayout

    protected abstract fun getViewExtensionPreview(): GlanceViewExtension

    protected abstract fun isEnabled(): Boolean

    protected abstract fun updateScaling(
        @FloatRange(from = 0.0, to = 1.0) fraction: Float,
        scale: Float
    )

    private companion object {
        private const val X1 = 0.2f
        private const val Y1 = 1f
        private const val X2 = 0.4f
        private const val Y2 = 1f
    }
}
