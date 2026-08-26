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

package adrianogba.stario.launcher.ui.utils.animation

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import adrianogba.stario.launcher.ui.keyboard.ImeAnimationController
import adrianogba.stario.launcher.ui.keyboard.KeyboardHeightProvider
import adrianogba.stario.launcher.ui.utils.UiUtils
import java.util.LinkedList
import kotlin.math.abs

object KeyboardAnimationHelper {
    private const val DEBOUNCED_KEYBOARD_RESIZE_DELAY = 50L
    private const val CONTROLLER_DISALLOW_DELAY = 50L

    @JvmStatic
    fun configureKeyboardAnimator(
        root: View,
        heightProvider: KeyboardHeightProvider,
        listener: ContentTranslationListener
    ) = configureKeyboardAnimator(root, heightProvider, null, listener)

    @JvmStatic
    fun configureKeyboardAnimator(
        root: View,
        heightProvider: KeyboardHeightProvider,
        controller: ImeAnimationController?,
        listener: ContentTranslationListener
    ) {
        ViewCompat.setWindowInsetsAnimationCallback(
            root,
            object : WindowInsetsAnimationCompat.Callback(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP
            ) {
                private val queue = LinkedList<Float>()
                private val allowControllerAnimation: Runnable? =
                    if (controller != null) Runnable { controller.disallowAnimationControl(false) }
                    else null

                private var imeAnimation: WindowInsetsAnimationCompat? = null
                private var debouncedResize: Runnable? = null
                private var startBottom = 0f
                private var endBottom = 0f
                private var running = false

                init {
                    heightProvider.addKeyboardHeightListener { height ->
                        if (!UiUtils.areAnimationsOn()) {
                            listener.translate(-height.toFloat())
                        } else {
                            if (!running) {
                                debouncedResize?.let { UiUtils.removeUICallback(it) }

                                val resize = Runnable { listener.translate(-height.toFloat()) }
                                debouncedResize = resize

                                UiUtils.postDelayed(resize, DEBOUNCED_KEYBOARD_RESIZE_DELAY)
                            }

                            endBottom = height.toFloat()
                        }
                    }
                }

                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    running = true

                    debouncedResize?.let { UiUtils.removeUICallback(it) }
                }

                override fun onStart(
                    animation: WindowInsetsAnimationCompat,
                    bounds: WindowInsetsAnimationCompat.BoundsCompat
                ): WindowInsetsAnimationCompat.BoundsCompat {
                    startBottom = if (UiUtils.isKeyboardVisible(root)) 0f
                    else heightProvider.getKeyboardHeight().toFloat()
                    endBottom = if (startBottom > 0) 0f
                    else heightProvider.getKeyboardHeight().toFloat()
                    imeAnimation = null

                    return bounds
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    if (controller != null && controller.isAnimationInProgress) {
                        val delta = endBottom - startBottom

                        listener.translate(abs(delta) * -controller.getExpandedFraction())

                        return insets
                    }

                    if (imeAnimation == null) {
                        imeAnimation = runningAnimations.firstOrNull {
                            (it.typeMask and WindowInsetsCompat.Type.ime()) != 0
                        }
                    }

                    val animation = imeAnimation

                    if (animation != null &&
                        (animation.durationMillis < 0 || UiUtils.areAnimationsOn())
                    ) {
                        allowControllerAnimation?.let { UiUtils.removeUICallback(it) }
                        controller?.disallowAnimationControl(true)

                        val fraction = animation.interpolatedFraction

                        // last frame will always return 1 as a fraction
                        // bypass this by remembering the last frame as well
                        if (queue.size >= 2) {
                            queue.remove()
                        }
                        queue.add(fraction)

                        val delta = endBottom - startBottom

                        listener.translate(delta * ((if (delta < 0) 1f else 0f) - fraction))
                    }

                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    running = false
                    imeAnimation = null

                    // we have at least two frames (animate only the first in queue)
                    if (queue.size > 1) {
                        val delta = endBottom - startBottom

                        listener.translate(delta * ((if (delta < 0) 1f else 0f) - queue.poll()!!))
                    }

                    queue.clear()

                    allowControllerAnimation?.let {
                        UiUtils.postDelayed(it, CONTROLLER_DISALLOW_DELAY)
                    }
                }
            })
    }

    fun interface ContentTranslationListener {
        fun translate(translation: Float)
    }
}
