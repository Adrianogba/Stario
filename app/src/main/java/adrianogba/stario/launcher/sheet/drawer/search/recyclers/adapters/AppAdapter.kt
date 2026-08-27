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

package adrianogba.stario.launcher.sheet.drawer.search.recyclers.adapters

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.apps.LauncherApplication
import adrianogba.stario.launcher.apps.ProfileManager
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.sheet.drawer.RecyclerApplicationAdapter
import adrianogba.stario.launcher.sheet.drawer.search.JaroWinklerDistance
import adrianogba.stario.launcher.sheet.drawer.search.SearchFragment
import adrianogba.stario.launcher.sheet.drawer.search.Searchable
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.recyclers.async.InflationType

class AppAdapter(activity: ThemedActivity) :
    RecyclerApplicationAdapter(activity, null, InflationType.SYNCED), Searchable {

    private val preferences =
        activity.applicationContext.getSharedPreferences(Entry.SEARCH)

    private var applications: List<LauncherApplication> = ArrayList()
    private var recyclerView: RecyclerView? = null
    private var currentQuery: String = ""

    override fun getApplication(index: Int): LauncherApplication = applications[index]

    override fun allowApplicationStateEditing(): Boolean = false

    override fun getLabelLineCount(): Int = 1

    override fun getTotalItemCount(): Int = applications.size

    @SuppressLint("NotifyDataSetChanged")
    override fun update(query: String?) {
        applications = filter(query)
        currentQuery = query.orEmpty()

        val runnable = Runnable {
            notifyDataSetChanged()
            updateRecyclerVisibility()
        }

        val recyclerView = recyclerView

        if (recyclerView != null && recyclerView.isAnimating) {
            recyclerView.post(runnable)
        } else {
            runnable.run()
        }
    }

    /**
     * Three tiers, kept in order within one list: labels that start with the
     * query, then labels that contain it, then labels close enough by Jaro
     * Winkler. The three counters are the insertion points for each tier.
     */
    private fun filter(query: String?): List<LauncherApplication> {
        if (query.isNullOrEmpty()) {
            return ArrayList()
        }

        val filterPattern = query.lowercase()
        val filtered = ArrayList<LauncherApplication>()

        var starting = 0
        var containing = 0
        var close = 0

        val showHiddenItems =
            preferences.getBoolean(SearchFragment.SEARCH_HIDDEN_APPS, false)

        for (manager in ProfileManager.getInstance().profiles) {
            val count = if (showHiddenItems) manager.size else manager.actualSize

            for (index in 0 until count) {
                val application =
                    (if (showHiddenItems) manager.get(index, true) else manager.get(index))
                        ?: continue

                val lowercaseLabel = application.label.lowercase()

                if (lowercaseLabel.startsWith(filterPattern)) {
                    filtered.add(starting++, application)
                } else if (lowercaseLabel.contains(filterPattern)) {
                    filtered.add(starting + containing++, application)
                } else if (JaroWinklerDistance.getScore(lowercaseLabel, filterPattern) > 0.87) {
                    filtered.add(starting + containing + close++, application)
                }
            }
        }

        return if (filtered.size > SearchFragment.MAX_APP_QUERY_ITEMS) {
            filtered.subList(0, SearchFragment.MAX_APP_QUERY_ITEMS)
        } else {
            filtered
        }
    }

    override fun submit(): Boolean {
        val recyclerView = recyclerView ?: return false

        if (recyclerView.visibility != View.VISIBLE) {
            return false
        }

        val view = recyclerView.layoutManager?.findViewByPosition(0) ?: return false

        view.callOnClick()

        return true
    }

    private fun updateRecyclerVisibility() {
        val recyclerView = recyclerView ?: return

        recyclerView.visibility = if (itemCount == 0) View.GONE else View.VISIBLE
    }

    override fun onBind(viewHolder: ApplicationViewHolder, index: Int) {
        super.onBind(viewHolder, index)

        val label = getApplication(index).label
        val substringStart = label.lowercase().indexOf(currentQuery)

        if (substringStart < 0) {
            return
        }

        val builder = SpannableStringBuilder(label)
        builder.setSpan(
            StyleSpan(Typeface.BOLD),
            substringStart, substringStart + currentQuery.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        viewHolder.setLabel(builder)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView

        updateRecyclerVisibility()

        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        updateRecyclerVisibility()

        this.recyclerView = null

        super.onDetachedFromRecyclerView(recyclerView)
    }
}
