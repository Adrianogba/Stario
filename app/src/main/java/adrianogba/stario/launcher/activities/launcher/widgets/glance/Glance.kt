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

package adrianogba.stario.launcher.activities.launcher.widgets.glance

import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.FragmentActivity
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.sheet.SheetsFocusController
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.common.glance.GlanceConstraintLayout
import adrianogba.stario.launcher.ui.common.glass.Glass
import adrianogba.stario.launcher.ui.common.glass.GlassSurfaceView
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.common.grid.DraggableGridItem
import adrianogba.stario.launcher.ui.common.grid.DynamicGridLayout
import adrianogba.stario.launcher.ui.utils.animation.Animation

class Glance(private val activity: ThemedActivity) {
    private val extensions = ArrayList<GlanceExtension>()

    private var extensionContainer: LinearLayout? = null
    private var root: GlanceConstraintLayout? = null

    /**
     * Swaps the card's flat background for glass when that style is selected.
     * The card is translucent, so the wallpaper the system composites behind
     * this window shows through it for real.
     */
    private fun applySurfaceStyle(root: GlanceConstraintLayout) {
        if (!Glass.isEnabled(activity)) {
            return
        }

        // The card's own fill has to go, or the glass has an opaque sheet
        // rather than the wallpaper behind it.
        root.background = null

        val glass = root.findViewById<GlassSurfaceView>(R.id.glass)
        glass.setTint(Glass.wallpaperTint(activity))
        glass.visibility = View.VISIBLE
    }

    fun attach(container: DynamicGridLayout) {
        val gridItem = DraggableGridItem(activity)
        gridItem.itemId = GLANCE_TAG

        val root = activity.layoutInflater
            .inflate(R.layout.glance, gridItem, false) as GlanceConstraintLayout
        this.root = root

        extensionContainer = root.findViewById(R.id.extensions)

        applySurfaceStyle(root)

        val transition = LayoutTransition()

        val changeIn = ObjectAnimator.ofFloat(null, "alpha", 0f, 1f)
        val changeOut = ObjectAnimator.ofFloat(null, "alpha", 1f, 0f)

        transition.setAnimator(LayoutTransition.APPEARING, changeIn)
        transition.setAnimator(LayoutTransition.DISAPPEARING, changeOut)
        transition.setAnimator(LayoutTransition.CHANGE_APPEARING, changeIn)
        transition.setAnimator(LayoutTransition.CHANGE_DISAPPEARING, changeOut)
        transition.setAnimator(LayoutTransition.CHANGING, changeIn)

        gridItem.addView(root)

        val defaultLayoutData = DynamicGridLayout.ItemLayoutData(GLANCE_TAG, 0, 0, 3, 1)
        defaultLayoutData.minColSpan = 3
        defaultLayoutData.minWidth = Measurements.dpToPx(330f)
        defaultLayoutData.maxColSpan = 4
        defaultLayoutData.maxRowSpan = 1

        container.addItem(gridItem, defaultLayoutData)
    }

    @SuppressLint("ClickableViewAccessibility")
    @JvmOverloads
    fun attachViewExtension(
        extension: GlanceViewExtension,
        additionalClickListener: View.OnClickListener? = null
    ) {
        if (root == null) {
            throw RuntimeException(
                "Glance should attach itself first before attaching extensions."
            )
        }

        val container = extensionContainer!!
        val view = extension.inflate(activity, container)!!

        container.addView(view)
        view.isHapticFeedbackEnabled = false
        view.setOnTouchListener(SheetsFocusController.createClickTouchListener { clicked ->
            extension.getClickListener()?.onClick(clicked)

            additionalClickListener?.onClick(clicked)
        })

        extensions.add(extension)
    }

    fun attachDialogExtension(
        extension: GlanceDialogExtension,
        listener: GlanceDialogExtension.TransitionListener?
    ) {
        val root = this.root
            ?: throw RuntimeException(
                "Glance should attach itself first before attaching extensions."
            )

        extension.addTransitionListener { progress ->
            // hide the blur
            root.alpha = 1f - progress

            listener?.onProgressFraction(progress)

            val container = extensionContainer!!

            if (progress == 0f) {
                container.animate()
                    .alpha(1f)
                    .setDuration(Animation.SHORT.duration.toLong())
            } else {
                container.animate().cancel()
                container.alpha = 0f
            }
        }

        extension.attach(this)
        extensions.add(extension)
    }

    // Public rather than package-private: GlanceDialogExtension is still Java
    // and reads it, and Kotlin has no package visibility.
    fun getRootView(): View? = root

    fun updateSheetSystemUI(value: Boolean) {
        for (extension in extensions) {
            if (extension is GlanceDialogExtension) {
                extension.updateSheetSystemUI(value)
            }
        }
    }

    fun getActivity(): FragmentActivity = activity

    fun post(runnable: Runnable) {
        root!!.post(runnable)
    }

    fun getHeight(): Float = root!!.height.toFloat()

    fun getWidth(): Float = root!!.width.toFloat()

    fun update() {
        for (extension in extensions) {
            extension.update()
        }
    }

    fun hasFocus(): Boolean {
        for (extension in extensions) {
            if (extension is GlanceDialogExtension) {
                val dialog = extension.getDialog()

                if (dialog != null && dialog.isShowing) {
                    return true
                }
            }
        }

        return false
    }

    private companion object {
        private const val GLANCE_TAG = "GridGlance"
    }
}
