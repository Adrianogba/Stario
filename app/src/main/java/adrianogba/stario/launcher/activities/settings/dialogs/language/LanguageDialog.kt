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

package adrianogba.stario.launcher.activities.settings.dialogs.language

import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.divider.MaterialDividerItemDecoration
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.dialogs.ActionDialog
import adrianogba.stario.launcher.ui.recyclers.DividerItemDecorator

class LanguageDialog(activity: ThemedActivity) : ActionDialog(activity) {

    private var listener: OnLanguageSelected? = null
    private var changed = false

    override fun inflateContent(inflater: LayoutInflater): View {
        val root = inflater.inflate(R.layout.pop_up_language, null)
        val recycler = root.findViewById<RecyclerView>(R.id.recycler)

        recycler.layoutManager = LinearLayoutManager(
            activity, LinearLayoutManager.VERTICAL, false
        )
        recycler.addItemDecoration(
            DividerItemDecorator(activity, MaterialDividerItemDecoration.VERTICAL)
        )
        recycler.adapter = LanguageRecyclerAdapter(activity) { picked ->
            changed = picked

            dismiss()
        }

        return root
    }

    override fun dismiss() {
        super.dismiss()

        listener?.onSelected(changed)
        changed = false
    }

    // Not setOnDismissListener: Dialog already has one taking a
    // DialogInterface, and the two overloads are ambiguous from Java.
    fun setOnLanguageSelected(listener: OnLanguageSelected?) {
        this.listener = listener
    }

    override fun blurBehind(): Boolean = true

    override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_EXPANDED

    fun interface OnLanguageSelected {
        fun onSelected(stateChanged: Boolean)
    }
}
