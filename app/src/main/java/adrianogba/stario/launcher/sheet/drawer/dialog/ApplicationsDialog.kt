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

package adrianogba.stario.launcher.sheet.drawer.dialog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RelativeLayout
import android.window.OnBackInvokedDispatcher
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.FragmentManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.transition.Transition
import androidx.transition.TransitionListenerAdapter
import androidx.viewpager.widget.ViewPager
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.sheet.SheetDialogFragment
import adrianogba.stario.launcher.sheet.SheetType
import adrianogba.stario.launcher.sheet.behavior.SheetBehavior
import adrianogba.stario.launcher.sheet.drawer.DrawerAdapter
import adrianogba.stario.launcher.sheet.drawer.DrawerPage
import adrianogba.stario.launcher.sheet.drawer.category.Categories
import adrianogba.stario.launcher.sheet.drawer.search.SearchEngine
import adrianogba.stario.launcher.sheet.drawer.search.SearchFragment
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.common.FadingEdgeLayout
import adrianogba.stario.launcher.ui.utils.animation.Animation
import adrianogba.stario.launcher.ui.utils.animation.FragmentTransition
import kotlin.math.abs
import kotlin.math.sign

class ApplicationsDialog : SheetDialogFragment {

    private var launchSearchReceiver: BroadcastReceiver? = null
    private var popStackReceiver: BroadcastReceiver? = null
    private var searchFragment: SearchFragment? = null
    private var listener: ResumeListener? = null
    private var swipeDrawable: Drawable? = null

    private lateinit var themedActivity: ThemedActivity
    private lateinit var fader: FadingEdgeLayout
    private lateinit var adapter: DrawerAdapter
    private lateinit var pager: ViewPager
    private lateinit var search: EditText

    constructor() : super()

    constructor(type: SheetType) : super(type)

    override fun requiresEagerInitialization(): Boolean = true

    override fun onAttach(context: Context) {
        super.onAttach(context)

        themedActivity = context as ThemedActivity
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.drawer, container, false) as ViewGroup

        fader = root.findViewById(R.id.fader)
        pager = root.findViewById(R.id.pager)
        search = root.findViewById(R.id.search)

        val searchContainer = search.parent as ViewGroup

        setOnBackPressed { onBackPressed() }

        addOnShowListener {
            getBehavior()?.addSheetCallback(SearchBarCallback(searchContainer))
        }

        Measurements.addNavListener { value ->
            searchContainer.setPadding(
                searchContainer.paddingLeft, searchContainer.paddingTop,
                searchContainer.paddingRight, value + Measurements.dpToPx(20f)
            )

            updateFadeSizes(value)
        }

        search.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateFadeSizes(Measurements.getNavHeight())
        }

        search.inputType = 0

        search.isFocusable = false
        search.isFocusableInTouchMode = false
        search.setOnClickListener { showSearch(true) }

        return root
    }

    private fun updateFadeSizes(navHeight: Int) {
        fader.setFadeSizes(
            if (Measurements.isLandscape()) {
                Measurements.getSysUIHeight()
            } else {
                Measurements.dpToPx(Measurements.HEADER_SIZE_DP / 2f)
            },
            0,
            navHeight + Measurements.getDefaultPadding() + search.measuredHeight,
            0
        )
    }

    private fun onBackPressed(): Boolean {
        getBehavior()?.draggable = true

        val popped = childFragmentManager.popBackStackImmediate(
            SearchFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE
        )

        if (popped ||
            (pager.currentItem == DrawerAdapter.CATEGORIES_POSITION && adapter.collapse())
        ) {
            return false
        }

        hide(true)

        return true
    }

    /**
     * Nudges the search bar as the sheet slides, so it trails the sheet edge it
     * is anchored to. All instances compare equal so re-adding replaces rather
     * than stacking.
     */
    private inner class SearchBarCallback(
        private val searchContainer: ViewGroup
    ) : SheetBehavior.SheetCallback {

        override fun onSlide(sheet: View, slideOffset: Float) {
            val collapsedDelta = Measurements.dpToPx(SheetBehavior.COLLAPSED_DELTA_DP.toFloat()) -
                    search.measuredHeight * 2

            when (getType()) {
                SheetType.BOTTOM_SHEET -> {
                    searchContainer.translationY = (1f - slideOffset) * -collapsedDelta
                    searchContainer.translationX = 0f
                }

                SheetType.LEFT_SHEET -> {
                    searchContainer.translationY = 0f
                    searchContainer.translationX =
                        (1f - slideOffset) * search.measuredWidth / 4
                }

                SheetType.RIGHT_SHEET -> {
                    searchContainer.translationY = 0f
                    searchContainer.translationX = (slideOffset - 1f) * -collapsedDelta
                }

                else -> {
                }
            }
        }

        override fun onStateChanged(sheet: View, newState: Int) {
            if (newState == SheetBehavior.STATE_EXPANDED) {
                searchContainer.translationY = 0f
                searchContainer.translationX = 0f

                return
            }

            if (newState != SheetBehavior.STATE_COLLAPSED) {
                return
            }

            if (isAdded) {
                try {
                    childFragmentManager.popBackStackImmediate(
                        SearchFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE
                    )
                    getBehavior()!!.draggable = true
                } catch (exception: Exception) {
                    Log.e("ApplicationsDialog", "onStateChanged: " + exception.message)
                }
            }

            try {
                adapter.reset()
            } catch (exception: Exception) {
                listener = ResumeListener {
                    adapter.reset()

                    listener = null
                }
            }
        }

        override fun hashCode(): Int = HASH

        override fun equals(other: Any?): Boolean =
            other is SheetBehavior.SheetCallback && other.hashCode() == hashCode()
    }

    private fun showSearch(animate: Boolean) {
        val manager = childFragmentManager

        val fragment = searchFragment ?: SearchFragment().also {
            searchFragment = it

            registerSearchBackCallback(it)
        }

        if (manager.fragments.contains(fragment)) {
            return
        }

        val enterTransition = searchTransition()
        enterTransition.duration = if (animate) Animation.MEDIUM.duration.toLong() else 0

        val exitTransition = searchTransition()
        exitTransition.duration = Animation.MEDIUM.duration.toLong()

        enterTransition.addListener(object : TransitionListenerAdapter() {
            override fun onTransitionStart(transition: Transition) {
                if (animate) {
                    fader.alpha = 1f
                    fader.animate().alpha(0f)
                        .translationY(-Measurements.getHeight().toFloat() / 2)
                        .setDuration(transition.duration)
                        .setInterpolator(transition.interpolator)
                        .withEndAction {
                            notifySelection(false)
                            fader.translationY = 0f
                            fader.scaleX = 0.9f
                            fader.scaleY = 0.9f
                        }
                } else {
                    fader.alpha = 0f
                    notifySelection(false)
                    fader.scaleX = 0.9f
                    fader.scaleY = 0.9f
                }

                search.visibility = View.GONE
            }
        })

        exitTransition.addListener(object : TransitionListenerAdapter() {
            override fun onTransitionStart(transition: Transition) {
                fader.alpha = 0f
                fader.scaleX = 0.9f
                fader.scaleY = 0.9f

                fader.animate()
                    .alpha(1f)
                    .scaleY(1f)
                    .scaleX(1f)
                    .translationY(0f)
                    .setDuration(transition.duration)
                    .setInterpolator(transition.interpolator)
                    .withEndAction { notifySelection(true) }

                search.visibility = View.VISIBLE
            }
        })

        fragment.enterTransition = enterTransition
        fragment.returnTransition = exitTransition

        manager.beginTransaction()
            .setReorderingAllowed(true)
            .addToBackStack(SearchFragment.TAG)
            .add(R.id.root, fragment)
            .commit()

        getBehavior()?.draggable = false
    }

    private fun searchTransition(): Transition = FragmentTransition(true)
        .excludeTarget(EditText::class.java, true)
        .excludeTarget(RelativeLayout::class.java, true)

    private fun registerSearchBackCallback(fragment: SearchFragment) {
        val dialog = dialog ?: return

        dialog.onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY
        ) {
            if (!fragment.onBackPressed()) {
                @Suppress("DEPRECATION")
                dialog.onBackPressed()

                getBehavior()?.draggable = true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        pager.overScrollMode = View.OVER_SCROLL_NEVER
        pager.isSaveEnabled = true

        val adapter = DrawerAdapter(childFragmentManager)
        this.adapter = adapter

        pager.offscreenPageLimit = 100
        pager.adapter = adapter

        pager.setPageTransformer(false) { page, position ->
            notifySelection(position == position.toInt().toFloat())

            // The two empty pages at either end wrap around, so a page far
            // enough out is pulled back across the whole pager width.
            if (abs(position) > adapter.count - 3) {
                page.translationX =
                    -(adapter.count - 2) * page.width * sign(position)
            } else {
                page.translationX = 0f
            }
        }

        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageScrolled(
                position: Int, positionOffset: Float, positionOffsetPixels: Int
            ) {
                if (position == 0 && positionOffset == 0f) {
                    pager.setCurrentItem(adapter.count - 2, false)
                }

                if (position == adapter.count - 1) {
                    pager.setCurrentItem(1, false)
                }
            }

            override fun onPageSelected(position: Int) {
                if (position > 0 && position < adapter.count - 1) {
                    themedActivity.applicationContext
                        .getSharedPreferences(Entry.DRAWER)
                        .edit()
                        .putInt(APPLICATIONS_PAGE, position)
                        .apply()
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager.SCROLL_STATE_DRAGGING) {
                    swipeDrawable = null
                    updateSearchBarCompoundDrawables()
                }
            }
        })

        pager.setCurrentItem(
            themedActivity.applicationContext
                .getSharedPreferences(Entry.DRAWER)
                .getInt(APPLICATIONS_PAGE, DrawerAdapter.CATEGORIES_POSITION),
            false
        )
    }

    private fun notifySelection(focused: Boolean) {
        val focusedFragment = adapter.getFragment(pager.currentItem)

        for (index in 0 until adapter.count) {
            val fragment = adapter.getFragment(index)

            if (fragment is DrawerPage) {
                fragment.setSelected(focused && fragment == focusedFragment)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launchSearchReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                expand()

                showSearch(false)
            }
        }
        this.launchSearchReceiver = launchSearchReceiver

        val popStackReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (::adapter.isInitialized) {
                    adapter.collapse()
                }
            }
        }
        this.popStackReceiver = popStackReceiver

        @Suppress("DEPRECATION")
        val manager = LocalBroadcastManager.getInstance(themedActivity)
        manager.registerReceiver(popStackReceiver, IntentFilter(Categories.FOLDER_STACK_ID))
        manager.registerReceiver(launchSearchReceiver, IntentFilter(INTENT_LAUNCH_SEARCH))

        swipeDrawable = AppCompatResources.getDrawable(themedActivity, R.drawable.ic_swipe)
    }

    override fun onDestroy() {
        @Suppress("DEPRECATION")
        val manager = LocalBroadcastManager.getInstance(themedActivity)

        launchSearchReceiver?.let { manager.unregisterReceiver(it) }
        popStackReceiver?.let { manager.unregisterReceiver(it) }

        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()

        updateSearchBarCompoundDrawables()

        childFragmentManager.popBackStackImmediate(
            SearchFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE
        )

        getBehavior()?.draggable = true

        listener?.onResume()
    }

    private fun updateSearchBarCompoundDrawables() {
        val engine = SearchEngine.getEngine(themedActivity.applicationContext)

        search.setCompoundDrawablesWithIntrinsicBounds(
            engine.getDrawable(themedActivity), null, swipeDrawable, null
        )
    }

    private fun interface ResumeListener {
        fun onResume()
    }

    companion object {
        const val INTENT_LAUNCH_SEARCH = "com.stario.INTENT_LAUNCH_SEARCH"

        private const val APPLICATIONS_PAGE = "com.stario.APPLICATIONS_PAGE"
        private const val HASH = 2345678

        @JvmStatic
        fun getName(): String = "Application Drawer"
    }
}
