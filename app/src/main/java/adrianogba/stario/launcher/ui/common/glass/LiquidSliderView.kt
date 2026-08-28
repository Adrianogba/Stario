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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * A slider built from the same parts as [LiquidToggleView], because iOS builds
 * them the same way.
 *
 * The track is a channel with the filled part in the accent colour, and a pane
 * of glass rides it exactly as the switch's does: round and near opaque at
 * rest, swelling sideways and thinning as it is held so the track comes through
 * it, and pulling back round when released. Dragging swells it too, in
 * proportion to how fast it is moving, so a flick deforms it more than a slow
 * drag does.
 *
 * The refraction is real here for the same reason it is on the switch. The
 * track is drawn by this view, so the pane samples it through a layer backdrop
 * instead of approximating what is underneath.
 */
class LiquidSliderView
@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AbstractComposeView(context, attrs) {

    private val valueState = mutableFloatStateOf(0f)
    private val trackColorState = mutableStateOf(Color.Gray)
    private val accentColorState = mutableStateOf(Color(0xFF34C759))

    var listener: OnValueChanged? = null

    /** Normalised to 0..1, since the host slider owns the real range. */
    var value: Float
        get() = valueState.floatValue
        set(newValue) {
            valueState.floatValue = newValue.coerceIn(0f, 1f)
        }

    /**
     * Sets the position without telling [listener] about it, for when something
     * else is already the source of truth.
     */
    fun setValueSilently(newValue: Float) {
        valueState.floatValue = newValue.coerceIn(0f, 1f)
    }

    fun setColors(track: Int, accent: Int) {
        trackColorState.value = Color(track)
        accentColorState.value = Color(accent)
    }

    @Composable
    override fun Content() {
        val scope = rememberCoroutineScope()

        val position = remember { Animatable(valueState.floatValue) }
        val press = remember { Animatable(0f) }

        val track = trackColorState.value
        val accent = accentColorState.value

        val width = remember { mutableFloatStateOf(0f) }

        // Follows the property when something else moves it, so a preference
        // write settles the same way a drag does.
        LaunchedEffect(Unit) {
            snapshotFlow { valueState.floatValue }.collectLatest { target ->
                if (press.value == 0f) {
                    position.animateTo(target, TRAVEL_SPRING)
                } else {
                    position.snapTo(target)
                }
            }
        }

        val trackBackdrop = rememberLayerBackdrop()

        fun report(fraction: Float) {
            val clamped = fraction.coerceIn(0f, 1f)

            valueState.floatValue = clamped
            listener?.onValueChanged(clamped)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(HEIGHT.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            scope.launch { press.animateTo(1f, PRESS_SPRING) }

                            tryAwaitRelease()

                            scope.launch { press.animateTo(0f, PRESS_SPRING) }
                        },
                        onTap = { offset -> report(offset.x / size.width) }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { scope.launch { press.animateTo(1f, PRESS_SPRING) } },
                        onDragEnd = { scope.launch { press.animateTo(0f, PRESS_SPRING) } },
                        onDragCancel = { scope.launch { press.animateTo(0f, PRESS_SPRING) } }
                    ) { change, _ ->
                        report(change.position.x / size.width)
                    }
                }
                // This pane is the accessibility node for the control, not the
                // Material slider underneath it. A view at zero alpha is
                // reported as not visible to the user and drops out of the
                // tree entirely, so leaning on it would have left the slider
                // unreachable. progressSemantics supplies the value and range
                // that get announced, and setProgress is what lets an
                // accessibility service move it without touching the screen.
                .progressSemantics(position.value.coerceIn(0f, 1f), 0f..1f)
                .semantics {
                    setProgress { target ->
                        report(target)

                        true
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .layerBackdrop(trackBackdrop)
                    .clip(Capsule())
                    .fillMaxWidth()
                    .height(TRACK_HEIGHT.dp)
                    .drawBehind {
                        width.floatValue = size.width

                        drawRect(track)
                        drawRect(
                            accent,
                            size = Size(size.width * position.value.coerceIn(0f, 1f), size.height)
                        )
                    }
            )

            Box(
                Modifier
                    .graphicsLayer {
                        val thumb = THUMB_SIZE.dp.toPx()
                        val travel = (width.floatValue - thumb).coerceAtLeast(0f)
                        val fraction = position.value.coerceIn(0f, 1f)

                        translationX = travel * fraction

                        // Held, or moving fast, both swell it, the same way the
                        // switch's pane behaves.
                        val speed = (abs(position.velocity) / REFERENCE_VELOCITY)
                            .coerceIn(0f, 1f)
                        val swell = max(press.value, speed)

                        scaleX = 1f + GROWTH * swell
                        scaleY = 1f - GROWTH * swell * VOLUME_LOSS

                        // Grows towards whichever end of the track it has more
                        // room for, so it never spills off either end.
                        transformOrigin = TransformOrigin(fraction, 0.5f)
                    }
                    .drawBackdrop(
                        backdrop = trackBackdrop,
                        shape = { Capsule() },
                        effects = {
                            vibrancy()
                            blur(2f.dp.toPx())
                            lens(
                                LENS_HEIGHT.dp.toPx(),
                                LENS_AMOUNT.dp.toPx() * (0.15f + 0.85f * press.value),
                                chromaticAberration = true
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = 0.9f + 0.1f * press.value)
                        },
                        shadow = {
                            Shadow(
                                radius = (5f + 4f * press.value).dp,
                                color = Color.Black.copy(alpha = 0.24f)
                            )
                        },
                        innerShadow = {
                            InnerShadow(radius = 4f.dp, alpha = 0.35f)
                        },
                        onDrawSurface = {
                            drawRect(
                                Color.White.copy(
                                    alpha = REST_OPACITY -
                                            (REST_OPACITY - HELD_OPACITY) * press.value
                                )
                            )
                        }
                    )
                    .size(THUMB_SIZE.dp, THUMB_SIZE.dp)
            )
        }
    }

    fun interface OnValueChanged {
        fun onValueChanged(value: Float)
    }

    private companion object {
        const val HEIGHT = 40
        const val TRACK_HEIGHT = 10
        const val THUMB_SIZE = 26

        const val LENS_HEIGHT = 14f
        const val LENS_AMOUNT = 20f

        const val GROWTH = 0.55f
        const val VOLUME_LOSS = 0.45f
        const val REFERENCE_VELOCITY = 6f

        const val REST_OPACITY = 0.88f
        const val HELD_OPACITY = 0.34f

        val TRAVEL_SPRING = spring<Float>(0.55f, 380f, 0.001f)
        val PRESS_SPRING = spring<Float>(1f, 1000f, 0.001f)
    }
}
