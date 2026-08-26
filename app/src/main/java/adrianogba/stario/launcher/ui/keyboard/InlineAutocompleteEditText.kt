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

package adrianogba.stario.launcher.ui.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.appcompat.widget.AppCompatEditText
import adrianogba.stario.launcher.ui.utils.UiUtils

open class InlineAutocompleteEditText : AppCompatEditText {
    private var provider: AutocompleteProvider? = null
    private var autocompleted = false

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    fun setAutocompleteProvider(provider: AutocompleteProvider?) {
        this.provider = provider
    }

    fun getAutocompleteProvider(): AutocompleteProvider? = provider

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            val text = this.text

            if (text != null && selectionEnd == text.length) {
                setSelection(text.length)
            }

            UiUtils.hideKeyboard(this)
        }

        return super.onKeyUp(keyCode, event)
    }

    @SuppressLint("SetTextI18n")
    override fun onTextChanged(
        text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int
    ) {
        val provider = this.provider

        if (provider == null || text == null) {
            if (autocompleted) {
                autocompleted = false
            }

            return
        }

        val textLength = text.length

        if (textLength > 0 && !autocompleted &&
            selectionEnd == textLength &&
            ((lengthBefore > 0 && lengthAfter > 0) || // selection was replaced
                    lengthBefore < lengthAfter) // check for insertion, not deletion
        ) {
            val autocompletion = provider.autocomplete(text.toString())

            if (autocompletion != null) {
                autocompleted = true

                super.setText(text.toString() + autocompletion)

                setSelection(textLength, textLength + autocompletion.length)
            }
        } else if (autocompleted) {
            autocompleted = false
        }
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        autocompleted = true // skip autocompletion if externally set

        super.setText(text, type)
    }

    fun interface AutocompleteProvider {
        /**
         * Returns an autocompleted suggestion for the given input string.
         *
         * If a valid autocomplete suggestion is found based on the input, the suggestion
         * returns the remaining suggestion characters as a string. If no suggestion is
         * available, this method returns `null`.
         *
         * @param input the input string to be autocompleted
         * @return the autocompleted suggestion, or `null` if no suggestion is available
         */
        fun autocomplete(input: String): String?
    }
}
