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

import adrianogba.stario.launcher.sheet.behavior.bottom.BottomSheetDialog
import adrianogba.stario.launcher.sheet.behavior.left.LeftSheetDialog
import adrianogba.stario.launcher.sheet.behavior.right.RightSheetDialog
import adrianogba.stario.launcher.sheet.behavior.top.TopSheetDialog
import adrianogba.stario.launcher.themes.ThemedActivity

object SheetDialogFactory {
    @JvmStatic
    fun forType(type: SheetType, activity: ThemedActivity?, theme: Int): SheetDialog? {
        return when (type) {
            SheetType.TOP_SHEET -> TopSheetDialog(activity, theme)
            SheetType.RIGHT_SHEET -> RightSheetDialog(activity, theme)
            SheetType.BOTTOM_SHEET -> BottomSheetDialog(activity, theme)
            SheetType.LEFT_SHEET -> LeftSheetDialog(activity, theme)
            else -> null
        }
    }
}
