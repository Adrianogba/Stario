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

package adrianogba.stario.launcher.activities.settings.dialogs.icons

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.slider.Slider
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.dialogs.ActionDialog
import adrianogba.stario.launcher.ui.icons.AdaptiveIconView
import adrianogba.stario.launcher.ui.icons.PathCornerTreatmentAlgorithm
import adrianogba.stario.launcher.ui.recyclers.DividerItemDecorator

class IconsDialog(activity: ThemedActivity) : ActionDialog(activity) {
    private val localBroadcastManager: LocalBroadcastManager =
        LocalBroadcastManager.getInstance(activity)
    private val preferences: SharedPreferences =
        activity.applicationContext.getSharedPreferences(Entry.ICONS)

    private var adapter: IconsRecyclerAdapter? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun inflateContent(inflater: LayoutInflater): View {
        val root = inflater.inflate(R.layout.pop_up_icons, null)

        val slider = root.findViewById<Slider>(R.id.slider)
        val algorithm = root.findViewById<MaterialButtonToggleGroup>(R.id.algorithm)

        val recycler = root.findViewById<RecyclerView>(R.id.recycler)
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
                if (event.action == MotionEvent.ACTION_CANCEL ||
                    event.action == MotionEvent.ACTION_UP
                ) {
                    behavior.isDraggable = true
                } else {
                    behavior.isDraggable = scrolledToTop
                }

                return false
            }
        })

        val adapter = IconsRecyclerAdapter(activity) { dismiss() }
        this.adapter = adapter

        slider.valueFrom = 0f
        slider.valueTo = 1f
        slider.stepSize = 0.01f

        slider.value = (preferences.getFloat(
            AdaptiveIconView.CORNER_RADIUS_ENTRY,
            AdaptiveIconView.DEFAULT_CORNER_RADIUS
        ) * 100f).toInt() / 100f
        slider.addOnChangeListener { _, value, _ ->
            val intent = Intent(INTENT_CHANGE_CORNER_RADIUS)
            intent.putExtra(EXTRA_CORNER_RADIUS, value)

            preferences.edit()
                .putFloat(AdaptiveIconView.CORNER_RADIUS_ENTRY, value)
                .apply()

            localBroadcastManager.sendBroadcastSync(intent)
        }

        val currentPathCornerTreatmentAlgorithm =
            PathCornerTreatmentAlgorithm.fromIdentifier(
                preferences.getInt(
                    PathCornerTreatmentAlgorithm.PATH_ALGORITHM_ENTRY,
                    PathCornerTreatmentAlgorithm.DEFAULT_PATH_ALGORITHM_ENTRY
                )
            )

        if (currentPathCornerTreatmentAlgorithm == PathCornerTreatmentAlgorithm.SQUIRCLE) {
            algorithm.check(R.id.squircle)
        } else {
            algorithm.check(R.id.regular)
        }

        algorithm.addOnButtonCheckedListener { _, checkedId, isChecked ->
            val intent = Intent(INTENT_CHANGE_PATH_ALGORITHM)

            if (checkedId == R.id.squircle && isChecked) {
                intent.putExtra(EXTRA_PATH_ALGORITHM, PathCornerTreatmentAlgorithm.SQUIRCLE)

                preferences.edit()
                    .putInt(
                        PathCornerTreatmentAlgorithm.PATH_ALGORITHM_ENTRY,
                        PathCornerTreatmentAlgorithm.SQUIRCLE.ordinal
                    )
                    .apply()
            } else {
                intent.putExtra(EXTRA_PATH_ALGORITHM, PathCornerTreatmentAlgorithm.REGULAR)

                preferences.edit()
                    .putInt(
                        PathCornerTreatmentAlgorithm.PATH_ALGORITHM_ENTRY,
                        PathCornerTreatmentAlgorithm.REGULAR.ordinal
                    )
                    .apply()
            }

            localBroadcastManager.sendBroadcastSync(intent)
        }

        recycler.addItemDecoration(
            DividerItemDecorator(
                context,
                MaterialDividerItemDecoration.VERTICAL
            )
        )
        recycler.layoutManager = LinearLayoutManager(
            activity,
            LinearLayoutManager.VERTICAL, false
        )
        recycler.adapter = adapter

        return root
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun show() {
        super.show()

        adapter?.notifyDataSetChanged()
    }

    override fun blurBehind(): Boolean = true

    override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_EXPANDED

    companion object {
        /**
         * Broadcast Action: Icon corner radius changed by the user.
         *
         *  * [EXTRA_CORNER_RADIUS] containing the new radius.
         */
        const val INTENT_CHANGE_CORNER_RADIUS: String =
            "com.stario.IconsDialog.CHANGE_CORNER_RADIUS"
        const val EXTRA_CORNER_RADIUS: String = "com.stario.IconsDialog.CORNER_RADIUS"

        /**
         * Broadcast Action: Icon clip path algorithm changed by the user.
         *
         *  * [EXTRA_PATH_ALGORITHM] containing the new algorithm.
         */
        const val INTENT_CHANGE_PATH_ALGORITHM: String =
            "com.stario.IconsDialog.CHANGE_PATH_ALGORITHM"
        const val EXTRA_PATH_ALGORITHM: String = "com.stario.IconsDialog.PATH_ALGORITHM"
    }
}
