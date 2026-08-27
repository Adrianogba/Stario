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

package adrianogba.stario.launcher.activities.pages

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Point
import android.os.Bundle
import android.util.Log
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.util.Pair
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.activities.launcher.sheets.LauncherSheets
import adrianogba.stario.launcher.activities.pages.insert.InsertPageDialog
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.preferences.Vibrations
import adrianogba.stario.launcher.sheet.SheetDialogFragment
import adrianogba.stario.launcher.sheet.SheetType
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.common.DragShadowBuilder
import adrianogba.stario.launcher.ui.utils.UiUtils
import adrianogba.stario.launcher.ui.utils.animation.Animation
import java.lang.reflect.InvocationTargetException
import java.util.Hashtable

class PageManager : ThemedActivity() {
    private val pages = Hashtable<View, Class<out SheetDialogFragment>>()

    // The Java version held these as androidx Pairs, whose fields are both
    // nullable, so every read needed unwrapping. Nothing outside this class
    // sees the list, and neither half is ever null.
    private val placeholders = ArrayList<Placeholder>()

    private var broadcastManager: LocalBroadcastManager? = null
    private lateinit var pagesContainer: ConstraintLayout
    private lateinit var preferences: SharedPreferences
    private lateinit var inflater: LayoutInflater
    private lateinit var addLabel: View
    private lateinit var homePage: View
    private lateinit var add: ViewGroup
    private var dragging = false

    private var insertDialog: InsertPageDialog? = null
    private var insertDialogShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.page_manager)

        broadcastManager = LocalBroadcastManager.getInstance(this)
        preferences = applicationContext.getSharedPreferences(Entry.SHEET)

        inflater = LayoutInflater.from(this)

        pagesContainer = findViewById(R.id.pages_container)
        homePage = findViewById(R.id.home)
        add = findViewById(R.id.add)
        addLabel = add.findViewById(R.id.add_label)

        add.setOnClickListener { showInsertDialog() }

        loadParams()

        homePage.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            homePage.post {
                for (page in pages.keys) {
                    measure(page)
                }
            }
        }

        loadPlaceholders()
        loadPages(pages)

        if (pages.size == SheetDialogFragment.IMPLEMENTATIONS.size ||
            pages.size == placeholders.size
        ) {
            add.visibility = View.GONE
        }

        findViewById<View>(R.id.gradient).animate()
            .alpha(0.5f).setDuration(Animation.EXTENDED.duration.toLong())

        val root = root!!
        UiUtils.Notch.applyNotchMargin(root, UiUtils.Notch.Treatment.CENTER)
        Measurements.addStatusBarListener { value ->
            root.setPadding(root.paddingLeft, value, root.paddingRight, root.paddingBottom)
        }
        Measurements.addNavListener { value ->
            root.setPadding(root.paddingLeft, root.paddingTop, root.paddingRight, value)
        }
    }

    private fun showInsertDialog() {
        var dialog = insertDialog

        if (dialog == null) {
            dialog = InsertPageDialog(this) { item ->
                val type = getAvailableSpace(item.first)
                val clazz = item.second!!

                preferences.edit()
                    .putString(clazz.name, type.toString())
                    .apply()

                pages[inflatePage(type, clazz)] = clazz

                if (pages.size == SheetDialogFragment.IMPLEMENTATIONS.size ||
                    pages.size == placeholders.size
                ) {
                    hideAddButton()
                }

                val intent = Intent(LauncherSheets.ACTION_ADD_SHEET)
                intent.putExtra(LauncherSheets.INTENT_SHEET_CLASS_EXTRA, clazz)
                broadcastManager?.sendBroadcastSync(intent)
            }

            dialog.setOnDismissListener { insertDialogShowing = false }

            insertDialog = dialog
        }

        if (!insertDialogShowing) {
            dialog.setItems(getItems())
            dialog.show()

            insertDialogShowing = true
        }
    }

    private fun getAvailableSpace(desiredLocation: SheetType?): SheetType {
        var firstFreeSpace = SheetType.UNDEFINED

        for (placeholder in placeholders) {
            if (placeholder.view.childCount == 0) {
                if (desiredLocation == placeholder.type) {
                    firstFreeSpace = placeholder.type
                    break
                } else if (firstFreeSpace == SheetType.UNDEFINED) {
                    firstFreeSpace = placeholder.type
                }
            }
        }

        return firstFreeSpace
    }

    private fun getItems(): List<Pair<SheetType, Class<out SheetDialogFragment>>> {
        val items = ArrayList<Pair<SheetType, Class<out SheetDialogFragment>>>()

        for (clazz in SheetDialogFragment.IMPLEMENTATIONS) {
            var isActive = false

            for (tester in pages.values) {
                if (clazz == tester) {
                    isActive = true
                    break
                }
            }

            if (!isActive) {
                items.add(
                    Pair(
                        SheetType.getDefaultSheetTypeForSheetDialogFragment(this, clazz), clazz
                    )
                )
            }
        }

        return items
    }

    @SuppressLint("FindViewByIdCast")
    private fun loadPlaceholders() {
        placeholders.add(Placeholder(findViewById(R.id.left), SheetType.LEFT_SHEET))
        placeholders.add(Placeholder(findViewById(R.id.top), SheetType.TOP_SHEET))
        placeholders.add(Placeholder(findViewById(R.id.right), SheetType.RIGHT_SHEET))
        placeholders.add(Placeholder(findViewById(R.id.bottom), SheetType.BOTTOM_SHEET))

        for (placeholder in placeholders) {
            val view = placeholder.view
            val type = placeholder.type

            view.clipChildren = false
            view.clipToOutline = false
            view.clipToPadding = false

            view.setOnDragListener { _, event ->
                val draggedView = event.localState as View

                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> {
                        dragging = true

                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

                        true
                    }

                    DragEvent.ACTION_DROP -> {
                        val parent = draggedView.parent

                        if (parent is ViewGroup && parent != view) {
                            parent.removeView(draggedView)
                            view.addView(draggedView)

                            val clazz = pages[draggedView]

                            if (clazz != null) {
                                preferences.edit()
                                    .putString(clazz.name, type.toString())
                                    .apply()

                                val intent = Intent(LauncherSheets.ACTION_MOVE_SHEET)
                                intent.putExtra(
                                    LauncherSheets.INTENT_SHEET_CLASS_EXTRA, clazz
                                )
                                broadcastManager?.sendBroadcastSync(intent)
                            }
                        }

                        resetDraggedPage(draggedView)

                        true
                    }

                    DragEvent.ACTION_DRAG_ENDED -> {
                        resetDraggedPage(draggedView)

                        true
                    }

                    else -> false
                }
            }
        }
    }

    private fun resetDraggedPage(view: View) {
        view.visibility = View.VISIBLE
        view.findViewById<View>(R.id.remove)?.visibility = View.VISIBLE

        dragging = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun loadPages(pages: MutableMap<View, Class<out SheetDialogFragment>>) {
        val list = SheetType.getStoredSheets(this)

        for (pair in list) {
            val type = pair.first
            val clazz = pair.second

            if (type != null && type != SheetType.UNDEFINED && clazz != null) {
                pages[inflatePage(type, clazz)] = clazz
            }
        }
    }

    private fun showAddButton() {
        if (add.visibility == View.VISIBLE) {
            return
        }

        add.setOnTouchListener(null)
        add.animate().cancel()

        add.alpha = 0f
        add.scaleX = ADD_BUTTON_ANIMATION_SCALE_FACTOR
        add.scaleY = ADD_BUTTON_ANIMATION_SCALE_FACTOR

        add.visibility = View.VISIBLE

        add.animate()
            .alpha(1f)
            .scaleY(1f)
            .scaleX(1f)
            .setDuration(Animation.MEDIUM.duration.toLong())
            .setInterpolator(FastOutSlowInInterpolator())
            .setListener(null)
            .start()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun hideAddButton() {
        if (add.visibility == View.GONE) {
            return
        }

        add.setOnTouchListener { _, _ -> false }
        add.animate()
            .alpha(0f)
            .scaleY(ADD_BUTTON_ANIMATION_SCALE_FACTOR)
            .scaleX(ADD_BUTTON_ANIMATION_SCALE_FACTOR)
            .setDuration(Animation.MEDIUM.duration.toLong())
            .setInterpolator(FastOutSlowInInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    add.visibility = View.GONE
                }
            })
            .start()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun inflatePage(type: SheetType, clazz: Class<out SheetDialogFragment>): View {
        val page = inflater.inflate(R.layout.system_page, pagesContainer, false) as ViewGroup
        page.clipToOutline = false
        page.clipChildren = false
        measure(page)

        val remove = page.findViewById<View>(R.id.remove)
        remove.setOnClickListener {
            val parent = page.parent

            if (parent != null) {
                (parent as ViewGroup).removeView(page)
                pages.remove(page)

                showAddButton()

                preferences.edit()
                    .putString(clazz.name, SheetType.UNDEFINED.toString())
                    .apply()

                val intent = Intent(LauncherSheets.ACTION_REMOVE_SHEET)
                intent.putExtra(LauncherSheets.INTENT_SHEET_CLASS_EXTRA, clazz)
                broadcastManager?.sendBroadcastSync(intent)
            }
        }

        var name: String? = null
        try {
            val method = clazz.getMethod("getName")
            name = method.invoke(null) as String?
        } catch (exception: NoSuchMethodException) {
            Log.e(
                TAG, "inflatePage: " + clazz.name +
                        " does not implement getName(). Defaulting to class name..."
            )
        } catch (exception: InvocationTargetException) {
            Log.e(
                TAG, "inflatePage: " + clazz.name +
                        " does not implement getName(). Defaulting to class name..."
            )
        } catch (exception: IllegalAccessException) {
            Log.e(
                TAG, "inflatePage: " + clazz.name +
                        " getName() method is not publicly visible. Defaulting to class name..."
            )
        } catch (exception: ClassCastException) {
            Log.e(
                TAG, "inflatePage: " + clazz.name +
                        " getName() return type is not " + String::class.java.name +
                        ". Defaulting to class name..."
            )
        } finally {
            if (name == null) {
                name = clazz.simpleName
            }

            page.findViewById<TextView>(R.id.name)
                .text = name.replace(" ", System.lineSeparator())
        }

        val pageContainer = page.findViewById<View>(R.id.page_container)
        pageContainer.setOnDragListener { _, event ->
            val draggedPage = event.localState as View

            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> draggedPage !== page

                DragEvent.ACTION_DRAG_ENTERED,
                DragEvent.ACTION_DRAG_EXITED,
                DragEvent.ACTION_DRAG_LOCATION,
                DragEvent.ACTION_DRAG_ENDED -> true

                DragEvent.ACTION_DROP -> {
                    val view = page.parent as? ViewGroup

                    if (view == null) {
                        false
                    } else {
                        val parent = draggedPage.parent

                        if (parent is ViewGroup && parent != view) {
                            val otherPage = view.getChildAt(0)
                            view.removeView(otherPage)
                            parent.addView(otherPage)

                            storePlaceholderFor(parent, otherPage)

                            parent.removeView(draggedPage)
                            view.addView(draggedPage)

                            storePlaceholderFor(view, draggedPage)

                            val intent = Intent(LauncherSheets.ACTION_MOVE_SHEET)
                            intent.putExtra(
                                LauncherSheets.INTENT_SHEET_CLASS_EXTRA,
                                arrayOf(pages[draggedPage], pages[otherPage])
                            )
                            broadcastManager?.sendBroadcastSync(intent)
                        }

                        resetDraggedPage(draggedPage)
                        true
                    }
                }

                else -> false
            }
        }

        val touchPoint = Point()

        pageContainer.isHapticFeedbackEnabled = false
        pageContainer.setOnLongClickListener {
            Vibrations.getInstance().vibrate()

            val dragData = ClipData(
                clazz.name,
                arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
                ClipData.Item(page.tag as CharSequence?)
            )

            remove.visibility = View.INVISIBLE
            page.startDragAndDrop(dragData, DragShadowBuilder(page, touchPoint), page, 0)
            page.visibility = View.INVISIBLE

            true
        }

        pageContainer.setOnTouchListener { _, event ->
            touchPoint.x = event.x.toInt()
            touchPoint.y = event.y.toInt()

            false
        }

        for (placeholder in placeholders) {
            if (placeholder.type == type) {
                placeholder.view.addView(page)

                break
            }
        }

        return page
    }

    /**
     * Writes down which placeholder a page has landed in, so the launcher puts
     * the sheet back on the same edge next time it starts.
     */
    private fun storePlaceholderFor(group: ViewGroup, page: View) {
        for (placeholder in placeholders) {
            if (placeholder.view == group) {
                pages[page]?.let { clazz ->
                    preferences.edit()
                        .putString(clazz.name, placeholder.type.toString())
                        .apply()
                }

                break
            }
        }
    }

    private fun measure(page: View) {
        val params = page.layoutParams

        params.height = homePage.measuredHeight
        params.width = homePage.measuredWidth

        page.layoutParams = params
    }

    private fun loadParams() {
        val params = pagesContainer.layoutParams as ConstraintLayout.LayoutParams

        if (Measurements.isLandscape()) {
            params.height = ConstraintLayout.LayoutParams.MATCH_PARENT
            params.width = 0
            params.dimensionRatio = "H,9:16"
        } else {
            params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
            params.height = 0
            params.dimensionRatio = "W,16:9"
        }

        addLabel.visibility =
            if (Measurements.isLandscape()) View.VISIBLE else View.GONE

        pagesContainer.layoutParams = params
        root!!.requestLayout()
    }

    override fun onConfigurationChanged(config: Configuration) {
        super.onConfigurationChanged(config)

        loadParams()
    }

    override val isOpaque: Boolean
        get() = true

    override val isAffectedByBackGesture: Boolean
        get() = !dragging

    private class Placeholder(val view: ConstraintLayout, val type: SheetType)

    private companion object {
        const val TAG = "PageManager"
        const val ADD_BUTTON_ANIMATION_SCALE_FACTOR = 0.5f
    }
}
