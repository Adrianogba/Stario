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

package adrianogba.stario.launcher.sheet.behavior.right

import android.view.View
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.sheet.SheetCoordinator
import adrianogba.stario.launcher.sheet.SheetDialog
import adrianogba.stario.launcher.sheet.SheetType
import adrianogba.stario.launcher.sheet.behavior.SheetBehavior
import adrianogba.stario.launcher.themes.ThemedActivity

class RightSheetDialog(activity: ThemedActivity, themeResId: Int) : SheetDialog(activity, themeResId) {
    private var container: SheetCoordinator? = null

    override fun getContainer(): SheetCoordinator {
        if (container == null) {
            container = View.inflate(context, R.layout.right_sheet_dialog, null) as SheetCoordinator

            sheet = container!!.findViewById(R.id.design_right_sheet)
            behavior = SheetBehavior.from(sheet!!)
        }

        return container!!
    }

    override fun getType(): SheetType = SheetType.RIGHT_SHEET
}
