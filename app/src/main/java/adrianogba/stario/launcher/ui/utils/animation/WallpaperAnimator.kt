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

package adrianogba.stario.launcher.ui.utils.animation

import android.app.Activity
import android.os.Handler
import android.os.IBinder
import android.util.Log
import adrianogba.stario.launcher.hidden.WallpaperManagerHidden
import dev.rikka.tools.refine.Refine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

object WallpaperAnimator {
    private const val TAG = "WallpaperAnimation"
    private const val ANIMATION_FRAME_STEP = 0.005f
    private const val TARGET_FRAME_COUNT = 30
    private const val ANIMATION_FRAME_DELAY = 8L

    @Suppress("DEPRECATION")
    private val handler = Handler()

    private var wallpaperManager: WallpaperManagerHidden? = null
    private var hasLoggedMissingMethod = false
    private var lastRecordedZoomValue = 0f
    private var zoomAnimator: Runnable? = null

    @JvmStatic
    fun updateZoom(activity: Activity, zoom: Float) {
        if (hasLoggedMissingMethod || zoom == lastRecordedZoomValue) {
            return
        }

        zoomAnimator?.let { handler.removeCallbacks(it) }

        val direction = sign(zoom - lastRecordedZoomValue)
        val token = getWindowToken(activity)

        val animator = object : Runnable {
            override fun run() {
                if (token == null) {
                    return
                }

                lastRecordedZoomValue += direction * max(
                    ANIMATION_FRAME_STEP,
                    abs(zoom - lastRecordedZoomValue) / TARGET_FRAME_COUNT
                )

                if ((direction > 0 && lastRecordedZoomValue > zoom) ||
                    (direction < 0 && lastRecordedZoomValue < zoom)
                ) {
                    lastRecordedZoomValue = zoom
                }

                try {
                    getWallpaperManager(activity)
                        .setWallpaperZoomOut(token, lastRecordedZoomValue)
                } catch (exception: NoSuchMethodError) {
                    if (!hasLoggedMissingMethod) {
                        Log.e(
                            TAG,
                            "WallpaperManager::setWallpaperZoomOut does not exist. " +
                                    "This error message will not be shown again."
                        )
                        hasLoggedMissingMethod = true
                    }

                    return
                }

                if (lastRecordedZoomValue != zoom) {
                    handler.postDelayed(this, ANIMATION_FRAME_DELAY)
                }
            }
        }

        zoomAnimator = animator
        animator.run()
    }

    private fun getWallpaperManager(activity: Activity): WallpaperManagerHidden {
        wallpaperManager?.let { return it }

        val manager: WallpaperManagerHidden =
            Refine.unsafeCast(WallpaperManagerHidden.getInstance(activity))
        wallpaperManager = manager

        return manager
    }

    private fun getWindowToken(activity: Activity): IBinder? {
        return activity.window?.decorView?.windowToken
    }
}
