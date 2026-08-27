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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.dp
import adrianogba.stario.launcher.R
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/**
 * A pane of glass for a launcher surface that sits directly on the wallpaper.
 *
 * The wallpaper cannot be sampled, so there is no refraction here. See
 * [WallpaperSource] for the four ways that was tested. What is available is
 * better than it sounds: the launcher window is translucent and the system
 * composites the wallpaper behind it, so a translucent surface shows the real
 * wallpaper through it without reading a single pixel.
 *
 * So this draws the parts of glass that do not need sampling. A low alpha body
 * the wallpaper shows through, a bright specular rim, an inner shadow for
 * thickness, and a soft drop shadow to lift it off the wallpaper. That is the
 * floating control layer Apple's guidance describes, minus the blur.
 */
class GlassSurfaceView
@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AbstractComposeView(context, attrs) {

    private val cornerRadius = mutableFloatStateOf(DEFAULT_CORNER_RADIUS_DP)
    private val tintColor = mutableIntStateOf(Color.White.value.toInt())
    private val tintAlpha = mutableFloatStateOf(DEFAULT_ALPHA)
    private val capsule = mutableFloatStateOf(0f)

    init {
        if (attrs != null) {
            val array = context.obtainStyledAttributes(attrs, R.styleable.GlassSurfaceView)

            cornerRadius.floatValue = array.getFloat(
                R.styleable.GlassSurfaceView_glassCornerRadius, DEFAULT_CORNER_RADIUS_DP
            )
            capsule.floatValue =
                if (array.getBoolean(R.styleable.GlassSurfaceView_glassCapsule, false)) 1f else 0f

            array.recycle()
        }
    }

    /**
     * Corner radius in dp. Match it to the host surface, or the rim will not
     * follow the surface edge.
     */
    fun setCornerRadius(radius: Float) {
        cornerRadius.floatValue = radius
    }

    /**
     * Tint and how much of it. Feed this the wallpaper's own colours through
     * [WallpaperPalette] so the glass picks up the wall behind it.
     */
    @JvmOverloads
    fun setTint(color: Int, alpha: Float = DEFAULT_ALPHA) {
        tintColor.intValue = color
        tintAlpha.floatValue = alpha
    }

    @Composable
    override fun Content() {
        val tint = Color(tintColor.intValue).copy(alpha = tintAlpha.floatValue)
        val radius = cornerRadius.floatValue
        val isCapsule = capsule.floatValue == 1f

        val backdrop = remember { emptyBackdrop() }

        Box(
            Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { if (isCapsule) Capsule() else RoundedRectangle(radius.dp) },
                    effects = {},
                    highlight = { Highlight.Default },
                    shadow = {
                        Shadow(radius = 12f.dp, color = Color.Black.copy(alpha = 0.18f))
                    },
                    innerShadow = { InnerShadow.Default },
                    onDrawSurface = {
                        // Glass catches more light along its upper edge than
                        // its lower one. A flat fill of the tint reads as a
                        // translucent rectangle; the gradient is what makes the
                        // same alpha read as a solid pane with thickness.
                        drawRect(
                            Brush.linearGradient(
                                0f to tint.copy(
                                    alpha = (tint.alpha * TOP_LIGHT).coerceAtMost(1f)
                                ),
                                1f to tint.copy(alpha = tint.alpha * BOTTOM_LIGHT),
                                start = Offset.Zero,
                                end = Offset(0f, size.height)
                            )
                        )
                    }
                )
        )
    }

    private companion object {
        const val DEFAULT_CORNER_RADIUS_DP = 30f
        const val DEFAULT_ALPHA = 0.32f

        const val TOP_LIGHT = 1.35f
        const val BOTTOM_LIGHT = 0.75f
    }
}
