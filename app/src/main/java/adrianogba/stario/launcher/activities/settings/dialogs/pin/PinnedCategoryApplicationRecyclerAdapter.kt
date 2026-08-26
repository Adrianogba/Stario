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

package adrianogba.stario.launcher.activities.settings.dialogs.pin

import adrianogba.stario.launcher.apps.Category
import adrianogba.stario.launcher.apps.LauncherApplication
import adrianogba.stario.launcher.sheet.drawer.RecyclerApplicationAdapter
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.icons.AdaptiveIconView
import adrianogba.stario.launcher.ui.recyclers.async.InflationType
import java.util.function.Supplier

class PinnedCategoryApplicationRecyclerAdapter(
    activity: ThemedActivity,
    private val category: Category?
) : RecyclerApplicationAdapter(activity, false, InflationType.SYNCED) {

    inner class PinnedCategoryApplicationViewHolder(viewType: Int) :
        ApplicationViewHolder(viewType) {

        override fun onInflated() {
            itemView.layoutParams.width = AdaptiveIconView.getMaxIconSize() +
                    Measurements.getDefaultPadding()

            super.onInflated()
        }
    }

    override fun getHolderSupplier(viewType: Int): Supplier<ApplicationViewHolder> {
        return Supplier { PinnedCategoryApplicationViewHolder(viewType) }
    }

    override fun getApplication(index: Int): LauncherApplication? {
        return category?.get(index)
    }

    override fun getTotalItemCount(): Int = category?.size ?: 0

    override fun allowApplicationStateEditing(): Boolean = false
}
