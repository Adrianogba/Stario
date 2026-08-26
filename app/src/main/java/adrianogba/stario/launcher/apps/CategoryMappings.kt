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

package adrianogba.stario.launcher.apps

import android.content.Context
import android.content.SharedPreferences
import adrianogba.stario.launcher.Stario
import adrianogba.stario.launcher.exceptions.NoExistingInstanceException
import adrianogba.stario.launcher.preferences.Entry

class CategoryMappings private constructor(
    private val provider: SharedPreferencesProvider
) {

    private fun interface SharedPreferencesProvider {
        fun provide(name: String): SharedPreferences
    }

    /**
     * updatePermutation is public rather than package-private: Category calls it
     * and Kotlin has no package visibility.
     */
    abstract class Comparator<T> : java.util.Comparator<T> {
        abstract fun updatePermutation()
    }

    private class ApplicationComparator(
        provider: SharedPreferencesProvider,
        private val category: Category
    ) : Comparator<LauncherApplication>() {

        private val indexCache = HashMap<String, Int>()
        private val categoryMap: SharedPreferences =
            provider.provide(category.identifier.toString())

        init {
            for ((key, value) in categoryMap.all) {
                if (value is Int) {
                    indexCache[key] = value
                }
            }
        }

        private fun getApplicationKey(application: LauncherApplication): String {
            return if (application.getProfile() == ProfileManager.getOwner()) {
                application.info.packageName
            } else {
                application.info.packageName + ":" + application.getProfile().hashCode()
            }
        }

        override fun compare(a: LauncherApplication, b: LauncherApplication): Int {
            val aIndex = indexCache[getApplicationKey(a)]
            val bIndex = indexCache[getApplicationKey(b)]

            if (aIndex != null && bIndex != null) {
                return aIndex.compareTo(bIndex)
            }

            return a.compareTo(b)
        }

        override fun updatePermutation() {
            val applications = category.getAll()

            val editor = categoryMap.edit()
            editor.clear()
            indexCache.clear()

            for (index in applications.indices) {
                val key = getApplicationKey(applications[index])

                editor.putInt(key, index)
                indexCache[key] = index
            }

            editor.apply()
        }
    }

    private class MapComparator(provider: SharedPreferencesProvider) : Comparator<Category>() {

        private val indexCache = HashMap<String, Int>()
        private val categoryMap: SharedPreferences = provider.provide("CATEGORIES")

        init {
            for ((key, value) in categoryMap.all) {
                if (value is Int) {
                    indexCache[key] = value
                }
            }
        }

        override fun compare(a: Category, b: Category): Int {
            val aIndex = indexCache[a.identifier.toString()]
            val bIndex = indexCache[b.identifier.toString()]

            if (aIndex != null && bIndex != null) {
                return aIndex.compareTo(bIndex)
            }

            return a.identifier.compareTo(b.identifier)
        }

        override fun updatePermutation() {
            val categories = CategoryManager.getInstance().getAll()

            val editor = categoryMap.edit()
            editor.clear()
            indexCache.clear()

            for (index in categories.indices) {
                val key = categories[index].identifier.toString()

                editor.putInt(key, index)
                indexCache[key] = index
            }

            editor.apply()
        }
    }

    companion object {
        private var instance: CategoryMappings? = null

        @JvmStatic
        fun from(stario: Stario) {
            if (instance == null) {
                instance = CategoryMappings({ name ->
                    stario.getSharedPreferences(
                        Entry.CATEGORY_MAP.toSubPreference(name), Context.MODE_PRIVATE
                    )
                })
            }
        }

        /**
         * @return A comparator for sorting [Category] objects.
         * @throws NoExistingInstanceException If the singleton instance has not been initialized.
         */
        @JvmStatic
        @Throws(NoExistingInstanceException::class)
        fun getCategoryComparator(): Comparator<Category> {
            val instance = instance
                ?: throw NoExistingInstanceException(CategoryMappings::class.java)

            return MapComparator(instance.provider)
        }

        /**
         * @param category The category for which to retrieve the application comparator.
         * @return A comparator for sorting [LauncherApplication] objects.
         * @throws NoExistingInstanceException If the singleton instance has not been initialized.
         */
        @JvmStatic
        fun getCategoryApplicationComparator(category: Category): Comparator<LauncherApplication> {
            val instance = instance
                ?: throw NoExistingInstanceException(CategoryMappings::class.java)

            return ApplicationComparator(instance.provider, category)
        }
    }
}
