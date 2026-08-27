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
}
