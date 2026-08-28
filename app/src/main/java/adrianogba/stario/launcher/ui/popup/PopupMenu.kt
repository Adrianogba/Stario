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

package adrianogba.stario.launcher.ui.popup

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.VectorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.annotation.Size
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.transition.platform.MaterialElevationScale
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.common.glass.Glass
import adrianogba.stario.launcher.ui.recyclers.DividerItemDecorator
import adrianogba.stario.launcher.ui.recyclers.overscroll.OverScrollRecyclerView
import adrianogba.stario.launcher.ui.utils.animation.Animation
import kotlin.math.max

class PopupMenu @JvmOverloads constructor(
    private val activity: ThemedActivity,
    private val dismissOnItemClick: Boolean = true
) {

    private val recyclers = HashMap<Int, Pair<RecyclerView, RecyclerAdapter>>()
    private val popupWindow: PopupWindow
    private val root: LinearLayout

    private val observer: LifecycleObserver
    private val dismissListener: PopupWindow.OnDismissListener

    private var oldOrientationFlags = 0
    private var shortcutCount = 0

    init {
        val inflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        root = inflater.inflate(R.layout.popup_window, null) as LinearLayout
        popupWindow = PopupWindow(
            root, ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, true
        )

        popupWindow.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED

        val enter = MaterialElevationScale(true)
        enter.setDuration(Animation.SHORT.duration.toLong())
        // Glass arrives with a little overshoot rather than easing to a stop.
        enter.setInterpolator(Glass.interpolator(activity))

        popupWindow.enterTransition = enter

        val exit = MaterialElevationScale(false)
        exit.setDuration(Animation.SHORT.duration.toLong())
        exit.setInterpolator(PathInterpolator(0.5f, 0f, .9f, 1.1f))

        popupWindow.exitTransition = exit

        observer = object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                popupWindow.dismiss()
            }
        }

        dismissListener = PopupWindow.OnDismissListener {
            activity.setTouchEnabled(true)
            activity.requestedOrientation = oldOrientationFlags
            activity.lifecycle.removeObserver(observer)
        }

        setOnDismissListener(dismissListener)
    }

    private fun getRecycler(identifier: Int): Pair<RecyclerView, RecyclerAdapter> {
        recyclers[identifier]?.let {
            return it
        }

        val recycler: RecyclerView = OverScrollRecyclerView(activity)
        val adapter = RecyclerAdapter(
            activity,
            if (dismissOnItemClick) View.OnClickListener { dismiss() } else null
        )

        val padding = Measurements.dpToPx(PADDING)

        recycler.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        recycler.setPadding(padding, padding, padding, padding)

        recycler.background = AppCompatResources.getDrawable(activity, R.drawable.popup_background)

        // A popup is the floating layer by definition, so it takes glass
        // whenever the style is on.
        Glass.applyTo(
            recycler, POPUP_CORNER_RADIUS_DP,
            activity.getAttributeData(
                com.google.android.material.R.attr.colorSurfaceContainer
            )
        )
        recycler.itemAnimator = null
        recycler.addItemDecoration(
            DividerItemDecorator(
                activity,
                MaterialDividerItemDecoration.VERTICAL, Measurements.dpToPx(PADDING) / 3
            )
        )
        recycler.clipToOutline = true
        recycler.clipToPadding = false

        recycler.layoutManager = LinearLayoutManager(activity)
        recycler.adapter = adapter

        val entry = Pair(recycler, adapter)
        recyclers[identifier] = entry

        return entry
    }

    fun addShortcuts(launcherApps: LauncherApps, shortcuts: List<ShortcutInfo?>) {
        if (shortcuts.isEmpty()) {
            return
        }

        val adapter = getRecycler(SHORTCUT_GROUP_ID).second

        for (shortcut in shortcuts) {
            if (shortcutCount >= MAX_SHORTCUT_COUNT) {
                break
            }

            if (shortcut == null) {
                continue
            }

            val label = shortcut.shortLabel
            var icon = launcherApps.getShortcutIconDrawable(shortcut, Measurements.getDotsPerInch())

            if (icon == null) {
                if (label == null || label.toString().isBlank()) {
                    continue
                }

                // the Java version dereferenced the result without a null check
                icon = generateCharacterDrawable(label.toString())!!
            }

            val width = max(1, icon.intrinsicWidth)
            val height = max(1, icon.intrinsicHeight)

            val paddingHorizontal = (max(width, height) - width) / 2
            val paddingVertical = (max(width, height) - height) / 2

            val bitmap = Bitmap.createBitmap(
                width + 2 * paddingHorizontal,
                height + 2 * paddingVertical, Bitmap.Config.ARGB_8888
            )

            val canvas = Canvas(bitmap)
            icon.setBounds(
                paddingHorizontal, paddingVertical,
                width + paddingHorizontal, height + paddingVertical
            )
            icon.draw(canvas)

            icon = if (icon is BitmapDrawable || icon is VectorDrawable) {
                opaqueEdgeAwareIcon(bitmap, width, height)
            } else {
                BitmapDrawable(activity.resources, bitmap)
            }

            if (label != null) {
                adapter.add(Item(label.toString(), icon) {
                    launcherApps.startShortcut(shortcut, null, null)
                })

                shortcutCount++
            }
        }
    }

    // walks inwards from the centre looking for a fully opaque edge, and pads the
    // icon onto a white plate when it does not reach the threshold.
    // the width and height mix on the vertical samples is inherited from the Java
    // version and kept as is to preserve behaviour.
    private fun opaqueEdgeAwareIcon(bitmap: Bitmap, width: Int, height: Int): Drawable {
        val threshold = bitmap.width / 10

        val centerX = bitmap.width / 2
        val centerY = bitmap.width / 2
        var target = bitmap.width / 2

        while (target > threshold &&
            Color.alpha(bitmap.getPixel(centerX, target)) == 255 &&
            Color.alpha(bitmap.getPixel(target, centerY)) == 255 &&
            Color.alpha(bitmap.getPixel(centerX, width - target)) == 255 &&
            Color.alpha(bitmap.getPixel(height - target, centerY)) == 255 &&
            Color.alpha(bitmap.getPixel(centerX / 2 + target, centerY / 2 + target)) == 255 &&
            Color.alpha(bitmap.getPixel(centerY / 2 + target, centerX / 2 + target)) == 255
        ) {
            target -= 2
        }

        if (target <= threshold) {
            return BitmapDrawable(activity.resources, bitmap)
        }

        return LayerDrawable(
            arrayOf(
                ColorDrawable(Color.WHITE),
                InsetDrawable(BitmapDrawable(activity.resources, bitmap), INSET_FRACTION)
            )
        )
    }

    private fun generateCharacterDrawable(text: String?): Drawable? {
        if (text == null || text.isBlank()) {
            return null
        }

        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)

        val paint = Paint()
        paint.style = Paint.Style.FILL
        paint.color =
            activity.getAttributeData(com.google.android.material.R.attr.colorOnPrimaryContainer)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = ResourcesCompat.getFont(activity, R.font.dm_sans_medium)
        paint.textSize = ICON_SIZE * 0.7f

        val canvas = Canvas(bitmap)
        canvas.drawColor(
            activity.getAttributeData(com.google.android.material.R.attr.colorPrimaryContainer)
        )
        canvas.drawText(
            text[0].toString(),
            bitmap.width / 2f,
            ((canvas.height / 2f) - ((paint.descent() + paint.ascent()) / 2)).toInt().toFloat(),
            paint
        )

        return BitmapDrawable(activity.resources, bitmap)
    }

    fun add(item: Item) {
        getRecycler(GENERAL_ID).second.add(item)
    }

    fun add(items: List<Item>) {
        for (item in items) {
            add(item)
        }
    }

    fun setOnDismissListener(listener: PopupWindow.OnDismissListener?) {
        popupWindow.setOnDismissListener {
            dismissListener.onDismiss()

            listener?.onDismiss()
        }
    }

    fun show(activity: Activity, parent: View, pivotAxis: Short): PopupWindow? =
        show(activity, parent, null, pivotAxis)

    fun show(
        activity: Activity, parent: View, pivotAxis: Short, interceptTouches: Boolean
    ): PopupWindow? = show(activity, parent, null, pivotAxis, interceptTouches)

    fun show(
        activity: Activity, parent: View, margins: Rect?, pivotAxis: Short
    ): PopupWindow? = show(activity, parent, margins, pivotAxis, false)

    fun show(
        activity: Activity, parent: View, margins: Rect?,
        pivotAxis: Short, interceptTouches: Boolean
    ): PopupWindow? {
        val window = activity.window ?: return null

        val location = IntArray(2)
        parent.getLocationInWindow(location)

        val width = parent.measuredWidth
        val height = parent.measuredHeight

        var gravity = Gravity.NO_GRAVITY

        if (window.decorView.width / 2 < location[0] + width / 2) {
            gravity = gravity or Gravity.RIGHT

            location[0] = window.decorView.width -
                    location[0] - (parent.width * parent.scaleX).toInt()

            if (margins != null && Measurements.isLandscape()) {
                location[0] += margins.right
            }
        } else {
            gravity = gravity or Gravity.LEFT

            if (margins != null && Measurements.isLandscape()) {
                location[0] += margins.left
            }
        }

        if (Measurements.isLandscape()) {
            location[0] += (parent.width * parent.scaleX).toInt() +
                    Measurements.dpToPx(PADDING)
        }

        if (window.decorView.height / 2 > location[1] + height / 2) {
            gravity = gravity or Gravity.TOP

            if (!Measurements.isLandscape()) {
                location[1] += (parent.height * parent.scaleY).toInt() +
                        Measurements.dpToPx(PADDING)

                if (margins != null) {
                    location[1] += margins.top
                }
            }
        } else {
            gravity = gravity or Gravity.BOTTOM

            location[1] = window.decorView.height - location[1]

            if (!Measurements.isLandscape()) {
                location[1] += Measurements.dpToPx(PADDING)

                if (margins != null) {
                    location[1] -= margins.bottom
                }
            } else {
                location[1] -= (parent.height * parent.scaleY).toInt()
            }
        }

        root.post { root.setPadding(0, 0, 0, 0) }

        return showAtLocation(parent, location, gravity, pivotAxis, interceptTouches)
    }

    @JvmOverloads
    fun showAtLocation(
        activity: Activity, parent: View, x: Float, y: Float,
        pivotAxis: Short, interceptTouches: Boolean = true
    ): PopupWindow? {
        val window = activity.window ?: return null

        val location = IntArray(2)
        parent.getLocationInWindow(location)

        val width = parent.measuredWidth
        val height = parent.measuredHeight

        var gravity = Gravity.NO_GRAVITY

        if (width / 2f < x) {
            gravity = gravity or Gravity.RIGHT

            location[0] = window.decorView.width -
                    location[0] - x.toInt() - Measurements.dpToPx(WIDTH) / 2
        } else {
            gravity = gravity or Gravity.LEFT

            location[0] = location[0] + x.toInt() -
                    Measurements.dpToPx(WIDTH) / 2
        }

        if (height / 2f > y) {
            gravity = gravity or Gravity.TOP

            location[1] += y.toInt()
        } else {
            gravity = gravity or Gravity.BOTTOM

            location[1] = window.decorView.height - location[1] - y.toInt()
        }

        return showAtLocation(parent, location, gravity, pivotAxis, interceptTouches)
    }

    private fun showAtLocation(
        parent: View, @Size(2) location: IntArray,
        gravity: Int, pivotAxis: Short, interceptTouches: Boolean
    ): PopupWindow? {
        for (entry in recyclers.values) {
            if ((gravity and Gravity.TOP) == Gravity.TOP) { // flip options when popup expands upwards
                root.addView(entry.first, 0)
            } else {
                root.addView(entry.first)
            }
        }

        if (popupWindow.isShowing) {
            return null
        }

        root.post {
            if ((pivotAxis.toInt() and PIVOT_CENTER_HORIZONTAL.toInt()) ==
                PIVOT_CENTER_HORIZONTAL.toInt()
            ) {
                root.pivotX = root.measuredWidth / 2f
            } else if ((gravity and Gravity.LEFT) == Gravity.LEFT) {
                root.pivotX = 0f
            } else if ((gravity and Gravity.RIGHT) == Gravity.RIGHT) {
                root.pivotX = root.measuredWidth.toFloat()
            }

            if ((pivotAxis.toInt() and PIVOT_CENTER_VERTICAL.toInt()) ==
                PIVOT_CENTER_VERTICAL.toInt()
            ) {
                root.pivotY = root.measuredHeight / 2f
            } else if ((gravity and Gravity.TOP) == Gravity.TOP) {
                root.pivotY = 0f
            } else if ((gravity and Gravity.BOTTOM) == Gravity.BOTTOM) {
                root.pivotY = root.measuredHeight.toFloat()
            }
        }

        val padding = Measurements.dpToPx(SCREEN_MARGIN_DP)

        popupWindow.width = Measurements.dpToPx(WIDTH) + padding * 2
        popupWindow.showAtLocation(
            parent, gravity,
            location[0] - padding, location[1] - padding
        )

        val params = root.layoutParams as? ViewGroup.MarginLayoutParams
        if (params != null) {
            params.leftMargin = padding
            params.topMargin = padding
            params.rightMargin = padding
            params.bottomMargin = padding
        }

        oldOrientationFlags = activity.requestedOrientation

        activity.setTouchEnabled(!interceptTouches)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        activity.lifecycle.addObserver(observer)

        return popupWindow
    }

    fun dismiss() {
        popupWindow.dismiss()
    }

    class Item(
        @JvmField val label: String,
        @JvmField val icon: Drawable?,
        @JvmField val listener: View.OnClickListener
    )

    companion object {
        // Matches popup_background, which glass replaces.
        private const val POPUP_CORNER_RADIUS_DP = 25f

        const val PIVOT_DEFAULT: Short = 0b00
        const val PIVOT_CENTER_VERTICAL: Short = 0b01
        const val PIVOT_CENTER_HORIZONTAL: Short = 0b10

        private const val GENERAL_ID = 1
        private const val SHORTCUT_GROUP_ID = 2
        private const val MAX_SHORTCUT_COUNT = 4
        private const val INSET_FRACTION = 0.2f
        private const val ICON_SIZE = 64
        private const val SCREEN_MARGIN_DP = 20f
        private const val PADDING = 9f
        private const val WIDTH = 220f
    }
}
