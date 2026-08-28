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
import androidx.compose.ui.graphics.TransformOrigin
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
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A switch in the shape iOS gives it: a flat coloured track with a wide pane of
 * glass riding on top.
 *
 * This is one of the few places in the app where refraction is real. A lens
 * needs pixels to bend, and the track is drawn by this view, so the pane
 * samples it through a layer backdrop rather than approximating it. That is why
 * the track's colour appears inside the pane, pulled towards the middle with a
 * clear margin at the rim, instead of being painted there.
 *
 * The motion is the other half of it. The pane behaves like a drop of water
 * being dragged: it stretches along its direction of travel, with the trailing
 * edge giving while the leading edge keeps its place, and pulls back round once
 * it arrives. The stretch is driven by the spring's own velocity rather than by
 * a separate timeline, so it deforms most where it is moving fastest and eases
 * out through the overshoot exactly as the travel does.
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

    /**
     * Sets the state without telling [listener] about it, for when something
     * else is already the source of truth.
     */
    fun setCheckedSilently(checked: Boolean) {
        checkedState.value = checked
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

        val track = trackColorState.value
        val accent = accentColorState.value

        // Follows the property when something else changes it, so a row tap or
        // a preference write animates the same way a direct tap does.
        LaunchedEffect(Unit) {
            snapshotFlow { checkedState.value }.collectLatest { checked ->
                fraction.animateTo(if (checked) 1f else 0f, TRAVEL_SPRING)
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

                            tryAwaitRelease()

                            scope.launch { press.animateTo(0f, PRESS_SPRING) }
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
                    .drawBehind { drawRect(lerp(track, accent, fraction.value.coerceIn(0f, 1f))) }
                    .size(TRACK_WIDTH.dp, TRACK_HEIGHT.dp)
            )

            Box(
                Modifier
                    .graphicsLayer {
                        val padding = THUMB_PADDING.dp.toPx()
                        val travel = (TRACK_WIDTH - THUMB_WIDTH - THUMB_PADDING * 2).dp.toPx()

                        translationX = lerp(padding, padding + travel, fraction.value)

                        // A droplet deforms in proportion to how fast it is
                        // being dragged, so the spring's velocity drives this
                        // rather than a timeline of its own. It also means the
                        // overshoot at the end wobbles the pane, which is what
                        // makes it read as liquid rather than as a sliding pill.
                        val speed = abs(fraction.velocity) / REFERENCE_VELOCITY
                        val stretch = speed.coerceIn(0f, 1f)

                        scaleX = 1f + STRETCH * stretch
                        scaleY = 1f - STRETCH * stretch * VOLUME_LOSS

                        // The leading edge keeps its place and the tail gives,
                        // so the pane trails behind itself.
                        transformOrigin = if (fraction.velocity >= 0f) {
                            TransformOrigin(1f, 0.5f)
                        } else {
                            TransformOrigin(0f, 0.5f)
                        }
                    }
                    .drawBackdrop(
                        backdrop = trackBackdrop,
                        shape = { Capsule() },
                        effects = {
                            // The lens is on at rest, not only while held. It is
                            // what puts the track's colour inside the pane.
                            vibrancy()
                            blur(2f.dp.toPx())
                            lens(
                                LENS_HEIGHT.dp.toPx(),
                                LENS_AMOUNT.dp.toPx() * (1f + press.value),
                                chromaticAberration = true
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = 0.9f + 0.1f * press.value)
                        },
                        shadow = {
                            Shadow(
                                radius = (6f + 4f * press.value).dp,
                                color = Color.Black.copy(alpha = 0.22f)
                            )
                        },
                        innerShadow = {
                            InnerShadow(radius = 4f.dp, alpha = 0.35f)
                        },
                        onDrawSurface = {
                            // A touch of white body, so the pane is a lit object
                            // rather than a bare magnifier over the track.
                            drawRect(Color.White.copy(alpha = 0.18f + 0.10f * press.value))
                        }
                    )
                    .size(THUMB_WIDTH.dp, THUMB_HEIGHT.dp)
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

        // Wide rather than round. The pane covering most of the control is what
        // separates this from a Material switch at a glance.
        const val THUMB_WIDTH = 34
        const val THUMB_HEIGHT = 28
        const val THUMB_PADDING = 2

        const val LENS_HEIGHT = 14f
        const val LENS_AMOUNT = 20f

        /** How much longer the pane gets at full speed. */
        const val STRETCH = 0.20f

        /** A stretched droplet is also thinner, since its volume is fixed. */
        const val VOLUME_LOSS = 0.45f

        /** The speed, in fraction per second, that counts as fully stretched. */
        const val REFERENCE_VELOCITY = 6f

        val TRAVEL_SPRING = spring<Float>(0.55f, 380f, 0.001f)
        val PRESS_SPRING = spring<Float>(1f, 1000f, 0.001f)
    }
}
