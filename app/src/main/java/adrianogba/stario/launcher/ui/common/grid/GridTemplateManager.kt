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

package adrianogba.stario.launcher.ui.common.grid

import android.content.Context
import android.util.Log
import androidx.annotation.RawRes
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import adrianogba.stario.launcher.Stario
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.utils.Utils
import java.io.InputStreamReader

class GridTemplateManager(
    context: Stario,
    identifier: String,
    @RawRes templateId: Int
) {
    private val templateCache = HashMap<String, GridTemplate>()
    private val prefs = context.getSharedPreferences(
        Entry.GRID_TEMPLATE_MANAGER.toSubPreference(identifier), Context.MODE_PRIVATE
    )

    init {
        if (templateId == 0) {
            Log.w(TAG, "GridTemplateManager: a template file has not been provided.")
        } else {
            try {
                val inputStream = context.resources.openRawResource(templateId)

                val list: List<GridTemplate>? = Utils.getGsonInstance().fromJson(
                    InputStreamReader(inputStream),
                    object : TypeToken<List<GridTemplate>>() {}.type
                )

                if (list != null) {
                    for (template in list) {
                        template.processItems()
                        templateCache[template.getDimensionsKey()] = template
                    }
                }
            } catch (exception: Exception) {
                Log.e(TAG, "loadTemplates: $exception")
            }
        }
    }

    fun getLayoutForSize(
        cols: Int, rows: Int
    ): MutableMap<String, DynamicGridLayout.ItemLayoutData> {
        val layout = HashMap<String, DynamicGridLayout.ItemLayoutData>()
        val key = cols.toString() + "x" + rows

        val template = templateCache[key]
        if (template != null) {
            layout.putAll(template.getItemMap())
        }

        val savedJson = prefs.getString("state_$key", null)
        if (savedJson != null) {
            val type = object : TypeToken<Map<String,
                    DynamicGridLayout.ItemLayoutData>>() {}.type

            val savedMap: Map<String, DynamicGridLayout.ItemLayoutData> =
                Utils.getGsonInstance().fromJson(savedJson, type)
            layout.putAll(savedMap)
        }

        return layout
    }

    fun saveUserLayout(
        cols: Int, rows: Int, map: Map<String, DynamicGridLayout.ItemLayoutData>
    ) {
        prefs.edit().putString(
            "state_" + cols + "x" + rows, Utils.getGsonInstance().toJson(map)
        ).apply()
    }

    class GridTemplate {
        @Transient
        private var itemMap: MutableMap<String, DynamicGridLayout.ItemLayoutData> = HashMap()

        @JvmField
        @SerializedName("items")
        var itemList: MutableList<DynamicGridLayout.ItemLayoutData>? = ArrayList()

        @JvmField
        @SerializedName("cols")
        var cols: Int = 0

        @JvmField
        @SerializedName("rows")
        var rows: Int = 0

        fun processItems() {
            itemMap = HashMap()

            // Gson can write a null here straight from the JSON, which is why
            // the original guarded it.
            for (item in itemList.orEmpty()) {
                // An entry with no id could never be matched to a view, so it
                // is dropped rather than stored under a null key.
                itemMap[item.id ?: continue] = item
            }
        }

        fun getItemMap(): Map<String, DynamicGridLayout.ItemLayoutData> {
            if (itemMap.isEmpty() && !itemList.isNullOrEmpty()) {
                processItems()
            }

            return itemMap
        }

        fun getDimensionsKey(): String = cols.toString() + "x" + rows
    }

    private companion object {
        private const val TAG = "GridTemplateManager"
    }
}
