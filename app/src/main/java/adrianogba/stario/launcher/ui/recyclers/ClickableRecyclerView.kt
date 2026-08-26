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

package adrianogba.stario.launcher.ui.recyclers

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

open class ClickableRecyclerView : RecyclerView {

    private val moveSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var valid = false
    private var x = 0
    private var y = 0

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val result = super.onTouchEvent(e)

        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                x = e.rawX.toInt()
                y = e.rawY.toInt()
                valid = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (abs(e.rawX - x) > moveSlop || abs(e.rawY - y) > moveSlop) {
                    valid = false
                }
            }

            MotionEvent.ACTION_UP -> {
                if (valid && abs(e.rawX - x) < moveSlop && abs(e.rawY - y) < moveSlop) {
                    performClick()
                }
            }
        }

        return result
    }
}
