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

package adrianogba.stario.launcher.ui.widgets

import adrianogba.stario.launcher.sheet.widgets.WidgetSize

// Members are public rather than package-private: Kotlin has no package
// visibility, and internal would mangle the JVM names the Java callers in
// this package still use.
class WidgetMap(private var columns: Int) {
    private val set = HashSet<Cell>()

    fun setColumnCount(columns: Int) {
        this.columns = columns
    }

    fun add(origin: Cell, size: WidgetSize) {
        for (row in origin.row until origin.row + size.height) {
            for (column in origin.column until origin.column + size.width) {
                set.add(Cell(row, column))
            }
        }
    }

    fun getAvailableOrigin(size: WidgetSize): Cell {
        var column = 0
        var row = 0

        var testedCell: Cell

        do {
            testedCell = Cell(row, column)

            column += 1

            if (column >= columns) {
                column = 0
                row++
            }
        } while (!checkFreeSpace(testedCell, size))

        return testedCell
    }

    fun checkFreeSpace(origin: Cell, size: WidgetSize): Boolean {
        if (origin.column + size.width > columns) {
            return false
        }

        for (row in origin.row until origin.row + size.height) {
            for (column in origin.column until origin.column + size.width) {
                if (set.contains(Cell(row, column))) {
                    return false
                }
            }
        }

        return true
    }

    fun clear() {
        set.clear()
    }

    class Cell(@JvmField val row: Int, @JvmField val column: Int) {

        override fun hashCode(): Int {
            var result = row
            result = 31 * result + column

            return result
        }

        override fun equals(other: Any?): Boolean {
            return other is Cell && other.row == row && other.column == column
        }
    }
}
