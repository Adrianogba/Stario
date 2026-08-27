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

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import android.util.Log

/**
 * The wallpaper's colours, which unlike its pixels are readable without any
 * permission at all.
 *
 * `getWallpaperColors` is what AOSP's own launcher tints from. It returns up to
 * three colours the system extracted when the wallpaper was set, so a glass
 * surface can take on the wall behind it even though it cannot refract it.
 */
object WallpaperPalette {
    private const val TAG = "WallpaperPalette"

    /**
     * @return the wallpaper's primary colour, or [fallback] when the system has
     * no colours for it, which happens for some live wallpapers.
     */
    fun primary(context: Context, fallback: Int): Int {
        return try {
            val colors = WallpaperManager.getInstance(context)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return fallback

            colors.primaryColor.toArgb()
        } catch (exception: Exception) {
            Log.e(TAG, "Could not read the wallpaper colours", exception)

            fallback
        }
    }

    /**
     * Whether the wallpaper is dark enough that glass over it should be a light
     * tint rather than a dark one.
     */
    fun isDark(context: Context): Boolean {
        val primary = primary(context, Color.BLACK)

        return Color.luminance(primary) < 0.5f
    }
}
