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

package adrianogba.stario.launcher.sheet.briefing.dialog.page

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.sheet.SheetType
import adrianogba.stario.launcher.sheet.briefing.dialog.BriefingDialog
import adrianogba.stario.launcher.sheet.briefing.dialog.page.feed.BriefingFeedList
import adrianogba.stario.launcher.sheet.briefing.rss.RSSHelper
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.common.scrollers.CustomSwipeRefreshLayout
import adrianogba.stario.launcher.ui.recyclers.RecyclerItemAnimator
import adrianogba.stario.launcher.ui.recyclers.managers.ScrollControlStaggeredGridLayoutManager
import adrianogba.stario.launcher.ui.recyclers.overscroll.OverScrollEffect
import adrianogba.stario.launcher.ui.recyclers.overscroll.OverScrollRecyclerView
import adrianogba.stario.launcher.ui.utils.LayoutSizeObserver
import adrianogba.stario.launcher.ui.utils.UiUtils
import adrianogba.stario.launcher.ui.utils.animation.Animation
import adrianogba.stario.launcher.utils.Utils
import java.util.concurrent.Future
import kotlin.math.max

class FeedPage : Fragment {
    private var swipeRefreshLayout: CustomSwipeRefreshLayout? = null
    private var manager: ScrollControlStaggeredGridLayoutManager? = null
    private var recyclerView: OverScrollRecyclerView? = null
    private var adapter: FeedPageAdapter? = null
    private var exceptionView: ViewGroup? = null
    private var fetchingView: TextView? = null
    private var runningTask: Future<*>? = null
    private var title: View? = null
    private var tabs: View? = null

    // named to avoid colliding with Fragment.getActivity()
    private lateinit var themedActivity: ThemedActivity

    private var position = 0

    constructor() {
        // default
    }

    constructor(position: Int) {
        this.position = position
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(FEED_POSITION, position)

        super.onSaveInstanceState(outState)
    }

    override fun onAttach(context: Context) {
        // kept as a Context so the super call does not resolve to the deprecated
        // onAttach(Activity) overload once context is smart cast below
        val attached: Context = context

        if (context !is ThemedActivity) {
            throw RuntimeException("Parent activity is not of type ThemedActivity.")
        }

        this.themedActivity = context

        super.onAttach(attached)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        if (savedInstanceState != null) {
            position = savedInstanceState.getInt(FEED_POSITION, -1)
        }

        val root = inflater.inflate(R.layout.articles, container, false)

        // the Java version asserted on this, which Android strips at runtime
        val containerRoot = container!!.rootView
        title = containerRoot.findViewById(R.id.title_feeds)
        tabs = containerRoot.findViewById(R.id.tabs)

        val recyclerView = root.findViewById<OverScrollRecyclerView>(R.id.recycler_view)
        val swipeRefreshLayout = root.findViewById<CustomSwipeRefreshLayout>(R.id.refresh)

        this.recyclerView = recyclerView
        this.swipeRefreshLayout = swipeRefreshLayout
        exceptionView = root.findViewById(R.id.exception)
        fetchingView = root.findViewById(R.id.fetching)

        recyclerView.itemAnimator =
            RecyclerItemAnimator(RecyclerItemAnimator.APPEARANCE, Animation.EXTENDED)

        val type = SheetType.getSheetTypeForSheetDialogFragment(
            themedActivity, BriefingDialog::class.java
        )
        if (type!!.axes == View.SCROLL_AXIS_HORIZONTAL) {
            recyclerView.overscrollPullEdges = OverScrollEffect.PULL_EDGE_BOTTOM
        }

        invalidateLayoutPadding()
        Measurements.addNavListener { value -> setRecyclerBottomPadding(value) }
        title?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            invalidateLayoutPadding()
        }
        tabs?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            invalidateLayoutPadding()
        }

        Measurements.addStatusBarListener {
            setRecyclerBottomPadding(Measurements.getNavHeight())
        }

        if (adapter == null) {
            adapter = FeedPageAdapter(themedActivity.applicationContext)
        }

        val manager = ScrollControlStaggeredGridLayoutManager(0)
        this.manager = manager

        LayoutSizeObserver.attach(root, LayoutSizeObserver.WIDTH,
            object : LayoutSizeObserver.OnChange {
                override fun onChange(view: View, watchFlags: Int, rect: Rect) {
                    manager.spanCount = max(1, rect.width() / Measurements.dpToPx(400f))
                }
            })
        manager.isItemPrefetchEnabled = true

        recyclerView.layoutManager = manager
        recyclerView.adapter = adapter

        swipeRefreshLayout.setOnRefreshListener { update() }
        swipeRefreshLayout.setOnEngageListener { engaged -> manager.setScrollEnabled(!engaged) }
        swipeRefreshLayout.setSize(SwipeRefreshLayout.LARGE)
        swipeRefreshLayout.overScrollMode = View.OVER_SCROLL_NEVER

        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(
            themedActivity.getAttributeData(
                com.google.android.material.R.attr.colorSurfaceContainer
            )
        )

        swipeRefreshLayout.setColorSchemeColors(
            themedActivity.getAttributeData(com.google.android.material.R.attr.colorSecondary),
            themedActivity.getAttributeData(com.google.android.material.R.attr.colorTertiary),
            themedActivity.getAttributeData(androidx.appcompat.R.attr.colorPrimary)
        )

        root.findViewById<View>(R.id.refresh_button)
            .setOnClickListener { UiUtils.post { update() } }

        // the Java version registered a second, identical nav listener here
        Measurements.addNavListener { bottomInset -> setRecyclerBottomPadding(bottomInset) }

        return root
    }

    private fun setRecyclerBottomPadding(bottom: Int) {
        val recyclerView = this.recyclerView!!

        recyclerView.setPadding(
            recyclerView.paddingLeft, recyclerView.paddingTop,
            recyclerView.paddingRight, bottom
        )
    }

    fun invalidateLayoutPadding() {
        val recyclerView = this.recyclerView!!

        val titleHeight = title!!.measuredHeight
        val tabsHeight = tabs!!.measuredHeight

        recyclerView.setPadding(
            recyclerView.paddingLeft, Measurements.dpToPx(15f) + titleHeight + tabsHeight,
            recyclerView.paddingRight, Measurements.getNavHeight()
        )
        exceptionView!!.setPadding(0, (titleHeight + tabsHeight) / 2, 0, 0)
        fetchingView!!.setPadding(0, (titleHeight + tabsHeight) / 2, 0, 0)
        swipeRefreshLayout!!.setProgressViewOffset(
            true, titleHeight + tabsHeight, ((titleHeight + tabsHeight) * 1.5f).toInt()
        )
    }

    override fun onResume() {
        super.onResume()
        reset()

        UiUtils.post { update() }
    }

    fun update() {
        val runningTask = this.runningTask
        if (runningTask != null && !runningTask.isDone) {
            swipeRefreshLayout!!.isRefreshing = false

            return
        }

        val adapter = this.adapter
        if (adapter == null || position < 0 ||
            position >= BriefingFeedList.from(themedActivity).size()
        ) {
            showErrorState()

            return
        }

        if (!adapter.shouldUpdate()) {
            if (adapter.itemCount == 0) {
                showErrorState()
            } else {
                showContentState(false)
            }

            return
        }

        manager!!.setScrollEnabled(false)
        exceptionView!!.visibility = View.GONE
        recyclerView!!.clearAnimation()

        if (adapter.itemCount == 0) {
            fetchingView!!.visibility = View.VISIBLE
            swipeRefreshLayout!!.visibility = View.INVISIBLE
            recyclerView!!.alpha = 0f
        } else {
            fetchingView!!.visibility = View.GONE
            swipeRefreshLayout!!.isRefreshing = true
        }

        this.runningTask = Utils.submitTask(Runnable {
            val items = RSSHelper.parse(
                BriefingFeedList.getInstance()
                    .get(position).getRSSLink()
            )

            UiUtils.post {
                if (items == null) {
                    showErrorState()

                    return@post
                }

                adapter.update(items)

                if (adapter.itemCount == 0) {
                    showErrorState()
                } else {
                    showContentState(true)
                }
            }
        })
    }

    private fun showContentState(animate: Boolean) {
        val recyclerView = this.recyclerView!!

        exceptionView!!.visibility = View.GONE
        fetchingView!!.visibility = View.GONE
        swipeRefreshLayout!!.visibility = View.VISIBLE

        manager!!.setScrollEnabled(true)
        swipeRefreshLayout!!.isRefreshing = false

        if (recyclerView.alpha == 0f) {
            recyclerView.alpha = 1f
            recyclerView.scaleX = UPDATE_SCALE
            recyclerView.scaleY = UPDATE_SCALE

            recyclerView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(if (animate) Animation.MEDIUM.duration.toLong() else 0)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        }
    }

    private fun showErrorState() {
        exceptionView!!.visibility = View.VISIBLE
        fetchingView!!.visibility = View.GONE
        swipeRefreshLayout!!.visibility = View.INVISIBLE
        recyclerView!!.alpha = 0f

        swipeRefreshLayout!!.isRefreshing = false
    }

    fun reset() {
        swipeRefreshLayout!!.isRefreshing = false
        recyclerView!!.scrollBy(0, -Int.MAX_VALUE)
    }

    val recycler: RecyclerView?
        get() = recyclerView

    companion object {
        const val FEED_POSITION: String = "com.stario.FeedTab.FEED_POSITION"

        private const val UPDATE_SCALE = 0.9f
    }
}
