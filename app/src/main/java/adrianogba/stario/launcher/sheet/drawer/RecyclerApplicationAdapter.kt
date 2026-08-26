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

package adrianogba.stario.launcher.sheet.drawer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.apps.LauncherApplication
import adrianogba.stario.launcher.apps.ProfileManager
import adrianogba.stario.launcher.apps.popup.ApplicationCustomizationDialog
import adrianogba.stario.launcher.preferences.Vibrations
import adrianogba.stario.launcher.sheet.SheetsFocusController
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.icons.AdaptiveIconView
import adrianogba.stario.launcher.ui.popup.PopupMenu
import adrianogba.stario.launcher.ui.recyclers.async.AsyncRecyclerAdapter
import adrianogba.stario.launcher.ui.recyclers.async.InflationType
import adrianogba.stario.launcher.ui.utils.animation.Animation
import adrianogba.stario.launcher.utils.Utils
import java.util.function.Supplier
import kotlin.math.min

abstract class RecyclerApplicationAdapter(
    private val activity: ThemedActivity,
    private val showLabels: Boolean,
    private val itemTouchHelper: ItemTouchHelper?,
    type: InflationType
) : AsyncRecyclerAdapter<RecyclerApplicationAdapter.ApplicationViewHolder>(activity, type) {

    constructor(activity: ThemedActivity) :
            this(activity, true, null, InflationType.ASYNC)

    constructor(activity: ThemedActivity, type: InflationType) :
            this(activity, true, null, type)

    constructor(activity: ThemedActivity, showLabel: Boolean, type: InflationType) :
            this(activity, showLabel, null, type)

    constructor(
        activity: ThemedActivity, itemTouchHelper: ItemTouchHelper?, type: InflationType
    ) : this(activity, true, itemTouchHelper, type)

    init {
        setHasStableIds(true)
    }

    open inner class ApplicationViewHolder @JvmOverloads constructor(
        viewType: Int = DEFAULT_VIEW_TYPE
    ) : AsyncViewHolder(viewType) {

        internal var icon: AdaptiveIconView? = null
        internal var label: TextView? = null
        internal var notification: View? = null

        private var dialog: PopupWindow? = null

        @SuppressLint("ClickableViewAccessibility")
        override fun onInflated() {
            itemView.isHapticFeedbackEnabled = false

            label = itemView.findViewById(R.id.label)
            icon = itemView.findViewById(R.id.icon)
            notification = itemView.findViewById(R.id.notification_dot)

            label?.setLines(getLabelLineCount())

            itemView.setOnTouchListener(
                SheetsFocusController.createClickTouchListener(
                    getOnClickListener(),
                    getOnLongClickListener(),
                    IconScaleListener(),
                    this,
                    itemTouchHelper
                ) { dialog!!.dismiss() }
            )
        }

        /**
         * Grows the icon while a long press is being held, then springs it back.
         * Driven off Choreographer rather than a ViewPropertyAnimator so the
         * growth can be cut short at whatever scale the finger left it.
         */
        private inner class IconScaleListener :
            SheetsFocusController.OnLongClickEventListener {

            private var frameCallback: Choreographer.FrameCallback? = null

            override fun onDown(duration: Long) {
                val icon = icon ?: return

                icon.animate().cancel()

                val startScale = icon.scaleX
                val endScale = AdaptiveIconView.MAX_SCALE

                cancelFrameCallback()

                if (duration <= 0) {
                    icon.scaleX = endScale
                    icon.scaleY = endScale

                    return
                }

                val startTime = SystemClock.uptimeMillis()

                val callback = object : Choreographer.FrameCallback {
                    override fun doFrame(frameTimeNanos: Long) {
                        val elapsed = SystemClock.uptimeMillis() - startTime
                        val fraction = min(1f, elapsed.toFloat() / duration)

                        val scale = startScale + fraction * (endScale - startScale)

                        icon.scaleX = scale
                        icon.scaleY = scale

                        if (fraction < 1f) {
                            Choreographer.getInstance().postFrameCallback(this)
                        } else {
                            frameCallback = null
                        }
                    }
                }

                frameCallback = callback
                Choreographer.getInstance().postFrameCallback(callback)
            }

            override fun onFinished() {
                cancelFrameCallback()

                icon?.animate()
                    ?.scaleY(1f)
                    ?.scaleX(1f)
                    ?.setInterpolator(DecelerateInterpolator())
                    ?.setDuration(Animation.SHORT.duration.toLong())
            }

            private fun cancelFrameCallback() {
                val callback = frameCallback ?: return

                Choreographer.getInstance().removeFrameCallback(callback)
                frameCallback = null
            }
        }

        private fun showPopup(application: LauncherApplication) {
            val launcherApps =
                activity.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

            val menu = PopupMenu(activity)

            if (Utils.isProfileAvailable(activity, application.getProfile())) {
                menu.addShortcuts(
                    launcherApps, getShortcutForApplication(launcherApps, application)
                )
            }

            val resources = activity.resources

            menu.add(
                PopupMenu.Item(
                    resources.getString(R.string.app_info),
                    ResourcesCompat.getDrawable(resources, R.drawable.ic_info, activity.theme)
                ) {
                    val activities = launcherApps.getActivityList(
                        application.info.packageName, application.getProfile()
                    )

                    if (activities.isNotEmpty()) {
                        try {
                            activity.getSystemService(LauncherApps::class.java)
                                .startAppDetailsActivity(
                                    activities[0].componentName,
                                    application.getProfile(), null, null
                                )
                        } catch (exception: Exception) {
                            Log.e(TAG, "Unable to launch settings", exception)
                        }
                    }
                })

            if (allowApplicationStateEditing()) {
                menu.add(
                    PopupMenu.Item(
                        resources.getString(R.string.customize),
                        ResourcesCompat.getDrawable(
                            resources, R.drawable.ic_edit, activity.theme
                        )
                    ) { ApplicationCustomizationDialog(activity, application).show() })

                menu.add(
                    PopupMenu.Item(
                        resources.getString(R.string.hide),
                        ResourcesCompat.getDrawable(
                            resources, R.drawable.ic_hide, activity.theme
                        )
                    ) {
                        ProfileManager.getInstance()
                            .getProfile(application.getProfile())
                            ?.hideApplication(application)
                    })

                if (!application.systemPackage) {
                    menu.add(
                        PopupMenu.Item(
                            resources.getString(R.string.uninstall),
                            ResourcesCompat.getDrawable(
                                resources, R.drawable.ic_delete, activity.theme
                            )
                        ) {
                            val info = application.info

                            try {
                                // https://github.com/LawnchairLauncher/lawnchair/blob/d69b89e5e1367117690580deb331ed5fb63e9068/res/values/config.xml#L25
                                val intent = Intent.parseUri(
                                    "#Intent;action=android.intent.action.DELETE;launchFlags=0x10800000;end",
                                    0
                                )
                                    .setData(Uri.fromParts("package", info.packageName, info.name))
                                    .putExtra(Intent.EXTRA_USER, application.getProfile())

                                activity.startActivity(intent)
                            } catch (exception: Exception) {
                                Log.e(TAG, "Unable to uninstall application", exception)
                            }
                        })
                }
            }

            (itemView as ViewGroup).requestDisallowInterceptTouchEvent(true)

            // the Java version dereferenced the icon inside show, so a null one still throws here
            dialog = menu.show(activity, icon!!, popupInsets(), popupPivot(), itemTouchHelper == null)
        }

        private fun popupInsets(): Rect {
            val label = label
            val icon = icon

            if (Measurements.isLandscape()) {
                val side = ((label?.measuredWidth ?: 0) - (icon?.measuredWidth ?: 0)) / 2

                return Rect(side, 0, side, 0)
            }

            val top = if (label == null) 0
            else label.measuredHeight * label.lineCount / label.maxLines

            return Rect(0, top + Measurements.dpToPx(10f), 0, 0)
        }

        private fun popupPivot(): Short =
            if (Measurements.isLandscape()) PopupMenu.PIVOT_CENTER_VERTICAL
            else PopupMenu.PIVOT_DEFAULT

        open fun getOnLongClickListener(): View.OnLongClickListener? =
            View.OnLongClickListener {
                Vibrations.getInstance().vibrate()

                val index = bindingAdapterPosition
                if (index == RecyclerView.NO_POSITION) {
                    return@OnLongClickListener false
                }

                val application = getApplication(index)
                    ?: return@OnLongClickListener false

                showPopup(application)

                true
            }

        open fun getOnClickListener(): View.OnClickListener? =
            View.OnClickListener {
                Vibrations.getInstance().vibrate()

                val index = bindingAdapterPosition
                if (index == RecyclerView.NO_POSITION) {
                    return@OnClickListener
                }

                getApplication(index)?.launch(activity)
            }

        fun setIcon(drawable: Drawable?) {
            icon?.setIcon(drawable)
        }

        fun setLabel(sequence: CharSequence?) {
            label?.text = sequence
        }

        fun hideLabel() {
            label?.animate()?.alpha(0f)?.setDuration(Animation.SHORT.duration.toLong())
        }

        fun showLabel() {
            label?.animate()?.alpha(1f)?.setDuration(Animation.SHORT.duration.toLong())
        }
    }

    private fun getShortcutForApplication(
        launcherApps: LauncherApps, application: LauncherApplication
    ): List<ShortcutInfo> {
        val shortcutQuery = LauncherApps.ShortcutQuery()
        shortcutQuery.setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
        )

        shortcutQuery.setPackage(application.info.packageName)

        return try {
            launcherApps.getShortcuts(shortcutQuery, application.getProfile()) ?: ArrayList()
        } catch (exception: SecurityException) {
            ArrayList()
        }
    }

    override fun onBind(viewHolder: ApplicationViewHolder, index: Int) {
        val application = getApplication(index)

        if (application != null) {
            viewHolder.setLabel(application.label)

            // TODO: notification dots
            viewHolder.notification?.visibility = View.GONE

            viewHolder.icon?.setApplication(application)
            viewHolder.icon?.transitionName =
                application.info.packageName + application.getProfile()
        }

        viewHolder.icon?.setTag(R.id.stagger_order_tag, index)
    }

    override fun getItemViewType(position: Int): Int {
        if (!showLabels) {
            return ONLY_ICON_LAYOUT
        }

        return super.getItemViewType(position)
    }

    override fun getLayout(viewType: Int): Int {
        if (viewType == ONLY_ICON_LAYOUT) {
            return R.layout.recycler_application_item_only_icon
        }

        return R.layout.recycler_application_item
    }

    override fun getHolderSupplier(viewType: Int): Supplier<ApplicationViewHolder> =
        Supplier { ApplicationViewHolder(viewType) }

    override fun getItemId(position: Int): Long =
        getApplication(position)?.info?.packageName?.hashCode()?.toLong() ?: 0

    protected open fun getLabelLineCount(): Int = 2

    protected abstract fun getApplication(index: Int): LauncherApplication?

    protected abstract fun allowApplicationStateEditing(): Boolean

    companion object {
        const val ONLY_ICON_LAYOUT = 1

        private const val TAG = "RecyclerApplicationAdapter"
    }
}
