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

package adrianogba.stario.launcher.ui.common.text

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.animation.PathInterpolator
import androidx.appcompat.widget.AppCompatTextView
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.utils.UiUtils
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.min

class DelayedMarqueeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    // Nullable and lazily created on purpose. TextView's constructor calls
    // setText() before any of this class's initializers have run, so anything
    // touched from there has to survive being null.
    private var textLock: ReentrantLock? = ReentrantLock()

    private var scrollAnimator: ValueAnimator? = null
    private var originalText: CharSequence? = ""
    private var isMarqueeNeeded = false
    private var lastWidth = -1

    init {
        setSingleLine(true)
        ellipsize = null
        setHorizontallyScrolling(true)

        isHorizontalScrollBarEnabled = false
        isHorizontalFadingEdgeEnabled = true
        setFadingEdgeLength(FADING_EDGE_LENGTH)
    }

    private fun lock(): ReentrantLock {
        val existing = textLock

        if (existing != null) {
            return existing
        }

        val created = ReentrantLock()
        textLock = created

        return created
    }

    override fun setSingleLine(singleLine: Boolean) {
        if (!singleLine) {
            return
        }

        super.setSingleLine(true)
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        val textLock = lock()
        textLock.lock()

        try {
            originalText = text
            lastWidth = -1

            super.setText(text, type)
        } finally {
            textLock.unlock()
            setupMarqueeInternal()
        }
    }

    override fun getText(): CharSequence = originalText ?: ""

    private fun setupMarqueeInternal() {
        val viewWidth = width
        if (viewWidth <= 0 || viewWidth == lastWidth) {
            return
        }

        lastWidth = viewWidth
        stopMarqueeInternal()

        val originalText = originalText
        if (originalText.isNullOrEmpty()) {
            return
        }

        val originalTextWidth = paint.measureText(originalText.toString())
        val availableWidth = viewWidth - paddingLeft - paddingRight

        isMarqueeNeeded = originalTextWidth > availableWidth && UiUtils.areAnimationsOn()
        if (!isMarqueeNeeded) {
            restoreOriginalText()

            return
        }

        val textLock = lock()
        textLock.lock()
        try {
            super.setText(
                TextUtils.concat(originalText, SPACING, originalText),
                BufferType.NORMAL
            )
        } finally {
            textLock.unlock()
        }

        val scrollDistance = paint.measureText(originalText.toString() + SPACING).toInt()
        val duration = (scrollDistance / SCROLL_SPEED_PIXELS_PER_SECOND * 1000).toLong()

        val animator = ValueAnimator.ofInt(0, scrollDistance)
        scrollAnimator = animator

        animator.interpolator = PathInterpolator(0.15f, 0.01f, 0.85f, 1f)
        animator.duration = (duration / Measurements.getAnimatorDurationScale()).toLong()
        animator.startDelay =
            (MARQUEE_DELAY / Measurements.getAnimatorDurationScale()).toLong()

        animator.addUpdateListener { anim -> scrollTo(anim.animatedValue as Int, 0) }

        animator.addListener(object : AnimatorListenerAdapter() {
            var isCancelled = false

            override fun onAnimationCancel(animation: Animator) {
                isCancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                scrollTo(0, 0)

                if (isCancelled) {
                    isCancelled = false

                    return
                }

                postDelayed({
                    val running = scrollAnimator

                    if (!isCancelled && running != null) {
                        running.setIntValues(0, scrollDistance)
                        running.start()
                    }
                }, (MARQUEE_DELAY / Measurements.getAnimatorDurationScale()).toLong())
            }
        })

        animator.start()
    }

    private fun restoreOriginalText() {
        val textLock = lock()
        textLock.lock()

        try {
            if (!TextUtils.equals(super.getText(), originalText)) {
                super.setText(originalText, BufferType.NORMAL)
            }

            scrollTo(0, 0)
        } finally {
            textLock.unlock()
        }
    }

    fun stopMarqueeInternal() {
        scrollAnimator?.cancel()
        scrollAnimator = null

        lastWidth = -1
        scrollTo(0, 0)
    }

    override fun getLeftFadingEdgeStrength(): Float {
        if (!isMarqueeNeeded && layout != null) {
            return 0f
        }

        if (layoutDirection != LAYOUT_DIRECTION_LTR) {
            return 1f
        }

        return fadeStrength()
    }

    override fun getRightFadingEdgeStrength(): Float {
        if (!isMarqueeNeeded && layout != null) {
            return 0f
        }

        if (layoutDirection == LAYOUT_DIRECTION_LTR) {
            return 1f
        }

        return fadeStrength()
    }

    private fun fadeStrength(): Float {
        val distance = scrollX

        return if (distance > 0 && distance <= layout.getLineRight(0).toInt() / 2) {
            min(distance.toFloat() / FADING_EDGE_LENGTH, 1f)
        } else {
            0f
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        if (width != oldWidth) {
            setupMarqueeInternal()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        setupMarqueeInternal()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)

        if (hasWindowFocus) {
            setupMarqueeInternal()
        } else {
            stopMarqueeInternal()
        }
    }

    private companion object {
        const val SCROLL_SPEED_PIXELS_PER_SECOND = 100f
        const val MARQUEE_DELAY = 2000L
        const val FADING_EDGE_LENGTH = 20
        const val SPACING = "        "
    }
}
