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

package adrianogba.stario.launcher.ui.common.glass

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.view.animation.Interpolator
import androidx.core.graphics.ColorUtils
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import adrianogba.stario.launcher.Stario
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.themes.SurfaceStyle
import adrianogba.stario.launcher.themes.ThemedActivity

/**
 * The one place the home screen widgets ask whether they should be glass.
 *
 * Each widget owns its own layout and its own idea of what a background is, so
 * what they share is only the question, not the answer.
 */
object Glass {

    @JvmStatic
    fun isEnabled(activity: ThemedActivity): Boolean {
        val style = SurfaceStyle.from(
            activity.applicationContext.getSharedPreferences(Entry.THEME)
                .getString(ThemedActivity.SURFACE_STYLE, null)
        )

        return style == SurfaceStyle.LIQUID_GLASS
    }

    /**
     * Whether the Liquid Glass style is on, for a plain context rather than an
     * activity. Views and drawables get one of these where they do not get the
     * other.
     */
    @JvmStatic
    fun isEnabled(context: Context): Boolean {
        val style = SurfaceStyle.from(
            (context.applicationContext as Stario)
                .getSharedPreferences(Entry.THEME)
                .getString(ThemedActivity.SURFACE_STYLE, null)
        )

        return style == SurfaceStyle.LIQUID_GLASS
    }

    /**
     * Dresses a floating surface in glass, and leaves it alone under Material.
     *
     * Apple's guidance puts glass on the layer that floats above content and
     * never on the content itself, so this is for dialogs, menus and bars. The
     * rows and cards inside them keep their own backgrounds, which is also what
     * Apple's own Settings does.
     *
     * @param cornerRadiusDp match it to the surface being replaced, or the rim
     * will not follow the edge
     * @param tint the colour the pane takes, usually the surface colour it is
     * standing in for
     */
    @JvmStatic
    @JvmOverloads
    fun applyTo(
        view: View,
        cornerRadiusDp: Float,
        tint: Int,
        topCornersOnly: Boolean = false,
        alpha: Float = -1f
    ) {
        if (!isEnabled(view.context)) {
            return
        }

        val density = view.resources.displayMetrics.density
        val radius = cornerRadiusDp * density

        // Bottom sheets sit flush with the bottom of the screen, so only their
        // top corners are ever rounded.
        val bottom = if (topCornersOnly) 0f else radius
        val radii = floatArrayOf(
            radius, radius, radius, radius, bottom, bottom, bottom, bottom
        )

        view.background = if (alpha < 0f) {
            GlassDrawable(tint, radii, RIM_WIDTH_DP * density)
        } else {
            GlassDrawable(tint, radii, RIM_WIDTH_DP * density, alpha)
        }
    }

    /**
     * Restyles every switch and slider under [root] as its Liquid Glass
     *
     * equivalent.
     *
     * Walking the tree rather than asking each screen to opt in means one call
     * per host reaches all of them, and screens added later get it for free. In
     * both cases the original widget stays in the tree and stays the thing that
     * decides, so the listener each screen attached keeps working untouched.
     */
    @JvmStatic
    fun applyToControlsIn(root: View) {
        if (!isEnabled(root.context)) {
            return
        }

        when (root) {
            is MaterialSwitch -> applyToSwitch(root)

            is Slider -> applyToSlider(root)

            is ViewGroup ->
                for (index in 0 until root.childCount) {
                    applyToControlsIn(root.getChildAt(index))
                }
        }
    }

    /**
     * Unlike the switches, a slider here is only the control: its label is a
     * separate view. So the Material one is hidden outright rather than kept
     * drawing, and the glass one laid over the space it holds.
     *
     * Slider keeps a list of change listeners rather than a single slot, so
     * both directions are ordinary listeners and none of the switch's
     * indirection is needed.
     */
    private fun applyToSlider(slider: Slider) {
        val parent = slider.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(slider)
        val context = slider.context

        val frame = FrameLayout(context)
        frame.id = slider.id
        frame.layoutParams = slider.layoutParams

        parent.removeViewAt(index)

        slider.id = View.NO_ID
        // A view at zero alpha is reported as not visible to the user, which
        // takes it out of the accessibility tree, so this one cannot be the
        // node for the control. It is hidden from the tree explicitly and the
        // glass slider carries the semantics itself.
        slider.alpha = 0f
        slider.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        slider.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL
        )

        val span = (slider.valueTo - slider.valueFrom).takeIf { it > 0f } ?: 1f

        val glass = LiquidSliderView(context)

        glass.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL
        )
        // Blended towards the foreground rather than taken straight from a
        // surface role, so the unfilled part of the track stays visible against
        // the row it sits on in both light and dark.
        glass.setColors(
            ColorUtils.blendARGB(
                resolve(context, com.google.android.material.R.attr.colorSurfaceContainerHighest),
                resolve(context, com.google.android.material.R.attr.colorOnSurface),
                TRACK_CONTRAST
            ),
            resolve(context, com.google.android.material.R.attr.colorPrimaryContainer)
        )
        glass.setValueSilently((slider.value - slider.valueFrom) / span)

        glass.listener = LiquidSliderView.OnValueChanged { fraction ->
            val next = slider.valueFrom + fraction * span

            // Still the thing every screen reads and listens to.
            val clamped = next.coerceIn(slider.valueFrom, slider.valueTo)

            if (slider.value != clamped) {
                slider.value = clamped
            }
        }

        slider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) {
                glass.setValueSilently((value - slider.valueFrom) / span)
            }
        }

        frame.addView(slider)
        frame.addView(glass)

        parent.addView(frame, index)
    }

    private fun applyToSwitch(switchView: MaterialSwitch) {
        val parent = switchView.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(switchView)
        val context = switchView.context

        // The switch is the whole row here, not just the control on the end of
        // it: the label is its android:text and the icon is its drawableStart.
        // So it stays visible and keeps drawing both, and only the two
        // drawables that make up the switch graphic are blanked out. The glass
        // pane is then laid over the space they were occupying.
        val trackSize = switchView.trackDrawable
        val blankWidth = trackSize?.intrinsicWidth ?: 0
        val blankHeight = trackSize?.intrinsicHeight ?: 0

        val frame = FrameLayout(context)
        frame.id = switchView.id
        frame.layoutParams = switchView.layoutParams

        parent.removeViewAt(index)

        switchView.id = View.NO_ID
        switchView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )

        // Same footprint as the drawables they replace, so the label and icon
        // do not shift by a pixel when the style changes.
        switchView.trackTintList = null
        switchView.thumbTintList = null
        switchView.trackDecorationDrawable = null
        switchView.trackDrawable = CheckedStateBridge(blankWidth, blankHeight) {}

        val toggle = LiquidToggleView(context)

        // The switch underneath is the accessibility node: it has the label,
        // the role, the state and the actions, all of it already correct. The
        // pane is decoration over it, so it is taken out of the tree rather
        // than announcing the same control a second time.
        toggle.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        toggle.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.END or Gravity.CENTER_VERTICAL
        ).apply { marginEnd = switchView.paddingEnd }

        toggle.setColors(
            resolve(context, com.google.android.material.R.attr.colorSurfaceContainerHighest),
            resolve(context, com.google.android.material.R.attr.colorPrimaryContainer)
        )
        toggle.setCheckedSilently(switchView.isChecked)

        // The screen already attached its listener to the switch, so the switch
        // stays the thing that decides. The pane only reports taps into it.
        toggle.listener = LiquidToggleView.OnCheckedChange { checked ->
            if (switchView.isChecked != checked) {
                switchView.isChecked = checked
            }
        }

        // The other direction, for rows that call performClick on the switch.
        // A drawable hears about a check through refreshDrawableState, so this
        // needs none of the single listener slot the screen is already using.
        switchView.thumbDrawable = CheckedStateBridge(0, 0) { checked ->
            toggle.setCheckedSilently(checked)
        }

        frame.addView(switchView)
        frame.addView(toggle)

        parent.addView(frame, index)
    }

    /**
     * A drawable that paints nothing. It reserves the footprint of whatever it
     * replaces, and reports the check changes of the switch it is attached to.
     */
    private class CheckedStateBridge(
        private val width: Int,
        private val height: Int,
        private val onChanged: (Boolean) -> Unit
    ) : android.graphics.drawable.Drawable() {

        private var checked = false

        override fun isStateful(): Boolean = true

        override fun onStateChange(state: IntArray): Boolean {
            val next = state.any { it == android.R.attr.state_checked }

            if (next != checked) {
                checked = next
                onChanged(next)
            }

            return false
        }

        override fun draw(canvas: android.graphics.Canvas) {
        }

        override fun setAlpha(alpha: Int) {
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSPARENT

        override fun getIntrinsicWidth(): Int = width

        override fun getIntrinsicHeight(): Int = height
    }

    private fun resolve(context: Context, attribute: Int): Int {
        val value = android.util.TypedValue()
        context.theme.resolveAttribute(attribute, value, true)

        return value.data
    }

    /**
     * The interpolator a glass surface should move on.
     *
     * Apple's glass overshoots slightly and settles, rather than easing to a
     * stop. This is that shape as an interpolator, so existing animate() calls
     * can take it without being rewritten as springs.
     */
    @JvmStatic
    fun interpolator(context: Context, material: Interpolator): Interpolator =
        if (isEnabled(context)) OvershootSettleInterpolator() else material

    /**
     * Softens a colour towards transparency, for text and icons that now sit on
     * glass rather than on an opaque surface.
     */
    @JvmStatic
    fun translucent(color: Int, alpha: Float): Int =
        ColorUtils.setAlphaComponent(color, (alpha * 255).toInt().coerceIn(0, 255))

    /**
     * The tint a surface floating on the wallpaper should take.
     *
     * Pulled from the wallpaper's own extracted colours rather than the theme,
     * because the pane sits directly on the wall and has to belong to it. The
     * theme colour is the fallback for wallpapers the system found no colours
     * for.
     */
    @JvmStatic
    fun wallpaperTint(activity: ThemedActivity): Int {
        return WallpaperPalette.primary(
            activity,
            activity.getAttributeData(
                com.google.android.material.R.attr.colorSurfaceContainer
            )
        )
    }

    /**
     * Overshoots by a little and settles back, which is how Apple's glass
     * arrives. A plain decelerate stops dead and reads as a panel rather than
     * something with mass.
     */
    private class OvershootSettleInterpolator : Interpolator {
        override fun getInterpolation(input: Float): Float {
            val t = input - 1f

            return t * t * ((OVERSHOOT + 1f) * t + OVERSHOOT) + 1f
        }
    }

    private const val RIM_WIDTH_DP = 1f
    private const val OVERSHOOT = 0.9f

    private const val TRACK_OFF_ALPHA = 0.55f
    private const val TRACK_CONTRAST = 0.22f
    private const val TRACK_WIDTH_DP = 52f
    private const val TRACK_HEIGHT_DP = 32f
    private const val THUMB_WIDTH_DP = 34f
    private const val THUMB_HEIGHT_DP = 30f
    private const val THUMB_GLOW_DP = 4f
}
