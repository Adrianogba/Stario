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

package adrianogba.stario.launcher.activities.settings.dialogs.icons

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.apps.IconPackManager
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.icons.AdaptiveIconView
import adrianogba.stario.launcher.ui.utils.UiUtils

class IconsRecyclerAdapter(
    private val activity: ThemedActivity,
    private val listener: View.OnClickListener?
) : RecyclerView.Adapter<IconsRecyclerAdapter.ViewHolder>() {

    private val manager: IconPackManager = IconPackManager.from(activity)

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: AdaptiveIconView = itemView.findViewById(R.id.icon)
        val label: TextView = itemView.findViewById(R.id.label)
        val count: TextView = itemView.findViewById(R.id.count)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        if (position < itemCount - 1) {
            val pack = manager.getPack(position)

            viewHolder.label.text = pack.label
            viewHolder.icon.setIcon(pack.icon)

            viewHolder.count.visibility = View.VISIBLE
            viewHolder.count.setText(R.string.calculating)

            pack.componentCount.thenAccept { integer ->
                UiUtils.post {
                    viewHolder.count.text = String.format("%,d", integer) + " " +
                            activity.resources.getString(R.string.components)
                }
            }

            viewHolder.itemView.setOnClickListener { v ->
                manager.setActiveIconPack(pack)

                listener?.onClick(v)
            }
        } else {
            viewHolder.label.setText(R.string.default_text)
            viewHolder.icon.setIcon(
                AppCompatResources.getDrawable(activity, R.mipmap.ic_launcher)
            )

            viewHolder.count.visibility = View.GONE

            viewHolder.itemView.setOnClickListener { v ->
                manager.setActiveIconPack(null)

                listener?.onClick(v)
            }
        }
    }

    override fun getItemCount(): Int = manager.count + 1

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(activity).inflate(R.layout.icon_item, container, false)
        )
    }
}
