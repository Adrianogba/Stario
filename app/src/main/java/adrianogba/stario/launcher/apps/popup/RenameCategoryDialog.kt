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

package adrianogba.stario.launcher.apps.popup

import android.text.InputType
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomsheet.BottomSheetBehavior
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.apps.CategoryManager
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.dialogs.ActionDialog
import adrianogba.stario.launcher.ui.keyboard.extract.ExtractEditText
import java.util.UUID

class RenameCategoryDialog(
    activity: ThemedActivity,
    private val categoryIdentifier: UUID
) : ActionDialog(activity) {

    private val categoryManager: CategoryManager = CategoryManager.getInstance()

    private lateinit var editText: ExtractEditText

    init {
        setOnDismissListener {
            val name = editText.text

            if (name != null) {
                categoryManager.updateCategory(categoryIdentifier, name.toString())
            }
        }
    }

    override fun inflateContent(inflater: LayoutInflater): View {
        val root = inflater.inflate(R.layout.pop_up_category, null)

        // getCategoryName returns null for a category with neither a custom name
        // nor a default resource. The reset button below used to dereference it,
        // so that case threw. Empty string reads the same and does not.
        val initialName = categoryManager.getCategoryName(categoryIdentifier).orEmpty()

        val warning = root.findViewById<View>(R.id.warning)

        val editText = root.findViewById<ExtractEditText>(R.id.category)
        this.editText = editText

        editText.setText(initialName)
        editText.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD

        editText.doAfterTextChanged { editable ->
            val text = editable ?: return@doAfterTextChanged

            val taken = !text.toString().equals(initialName, ignoreCase = true) &&
                    categoryManager.getIdentifier(text.toString(), true) != null

            val target = if (taken) View.VISIBLE else View.GONE

            if (warning.visibility != target) {
                TransitionManager.beginDelayedTransition(
                    root.rootView as ViewGroup, ChangeBounds()
                )

                warning.visibility = target
            }
        }

        root.findViewById<View>(R.id.reset).setOnClickListener {
            editText.setText(initialName)
            editText.setSelection(initialName.length)
        }

        return root
    }

    override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_EXPANDED

    override fun blurBehind(): Boolean = true
}
