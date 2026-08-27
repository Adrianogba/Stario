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

package adrianogba.stario.launcher.activities.settings.dialogs.theme

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.materialswitch.MaterialSwitch
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.themes.SurfaceStyle
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.dialogs.ActionDialog
import adrianogba.stario.launcher.ui.common.glass.GlassPreviewView
import adrianogba.stario.launcher.ui.common.glass.MaterialPreviewView

class ThemeDialog(activity: ThemedActivity) : ActionDialog(activity) {
    private var listener: OnDismissListener? = null
    private var recreateActivity = false

    @SuppressLint("ClickableViewAccessibility")
    override fun inflateContent(inflater: LayoutInflater): View {
        val themePreferences = activity.applicationContext.getSharedPreferences(Entry.THEME)
        val root = inflater.inflate(R.layout.pop_up_theme, null)

        val isForceDarkOn = themePreferences.getBoolean(ThemedActivity.FORCE_DARK, false)

        val materialSwitch = root.findViewById<MaterialSwitch>(R.id.force_dark)
        materialSwitch.isChecked = isForceDarkOn
        materialSwitch.jumpDrawablesToCurrentState()
        materialSwitch.setOnCheckedChangeListener { _, isChecked ->
            themePreferences.edit()
                .putBoolean(ThemedActivity.FORCE_DARK, isChecked)
                .apply()

            // The activity re-reads the theme in onCreate, so dark mode cannot
            // take effect while this dialog is up. Closing here means the flip
            // is applied straight away instead of waiting for a manual dismiss.
            dismiss()
        }

        root.findViewById<View>(R.id.force_dark_container)
            .setOnClickListener { materialSwitch.performClick() }

        val initialStyle = setupSurfaceStyle(root, themePreferences)

        val recycler = root.findViewById<RecyclerView>(R.id.recycler)
        // Wraps onto as many centred lines as it needs. A single sideways
        // scrolling row hid most of the twelve themes behind a swipe.
        recycler.layoutManager = FlexboxLayoutManager(activity).apply {
            flexWrap = FlexWrap.WRAP
            justifyContent = JustifyContent.CENTER
        }
        recycler.adapter = ThemeRecyclerAdapter(activity) {
            recreateActivity = true
            dismiss()
        }

        super.setOnDismissListener(DialogInterface.OnDismissListener {
            val style = SurfaceStyle.from(
                themePreferences.getString(ThemedActivity.SURFACE_STYLE, null)
            )

            listener?.onDismiss(
                recreateActivity ||
                        isForceDarkOn != materialSwitch.isChecked ||
                        initialStyle != style
            )
        })

        return root
    }

    /**
     * The two chips are drawn in the style they select, so the control shows
     * what it is offering rather than describing it. The glass one renders
     * real refraction over the current theme's own colours, which is also the
     * first place the glass code has to work.
     */
    private fun setupSurfaceStyle(
        root: View, preferences: android.content.SharedPreferences
    ): SurfaceStyle {
        val initial = SurfaceStyle.from(
            preferences.getString(ThemedActivity.SURFACE_STYLE, null)
        )

        val materialCheck = root.findViewById<ImageView>(R.id.style_material_check)
        val glassCheck = root.findViewById<ImageView>(R.id.style_glass_check)

        val surface = activity.getAttributeData(
            com.google.android.material.R.attr.colorSurface
        )
        val primary = activity.getAttributeData(
            com.google.android.material.R.attr.colorPrimaryContainer
        )
        val secondary = activity.getAttributeData(
            com.google.android.material.R.attr.colorSecondaryContainer
        )
        val tertiary = activity.getAttributeData(
            com.google.android.material.R.attr.colorTertiaryContainer
        )

        val glass = root.findViewById<GlassPreviewView>(R.id.style_glass_preview)
        glass.setSurfaceColor(surface)
        glass.setBackdropColors(primary, secondary, tertiary)

        val material = root.findViewById<MaterialPreviewView>(R.id.style_material_preview)
        material.setSurfaceColor(surface)
        material.setBackdropColors(primary, secondary, tertiary)

        fun show(style: SurfaceStyle) {
            materialCheck.visibility =
                if (style == SurfaceStyle.MATERIAL) View.VISIBLE else View.INVISIBLE
            glassCheck.visibility =
                if (style == SurfaceStyle.LIQUID_GLASS) View.VISIBLE else View.INVISIBLE
        }

        fun pick(style: SurfaceStyle) {
            preferences.edit()
                .putString(ThemedActivity.SURFACE_STYLE, style.name)
                .apply()

            show(style)
        }

        show(initial)

        root.findViewById<View>(R.id.style_material)
            .setOnClickListener { pick(SurfaceStyle.MATERIAL) }
        root.findViewById<View>(R.id.style_glass)
            .setOnClickListener { pick(SurfaceStyle.LIQUID_GLASS) }

        return initial
    }

    override fun blurBehind(): Boolean = true

    override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_EXPANDED

    override fun setOnDismissListener(listener: DialogInterface.OnDismissListener?) {
        throw RuntimeException("Operation not supported by " + javaClass.name)
    }

    fun setOnDismissListener(listener: OnDismissListener?) {
        this.listener = listener
    }

    fun interface OnDismissListener {
        fun onDismiss(stateChanged: Boolean)
    }
}
