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

package adrianogba.stario.launcher.ui.utils

import android.graphics.Rect
import android.view.View
import androidx.annotation.IntRange

object LayoutSizeObserver {
    const val WIDTH = 0b000001
    const val HEIGHT = 0b000010
    const val LEFT = 0b000100
    const val TOP = 0b001000
    const val RIGHT = 0b010000
    const val BOTTOM = 0b100000

    @JvmStatic
    @JvmOverloads
    fun attach(
        view: View,
        @IntRange(from = 1, to = 0b111111) watchFlags: Int,
        listener: OnChange,
        invalidateOnAttach: Boolean = true
    ) {
        val viewChangeListener = object : View.OnLayoutChangeListener {
            private var previous: Rect? = null

            override fun onLayoutChange(
                view: View, i: Int, i1: Int, i2: Int, i3: Int,
                i4: Int, i5: Int, i6: Int, i7: Int
            ) {
                val rect = Rect(view.left, view.top, view.right, view.bottom)

                val previous = this.previous

                if (previous == null) {
                    val all = WIDTH or HEIGHT or LEFT or TOP or RIGHT or BOTTOM
                    this.previous = rect

                    listener.onChange(view, all and watchFlags)
                    listener.onChange(view, all and watchFlags, rect)

                    return
                }

                var flags = 0

                if (previous.width() != rect.width()) {
                    flags = flags or WIDTH
                }

                if (previous.height() != rect.height()) {
                    flags = flags or HEIGHT
                }

                if (previous.left != rect.left) {
                    flags = flags or LEFT
                }

                if (previous.top != rect.top) {
                    flags = flags or TOP
                }

                if (previous.right != rect.right) {
                    flags = flags or RIGHT
                }

                if (previous.bottom != rect.bottom) {
                    flags = flags or BOTTOM
                }

                this.previous = rect

                if ((flags and watchFlags) != 0) {
                    listener.onChange(view, flags and watchFlags)
                    listener.onChange(view, flags and watchFlags, rect)
                }
            }
        }

        if (invalidateOnAttach) {
            viewChangeListener.onLayoutChange(view, 0, 0, 0, 0, 0, 0, 0, 0)
        }

        view.addOnLayoutChangeListener(viewChangeListener)
    }

    interface OnChange {
        fun onChange(view: View, watchFlags: Int) {
        }

        fun onChange(view: View, watchFlags: Int, rect: Rect) {
        }
    }
}
