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

package adrianogba.stario.launcher.sheet.drawer.search.recyclers.adapters.suggestions

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.sheet.drawer.search.recyclers.adapters.AbstractSearchListAdapter
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.icons.AdaptiveIconView

abstract class SuggestionSearchAdapter(
    private val activity: ThemedActivity,
    private val hasLinkArrow: Boolean
) : AbstractSearchListAdapter<SuggestionSearchAdapter.ViewHolder>() {

    inner class ViewHolder @SuppressLint("ClickableViewAccessibility") constructor(
        itemView: ViewGroup
    ) : RecyclerView.ViewHolder(itemView) {
        @JvmField val label: TextView = itemView.findViewById(R.id.textView)
        @JvmField val icon: AdaptiveIconView = itemView.findViewById(R.id.icon)

        init {
            if (!hasLinkArrow) {
                itemView.findViewById<View>(R.id.target_arrow).visibility = View.GONE
            }

            itemView.isHapticFeedbackEnabled = false
        }
    }

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(activity)
                .inflate(R.layout.suggestion_item, container, false) as ViewGroup
        )
    }
}
