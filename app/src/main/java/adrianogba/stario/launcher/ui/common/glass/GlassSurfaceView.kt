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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import adrianogba.stario.launcher.R
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow

/**
 * A liquid glass surface, meant to sit behind a launcher surface's content in
 * place of a flat background.
 *
 * It samples the wallpaper at its own position on screen, so what refracts
 * through it is what is actually behind it rather than a stand-in. When the
 * wallpaper is unavailable, because the permission was refused, it draws
 * nothing at all and whatever background the host view already has shows
 * through unchanged.
 */
class GlassSurfaceView
@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AbstractComposeView(context, attrs) {

    private val wallpaper = mutableStateOf<ImageBitmap?>(null)
    private val cornerRadius = mutableFloatStateOf(DEFAULT_CORNER_RADIUS_DP)
    private val offsetX = mutableIntStateOf(0)
    private val offsetY = mutableIntStateOf(0)

    private val location = IntArray(2)

    init {
        if (attrs != null) {
            val array = context.obtainStyledAttributes(attrs, R.styleable.GlassSurfaceView)

            cornerRadius.floatValue = array.getFloat(
                R.styleable.GlassSurfaceView_glassCornerRadius, DEFAULT_CORNER_RADIUS_DP
            )

            array.recycle()
        }
    }

    /**
     * Corner radius in dp. Match it to whatever the host surface uses, or the
     * glass rim will not follow the surface edge.
     */
    fun setCornerRadius(radius: Float) {
        cornerRadius.floatValue = radius
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        wallpaper.value = WallpaperSource.get(context)

        addOnLayoutChangeListener(positionListener)
        updatePosition()
    }

    override fun onDetachedFromWindow() {
        removeOnLayoutChangeListener(positionListener)

        super.onDetachedFromWindow()
    }

    // AbstractComposeView makes onLayout final, so the position is picked up
    // from a layout change listener instead.
    private val positionListener =
        OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updatePosition() }

    private fun updatePosition() {
        getLocationOnScreen(location)

        offsetX.intValue = location[0]
        offsetY.intValue = location[1]
    }

    @Composable
    override fun Content() {
        val image = wallpaper.value ?: return

        val x = offsetX.intValue
        val y = offsetY.intValue

        val backdrop = rememberCanvasBackdrop { drawWallpaper(image, x, y) }

        Box(
            Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(cornerRadius.floatValue.dp) },
                    effects = {
                        blur(2f.dp.toPx())
                        lens(
                            20f.dp.toPx(),
                            40f.dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = true
                        )
                    },
                    highlight = { Highlight.Default },
                    innerShadow = { InnerShadow.Default }
                )
        )
    }

    /**
     * Draws the wallpaper scaled to cover the screen and shifted so the part
     * landing under this view is the part that would be behind it. Without the
     * shift every surface would refract the wallpaper's top left corner.
     */
    private fun DrawScope.drawWallpaper(image: ImageBitmap, offsetX: Int, offsetY: Int) {
        val metrics = resources.displayMetrics

        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(-offsetX, -offsetY),
            dstSize = IntSize(metrics.widthPixels, metrics.heightPixels)
        )
    }

    private companion object {
        const val DEFAULT_CORNER_RADIUS_DP = 30f
    }
}
