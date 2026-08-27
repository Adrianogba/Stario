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

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.sheet.behavior.SheetBehavior
import adrianogba.stario.launcher.sheet.briefing.dialog.BriefingDialog
import adrianogba.stario.launcher.sheet.drawer.dialog.ApplicationsDialog
import adrianogba.stario.launcher.sheet.widgets.dialog.WidgetsDialog
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.dialogs.PersistentFullscreenDialog
import adrianogba.stario.launcher.ui.utils.HomeWatcher
import adrianogba.stario.launcher.ui.utils.UiUtils
import java.util.concurrent.atomic.AtomicInteger

abstract class SheetDialogFragment : DialogFragment {

    private val destroyListeners = ArrayList<OnDestroyListener>()
    private val onShowListeners = ArrayList<OnShowListener>()

    private var slideListener: SheetDialog.OnSlideListener? = null
    private var activity: ThemedActivity? = null
    private var homeWatcher: HomeWatcher? = null
    private var receivedDragEvents = 0
    private var dialog: SheetDialog? = null
    private var type: SheetType? = null

    constructor()

    protected constructor(type: SheetType) {
        this.type = type
    }

    override fun onAttach(context: Context) {
        // Bound before the check so super.onAttach() resolves to the Context
        // overload rather than the deprecated Activity one after a smart cast.
        val attached: Context = context

        if (attached !is ThemedActivity) {
            throw RuntimeException("Parent activity is not of type ThemedActivity.")
        }

        activity = attached

        val watcher = HomeWatcher(attached)
        homeWatcher = watcher

        watcher.setOnHomePressedListener { hide(true) }
        watcher.startWatch()

        super.onAttach(attached)
    }

    override fun onDetach() {
        homeWatcher?.stopWatch()

        super.onDetach()
    }

    override fun onDestroy() {
        for (listener in destroyListeners) {
            listener.onDestroy(type)
        }

        super.onDestroy()
    }

    fun getSheetDialog(): SheetDialog? = dialog

    override fun onStop() {
        hide(false)

        super.onStop()
    }

    fun hide(animate: Boolean) {
        getBehavior()?.setState(SheetBehavior.STATE_COLLAPSED, animate)

        if (!animate) {
            dialog?.hide()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            @Suppress("DEPRECATION")
            type = savedInstanceState.getSerializable(TYPE_KEY) as? SheetType
        }

        if (type == null) {
            throw RuntimeException("SheetDialogFragment type cannot be null.")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putSerializable(TYPE_KEY, type)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // The Java version asserted here. Assertions are off on Android, so a
        // null from the factory threw on the next line anyway.
        val dialog = SheetDialogFactory.forType(type!!, activity!!, theme)!!
        this.dialog = dialog

        slideListener?.let {
            dialog.setOnSlideListener(it)

            slideListener = null
        }

        dialog.setOnShowListener {
            val state = AtomicInteger(SheetBehavior.STATE_COLLAPSED)

            dialog.getBehavior()?.addSheetCallback(object : SheetBehavior.SheetCallback {
                override fun onStateChanged(sheet: View, newState: Int) {
                    state.set(newState)

                    if (newState == SheetBehavior.STATE_DRAGGING) {
                        countDragEvent()
                    }
                }
            })

            if (receivedDragEvents == 0 && state.get() == SheetBehavior.STATE_COLLAPSED) {
                dialog.getBehavior()?.setState(SheetBehavior.STATE_EXPANDED, true)
            }

            for (listener in onShowListeners) {
                listener.onShow()
            }

            onShowListeners.clear()
        }

        UiUtils.Notch.applyNotchMargin(dialog.getContainer(), UiUtils.Notch.Treatment.CENTER)

        return dialog
    }

    private fun countDragEvent() {
        if (receivedDragEvents < Int.MAX_VALUE) {
            receivedDragEvents++
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val container = dialog?.getContainer() ?: return

        container.requestLayout()
        snapScrollingViews(container)
    }

    /**
     * A rotation can leave a scroller reporting zero offset while still drawn
     * scrolled. Nudging it by its own offset settles it back to the top.
     */
    private fun snapScrollingViews(view: View) {
        when (view) {
            is RecyclerView -> {
                if (view.computeVerticalScrollOffset() == 0) {
                    view.post { view.scrollBy(0, -view.computeVerticalScrollOffset()) }
                }

                if (view.computeHorizontalScrollOffset() == 0) {
                    view.post { view.scrollBy(-view.computeHorizontalScrollOffset(), 0) }
                }
            }

            is ScrollView -> snapScroller(view)

            is NestedScrollView -> snapScroller(view)
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                snapScrollingViews(view.getChildAt(index))
            }
        }
    }

    private fun snapScroller(view: View) {
        if (view.scrollY == 0) {
            view.post { view.scrollBy(0, -view.scrollY) }
        }

        if (view.scrollX == 0) {
            view.post { view.scrollBy(-view.scrollX, 0) }
        }
    }

    fun getType(): SheetType? = type

    fun getBehavior(): SheetBehavior<*>? = dialog?.getBehavior()

    fun onMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            countDragEvent()
        }

        return dialog?.onMotionEvent(event) == true
    }

    fun show() {
        dialog?.showDialog()
    }

    fun expand() {
        val dialog = dialog ?: return

        dialog.showDialog()

        dialog.getContainer().post {
            getBehavior()?.setState(SheetBehavior.STATE_EXPANDED, true)
        }
    }

    /**
     * Add a one time show listener
     */
    protected fun addOnShowListener(listener: OnShowListener?) {
        if (listener != null) {
            onShowListeners.add(listener)
        }
    }

    fun addOnDestroyListener(listener: OnDestroyListener?) {
        if (listener != null) {
            destroyListeners.add(listener)
        }
    }

    protected fun setOnBackPressed(listener: PersistentFullscreenDialog.OnBackPressed?) {
        dialog?.setOnBackPressed(listener)
    }

    fun setOnSlideListener(listener: SheetDialog.OnSlideListener?) {
        val dialog = dialog

        if (dialog != null) {
            dialog.setOnSlideListener(listener)
        } else {
            slideListener = listener
        }
    }

    @Suppress("DEPRECATION")
    fun updateSheetSystemUI(lightMode: Boolean) {
        val decor = dialog?.window?.decorView ?: return

        val flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        decor.systemUiVisibility = if (lightMode) {
            decor.systemUiVisibility and flags.inv()
        } else {
            decor.systemUiVisibility or flags
        }
    }

    abstract fun requiresEagerInitialization(): Boolean

    fun interface OnDestroyListener {
        fun onDestroy(type: SheetType?)
    }

    fun interface OnShowListener {
        fun onShow()
    }

    companion object {
        @JvmField
        val IMPLEMENTATIONS: List<Class<out SheetDialogFragment>> = listOf(
            ApplicationsDialog::class.java,
            WidgetsDialog::class.java,
            BriefingDialog::class.java
        )

        private const val TYPE_KEY = "DialogSheetType"
    }
}
