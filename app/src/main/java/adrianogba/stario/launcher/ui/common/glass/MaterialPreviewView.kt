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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.dp

/**
 * The Material counterpart to [GlassPreviewView].
 *
 * Deliberately not the same scene. Glass needs busy content behind it to have
 * anything to refract, while Material is the opposite idea: flat tonal ground
 * with an opaque card on top, lifted by a shadow rather than by anything
 * optical. Showing each style on the ground it is designed for is what makes
 * the two chips read as different choices.
 */
class MaterialPreviewView
@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AbstractComposeView(context, attrs) {

    private val topColor = mutableIntStateOf(Color.Gray.value.toInt())
    private val middleColor = mutableIntStateOf(Color.Gray.value.toInt())
    private val bottomColor = mutableIntStateOf(Color.Gray.value.toInt())
    private val surfaceColor = mutableIntStateOf(Color.White.value.toInt())

    fun setBackdropColors(top: Int, middle: Int, bottom: Int) {
        topColor.intValue = top
        middleColor.intValue = middle
        bottomColor.intValue = bottom
    }

    fun setSurfaceColor(color: Int) {
        surfaceColor.intValue = color
    }

    @Composable
    override fun Content() {
        val top = Color(topColor.intValue)
        val middle = Color(middleColor.intValue)
        val bottom = Color(bottomColor.intValue)
        val surface = Color(surfaceColor.intValue)

        val shape = RoundedCornerShape(18f.dp)
        val pill = RoundedCornerShape(50)

        Box(Modifier.fillMaxSize().background(surface)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 22.dp)
                    .shadow(3f.dp, shape)
                    .background(top, shape)
            ) {
                // Two tonal pills sitting on the card, which is how Material
                // separates elements: by colour role rather than by depth or
                // translucency
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.size(34.dp, 8.dp).background(middle, pill))
                    Box(Modifier.size(18.dp, 8.dp).background(bottom, pill))
                }
            }
        }
    }
}
