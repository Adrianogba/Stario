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

package adrianogba.stario.launcher.sheet.briefing.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.preferences.Vibrations
import adrianogba.stario.launcher.sheet.SheetDialogFragment
import adrianogba.stario.launcher.sheet.SheetType
import adrianogba.stario.launcher.sheet.briefing.configurator.BriefingConfigurator
import adrianogba.stario.launcher.sheet.briefing.configurator.FeedConfigurator
import adrianogba.stario.launcher.sheet.briefing.dialog.page.feed.BriefingFeedList
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.common.pager.CustomDurationViewPager
import adrianogba.stario.launcher.ui.common.tabs.LeftTabLayout
import adrianogba.stario.launcher.ui.popup.PopupMenu
import kotlin.math.max
import kotlin.math.min

class BriefingDialog : SheetDialogFragment {

    private var recyclerToBeObserved: RecyclerView? = null
    private var listener: BriefingDialogPageListener? = null
    private var list: BriefingFeedList? = null

    private lateinit var themedActivity: ThemedActivity
    private lateinit var pager: CustomDurationViewPager
    private lateinit var adapter: BriefingAdapter
    private lateinit var placeholder: ViewGroup
    private lateinit var tabsContainer: View
    private lateinit var tabs: LeftTabLayout
    private lateinit var main: ViewGroup
    private lateinit var title: View
    private lateinit var root: View

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            updateHeader(recyclerView)
        }
    }

    private val layoutListener =
        View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateHeader(recyclerToBeObserved)
        }

    private val feedListener = object : BriefingFeedList.FeedListener {
        override fun onInserted(index: Int) = notifyUpdate()

        override fun onRemoved(index: Int) = notifyUpdate()

        override fun onUpdated(index: Int) = notifyUpdate()

        @SuppressLint("NotifyDataSetChanged")
        private fun notifyUpdate() {
            adapter.notifyDataSetChanged()
            tabs.setViewPager(pager)

            if (adapter.count > 0) {
                pager.setCurrentItem(0, true)
                observePageRecycler(0)
            } else {
                observePageRecycler(null)
            }

            updateHeader(recyclerToBeObserved)

            updateEmptyState()
        }
    }

    constructor() : super()

    constructor(type: SheetType) : super(type)

    override fun requiresEagerInitialization(): Boolean = false

    override fun onAttach(context: Context) {
        super.onAttach(context)

        themedActivity = context as ThemedActivity
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.briefing, container, false)
        this.root = root

        title = root.findViewById(R.id.title_feeds)
        placeholder = root.findViewById(R.id.placeholder)
        pager = root.findViewById(R.id.articles_container)
        tabs = root.findViewById(R.id.tabs)
        main = root.findViewById(R.id.main)

        title.isHapticFeedbackEnabled = false
        main.isHapticFeedbackEnabled = false

        pager.offscreenPageLimit = 2
        pager.overScrollMode = View.OVER_SCROLL_NEVER
        pager.isSaveEnabled = false

        val listener = BriefingDialogPageListener()
        this.listener = listener
        pager.addOnPageChangeListener(listener)

        val adapter = BriefingAdapter(themedActivity, childFragmentManager)
        this.adapter = adapter
        pager.adapter = adapter

        pager.post {
            if (adapter.count > 0) {
                observePageRecycler(pager.currentItem)
            }
        }

        val list = BriefingFeedList.from(themedActivity)
        this.list = list
        list.addOnFeedUpdateListener(feedListener)

        tabs.overScrollMode = View.OVER_SCROLL_NEVER
        tabs.setOnTabLongClickListener { tab, position -> showTabMenu(tab, position) }
        tabs.setViewPager(pager)

        tabsContainer = tabs.parent as View

        setOnBackPressed {
            hide(true)

            true
        }

        title.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            recyclerToBeObserved?.post { updateHeader(recyclerToBeObserved) }
        }

        val clickListener = object : View.OnClickListener {
            private var configurator: BriefingConfigurator? = null

            override fun onClick(view: View) {
                val configurator = this.configurator
                    ?: BriefingConfigurator(themedActivity).also { this.configurator = it }

                configurator.show()
            }
        }

        title.setOnClickListener(clickListener)
        placeholder.setOnClickListener(clickListener)
        placeholder.findViewById<View>(R.id.add_button).setOnClickListener(clickListener)

        title.setOnLongClickListener { false }
        title.setOnTouchListener { _, event ->
            getBehavior()!!.interceptTouches(
                event.action != MotionEvent.ACTION_CANCEL &&
                        event.action != MotionEvent.ACTION_UP
            )

            false
        }

        Measurements.addStatusBarListener { value -> root.setPadding(0, value, 0, 0) }
        Measurements.addNavListener { value -> placeholder.setPadding(0, 0, 0, value) }

        return root
    }

    private fun showTabMenu(tab: View, position: Int) {
        Vibrations.getInstance().vibrate()

        val resources = themedActivity.resources
        val menu = PopupMenu(themedActivity)

        menu.add(
            PopupMenu.Item(
                resources.getString(R.string.remove),
                ResourcesCompat.getDrawable(
                    resources, R.drawable.ic_delete, themedActivity.theme
                )
            ) { list?.remove(position) })

        val feed = list?.get(position)

        if (feed != null) {
            menu.add(
                PopupMenu.Item(
                    resources.getString(R.string.rename_feed),
                    ResourcesCompat.getDrawable(
                        resources, R.drawable.ic_edit, themedActivity.theme
                    )
                ) { FeedConfigurator(themedActivity, feed).show() })
        }

        menu.show(themedActivity, tab, PopupMenu.PIVOT_CENTER_HORIZONTAL)
    }

    override fun onDestroy() {
        list?.removeOnFeedUpdateListener(feedListener)

        super.onDestroy()
    }

    private fun observePageRecycler(position: Int?) {
        recyclerToBeObserved?.let {
            it.removeOnScrollListener(scrollListener)
            it.removeOnLayoutChangeListener(layoutListener)
        }

        val page = if (position == null) null else adapter.getRegisteredFragment(position)

        recyclerToBeObserved = page?.recycler

        recyclerToBeObserved?.let {
            it.addOnScrollListener(scrollListener)
            it.addOnLayoutChangeListener(layoutListener)
        }
    }

    /**
     * The title collapses as the list scrolls under it. Offset is how far the
     * first item has travelled past its resting padding, or the whole title
     * height once that item has scrolled off entirely.
     */
    private fun updateHeader(recycler: RecyclerView?) {
        val layoutManager = recycler?.layoutManager

        if (layoutManager == null) {
            updateHeader(0f)

            return
        }

        val firstView = layoutManager.findViewByPosition(0)

        val offset = if (firstView != null) {
            val padding = recycler.paddingTop +
                    // Margin of the first elements in the grid
                    Measurements.dpToPx(10f)

            max(0, padding - firstView.top)
        } else if (layoutManager.itemCount > 0) {
            title.measuredHeight
        } else {
            0
        }

        updateHeader(offset.toFloat())
    }

    private fun updateHeader(translation: Float) {
        val maxTranslation = title.measuredHeight
        val bounded = -min(maxTranslation.toFloat(), translation)

        tabsContainer.translationY = maxTranslation + bounded
        title.translationY = bounded

        val alpha = (bounded + maxTranslation / 2f) / maxTranslation * 2f

        title.alpha = alpha
        title.visibility = if (alpha > 0) View.VISIBLE else View.INVISIBLE
    }

    private fun updateTitleHeight() {
        title.layoutParams.height = if (Measurements.isLandscape()) {
            ViewGroup.LayoutParams.WRAP_CONTENT
        } else {
            Measurements.dpToPx(Measurements.HEADER_SIZE_DP.toFloat())
        }

        listener?.reset()
    }

    private fun updateEmptyState() {
        val empty = adapter.count == 0

        placeholder.visibility = if (empty) View.VISIBLE else View.GONE
        main.visibility = if (empty) View.GONE else View.VISIBLE
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        updateTitleHeight()
    }

    override fun onResume() {
        updateTitleHeight()
        updateEmptyState()

        super.onResume()
    }

    /**
     * Carries the title and tab bar between their collapsed and expanded
     * positions as pages swipe. The starting values are captured when a drag
     * begins so an interrupted swipe animates from where it actually is.
     */
    inner class BriefingDialogPageListener : ViewPager.OnPageChangeListener {
        private var startingTranslationTitle = Float.NaN
        private var startingTranslationTabs = Float.NaN
        private var startingAlphaTitle = Float.NaN
        private var currentOffset = 0f
        private var startingPosition = -1
        private var currentPosition = 0

        fun reset() {
            startingTranslationTitle = Float.NaN
            startingTranslationTabs = Float.NaN
            startingAlphaTitle = Float.NaN

            onPageScrolled(currentPosition, currentOffset, 0)
        }

        override fun onPageScrolled(
            position: Int, positionOffset: Float, positionOffsetPixels: Int
        ) {
            currentPosition = position
            currentOffset = positionOffset

            val offset = when {
                position < startingPosition ->
                    if (startingPosition - position > 1) 1f else 1 - positionOffset

                position > startingPosition -> 1f

                else -> positionOffset
            }

            val captured = !startingTranslationTitle.isNaN() &&
                    !startingTranslationTabs.isNaN() &&
                    !startingAlphaTitle.isNaN()

            if (captured) {
                tabsContainer.translationY = startingTranslationTabs * (1 - offset) +
                        title.measuredHeight * offset

                title.translationY = startingTranslationTitle * (1 - offset)
                title.alpha = startingAlphaTitle + (1 - startingAlphaTitle) * offset
            } else {
                tabsContainer.translationY =
                    (1 - offset) + title.measuredHeight * offset

                title.translationY = 1 - offset
                title.alpha = offset
            }

            title.visibility = if (title.alpha > 0) View.VISIBLE else View.INVISIBLE
        }

        override fun onPageSelected(position: Int) {
            observePageRecycler(position)
        }

        override fun onPageScrollStateChanged(state: Int) {
            if (state == ViewPager.SCROLL_STATE_IDLE) {
                startingTranslationTitle = Float.NaN
                startingTranslationTabs = Float.NaN
                startingAlphaTitle = Float.NaN
                startingPosition = -1

                adapter.reset(pager.currentItem)

                return
            }

            if (state != ViewPager.SCROLL_STATE_DRAGGING &&
                state != ViewPager.SCROLL_STATE_SETTLING
            ) {
                return
            }

            if (startingPosition == -1) {
                startingPosition =
                    if (currentOffset > 0.5f) currentPosition + 1 else currentPosition
            }

            if (startingAlphaTitle.isNaN()) {
                startingAlphaTitle = title.alpha
            }

            if (startingTranslationTitle.isNaN()) {
                startingTranslationTitle = title.translationY
            }

            if (startingTranslationTabs.isNaN()) {
                startingTranslationTabs = tabsContainer.translationY
            }
        }
    }

    companion object {
        @JvmStatic
        fun getName(): String = "Briefing"
    }
}
