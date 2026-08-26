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

package adrianogba.stario.launcher.ui.recyclers.async

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.asynclayoutinflater.view.AsyncLayoutInflater
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.common.LimitingTranslationFrameLayout
import java.util.function.Supplier
import kotlin.math.min

abstract class AsyncRecyclerAdapter<AVH : AsyncRecyclerAdapter<AVH>.AsyncViewHolder>
@JvmOverloads constructor(
    private val activity: Activity,
    private val type: InflationType = InflationType.ASYNC
) : RecyclerView.Adapter<AVH>() {

    private val layoutInflater = AsyncLayoutInflater(activity)

    private var listener: RecyclerHeightApproximationListener? = null
    private var approximatedRecyclerHeight = HEIGHT_UNMEASURED
    private var recyclerView: RecyclerView? = null
    private var holderHeight = HEIGHT_UNMEASURED
    private var limit = if (type == InflationType.SYNCED) NO_LIMIT else 1

    abstract inner class AsyncViewHolder @JvmOverloads constructor(
        viewType: Int = DEFAULT_VIEW_TYPE
    ) : RecyclerView.ViewHolder(createHolderRoot()) {

        private var inflationListener: InflationListener? = null
        private var inflated = false

        init {
            val container = itemView as ViewGroup

            when (type) {
                InflationType.ASYNC ->
                    layoutInflater.inflate(getLayout(viewType), container) { view, _, _ ->
                        wrapContent()

                        container.addView(view)

                        postInflate()
                    }

                InflationType.SYNCED -> {
                    wrapContent()
                    container.requestLayout()

                    LayoutInflater.from(activity).inflate(getLayout(viewType), container, true)

                    postInflate()
                }
            }
        }

        private fun wrapContent() {
            val params = itemView.layoutParams
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            itemView.layoutParams = params
        }

        private fun postInflate() {
            onInflated()

            itemView.viewTreeObserver.addOnGlobalLayoutListener(
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        itemView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                        val measured = itemView.measuredHeight

                        if (holderHeight == measured) {
                            return
                        }

                        if (holderHeight != HEIGHT_UNMEASURED) {
                            Log.w(
                                TAG, "Holder height estimation for " +
                                        this@AsyncRecyclerAdapter.javaClass +
                                        "async holders changed. New estimation: " + measured
                            )
                        }

                        holderHeight = measured

                        approximateRecyclerHeight()
                    }
                })

            inflationListener?.onInflated()

            inflationListener = null
            inflated = true
        }

        internal fun setOnInflatedInternal(listener: InflationListener) {
            if (inflated) {
                listener.onInflated()
            } else {
                inflationListener = listener
            }
        }

        protected abstract fun onInflated()
    }

    fun approximateRecyclerHeight(): Int {
        if (holderHeight == HEIGHT_UNMEASURED) {
            return HEIGHT_UNMEASURED
        }

        val recyclerView = recyclerView ?: return approximatedRecyclerHeight

        val size = getTotalItemCount()
        val manager = recyclerView.layoutManager

        val newApproximation = if (manager is GridLayoutManager) {
            val spanCount = manager.spanCount

            holderHeight * (size / spanCount + if (size % spanCount == 0) 0 else 1)
        } else {
            holderHeight * size
        }

        if (approximatedRecyclerHeight == HEIGHT_UNMEASURED ||
            approximatedRecyclerHeight != newApproximation
        ) {
            approximatedRecyclerHeight = newApproximation

            listener?.onNewApproximation(newApproximation)
        }

        return approximatedRecyclerHeight
    }

    private fun createHolderRoot(): View {
        val root: ViewGroup = LimitingTranslationFrameLayout(activity)

        root.clipChildren = false
        root.clipToPadding = false
        root.layoutTransition = null

        root.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (holderHeight != HEIGHT_UNMEASURED) holderHeight else Measurements.dpToPx(50f)
        )

        return root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView

        recyclerView.recycledViewPool.setMaxRecycledViews(DEFAULT_VIEW_TYPE, MAX_POOL_SIZE)

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                removeLimit()
                recyclerView.removeOnScrollListener(this)
            }
        })
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = null
    }

    protected fun removeLimit() {
        if (limit == NO_LIMIT) {
            return
        }

        val oldLimit = limit

        limit = NO_LIMIT

        val runnable = Runnable {
            notifyItemRangeInserted(oldLimit, getTotalItemCount() - oldLimit)
        }

        val recyclerView = recyclerView

        if (recyclerView != null) {
            recyclerView.post(runnable)
        } else {
            runnable.run()
        }
    }

    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AVH =
        getHolderSupplier(viewType).get()

    override fun getItemViewType(position: Int): Int = DEFAULT_VIEW_TYPE

    final override fun onBindViewHolder(holder: AVH, position: Int) {
        holder.setOnInflatedInternal {
            if (type == InflationType.ASYNC && limit != NO_LIMIT) {
                limit++

                if (limit > getTotalItemCount()) {
                    limit = NO_LIMIT
                } else {
                    val recyclerView = recyclerView

                    if (recyclerView != null) {
                        if (recyclerView.isComputingLayout) {
                            recyclerView.post { notifyItemInserted(limit) }
                        } else {
                            notifyItemInserted(limit)
                        }
                    }
                }
            }

            onBind(holder, position)
        }
    }

    final override fun getItemCount(): Int {
        if (type == InflationType.SYNCED) {
            return getTotalItemCount()
        }

        return if (limit > 0) min(limit, getTotalItemCount()) else getTotalItemCount()
    }

    fun setRecyclerHeightApproximationListener(listener: RecyclerHeightApproximationListener?) {
        this.listener = listener

        approximateRecyclerHeight()
    }

    abstract fun getTotalItemCount(): Int

    protected abstract fun onBind(holder: AVH, position: Int)

    protected abstract fun getLayout(viewType: Int): Int

    protected abstract fun getHolderSupplier(viewType: Int): Supplier<AVH>

    fun interface InflationListener {
        fun onInflated()
    }

    fun interface RecyclerHeightApproximationListener {
        fun onNewApproximation(height: Int)
    }

    companion object {
        const val DEFAULT_VIEW_TYPE = 0

        /**
         * Was AsyncViewHolder.HEIGHT_UNMEASURED. Kotlin inner classes cannot hold
         * a companion object, and nothing outside this file referenced it.
         */
        const val HEIGHT_UNMEASURED = -1

        private const val TAG = "AsyncViewHolder"
        private const val NO_LIMIT = -1
        private const val MAX_POOL_SIZE = 20
    }
}
