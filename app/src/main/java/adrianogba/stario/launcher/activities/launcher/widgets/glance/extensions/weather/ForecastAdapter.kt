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

package adrianogba.stario.launcher.activities.launcher.widgets.glance.extensions.weather

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.utils.Utils
import java.text.DateFormat
import java.text.SimpleDateFormat
import kotlin.math.min
import kotlin.math.roundToInt

class ForecastAdapter(
    activity: ThemedActivity,
    private val data: List<Weather.Data>,
    private val indexToStart: Int
) : RecyclerView.Adapter<ForecastAdapter.ViewHolder>() {

    private val activity: Activity = activity
    private val preferences: SharedPreferences =
        activity.applicationContext.getSharedPreferences(Entry.WEATHER)

    class ViewHolder @SuppressLint("ClickableViewAccessibility") constructor(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {
        val time: TextView = itemView.findViewById(R.id.time)
        val icon: ImageView = itemView.findViewById(R.id.icon)
        val temperature: TextView = itemView.findViewById(R.id.temperature)

        companion object {
            @JvmField
            val DATE_FORMAT: DateFormat = SimpleDateFormat.getTimeInstance(DateFormat.SHORT)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(viewHolder: ViewHolder, index: Int) {
        val data = this.data[index + indexToStart]

        viewHolder.time.text = ViewHolder.DATE_FORMAT.format(data.date)
        viewHolder.icon.setImageResource(Weather.getIcon(data.iconCode))

        if (preferences.getBoolean(Weather.IMPERIAL_KEY, Utils.isSystemUsingImperial(activity))) {
            viewHolder.temperature.text =
                Utils.toFahrenheit(data.temperature).roundToInt().toString() + "\u00B0"
        } else {
            viewHolder.temperature.text = data.temperature.roundToInt().toString() + "\u00B0"
        }
    }

    override fun getItemCount(): Int {
        return if (indexToStart < 0) 0 else min(data.size - indexToStart, FORECAST_SIZE)
    }

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(container.context)

        return ViewHolder(inflater.inflate(R.layout.forecast_item, container, false))
    }

    private companion object {
        private const val FORECAST_SIZE = 12
    }
}
