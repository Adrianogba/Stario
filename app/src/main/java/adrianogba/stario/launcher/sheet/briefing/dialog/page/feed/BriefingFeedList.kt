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

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.util.Log
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.themes.ThemedActivity
import org.json.JSONArray

class BriefingFeedList private constructor(activity: ThemedActivity) {
    private val listeners = ArrayList<FeedListener>()
    private val items = ArrayList<Feed>()

    private val state: SharedPreferences =
        activity.applicationContext.getSharedPreferences(Entry.BRIEFING)

    init {
        load(state.getString(FEEDS_KEY, null))
    }

    fun get(position: Int): Feed = items[position]

    fun size(): Int = items.size

    private fun load(feedsSerial: String?) {
        if (feedsSerial == null) {
            return
        }

        try {
            val array = JSONArray(feedsSerial)

            for (index in 0 until array.length()) {
                val feed = Feed.deserialize(array.get(index) as String)

                if (feed != null && !items.contains(feed)) {
                    items.add(feed)

                    for (listener in listeners) {
                        listener.onInserted(size() - 1)
                    }
                }
            }
        } catch (exception: Exception) {
            Log.e("BriefingFeedList", "Error loading feeds.", exception)

            state.edit()
                .remove(FEEDS_KEY)
                .apply()
        }
    }

    fun add(feed: Feed?): Boolean {
        if (feed == null || items.contains(feed)) {
            return false
        }

        items.add(feed)
        serialize()

        for (listener in listeners) {
            listener.onInserted(size() - 1)
        }

        return true
    }

    fun updateName(feed: Feed, name: String?) {
        updateName(items.indexOf(feed), name)
    }

    fun updateName(position: Int, name: String?) {
        if (position < 0 || position >= items.size || name == null) {
            return
        }

        items[position].title = name
        serialize()

        for (listener in listeners) {
            listener.onUpdated(position)
        }
    }

    fun remove(feed: Feed) {
        remove(items.indexOf(feed))
    }

    fun remove(position: Int) {
        if (position < 0 || position >= items.size) {
            return
        }

        items.removeAt(position)
        serialize()

        for (listener in listeners) {
            listener.onRemoved(position)
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun serialize() {
        val serials = ArrayList<String?>()

        for (item in items) {
            serials.add(item.serialize())
        }

        state.edit().putString(FEEDS_KEY, JSONArray(serials).toString()).commit()
    }

    fun addOnFeedUpdateListener(listener: FeedListener?) {
        if (listener != null) {
            listeners.add(listener)
        }
    }

    fun removeOnFeedUpdateListener(listener: FeedListener?) {
        if (listener != null) {
            listeners.remove(listener)
        }
    }

    interface FeedListener {
        fun onInserted(index: Int) {
        }

        fun onUpdated(index: Int) {
        }

        fun onRemoved(index: Int) {
        }
    }

    companion object {
        private const val FEEDS_KEY = "com.stario.FEEDS"

        private var instance: BriefingFeedList? = null

        @JvmStatic
        fun from(activity: ThemedActivity): BriefingFeedList {
            return instance ?: BriefingFeedList(activity).also { instance = it }
        }

        @JvmStatic
        fun getInstance(): BriefingFeedList {
            return instance ?: throw RuntimeException("BriefingFeedList not initialized.")
        }
    }
}
