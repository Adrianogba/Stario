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

package adrianogba.stario.launcher.ui.common.grid

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.Stario
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.utils.animation.Animation
import java.util.LinkedList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Used Gemini 3 for the nudge and item move logic
open class DynamicGridLayout(
    context: Context,
    attrs: AttributeSet?
) : ViewGroup(context, attrs) {

    private val templateManager: GridTemplateManager
    private val preAnimVisualPos = HashMap<View, Point>()
    private val hintedViews = ArrayList<View>()
    private val handler = Handler(Looper.getMainLooper())

    private val warningMessage: String

    private var isRearrangeable = false
    private var cellWidth = 0
    private var cellHeight = 0
    private var colCount = MIN_COLS_PORTRAIT
    private var rowCount = MIN_ROWS_PORTRAIT

    private var activeItem: DraggableGridItem? = null
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isResizing = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private var originalParams: GridLayoutParams? = null
    private var reorderRunnable: Runnable? = null
    private var currentHoverTarget: View? = null
    private var pendingTargetCol = 0
    private var pendingTargetRow = 0
    private var lastMeasuredCols = -1
    private var lastMeasuredRows = -1
    private var lastReorderCol = -1
    private var lastReorderRow = -1

    private var runningAnimations = 0

    init {
        val array = context.theme.obtainStyledAttributes(
            attrs, R.styleable.DynamicGridLayout, 0, 0
        )

        val key = array.getString(R.styleable.DynamicGridLayout_grid_preference_key)
        val templateResourceId = array.getResourceId(
            R.styleable.DynamicGridLayout_grid_layout_template, 0
        )
        warningMessage = array.getString(
            R.styleable.DynamicGridLayout_grid_no_space_warning
        ) ?: "No room available."

        array.recycle()

        if (key.isNullOrEmpty()) {
            throw RuntimeException("'grid_preference_key' attribute is required")
        }

        templateManager = GridTemplateManager(
            context.applicationContext as Stario, key, templateResourceId
        )

        clipChildren = false
        clipToPadding = false
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)

        if (child !is DraggableGridItem) {
            throw IllegalStateException(
                "DraggableGridLayout can host only DraggableGridItem children."
            )
        }
    }

    private fun isAnimationRunning(): Boolean = runningAnimations > 0

    fun setRearrangeable(rearrangeable: Boolean) {
        if (isRearrangeable == rearrangeable) {
            return
        }

        isRearrangeable = rearrangeable

        forEachItem { it.isResizingActive = rearrangeable }
    }

    internal fun getCellWidth(): Int = cellWidth

    internal fun getCellHeight(): Int = cellHeight

    internal fun getColumnCount(): Int = colCount

    internal fun getRowCount(): Int = rowCount

    fun addItem(view: DraggableGridItem, defaultTemplateData: ItemLayoutData?) {
        val saved = templateManager.getLayoutForSize(colCount, rowCount)[view.itemId]

        val data = saved
            ?: defaultTemplateData
            ?: ItemLayoutData(view.itemId, 0, 0, 1, 1)

        if (defaultTemplateData != null) {
            data.copyBoundsFrom(defaultTemplateData)
        }

        view.minColSpan = if (data.minColSpan > 0) data.minColSpan else 1
        view.minWidth = if (data.minWidth > 0) data.minWidth else -1
        view.maxColSpan = if (data.maxColSpan > 0) data.maxColSpan else -1
        view.maxWidth = if (data.maxWidth > 0) data.maxWidth else -1

        view.minRowSpan = if (data.minRowSpan > 0) data.minRowSpan else 1
        view.minHeight = if (data.minHeight > 0) data.minHeight else -1
        view.maxRowSpan = if (data.maxRowSpan > 0) data.maxRowSpan else -1
        view.maxHeight = if (data.maxHeight > 0) data.maxHeight else -1

        view.isResizingActive = isRearrangeable

        val layoutParams = GridLayoutParams(data.col, data.row, data.colSpan, data.rowSpan)
        view.layoutParams = layoutParams

        val currentState = buildCurrentState()
        val targetRect = layoutParams.toRect()

        // Attempt preferred position first
        if (!currentState.isOccupied(targetRect, null)) {
            addView(view)
            saveLayoutState()

            return
        }

        // Try to rearrange items, then try again allowing them to shrink
        for (allowShrink in booleanArrayOf(false, true)) {
            val rearranged = attemptGlobalRearrange(
                currentState, view, layoutParams.colSpan, layoutParams.rowSpan, allowShrink
            )

            if (rearranged != null) {
                commitState(rearranged)
                addView(view)
                saveLayoutState()

                return
            }
        }

        // Drop it on the first free spot
        val firstFree = findClosestFreeSpotInState(
            currentState, layoutParams.colSpan, layoutParams.rowSpan, 0, 0, null
        )

        if (firstFree != null) {
            layoutParams.col = firstFree.left
            layoutParams.row = firstFree.top
            addView(view)
            saveLayoutState()

            return
        }

        // (#-_-)
        Toast.makeText(context, warningMessage, Toast.LENGTH_SHORT).show()
    }

    private fun findClosestFreeSpotInState(
        state: GridState, spanX: Int, spanY: Int,
        preferredCol: Int, preferredRow: Int, ignoreView: View?
    ): Rect? {
        var bestDistance = Double.MAX_VALUE
        var bestRect: Rect? = null

        for (row in 0..state.rows - spanY) {
            for (col in 0..state.cols - spanX) {
                val candidate = Rect(col, row, col + spanX, row + spanY)

                if (!state.isOccupied(candidate, ignoreView)) {
                    val dx = (col - preferredCol).toDouble()
                    val dy = (row - preferredRow).toDouble()
                    val distance = sqrt(dx * dx + dy * dy)

                    if (distance < bestDistance) {
                        bestDistance = distance
                        bestRect = candidate
                    }
                }
            }
        }

        return bestRect
    }

    private fun attemptGlobalRearrange(
        currentState: GridState, newItem: View,
        newSpanX: Int, newSpanY: Int, allowShrink: Boolean
    ): GridState? {
        val newDrag = newItem as DraggableGridItem
        val minX = newDrag.minColSpan
        val minY = newDrag.minRowSpan

        // really, REALLY SLOW, but the cell count is small
        for (spanX in newSpanX downTo minX) {
            for (spanY in newSpanY downTo minY) {
                for (row in 0..rowCount - spanY) {
                    for (col in 0..colCount - spanX) {
                        val state = currentState.cloneState()
                        state.placements[newItem] = Rect(col, row, col + spanX, row + spanY)

                        val solved = resolveAllCollisions(
                            state, newItem, allowShrink, rowCount, colCount
                        )

                        if (solved != null) {
                            return solved
                        }
                    }
                }
            }
        }

        return null
    }

    private fun resolveAllCollisions(
        state: GridState, newItem: View, allowShrink: Boolean,
        rowCount: Int, colCount: Int
    ): GridState? {
        val queue = LinkedList<GridState>()
        queue.add(state)

        while (!queue.isEmpty()) {
            val current = queue.poll() ?: break

            var collisionFound = false
            var stopProcessing = false

            for (a in current.placements.entries) {
                if (stopProcessing) {
                    break
                }

                for (b in current.placements.entries) {
                    if (a.key === b.key || !Rect.intersects(a.value, b.value)) {
                        continue
                    }

                    collisionFound = true
                    val victim = b.key

                    if (victim !== newItem) {
                        if (allowShrink) {
                            val item = victim as DraggableGridItem

                            if (b.value.width() > item.minColSpan) {
                                shrinkAndReflow(current, victim, rowCount, colCount) {
                                    it.right -= 1
                                }?.let { queue.add(it) }
                            }

                            if (b.value.height() > item.minRowSpan) {
                                shrinkAndReflow(current, victim, rowCount, colCount) {
                                    it.bottom -= 1
                                }?.let { queue.add(it) }
                            }
                        }

                        val movedCopy = current.cloneState()
                        if (reflowItem(movedCopy, victim, rowCount, colCount)) {
                            queue.add(movedCopy)
                        }
                    }

                    stopProcessing = true
                }
            }

            if (!collisionFound) {
                return current
            }
        }

        return null
    }

    /**
     * Shrinks a colliding item by one cell and looks for somewhere it still
     * fits.
     *
     * Carries an aliasing bug forward from the Java version rather than
     * quietly fixing it in a conversion: cloneState copies the map but not the
     * rectangles in it, so [shrink] narrows a Rect that the state it was cloned
     * from is still holding. The states in the queue are therefore not
     * independent of each other.
     */
    private fun shrinkAndReflow(
        current: GridState, victim: View, rowCount: Int, colCount: Int,
        shrink: (Rect) -> Unit
    ): GridState? {
        val copy = current.cloneState()
        val victimRect = copy.placements[victim] ?: return null

        shrink(victimRect)

        return if (reflowItem(copy, victim, rowCount, colCount)) copy else null
    }

    private fun reflowItem(
        state: GridState, item: View, rowCount: Int, colCount: Int
    ): Boolean {
        val rect = state.placements.remove(item) ?: return false

        val spanX = rect.width()
        val spanY = rect.height()

        for (row in 0..rowCount - spanY) {
            for (col in 0..colCount - spanX) {
                val candidate = Rect(col, row, col + spanX, row + spanY)

                if (state.placements.values.none { Rect.intersects(candidate, it) }) {
                    state.placements[item] = candidate

                    return true
                }
            }
        }

        state.placements[item] = rect

        return false
    }

    fun removeItem(view: DraggableGridItem?) {
        if (view == null) {
            return
        }

        removeView(view)
        preAnimVisualPos.remove(view)

        if (activeItem === view) {
            activeItem = null
            resetHoverState()
        }

        if (currentHoverTarget === view) {
            currentHoverTarget = null
        }

        saveLayoutState()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalWidth = MeasureSpec.getSize(widthMeasureSpec)
        val totalHeight = MeasureSpec.getSize(heightMeasureSpec)

        val horizontalPadding = paddingLeft + paddingRight
        val verticalPadding = paddingTop + paddingBottom

        val availableWidth = totalWidth - horizontalPadding
        val availableHeight = totalHeight - verticalPadding

        val density = resources.displayMetrics.density
        val minCellSizePx = (MIN_CELL_SIZE_DP * density).toInt()

        colCount = max(
            if (Measurements.isLandscape()) MIN_COLS_LANDSCAPE else MIN_COLS_PORTRAIT,
            availableWidth / minCellSizePx
        )

        while ((availableWidth / colCount) > (minCellSizePx * 2)) colCount++
        cellWidth = availableWidth / colCount

        rowCount = max(
            if (Measurements.isLandscape()) MIN_ROWS_LANDSCAPE else MIN_ROWS_PORTRAIT,
            availableHeight / minCellSizePx
        )

        while ((availableHeight / rowCount) > (minCellSizePx * 2)) rowCount++
        cellHeight = availableHeight / rowCount

        if (colCount != lastMeasuredCols || rowCount != lastMeasuredRows) {
            lastMeasuredCols = colCount
            lastMeasuredRows = rowCount

            reloadLayoutForCurrentSize()
        }

        setMeasuredDimension(
            colCount * cellWidth + horizontalPadding,
            rowCount * cellHeight + verticalPadding
        )

        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val layoutParams = child.gridParams()

            if (layoutParams.col + layoutParams.colSpan > colCount) {
                layoutParams.col = max(0, colCount - layoutParams.colSpan)
            }

            if (layoutParams.row + layoutParams.rowSpan > rowCount) {
                layoutParams.row = max(0, rowCount - layoutParams.rowSpan)
            }

            child.measure(
                MeasureSpec.makeMeasureSpec(
                    layoutParams.colSpan * cellWidth, MeasureSpec.EXACTLY
                ),
                MeasureSpec.makeMeasureSpec(
                    layoutParams.rowSpan * cellHeight, MeasureSpec.EXACTLY
                )
            )
        }
    }

    private fun reloadLayoutForCurrentSize() {
        val newConfig = templateManager.getLayoutForSize(colCount, rowCount)

        var changed = false
        var newState = GridState(colCount, rowCount)

        val configuredItems = ArrayList<View>()
        val unconfiguredItems = ArrayList<View>()

        for (index in 0 until childCount) {
            val child = getChildAt(index) ?: continue

            if (newConfig.containsKey((child as DraggableGridItem).itemId)) {
                configuredItems.add(child)
            } else {
                unconfiguredItems.add(child)
            }
        }

        for (child in configuredItems) {
            val data = newConfig[(child as DraggableGridItem).itemId] ?: continue

            val column = max(0, min(data.col, colCount - data.colSpan))
            val row = max(0, min(data.row, rowCount - data.rowSpan))

            val targetRect = Rect(column, row, column + data.colSpan, row + data.rowSpan)

            if (newState.isOccupied(targetRect, null)) {
                unconfiguredItems.add(child)
            } else {
                newState.placements[child] = targetRect
            }
        }

        for (child in unconfiguredItems) {
            val layoutParams = child.gridParams()

            val oldCol = layoutParams.col
            val oldRow = layoutParams.row

            val freeSpot = findClosestFreeSpotInState(
                newState, layoutParams.colSpan, layoutParams.rowSpan, oldCol, oldRow, null
            )

            if (freeSpot != null) {
                newState.placements[child] = freeSpot

                continue
            }

            val rearranged = attemptGlobalRearrange(
                newState, child, layoutParams.colSpan, layoutParams.rowSpan, true
            )

            if (rearranged != null) {
                newState = rearranged
            } else {
                val clampedCol = max(0, min(oldCol, colCount - layoutParams.colSpan))
                val clampedRow = max(0, min(oldRow, rowCount - layoutParams.rowSpan))

                newState.placements[child] = Rect(
                    clampedCol, clampedRow,
                    clampedCol + layoutParams.colSpan, clampedRow + layoutParams.rowSpan
                )
            }
        }

        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val placedRect = newState.placements[child] ?: continue

            val layoutParams = child.gridParams()

            if (layoutParams.col != placedRect.left || layoutParams.row != placedRect.top ||
                layoutParams.colSpan != placedRect.width() ||
                layoutParams.rowSpan != placedRect.height()
            ) {
                layoutParams.col = placedRect.left
                layoutParams.row = placedRect.top
                layoutParams.colSpan = placedRect.width()
                layoutParams.rowSpan = placedRect.height()

                changed = true
            }
        }

        if (changed) {
            requestLayout()
        }
    }

    @SuppressLint("DrawAllocation")
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val layoutParams = child.gridParams()

            val left = paddingLeft + (layoutParams.col * cellWidth)
            val top = paddingTop + (layoutParams.row * cellHeight)

            if (child === activeItem && !isResizing &&
                child.getTag(R.id.is_dragging_tag) != null
            ) {
                child.layout(
                    child.left, child.top,
                    child.left + child.measuredWidth, child.top + child.measuredHeight
                )

                continue
            }

            val oldPos = preAnimVisualPos[child]
            child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)

            if (child === activeItem) {
                continue
            }

            if (oldPos == null) {
                child.translationX = 0f
                child.translationY = 0f

                continue
            }

            val startTransX = oldPos.x - left
            val startTransY = oldPos.y - top

            val distance = sqrt(
                (startTransX.toDouble() * startTransX) + (startTransY.toDouble() * startTransY)
            )

            if (distance > 2) { // Small threshold to avoid micro-animations
                child.translationX = startTransX.toFloat()
                child.translationY = startTransY.toFloat()

                child.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .setDuration(
                        min(
                            Animation.LONG.duration.toDouble(),
                            max(Animation.SHORT.duration.toDouble(), distance * 0.8)
                        ).toLong()
                    )
                    .setInterpolator(DecelerateInterpolator(1.2f))
                    .start()
            } else {
                child.translationX = 0f
                child.translationY = 0f
            }
        }

        preAnimVisualPos.clear()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean =
        isRearrangeable || super.onInterceptTouchEvent(ev)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isRearrangeable) {
            return super.onTouchEvent(event)
        }

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val item = getChildAtPos(x, y) ?: return true
                activeItem = item

                captureLayoutState()

                lastTouchX = x
                lastTouchY = y
                initialTouchX = x
                initialTouchY = y

                isResizing = item.isResizeHandleTouched(x - item.left, y - item.top)

                val layoutParams = item.gridParams()
                val original = GridLayoutParams(
                    layoutParams.col, layoutParams.row,
                    layoutParams.colSpan, layoutParams.rowSpan
                )
                originalParams = original

                lastReorderCol = original.col
                lastReorderRow = original.row
                pendingTargetCol = original.col
                pendingTargetRow = original.row

                item.bringToFront()

                if (!isResizing) {
                    item.setTag(R.id.is_dragging_tag, true)
                }

                forEachItem {
                    it.animateToState(
                        if (it === item) DraggableGridItem.STATE_ACTIVE
                        else DraggableGridItem.STATE_INACTIVE
                    )
                }

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeItem != null) {
                    if (isResizing) {
                        handleResize(x, y)
                    } else {
                        handleDrag(x - lastTouchX, y - lastTouchY)
                    }

                    lastTouchX = x
                    lastTouchY = y
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeItem != null) {
                    reorderRunnable?.let { runnable ->
                        handler.removeCallbacks(runnable)

                        runnable.run()
                        reorderRunnable = null
                    }

                    if (isResizing) {
                        finishResize()
                    } else {
                        finishDrag()
                    }

                    forEachItem { it.animateToState(DraggableGridItem.STATE_IDLE) }
                }

                clearCurrentHints()
                resetHoverState()
            }
        }

        return true
    }

    private fun handleResize(currX: Float, currY: Float) {
        val item = activeItem ?: return
        val original = originalParams ?: return

        val rawVisualW = original.colSpan * cellWidth + (currX - initialTouchX)
        val rawVisualH = original.rowSpan * cellHeight + (currY - initialTouchY)

        var minAllowedW = (item.minColSpan * cellWidth).toFloat()
        if (item.minWidth > 0) {
            minAllowedW = max(minAllowedW, item.minWidth.toFloat())
        }

        var maxAllowedW = (width - paddingLeft - paddingRight).toFloat()
        if (item.maxColSpan > 0) {
            maxAllowedW = min(maxAllowedW, (item.maxColSpan * cellWidth).toFloat())
        }
        if (item.maxWidth > 0) {
            maxAllowedW = min(maxAllowedW, item.maxWidth.toFloat())
        }

        var minAllowedH = (item.minRowSpan * cellHeight).toFloat()
        if (item.minHeight > 0) {
            minAllowedH = max(minAllowedH, item.minHeight.toFloat())
        }

        var maxAllowedH = (height - paddingTop - paddingBottom).toFloat()
        if (item.maxRowSpan > 0) {
            maxAllowedH = min(maxAllowedH, (item.maxRowSpan * cellHeight).toFloat())
        }
        if (item.maxHeight > 0) {
            maxAllowedH = min(maxAllowedH, item.maxHeight.toFloat())
        }

        val clampedVisualW = max(minAllowedW, min(rawVisualW, maxAllowedW))
        val clampedVisualH = max(minAllowedH, min(rawVisualH, maxAllowedH))

        item.setVisualResizeBounds(clampedVisualW, clampedVisualH)

        var spanX = calculateGridSpan(clampedVisualW, cellWidth)
        var spanY = calculateGridSpan(clampedVisualH, cellHeight)

        spanX = max(
            item.minColSpan,
            if (item.maxColSpan > 0) min(spanX, item.maxColSpan) else spanX
        )
        spanY = max(
            item.minRowSpan,
            if (item.maxRowSpan > 0) min(spanY, item.maxRowSpan) else spanY
        )

        if (original.col + spanX > colCount) {
            spanX = colCount - original.col
        }

        val potentialRect = Rect(
            original.col, original.row, original.col + spanX, original.row + spanY
        )

        if (getCollisions(potentialRect, item).isEmpty()) {
            val layoutParams = item.gridParams()

            if (layoutParams.colSpan != spanX || layoutParams.rowSpan != spanY) {
                layoutParams.colSpan = spanX
                layoutParams.rowSpan = spanY

                item.layoutParams = layoutParams
            }
        }
    }

    private fun finishResize() {
        val itemToResize = activeItem

        activeItem = null
        isResizing = false

        if (itemToResize == null) {
            return
        }

        val layoutParams = itemToResize.gridParams()

        itemToResize.animateVisualResize(
            (layoutParams.colSpan * cellWidth).toFloat(),
            (layoutParams.rowSpan * cellHeight).toFloat()
        ) {
            itemToResize.isResizingActive = isRearrangeable

            saveLayoutState()
            requestLayout()
        }
    }

    private fun handleDrag(dx: Float, dy: Float) {
        val item = activeItem ?: return

        // Move the view visually
        val parentLeft = paddingLeft
        val parentTop = paddingTop
        val parentRight = width - paddingRight
        val parentBottom = height - paddingBottom

        val clampedX = max(parentLeft, min(item.left + dx.toInt(), parentRight - item.width))
        val clampedY = max(parentTop, min(item.top + dy.toInt(), parentBottom - item.height))

        item.offsetLeftAndRight(clampedX - item.left)
        item.offsetTopAndBottom(clampedY - item.top)

        // Compute which cell we are hovering over
        val layoutParams = item.gridParams()
        val relativeLeft = (item.left - parentLeft).toFloat()
        val relativeTop = (item.top - parentTop).toFloat()

        val targetCol = max(
            0, min(
                ((relativeLeft + (cellWidth / 2f)) / cellWidth).toInt(),
                colCount - layoutParams.colSpan
            )
        )
        val targetRow = max(
            0, min(
                ((relativeTop + (cellHeight / 2f)) / cellHeight).toInt(),
                rowCount - layoutParams.rowSpan
            )
        )

        // Check if we are hovering over a different cell
        // than the one we are currently waiting for
        if (targetCol == pendingTargetCol && targetRow == pendingTargetRow) {
            return
        }

        // If we move, cancel the previous timer
        // Because this block only runs if targetCol or targetRow changes,
        // tiny jitters inside the same cell will NOT trigger this and thus
        // NOT reset the timer.
        reorderRunnable?.let {
            handler.removeCallbacks(it)
            reorderRunnable = null
        }

        clearCurrentHints()

        pendingTargetCol = targetCol
        pendingTargetRow = targetRow

        // Don't start a timer if we are just hovering over where the item already is
        if (targetCol == lastReorderCol && targetRow == lastReorderRow) {
            return
        }

        val currentState = buildCurrentState()
        val targetRect = Rect(
            targetCol, targetRow,
            targetCol + layoutParams.colSpan, targetRow + layoutParams.rowSpan
        )
        val isSpotOccupied = currentState.isOccupied(targetRect, item)

        if (isSpotOccupied) {
            applyHint(targetCol, targetRow, layoutParams.colSpan, layoutParams.rowSpan)
        }

        val runnable = Runnable {
            if (isAnimationRunning()) {
                clearCurrentHints()

                return@Runnable
            }

            val simulated = simulateMove(
                currentState, item, targetCol, targetRow,
                layoutParams.colSpan, layoutParams.rowSpan
            )

            if (simulated != null) {
                clearCurrentHints()

                if (isSpotOccupied) {
                    applySimulatedStateVisually(simulated)
                } else {
                    clearVisualNudges()
                }

                commitState(simulated)
                lastReorderCol = targetCol
                lastReorderRow = targetRow
            }
        }
        reorderRunnable = runnable

        handler.postDelayed(runnable, REORDER_DELAY_MS)
    }

    private fun applyHint(targetCol: Int, targetRow: Int, colSpan: Int, rowSpan: Int) {
        clearCurrentHints()

        val targetRect = Rect(
            targetCol, targetRow, targetCol + colSpan, targetRow + rowSpan
        )

        for (index in 0 until childCount) {
            val child = getChildAt(index)

            if (child === activeItem || !Rect.intersects(targetRect, child.gridRect())) {
                continue
            }

            hintedViews.add(child)
            child.animate()
                .scaleX(HINT_SCALE)
                .scaleY(HINT_SCALE)
                .alpha(HINT_ALPHA)
                .setDuration(Animation.MEDIUM.duration.toLong())
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun clearCurrentHints() {
        for (view in hintedViews) {
            view.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1f)
                .setDuration(Animation.EXTENDED.duration.toLong())
                .start()
        }

        hintedViews.clear()
    }

    private fun clearVisualNudges() {
        for (index in 0 until childCount) {
            val child = getChildAt(index)

            if (child !== activeItem &&
                (child.translationX != 0f || child.translationY != 0f)
            ) {
                child.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .setDuration(Animation.SHORT.duration.toLong())
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }

    private fun applySimulatedStateVisually(state: GridState) {
        for ((view, targetRect) in state.placements) {
            if (view === activeItem) {
                continue
            }

            val targetLeft = paddingLeft + (targetRect.left * cellWidth)
            val targetTop = paddingTop + (targetRect.top * cellHeight)

            runningAnimations++
            view.animate()
                .translationX((targetLeft - view.left).toFloat())
                .translationY((targetTop - view.top).toFloat())
                .setDuration(Animation.MEDIUM.duration.toLong())
                .setInterpolator(DecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    private var handled = false

                    private fun finish() {
                        if (!handled) {
                            handled = true
                            runningAnimations--
                        }
                    }

                    override fun onAnimationEnd(animation: Animator) = finish()

                    override fun onAnimationCancel(animation: Animator) = finish()
                })
                .start()
        }
    }

    private fun commitState(state: GridState) {
        captureLayoutState()

        for ((view, rect) in state.placements) {
            val layoutParams = view.gridParams()

            layoutParams.col = rect.left
            layoutParams.row = rect.top
            layoutParams.colSpan = rect.width()
            layoutParams.rowSpan = rect.height()
        }

        requestLayout()
        saveLayoutState()
    }

    private fun finishDrag() {
        val item = activeItem ?: return

        val layoutParams = item.gridParams()

        val relativeLeft = (item.left - paddingLeft).toFloat()
        val relativeTop = (item.top - paddingTop).toFloat()

        val rawCol = ((relativeLeft + (cellWidth / 2f)) / cellWidth).toInt()
        val rawRow = ((relativeTop + (cellHeight / 2f)) / cellHeight).toInt()

        val targetCol = max(0, min(rawCol, colCount - layoutParams.colSpan))
        val targetRow = max(0, min(rawRow, rowCount - layoutParams.rowSpan))

        val simulated = simulateMove(
            buildCurrentState(), item, targetCol, targetRow,
            layoutParams.colSpan, layoutParams.rowSpan
        )

        if (simulated != null) {
            commitState(simulated)
        }

        item.setTag(R.id.is_dragging_tag, null)
        captureLayoutState()

        activeItem = null

        requestLayout()
        saveLayoutState()
    }

    private fun resetHoverState() {
        reorderRunnable?.let {
            handler.removeCallbacks(it)
            reorderRunnable = null
        }

        currentHoverTarget?.let {
            it.animate()
                .translationX(0f)
                .translationY(0f)
                .setDuration(Animation.MEDIUM.duration.toLong())
                .start()

            currentHoverTarget = null
        }
    }

    private fun calculateGridSpan(visualSize: Float, cellSize: Int): Int =
        max(1, (visualSize / cellSize).roundToInt())

    private fun captureLayoutState() {
        preAnimVisualPos.clear()

        for (index in 0 until childCount) {
            val view = getChildAt(index)
            preAnimVisualPos[view] = Point(view.x.toInt(), view.y.toInt())
        }
    }

    private fun getChildAtPos(x: Float, y: Float): DraggableGridItem? {
        for (index in childCount - 1 downTo 0) {
            val child = getChildAt(index)

            if (x >= child.left && x <= child.right && y >= child.top && y <= child.bottom) {
                return child as DraggableGridItem
            }
        }

        return null
    }

    private fun getCollisions(targetRect: Rect, ignoreView: View?): List<View> {
        val collisions = ArrayList<View>()

        for (index in 0 until childCount) {
            val child = getChildAt(index)

            if (child !== ignoreView && Rect.intersects(targetRect, child.gridRect())) {
                collisions.add(child)
            }
        }

        return collisions
    }

    private fun saveLayoutState() {
        val existing = templateManager.getLayoutForSize(colCount, rowCount)

        for (index in 0 until childCount) {
            val childItem = getChildAt(index) as DraggableGridItem
            val layoutParams = childItem.gridParams()

            val data = ItemLayoutData(
                childItem.itemId,
                layoutParams.col, layoutParams.row,
                layoutParams.colSpan, layoutParams.rowSpan
            )

            data.minColSpan = childItem.minColSpan
            data.minWidth = childItem.minWidth
            data.maxColSpan = childItem.maxColSpan
            data.maxWidth = childItem.maxWidth

            data.minRowSpan = childItem.minRowSpan
            data.minHeight = childItem.minHeight
            data.maxRowSpan = childItem.maxRowSpan
            data.maxHeight = childItem.maxHeight

            existing[childItem.itemId!!] = data
        }

        templateManager.saveUserLayout(colCount, rowCount, existing)
    }

    private fun simulateMove(
        initialState: GridState, item: View,
        targetCol: Int, targetRow: Int, spanX: Int, spanY: Int
    ): GridState? {
        val state = initialState.cloneState()
        val targetRect = Rect(targetCol, targetRow, targetCol + spanX, targetRow + spanY)

        if (targetRect.right > state.cols || targetRect.bottom > state.rows) {
            return null
        }

        state.placements.remove(item) // Prevent self-collision logic
        val collisions = state.getCollisions(targetRect, null)
        state.placements[item] = targetRect // Claim space for dragged item

        if (collisions.isEmpty()) {
            return state
        }

        // Resolve overlaps deterministically
        for (victim in collisions) {
            if (!resolveCollision(state, victim, targetCol, targetRow)) {
                return null // Dead end, move is invalid
            }
        }

        return state
    }

    private fun resolveCollision(
        state: GridState, victim: View, activeCol: Int, activeRow: Int
    ): Boolean {
        val victimRect = state.placements[victim] ?: return false

        val pushX = (activeCol - victimRect.left).compareTo(0)
        val pushY = (activeRow - victimRect.top).compareTo(0)

        val directions = if (abs(pushX) > abs(pushY)) {
            arrayOf(
                intArrayOf(-1, 0), // prefer left movement
                intArrayOf(pushX, 0),
                intArrayOf(0, pushY),
                intArrayOf(0, -pushY),
                intArrayOf(-pushX, 0)
            )
        } else {
            arrayOf(
                intArrayOf(0, 1), // prefer down movement
                intArrayOf(0, pushY),
                intArrayOf(pushX, 0),
                intArrayOf(-pushX, 0),
                intArrayOf(0, -pushY)
            )
        }

        for (dir in directions) {
            if (dir[0] == 0 && dir[1] == 0) {
                continue
            }

            val testRect = Rect(
                victimRect.left + dir[0], victimRect.top + dir[1],
                victimRect.right + dir[0], victimRect.bottom + dir[1]
            )

            if (!state.isOccupied(testRect, victim)) {
                state.placements[victim] = testRect

                return true
            }
        }

        // Fallback
        val closest = findClosestFreeSpotInState(
            state, victimRect.width(), victimRect.height(),
            victimRect.left, victimRect.top, victim
        )

        if (closest != null) {
            state.placements[victim] = closest

            return true
        }

        return false
    }

    private fun buildCurrentState(): GridState {
        val state = GridState(colCount, rowCount)

        for (index in 0 until childCount) {
            val child = getChildAt(index)
            state.placements[child] = child.gridRect()
        }

        return state
    }

    /**
     * Every child of this layout is a [DraggableGridItem] by construction, so
     * the walk and the cast are the same three lines everywhere they appear.
     */
    private inline fun forEachItem(action: (DraggableGridItem) -> Unit) {
        for (index in 0 until childCount) {
            val child = getChildAt(index) ?: continue

            action(child as DraggableGridItem)
        }
    }

    private fun View.gridParams(): GridLayoutParams = layoutParams as GridLayoutParams

    private fun View.gridRect(): Rect = gridParams().toRect()

    private class GridState(val cols: Int, val rows: Int) {
        val placements = HashMap<View, Rect>()

        fun cloneState(): GridState {
            val copy = GridState(cols, rows)
            copy.placements.putAll(placements)

            return copy
        }

        fun getCollisions(targetRect: Rect, ignoreView: View?): List<View> =
            placements.entries
                .filter { it.key !== ignoreView && Rect.intersects(targetRect, it.value) }
                .map { it.key }

        fun isOccupied(rect: Rect, ignoreView: View?): Boolean {
            if (rect.left < 0 || rect.top < 0 || rect.right > cols || rect.bottom > rows) {
                return true
            }

            return getCollisions(rect, ignoreView).isNotEmpty()
        }
    }

    /**
     * Named apart from [ViewGroup.LayoutParams] so the two do not shadow each
     * other inside this class.
     */
    private class GridLayoutParams(
        var col: Int,
        var row: Int,
        var colSpan: Int,
        var rowSpan: Int
    ) : LayoutParams(MATCH_PARENT, MATCH_PARENT) {

        fun toRect(): Rect = Rect(col, row, col + colSpan, row + rowSpan)
    }

    class ItemLayoutData(
        @JvmField var id: String?,
        @JvmField var col: Int,
        @JvmField var row: Int,
        @JvmField var colSpan: Int,
        @JvmField var rowSpan: Int
    ) {
        @JvmField
        var minColSpan: Int = 1

        @JvmField
        var minWidth: Int = -1

        @JvmField
        var maxColSpan: Int = -1

        @JvmField
        var maxWidth: Int = -1

        @JvmField
        var minRowSpan: Int = 1

        @JvmField
        var minHeight: Int = -1

        @JvmField
        var maxRowSpan: Int = -1

        @JvmField
        var maxHeight: Int = -1

        /**
         * Takes the span and size limits from [other], falling back to the
         * defaults for anything it does not set.
         */
        internal fun copyBoundsFrom(other: ItemLayoutData) {
            minColSpan = if (other.minColSpan > 0) other.minColSpan else 1
            minWidth = if (other.minWidth > 0) other.minWidth else -1
            maxColSpan = if (other.maxColSpan > 0) other.maxColSpan else -1
            maxWidth = if (other.maxWidth > 0) other.maxWidth else -1

            minRowSpan = if (other.minRowSpan > 0) other.minRowSpan else 1
            minHeight = if (other.minHeight > 0) other.minHeight else -1
            maxRowSpan = if (other.maxRowSpan > 0) other.maxRowSpan else -1
            maxHeight = if (other.maxHeight > 0) other.maxHeight else -1
        }
    }

    companion object {
        const val TAG: String = "DynamicGridLayout"

        private const val MIN_CELL_SIZE_DP = 110
        private const val MIN_COLS_PORTRAIT = 4
        private const val MIN_COLS_LANDSCAPE = 5
        private const val MIN_ROWS_PORTRAIT = 5
        private const val MIN_ROWS_LANDSCAPE = 3
        private const val REORDER_DELAY_MS = 300L
        private const val HINT_SCALE = 0.85f
        private const val HINT_ALPHA = 0.85f
    }
}
