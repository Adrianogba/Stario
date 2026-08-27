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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill

/**
 * The scene both surface style previews sit on: a gradient with three discs
 * over it, in the current theme's container colours.
 *
 * Shared so the two chips are a fair comparison. Same content behind both, one
 * with a flat Material card on top and one with a pane of glass, which is the
 * only way to see what the choice actually changes.
 *
 * The discs matter. A gradient alone is too smooth to refract visibly, since a
 * lens only shows itself where there are edges to bend.
 */
internal fun DrawScope.drawSurfacePreviewBackdrop(top: Color, middle: Color, bottom: Color) {
    drawRect(
        brush = Brush.linearGradient(
            0f to top,
            0.5f to middle,
            1f to bottom,
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        ),
        style = Fill
    )

    val radius = size.minDimension * 0.42f

    drawCircle(bottom, radius, Offset(size.width * 0.18f, size.height * 0.22f))
    drawCircle(top, radius * 0.8f, Offset(size.width * 0.82f, size.height * 0.34f))
    drawCircle(middle, radius * 0.9f, Offset(size.width * 0.5f, size.height * 0.92f))
}
