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

package adrianogba.stario.launcher.preferences

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import adrianogba.stario.launcher.R

/**
 * The languages offered in settings.
 *
 * Adding one is two lines: an entry here with its tag, and the matching
 * `<locale>` in res/strings/xml/locales_config.xml so the system language
 * picker lists it too. The strings themselves go in a values-b+<tag> folder.
 *
 * SYSTEM carries an empty tag, which is how AppCompat spells "no override,
 * follow the phone".
 */
enum class Language(
    val tag: String,
    @param:StringRes val displayName: Int
) {
    SYSTEM("", R.string.language_system),
    ENGLISH("en", R.string.language_english);

    companion object {
        @JvmStatic
        fun current(): Language {
            val locales = AppCompatDelegate.getApplicationLocales()

            if (locales.isEmpty) {
                return SYSTEM
            }

            val language = locales.get(0)?.language ?: return SYSTEM

            return entries.firstOrNull { it.tag.isNotEmpty() && it.tag == language } ?: SYSTEM
        }

        @JvmStatic
        fun apply(language: Language) {
            AppCompatDelegate.setApplicationLocales(
                if (language.tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(language.tag)
            )
        }
    }
}
