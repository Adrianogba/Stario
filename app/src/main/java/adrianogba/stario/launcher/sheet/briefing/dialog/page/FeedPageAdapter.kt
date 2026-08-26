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

package adrianogba.stario.launcher.sheet.briefing.dialog.page

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.prof18.rssparser.model.RssItem
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.Stario
import adrianogba.stario.launcher.preferences.Vibrations
import adrianogba.stario.launcher.ui.common.text.ClickableSpanTextView
import adrianogba.stario.launcher.ui.utils.animation.Animation
import adrianogba.stario.launcher.utils.Utils
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Locale

// package private in Java, and every caller is Kotlin now
internal class FeedPageAdapter(
    private val context: Stario
) : RecyclerView.Adapter<FeedPageAdapter.ViewHolder>() {

    private var items: List<RssItem> = Collections.synchronizedList(ArrayList())

    @Volatile
    private var lastUpdate: Long = -1

    fun update(items: List<RssItem>) {
        val filteredList = ArrayList<RssItem>()

        for (item in items) {
            val title = item.title

            if (title != null && !title.isBlank()) {
                filteredList.add(item)
            }
        }

        if (filteredList.isEmpty()) {
            return
        }

        val diffResult = DiffUtil.calculateDiff(RssItemDiffUtil(this.items, filteredList))

        this.items = Collections.synchronizedList(filteredList)

        diffResult.dispatchUpdatesTo(this)

        lastUpdate = System.currentTimeMillis()
    }

    // the Java version also allowed for a null item list, which could never happen
    fun shouldUpdate(): Boolean =
        System.currentTimeMillis() - lastUpdate > UPDATE_TIME_THRESHOLD &&
                Utils.isNetworkAvailable(context)

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener {

        val display: ImageView = itemView.findViewById(R.id.display)
        val representative: ViewGroup = itemView.findViewById(R.id.representative)
        val title: TextView = itemView.findViewById(R.id.title)
        val description: ClickableSpanTextView = itemView.findViewById(R.id.description)
        val timestamp: TextView = itemView.findViewById(R.id.timestamp)
        val author: TextView = itemView.findViewById(R.id.author)
        val category: TextView = itemView.findViewById(R.id.category)

        init {
            itemView.clipToOutline = true
            itemView.setOnClickListener(this)

            description.setOnSpanClickListener { view, span ->
                Vibrations.getInstance().vibrate()
                span.onClick(view)
            }
        }

        override fun onClick(view: View) {
            val index = bindingAdapterPosition
            if (index == RecyclerView.NO_POSITION) {
                return
            }

            val item = items[index]
            Vibrations.getInstance().vibrate()

            val link = item.link
            val guid = item.guid

            val intent = when {
                link != null -> Intent(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                guid != null -> Intent(Intent(Intent.ACTION_VIEW, Uri.parse(guid)))
                else -> null
            }

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val item = items[position]

        viewHolder.title.visibility = View.GONE
        viewHolder.author.visibility = View.GONE
        viewHolder.category.visibility = View.GONE
        viewHolder.description.visibility = View.GONE
        viewHolder.timestamp.visibility = View.GONE
        viewHolder.display.alpha = 0f

        val image = item.image
        if (image != null) {
            viewHolder.representative.visibility = View.VISIBLE

            Glide.with(context)
                .load(image)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        exception: GlideException?, model: Any?,
                        target: Target<Drawable>, isFirstResource: Boolean
                    ): Boolean {
                        viewHolder.representative.visibility = View.GONE

                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable, model: Any, target: Target<Drawable>,
                        dataSource: DataSource, isFirstResource: Boolean
                    ): Boolean {
                        viewHolder.representative.visibility = View.VISIBLE
                        viewHolder.display.animate()
                            .alpha(1f)
                            .setDuration(Animation.MEDIUM.duration.toLong())

                        if (item.categories.isNotEmpty()) {
                            val text = cleanHtml(item.categories[0])

                            if (text.toString().isNotEmpty()) {
                                viewHolder.category.text = text
                                viewHolder.category.visibility = View.VISIBLE
                            }
                        }

                        return false
                    }
                })
                .into(viewHolder.display)
        } else {
            viewHolder.representative.visibility = View.GONE
        }

        val rawTitle = item.title
        if (!rawTitle.isNullOrEmpty()) {
            val title = cleanHtml(rawTitle)

            if (title.toString().isNotEmpty()) {
                viewHolder.title.text = title
                viewHolder.title.visibility = View.VISIBLE
            }
        }

        val rawDescription = item.description
        val rawContent = item.content

        val content = when {
            !rawDescription.isNullOrEmpty() -> rawDescription
            !rawContent.isNullOrEmpty() -> rawContent
            else -> null
        }

        if (content != null) {
            val description = cleanHtml(content)

            if (description.toString().isNotEmpty()) {
                viewHolder.description.text = description
                viewHolder.description.visibility = View.VISIBLE
            }
        }

        val rawAuthor = item.author
        if (!rawAuthor.isNullOrEmpty()) {
            val author = cleanHtml(rawAuthor)

            if (author.toString().isNotEmpty()) {
                viewHolder.author.text = author
                viewHolder.author.visibility = View.VISIBLE
            }
        }

        val pubDate = item.pubDate
        if (!pubDate.isNullOrEmpty()) {
            try {
                val date = Utils.parseDate(pubDate)
                val output = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault())

                if (date != null) {
                    viewHolder.timestamp.text = output.format(date)
                } else {
                    viewHolder.timestamp.text = pubDate
                }
            } catch (exception: Exception) {
                viewHolder.timestamp.text = pubDate
            } finally {
                viewHolder.timestamp.visibility = View.VISIBLE
            }
        }
    }

    // every call site null checks first, so the Java null guard was unreachable
    private fun cleanHtml(html: String): Spanned = HtmlCompat.fromHtml(
        Jsoup.clean(html, CONTENT_SAFELIST),
        HtmlCompat.FROM_HTML_MODE_LEGACY
    )

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(container.context)

        return ViewHolder(inflater.inflate(R.layout.article, container, false))
    }

    override fun getItemCount(): Int = items.size

    private companion object {
        private val CONTENT_SAFELIST: Safelist = Safelist()
            .addTags(
                "p", "br", "b", "strong", "i", "em", "u", "strike",
                "del", "a", "ul", "ol", "li", "h1", "h2", "h3", "h4"
            )
            .addAttributes("a", "href")

        private const val UPDATE_TIME_THRESHOLD = 900_000L
    }
}
