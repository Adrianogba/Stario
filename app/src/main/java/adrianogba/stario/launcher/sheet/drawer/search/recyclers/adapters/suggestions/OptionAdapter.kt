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
import android.app.ActivityOptions
import android.app.SearchManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.Toast
import android.window.SplashScreen
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.apps.LauncherApplication
import adrianogba.stario.launcher.apps.ProfileManager
import adrianogba.stario.launcher.apps.interfaces.LauncherApplicationListener
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.utils.UiUtils
import adrianogba.stario.launcher.utils.Utils

class OptionAdapter(
    private val activity: ThemedActivity
) : SuggestionSearchAdapter(activity, false) {

    private val options = ArrayList<OptionEntry>()
    private val packageManager: PackageManager = activity.packageManager
    private val applicationManager = ProfileManager.getInstance().getProfile(null)

    private var recyclerView: RecyclerView? = null
    private var show = false
    private var query: String = ""

    init {
        Utils.submitTask { collectOptions() }
    }

    /**
     * Everything installed that can answer a search, in two groups: apps that
     * handle one of the predefined uri schemes, and apps that declare a web or
     * plain search intent filter.
     */
    private fun collectOptions() {
        val applicationManager = applicationManager ?: return

        for (uri in PREDEFINED_URIS) {
            val resolvers = packageManager.queryIntentActivities(
                Intent(Intent.ACTION_VIEW, Uri.parse(uri)), PackageManager.MATCH_ALL
            )

            for (info in resolvers) {
                val application =
                    applicationManager.get(info.activityInfo.packageName) ?: continue

                addIfNew(OptionEntry(application, uri))
            }
        }

        for (filter in SEARCH_FILTERS) {
            val resolvers =
                packageManager.queryIntentActivities(Intent(filter), PackageManager.MATCH_ALL)

            for (info in resolvers) {
                val application =
                    applicationManager.get(info.activityInfo.packageName) ?: continue

                if (isUsableSearchActivity(info.activityInfo)) {
                    addIfNew(OptionEntry(application, info.activityInfo, filter))
                }
            }
        }

        UiUtils.post { notifyInternal() }
    }

    private fun isUsableSearchActivity(info: ActivityInfo): Boolean {
        if (!info.enabled || !info.exported ||
            info.name.lowercase().contains("redirect")
        ) {
            return false
        }

        return info.permission == null ||
                ContextCompat.checkSelfPermission(activity, info.permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun addIfNew(entry: OptionEntry) {
        if (!options.contains(entry)) {
            options.add(entry)
        }
    }

    private val listener = object : LauncherApplicationListener {
        override fun onInserted(application: LauncherApplication?) = insert(application)

        override fun onShowed(application: LauncherApplication?) = insert(application)

        override fun onRemoved(application: LauncherApplication?) = remove(application)

        override fun onHidden(application: LauncherApplication?) = remove(application)

        override fun onUpdated(application: LauncherApplication?) {
            recyclerView?.post {
                val index = options.indexOfFirst { it.application == application }

                if (index >= 0) {
                    notifyItemChanged(index)
                }
            }
        }

        private fun insert(application: LauncherApplication?) {
            if (application == null) {
                return
            }

            recyclerView?.post {
                for (filter in SEARCH_FILTERS) {
                    val intent = Intent(filter)
                    intent.setPackage(application.info.packageName)

                    val resolveInfo = packageManager.queryIntentActivities(
                        intent, PackageManager.GET_RESOLVED_FILTER
                    )

                    if (resolveInfo.isNotEmpty()) {
                        options.add(
                            OptionEntry(application, resolveInfo[0].activityInfo, filter)
                        )

                        notifyInternal()
                    }
                }
            }
        }

        private fun remove(application: LauncherApplication?) {
            recyclerView?.post {
                options.removeAll { it.application == application }
            }
        }
    }

    override fun update(query: String?) {
        this.query = query.orEmpty()

        val shouldShow = this.query.isNotEmpty()

        if (show != shouldShow) {
            show = shouldShow

            invalidateRecyclerVisibility()
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

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(viewHolder: ViewHolder, index: Int) {
        val entry = options[index]

        viewHolder.label.text =
            activity.resources.getString(R.string.search_on) + " " + entry.application.label

        viewHolder.icon.setApplication(entry.application)

        viewHolder.itemView.setOnClickListener {
            val options = ActivityOptions.makeScaleUpAnimation(
                viewHolder.icon, 0, 0, viewHolder.icon.width, viewHolder.icon.height
            )
            options.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR)

            val intent = entry.getIntent()
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val uri = intent.data

            if (uri != null) {
                intent.data = Uri.parse(uri.toString() + query)
            } else {
                intent.putExtra(SearchManager.QUERY, query)
            }

            try {
                activity.startActivity(intent, options.toBundle())
            } catch (exception: Exception) {
                Toast.makeText(activity, "Unable to launch activity", Toast.LENGTH_SHORT)
                    .show()

                Log.e(TAG, "onBindViewHolder: ", exception)
            }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        this.recyclerView = recyclerView

        applicationManager?.addApplicationListener(listener)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)

        applicationManager?.removeApplicationListener(listener)

        this.recyclerView = null
    }

    override fun getItemCount(): Int = if (show) options.size else 0

    override fun getItemId(position: Int): Long = options[position].hashCode().toLong()

    private class OptionEntry {
        val application: LauncherApplication

        private val referenceIntent: Intent

        constructor(application: LauncherApplication, info: ActivityInfo, filter: String) {
            this.application = application

            referenceIntent = Intent()
            referenceIntent.component = ComponentName(info.packageName, info.name)
            referenceIntent.action = filter
        }

        constructor(application: LauncherApplication, uri: String) {
            this.application = application

            referenceIntent = Intent()
            referenceIntent.setPackage(application.info.packageName)
            referenceIntent.action = Intent.ACTION_VIEW
            referenceIntent.data = Uri.parse(uri)
        }

        fun getIntent(): Intent = Intent(referenceIntent)

        override fun hashCode(): Int = application.info.hashCode()

        override fun equals(other: Any?): Boolean =
            this === other || (other is OptionEntry && other.application == application)
    }

    private companion object {
        const val TAG = "adrianogba.stario.launcher.OptionAdapter"

        val PREDEFINED_URIS = arrayOf("market://search?q=", "geo:?q=")
        val SEARCH_FILTERS = arrayOf(Intent.ACTION_WEB_SEARCH, Intent.ACTION_SEARCH)
    }
}
