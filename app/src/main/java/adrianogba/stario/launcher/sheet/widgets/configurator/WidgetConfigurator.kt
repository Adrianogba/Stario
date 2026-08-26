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

package adrianogba.stario.launcher.sheet.widgets.configurator

import android.appwidget.AppWidgetProviderInfo
import android.view.LayoutInflater
import android.view.View
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.divider.MaterialDividerItemDecoration
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.sheet.widgets.WidgetSize
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.dialogs.ActionDialog
import adrianogba.stario.launcher.ui.recyclers.DividerItemDecorator

class WidgetConfigurator(
    activity: ThemedActivity,
    private val requestListener: Request
) : ActionDialog(activity) {

    private lateinit var scroller: NestedScrollView
    private lateinit var adapter: WidgetListAdapter

    override fun inflateContent(inflater: LayoutInflater): View {
        val contentView = inflater.inflate(R.layout.widget_picker, null)

        scroller = contentView.findViewById(R.id.scroller)
        scroller.clipToOutline = true

        val recycler = contentView.findViewById<RecyclerView>(R.id.container_widgets)

        val layoutManager = LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)

        adapter = WidgetListAdapter(activity, recycler, requestListener)

        recycler.adapter = adapter
        recycler.layoutManager = layoutManager
        recycler.addItemDecoration(
            DividerItemDecorator(activity, MaterialDividerItemDecoration.VERTICAL)
        )

        return contentView
    }

    override fun blurBehind(): Boolean = true

    override fun show() {
        super.show()

        scroller.scrollTo(0, 0)
        adapter.update()
    }

    override fun getDesiredInitialState(): Int {
        if (!Measurements.isLandscape()) {
            return BottomSheetBehavior.STATE_HALF_EXPANDED
        }

        return BottomSheetBehavior.STATE_EXPANDED
    }

    fun interface Request {
        fun requestAddition(info: AppWidgetProviderInfo?, size: WidgetSize?)
    }
}
