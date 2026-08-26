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

package adrianogba.stario.launcher.activities.settings.dialogs.hide.pager

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.apps.ProfileApplicationManager
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.recyclers.autogrid.AutoGridLayoutManager
import adrianogba.stario.launcher.ui.recyclers.overscroll.OverScrollEffect
import adrianogba.stario.launcher.ui.recyclers.overscroll.OverScrollRecyclerView
import adrianogba.stario.launcher.ui.utils.LayoutSizeObserver
import kotlin.math.min

class HideApplicationsPage : Fragment {
    private var applicationManager: ProfileApplicationManager? = null
    private var recyclerView: OverScrollRecyclerView? = null

    constructor() {
        // default
    }

    constructor(applicationManager: ProfileApplicationManager?) {
        this.applicationManager = applicationManager
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.pop_up_hide_page, container, false)

        val recycler = root.findViewById<OverScrollRecyclerView>(R.id.recycler)
        recyclerView = recycler

        recycler.setOverscrollPullEdges(OverScrollEffect.PULL_EDGE_BOTTOM)

        recycler.setPadding(
            recycler.paddingLeft, recycler.paddingTop,
            recycler.paddingRight, Measurements.dpToPx(40f) + Measurements.spToPx(60f)
        )

        val manager = AutoGridLayoutManager(activity, 1)
        LayoutSizeObserver.attach(root, LayoutSizeObserver.WIDTH,
            object : LayoutSizeObserver.OnChange {
                override fun onChange(view: View, watchFlags: Int, rect: Rect) {
                    manager.spanCount = min(6, rect.width() / Measurements.dpToPx(90f))
                }
            })

        recycler.layoutManager = manager
        recycler.itemAnimator = null

        recycler.adapter = HiddenRecyclerAdapter(activity as ThemedActivity, applicationManager)

        return root
    }

    override fun onDestroy() {
        recyclerView?.adapter = null

        super.onDestroy()
    }

    fun getRecycler(): OverScrollRecyclerView? = recyclerView
}
