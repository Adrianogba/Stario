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

package adrianogba.stario.launcher.sheet.briefing.configurator

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import com.google.android.material.bottomsheet.BottomSheetBehavior
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.Stario
import adrianogba.stario.launcher.sheet.briefing.dialog.page.feed.BriefingFeedList
import adrianogba.stario.launcher.sheet.briefing.dialog.page.feed.Feed
import adrianogba.stario.launcher.sheet.briefing.rss.RSSHelper
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.common.text.PulsingTextView
import adrianogba.stario.launcher.ui.dialogs.ActionDialog
import adrianogba.stario.launcher.ui.utils.UiUtils
import adrianogba.stario.launcher.utils.Utils
import carbon.view.SimpleTextWatcher
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class BriefingConfigurator(activity: ThemedActivity) : ActionDialog(activity) {

    private val list = BriefingFeedList.from(activity)

    @Volatile
    private var currentSearchTask: Future<*>? = null

    @Volatile
    private var validatedFeed: Feed? = null

    private var debounceRunnable: Runnable? = null
    private var contentView: ViewGroup? = null
    private var limit: PulsingTextView? = null
    private var preview: LinearLayout? = null
    private var title: TextView? = null
    private var query: EditText? = null

    override fun inflateContent(inflater: LayoutInflater): View {
        val contentView = inflater.inflate(R.layout.briefing_configurator, null) as ViewGroup
        this.contentView = contentView

        val query = contentView.findViewById<EditText>(R.id.query)
        this.query = query

        preview = contentView.findViewById(R.id.preview)
        title = contentView.findViewById(R.id.title)
        limit = contentView.findViewById(R.id.limit)

        query.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(
                sequence: CharSequence, start: Int, before: Int, count: Int
            ) {
                onQueryChanged(sequence.toString())
            }
        })

        contentView.findViewById<View>(R.id.add).setOnClickListener { view ->
            view.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.bounce_small))

            addValidatedFeed()
        }

        contentView.findViewById<View>(R.id.paste).setOnClickListener { pasteFromClipboard() }

        return contentView
    }

    private fun onQueryChanged(text: String) {
        debounceRunnable?.let { UiUtils.removeUICallback(it) }

        currentSearchTask?.let {
            if (!it.isDone) {
                it.cancel(true)
            }
        }

        if (text.isEmpty()) {
            showStatus(null, false)

            return
        }

        val validUrl = firstValidUrl(text)

        if (validUrl == null) {
            showStatus(R.string.invalid_url, false)

            return
        }

        if (!Utils.isNetworkAvailable(activity)) {
            showStatus(R.string.no_connection, false)

            return
        }

        val runnable = Runnable {
            showStatus(R.string.searching, true)

            currentSearchTask = Utils.submitTask(
                FeedDiscoveryTask(
                    activity.applicationContext,
                    arrayOf(validUrl, validUrl.replace(Regex("/$"), "") + ".rss")
                )
            )
        }
        debounceRunnable = runnable

        UiUtils.postDelayed(runnable, DEBOUNCE_DELAY)
    }

    /**
     * Takes the text as typed if it is already a url, otherwise tries it with
     * an https prefix, which is what people actually paste.
     */
    private fun firstValidUrl(text: String): String? {
        if (isValidUrl(text)) {
            return text
        }

        val prefixed = "https://$text"

        return if (isValidUrl(prefixed)) prefixed else null
    }

    private fun addValidatedFeed() {
        val feed = validatedFeed

        if (feed == null || feed.title.isEmpty()) {
            return
        }

        if (!list.add(feed)) {
            Toast.makeText(activity, R.string.already_subscribed, Toast.LENGTH_LONG).show()

            return
        }

        val behavior = getBehavior()
        behavior.isDraggable = false
        behavior.state = BottomSheetBehavior.STATE_HIDDEN

        contentView?.let { UiUtils.hideKeyboard(it) }
    }

    private fun pasteFromClipboard() {
        val clipboard =
            activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        if (!clipboard.hasPrimaryClip()) {
            return
        }

        val clip = clipboard.primaryClip ?: return

        if (!clip.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
            return
        }

        val text = clip.getItemAt(0).coerceToText(activity).toString()

        if (text.isNotEmpty()) {
            query?.setText(text)
            query?.setSelection(text.length)
        }
    }

    override fun show() {
        query?.setText(null)

        super.show()
    }

    private fun isValidUrl(url: String): Boolean =
        URLUtil.isValidUrl(url) && Patterns.WEB_URL.matcher(url).matches()

    private fun showStatus(@StringRes messageRes: Int?, pulsating: Boolean) {
        preview?.visibility = View.GONE

        val limit = limit

        if (limit != null) {
            if (messageRes != null) {
                limit.text = activity.resources.getString(messageRes)
                limit.setPulsating(pulsating)
                limit.visibility = View.VISIBLE
            } else {
                limit.visibility = View.GONE
            }
        }

        validatedFeed = null
    }

    private fun showPreview(feed: Feed) {
        validatedFeed = feed

        title?.text = feed.title
        preview?.visibility = View.VISIBLE
        limit?.visibility = View.GONE
    }

    /**
     * Tries each candidate url as a feed directly, then as a page whose head
     * links to one.
     */
    private inner class FeedDiscoveryTask(
        private val context: Stario,
        private val urls: Array<String>
    ) : Runnable {

        override fun run() {
            for (url in urls) {
                if (Thread.currentThread().isInterrupted) {
                    return
                }

                try {
                    if (publishIfParses(url)) {
                        return
                    }

                    val discovered = discoverFeedUrl(url) ?: continue

                    if (publishIfParses(discovered)) {
                        return
                    }
                } catch (exception: IOException) {
                    Log.e(TAG, "IOException: " + exception.message)
                }
            }

            contentView?.post { showStatus(R.string.invalid_rss, false) }
        }

        private fun publishIfParses(url: String): Boolean {
            val feed = attemptParse(url)

            // after a network task, check for interruption
            if (Thread.currentThread().isInterrupted) {
                return true
            }

            if (feed == null) {
                return false
            }

            contentView?.post { showPreview(feed) }

            return true
        }

        private fun attemptParse(url: String): Feed? {
            var streamFuture: CompletableFuture<*>? = null

            try {
                val future = RSSHelper.futureParse(url)
                streamFuture = future

                val title = future.get(10, TimeUnit.SECONDS).title
                    ?: context.getString(R.string.unknown_feed)

                return Feed(title, url)
            } catch (exception: InterruptedException) {
                streamFuture?.cancel(true)

                Thread.currentThread().interrupt()
            } catch (exception: TimeoutException) {
                streamFuture?.cancel(true)
            } catch (exception: Exception) {
                streamFuture?.cancel(true)
            }

            return null
        }

        @Throws(IOException::class)
        private fun discoverFeedUrl(url: String): String? {
            val document = Jsoup.connect(url)
                .userAgent(Utils.USER_AGENT)
                .timeout(JSOUP_TIMEOUT)
                .get()

            val nodes = document.select("link[type*=\"rss\"], link[type*=\"atom\"]")

            for (node in nodes) {
                if (node.hasAttr("href")) {
                    return node.attr("abs:href")
                }
            }

            return null
        }
    }

    override fun blurBehind(): Boolean = true

    override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_EXPANDED

    private companion object {
        const val TAG = "BriefingConfigurator"
        const val DEBOUNCE_DELAY = 300L
        const val JSOUP_TIMEOUT = 5000
    }
}
