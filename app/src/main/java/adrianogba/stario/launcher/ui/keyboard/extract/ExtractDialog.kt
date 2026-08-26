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

package adrianogba.stario.launcher.ui.keyboard.extract

import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.keyboard.InlineAutocompleteEditText
import adrianogba.stario.launcher.ui.keyboard.KeyboardHeightProvider
import adrianogba.stario.launcher.ui.utils.UiUtils

class ExtractDialog(private val editText: ExtractEditText) : DialogFragment() {

    private val activity: ThemedActivity = editText.context as ThemedActivity
    private val heightProvider = KeyboardHeightProvider(activity)

    private var extractedEditText: InlineAutocompleteEditText? = null
    private var shown = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        var theme = theme

        if (theme == 0) {
            val outValue = TypedValue()

            theme = if (activity.theme.resolveAttribute(
                    com.google.android.material.R.attr.bottomSheetDialogTheme, outValue, true
                )
            ) {
                outValue.resourceId
            } else {
                com.google.android.material.R.style.Theme_Design_Light_BottomSheetDialog
            }
        }

        val dialog = ComponentDialog(activity, theme)

        dialog.setOnShowListener {
            val extracted = extractedEditText ?: return@setOnShowListener

            extracted.maxLines = editText.maxLines
            extracted.imeOptions = editText.imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            extracted.setSingleLine(editText.isSingleLine)
            extracted.hint = editText.hint

            val text = editText.text

            if (text != null) {
                extracted.setText(text)
                extracted.setSelection(text.length)
            }
        }

        return dialog
    }

    override fun onStart() {
        super.onStart()

        val window = dialog?.window

        if (window != null) {
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )

            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

            window.setWindowAnimations(R.style.ExtractedEditTextDialogAnimations)

            UiUtils.makeSysUITransparent(window)
            window.decorView.setBackgroundColor(
                activity.getAttributeData(com.google.android.material.R.attr.colorSurface)
            )
        }

        shown = false

        heightProvider.start()
    }

    override fun onStop() {
        super.onStop()

        heightProvider.close()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.extract_dialog, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        UiUtils.Notch.applyNotchMargin(view)

        view.findViewById<MaterialButton>(R.id.proceed).setOnClickListener { dismiss() }

        val extracted = view.findViewById<InlineAutocompleteEditText>(R.id.edit_text)
        extractedEditText = extracted

        extracted.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        extracted.setAutocompleteProvider(editText.getAutocompleteProvider())
        extracted.doAfterTextChanged { editable ->
            if (shown) {
                editText.setText(editable)
            }
        }

        val params = view.layoutParams as ViewGroup.MarginLayoutParams

        Measurements.addStatusBarListener { value ->
            params.topMargin = value

            view.requestLayout()
        }

        Measurements.addNavListener { value ->
            params.bottomMargin = value + heightProvider.getKeyboardHeight()

            view.requestLayout()
        }

        heightProvider.addKeyboardHeightListener { height ->
            if (height <= 0) {
                if (shown) {
                    dismiss()

                    shown = false
                }
            } else {
                shown = true
            }

            params.bottomMargin = height + Measurements.getNavHeight()

            view.requestLayout()
        }

        UiUtils.post(object : Runnable {
            override fun run() {
                if (!shown) {
                    UiUtils.showKeyboard(extracted)
                    UiUtils.postDelayed(this, 50)
                }
            }
        })
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        dismiss()
    }

    override fun dismiss() {
        // Null until onViewCreated has run. onConfigurationChanged can land
        // before that, and there is nothing to copy back when it does.
        extractedEditText?.let { editText.setText(it.text) }

        super.dismiss()
    }
}
