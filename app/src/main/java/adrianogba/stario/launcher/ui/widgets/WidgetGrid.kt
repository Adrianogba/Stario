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

package adrianogba.stario.launcher.ui.widgets

import android.animation.AnimatorSet
import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import adrianogba.stario.launcher.sheet.widgets.Widget
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.utils.LayoutSizeObserver
import adrianogba.stario.launcher.ui.utils.animation.Animation
import adrianogba.stario.launcher.utils.objects.ObservableObject
import java.util.PriorityQueue
import kotlin.math.max

class WidgetGrid : GridLayout {
    private var onDispatchDrawReorderRunnable: Runnable? = null
    private lateinit var columnCount: ObservableObject<Int>
    private lateinit var map: WidgetMap

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        this.map = WidgetMap(2)
        this.onDispatchDrawReorderRunnable = null
        this.columnCount = ObservableObject(0, ObservableObject.OnSet { value ->
            val parentView = parent as View?

            val originalAlpha = parentView?.alpha ?: 1f
            parentView?.alpha = 0f

            map.setColumnCount(value)

            val transition = layoutTransition
            layoutTransition = null

            scheduleReorder()

            if (parentView != null && originalAlpha > 0) {
                post {
                    parentView.animate().alpha(originalAlpha)
                        .setDuration(Animation.MEDIUM.duration.toLong())
                }
            }

            layoutTransition = transition
        })

        rotation = 180f

        val set = AnimatorSet()
        set.playTogether(
            ObjectAnimator.ofFloat(null, "alpha", 0f, 1f),
            ObjectAnimator.ofFloat(null, "scaleX", 0.8f, 1f),
            ObjectAnimator.ofFloat(null, "scaleY", 0.8f, 1f)
        )

        val layoutTransition = LayoutTransition()
        layoutTransition.setAnimator(LayoutTransition.APPEARING, set)
        layoutTransition.setDuration(LayoutTransition.APPEARING, Animation.MEDIUM.duration.toLong())
        layoutTransition.setDuration(
            LayoutTransition.CHANGE_APPEARING, Animation.MEDIUM.duration.toLong()
        )
        layoutTransition.setDuration(
            LayoutTransition.CHANGE_DISAPPEARING, Animation.MEDIUM.duration.toLong()
        )
        layoutTransition.setDuration(
            LayoutTransition.DISAPPEARING, Animation.MEDIUM.duration.toLong()
        )
        layoutTransition.setStartDelay(LayoutTransition.APPEARING, 0)
        layoutTransition.disableTransitionType(LayoutTransition.CHANGING)

        setLayoutTransition(layoutTransition)

        LayoutSizeObserver.attach(this, LayoutSizeObserver.WIDTH,
            object : LayoutSizeObserver.OnChange {
                override fun onChange(view: View, watchFlags: Int, rect: Rect) {
                    columnCount.updateObject(
                        max(2, (rect.width() / Measurements.dpToPx(160f) / 2) * 2)
                    )
                }
            })
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        if (child is WidgetContainer) {
            super.addView(child, index, params)
        } else {
            throw RuntimeException("WidgetGrid can only have WidgetContainer children.")
        }
    }

    override fun removeView(view: View) {
        super.removeView(view)

        scheduleReorder()
    }

    private fun scheduleReorder() {
        if (onDispatchDrawReorderRunnable != null) {
            return
        }

        onDispatchDrawReorderRunnable = Runnable {
            onDispatchDrawReorderRunnable = null
            reorder()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val runnable = onDispatchDrawReorderRunnable

        if (runnable != null) {
            runnable.run()

            invalidate()
        } else {
            super.dispatchDraw(canvas)
        }
    }

    // public because Kotlin has no package private and WidgetContainer reads it
    val cellSize: Int
        get() = measuredWidth / columnCount.getObject()

    fun attach(host: AppWidgetHostView, widget: Widget) {
        val cell = map.getAvailableOrigin(widget.size!!)

        super.addView(WidgetContainer(context, host, widget, cell))
        map.add(cell, widget.size!!)
    }

    fun reorder() {
        map.clear()

        val containers = PriorityQueue<WidgetContainer>()

        for (index in 0 until childCount) {
            containers.add(getChildAt(index) as WidgetContainer)
        }

        while (!containers.isEmpty()) {
            val container = containers.poll()

            if (container != null) {
                val cell = map.getAvailableOrigin(container.getSize()!!)

                container.updateOrigin(cell)

                map.add(cell, container.getSize()!!)
            }
        }
    }

    fun allocatePosition(): Int {
        var max = -1

        for (index in 0 until childCount) {
            val pos = (getChildAt(index) as WidgetContainer).getPosition()
            if (pos > max) {
                max = pos
            }
        }
        return max + 1
    }

    fun computeCellSize(): Int = width / columnCount.getObject()
}
