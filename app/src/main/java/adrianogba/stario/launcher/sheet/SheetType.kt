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

package adrianogba.stario.launcher.sheet

import android.util.Log
import android.view.View
import androidx.core.util.Pair
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.sheet.briefing.dialog.BriefingDialog
import adrianogba.stario.launcher.sheet.drawer.dialog.ApplicationsDialog
import adrianogba.stario.launcher.sheet.widgets.dialog.WidgetsDialog
import adrianogba.stario.launcher.themes.ThemedActivity

enum class SheetType(
    private val stringType: String,
    val axes: Int
) {
    TOP_SHEET("adrianogba.stario.launcher.TOP_SHEET", View.SCROLL_AXIS_VERTICAL),
    BOTTOM_SHEET("adrianogba.stario.launcher.BOTTOM_SHEET", View.SCROLL_AXIS_VERTICAL),
    LEFT_SHEET("adrianogba.stario.launcher.LEFT_SHEET", View.SCROLL_AXIS_HORIZONTAL),
    RIGHT_SHEET("adrianogba.stario.launcher.RIGHT_SHEET", View.SCROLL_AXIS_HORIZONTAL),
    UNDEFINED("adrianogba.stario.launcher.UNDEFINED", View.SCROLL_AXIS_NONE);

    override fun toString(): String = stringType

    companion object {
        private const val TAG = "SheetType"

        @JvmStatic
        fun deserialize(serial: String?): SheetType? {
            if (serial == null) {
                return null
            }

            return when (serial) {
                TOP_SHEET.stringType -> TOP_SHEET
                RIGHT_SHEET.stringType -> RIGHT_SHEET
                BOTTOM_SHEET.stringType -> BOTTOM_SHEET
                LEFT_SHEET.stringType -> LEFT_SHEET
                UNDEFINED.stringType -> UNDEFINED
                else -> null
            }
        }

        @JvmStatic
        fun getStoredSheets(
            activity: ThemedActivity
        ): List<Pair<SheetType, Class<out SheetDialogFragment>>> {
            val list = ArrayList<Pair<SheetType, Class<out SheetDialogFragment>>>()
            val preferences = activity.applicationContext.getSharedPreferences(Entry.SHEET)

            preferences.all.forEach { (string, value) ->
                try {
                    val clazz = Class.forName(string)

                    if (!SheetDialogFragment::class.java.isAssignableFrom(clazz)) {
                        Log.e(
                            TAG, "getSheets: " + string + " does not extend " +
                                    SheetDialogFragment::class.java.name
                        )
                        preferences.edit().remove(string).apply()

                        return@forEach
                    }

                    if (value !is String) {
                        Log.e(
                            TAG, "getSheets: " + string + " can only map to a " +
                                    SheetType::class.java.name + " serial String."
                        )
                        preferences.edit().remove(string).apply()

                        return@forEach
                    }

                    val type = deserialize(value)

                    if (type == null) {
                        Log.e(
                            TAG, "getSheets: " + value + " does not map to a valid " +
                                    SheetType::class.java.name + " serial String."
                        )
                        preferences.edit().remove(string).apply()

                        return@forEach
                    }

                    @Suppress("UNCHECKED_CAST")
                    list.add(Pair(type, clazz as Class<out SheetDialogFragment>))
                } catch (exception: ClassNotFoundException) {
                    Log.e(TAG, "getSheets: Could not get class $string")
                    preferences.edit().remove(string).apply()
                }
            }

            return list
        }

        @JvmStatic
        fun getSheetTypeForSheetDialogFragment(
            activity: ThemedActivity, clazz: Class<out SheetDialogFragment>
        ): SheetType? {
            val typeString = activity.applicationContext
                .getSharedPreferences(Entry.SHEET)
                .getString(clazz.name, null)

            if (typeString != null) {
                val type = deserialize(typeString)

                if (type != null) {
                    return type
                }
            }

            return getDefaultSheetTypeForSheetDialogFragment(activity, clazz, true)
        }

        @JvmStatic
        fun getDefaultSheetTypeForSheetDialogFragment(
            activity: ThemedActivity, clazz: Class<out SheetDialogFragment>
        ): SheetType? = getDefaultSheetTypeForSheetDialogFragment(activity, clazz, false)

        private fun getDefaultSheetTypeForSheetDialogFragment(
            activity: ThemedActivity,
            clazz: Class<out SheetDialogFragment>,
            writeToPreferences: Boolean
        ): SheetType? {
            val preferences = activity.applicationContext.getSharedPreferences(Entry.SHEET)

            val type = when (clazz) {
                ApplicationsDialog::class.java -> BOTTOM_SHEET
                BriefingDialog::class.java -> LEFT_SHEET
                WidgetsDialog::class.java -> RIGHT_SHEET
                else -> return null
            }

            if (writeToPreferences) {
                preferences.edit()
                    .putString(clazz.name, type.toString())
                    .apply()
            }

            return type
        }
    }
}
