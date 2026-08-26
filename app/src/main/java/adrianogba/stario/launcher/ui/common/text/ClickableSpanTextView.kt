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

package adrianogba.stario.launcher.ui.common.text

import android.annotation.SuppressLint
import android.content.Context
import android.text.Spanned
import android.text.style.ClickableSpan
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.abs

// Modification of https://stackoverflow.com/a/52373765
class ClickableSpanTextView : AppCompatTextView, View.OnTouchListener {
    private var spanClickListener: OnSpanClickListener? = null
    private var longPressTriggered = false
    private var pressedSpan: ClickableSpan? = null
    private var downX = 0f
    private var downY = 0f

    private val longPressRunnable = Runnable { longPressTriggered = true }
    private val moveSlop: Int = ViewConfiguration.get(context).scaledTouchSlop

    @SuppressLint("ClickableViewAccessibility")
    constructor(context: Context) : super(context) {
        super.setOnTouchListener(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        super.setOnTouchListener(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr) {
        super.setOnTouchListener(this)
    }

    override fun setOnTouchListener(l: OnTouchListener?) {
        throw RuntimeException("ClickableSpanTextView cannot set a touch listener.")
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val text = this.text

        if (text !is Spanned) {
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pressedSpan = findSpan(event, text)

                if (pressedSpan != null) {
                    downX = event.x
                    downY = event.y
                    longPressTriggered = false

                    postDelayed(
                        longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong()
                    )

                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (pressedSpan == null) {
                    return false
                }

                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)

                if (dx > moveSlop || dy > moveSlop) {
                    cancelPressedSpan()
                }
            }

            MotionEvent.ACTION_CANCEL -> cancelPressedSpan()

            MotionEvent.ACTION_UP -> {
                val pressedSpan = this.pressedSpan
                val spanClickListener = this.spanClickListener

                if (pressedSpan != null && !longPressTriggered && spanClickListener != null) {
                    spanClickListener.onSpanClick(this, pressedSpan)
                    cancelPressedSpan()

                    return true
                }

                cancelPressedSpan()
            }
        }

        return false
    }

    private fun cancelPressedSpan() {
        removeCallbacks(longPressRunnable)

        pressedSpan = null
        longPressTriggered = false
    }

    private fun findSpan(event: MotionEvent, spannable: Spanned): ClickableSpan? {
        var x = event.x.toInt()
        var y = event.y.toInt()

        x -= totalPaddingLeft
        y -= totalPaddingTop

        x += scrollX
        y += scrollY

        val layout = this.layout ?: return null

        val line = layout.getLineForVertical(y)
        val off = layout.getOffsetForHorizontal(line, x.toFloat())

        val spans = spannable.getSpans(off, off, ClickableSpan::class.java)

        return if (spans.isNotEmpty()) spans[0] else null
    }

    fun setOnSpanClickListener(listener: OnSpanClickListener?) {
        this.spanClickListener = listener
    }

    fun interface OnSpanClickListener {
        fun onSpanClick(view: ClickableSpanTextView, span: ClickableSpan)
    }
}
