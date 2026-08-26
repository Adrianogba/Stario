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

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.view.ViewGroup
import adrianogba.stario.launcher.ui.utils.UiUtils

open class RoundedWidgetHost : AppWidgetHostView {

    constructor(context: Context) : super(context) {
        clipChildren = true
        clipToOutline = true
    }

    constructor(context: Context, params: ViewGroup.LayoutParams) : super(context) {
        layoutParams = params
        setPadding(0, 0, 0, 0)
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        super.setPadding(0, 0, 0, 0)
    }

    override fun setPaddingRelative(start: Int, top: Int, end: Int, bottom: Int) {
        super.setPaddingRelative(0, 0, 0, 0)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        UiUtils.roundViewGroup(this, RADIUS_DP)
    }

    companion object {
        const val RADIUS_DP: Int = 20
    }
}
