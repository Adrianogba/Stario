package adrianogba.stario.launcher.ui.common.text

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.Gravity
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatTextView
import adrianogba.stario.launcher.R
import kotlin.math.PI
import kotlin.math.sin

class WaveUnderlineTextView : AppCompatTextView {
    private val wavePath = Path()
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var pulsationAnimator: ValueAnimator? = null
    private var currentSpeedMultiplier = PULSATE_SPEED_MIN_FACTOR
    private var waveAnimator: ValueAnimator? = null
    private var lastWaveUpdate = 0L
    private var animationOffset = 0f
    private var waveColor = Color.WHITE

    constructor(context: Context) : super(context) {
        init(null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr) {
        init(attrs)
    }

    private fun init(attrs: AttributeSet?) {
        wavePaint.style = Paint.Style.STROKE
        wavePaint.strokeWidth = WAVE_STROKE_LENGTH_PIXELS.toFloat()
        wavePaint.pathEffect = null

        waveColor = Color.WHITE
        if (attrs != null) {
            val array = context.obtainStyledAttributes(attrs, R.styleable.WaveUnderlineTextView)

            try {
                waveColor = array.getColor(
                    R.styleable.WaveUnderlineTextView_waveColor, Color.WHITE
                )
            } finally {
                array.recycle()
            }
        }

        wavePaint.color = Color.argb(
            PULSATE_ALPHA_MIN,
            Color.red(waveColor), Color.green(waveColor), Color.blue(waveColor)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val textPaint = getPaint()
        val text = getText().toString()

        if (text.isEmpty()) {
            return
        }

        val textWidth = textPaint.measureText(text)
        val xStart: Float
        val viewWidth = width.toFloat()
        val gravity = getGravity()

        xStart = if ((gravity and Gravity.LEFT) == Gravity.LEFT ||
            (gravity and Gravity.START) == Gravity.START
        ) {
            paddingLeft.toFloat()
        } else if ((gravity and Gravity.RIGHT) == Gravity.RIGHT ||
            (gravity and Gravity.END) == Gravity.END
        ) {
            viewWidth - paddingRight - textWidth
        } else if ((gravity and Gravity.CENTER_HORIZONTAL) == Gravity.CENTER_HORIZONTAL) {
            (viewWidth - textWidth) / 2f
        } else {
            paddingLeft.toFloat()
        }

        val xEnd = xStart + textWidth
        val y = (height - paddingBottom + BOTTOM_OFFSET).toFloat()

        wavePath.reset()

        if (xStart >= xEnd) { // still, draw something
            val currentWaveX = xStart - animationOffset
            val angle = (currentWaveX / WAVE_LENGTH_PIXELS) * (2 * PI).toFloat()
            val yOffset = sin(angle.toDouble()).toFloat() * WAVE_AMPLITUDE_PIXELS

            wavePath.moveTo(xStart, y + yOffset)
            wavePath.lineTo(xStart + 1, y + yOffset)
        } else { // draw every sine point
            var currentXDraw = xStart

            while (currentXDraw <= xEnd) {
                val currentWaveX = currentXDraw - animationOffset
                val angle = (currentWaveX / WAVE_LENGTH_PIXELS) * (2 * PI).toFloat()
                val yOffset = sin(angle.toDouble()).toFloat() * WAVE_AMPLITUDE_PIXELS

                if (currentXDraw == xStart) {
                    wavePath.moveTo(currentXDraw, y + yOffset)
                } else {
                    wavePath.lineTo(currentXDraw, y + yOffset)
                }

                currentXDraw += 1
            }
        }

        canvas.drawPath(wavePath, wavePaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        lastWaveUpdate = 0

        // Wave Animator
        val waveAnimator = ValueAnimator.ofInt(0, 1)
        this.waveAnimator = waveAnimator

        waveAnimator.duration = 1000L
        waveAnimator.repeatCount = ValueAnimator.INFINITE
        waveAnimator.interpolator = LinearInterpolator()

        waveAnimator.addUpdateListener {
            val currentTimeNanos = System.nanoTime()

            if (lastWaveUpdate == 0L) {
                lastWaveUpdate = currentTimeNanos
                invalidate()

                return@addUpdateListener
            }

            var deltaTimeSeconds = (currentTimeNanos - lastWaveUpdate) / 1_000_000_000.0f
            lastWaveUpdate = currentTimeNanos

            // cap deltaTime
            if (deltaTimeSeconds > 0.1f) {
                deltaTimeSeconds = 0.1f
            }

            val actualSpeed = WAVE_SPEED_PIXELS_PER_SECOND * currentSpeedMultiplier
            animationOffset += actualSpeed * deltaTimeSeconds

            if (WAVE_LENGTH_PIXELS > 0) {
                animationOffset %= WAVE_LENGTH_PIXELS
            }

            invalidate()
        }
        waveAnimator.start()

        // Pulsation Animator
        val pulsationAnimator = ValueAnimator.ofFloat(0f, 1f)
        this.pulsationAnimator = pulsationAnimator

        pulsationAnimator.duration = PULSATE_DURATION_MS
        pulsationAnimator.repeatCount = ValueAnimator.INFINITE
        pulsationAnimator.repeatMode = ValueAnimator.REVERSE
        pulsationAnimator.interpolator = AccelerateDecelerateInterpolator()

        pulsationAnimator.addUpdateListener { animation ->
            val interpolatedFraction = animation.animatedValue as Float

            wavePaint.color = Color.argb(
                (PULSATE_ALPHA_MIN + interpolatedFraction *
                        (PULSATE_ALPHA_MAX - PULSATE_ALPHA_MIN)).toInt(),
                Color.red(waveColor), Color.green(waveColor), Color.blue(waveColor)
            )

            currentSpeedMultiplier = PULSATE_SPEED_MIN_FACTOR +
                    interpolatedFraction * (PULSATE_SPEED_MAX_FACTOR - PULSATE_SPEED_MIN_FACTOR)
        }
        pulsationAnimator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        waveAnimator?.cancel()
        waveAnimator = null

        pulsationAnimator?.cancel()
        pulsationAnimator = null

        lastWaveUpdate = 0
    }

    fun setWaveColor(newColor: Int) {
        waveColor = newColor

        val animator = pulsationAnimator
        val currentAlphaValue: Int

        if (animator != null && animator.isRunning) {
            val interpolatedFraction = animator.animatedValue as Float

            currentAlphaValue = (PULSATE_ALPHA_MIN + interpolatedFraction *
                    (PULSATE_ALPHA_MAX - PULSATE_ALPHA_MIN)).toInt()
        } else {
            currentAlphaValue = PULSATE_ALPHA_MIN
        }

        wavePaint.color = Color.argb(
            currentAlphaValue,
            Color.red(waveColor), Color.green(waveColor), Color.blue(waveColor)
        )

        invalidate()
    }

    private companion object {
        // Wave movement
        private const val WAVE_SPEED_PIXELS_PER_SECOND = 30
        private const val WAVE_STROKE_LENGTH_PIXELS = 6
        private const val WAVE_AMPLITUDE_PIXELS = 8
        private const val WAVE_LENGTH_PIXELS = 80
        private const val BOTTOM_OFFSET = 10

        // Pulsation parameters
        private const val PULSATE_SPEED_MIN_FACTOR = 0.5f
        private const val PULSATE_SPEED_MAX_FACTOR = 1.5f
        private const val PULSATE_DURATION_MS = 2000L
        private const val PULSATE_ALPHA_MAX = 180
        private const val PULSATE_ALPHA_MIN = 80
    }
}
