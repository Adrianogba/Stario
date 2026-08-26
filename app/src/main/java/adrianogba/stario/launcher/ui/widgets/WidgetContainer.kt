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

package adrianogba.stario.launcher.ui.widgets

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.widget.GridLayout
import android.widget.RelativeLayout
import adrianogba.stario.launcher.sheet.widgets.Widget
import adrianogba.stario.launcher.sheet.widgets.WidgetSize
import adrianogba.stario.launcher.ui.Measurements

@SuppressLint("ViewConstructor")
class WidgetContainer(
    context: Context,
    private val host: AppWidgetHostView,
    private val widget: Widget,
    cell: WidgetMap.Cell
) : RelativeLayout(context), Comparable<WidgetContainer> {

    private var origin: WidgetMap.Cell = cell // top-left

    init {
        val padding = Measurements.dpToPx(10f)
        setPadding(padding, padding, padding, padding)
        rotation = 180f

        addView(host)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (parent !is WidgetGrid) {
            throw RuntimeException("WidgetContainer views can only be children of WidgetGrid")
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val params = layoutParams as GridLayout.LayoutParams

        val cellSize = (parent as WidgetGrid).cellSize
        val size = widget.size!!

        params.rowSpec = GridLayout.spec(origin.row, size.height)
        params.columnSpec = GridLayout.spec(origin.column, size.width)
        params.width = cellSize * size.width
        params.height = cellSize * size.height

        layoutParams = params

        val hostWidth = params.width - paddingLeft - paddingRight
        val hostHeight = params.height - paddingTop - paddingBottom

        if (measuredWidth > 0 && measuredHeight > 0) {
            val density = Measurements.getDensity()

            host.updateAppWidgetSize(
                null,
                (hostWidth / density).toInt(),
                (hostHeight / density).toInt(),
                (hostWidth / density).toInt(),
                (hostHeight / density).toInt()
            )
        }
    }

    fun getSize(): WidgetSize? = widget.size

    fun getPosition(): Int = widget.position

    fun getWidget(): Widget = widget

    fun getOriginRow(): Int = origin.row

    fun getOriginColumn(): Int = origin.column

    fun updateOrigin(origin: WidgetMap.Cell?) {
        if (origin != null && origin != this.origin) {
            this.origin = origin

            requestLayout()
        }
    }

    override fun compareTo(other: WidgetContainer): Int = widget.compareTo(other.widget)
}
