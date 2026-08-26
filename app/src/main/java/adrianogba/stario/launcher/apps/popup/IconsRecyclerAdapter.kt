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

package adrianogba.stario.launcher.apps.popup

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.apps.IconPackManager
import adrianogba.stario.launcher.apps.LauncherApplication
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.icons.AdaptiveIconView
import adrianogba.stario.launcher.ui.utils.UiUtils

class IconsRecyclerAdapter(
    private val activity: ThemedActivity,
    private val application: LauncherApplication,
    private val listener: View.OnClickListener?
) : RecyclerView.Adapter<IconsRecyclerAdapter.ViewHolder>() {

    private val manager: IconPackManager = IconPackManager.from(activity)

    private var icons: List<Pair<IconPackManager.IconPack?, Pair<String?, Drawable?>>> =
        ArrayList()

    init {
        manager.getIcons(application).thenAccept { iconList ->
            icons = iconList

            UiUtils.post { notifyChanged() }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyChanged() {
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: AdaptiveIconView = itemView.findViewById(R.id.icon)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val item = icons[position]

        viewHolder.icon.setIcon(item.second.second)
        viewHolder.itemView.setOnClickListener { v ->
            manager.setIconPackPreference(
                application.info.packageName, item.first, item.second.first
            )

            listener?.onClick(v)
        }
    }

    override fun getItemCount(): Int = icons.size

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(activity).inflate(R.layout.pop_up_icon_item, container, false)
        )
    }
}
