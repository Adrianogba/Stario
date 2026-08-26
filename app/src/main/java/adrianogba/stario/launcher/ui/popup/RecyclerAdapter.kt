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

package adrianogba.stario.launcher.ui.popup

import android.annotation.SuppressLint
import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.preferences.Vibrations
import carbon.widget.ImageView

class RecyclerAdapter(
    private val activity: Activity,
    private val clickListener: View.OnClickListener?
) : RecyclerView.Adapter<RecyclerAdapter.ViewHolder>() {

    private val items = ArrayList<PopupMenu.Item>()

    class ViewHolder @SuppressLint("ClickableViewAccessibility") constructor(
        itemView: ViewGroup
    ) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.icon)
        val label: TextView = itemView.findViewById(R.id.label)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, index: Int) {
        val holder = items[index]

        viewHolder.icon.background = holder.icon
        viewHolder.label.text = holder.label

        viewHolder.itemView.setOnClickListener { view ->
            Vibrations.getInstance().vibrate()
            holder.listener.onClick(view)

            clickListener?.onClick(view)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(activity)
                .inflate(R.layout.popup_item, container, false) as ViewGroup
        )
    }

    fun add(item: PopupMenu.Item) {
        items.add(item)
    }
}
