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

package adrianogba.stario.launcher.activities.settings.dialogs.language

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.preferences.Language
import adrianogba.stario.launcher.themes.ThemedActivity

class LanguageRecyclerAdapter(
    private val activity: ThemedActivity,
    private val listener: OnLanguagePicked
) : RecyclerView.Adapter<LanguageRecyclerAdapter.ViewHolder>() {

    private val selected = Language.current()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val label: TextView = itemView.findViewById(R.id.label)
        val check: ImageView = itemView.findViewById(R.id.check)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val language = Language.entries[position]

        viewHolder.label.setText(language.displayName)
        viewHolder.check.visibility = if (language == selected) View.VISIBLE else View.INVISIBLE

        viewHolder.itemView.setOnClickListener {
            if (language == selected) {
                listener.onPicked(false)

                return@setOnClickListener
            }

            Language.apply(language)

            listener.onPicked(true)
        }
    }

    override fun getItemCount(): Int = Language.entries.size

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(activity).inflate(R.layout.language_item, container, false)
        )
    }

    fun interface OnLanguagePicked {
        fun onPicked(changed: Boolean)
    }
}
