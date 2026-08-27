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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * A switch whose thumb is a pane of liquid glass refracting its own track.
 *
 * Follows the shape of the reference implementation in the backdrop library's
 * catalog, which is Apache-2.0, rather than inventing behaviour:
 * https://github.com/Kyant0/AndroidLiquidGlass
 *
 * The three things that make it Liquid Glass rather than a blurred circle:
 * the thumb samples the track through a layer backdrop, so what refracts is
 * real content rather than a stand-in; pressing swaps blur for refraction and
 * brings up a specular highlight, so the glass reacts to touch; and the thumb
 * swells while held, which is the motion Apple's guidance describes for
 * controls in the floating layer.
 */
class LiquidToggleView
@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AbstractComposeView(context, attrs) {

    private val checkedState = mutableStateOf(false)
    private val trackColorState = mutableStateOf(Color.Gray)
    private val accentColorState = mutableStateOf(Color(0xFF34C759))

    var listener: OnCheckedChange? = null

    var isChecked: Boolean
        get() = checkedState.value
        set(value) {
            checkedState.value = value
        }

    fun setColors(track: Int, accent: Int) {
        trackColorState.value = Color(track)
        accentColorState.value = Color(accent)
    }

    @Composable
    override fun Content() {
        val scope = rememberCoroutineScope()

        val fraction = remember { Animatable(if (checkedState.value) 1f else 0f) }
        val press = remember { Animatable(0f) }
        val scale = remember { Animatable(1f) }

        val track = trackColorState.value
        val accent = accentColorState.value

        // Follows the property when something else changes it, so a row tap or
        // a preference write animates the same way a direct tap does.
        LaunchedEffect(Unit) {
            snapshotFlow { checkedState.value }.collectLatest { checked ->
                fraction.animateTo(
                    if (checked) 1f else 0f, spring(0.9f, 900f, 0.001f)
                )
            }
        }

        val trackBackdrop = rememberLayerBackdrop()

        Box(
            Modifier
                .size(TRACK_WIDTH.dp, TRACK_HEIGHT.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            scope.launch { press.animateTo(1f, PRESS_SPRING) }
                            scope.launch { scale.animateTo(PRESSED_SCALE, SCALE_SPRING) }

                            tryAwaitRelease()

                            scope.launch { press.animateTo(0f, PRESS_SPRING) }
                            scope.launch { scale.animateTo(1f, SCALE_SPRING) }
                        },
                        onTap = { toggle() }
                    )
                }
                .semantics { role = Role.Switch },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .layerBackdrop(trackBackdrop)
                    .clip(Capsule())
                    .drawBehind { drawRect(lerp(track, accent, fraction.value)) }
                    .size(TRACK_WIDTH.dp, TRACK_HEIGHT.dp)
            )

            Box(
                Modifier
                    .graphicsLayer {
                        val padding = THUMB_PADDING.dp.toPx()
                        val travel = (TRACK_WIDTH - THUMB_SIZE - THUMB_PADDING * 2).dp.toPx()

                        translationX = lerp(padding, padding + travel, fraction.value)
                        scaleX = scale.value
                        scaleY = scale.value
                    }
                    .drawBackdrop(
                        backdrop = trackBackdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = press.value

                            // Blurred at rest, refracting while held. Swapping
                            // between the two is what reads as glass being
                            // pushed rather than a shape being tinted.
                            blur(8f.dp.toPx() * (1f - progress))
                            lens(
                                5f.dp.toPx() * progress,
                                10f.dp.toPx() * progress,
                                chromaticAberration = true
                            )
                        },
                        highlight = {
                            Highlight.Ambient.copy(
                                width = Highlight.Ambient.width / 1.5f,
                                blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                                alpha = press.value
                            )
                        },
                        shadow = {
                            Shadow(radius = 4f.dp, color = Color.Black.copy(alpha = 0.05f))
                        },
                        innerShadow = {
                            InnerShadow(radius = 4f.dp * press.value, alpha = press.value)
                        },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = 1f - press.value))
                        }
                    )
                    .size(THUMB_SIZE.dp, THUMB_SIZE.dp)
            )
        }
    }

    private fun toggle() {
        val next = !checkedState.value
        checkedState.value = next

        listener?.onCheckedChange(next)
    }

    fun interface OnCheckedChange {
        fun onCheckedChange(checked: Boolean)
    }

    private companion object {
        const val TRACK_WIDTH = 52
        const val TRACK_HEIGHT = 32
        const val THUMB_SIZE = 26
        const val THUMB_PADDING = 3
        const val PRESSED_SCALE = 1.15f

        val PRESS_SPRING = spring<Float>(1f, 1000f, 0.001f)
        val SCALE_SPRING = spring<Float>(0.6f, 250f, 0.001f)
    }
}
