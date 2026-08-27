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

package adrianogba.stario.launcher.ui.common.glass

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * A [Shape] cut from the same SVG path a vector drawable is built from.
 *
 * The home screen widgets are not rectangles. The search button is a four
 * lobed squircle and the weather chip is a scalloped disc, and both of them
 * turn slowly. Glass on either one has to be that silhouette, or it stops being
 * the widget and becomes a pane sitting behind it.
 *
 * The path string comes from a string resource that the vector drawable also
 * points at, so the drawable and the glass cannot drift apart.
 *
 * @param pathData the SVG path, in viewport coordinates
 * @param viewport the drawable's declared viewport, which the path is scaled
 * out of
 * @param inset the drawable's own group translation, which the path is offset
 * by before scaling
 */
class VectorPathShape(
    private val pathData: String,
    private val viewport: Float,
    private val inset: Float = 0f
) : Shape {

    // Parsing is not free and the shape is asked for its outline on every
    // draw, so the result is kept for as long as the size holds still.
    private var cachedSize: Size? = null
    private var cachedOutline: Outline? = null

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        cachedOutline?.let { outline ->
            if (cachedSize == size) {
                return outline
            }
        }

        val path = PathParser().parsePathString(pathData).toPath()

        val matrix = Matrix()
        matrix.scale(size.width / viewport, size.height / viewport)
        matrix.translate(inset, inset)
        path.transform(matrix)

        return Outline.Generic(path).also {
            cachedSize = size
            cachedOutline = it
        }
    }

    override fun equals(other: Any?): Boolean =
        other is VectorPathShape &&
                other.pathData == pathData &&
                other.viewport == viewport &&
                other.inset == inset

    override fun hashCode(): Int =
        (pathData.hashCode() * 31 + viewport.hashCode()) * 31 + inset.hashCode()
}
