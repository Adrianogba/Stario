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

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.sheet.SheetDialogFragment
import adrianogba.stario.launcher.sheet.SheetType
import adrianogba.stario.launcher.sheet.drawer.dialog.ApplicationsDialog
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.recyclers.overscroll.OverScrollEffect
import adrianogba.stario.launcher.ui.recyclers.overscroll.OverScrollRecyclerView
import adrianogba.stario.launcher.ui.utils.animation.Animation

abstract class DrawerPage : Fragment(), ScrollToTop {

    private var titleContainer: RelativeLayout? = null
    private var root: ViewGroup? = null

    protected lateinit var drawer: OverScrollRecyclerView
    protected lateinit var activity: ThemedActivity
    protected lateinit var search: EditText
    protected lateinit var title: TextView

    private var selected = false

    // Methods rather than a property: the List page overrides setSelected
    // and calls both through super.
    open fun setSelected(selected: Boolean) {
        this.selected = selected
    }

    fun isSelected(): Boolean = selected

    override fun onAttach(context: Context) {
        // Bound before the check so super.onAttach() resolves to the Context
        // overload rather than the deprecated Activity one after a smart cast.
        val attached: Context = context

        if (attached !is ThemedActivity) {
            throw RuntimeException("Parent activity is not of type ThemedActivity.")
        }

        activity = attached

        super.onAttach(attached)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(getLayoutResID(), container, false) as ViewGroup
        this.root = root

        if (hideInflatedLayout()) {
            root.visibility = View.GONE
            root.alpha = 0f
        }

        val titleContainer = root.findViewById<RelativeLayout>(R.id.title_container)
        this.titleContainer = titleContainer

        val drawer = root.findViewById<OverScrollRecyclerView>(R.id.drawer)
        this.drawer = drawer

        title = root.findViewById(R.id.title)

        // The Java version asserted on this. Assertions are off on Android, so
        // it threw here anyway when the page was inflated without a container.
        val search = container!!.rootView.findViewById<EditText>(R.id.search)
        this.search = search

        drawer.overScrollMode = View.OVER_SCROLL_ALWAYS
        drawer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTitleTransforms(drawer)
        }
        drawer.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateTitleTransforms(drawer)
            }
        })

        val type = SheetType.getSheetTypeForSheetDialogFragment(
            activity, ApplicationsDialog::class.java
        )

        if (type == SheetType.BOTTOM_SHEET) {
            drawer.overscrollPullEdges = OverScrollEffect.PULL_EDGE_BOTTOM
        }

        drawer.addOnOverScrollListener(HideOnOverScroll())

        titleContainer.layoutParams.height =
            Measurements.dpToPx(Measurements.HEADER_SIZE_DP.toFloat()) + Measurements.spToPx(8f)

        Measurements.addStatusBarListener { value ->
            drawer.setPadding(
                drawer.paddingLeft,
                value + if (Measurements.isLandscape()) {
                    Measurements.getDefaultPadding()
                } else {
                    Measurements.dpToPx(Measurements.HEADER_SIZE_DP.toFloat()) +
                            Measurements.getDefaultPadding()
                },
                drawer.paddingRight,
                drawer.paddingBottom
            )

            (titleContainer.layoutParams as ViewGroup.MarginLayoutParams).topMargin = value
        }

        val searchContainer = search.parent as View

        drawer.setPadding(
            drawer.paddingLeft, drawer.paddingTop, drawer.paddingRight,
            searchContainer.paddingBottom + (search.bottom - search.top)
        )

        search.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            drawer.setPadding(
                drawer.paddingLeft, drawer.paddingTop, drawer.paddingRight,
                searchContainer.paddingBottom + (bottom - top)
            )
        }

        updateTitleTransforms(drawer)

        return root
    }

    /**
     * Pulling the list past either end far enough closes the sheet. The two
     * flags remember which edge the gesture started at, so a fling that
     * overshoots the far end does not count.
     */
    private inner class HideOnOverScroll : OverScrollEffect.OnOverScrollListener {
        private var receivedBottomOverscrollEvent = false
        private var receivedTopOverscrollEvent = false
        private var factor = 0f

        override fun onOverScrollStateChanged(
            edge: Int, state: OverScrollEffect.OverScrollState
        ) {
            when (state) {
                OverScrollEffect.OverScrollState.SETTLING -> {
                    val pulledFromTrackedEdge =
                        (receivedTopOverscrollEvent && edge == OverScrollEffect.PULL_EDGE_TOP) ||
                                (receivedBottomOverscrollEvent &&
                                        edge == OverScrollEffect.PULL_EDGE_BOTTOM)

                    if (pulledFromTrackedEdge &&
                        factor * drawer.measuredHeight >
                        Measurements.dpToPx(HIDE_THRESHOLD_DP)
                    ) {
                        (parentFragment as? SheetDialogFragment)?.hide(true)
                    }

                    receivedBottomOverscrollEvent = false
                    receivedTopOverscrollEvent = false
                }

                OverScrollEffect.OverScrollState.IDLE -> {
                    receivedBottomOverscrollEvent = false
                    receivedTopOverscrollEvent = false
                }

                OverScrollEffect.OverScrollState.OVER_SCROLLING -> {
                    if (edge == OverScrollEffect.PULL_EDGE_TOP) {
                        receivedTopOverscrollEvent = true
                    } else if (edge == OverScrollEffect.PULL_EDGE_BOTTOM) {
                        receivedBottomOverscrollEvent = true
                    }
                }
            }
        }

        override fun onOverScrolled(edge: Int, factor: Float) {
            this.factor = factor
        }
    }

    open fun hideInflatedLayout(): Boolean = true

    protected fun showLayout() {
        val root = root

        if (hideInflatedLayout() && root != null) {
            root.visibility = View.VISIBLE
            root.post {
                root.animate()
                    .alpha(1f)
                    .setDuration(Animation.LONG.duration.toLong())
                    .setInterpolator(FastOutLinearInInterpolator())
            }
        }
    }

    protected fun updateTitleTransforms(recyclerView: RecyclerView) {
        val titleContainer = titleContainer ?: return

        titleContainer.post {
            val translation = recyclerView.computeVerticalScrollOffset()

            titleContainer.translationY = -translation / 2f

            val alpha = 1f -
                    translation / (Measurements.dpToPx(Measurements.HEADER_SIZE_DP.toFloat()) / 2f)

            if (alpha > 0 && !Measurements.isLandscape()) {
                titleContainer.alpha = alpha
                titleContainer.visibility = View.VISIBLE
            } else {
                titleContainer.visibility = View.GONE
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        updateTitleTransforms(drawer)
    }

    override fun onResume() {
        updateTitleTransforms(drawer)

        super.onResume()
    }

    override fun scrollToTop() {
        drawer.scrollToPosition(0)
    }

    override fun onDestroyView() {
        drawer.adapter = null

        super.onDestroyView()
    }

    protected abstract fun getLayoutResID(): Int

    private companion object {
        const val HIDE_THRESHOLD_DP = 100f
    }
}
