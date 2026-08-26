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

package adrianogba.stario.launcher.sheet.briefing.rss

import android.util.Log
import com.prof18.rssparser.RssParser
import com.prof18.rssparser.RssParserBuilder
import com.prof18.rssparser.model.RssChannel
import com.prof18.rssparser.model.RssItem
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

object RSSHelper {
    private const val TAG = "RSSHelper"

    private var reader: RssParser? = null

    private fun reader(): RssParser =
        reader ?: RssParserBuilder().build().also { reader = it }

    @JvmStatic
    @OptIn(DelicateCoroutinesApi::class)
    fun futureParse(url: String): CompletableFuture<RssChannel> {
        val parser = reader()

        return GlobalScope.future { parser.getRssChannel(url) }
    }

    @JvmStatic
    fun parse(url: String): List<RssItem>? {
        try {
            return futureParse(url).get().items
        } catch (exception: Exception) {
            Log.e(TAG, "parse: ", exception)
        }

        return null
    }
}
