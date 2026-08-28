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

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.OverScroller
import androidx.core.view.WindowCompat
import androidx.customview.widget.ViewDragHelper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.common.glass.Glass
import adrianogba.stario.launcher.ui.keyboard.KeyboardHeightProvider
import adrianogba.stario.launcher.ui.utils.HomeWatcher
import adrianogba.stario.launcher.ui.utils.UiUtils
import adrianogba.stario.launcher.ui.utils.animation.KeyboardAnimationHelper
import kotlin.math.min

abstract class ActionDialog(
    // @JvmField so that the Java subclasses can keep reading it as a field
    @JvmField protected val activity: ThemedActivity
) : BottomSheetDialog(activity) {
    private val homeWatcher: HomeWatcher = HomeWatcher(activity)

    private var dimmingController: DialogBackgroundDimmingController.DimmingController? = null
    private var heightProvider: KeyboardHeightProvider? = null
    private var canCollapse = false
    private var root: View? = null

    init {
        homeWatcher.setOnHomePressedListener {
            val behavior = behavior
            behavior.isDraggable = false
            behavior.state = BottomSheetBehavior.STATE_HIDDEN

            UiUtils.hideKeyboard(root!!)
        }

        activity.addOnConfigurationChangedListener {
            heightProvider?.dismiss()

            dismissWithoutSheetAnimation()
        }

        val lifecycle = activity.lifecycle
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                lifecycle.removeObserver(this)
            }

            override fun onPause(owner: LifecycleOwner) {
                heightProvider?.dismiss()

                dismissWithoutSheetAnimation()
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val behavior = behavior

        behavior.skipCollapsed = true

        val inflater = activity.layoutInflater

        val root = inflater.inflate(R.layout.pop_up_root, null, false)
        this.root = root

        val content = root.findViewById<ViewGroup>(R.id.content)
        content.addView(inflateContent(activity.layoutInflater))

        // Every settings dialog in the app comes through here, so this is the
        // one place the sheet itself has to learn about glass. A bottom sheet
        // floats over the screen it was opened from, which is exactly the layer
        // Apple's guidance says glass belongs to.
        Glass.applyTo(
            content, SHEET_CORNER_RADIUS_DP,
            activity.getAttributeData(
                com.google.android.material.R.attr.colorSurfaceContainer
            ),
            topCornersOnly = true
        )

        Glass.applyToSwitchesIn(content)

        val heightProvider = KeyboardHeightProvider(activity)
        this.heightProvider = heightProvider

        KeyboardAnimationHelper.configureKeyboardAnimator(
            window!!.decorView, heightProvider
        ) { translation ->
            content.setPadding(
                content.paddingLeft, content.paddingTop,
                content.paddingRight, (Measurements.getNavHeight() - translation).toInt()
            )
        }

        heightProvider.addKeyboardHeightListener { height -> behavior.isDraggable = height == 0 }

        Measurements.addNavListener { value ->
            content.setPadding(
                content.paddingLeft, content.paddingTop, content.paddingRight, value
            )
        }

        root.setOnClickListener { dismiss() }
        content.setOnClickListener { }

        setContentView(root)

        (root.parent as View).setBackgroundColor(Color.TRANSPARENT)
        Measurements.addStatusBarListener { value ->
            val params = root.layoutParams as ViewGroup.MarginLayoutParams
            params.topMargin = value
        }

        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED ||
                    newState == BottomSheetBehavior.STATE_HALF_EXPANDED ||
                    newState == BottomSheetBehavior.STATE_DRAGGING
                ) {
                    canCollapse = true
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                var offset = slideOffset

                if (offset <= -1 && canCollapse) {
                    superDismiss()

                    this@ActionDialog.heightProvider?.dismiss()

                    canCollapse = false
                }

                offset = min(offset, 0f)

                val window = window

                if (!activity.isDestroyed && !activity.isFinishing && window != null) {
                    root.scaleX = 1 + offset * 0.08f
                    root.scaleY = 1 + offset * 0.08f

                    dimmingController?.setFactor(1 + offset)
                }
            }
        })
    }

    override fun onAttachedToWindow() {
        val window: Window? = window

        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)

            if (blurBehind() && dimmingController == null) {
                dimmingController = DialogBackgroundDimmingController.attach(
                    activity, this, blurBehind(), DIMMING_MULTIPLIER
                )
            }

            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

            window.setWindowAnimations(R.style.ActionDialogAnimations)
            window.decorView.visibility = View.INVISIBLE

            behavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        homeWatcher.startWatch()

        val root = this.root!!

        root.post {
            val frame = root.parent

            if (frame != null) {
                fitToBottomInset(frame as View, false)

                val coordinator = frame.getParent()

                if (coordinator != null) {
                    fitToBottomInset(coordinator as View, false)

                    val container = coordinator.getParent()

                    if (container != null) {
                        fitToBottomInset(container as View, false)
                    }
                }
            }

            root.post {
                if (window != null) {
                    window.decorView.visibility = View.VISIBLE

                    behavior.state = getDesiredInitialState()
                }
            }
        }
    }

    // hack to remove STATE_EXPANDED fit to system window jitter on layout pass
    override fun onDetachedFromWindow() {
        val frame = root!!.parent

        homeWatcher.stopWatch()

        if (frame != null) {
            fitToBottomInset(frame as View, true)

            val coordinator = frame.getParent()

            if (coordinator != null) {
                fitToBottomInset(coordinator as View, true)

                val container = coordinator.getParent()

                if (container != null) {
                    fitToBottomInset(container as View, true)
                }
            }
        }

        super.onDetachedFromWindow()
    }

    private fun fitToBottomInset(view: View, fit: Boolean) {
        view.fitsSystemWindows = fit
        view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, 0)
    }

    override fun show() {
        super.show()

        behavior.isDraggable = heightProvider!!.getKeyboardHeight() == 0

        val root = this.root!!
        root.post { UiUtils.Notch.applyNotchMargin(root, UiUtils.Notch.Treatment.CENTER) }

        heightProvider?.start()
    }

    override fun hide() {
        dismiss()
    }

    private fun dismissWithoutSheetAnimation() {
        dismiss()

        // skip framework animation
        try {
            val helper = getViewDragHelper(behavior)
            if (helper != null) {
                val scroller = getScroller(helper)

                scroller?.abortAnimation()
            }
        } catch (exception: Exception) {
            Log.e("ActionDialog", "onAttachedToWindow: ", exception)
        }
    }

    override fun dismiss() {
        val behavior = behavior

        if (heightProvider!!.getKeyboardHeight() > 0) {
            UiUtils.hideKeyboard(root!!)
        } else if (canCollapse || behavior.state == BottomSheetBehavior.STATE_EXPANDED ||
            behavior.state == BottomSheetBehavior.STATE_HALF_EXPANDED
        ) {
            canCollapse = true
            behavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    override fun onStop() {
        dismiss()

        super.onStop()
    }

    // super is unreachable from inside the BottomSheetCallback object
    private fun superDismiss() {
        super.dismiss()
    }

    protected abstract fun inflateContent(inflater: LayoutInflater): View

    protected abstract fun getDesiredInitialState(): Int

    protected abstract fun blurBehind(): Boolean

    private companion object {
        private const val DIMMING_MULTIPLIER = 0.7f

        // Matches the top corners of background_sheet, which glass replaces.
        private const val SHEET_CORNER_RADIUS_DP = 30f

        private fun getViewDragHelper(behavior: BottomSheetBehavior<*>): ViewDragHelper? {
            return try {
                val field = BottomSheetBehavior::class.java.getDeclaredField("viewDragHelper")
                field.isAccessible = true

                field.get(behavior) as ViewDragHelper?
            } catch (exception: Exception) {
                null
            }
        }

        private fun getScroller(viewDragHelper: ViewDragHelper): OverScroller? {
            return try {
                val field = ViewDragHelper::class.java.getDeclaredField("mScroller")
                field.isAccessible = true

                field.get(viewDragHelper) as OverScroller?
            } catch (exception: Exception) {
                null
            }
        }
    }
}
