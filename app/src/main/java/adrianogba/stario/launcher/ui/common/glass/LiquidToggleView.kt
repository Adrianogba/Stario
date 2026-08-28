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
 *
 * The motion model is adapted from Prismal by Saurav Sajeev, MIT licensed:
 * https://github.com/styropyr0/Prismal
 *
 * Copyright (c) 2025 Saurav Sajeev
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 */

package adrianogba.stario.launcher.ui.common.glass

import android.content.Context
import android.util.AttributeSet
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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

/**
 * A switch in the shape iOS gives it: a flat coloured track with a pane of glass
 * riding on top.
 *
 * At rest it is an ordinary switch and nothing more: a coloured capsule with a
 * solid pane sitting inside it. Touching it is the whole effect. The pane swells
 * past the track it was sitting in, uniformly rather than along one axis, the
 * blur drops away, the fill clears and the refraction comes up, so the track
 * resolves through it as glass. Letting go runs all of that backwards.
 *
 * The swell is where the character is. Position uses a stiff, critically damped
 * spring so travel is decisive, while the scale uses a soft underdamped one, and
 * the two axes are damped slightly differently. That mismatch is what makes the
 * pane wobble as it settles instead of arriving flat, and is the detail that
 * reads as liquid.
 *
 * Refraction here is real. A lens needs pixels to bend, and the track is drawn
 * by this view, so the pane samples it through a layer backdrop rather than
 * approximating what is underneath.
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
        val scaleX = remember { Animatable(1f) }
        val scaleY = remember { Animatable(1f) }

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
            // Bigger than the track on purpose. The pane grows by half again
            // while held, and this view is what clips it, so the room has to
            // exist here or the swell gets cut off at the edges.
            Modifier
                .size(
                    (TRACK_WIDTH + SLACK * 2).dp,
                    (TRACK_HEIGHT + SLACK * 2).dp
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            scope.launch { press.animateTo(1f, PRESS_SPRING) }
                            scope.launch { scaleX.animateTo(PRESSED_SCALE, SCALE_X_SPRING) }
                            scope.launch { scaleY.animateTo(PRESSED_SCALE, SCALE_Y_SPRING) }

                            tryAwaitRelease()

                            scope.launch { press.animateTo(0f, PRESS_SPRING) }
                            scope.launch { scaleX.animateTo(1f, SCALE_X_SPRING) }
                            scope.launch { scaleY.animateTo(1f, SCALE_Y_SPRING) }
                        },
                        onTap = { toggle() }
                    )
                },
            // No role here on purpose. The switch this pane covers is the
            // accessibility node, and declaring a second one would announce the
            // control twice.
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .padding(SLACK.dp)
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

                        translationX = SLACK.dp.toPx() +
                                lerp(padding, padding + travel, fraction.value)

                        // Grown about its own centre, not stretched along the
                        // travel. The two axes run on springs damped slightly
                        // differently, so the pane arrives with a wobble.
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        this.scaleX = scaleX.value
                        this.scaleY = scaleY.value
                    }
                    .drawBackdrop(
                        backdrop = trackBackdrop,
                        shape = { Capsule() },
                        effects = {
                            // Frosted at rest and clear under the finger. The
                            // blur and the lens trade places, which is what
                            // makes the track resolve as the pane is held.
                            vibrancy()
                            blur(REST_BLUR.dp.toPx() * (1f - press.value))
                            // No chromatic aberration. It splits what the
                            // pane bends into red, green and blue fringes, and
                            // over a single flat colour that reads as a rainbow
                            // smear rather than as glass. What should come
                            // through is the track's own colour.
                            lens(
                                LENS_HEIGHT.dp.toPx(),
                                LENS_AMOUNT.dp.toPx() *
                                        (REST_LENS + (PRESS_LENS - REST_LENS) * press.value)
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(
                                alpha = 1f,
                                width = Highlight.Default.width * 1.4f
                            )
                        },
                        shadow = {
                            // A glow in the track's own colour, not a drop
                            // shadow. Light leaves the edge of a lens rather
                            // than being blocked by it, so a black shadow reads
                            // as a grey halo sitting on the row.
                            Shadow(
                                radius = (5f + 5f * press.value).dp,
                                color = lerp(track, accent, fraction.value.coerceIn(0f, 1f))
                                    .copy(alpha = GLOW_ALPHA)
                            )
                        },
                        innerShadow = {
                            InnerShadow(radius = 4f.dp, alpha = 0.35f)
                        },
                        onDrawSurface = {
                            // Solid at rest, clear while held.
                            drawRect(
                                Color.White.copy(
                                    alpha = REST_OPACITY -
                                            (REST_OPACITY - HELD_OPACITY) * press.value
                                )
                            )
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
        const val TRACK_WIDTH = 46
        const val TRACK_HEIGHT = 24

        // A lozenge, not a circle, and roughly the proportions Prismal uses:
        // its thumb is 40 by 24 on a 64 by 28 track.
        // Inside the track at rest, with a little margin, which is an ordinary
        // switch. Growing past the track is what the press does, and is the
        // only time the control looks like glass.
        const val THUMB_WIDTH = 22
        const val THUMB_HEIGHT = 18
        const val THUMB_PADDING = 3

        /** Room around the track for the pane to grow into. */
        const val SLACK = 8

        const val LENS_HEIGHT = 14f
        const val LENS_AMOUNT = 22f

        const val REST_BLUR = 6f
        const val REST_LENS = 0.30f
        const val PRESS_LENS = 1f

        /** How much the pane grows while held. */
        const val PRESSED_SCALE = 1.5f

        // Solid at rest and clear under the finger. The pane is a knob until
        // it is touched, and only then does it become something you can see
        // the track through.
        const val REST_OPACITY = 0.95f
        const val HELD_OPACITY = 0.14f

        const val GLOW_ALPHA = 0.45f

        // Stiff and critically damped, so travel is decisive.
        val TRAVEL_SPRING = spring<Float>(1f, 1000f, 0.001f)
        val PRESS_SPRING = spring<Float>(1f, 1000f, 0.001f)

        // Soft and underdamped, and not quite equal, which is where the wobble
        // comes from.
        val SCALE_X_SPRING = spring<Float>(0.6f, 250f, 0.001f)
        val SCALE_Y_SPRING = spring<Float>(0.7f, 250f, 0.001f)
    }
}
