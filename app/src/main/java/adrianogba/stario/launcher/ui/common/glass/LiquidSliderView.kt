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
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalDensity
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

/**
 * A slider built from the same parts as [LiquidToggleView], because iOS builds
 * them the same way.
 *
 * The track is a channel with the filled part in the accent colour, and the pane
 * that rides it is the switch's, unchanged: a frosted lozenge at rest that turns
 * to clear glass as it is held, growing about its own centre on two springs
 * damped slightly differently so it wobbles as it settles.
 *
 * Refraction here is real for the same reason it is on the switch. The track is
 * drawn by this view, so the pane samples it through a layer backdrop rather
 * than approximating what is underneath.
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
        val density = LocalDensity.current

        val position = remember { Animatable(valueState.floatValue) }
        val press = remember { Animatable(0f) }
        val scaleX = remember { Animatable(1f) }
        val scaleY = remember { Animatable(1f) }

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

        // The track is inset from the view, so a touch has to be measured
        // against the track rather than against the full width, or the value
        // runs ahead of the thumb at both ends.
        fun fractionAt(x: Float, viewWidth: Int): Float {
            val inset = with(density) { INSET.dp.toPx() }
            val usable = (viewWidth - inset * 2f).coerceAtLeast(1f)

            return (x - inset) / usable
        }

        fun hold(down: Boolean) {
            scope.launch { press.animateTo(if (down) 1f else 0f, PRESS_SPRING) }
            scope.launch {
                scaleX.animateTo(if (down) PRESSED_SCALE else 1f, SCALE_X_SPRING)
            }
            scope.launch {
                scaleY.animateTo(if (down) PRESSED_SCALE else 1f, SCALE_Y_SPRING)
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(HEIGHT.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            hold(true)

                            tryAwaitRelease()

                            hold(false)
                        },
                        onTap = { offset -> report(fractionAt(offset.x, size.width)) }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { hold(true) },
                        onDragEnd = { hold(false) },
                        onDragCancel = { hold(false) }
                    ) { change, _ ->
                        report(fractionAt(change.position.x, size.width))
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
                    // Inset so the track stops short of the row's edge instead
                    // of running into it, and so the pane has room at both ends.
                    .padding(horizontal = INSET.dp)
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
                        val thumb = THUMB_WIDTH.dp.toPx()
                        val travel = (width.floatValue - thumb).coerceAtLeast(0f)

                        translationX = INSET.dp.toPx() +
                                travel * position.value.coerceIn(0f, 1f)

                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        this.scaleX = scaleX.value
                        this.scaleY = scaleY.value
                    }
                    .drawBackdrop(
                        backdrop = trackBackdrop,
                        shape = { Capsule() },
                        effects = {
                            vibrancy()
                            blur(REST_BLUR.dp.toPx() * (1f - press.value))
                            lens(
                                LENS_HEIGHT.dp.toPx(),
                                LENS_AMOUNT.dp.toPx() *
                                        (REST_LENS + (PRESS_LENS - REST_LENS) * press.value),
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
                    .size(THUMB_WIDTH.dp, THUMB_HEIGHT.dp)
            )
        }
    }

    fun interface OnValueChanged {
        fun onValueChanged(value: Float)
    }

    private companion object {
        const val HEIGHT = 44
        const val TRACK_HEIGHT = 10

        /** Keeps the track off the edge of the row, and leaves the pane room. */
        const val INSET = 10

        const val THUMB_WIDTH = 34
        const val THUMB_HEIGHT = 24

        const val LENS_HEIGHT = 14f
        const val LENS_AMOUNT = 22f

        const val REST_BLUR = 6f
        const val REST_LENS = 0.30f
        const val PRESS_LENS = 1f

        const val PRESSED_SCALE = 1.5f

        const val REST_OPACITY = 0.80f
        const val HELD_OPACITY = 0.26f

        val TRAVEL_SPRING = spring<Float>(1f, 1000f, 0.001f)
        val PRESS_SPRING = spring<Float>(1f, 1000f, 0.001f)

        val SCALE_X_SPRING = spring<Float>(0.6f, 250f, 0.001f)
        val SCALE_Y_SPRING = spring<Float>(0.7f, 250f, 0.001f)
    }
}
