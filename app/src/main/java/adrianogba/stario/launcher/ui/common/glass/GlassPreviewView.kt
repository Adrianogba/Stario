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

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * One pane of liquid glass, drawn over a backdrop built from the current
 * theme's own colours.
 *
 * The backdrop is deliberately not the wallpaper. Reading the wallpaper needs
 * READ_MEDIA_IMAGES, and this view exists to show what the style looks like
 * before anyone has agreed to that. The refraction, blur and highlight are the
 * real ones from the library, so what you see here is what the surfaces do.
 */
class GlassPreviewView
@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AbstractComposeView(context, attrs) {

    private val topColor = mutableIntStateOf(Color.Gray.value.toInt())
    private val middleColor = mutableIntStateOf(Color.Gray.value.toInt())
    private val bottomColor = mutableIntStateOf(Color.Gray.value.toInt())

    /**
     * The three colours the backdrop gradient runs through. Pass the theme's
     * container colours so Dynamic and the twelve fixed themes all carry over.
     */
    fun setBackdropColors(top: Int, middle: Int, bottom: Int) {
        topColor.intValue = top
        middleColor.intValue = middle
        bottomColor.intValue = bottom
    }

    @Composable
    override fun Content() {
        val top = Color(topColor.intValue)
        val middle = Color(middleColor.intValue)
        val bottom = Color(bottomColor.intValue)

        val backdrop = rememberCanvasBackdrop { drawBackdropSource(top, middle, bottom) }

        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBackdrop(
                        backdrop = backdrop,
                        // lens() only accepts a corner based shape, and this
                        // matches the chip's own carbon_cornerRadius.
                        shape = { RoundedCornerShape(20f.dp) },
                        effects = {
                            blur(2f.dp.toPx())
                            lens(12f.dp.toPx(), 24f.dp.toPx())
                        },
                        highlight = { Highlight.Default }
                    )
            )
        }
    }

    /**
     * A gradient plus three discs. The gradient alone is too smooth to refract
     * visibly: a lens only shows itself where there are edges to bend, so the
     * discs are what make the effect readable at chip size.
     */
    private fun DrawScope.drawBackdropSource(top: Color, middle: Color, bottom: Color) {
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
}
