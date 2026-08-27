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

package adrianogba.stario.launcher.sheet.widgets.configurator

import android.annotation.SuppressLint
import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.sheet.widgets.WidgetSize
import adrianogba.stario.launcher.sheet.widgets.dialog.WidgetsDialog
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.icons.AdaptiveIconView
import adrianogba.stario.launcher.ui.utils.animation.Animation
import adrianogba.stario.launcher.ui.widgets.RoundedWidgetHost
import adrianogba.stario.launcher.utils.Casing
import kotlin.math.max
import kotlin.math.min

class WidgetItemAdapter(
    private val activity: ThemedActivity,
    private val entry: WidgetListAdapter.WidgetGroupEntry,
    private val requestListener: WidgetConfigurator.Request?
) : RecyclerView.Adapter<WidgetItemAdapter.ViewHolder>() {

    private var targetHolder: ViewHolder? = null

    internal fun reset() {
        val holder = targetHolder ?: return

        holder.preview.animate().alpha(1f).setDuration(Animation.SHORT.duration.toLong())
        holder.options.visibility = View.INVISIBLE

        targetHolder = null
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val preview: ConstraintLayout = itemView.findViewById(R.id.preview)
        val label: TextView = itemView.findViewById(R.id.label)
        val options: View = itemView.findViewById(R.id.options)
        val small: View = itemView.findViewById(R.id.small)
        val medium: View = itemView.findViewById(R.id.medium)
        val large: View = itemView.findViewById(R.id.large)
        val xlarge: View = itemView.findViewById(R.id.xlarge)
    }

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(container.context)
                .inflate(R.layout.widget_picker_preview, container, false)
        )
    }

    @SuppressLint("ResourceType")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val info = entry.widgets[position]

        holder.label.text = Casing.toTitleCase(info.loadLabel(activity.packageManager))

        holder.itemView.setOnClickListener { toggleOptions(holder, info) }

        applySizeAvailability(holder, info)

        val params = previewParams()
        val previewImage = info.loadPreviewImage(activity, Measurements.getDotsPerInch())

        if (addLivePreview(holder, info, params, previewImage)) {
            return
        }

        addImagePreview(holder, params, previewImage)
    }

    private fun toggleOptions(holder: ViewHolder, info: AppWidgetProviderInfo) {
        if (holder.options.visibility == View.VISIBLE) {
            holder.preview.animate().alpha(1f).setDuration(Animation.SHORT.duration.toLong())
            holder.options.visibility = View.INVISIBLE

            return
        }

        holder.preview.animate().alpha(0.3f).setDuration(Animation.SHORT.duration.toLong())
        holder.options.visibility = View.VISIBLE

        requestOn(holder.small, info, WidgetSize.SMALL)
        requestOn(holder.medium, info, WidgetSize.MEDIUM)
        requestOn(holder.large, info, WidgetSize.LARGE)
        requestOn(holder.xlarge, info, WidgetSize.XLARGE)

        if (holder != targetHolder) {
            reset()
        }

        targetHolder = holder
    }

    private fun requestOn(view: View, info: AppWidgetProviderInfo, size: WidgetSize) {
        view.setOnClickListener { requestListener?.requestAddition(info, size) }
    }

    /**
     * Hides the sizes a widget will not fit into, judged by its declared target
     * cells first and its minimum pixel size second.
     */
    private fun applySizeAvailability(holder: ViewHolder, info: AppWidgetProviderInfo) {
        holder.small.visibility = View.VISIBLE
        holder.medium.visibility = View.VISIBLE
        holder.large.visibility = View.VISIBLE
        holder.xlarge.visibility = View.VISIBLE

        if (info.targetCellHeight > 0 && info.targetCellWidth > 0) {
            if (info.targetCellHeight > 3) {
                holder.small.visibility = View.GONE
                holder.medium.visibility = View.GONE
            }

            if (info.targetCellWidth > 3) {
                holder.small.visibility = View.GONE
            }
        }

        if (info.minHeight > WidgetsDialog.getWidgetCellSize()) {
            holder.small.visibility = View.GONE
            holder.medium.visibility = View.GONE
        }

        if (info.minWidth > WidgetsDialog.getWidgetCellSize()) {
            holder.small.visibility = View.GONE
        }
    }

    private fun previewParams(): ConstraintLayout.LayoutParams {
        val params = ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )

        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        params.constrainedHeight = true
        params.matchConstraintMaxHeight = Measurements.dpToPx(200f)

        return params
    }

    /**
     * Widgets that declare a previewLayout get hosted for real, so the preview
     * is the widget itself rather than a screenshot of it.
     */
    private fun addLivePreview(
        holder: ViewHolder,
        info: AppWidgetProviderInfo,
        params: ConstraintLayout.LayoutParams,
        previewImage: Drawable?
    ): Boolean {
        if (info.previewLayout == 0) {
            return false
        }

        val previewInfo = info.clone()
        previewInfo.initialLayout = info.previewLayout

        if (previewInfo.targetCellHeight > 0 && previewInfo.targetCellWidth > 0) {
            params.height = 0
            // fake bigger cell height
            params.dimensionRatio = "W," + (previewInfo.targetCellHeight * 2 + 1) + ":" +
                    (previewInfo.targetCellWidth * 2)
        } else if (previewImage != null) {
            params.height = 0
            params.dimensionRatio = "W," + aspectRatio(previewImage) + "f"
        } else {
            params.height = params.matchConstraintMaxHeight
        }

        val host = RoundedWidgetHost(activity, params)

        host.setAppWidget(-1, previewInfo)
        host.updateAppWidget(null)

        host.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val child = host.getChildAt(0)

            if (child != null) {
                val scale = min(
                    host.measuredWidth.toFloat() / child.measuredWidth,
                    host.measuredHeight.toFloat() / child.measuredHeight
                )

                if (!scale.isNaN()) {
                    child.scaleY = scale
                    child.scaleX = scale
                }
            }

            forwardGroupClicks(host, holder.itemView)
        }

        holder.preview.addView(host)

        return true
    }

    private fun addImagePreview(
        holder: ViewHolder,
        params: ConstraintLayout.LayoutParams,
        previewImage: Drawable?
    ) {
        val drawable: Drawable?

        if (previewImage == null) {
            drawable = entry.icon
            params.height = AdaptiveIconView.getMaxIconSize()
        } else {
            drawable = previewImage
            params.height = 0
            params.dimensionRatio = "W," + aspectRatio(previewImage) + "f"
            params.matchConstraintMaxHeight = min(
                params.matchConstraintMaxHeight,
                max(AdaptiveIconView.getMaxIconSize(), previewImage.intrinsicHeight)
            )
        }

        val imageView = ImageView(activity)

        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        imageView.setImageDrawable(drawable)
        imageView.layoutParams = params

        holder.preview.addView(imageView)
    }

    private fun aspectRatio(drawable: Drawable): Float =
        drawable.intrinsicHeight.toFloat() / drawable.intrinsicWidth

    override fun getItemCount(): Int = entry.widgets.size

    companion object {
        @JvmStatic
        fun forwardGroupClicks(viewGroup: ViewGroup, forwardTarget: View) {
            for (index in 0 until viewGroup.childCount) {
                val view = viewGroup.getChildAt(index)

                view.isHapticFeedbackEnabled = false
                view.setOnTouchListener(null)
                view.setOnClickListener { forwardTarget.performClick() }

                if (view is ViewGroup) {
                    forwardGroupClicks(view, forwardTarget)
                }
            }
        }
    }
}
