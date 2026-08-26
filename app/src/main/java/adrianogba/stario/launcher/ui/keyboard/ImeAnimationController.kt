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

package adrianogba.stario.launcher.ui.keyboard

import android.os.CancellationSignal
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationControlListenerCompat
import androidx.core.view.WindowInsetsAnimationControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ImeAnimationController {

    private var insetsAnimationController: WindowInsetsAnimationControllerCompat? = null
    private var cancellationSignal: CancellationSignal? = null
    private var springAnimation: SpringAnimation? = null
    private var isImeShownAtStart = false

    var isAnimationControlDisallowed = false
        private set

    fun startControlRequest(view: View) {
        if (isAnimationControlDisallowed) {
            return
        }

        val insets = ViewCompat.getRootWindowInsets(view) ?: return

        isImeShownAtStart = insets.isVisible(WindowInsetsCompat.Type.ime())
        cancellationSignal = CancellationSignal()

        @Suppress("DEPRECATION")
        val windowInsetsController = ViewCompat.getWindowInsetsController(view) ?: return

        windowInsetsController.controlWindowInsetsAnimation(
            WindowInsetsCompat.Type.ime(), -1,
            LinearInterpolator(), cancellationSignal,
            object : WindowInsetsAnimationControlListenerCompat {
                override fun onReady(
                    controller: WindowInsetsAnimationControllerCompat, types: Int
                ) {
                    insetsAnimationController = controller
                    cancellationSignal = null

                    insetTo(if (isImeShownAtStart) controller.shownStateInsets.bottom else 0)
                }

                override fun onFinished(controller: WindowInsetsAnimationControllerCompat) {
                    reset()
                }

                override fun onCancelled(controller: WindowInsetsAnimationControllerCompat?) {
                    reset()
                }
            }
        )
    }

    fun insetBy(dy: Int): Int {
        val controller = insetsAnimationController

        if (controller == null || isAnimationControlDisallowed) {
            return 0
        }

        return insetTo(controller.currentInsets.bottom - dy)
    }

    fun insetTo(inset: Int): Int {
        val controller = insetsAnimationController

        if (controller == null || isAnimationControlDisallowed) {
            return 0
        }

        val hiddenBottom = controller.hiddenStateInsets.bottom
        val shownBottom = controller.shownStateInsets.bottom

        val startBottom = if (isImeShownAtStart) shownBottom else hiddenBottom
        val endBottom = if (isImeShownAtStart) hiddenBottom else shownBottom

        val bottom = max(hiddenBottom, min(shownBottom, inset))

        val consumed = controller.currentInsets.bottom - bottom

        controller.setInsetsAndAlpha(
            Insets.of(0, 0, 0, bottom),
            1f,
            (bottom - startBottom).toFloat() / (endBottom - startBottom)
        )

        return consumed
    }

    val isAnimationInProgress: Boolean
        get() = insetsAnimationController != null

    val isSettleAnimationInProgress: Boolean
        get() = springAnimation != null

    val isRequestPending: Boolean
        get() = cancellationSignal != null

    fun getExpandedFraction(): Float {
        val controller = insetsAnimationController
            ?: throw RuntimeException("Fraction can only be returned if the animation is running.")

        return controller.currentInsets.bottom.toFloat() /
                (controller.shownStateInsets.bottom - controller.hiddenStateInsets.bottom)
    }

    val isCurrentPositionFullyHidden: Boolean
        get() {
            val controller = insetsAnimationController ?: return false

            return controller.currentInsets.bottom == controller.hiddenStateInsets.bottom
        }

    val isCurrentPositionFullyShown: Boolean
        get() {
            val controller = insetsAnimationController ?: return false

            return controller.currentInsets.bottom == controller.shownStateInsets.bottom
        }

    fun cancel() {
        cancellationSignal?.cancel()
    }

    @JvmOverloads
    fun finish(velocity: Int? = null) {
        springAnimation?.cancel()

        val controller = insetsAnimationController

        if (controller == null) {
            cancel()

            return
        }

        if (velocity != null && velocity != 0) {
            if (velocity > 0 && isCurrentPositionFullyShown) {
                controller.finish(true)
            } else if (velocity < 0 && isCurrentPositionFullyHidden) {
                controller.finish(false)
            } else {
                setVisibilityWithAnimation(velocity > 0, velocity)
            }
        } else if (isCurrentPositionFullyShown) {
            controller.finish(true)
        } else if (isCurrentPositionFullyHidden) {
            controller.finish(false)
        } else if (controller.currentFraction >= SCROLL_THRESHOLD) {
            setVisibilityWithAnimation(!isImeShownAtStart, null)
        } else {
            setVisibilityWithAnimation(isImeShownAtStart, null)
        }
    }

    fun disallowAnimationControl(value: Boolean) {
        isAnimationControlDisallowed = value

        if (!value) {
            return
        }

        if (isRequestPending) {
            reset()
        } else {
            finish()
        }
    }

    private fun reset() {
        cancel()

        springAnimation = null
        insetsAnimationController = null
        cancellationSignal = null
        isImeShownAtStart = false
    }

    private fun setVisibilityWithAnimation(visible: Boolean, velocity: Int?) {
        val controller = insetsAnimationController!!

        val animation = SpringAnimation(
            FloatValueHolder(controller.currentInsets.bottom.toFloat())
        )
        springAnimation = animation

        animation.addUpdateListener { _, value, _ -> insetTo(value.roundToInt()) }
        animation.addEndListener { _, canceled, _, _ ->
            val current = insetsAnimationController

            if (!canceled && current != null) {
                if (isCurrentPositionFullyShown) {
                    current.finish(true)
                } else if (isCurrentPositionFullyHidden) {
                    current.finish(false)
                } else if (current.currentFraction >= SCROLL_THRESHOLD) {
                    current.finish(isImeShownAtStart)
                } else {
                    current.finish(!isImeShownAtStart)
                }
            }

            springAnimation = null
        }

        if (velocity != null) {
            animation.setStartVelocity(velocity.toFloat())
        }

        animation.animateToFinalPosition(
            if (visible) controller.shownStateInsets.bottom.toFloat()
            else controller.hiddenStateInsets.bottom.toFloat()
        )

        animation.spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
        animation.spring.stiffness = 3000f
    }

    private companion object {
        const val SCROLL_THRESHOLD = 0.15f
    }
}
