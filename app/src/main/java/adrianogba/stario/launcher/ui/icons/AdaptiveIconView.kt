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

package adrianogba.stario.launcher.ui.icons

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.activities.settings.dialogs.icons.IconsDialog
import adrianogba.stario.launcher.apps.LauncherApplication
import adrianogba.stario.launcher.apps.ProfileManager
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.utils.Utils
import adrianogba.stario.launcher.utils.objects.ObjectDelegate
import java.io.Serializable
import kotlin.math.min

// LocalBroadcastManager is deprecated but still used across the whole project.
@Suppress("DEPRECATION")
class AdaptiveIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val preferences = context.applicationContext
        .getSharedPreferences(Entry.ICONS.toString(), Context.MODE_PRIVATE)

    private val localBroadcastManager = LocalBroadcastManager.getInstance(context)

    private val radiusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            radius.value = intent.getFloatExtra(IconsDialog.EXTRA_CORNER_RADIUS, 1f)
        }
    }

    private val squircleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val serializable: Serializable? =
                intent.getSerializableExtra(IconsDialog.EXTRA_PATH_ALGORITHM)

            if (serializable is PathCornerTreatmentAlgorithm) {
                pathAlgorithm.value = serializable
            } else {
                pathAlgorithm.value = PathCornerTreatmentAlgorithm.REGULAR
            }
        }
    }

    var sizeRestricted: Boolean
    var looseClipping: Boolean

    init {
        if (attrs != null) {
            val attributes = context.applicationContext
                .obtainStyledAttributes(attrs, R.styleable.AdaptiveIconView)

            sizeRestricted =
                attributes.getBoolean(R.styleable.AdaptiveIconView_sizeRestricted, true)
            looseClipping =
                attributes.getBoolean(R.styleable.AdaptiveIconView_looseClipping, true)

            attributes.recycle()
        } else {
            sizeRestricted = true
            looseClipping = true
        }
    }

    private val path = Path()

    private val iconDelegate = ObjectDelegate<Drawable>(
        delegateAction { invalidate() }
    )

    private val pathAlgorithm = ObjectDelegate(
        PathCornerTreatmentAlgorithm.fromIdentifier(
            preferences.getInt(
                PathCornerTreatmentAlgorithm.PATH_ALGORITHM_ENTRY,
                PathCornerTreatmentAlgorithm.DEFAULT_PATH_ALGORITHM_ENTRY
            )
        ),
        delegateAction { requestLayout() }
    )

    private val radius = ObjectDelegate(
        preferences.getFloat(CORNER_RADIUS_ENTRY, DEFAULT_CORNER_RADIUS),
        delegateAction { requestLayout() }
    )

    private val badge: Drawable? = ResourcesCompat.getDrawable(
        context.resources, R.drawable.ic_alternate_badge, context.theme
    )

    private val grayscaleFilter = ColorMatrixColorFilter(ColorMatrix().apply {
        setSaturation(0f)
    })

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var profileStateBinding: ProfileStateBinding? = null
    private var grayscaleOverride: Boolean? = null
    private var applyBadge = false
    private var paused = false

    init {
        shadowPaint.color = Color.TRANSPARENT

        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        localBroadcastManager.registerReceiver(
            radiusReceiver,
            IntentFilter(IconsDialog.INTENT_CHANGE_CORNER_RADIUS)
        )
        localBroadcastManager.registerReceiver(
            squircleReceiver,
            IntentFilter(IconsDialog.INTENT_CHANGE_PATH_ALGORITHM)
        )

        profileStateBinding?.let { bindProfileState(it) }

        val currentPathCornerTreatmentAlgorithm = PathCornerTreatmentAlgorithm.fromIdentifier(
            preferences.getInt(
                PathCornerTreatmentAlgorithm.PATH_ALGORITHM_ENTRY,
                PathCornerTreatmentAlgorithm.DEFAULT_PATH_ALGORITHM_ENTRY
            )
        )
        if (pathAlgorithm.value!! != currentPathCornerTreatmentAlgorithm) {
            pathAlgorithm.value = currentPathCornerTreatmentAlgorithm
        }

        val currentRadius = preferences.getFloat(CORNER_RADIUS_ENTRY, DEFAULT_CORNER_RADIUS)
        if (radius.value!! != currentRadius) {
            radius.value = currentRadius
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        localBroadcastManager.unregisterReceiver(radiusReceiver)
        localBroadcastManager.unregisterReceiver(squircleReceiver)

        profileStateBinding?.let {
            localBroadcastManager.unregisterReceiver(it.receiver)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var widthSpec = widthMeasureSpec
        var heightSpec = heightMeasureSpec

        val maxIconSize = getMaxIconSize()
        var measuredWidth = MeasureSpec.getSize(widthSpec)
        val measuredHeight = MeasureSpec.getSize(heightSpec)

        if (measuredHeight > 0 && measuredWidth > 0) {
            if (measuredWidth != measuredHeight ||
                (sizeRestricted && measuredHeight > maxIconSize)
            ) {
                var size = min(measuredWidth, measuredHeight)

                if (sizeRestricted) {
                    size = min(maxIconSize, size)
                }

                widthSpec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.getMode(widthSpec))
                heightSpec = widthSpec
                measuredWidth = size
            }

            setClipBounds(Rect(0, 0, measuredWidth, measuredWidth))
        }

        super.onMeasure(widthSpec, heightSpec)
    }

    private fun updateClipPath(width: Int, height: Int) {
        if (pathAlgorithm.value == PathCornerTreatmentAlgorithm.SQUIRCLE) {
            createClipPathSquircle(width, height)

            return
        }

        createClipPathRegular(width, height)
    }

    //Thanks to Olga Nikolskaya https://medium.com/@nikolskayaolia/an-easy-way-to-implement-smooth-shapes-such-as-superellipse-and-squircle-into-a-user-interface-a5ba4e1139ed
    //And the https://copyicon.com/generator/svg-squircle implementation in JavaScript
    //Modified for the context of this project
    private fun createClipPathSquircle(width: Int, height: Int) {
        val fullWidth = width.toFloat()
        val fullHeight = height.toFloat()
        val halfWidth = fullWidth / 2f
        val halfHeight = fullHeight / 2f
        val arc = min(halfWidth, halfHeight) * (0.45f - (1f - radius.value!!) * 0.45f)

        path.reset()
        path.moveTo(0f, halfHeight)

        path.cubicTo(0f, arc, arc, 0f, halfWidth, 0f)
        path.cubicTo(fullWidth - arc, 0f, fullWidth, arc, fullWidth, halfHeight)
        path.cubicTo(
            fullWidth, fullHeight - arc,
            fullWidth - arc, fullHeight, halfWidth, fullHeight
        )
        path.cubicTo(arc, fullHeight, 0f, fullHeight - arc, 0f, halfHeight)

        path.close()
    }

    private fun createClipPathRegular(width: Int, height: Int) {
        val cornerRadius = radius.value!! * width / 2f

        path.reset()
        path.addRoundRect(
            0f, 0f, width.toFloat(), height.toFloat(),
            cornerRadius, cornerRadius, Path.Direction.CW
        )
        path.close()
    }

    fun getIcon(): Drawable? = iconDelegate.value

    fun setApplication(application: LauncherApplication?) {
        if (application == null) {
            setIcon(null)

            return
        }

        setIcon(application.getIcon())

        val binding = ProfileStateBinding(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    paused = !intent.getBooleanExtra(
                        ProfileManager.PROFILE_AVAILABLE_EXTRA, true
                    )

                    post { invalidate() }
                }
            },
            IntentFilter(
                ProfileManager.getProfileAvailabilityIntentAction(application.getProfile())
            ),
            application.getProfile()
        )

        profileStateBinding = binding

        bindProfileState(binding)
    }

    fun setIcon(icon: Drawable?) {
        profileStateBinding?.let {
            localBroadcastManager.unregisterReceiver(it.receiver)
            profileStateBinding = null
        }

        val constantState = icon?.constantState
        if (constantState != null) {
            val constantStateIcon = constantState.newDrawable()

            constantStateIcon.setBounds(
                MAX_SHADOW_SIZE, MAX_SHADOW_SIZE,
                measuredWidth - MAX_SHADOW_SIZE, measuredHeight - MAX_SHADOW_SIZE
            )

            iconDelegate.value = constantStateIcon
        } else {
            iconDelegate.value = null
        }

        paused = false
        applyBadge = false

        post { requestLayout() }
    }

    override fun setClipBounds(clipBounds: Rect?) {
        if (iconDelegate.value != null) {
            // Dereferenced without a null check in the Java original, kept as is.
            val size = clipBounds!!.width()
            val inset = size - MAX_SHADOW_SIZE * 2

            iconDelegate.value?.setBounds(
                MAX_SHADOW_SIZE, MAX_SHADOW_SIZE,
                inset + MAX_SHADOW_SIZE, inset + MAX_SHADOW_SIZE
            )

            updateClipPath(inset, inset)
            shadowPaint.setShadowLayer(
                (size.toFloat() / getMaxIconSize()) * MAX_SHADOW_SIZE * 0.75f,
                0f, 0f, Color.argb(100, 0, 0, 0)
            )
        }

        super.setClipBounds(clipBounds)
        invalidate()
    }

    /**
     * If set, will override default application paused grayscale state.
     */
    fun setGrayscale(value: Boolean) {
        this.grayscaleOverride = value

        post { invalidate() }
    }

    override fun draw(canvas: Canvas) {
        val saveCount = canvas.save()
        canvas.translate(MAX_SHADOW_SIZE.toFloat(), MAX_SHADOW_SIZE.toFloat())

        if (!looseClipping || iconDelegate.value is AdaptiveIconDrawable) {
            canvas.drawPath(path, shadowPaint)

            val clipSave = canvas.save()

            canvas.clipPath(path)

            super.draw(canvas)
            canvas.restoreToCount(clipSave)
        } else {
            super.draw(canvas)
        }

        val icon = iconDelegate.value
        if (applyBadge && icon != null) {
            // Dereferenced without a null check in the Java original, kept as is.
            val badge = this.badge!!
            val iconBounds = icon.bounds

            val intrinsicW = badge.intrinsicWidth
            val intrinsicH = badge.intrinsicHeight
            badge.setBounds(0, 0, intrinsicW, intrinsicH)

            val targetSize = iconBounds.width() * BADGE_SIZE
            val scale = targetSize / intrinsicW

            val save = canvas.save()

            canvas.translate(
                iconBounds.right - targetSize,
                iconBounds.bottom - targetSize
            )
            canvas.scale(scale, scale)

            badge.draw(canvas)
            canvas.restoreToCount(save)
        }

        canvas.restoreToCount(saveCount)
    }

    public override fun onDraw(canvas: Canvas) {
        val icon = iconDelegate.value ?: return

        if (icon is AdaptiveIconDrawable) {
            icon.background?.let { drawLayer(it, canvas) }
            icon.foreground?.let { drawLayer(it, canvas) }

            return
        }

        drawLayer(icon, canvas)
    }

    private fun drawLayer(drawable: Drawable, canvas: Canvas) {
        if (paused || grayscaleOverride == true) {
            drawable.colorFilter = grayscaleFilter
        }

        drawable.draw(canvas)
        drawable.colorFilter = null
    }

    private fun bindProfileState(binding: ProfileStateBinding) {
        paused = binding.isPaused(context)
        applyBadge = binding.shouldApplyManagedBadge()

        localBroadcastManager.registerReceiver(binding.receiver, binding.filter)
    }

    // The Java version kept the constructor and both queries private and relied on the
    // outer class being able to reach them. Kotlin does not grant an outer class access
    // to a nested class's private members, so they are public here.
    class ProfileStateBinding(
        val receiver: BroadcastReceiver,
        val filter: IntentFilter,
        private val handle: UserHandle
    ) {
        fun isPaused(context: Context): Boolean = !Utils.isProfileAvailable(context, handle)

        fun shouldApplyManagedBadge(): Boolean = !Utils.isMainProfile(handle)
    }

    companion object {
        const val CORNER_RADIUS_ENTRY: String = "com.stario.CORNER_RADIUS"
        const val DEFAULT_CORNER_RADIUS: Float = 1f
        const val MAX_SCALE: Float = 1.12f

        private const val MAX_SHADOW_SIZE = 5
        private const val BADGE_SIZE = 0.4f

        @JvmStatic
        fun getMaxIconSize(): Int = Measurements.dpToPx(60f)

        private fun <T> delegateAction(action: () -> Unit) =
            object : ObjectDelegate.ObjectDelegateAction<T> {
                override fun onSet(value: T?) {
                    action()
                }
            }
    }
}
