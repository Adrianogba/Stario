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
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.window.SplashScreen
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.exceptions.Unauthorized
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.utils.UiUtils
import adrianogba.stario.launcher.utils.Utils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Objects
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern
import kotlin.math.floor
import kotlin.random.Random

class WebAdapter(
    private val activity: ThemedActivity
) : AbstractSearchListAdapter<WebAdapter.ViewHolder>() {

    private val preferences =
        activity.applicationContext.getSharedPreferences(Entry.SEARCH)
    private val searchResults = ArrayList<WebEntry>()

    private var runningTask: CompletableFuture<ArrayList<WebEntry>>? = null
    private var listener: UnauthorizedListener? = null

    class ViewHolder @SuppressLint("ClickableViewAccessibility") constructor(
        itemView: ViewGroup
    ) : RecyclerView.ViewHolder(itemView) {

        val title: TextView = itemView.findViewById(R.id.title)
        val breadcrumbs: TextView = itemView.findViewById(R.id.breadcrumbs)
        val snippet: TextView = itemView.findViewById(R.id.snippet)
        val favicon: ImageView = itemView.findViewById(R.id.favicon)

        init {
            itemView.isHapticFeedbackEnabled = false
        }
    }

    override fun update(query: String?) {
        searchResults.clear()
        notifyInternal()

        runningTask?.let {
            if (!it.isDone) {
                it.cancel(true)
            }
        }

        val searchEnabled = preferences.getBoolean(SEARCH_RESULTS, false)
        val apiKey = preferences.getString(KAGI_API_KEY, null)

        if (!searchEnabled || apiKey.isNullOrEmpty() || query.isNullOrEmpty()) {
            return
        }

        val constraint = query.lowercase()

        val task = Utils.submitTask(Callable { fetch(constraint, apiKey) })
        runningTask = task

        task.thenApply { results ->
            UiUtils.post {
                searchResults.clear()

                for (entry in results) {
                    searchResults.add(0, entry)
                }

                notifyInternal()

                runningTask = null
            }

            results
        }
    }

    private fun fetch(constraint: String, apiKey: String): ArrayList<WebEntry> {
        val results = ArrayList<WebEntry>()

        val data = try {
            getData(constraint, apiKey)
        } catch (exception: Unauthorized) {
            UiUtils.post { listener?.onDenied() }

            return results
        }

        val queryResults = data?.opt("data") as? JSONArray ?: return results

        for (index in 0 until queryResults.length()) {
            val entry = queryResults.get(index) as? JSONObject ?: continue

            val url = entry.opt("url") as? String
            val title = entry.opt("title") as? String
            val snippet = entry.opt("snippet") as? String

            if (url != null && title != null) {
                results.add(WebEntry(url, title, snippet))
            }
        }

        return results
    }

    @Throws(Unauthorized::class)
    private fun getData(query: String, key: String): JSONObject? {
        try {
            val connection = URL(RESULTS_URL + query).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bot $key")
            connection.addRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")

            val code = connection.responseCode

            if (code in 200..399) {
                return BufferedReader(InputStreamReader(connection.inputStream))
                    .use { JSONObject(it.lineSequence().joinToString("\n")) }
            }

            if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw Unauthorized()
            }
        } catch (exception: IOException) {
            Log.e(TAG, "getData: ", exception)
        } catch (exception: JSONException) {
            Log.e(TAG, "getData: ", exception)
        }

        return null
    }

    override fun submit(): Boolean = true

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(activity)
                .inflate(R.layout.search_item, container, false) as ViewGroup
        )
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, index: Int) {
        val entry = searchResults[index]

        viewHolder.title.text = Html.fromHtml(entry.title, Html.FROM_HTML_MODE_LEGACY)

        if (entry.snippet != null) {
            viewHolder.snippet.text = Html.fromHtml(entry.snippet, Html.FROM_HTML_MODE_LEGACY)
            viewHolder.snippet.visibility = View.VISIBLE
        } else {
            viewHolder.snippet.visibility = View.GONE
        }

        viewHolder.favicon.visibility = View.GONE

        val baseMatcher = BASE_URL_REGEX.matcher(entry.url)

        if (baseMatcher.find() && !activity.isDestroyed) {
            loadFavicon(viewHolder, baseMatcher.group(1))

            viewHolder.breadcrumbs.text = getBreadcrumbString(baseMatcher.group(1), entry.url)
        }

        viewHolder.itemView.setOnClickListener {
            val options = ActivityOptions.makeClipRevealAnimation(
                viewHolder.itemView, 0, 0,
                viewHolder.itemView.width, viewHolder.itemView.height
            )
            options.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON)

            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(entry.url)), options.toBundle()
            )
        }
    }

    /**
     * allesedv.com serves favicons from nine numbered hosts. Picking one at
     * random spreads the requests rather than hammering a single one.
     */
    private fun loadFavicon(viewHolder: ViewHolder, host: String?) {
        val shard = floor(Random.nextDouble() * 9 + 1).toInt()

        Glide.with(activity)
            .load("https://f$shard.allesedv.com/32/$host")
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?, model: Any?,
                    target: Target<Drawable>, isFirstResource: Boolean
                ): Boolean = false

                override fun onResourceReady(
                    resource: Drawable, model: Any,
                    target: Target<Drawable>?, dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    viewHolder.favicon.visibility = View.VISIBLE
                    viewHolder.favicon.setImageDrawable(resource)

                    return true
                }
            })
            .into(viewHolder.favicon)
    }

    override fun getItemCount(): Int = searchResults.size

    fun setUnauthorizedListener(listener: UnauthorizedListener?) {
        this.listener = listener
    }

    fun interface UnauthorizedListener {
        fun onDenied()
    }

    private class WebEntry(val url: String, val title: String, val snippet: String?) {
        private val hash = Objects.hash(url, title, snippet)

        override fun hashCode(): Int = hash
    }

    companion object {
        const val SEARCH_RESULTS = "com.stario.SEARCH_RESULTS"
        const val KAGI_API_KEY = "com.stario.KAGI_API_KEY"

        private const val TAG = "adrianogba.stario.launcher.WebAdapter"
        private const val RESULTS_URL = "https://kagi.com/api/v0/search?limit=6&q="

        private val BASE_URL_REGEX: Pattern =
            Pattern.compile("^(?:https?://)?(?:www\\.)?([^/?:]+)")
        private val PATH_REGEX: Pattern =
            Pattern.compile("^(?:https?://)?(?:www\\.)?[^/]+(/[^/?#]+)+")

        private fun getBreadcrumbString(baseUrl: String?, url: String): String {
            val breadcrumb = StringBuilder(baseUrl.orEmpty())
            val pathMatcher = PATH_REGEX.matcher(url)

            if (pathMatcher.find()) {
                val path = pathMatcher.group(1)

                if (path != null) {
                    for (segment in path.split("/")) {
                        if (segment.isNotEmpty()) {
                            breadcrumb.append(" > ").append(segment)
                        }
                    }
                }
            }

            return breadcrumb.toString()
        }
    }
}
