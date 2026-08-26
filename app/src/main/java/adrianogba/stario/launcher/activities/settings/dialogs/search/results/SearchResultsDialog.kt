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

package adrianogba.stario.launcher.activities.settings.dialogs.search.results

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.materialswitch.MaterialSwitch
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.sheet.drawer.search.recyclers.adapters.WebAdapter
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.dialogs.ActionDialog

class SearchResultsDialog(activity: ThemedActivity) : ActionDialog(activity) {

    private val preferences: SharedPreferences =
        activity.applicationContext.getSharedPreferences(Entry.SEARCH)

    private lateinit var resultsSwitch: MaterialSwitch
    private var listener: StatusListener? = null

    override fun inflateContent(inflater: LayoutInflater): View {
        val root = inflater.inflate(R.layout.pop_up_kagi, null)

        val resultsSwitch = root.findViewById<MaterialSwitch>(R.id.search_results)
        this.resultsSwitch = resultsSwitch

        resultsSwitch.setOnCheckedChangeListener { button, isChecked ->
            val key = preferences.getString(WebAdapter.KAGI_API_KEY, null)

            if (key.isNullOrEmpty()) {
                button.isChecked = false
            } else {
                listener?.onChanged(isChecked)
            }
        }

        root.findViewById<View>(R.id.search_results_container)
            .setOnClickListener { resultsSwitch.performClick() }

        val editText = root.findViewById<EditText>(R.id.edit_text)
        editText.setText(preferences.getString(WebAdapter.KAGI_API_KEY, ""))
        editText.doAfterTextChanged { content ->
            preferences.edit()
                .putString(WebAdapter.KAGI_API_KEY, content.toString())
                .apply()
        }

        root.findViewById<View>(R.id.paste).setOnClickListener {
            val clipboard =
                activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            if (clipboard.hasPrimaryClip()) {
                val clip = clipboard.primaryClip

                if (clip != null &&
                    clip.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                ) {
                    val text = clip.getItemAt(0).coerceToText(activity).toString()

                    if (text.isNotEmpty()) {
                        editText.setText(text)
                        editText.setSelection(text.length)
                    }
                }
            }
        }

        return root
    }

    override fun show() {
        super.show()

        resultsSwitch.isChecked = preferences.getBoolean(WebAdapter.SEARCH_RESULTS, false)
        resultsSwitch.jumpDrawablesToCurrentState()
    }

    fun setStatusListener(listener: StatusListener?) {
        this.listener = listener
    }

    override fun blurBehind(): Boolean = true

    override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_EXPANDED

    fun interface StatusListener {
        fun onChanged(enabled: Boolean)
    }
}
