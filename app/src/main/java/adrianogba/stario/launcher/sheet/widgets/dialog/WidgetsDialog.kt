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

package adrianogba.stario.launcher.sheet.widgets.dialog

import android.app.Activity
import android.app.ActivityOptions
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.preferences.Vibrations
import adrianogba.stario.launcher.sheet.SheetDialogFragment
import adrianogba.stario.launcher.sheet.SheetType
import adrianogba.stario.launcher.sheet.widgets.Widget
import adrianogba.stario.launcher.sheet.widgets.WidgetSize
import adrianogba.stario.launcher.sheet.widgets.configurator.WidgetConfigurator
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.common.FadingEdgeLayout
import adrianogba.stario.launcher.ui.popup.PopupMenu
import adrianogba.stario.launcher.ui.utils.animation.Animation
import adrianogba.stario.launcher.ui.widgets.WidgetContainer
import adrianogba.stario.launcher.ui.widgets.WidgetGrid
import adrianogba.stario.launcher.ui.widgets.WidgetHost
import adrianogba.stario.launcher.ui.widgets.WidgetScroller
import java.util.PriorityQueue

class WidgetsDialog : SheetDialogFragment {
    constructor() : super()

    constructor(type: SheetType) : super(type)

    private var bindWidgetRequest: ActivityResultLauncher<Intent>? = null
    private var configurator: WidgetConfigurator? = null
    private var isConfiguratorVisible = false
    private var pendingWidgetSize: WidgetSize? = null
    private var host: WidgetHost? = null

    private lateinit var widgetStore: SharedPreferences
    private lateinit var manager: AppWidgetManager
    private lateinit var activity: ThemedActivity

    private lateinit var addWidgetContainer: View
    private lateinit var scroller: WidgetScroller
    private lateinit var placeholder: ViewGroup
    private lateinit var content: LinearLayout
    private lateinit var grid: WidgetGrid

    override fun onAttach(context: Context) {
        super.onAttach(context)

        activity = context as ThemedActivity
        manager = AppWidgetManager.getInstance(activity)
        widgetStore = activity.applicationContext.getSharedPreferences(Entry.WIDGETS)

        bindWidgetRequest = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val identifier = result.data
                    ?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1

                val size = pendingWidgetSize
                if (identifier != -1 && size != null) {
                    setupWidget(manager, identifier, size)
                }
            }

            pendingWidgetSize = null
        }
    }

    override fun requiresEagerInitialization(): Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.widget_grid, container, false)

        val content = view.findViewById<LinearLayout>(R.id.content)
        this.content = content
        placeholder = view.findViewById(R.id.placeholder)
        grid = view.findViewById(R.id.grid)
        scroller = view.findViewById(R.id.scroller)
        addWidgetContainer = view.findViewById(R.id.add_widget_container)
        val fader = view.findViewById<FadingEdgeLayout>(R.id.fader)

        val showConfiguratorListener = View.OnClickListener { showWidgetPicker() }

        placeholder.setOnClickListener(showConfiguratorListener)
        placeholder.findViewById<View>(R.id.add_widget_placeholder)
            .setOnClickListener(showConfiguratorListener)
        addWidgetContainer.findViewById<View>(R.id.add_widget)
            .setOnClickListener(showConfiguratorListener)

        content.setOnLongClickListener {
            showWidgetPicker()

            true
        }

        Measurements.addNavListener { value ->
            fader.setFadeSizes(
                Measurements.getSysUIHeight() +
                        (if (Measurements.isLandscape()) 0 else Measurements.getDefaultPadding()),
                0, value + Measurements.getDefaultPadding(), 0
            )

            // Reads the bottom padding into the top slot. Looks like a slip in
            // the original, kept as it was rather than fixed in a conversion.
            content.setPadding(
                content.paddingLeft, content.paddingBottom, content.paddingRight, value
            )
        }

        Measurements.addStatusBarListener { value ->
            fader.setFadeSizes(
                value + (if (Measurements.isLandscape()) 0 else Measurements.getDefaultPadding()),
                0, Measurements.getNavHeight() + Measurements.getDefaultPadding(), 0
            )

            content.setPadding(
                content.paddingLeft, value, content.paddingRight, content.paddingBottom
            )
        }

        setOnBackPressed {
            hide(true)

            false
        }

        val widgets = PriorityQueue<Widget>()

        for (identifier in requireWidgetHost().appWidgetIds) {
            val key = identifier.toString()

            if (!widgetStore.contains(key)) {
                requireWidgetHost().deleteAppWidgetId(identifier)

                continue
            }

            val widget = Widget.deserialize(widgetStore.getString(key, null))

            if (widget == null) {
                widgetStore.edit().remove(key).apply()
            } else if (widgets.size < MAX_COUNT) {
                widgets.add(widget)
            } else {
                requireWidgetHost().deleteAppWidgetId(identifier)
            }
        }

        val manager = AppWidgetManager.getInstance(activity)
        while (!widgets.isEmpty()) {
            val widget = widgets.poll()

            if (widget != null) {
                grid.attach(createWidgetView(manager, widget), widget)

                updatePlaceholderVisibility(View.GONE)
            }
        }

        grid.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            columnSize = grid.computeCellSize()
        }

        return view
    }

    private fun updatePlaceholderVisibility(visibility: Int) {
        placeholder.visibility = visibility

        addWidgetContainer.visibility =
            if (visibility == View.VISIBLE || Measurements.isLandscape()) View.GONE
            else View.VISIBLE
    }

    private fun showWidgetPicker() {
        var configurator = this.configurator

        if (configurator == null) {
            configurator = WidgetConfigurator(activity) { info, size ->
                addWidget(info!!, size!!)
            }

            configurator.setOnDismissListener { isConfiguratorVisible = false }

            this.configurator = configurator
        }

        if (!isConfiguratorVisible) {
            configurator.show()
            isConfiguratorVisible = true
        }
    }

    private fun addWidget(info: AppWidgetProviderInfo, size: WidgetSize) {
        if (grid.childCount <= MAX_COUNT) {
            val identifier = requireWidgetHost().allocateAppWidgetId()
            val allowed = manager.bindAppWidgetIdIfAllowed(identifier, info.profile, info.provider, null)

            if (allowed) {
                setupWidget(manager, identifier, size)
            } else {
                bindWidgetRequest?.let { request ->
                    val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)

                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, identifier)
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)

                    pendingWidgetSize = size
                    request.launch(intent)
                }
            }
        }

        configurator?.dismiss()
    }

    private fun setupWidget(manager: AppWidgetManager, identifier: Int, size: WidgetSize) {
        val widget = Widget(identifier, grid.allocatePosition(), size)
        val host = createWidgetView(manager, widget)

        if (host.appWidgetInfo.configure == null) {
            completeWidgetSetup(widget, host)

            return
        }

        try {
            val result = activity.addOnActivityResultListener(CONFIGURATION_CODE) { resultCode, _ ->
                if (resultCode == Activity.RESULT_OK) {
                    completeWidgetSetup(widget, host)
                    host.forceLayout()
                } else {
                    requireWidgetHost().deleteAppWidgetId(host.appWidgetId)
                }

                activity.removeOnActivityResultListener(CONFIGURATION_CODE)
            }

            if (result) {
                requireWidgetHost().startAppWidgetConfigureActivityForResult(
                    activity, identifier,
                    Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS or
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
                    CONFIGURATION_CODE, getActivityOptionsBundle()
                )
            } else {
                requireWidgetHost().deleteAppWidgetId(host.appWidgetId)
                activity.removeOnActivityResultListener(CONFIGURATION_CODE)
            }
        } catch (exception: ActivityNotFoundException) {
            completeWidgetSetup(widget, host)
            activity.removeOnActivityResultListener(CONFIGURATION_CODE)

            Log.w(TAG, "No configure activity found for identifier $identifier")
        }
    }

    private fun completeWidgetSetup(widget: Widget, host: AppWidgetHostView) {
        widgetStore.edit()
            .putString(widget.id.toString(), widget.serialize())
            .apply()

        grid.attach(host, widget)
        updatePlaceholderVisibility(View.GONE)
    }

    private fun createWidgetView(manager: AppWidgetManager, widget: Widget): AppWidgetHostView {
        val info = manager.getAppWidgetInfo(widget.id)
        val host = requireWidgetHost()
            .createView(activity.applicationContext, widget.id, info)

        host.setOnLongClickListener {
            Vibrations.getInstance().vibrate()

            val menu = PopupMenu(activity)
            val resources = resources

            menu.add(
                PopupMenu.Item(
                    resources.getString(R.string.remove),
                    AppCompatResources.getDrawable(activity, R.drawable.ic_delete)
                ) { deleteWidget(host) }
            )

            menu.add(
                PopupMenu.Item(
                    resources.getString(R.string.create_a_widget),
                    AppCompatResources.getDrawable(activity, R.drawable.ic_add)
                ) { showWidgetPicker() }
            )

            if (info.configure != null) {
                menu.add(
                    PopupMenu.Item(
                        resources.getString(R.string.configure_widget),
                        AppCompatResources.getDrawable(activity, R.drawable.ic_edit)
                    ) {
                        val added = activity.addOnActivityResultListener(CONFIGURATION_CODE) { _, _ ->
                            host.forceLayout()
                            activity.removeOnActivityResultListener(CONFIGURATION_CODE)
                        }

                        if (added) {
                            requireWidgetHost().startAppWidgetConfigureActivityForResult(
                                activity, widget.id,
                                Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS or
                                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
                                CONFIGURATION_CODE, getActivityOptionsBundle()
                            )
                        }
                    }
                )
            }

            if (widget.size == WidgetSize.SMALL) {
                menu.add(
                    PopupMenu.Item(
                        resources.getString(R.string.move_left),
                        AppCompatResources.getDrawable(activity, R.drawable.ic_move_left)
                    ) { moveWidgetLeftRight(widget, +1) }
                )

                menu.add(
                    PopupMenu.Item(
                        resources.getString(R.string.move_right),
                        AppCompatResources.getDrawable(activity, R.drawable.ic_move_right)
                    ) { moveWidgetLeftRight(widget, -1) }
                )
            }

            menu.add(
                PopupMenu.Item(
                    resources.getString(R.string.move_up),
                    AppCompatResources.getDrawable(activity, R.drawable.ic_move_up)
                ) { moveWidgetUpDown(widget, +1) }
            )

            menu.add(
                PopupMenu.Item(
                    resources.getString(R.string.move_down),
                    AppCompatResources.getDrawable(activity, R.drawable.ic_move_down)
                ) { moveWidgetUpDown(widget, -1) }
            )

            menu.setOnDismissListener {
                host.animate().scaleY(1f)
                    .scaleX(1f)
                    .alpha(1f)
                    .setDuration(Animation.SHORT.duration.toLong())
            }

            menu.show(activity, host, PopupMenu.PIVOT_CENTER_HORIZONTAL, true)

            true
        }

        return host
    }

    /**
     * The grid's children, ordered the way they are laid out rather than the
     * order they happen to be attached in.
     */
    private fun orderedContainers(): List<WidgetContainer> =
        (0 until grid.childCount)
            .map { grid.getChildAt(it) as WidgetContainer }
            .sortedBy { it.getPosition() }

    private fun indexOfWidget(list: List<WidgetContainer>, widget: Widget): Int =
        list.indexOfFirst { it.getWidget().id == widget.id }

    /**
     * Whether two containers are side by side on the same row, which only small
     * widgets ever are.
     */
    private fun sharesRow(container: WidgetContainer, row: Int): Boolean =
        container.getSize() == WidgetSize.SMALL && container.getOriginRow() == row

    private fun moveWidgetLeftRight(widget: Widget, direction: Int) {
        if (widget.size != WidgetSize.SMALL) {
            return
        }

        val list = orderedContainers()

        val targetIndex = indexOfWidget(list, widget)
        if (targetIndex == -1) {
            return
        }

        val container = list[targetIndex]
        val swapIndex = targetIndex + direction

        if (swapIndex < 0 || swapIndex >= list.size) {
            return
        }

        val swapContainer = list[swapIndex]
        if (!sharesRow(swapContainer, container.getOriginRow())) {
            return
        }

        val swapWidget = swapContainer.getWidget()
        val position = widget.position
        widget.position = swapWidget.position
        swapWidget.position = position

        widgetStore.edit()
            .putString(widget.id.toString(), widget.serialize())
            .putString(swapWidget.id.toString(), swapWidget.serialize())
            .apply()

        grid.reorder()
    }

    private fun moveWidgetUpDown(widget: Widget, direction: Int) {
        val list = orderedContainers()

        val targetIndex = indexOfWidget(list, widget)
        if (targetIndex == -1) {
            return
        }

        // A small widget moves together with whatever shares its row, so both
        // the block being moved and the one it trades places with are ranges
        // rather than single entries.
        val (chunkStart, chunkEnd) = rowRangeAround(list, targetIndex)

        val swapStart: Int
        val swapEnd: Int

        if (direction == -1) { // UP
            if (chunkStart == 0) {
                return
            }

            val range = rowRangeAround(list, chunkStart - 1)
            swapStart = range.first
            swapEnd = chunkStart - 1
        } else { // DOWN
            if (chunkEnd == list.size - 1) {
                return
            }

            val range = rowRangeAround(list, chunkEnd + 1)
            swapStart = chunkEnd + 1
            swapEnd = range.second
        }

        val rangeStart = minOf(chunkStart, swapStart)
        val rangeEnd = maxOf(chunkEnd, swapEnd)

        val positions = (rangeStart..rangeEnd).map { list[it].getPosition() }

        val newOrder = if (direction == -1) {
            list.subList(chunkStart, chunkEnd + 1) + list.subList(swapStart, swapEnd + 1)
        } else {
            list.subList(swapStart, swapEnd + 1) + list.subList(chunkStart, chunkEnd + 1)
        }

        val editor = widgetStore.edit()
        newOrder.forEachIndexed { index, container ->
            val moved = container.getWidget()
            moved.position = positions[index]

            editor.putString(moved.id.toString(), moved.serialize())
        }
        editor.apply()

        grid.reorder()
    }

    /**
     * The span of entries sharing a row with the one at [index], as a start and
     * end pair. Anything that is not a small widget owns its row alone, so the
     * range is just that one index.
     */
    private fun rowRangeAround(list: List<WidgetContainer>, index: Int): Pair<Int, Int> {
        var start = index
        var end = index

        val container = list[index]
        if (container.getSize() == WidgetSize.SMALL) {
            val row = container.getOriginRow()

            while (start > 0 && sharesRow(list[start - 1], row)) {
                start--
            }

            while (end < list.size - 1 && sharesRow(list[end + 1], row)) {
                end++
            }
        }

        return start to end
    }

    private fun deleteWidget(host: AppWidgetHostView) {
        val identifier = host.appWidgetId.toString()

        if (widgetStore.contains(identifier)) {
            // Only forgets the entry when it still parses. A stored value that
            // no longer deserializes is left alone rather than dropped.
            if (Widget.deserialize(widgetStore.getString(identifier, null)) != null) {
                widgetStore.edit()
                    .remove(identifier)
                    .apply()
            }
        }

        requireWidgetHost().deleteAppWidgetId(host.appWidgetId)
        grid.removeView(host.parent as View)

        updatePlaceholderVisibility(
            if (grid.childCount == 0) View.VISIBLE else View.GONE
        )
    }

    fun requireWidgetHost(): WidgetHost {
        var host = this.host

        if (host == null) {
            host = WidgetHost(activity, HOST_ID)
            host.startListening()

            this.host = host
        }

        return host
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        updatePlaceholderVisibility(placeholder.visibility)
    }

    override fun onStart() {
        super.onStart()

        host?.startListening()
    }

    override fun onStop() {
        scroller.scrollTo(0, 0)

        try {
            host?.stopListening()
        } catch (exception: Exception) {
            Log.e(TAG, "onStop: " + exception.message)
        }

        super.onStop()
    }

    companion object {
        private const val TAG = "WidgetsDialog"
        private const val HOST_ID = 219672
        private const val MAX_COUNT = 15
        private const val CONFIGURATION_CODE = 3264614

        private var columnSize = 0

        // Read through reflection by PageManager, which asks every sheet for
        // its display name.
        @JvmStatic
        fun getName(): String = "Widgets"

        @JvmStatic
        fun getWidgetCellSize(): Int = columnSize

        private fun getActivityOptionsBundle(): Bundle {
            val activityOptions = ActivityOptions.makeBasic()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                activityOptions.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                activityOptions.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
            }

            return activityOptions.toBundle()
        }
    }
}
