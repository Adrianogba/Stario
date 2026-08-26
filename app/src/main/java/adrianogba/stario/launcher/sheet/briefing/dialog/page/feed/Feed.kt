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

package adrianogba.stario.launcher.sheet.briefing.dialog.page.feed

import android.util.Log
import org.json.JSONObject
import java.io.Serializable
import java.util.Objects

class Feed(var title: String, private val rss: String) : Serializable {

    fun getRSSLink(): String = rss

    fun serialize(): String? {
        if (rss.isEmpty()) {
            return null
        }

        return "{" +
                "\"" + FEED_TITLE + "\":\"" + title + "\"," +
                "\"" + FEED_RSS + "\":\"" + rss + "\"" +
                "}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other == null || javaClass != other.javaClass) {
            return false
        }

        return rss == (other as Feed).rss
    }

    // Deliberately kept as it was: this hashes title as well as rss, while
    // equals only compares rss. Changing it would change behaviour.
    override fun hashCode(): Int = Objects.hash(title, rss)

    companion object {
        private const val TAG = "com.stario.FeedItem"
        private const val FEED_TITLE = "com.stario.FEED_TITLE"
        private const val FEED_RSS = "com.stario.FEED_RSS"

        @JvmStatic
        fun deserialize(data: String?): Feed? {
            return try {
                val jsonObject = JSONObject(data)

                Feed(
                    jsonObject.getString(FEED_TITLE),
                    jsonObject.getString(FEED_RSS)
                )
            } catch (exception: Exception) {
                Log.e(TAG, "deserialize: Serialized object has corrupt data.")

                null
            }
        }
    }
}
