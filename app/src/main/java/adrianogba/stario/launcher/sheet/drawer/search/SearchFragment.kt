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

package adrianogba.stario.launcher.sheet.drawer.search

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.PreEventNestedScrollView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.divider.MaterialDividerItemDecoration
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.sheet.drawer.search.recyclers.OnSearchRecyclerVisibilityChangeListener
import adrianogba.stario.launcher.sheet.drawer.search.recyclers.OnVisibilityChangeListener
import adrianogba.stario.launcher.sheet.drawer.search.recyclers.SearchRecyclerItemAnimator
import adrianogba.stario.launcher.sheet.drawer.search.recyclers.adapters.AppAdapter
import adrianogba.stario.launcher.sheet.drawer.search.recyclers.adapters.WebAdapter
import adrianogba.stario.launcher.sheet.drawer.search.recyclers.adapters.suggestions.AutosuggestAdapter
import adrianogba.stario.launcher.sheet.drawer.search.recyclers.adapters.suggestions.OptionAdapter
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.common.FadingEdgeLayout
import adrianogba.stario.launcher.ui.keyboard.ImeAnimationController
import adrianogba.stario.launcher.ui.keyboard.KeyboardHeightProvider
import adrianogba.stario.launcher.ui.recyclers.DividerItemDecorator
import adrianogba.stario.launcher.ui.recyclers.RecyclerItemAnimator
import adrianogba.stario.launcher.ui.utils.HomeWatcher
import adrianogba.stario.launcher.ui.utils.UiUtils
import adrianogba.stario.launcher.ui.utils.animation.Animation
import adrianogba.stario.launcher.ui.utils.animation.KeyboardAnimationHelper
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

class SearchFragment : Fragment() {
    private val controller = ImeAnimationController()

    private var heightProvider: KeyboardHeightProvider? = null
    private var activity: ThemedActivity? = null

    // Null checked where it is read, because the home watcher registered in
    // onAttach can fire before onCreateView has run.
    private var search: KeyPreImeListeningEditText? = null

    private lateinit var searchLayoutTransition: SearchLayoutTransition
    private lateinit var searchPreferences: SharedPreferences
    private lateinit var searchContainer: ConstraintLayout
    private lateinit var homeWatcher: HomeWatcher
    private lateinit var suggestions: RecyclerView
    private lateinit var options: RecyclerView
    private lateinit var apps: RecyclerView
    private lateinit var web: RecyclerView
    private lateinit var content: ViewGroup
    private lateinit var webContainer: View
    private lateinit var base: View

    override fun onAttach(context: Context) {
        if (context !is ThemedActivity) {
            throw RuntimeException("Parent activity is not of type ThemedActivity.")
        }

        activity = context
        context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        searchPreferences = context.applicationContext.getSharedPreferences(Entry.SEARCH)

        homeWatcher = HomeWatcher(context)
        homeWatcher.setOnHomePressedListener {
            search?.let { UiUtils.hideKeyboard(it) }
        }

        super.onAttach(context)
    }

    override fun onDetach() {
        controller.finish()

        super.onDetach()

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity = null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        postponeEnterTransition()

        val activity = this.activity!!
        val root = inflater.inflate(R.layout.search, container, false)

        root.setOnTouchListener { _, _ -> true }

        val heightProvider = KeyboardHeightProvider(activity)
        this.heightProvider = heightProvider

        val scrollView = root.findViewById<PreEventNestedScrollView>(R.id.scroller)
        val unauthorized = root.findViewById<View>(R.id.unauthorized)
        val searching = root.findViewById<View>(R.id.searching)
        val hint = root.findViewById<View>(R.id.result_hint)
        val fader = root.findViewById<FadingEdgeLayout>(R.id.fader)
        searchContainer = root.findViewById(R.id.search_container)
        webContainer = root.findViewById(R.id.web_container)
        content = root.findViewById(R.id.content)
        base = root.findViewById(R.id.base)

        val search = root.findViewById<KeyPreImeListeningEditText>(R.id.search)
        this.search = search

        val searchLayoutTransition = SearchLayoutTransition()
        this.searchLayoutTransition = searchLayoutTransition

        val nativeTransitionCast = searchLayoutTransition.getUnrefinedTransition()
        nativeTransitionCast.setDuration(
            LayoutTransition.CHANGING, Animation.MEDIUM.duration.toLong()
        )

        content.layoutTransition = nativeTransitionCast

        apps = root.findViewById(R.id.apps)
        apps.layoutManager = GridLayoutManager(activity, MAX_APP_QUERY_ITEMS)
        apps.itemAnimator = null

        val appAdapter = AppAdapter(activity)
        apps.adapter = appAdapter

        suggestions = root.findViewById(R.id.suggestions)
        suggestions.layoutManager = LinearLayoutManager(activity)
        suggestions.addItemDecoration(
            DividerItemDecorator(activity, MaterialDividerItemDecoration.VERTICAL)
        )
        suggestions.itemAnimator = SearchRecyclerItemAnimator(Animation.MEDIUM)

        val autosuggestAdapter = AutosuggestAdapter(activity)
        autosuggestAdapter.setOnVisibilityChangeListener(
            OnSearchRecyclerVisibilityChangeListener(searchLayoutTransition)
        )
        suggestions.adapter = autosuggestAdapter

        options = root.findViewById(R.id.options)
        options.clipToOutline = true
        options.layoutManager = LinearLayoutManager(activity)
        options.addItemDecoration(
            DividerItemDecorator(activity, MaterialDividerItemDecoration.VERTICAL)
        )
        options.itemAnimator = SearchRecyclerItemAnimator(Animation.MEDIUM)

        val optionAdapter = OptionAdapter(activity)
        optionAdapter.setOnVisibilityChangeListener(
            OnSearchRecyclerVisibilityChangeListener(searchLayoutTransition)
        )
        options.adapter = optionAdapter

        web = root.findViewById(R.id.web)
        web.layoutManager = LinearLayoutManager(activity)
        web.addItemDecoration(
            DividerItemDecorator(
                activity, MaterialDividerItemDecoration.VERTICAL, Measurements.dpToPx(10f)
            )
        )
        web.itemAnimator =
            RecyclerItemAnimator(RecyclerItemAnimator.APPEARANCE, Animation.MEDIUM)

        val webAdapter = WebAdapter(activity)
        webAdapter.setOnVisibilityChangeListener(object : OnVisibilityChangeListener {
            private val listener: OnVisibilityChangeListener =
                OnSearchRecyclerVisibilityChangeListener(searchLayoutTransition)

            override fun onPreChange(view: View?, visibility: Int) {
                listener.onPreChange(view, visibility)
            }

            override fun onChange(view: View?, visibility: Int) {
                listener.onChange(view, visibility)

                searching.visibility =
                    if (visibility == View.VISIBLE) View.GONE else View.VISIBLE

                unauthorized.visibility = View.GONE
            }
        })
        webAdapter.setUnauthorizedListener {
            searching.visibility = View.GONE
            unauthorized.visibility = View.VISIBLE
        }

        web.adapter = webAdapter

        search.isFocusable = true
        search.isFocusableInTouchMode = true
        search.showSoftInputOnFocus = true
        search.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD

        Measurements.addStatusBarListener { value ->
            scrollView.setPadding(
                scrollView.paddingLeft, value + Measurements.getDefaultPadding(),
                scrollView.paddingRight, scrollView.paddingBottom
            )

            fader.setFadeSizes(
                value, 0,
                Measurements.getNavHeight() + Measurements.getDefaultPadding() +
                        search.measuredHeight, 0
            )
        }

        Measurements.addNavListener { value ->
            scrollView.setPadding(
                scrollView.paddingLeft, scrollView.paddingTop, scrollView.paddingRight,
                value + Measurements.getDefaultPadding() + search.measuredHeight
            )

            (search.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = value
            search.requestLayout()

            fader.setFadeSizes(
                Measurements.getSysUIHeight(), 0,
                value + Measurements.getDefaultPadding() + search.measuredHeight, 0
            )
        }

        search.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            fader.setFadeSizes(
                Measurements.getSysUIHeight(), 0,
                Measurements.getNavHeight() + Measurements.getDefaultPadding() +
                        search.measuredHeight, 0
            )

            scrollView.setPadding(
                scrollView.paddingLeft, scrollView.paddingTop, scrollView.paddingRight,
                Measurements.getNavHeight() + Measurements.getDefaultPadding() +
                        search.measuredHeight
            )
        }

        heightProvider.start()

        val isTouching = AtomicBoolean(false)
        val scrollStopCallback = Runnable {
            if (!controller.isSettleAnimationInProgress) {
                controller.finish()
            }
        }

        scrollView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP ||
                event.action == MotionEvent.ACTION_CANCEL
            ) {
                scrollView.postDelayed(scrollStopCallback, SCROLL_STOP_TIMEOUT.toLong())
                isTouching.set(false)
            } else {
                scrollView.removeCallbacks(scrollStopCallback)
                isTouching.set(true)
            }

            false
        }

        scrollView.setOnPreScrollListener(object : PreEventNestedScrollView.PreEvent {
            private val flingStop = Runnable {
                if (!controller.isSettleAnimationInProgress) {
                    controller.finish(0)
                }
            }

            private var intercepted = false
            private var flingDistance = 0

            override fun onPreScroll(delta: Int): Boolean {
                if (controller.isRequestPending ||
                    controller.isAnimationControlDisallowed ||
                    controller.isSettleAnimationInProgress
                ) {
                    return true
                }

                scrollView.removeCallbacks(scrollStopCallback)
                scrollView.removeCallbacks(flingStop)

                if (delta != 0 && !controller.isAnimationInProgress) {
                    controller.startControlRequest(search)
                    search.requestFocus()

                    return true
                }

                intercepted = false

                if (controller.isAnimationInProgress) {
                    if (delta < 0 && !controller.isCurrentPositionFullyHidden) {
                        controller.insetBy(-delta)
                        intercepted = true
                    }

                    if (delta > 0 && scrollView.scrollY == 0 &&
                        !controller.isCurrentPositionFullyShown
                    ) {
                        controller.insetBy(-delta)
                        intercepted = true
                    }
                }

                if (!intercepted) {
                    consumeFlingDistance(delta)

                    if (flingDistance != 0) {
                        val scrolledPastTop = scrollView.scrollY - delta < 0
                        val scrolledPastBottom = flingDistance < 0 &&
                                (content.measuredHeight + scrollView.paddingBottom +
                                        scrollView.paddingTop - scrollView.measuredHeight) <
                                (scrollView.scrollY - delta)

                        if (scrolledPastTop || scrolledPastBottom) {
                            if (controller.isAnimationInProgress) {
                                controller.finish(
                                    scrollView.getSplineFlingVelocity(flingDistance)
                                )
                            }

                            flingDistance = 0
                        }
                    } else if (!isTouching.get() && controller.isAnimationInProgress) {
                        scrollView.postDelayed(flingStop, SCROLL_STOP_TIMEOUT.toLong())
                    }
                }

                return intercepted
            }

            override fun onPreFling(velocity: Int): Boolean {
                if (controller.isRequestPending ||
                    controller.isAnimationControlDisallowed ||
                    controller.isSettleAnimationInProgress
                ) {
                    return true
                }

                if (intercepted) {
                    controller.finish(-velocity)
                    flingDistance = 0

                    return true
                } else if (velocity > 0) {
                    flingDistance = -scrollView.getSplineFlingDistance(velocity)
                } else if (velocity < 0 && scrollView.scrollY > 0) {
                    flingDistance = scrollView.getSplineFlingDistance(velocity)
                }

                return false
            }

            private fun consumeFlingDistance(distance: Int) {
                if (flingDistance == 0 ||
                    (distance > 0 && flingDistance < 0) ||
                    (distance < 0 && flingDistance > 0)
                ) {
                    flingDistance = 0

                    return
                }

                flingDistance = if (flingDistance > 0) {
                    max(0, flingDistance - distance)
                } else {
                    min(0, flingDistance - distance)
                }
            }
        })

        KeyboardAnimationHelper.configureKeyboardAnimator(
            root, heightProvider, controller
        ) { translation ->
            content.translationY = -translation
            searchContainer.translationY = translation
        }

        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_GO) {
                return@setOnEditorActionListener false
            }

            if (searchPreferences.getBoolean(WebAdapter.SEARCH_RESULTS, false)) {
                if (webContainer.visibility != View.VISIBLE) {
                    searchLayoutTransition.setAnimate(false)
                    searchLayoutTransition.cancel()

                    base.visibility = View.GONE
                    webContainer.visibility = View.VISIBLE
                    searching.visibility = View.VISIBLE
                    unauthorized.visibility = View.GONE
                }

                val text = search.text
                if (text != null && text.isNotEmpty()) {
                    webAdapter.update(text.toString())

                    return@setOnEditorActionListener true
                }
            }

            if (base.visibility == View.VISIBLE) {
                return@setOnEditorActionListener appAdapter.submit() ||
                        autosuggestAdapter.submit() || optionAdapter.submit()
            }

            false
        }

        search.addTextChangedListener(object : TextWatcher {
            private var lastRegisteredTimestamp = 0L

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(editable: Editable) {
                scrollView.smoothScrollTo(0, 0, Animation.LONG.duration)

                val query = editable.toString()
                val filteredQuery = query.replace(Regex("(\\r\\n|\\r|\\n)"), "")

                hint.visibility = if (filteredQuery.isNotEmpty() &&
                    searchPreferences.getBoolean(WebAdapter.SEARCH_RESULTS, false)
                ) View.VISIBLE else View.GONE

                hint.post {
                    content.setPadding(
                        content.paddingLeft, content.paddingTop, content.paddingRight,
                        if (hint.visibility == View.VISIBLE) hint.height else 0
                    )
                }

                if (filteredQuery != query) {
                    search.setText(filteredQuery)

                    return
                }

                if (base.visibility != View.VISIBLE) {
                    return
                }

                appAdapter.update(query)
                optionAdapter.update(query)

                val timeStamp = System.currentTimeMillis()

                if (query.isBlank()) {
                    autosuggestAdapter.update(query)
                } else {
                    // don't process text changes too often
                    search.postDelayed({
                        if (timeStamp == lastRegisteredTimestamp) {
                            autosuggestAdapter.update(query)
                        }
                    }, SUGGESTION_PROCESS_INTERVAL.toLong())
                }

                lastRegisteredTimestamp = System.currentTimeMillis()
            }
        })

        root.post {
            val inputMethodManager = activity
                .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?

            if (inputMethodManager != null) {
                inputMethodManager.restartInput(search)
                UiUtils.hideKeyboard(search)
            }

            startPostponedEnterTransition()

            search.post(object : Runnable {
                override fun run() {
                    UiUtils.showKeyboard(search)

                    if (!UiUtils.isKeyboardVisible(search)) {
                        search.post(this)
                    }
                }
            })
        }

        return root
    }

    override fun onDestroyView() {
        suggestions.adapter = null
        options.adapter = null
        apps.adapter = null
        web.adapter = null

        super.onDestroyView()
    }

    /**
     * @return `true` if this instance wants to prevent the back event
     */
    fun onBackPressed(): Boolean {
        if (!UiUtils.isKeyboardVisible(view)) {
            return false
        }

        search?.let { UiUtils.hideKeyboard(it) }

        return true
    }

    override fun onStart() {
        homeWatcher.startWatch()

        super.onStart()
    }

    override fun onStop() {
        homeWatcher.stopWatch()
        search?.let { UiUtils.hideKeyboard(it) }

        super.onStop()

        search?.text = null
        base.visibility = View.VISIBLE
        webContainer.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()

        val activity = this.activity ?: return

        val engine = SearchEngine.getEngine(activity.applicationContext)
        search?.setCompoundDrawablesWithIntrinsicBounds(
            engine.getDrawable(activity), null, null, null
        )
    }

    override fun onDestroy() {
        heightProvider?.close()

        super.onDestroy()
    }

    companion object {
        const val TAG: String = "SearchFragment"
        const val SEARCH_HIDDEN_APPS: String = "com.stario.SEARCH_HIDDEN_APPS"
        const val MAX_APP_QUERY_ITEMS: Int = 4

        private const val SCROLL_STOP_TIMEOUT = 50
        private const val SUGGESTION_PROCESS_INTERVAL = 200
    }
}
