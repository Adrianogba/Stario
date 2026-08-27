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

package adrianogba.stario.launcher.sheet

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.activities.launcher.Launcher
import adrianogba.stario.launcher.sheet.behavior.SheetBehavior
import adrianogba.stario.launcher.ui.utils.UiUtils
import kotlin.math.abs
import kotlin.math.sign

class SheetsFocusController @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val wrappers = arrayOfNulls<SheetWrapper>(SheetType.entries.size)
    private val targetPointers = ArrayList<Int>()
    private val systemGestureInsets = Rect()
    private val moveSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private var pendingCheckForLongPress: CheckForLongPress? = null
    private var longClickListener: OnLongClickListener? = null
    private var slideListener: SheetDialog.OnSlideListener? = null
    private var dispatchedMoveEvent = false
    private var sheetType: SheetType? = null

    // Read from the static touch listener below, which is why it cannot be a
    // local of the gesture handling.
    private var hasPerformedLongPress = false

    private var deltaX = 0f
    private var deltaY = 0f
    private var x = 0f
    private var y = 0f

    var isControllerEnabled = true

    val lastX: Float
        get() = x

    val lastY: Float
        get() = y

    private fun isTouchInSystemInsets(x: Float, y: Float): Boolean {
        val params = layoutParams as MarginLayoutParams

        return x < systemGestureInsets.left + params.leftMargin ||
                x - params.leftMargin > (width - systemGestureInsets.right) ||
                y < systemGestureInsets.top + params.topMargin ||
                y - params.topMargin > (height - systemGestureInsets.bottom)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isControllerEnabled) {
            return super.onInterceptTouchEvent(ev)
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isTouchInSystemInsets(ev.rawX, ev.rawY)) {
                    return false
                }

                targetPointers.clear()
                targetPointers.add(0)

                x = ev.getX(getPointer(ev))
                y = ev.getY(getPointer(ev))

                deltaX = 0f
                deltaY = 0f
                dispatchedMoveEvent = false

                postCheckForLongClick()

                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (targetPointers.isEmpty()) {
                    return false
                }

                deltaX = x - ev.getX(getPointer(ev))
                deltaY = y - ev.getY(getPointer(ev))

                val movedEnough = abs(deltaX) >= moveSlop || abs(deltaY) >= moveSlop
                if (movedEnough) {
                    removeCheck()
                }

                return movedEnough
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCheck()

                sheetType = null
                targetPointers.clear()

                return false
            }

            else -> return false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!isControllerEnabled) {
            return super.onTouchEvent(ev)
        }

        val action = ev.action

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            dispatchSheetMotionEvent(MotionEvent.obtain(ev))

            removeCheck()

            sheetType = null
            targetPointers.clear()
        } else if (action == MotionEvent.ACTION_DOWN) {
            if (isTouchInSystemInsets(ev.rawX, ev.rawY)) {
                return false
            }

            targetPointers.add(0, 0)

            x = ev.getX(getPointer(ev))
            y = ev.getY(getPointer(ev))

            deltaX = 0f
            deltaY = 0f

            dispatchedMoveEvent = false
            dispatchSheetMotionEvent(MotionEvent.obtain(ev))
            postCheckForLongClick()
        } else {
            if (hasPerformedLongPress) {
                hideAllSheets()
                removeCheck()

                return super.onTouchEvent(ev)
            }

            deltaY = y - ev.getY(getPointer(ev))
            deltaX = x - ev.getX(getPointer(ev))

            if (ev.action == MotionEvent.ACTION_MOVE &&
                (abs(deltaX) >= moveSlop || abs(deltaY) >= moveSlop)
            ) {
                if (sheetType == null) {
                    // The larger axis picks which edge the drag belongs to, and
                    // its sign picks which of that axis' two sheets.
                    val type = if (abs(deltaY) > abs(deltaX)) {
                        if (sign(deltaY) >= 0) SheetType.BOTTOM_SHEET else SheetType.TOP_SHEET
                    } else {
                        if (sign(deltaX) >= 0) SheetType.RIGHT_SHEET else SheetType.LEFT_SHEET
                    }
                    sheetType = type

                    hideAllSheets(type)
                }

                removeCheck()
            }

            dispatchSheetMotionEvent(sheetType, MotionEvent.obtain(ev))
        }

        return true
    }

    override fun dispatchApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val compat = WindowInsetsCompat.toWindowInsetsCompat(insets)
        val gestureInsets = compat.getInsets(WindowInsetsCompat.Type.systemGestures())

        systemGestureInsets.set(
            gestureInsets.left,
            gestureInsets.top,
            gestureInsets.right,
            gestureInsets.bottom
        )

        return super.dispatchApplyWindowInsets(insets)
    }

    private fun getPointer(event: MotionEvent): Int {
        if (targetPointers.isEmpty()) {
            return 0
        }

        val pointerIndex = event.findPointerIndex(targetPointers[0])

        return pointerIndex.coerceIn(0, event.pointerCount - 1)
    }

    private fun removeCheck() {
        pendingCheckForLongPress?.let {
            removeCallbacks(it)

            pendingCheckForLongPress = null
        }
    }

    private fun postCheckForLongClick() {
        if (pendingCheckForLongPress == null) {
            hasPerformedLongPress = false

            val check = CheckForLongPress()
            pendingCheckForLongPress = check

            postDelayed(check, ViewConfiguration.getLongPressTimeout().toLong())
        }
    }

    override fun setOnLongClickListener(listener: OnLongClickListener?) {
        longClickListener = listener
    }

    override fun performLongClick(): Boolean = false // override default long click logic

    override fun cancelLongPress() {
        super.cancelLongPress()

        removeCheck()
    }

    fun updateSheetSystemUI(value: Boolean) {
        for (wrapper in wrappers) {
            wrapper?.dialogFragment?.updateSheetSystemUI(value)
        }
    }

    fun hasSheetFocus(): Boolean = sheetType != null && dispatchedMoveEvent

    private inner class CheckForLongPress : Runnable {
        private val originalWindowAttachCount = windowAttachCount

        override fun run() {
            // Window focus is ignored on purpose: a shown sheet is ready to
            // intercept input, and this still has to fire underneath it.
            if (parent == null ||
                originalWindowAttachCount != windowAttachCount ||
                hasPerformedLongPress
            ) {
                return
            }

            if (longClickListener?.onLongClick(this@SheetsFocusController) != true) {
                return
            }

            hasPerformedLongPress = true

            pendingCheckForLongPress?.let { removeCallbacks(it) }

            for (instance in wrappers) {
                val fragment = instance?.dialogFragment ?: continue

                if (fragment.getBehavior() != null) {
                    fragment.getSheetDialog()?.hide()
                }
            }
        }
    }

    fun setSlideListener(slideListener: SheetDialog.OnSlideListener?) {
        this.slideListener = slideListener
    }

    fun removeSheetDialog(dialogFragmentClass: List<Class<out SheetDialogFragment>>) {
        for (clazz in dialogFragmentClass) {
            for (index in wrappers.indices) {
                val fragment = wrappers[index]?.dialogFragment ?: continue

                if (fragment.javaClass == clazz) {
                    if (fragment.isAdded) {
                        fragment.dismissAllowingStateLoss()
                    }

                    wrappers[index] = null

                    break
                }
            }
        }
    }

    fun moveSheetDialog(
        launcher: Launcher,
        dialogFragmentClass: List<Class<out SheetDialogFragment>>
    ) {
        removeSheetDialog(dialogFragmentClass)
        addSheetDialog(launcher, dialogFragmentClass)
    }

    fun addSheetDialog(
        launcher: Launcher,
        dialogFragmentClass: List<Class<out SheetDialogFragment>>
    ) {
        val manager = launcher.supportFragmentManager

        for (clazz in dialogFragmentClass) {
            val type = SheetType.getSheetTypeForSheetDialogFragment(launcher, clazz)

            if (type == null || type == SheetType.UNDEFINED || wrappers[type.ordinal] != null) {
                continue
            }

            try {
                val existingFragment = manager.findFragmentByTag(type.toString())

                val fragment = if (clazz.isInstance(existingFragment)) {
                    existingFragment as SheetDialogFragment
                } else {
                    if (existingFragment != null) {
                        manager.beginTransaction()
                            .remove(existingFragment)
                            .commitNowAllowingStateLoss()
                    }

                    clazz.getConstructor(SheetType::class.java).newInstance(type)
                }

                wrappers[type.ordinal] = SheetWrapper(launcher, type, fragment)
            } catch (exception: Exception) {
                throw RuntimeException(
                    clazz.name + "(" + SheetType::class.java.name + ")" +
                            "has to be visible to public scope."
                )
            }
        }
    }

    private fun dispatchSheetMotionEvent(event: MotionEvent) {
        for (wrapper in wrappers) {
            val fragment = wrapper?.dialogFragment ?: continue

            if (fragment.isAdded && fragment.onMotionEvent(event) &&
                event.action == MotionEvent.ACTION_MOVE
            ) {
                dispatchedMoveEvent = true
            }
        }
    }

    private fun dispatchSheetMotionEvent(type: SheetType?, event: MotionEvent) {
        if (type == null) {
            return
        }

        // Another sheet that is open or on its way there owns the gesture, so
        // this one stays out of it entirely.
        for (index in wrappers.indices) {
            if (index == type.ordinal) {
                continue
            }

            val state = wrappers[index]?.dialogFragment?.getBehavior()?.state

            if (state == SheetBehavior.STATE_SETTLING || state == SheetBehavior.STATE_EXPANDED) {
                return
            }
        }

        val wrapper = wrappers[type.ordinal]

        if (wrapper == null) {
            if (type == SheetType.TOP_SHEET) {
                UiUtils.expandStatusBar(context)
            }

            return
        }

        if (!wrapper.dialogFragment.isAdded) {
            wrapper.show()

            return
        }

        if (wrapper.dialogFragment.onMotionEvent(event) &&
            event.action == MotionEvent.ACTION_MOVE
        ) {
            dispatchedMoveEvent = true
        }
    }

    fun hideAllSheets(vararg keepVisible: SheetType) {
        for (index in wrappers.indices) {
            if (keepVisible.any { it.ordinal == index }) {
                continue
            }

            val fragment = wrappers[index]?.dialogFragment ?: continue

            if (fragment.getSheetDialog() != null) {
                fragment.hide(false)
            }
        }
    }

    fun interface OnDragStartListener {
        fun onDragStart()
    }

    interface OnLongClickEventListener {
        fun onDown(duration: Long)

        fun onFinished()
    }

    /**
     * Kotlin cannot reach an inner class' private members from the class that
     * encloses it, so the constructor and the fragment are visible where the
     * Java version kept both private. Nothing outside this file touches either.
     */
    inner class SheetWrapper internal constructor(
        launcher: Launcher,
        type: SheetType,
        val dialogFragment: SheetDialogFragment
    ) {
        private var showRunnable: Runnable?

        init {
            dialogFragment.isCancelable = false
            dialogFragment.setOnSlideListener { slideOffset ->
                slideListener?.onSlide(slideOffset)
            }

            val manager = launcher.supportFragmentManager
            if (dialogFragment.requiresEagerInitialization() &&
                manager.findFragmentByTag(type.toString()) == null
            ) {
                manager.beginTransaction()
                    .add(dialogFragment, type.toString())
                    .commitNowAllowingStateLoss()
            }

            showRunnable = Runnable {
                val runnableManager = launcher.supportFragmentManager

                if (!runnableManager.isDestroyed &&
                    runnableManager.findFragmentByTag(type.toString()) == null
                ) {
                    dialogFragment.show(runnableManager, type.toString())
                }

                showRunnable = null
            }
        }

        fun show() {
            showRunnable?.run()
        }
    }

    companion object {
        /**
         * Use this if your view is or can be a direct or indirect child of
         * SheetFocusController.
         *
         * @param clickListener click listener to invoke on a valid gesture
         * @return touch listener
         */
        @JvmStatic
        fun createClickTouchListener(clickListener: OnClickListener?): OnTouchListener =
            createClickTouchListener(clickListener, null, null)

        /**
         * Use this if your view is or can be a direct or indirect child of
         * SheetFocusController.
         *
         * @param clickListener click listener to invoke on a valid gesture
         * @param longClickListener long click listener to invoke on a valid gesture
         * @param longClickEventListener event listener for long click.
         * `longClickListener` has to be provided.
         * @return touch listener
         */
        @JvmStatic
        fun createClickTouchListener(
            clickListener: OnClickListener?,
            longClickListener: OnLongClickListener?,
            longClickEventListener: OnLongClickEventListener?
        ): OnTouchListener = createClickTouchListener(
            clickListener, longClickListener, longClickEventListener, null, null, null
        )

        /**
         * Use this if your view is or can be a direct or indirect child of
         * SheetFocusController.
         *
         * @param clickListener click listener to invoke on a valid gesture
         * @param longClickListener long click listener to invoke on a valid gesture
         * @param longClickEventListener event listener for long click.
         * `longClickListener` has to be provided.
         * @param viewHolder target view holder
         * @param itemTouchHelper drag RecyclerView item touch helper
         * @param dragStartListener event listener for when a drag starts
         * @return touch listener
         */
        @JvmStatic
        @SuppressLint("ClickableViewAccessibility")
        fun createClickTouchListener(
            clickListener: OnClickListener?,
            longClickListener: OnLongClickListener?,
            longClickEventListener: OnLongClickEventListener?,
            viewHolder: RecyclerView.ViewHolder?,
            itemTouchHelper: ItemTouchHelper?,
            dragStartListener: OnDragStartListener?
        ): OnTouchListener = object : OnTouchListener {
            private var longPressPerformed = false
            private var longPressRunnable: Runnable? = null
            private var isClickCandidate = false
            private var isFinishedCalled = false
            private var dragStarted = false
            private var dragReady = false
            private var touchSlop: Int? = null
            private var startX = 0f
            private var startY = 0f

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                val slop = touchSlop
                    ?: ViewConfiguration.get(view.context).scaledTouchSlop
                        .also { touchSlop = it }

                val parentController = findParentController(view)

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.x
                        startY = event.y

                        longPressPerformed = false
                        isClickCandidate = true
                        isFinishedCalled = false
                        dragStarted = false
                        dragReady = false

                        if (longClickListener != null && parentController != null) {
                            parentController.cancelLongPress()
                        }

                        val runnable = Runnable {
                            dragReady = true

                            if (longClickListener != null) {
                                longPressPerformed = longClickListener.onLongClick(view)

                                triggerFinished()
                            }
                        }
                        longPressRunnable = runnable

                        val duration = ViewConfiguration.getLongPressTimeout()
                        view.postDelayed(runnable, duration.toLong())

                        if (longClickListener != null && longClickEventListener != null) {
                            longClickEventListener.onDown(duration.toLong())
                        }

                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = abs(event.x - startX)
                        val dy = abs(event.y - startY)

                        if (dx > slop || dy > slop) {
                            isClickCandidate = false

                            if (dragReady && !dragStarted &&
                                itemTouchHelper != null && viewHolder != null
                            ) {
                                triggerFinished()
                                dragStarted = true

                                view.parent?.requestDisallowInterceptTouchEvent(false)

                                itemTouchHelper.startDrag(viewHolder)

                                dragStartListener?.onDragStart()

                                return true
                            }

                            if (!dragReady) {
                                longPressRunnable?.let { view.removeCallbacks(it) }
                            }
                        }

                        return dragStarted
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let { view.removeCallbacks(it) }

                        val parentLongPressed =
                            parentController != null && parentController.hasPerformedLongPress

                        if (event.actionMasked == MotionEvent.ACTION_UP && isClickCandidate &&
                            !longPressPerformed && !parentLongPressed && clickListener != null
                        ) {
                            clickListener.onClick(view)
                        }

                        triggerFinished()

                        isClickCandidate = false
                        longPressPerformed = false
                        dragReady = false
                        dragStarted = false

                        return true
                    }

                    else -> return false
                }
            }

            private fun findParentController(view: View): SheetsFocusController? {
                var parent = view.parent

                while (parent is View) {
                    if (parent is SheetsFocusController) {
                        return parent
                    }

                    parent = parent.getParent()
                }

                return null
            }

            private fun triggerFinished() {
                if (!isFinishedCalled && longClickListener != null &&
                    longClickEventListener != null
                ) {
                    longClickEventListener.onFinished()
                    isFinishedCalled = true
                }
            }
        }
    }
}
