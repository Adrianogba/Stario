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

import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.window.SplashScreen
import androidx.appcompat.content.res.AppCompatResources
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.sheet.drawer.search.SearchEngine
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.utils.UiUtils
import adrianogba.stario.launcher.utils.Utils
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.util.Objects
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture

class AutosuggestAdapter(
    private val activity: ThemedActivity
) : SuggestionSearchAdapter(activity, true) {

    private val suggestionResults = ArrayList<SuggestionEntry>()

    private var runningTask: CompletableFuture<ArrayList<SuggestionEntry>>? = null
    private var currentQuery = ""

    init {
        setHasStableIds(true)
    }

    override fun update(query: String?) {
        runningTask?.let {
            if (!it.isDone) {
                it.cancel(true)
            }
        }

        if (query.isNullOrEmpty()) {
            currentQuery = ""
            suggestionResults.clear()

            notifyInternal()

            return
        }

        val engine = SearchEngine.getEngine(activity.applicationContext)
        val constraint = query.lowercase()
        currentQuery = constraint

        val task = Utils.submitTask(Callable { fetch(engine, constraint) })
        runningTask = task

        task.thenApply { results ->
            UiUtils.post {
                if (currentQuery == constraint) {
                    suggestionResults.clear()

                    for (entry in results) {
                        suggestionResults.add(0, entry)
                    }

                    notifyInternal()
                }

                runningTask = null
            }

            results
        }
    }

    private fun fetch(engine: SearchEngine, constraint: String): ArrayList<SuggestionEntry> {
        val results = ArrayList<SuggestionEntry>()

        try {
            val connection = URL(AUTOSUGGEST_URL + constraint).openConnection()

            val body = BufferedReader(InputStreamReader(connection.getInputStream()))
                .use { it.lineSequence().joinToString("\n") }

            val root = JSONArray(body)

            if (root.length() <= 1) {
                return results
            }

            val target = root.get(1) as? JSONArray ?: return results

            for (index in 0 until minOf(target.length(), MAX_RESULTS)) {
                val result = target.getString(index)
                val uri = Uri.parse(engine.getQuery(result))

                if (uri != null) {
                    results.add(SuggestionEntry(result, uri))
                }
            }
        } catch (exception: Exception) {
            Log.e(TAG, "update: ", exception)
        }

        return results
    }

    override fun submit(): Boolean {
        val engine = SearchEngine.getEngine(activity.applicationContext)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(engine.getQuery(currentQuery)))

        if (engine.getIsWebOnly()) {
            configureIntentToLaunchInBrowser(intent)
        }

        activity.startActivity(intent, iconSplashOptions(ActivityOptions.makeBasic()))

        return true
    }

    private fun iconSplashOptions(options: ActivityOptions): android.os.Bundle {
        options.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON)

        return options.toBundle()
    }

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {
        val holder = super.onCreateViewHolder(container, viewType)

        holder.icon.looseClipping = false
        holder.icon.setIcon(
            LayerDrawable(
                arrayOf<Drawable?>(
                    ColorDrawable(
                        activity.getAttributeData(
                            com.google.android.material.R.attr.colorSecondaryContainer
                        )
                    ),
                    AppCompatResources.getDrawable(activity, R.drawable.ic_search)
                )
            )
        )

        return holder
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, index: Int) {
        val entry = suggestionResults[index]

        viewHolder.itemView.setOnClickListener {
            val engine = SearchEngine.getEngine(activity.applicationContext)
            val intent = Intent(Intent.ACTION_VIEW, entry.uri)

            if (engine.getIsWebOnly()) {
                configureIntentToLaunchInBrowser(intent)
            }

            activity.startActivity(
                intent,
                iconSplashOptions(
                    ActivityOptions.makeScaleUpAnimation(
                        viewHolder.icon, 0, 0,
                        viewHolder.icon.width, viewHolder.icon.height
                    )
                )
            )
        }

        viewHolder.label.text = entry.label
    }

    override fun getItemCount(): Int = suggestionResults.size

    override fun getItemId(position: Int): Long = suggestionResults[position].hashCode().toLong()

    private fun configureIntentToLaunchInBrowser(intent: Intent) {
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://"))
        val resolveInfo = activity.packageManager
            .resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)

        if (resolveInfo != null) {
            intent.setPackage(resolveInfo.activityInfo.packageName)
        }
    }

    private class SuggestionEntry(val label: String, val uri: Uri) {
        private val hash = Objects.hash(label, uri)

        override fun hashCode(): Int = hash
    }

    private companion object {
        const val TAG = "adrianogba.stario.launcher.WebAdapter"
        const val MAX_RESULTS = 5
        const val AUTOSUGGEST_URL = "https://kagi.com/api/autosuggest?q="
    }
}
