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

package adrianogba.stario.launcher.activities.settings.dialogs.location

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.divider.MaterialDividerItemDecoration
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.dialogs.ActionDialog
import adrianogba.stario.launcher.ui.recyclers.DividerItemDecorator
import adrianogba.stario.launcher.ui.utils.UiUtils

class LocationDialog(activity: ThemedActivity) : ActionDialog(activity) {
    private var listener: OnLocationUpdate? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun inflateContent(inflater: LayoutInflater): View {
        val root = inflater.inflate(R.layout.pop_up_location, null)

        val query = root.findViewById<EditText>(R.id.query)
        val recycler = root.findViewById<RecyclerView>(R.id.recycler)

        recycler.addItemDecoration(
            DividerItemDecorator(context, MaterialDividerItemDecoration.VERTICAL)
        )
        recycler.layoutManager = LinearLayoutManager(
            activity, LinearLayoutManager.VERTICAL, false
        )
        recycler.itemAnimator = null
        recycler.setOnTouchListener(object : View.OnTouchListener {
            private val behavior: BottomSheetBehavior<*> = getBehavior()
            private var scrolledToTop = true

            init {
                recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        scrolledToTop = recyclerView.computeVerticalScrollOffset() == 0
                    }
                })
            }

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                if (UiUtils.isKeyboardVisible(view)) {
                    behavior.isDraggable = false
                } else {
                    if (event.action == MotionEvent.ACTION_CANCEL ||
                        event.action == MotionEvent.ACTION_UP
                    ) {
                        behavior.isDraggable = true
                    } else {
                        behavior.isDraggable = scrolledToTop
                    }
                }

                return false
            }
        })

        val adapter = LocationRecyclerAdapter(activity) { v ->
            UiUtils.hideKeyboard(v)

            val behavior = getBehavior()

            behavior.isDraggable = true
            behavior.state = BottomSheetBehavior.STATE_HIDDEN

            listener?.onUpdate()
        }
        recycler.adapter = adapter

        query.doAfterTextChanged { editable -> adapter.update(editable?.toString()) }

        return root
    }

    override fun blurBehind(): Boolean = true

    override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_EXPANDED

    fun setOnLocationUpdateListener(listener: OnLocationUpdate?) {
        this.listener = listener
    }

    fun interface OnLocationUpdate {
        fun onUpdate()
    }
}
