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

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.Drawable
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.apps.ProfileManager
import adrianogba.stario.launcher.preferences.Vibrations
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.icons.AdaptiveIconView
import adrianogba.stario.launcher.ui.utils.animation.Animation

class WidgetListAdapter(
    private val activity: ThemedActivity,
    private val recycler: RecyclerView,
    private val requestListener: WidgetConfigurator.Request
) : RecyclerView.Adapter<WidgetListAdapter.ViewHolder>() {

    private val entries = ArrayList<WidgetGroupEntry>()

    private var targetHolder: ViewHolder? = null

    init {
        setHasStableIds(true)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun update() {
        reset()

        entries.clear()

        val mainProfile = ProfileManager.getInstance().getProfile(null)

        if (mainProfile != null) {
            val packageManager = activity.packageManager

            for (info in AppWidgetManager.getInstance(activity).installedProviders) {
                val packageName = info.provider.packageName

                val entry = entries.firstOrNull { it.packageName == packageName }
                    ?: createEntry(info, packageName).also { insertSorted(it) }

                entry.addWidget(info)
            }
        }

        // stupidly inefficient
        notifyDataSetChanged()
    }

    private fun createEntry(
        info: AppWidgetProviderInfo, packageName: String
    ): WidgetGroupEntry {
        val application = ProfileManager.getInstance().getProfile(null)?.get(packageName)

        if (application != null) {
            return WidgetGroupEntry(packageName, application.label, application.getIcon())
        }

        val packageManager = activity.packageManager
        val activityInfo = info.activityInfo

        return WidgetGroupEntry(
            packageName,
            activityInfo.loadLabel(packageManager).toString(),
            activityInfo.loadIcon(packageManager)
        )
    }

    private fun insertSorted(entry: WidgetGroupEntry) {
        var index = 0

        while (index < entries.size && entries[index].compareTo(entry) < 0) {
            index++
        }

        entries.add(index, entry)
    }

    private fun reset() {
        val holder = targetHolder ?: return

        holder.adapter?.reset()
        holder.widgets.visibility = View.GONE

        targetHolder = null
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: AdaptiveIconView = itemView.findViewById(R.id.preview)
        val label: TextView = itemView.findViewById(R.id.label)
        val count: TextView = itemView.findViewById(R.id.count)
        val widgets: RecyclerView = itemView.findViewById(R.id.prebuilt)

        var adapter: WidgetItemAdapter? = null

        init {
            itemView.isHapticFeedbackEnabled = false

            widgets.layoutManager = object : LinearLayoutManager(activity) {
                override fun canScrollVertically(): Boolean = false
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, index: Int) {
        val data = entries[index]

        holder.icon.setIcon(data.icon)
        holder.label.text = data.label

        val size = data.widgets.size
        val unit = activity.resources.getString(
            if (size == 1) R.string.widget_one else R.string.widget_many
        )

        holder.count.text = " - $size $unit"

        holder.itemView.setOnClickListener {
            Vibrations.getInstance().vibrate()

            val adapter = WidgetItemAdapter(activity, data, requestListener)
            holder.adapter = adapter
            holder.widgets.adapter = adapter

            if (holder.widgets.visibility != View.VISIBLE) {
                holder.widgets.visibility = View.VISIBLE

                reset()

                targetHolder = holder
            } else {
                reset()
            }

            TransitionManager.beginDelayedTransition(
                recycler,
                ChangeBounds()
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .setDuration(Animation.MEDIUM.duration.toLong())
            )
        }
    }

    override fun getItemCount(): Int = entries.size

    override fun getItemId(position: Int): Long =
        entries[position].packageName.hashCode().toLong()

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(container.context)
                .inflate(R.layout.widget_picker_group, container, false)
        )
    }

    class WidgetGroupEntry(
        @JvmField val packageName: String,
        @JvmField val label: String?,
        @JvmField val icon: Drawable?
    ) : Comparable<WidgetGroupEntry> {

        @JvmField
        val widgets: MutableList<AppWidgetProviderInfo> = ArrayList()

        internal fun addWidget(info: AppWidgetProviderInfo) {
            widgets.add(info)
        }

        override fun compareTo(other: WidgetGroupEntry): Int =
            if (label == null) 0 else label.compareTo(other.label ?: "")
    }
}
