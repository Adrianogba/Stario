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

package adrianogba.stario.launcher.activities.launcher.widgets.pins

import android.annotation.SuppressLint
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.apps.Category
import adrianogba.stario.launcher.apps.LauncherApplication
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.icons.AdaptiveIconView
import adrianogba.stario.launcher.ui.recyclers.async.AsyncRecyclerAdapter
import adrianogba.stario.launcher.ui.recyclers.async.InflationType
import java.util.function.Supplier
import kotlin.math.max
import kotlin.math.min

class PinnedAppsGroupAdapter(
    activity: ThemedActivity,
    private val category: Category?,
    private val startingIndex: Int
) : AsyncRecyclerAdapter<PinnedAppsGroupAdapter.ViewHolder>(activity, InflationType.SYNCED) {

    inner class ViewHolder : AsyncViewHolder() {
        lateinit var icon: AdaptiveIconView

        @SuppressLint("ClickableViewAccessibility")
        override fun onInflated() {
            icon = itemView.findViewById(R.id.icon)
        }
    }

    public override fun onBind(viewHolder: ViewHolder, index: Int) {
        viewHolder.icon.setApplication(getApplication(index))
    }

    override fun getLayout(viewType: Int): Int = R.layout.pinned_application_group_item

    override fun getHolderSupplier(viewType: Int): Supplier<ViewHolder> {
        return Supplier { ViewHolder() }
    }

    fun getApplication(index: Int): LauncherApplication {
        return category?.get(index + startingIndex) ?: LauncherApplication.FALLBACK_APP
    }

    override fun getTotalItemCount(): Int {
        return min(if (category != null) max(0, category.size - startingIndex) else 0, 4)
    }
}
