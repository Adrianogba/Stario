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

import android.annotation.SuppressLint
import android.text.InputType
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.apps.CategoryManager
import adrianogba.stario.launcher.apps.LauncherApplication
import adrianogba.stario.launcher.apps.ProfileManager
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.dialogs.ActionDialog
import adrianogba.stario.launcher.ui.keyboard.InlineAutocompleteEditText

class ApplicationCustomizationDialog(
    activity: ThemedActivity,
    private val application: LauncherApplication
) : ActionDialog(activity) {

    private val categoryManager: CategoryManager = CategoryManager.getInstance()

    private var category: InlineAutocompleteEditText? = null
    private var label: EditText? = null

    init {
        setOnDismissListener {
            val manager = ProfileManager.getInstance()

            label?.text?.let {
                manager.updateLabel(application.info.packageName, it.toString())
            }

            val newCategoryName = category?.text

            if (newCategoryName != null &&
                newCategoryName.toString() != categoryManager.getCategoryName(application.category)
            ) {
                categoryManager.updateCategory(
                    application, categoryManager.addCustomCategory(newCategoryName.toString())
                )
                manager.notifyUpdate(application.info.packageName)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun inflateContent(inflater: LayoutInflater): View {
        val root = inflater.inflate(R.layout.pop_up_customize, null)

        val icons = root.findViewById<RecyclerView>(R.id.icons)
        icons.layoutManager =
            LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        icons.adapter = IconsRecyclerAdapter(activity, application) { dismiss() }

        val labelWarning = root.findViewById<View>(R.id.label_warning)

        val label = root.findViewById<EditText>(R.id.label)
        this.label = label

        label.setText(application.label)
        prepareInput(label)
        label.doAfterTextChanged { editable ->
            setWarningVisible(root, labelWarning, editable.isNullOrEmpty())
        }

        val categoryWarning = root.findViewById<View>(R.id.category_warning)

        val category = root.findViewById<InlineAutocompleteEditText>(R.id.category)
        this.category = category

        category.setText(categoryManager.getCategoryName(application.category))
        prepareInput(category)
        category.setAutocompleteProvider { input ->
            val suggestion = categoryManager.getSuggestion(input) ?: return@setAutocompleteProvider null

            suggestion.substring(
                suggestion.lowercase().indexOf(input.lowercase()) + input.length
            )
        }
        category.doAfterTextChanged { editable ->
            if (editable != null) {
                setWarningVisible(
                    root, categoryWarning,
                    categoryManager.getIdentifier(editable.toString()) == null
                )
            }
        }

        root.findViewById<View>(R.id.reset).setOnClickListener {
            val applicationLabel =
                application.info.loadLabel(activity.packageManager).toString()

            label.setText(applicationLabel)
            label.setSelection(applicationLabel.length)
        }

        return root
    }

    private fun prepareInput(editText: EditText) {
        editText.isFocusable = true
        editText.isFocusableInTouchMode = true
        editText.showSoftInputOnFocus = true
        editText.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
    }

    private fun setWarningVisible(root: View, warning: View, visible: Boolean) {
        val target = if (visible) View.VISIBLE else View.GONE

        if (warning.visibility == target) {
            return
        }

        TransitionManager.beginDelayedTransition(root.rootView as ViewGroup, ChangeBounds())

        warning.visibility = target
    }

    override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_EXPANDED

    override fun blurBehind(): Boolean = true
}
