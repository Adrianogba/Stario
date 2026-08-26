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

package adrianogba.stario.launcher.activities.launcher.sheets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import adrianogba.stario.launcher.activities.launcher.Launcher
import adrianogba.stario.launcher.sheet.SheetDialog
import adrianogba.stario.launcher.sheet.SheetDialogFragment

object LauncherSheets {
    const val INTENT_SHEET_CLASS_EXTRA: String =
        "adrianogba.stario.launcher.INTENT_SHEET_CLASS_EXTRA"
    const val ACTION_REMOVE_SHEET: String =
        "adrianogba.stario.launcher.ACTION_REMOVE_SHEET"
    const val ACTION_MOVE_SHEET: String =
        "adrianogba.stario.launcher.ACTION_MOVE_SHEET"
    const val ACTION_ADD_SHEET: String =
        "adrianogba.stario.launcher.ACTION_ADD_SHEET"

    @JvmStatic
    fun attach(launcher: Launcher, slideListener: SheetDialog.OnSlideListener?) {
        val controller = launcher.getSheetsController()

        controller.setSlideListener(slideListener)
        controller.addSheetDialog(launcher, SheetDialogFragment.IMPLEMENTATIONS)

        val filter = IntentFilter()
        filter.addAction(ACTION_ADD_SHEET)
        filter.addAction(ACTION_MOVE_SHEET)
        filter.addAction(ACTION_REMOVE_SHEET)

        LocalBroadcastManager.getInstance(launcher).registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val classes = getClasses(intent) ?: return

                    when (intent.action) {
                        ACTION_ADD_SHEET -> controller.addSheetDialog(launcher, classes)
                        ACTION_MOVE_SHEET -> controller.moveSheetDialog(launcher, classes)
                        ACTION_REMOVE_SHEET -> controller.removeSheetDialog(classes)
                    }
                }
            }, filter
        )
    }

    // Returns a list rather than the array the Java version built. The
    // controller has List overloads and its vararg ones only wrap into a list
    // anyway, so this drops an unchecked generic array cast for nothing.
    private fun getClasses(intent: Intent?): List<Class<out SheetDialogFragment>>? {
        if (intent == null) {
            return null
        }

        @Suppress("DEPRECATION")
        val extra = intent.getSerializableExtra(INTENT_SHEET_CLASS_EXTRA) ?: return null

        val classes = ArrayList<Class<out SheetDialogFragment>>()

        if (extra is Class<*> && SheetDialogFragment::class.java.isAssignableFrom(extra)) {
            @Suppress("UNCHECKED_CAST")
            classes.add(extra as Class<out SheetDialogFragment>)
        } else if (extra.javaClass.isArray) {
            for (element in extra as Array<*>) {
                if (element is Class<*> &&
                    SheetDialogFragment::class.java.isAssignableFrom(element)
                ) {
                    @Suppress("UNCHECKED_CAST")
                    classes.add(element as Class<out SheetDialogFragment>)
                }
            }
        }

        return classes.ifEmpty { null }
    }
}
