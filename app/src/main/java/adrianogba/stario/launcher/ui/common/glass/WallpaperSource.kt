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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * The wallpaper bitmap, which is what liquid glass surfaces would have to
 * sample to sit over the home screen.
 *
 * On API 37 this does not work, and no permission fixes it. Both entry points
 * were tried, getWallpaperFile and getDrawable, each in four configurations:
 * with no permission, with READ_MEDIA_IMAGES granted, as the default home app,
 * and as the default home app with the permission. Every one throws
 * SecurityException asking for READ_EXTERNAL_STORAGE, which apps targeting 33
 * and above cannot hold. dumpsys confirmed the wallpaper was a static image
 * rather than a live one, so this is not the live wallpaper exception.
 * Android restricted wallpaper reads on purpose, since a wallpaper is often a
 * personal photo.
 *
 * This is the one and only thing standing between the launcher and full
 * refraction on the home screen. Refraction needs source pixels, and the
 * library supplies them from either a canvas the app draws or a layer it
 * captures from its own composables. Both work, and both are used here. What
 * cannot be had is somebody else's pixels, and the wallpaper is somebody
 * else's. [WallpaperPalette] takes the colours instead, which need no
 * permission at all.
 *
 * Kept because it fails cleanly and documents the finding. Every caller treats
 * null as "no glass here" and falls back to a flat surface, so glass over the
 * wallpaper is simply unavailable rather than broken. Surfaces drawn over the
 * launcher's own content are a different matter: those can be captured
 * directly and do not need this.
 */
object WallpaperSource {
    private const val TAG = "WallpaperSource"

    private var cached: ImageBitmap? = null

    /**
     * Whether a wallpaper backdrop can be had at all. Currently it cannot, on
     * any tested configuration. See the note on this object.
     */
    fun isAvailable(context: Context): Boolean = get(context) != null

    /**
     * @return the wallpaper, or null if the permission is missing or the system
     * refuses to hand it over. Callers fall back to a flat surface.
     */
    fun get(context: Context): ImageBitmap? {
        cached?.let { return it }

        val bitmap = read(context) ?: return null

        return bitmap.asImageBitmap().also { cached = it }
    }

    private fun read(context: Context): Bitmap? {
        val manager = WallpaperManager.getInstance(context)

        return readFile(manager) ?: readDrawable(manager)
    }

    /**
     * The file descriptor path, which is a different code path in
     * WallpaperManagerService than getDrawable and worth trying first.
     */
    private fun readFile(manager: WallpaperManager): Bitmap? {
        return try {
            manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { descriptor ->
                BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor)
            }
        } catch (exception: Exception) {
            Log.e(TAG, "getWallpaperFile refused: " + exception.message)

            null
        }
    }

    private fun readDrawable(manager: WallpaperManager): Bitmap? {
        return try {
            val drawable = manager.drawable ?: return null

            // The common case is already a bitmap, so avoid the redraw
            (drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmap()
        } catch (exception: SecurityException) {
            Log.e(TAG, "getDrawable refused: " + exception.message)

            null
        } catch (exception: Exception) {
            Log.e(TAG, "Could not read the wallpaper", exception)

            null
        }
    }

    fun invalidate() {
        cached = null
    }
}
