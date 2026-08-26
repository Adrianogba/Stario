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

package adrianogba.stario.launcher.activities.settings.dialogs.hide

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.ogaclejapan.smarttablayout.SmartTabLayout
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.activities.settings.dialogs.hide.pager.HideApplicationsPagerAdapter
import adrianogba.stario.launcher.apps.ProfileManager
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.dialogs.ActionDialog
import adrianogba.stario.launcher.ui.recyclers.overscroll.OverScrollRecyclerView
import adrianogba.stario.launcher.ui.utils.animation.Animation

class HideApplicationsDialog : DialogFragment() {
    private var hideListener: OnHideListener? = null
    private var themedActivity: ThemedActivity? = null

    // Lint reads the throw as a path that skips the super call and reports
    // MissingSuperCall. The super call is right there on the only path that
    // continues. The Java original had the same shape and was not flagged.
    @SuppressLint("MissingSuperCall")
    override fun onAttach(context: Context) {
        if (context !is ThemedActivity) {
            throw RuntimeException("Parent activity is not of type ThemedActivity.")
        }

        themedActivity = context

        super.onAttach(context)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = themedActivity!!

        return object : ActionDialog(activity) {
            override fun inflateContent(inflater: LayoutInflater): View {
                val root = inflater.inflate(R.layout.pop_up_hide, null)

                val pager = root.findViewById<ViewPager>(R.id.pager)
                val adapter = HideApplicationsPagerAdapter(
                    childFragmentManager, activity.resources
                )

                val tabsContainer = root.findViewById<View>(R.id.tabs_container)
                val tabLayout = root.findViewById<SmartTabLayout>(R.id.tabs)

                pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
                    private val scrollListener = object : RecyclerView.OnScrollListener() {
                        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                            if (dy > 0 && !recyclerView.canScrollVertically(1)) {
                                showTooltip(false)
                            } else if (dy < 0) {
                                showTooltip(true)
                            } else if (dy > 0) {
                                hideTooltip()
                            }
                        }
                    }

                    private var recyclerView: OverScrollRecyclerView? = null
                    private var contrastDrawable: Drawable? = null
                    private var hidden: Boolean = tabsContainer.translationY != 0f

                    init {
                        val fader = root.findViewById<View>(R.id.contrast_fader)
                        if (fader != null) {
                            this.contrastDrawable = fader.background
                        }

                        pager.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                            val fragment = adapter.getRegisteredFragment(pager.currentItem)

                            if (fragment != null) {
                                updateObservedRecycler(fragment.getRecycler())
                            }
                        }
                    }

                    @SuppressLint("ClickableViewAccessibility")
                    private fun updateObservedRecycler(recyclerView: OverScrollRecyclerView?) {
                        val current = this.recyclerView
                        if (current != null) {
                            if (current === recyclerView) {
                                return
                            }

                            current.removeOnScrollListener(scrollListener)
                        }

                        recyclerView!!.addOnScrollListener(scrollListener)

                        this.recyclerView = recyclerView
                    }

                    override fun onPageScrollStateChanged(state: Int) {
                        if (state != ViewPager.SCROLL_STATE_IDLE) {
                            showTooltip(true)
                        }
                    }

                    override fun onPageSelected(position: Int) {
                        val fragment = adapter.getRegisteredFragment(position)

                        if (fragment != null) {
                            updateObservedRecycler(fragment.getRecycler())
                        }
                    }

                    private fun hideTooltip() {
                        if (!hidden) {
                            tabsContainer.animate()
                                .translationY(tabsContainer.measuredHeight.toFloat())
                                .setDuration(Animation.MEDIUM.duration.toLong())

                            hidden = true
                        }
                    }

                    private fun showTooltip(enforceContrast: Boolean) {
                        contrastDrawable?.alpha = if (enforceContrast) 255 else 0

                        if (hidden) {
                            tabsContainer.animate()
                                .translationY(0f)
                                .setDuration(Animation.MEDIUM.duration.toLong())

                            hidden = false
                        }
                    }
                })

                pager.adapter = adapter
                if (ProfileManager.from(activity.applicationContext, false)
                        .profiles.size > 1
                ) {
                    tabLayout.setViewPager(pager)
                } else {
                    tabLayout.visibility = View.GONE
                }

                return root
            }

            override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_EXPANDED

            override fun blurBehind(): Boolean = true

            override fun hide() {
                hideListener?.onHide()
            }
        }
    }

    override fun onStop() {
        dismissAllowingStateLoss()

        super.onStop()
    }

    fun show() {
        dialog?.show()
    }

    fun setOnHideListener(listener: OnHideListener?) {
        this.hideListener = listener
    }

    fun interface OnHideListener {
        fun onHide()
    }
}
