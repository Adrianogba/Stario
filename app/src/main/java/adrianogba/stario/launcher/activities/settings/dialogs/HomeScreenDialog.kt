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

package adrianogba.stario.launcher.activities.settings.dialogs

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.activities.launcher.widgets.ClockWidget
import adrianogba.stario.launcher.activities.launcher.widgets.SearchWidget
import adrianogba.stario.launcher.activities.launcher.widgets.glance.extensions.media.Media
import adrianogba.stario.launcher.activities.launcher.widgets.glance.extensions.weather.Weather
import adrianogba.stario.launcher.activities.launcher.widgets.pins.PinnedCategory
import adrianogba.stario.launcher.activities.settings.dialogs.location.LocationDialog
import adrianogba.stario.launcher.activities.settings.dialogs.pin.PinnedCategoryDialog
import adrianogba.stario.launcher.apps.CategoryManager
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.common.StylizedClockView
import adrianogba.stario.launcher.ui.common.lock.LockDetector
import adrianogba.stario.launcher.ui.dialogs.ActionDialog
import adrianogba.stario.launcher.utils.Utils
import java.util.UUID

class HomeScreenDialog(activity: ThemedActivity) : ActionDialog(activity) {

    private lateinit var scroller: NestedScrollView
    private lateinit var root: ViewGroup

    private lateinit var pinsPrefs: SharedPreferences
    private lateinit var clockPrefs: SharedPreferences
    private lateinit var weatherPrefs: SharedPreferences
    private lateinit var settingsPrefs: SharedPreferences

    private lateinit var pinnedCategorySwitch: MaterialSwitch
    private lateinit var lockAnimSwitch: MaterialSwitch
    private lateinit var pinnedCategoryName: TextView
    private lateinit var mediaSwitch: MaterialSwitch
    private lateinit var lockSwitch: MaterialSwitch
    private lateinit var lockAnimContainer: View

    @SuppressLint("ClickableViewAccessibility")
    override fun inflateContent(inflater: LayoutInflater): View {
        val root = inflater.inflate(R.layout.pop_up_home, null) as ViewGroup
        this.root = root

        val stario = activity.applicationContext

        pinsPrefs = stario.getSharedPreferences(Entry.PINNED_CATEGORY)
        weatherPrefs = stario.getSharedPreferences(Entry.WEATHER)
        clockPrefs = stario.getSharedPreferences(Entry.CLOCK)
        settingsPrefs = stario.getSettings()

        pinnedCategoryName = root.findViewById(R.id.pinned_category_name)
        pinnedCategorySwitch = root.findViewById(R.id.pinned_category)
        lockAnimContainer = root.findViewById(R.id.lock_animation_container)
        lockAnimSwitch = root.findViewById(R.id.lock_animation)
        mediaSwitch = root.findViewById(R.id.media)
        scroller = root.findViewById(R.id.scroller)
        lockSwitch = root.findViewById(R.id.lock)

        initGeneralSection()
        initClockSection()
        initWeatherSection()
        initGestureSection()

        return root
    }

    private fun initGeneralSection() {
        setupSwitch(
            mediaSwitch, root.findViewById(R.id.media_container),
            settingsPrefs.getBoolean(Media.PREFERENCE_ENTRY, false)
        ) { _, checked ->
            settingsPrefs.edit().putBoolean(Media.PREFERENCE_ENTRY, checked).apply()

            if (checked && !Utils.isNotificationServiceEnabled(activity)) {
                showNotificationPermissionDialog()
            }
        }

        setupSwitch(
            root.findViewById(R.id.search), root.findViewById(R.id.search_container),
            settingsPrefs.getBoolean(SearchWidget.SEARCH_WIDGET_KEY, true)
        ) { _, checked ->
            settingsPrefs.edit().putBoolean(SearchWidget.SEARCH_WIDGET_KEY, checked).apply()
        }

        val pinnedCategoryContainer = root.findViewById<View>(R.id.pinned_category_container)

        updatePinnedCategoryName()

        pinnedCategorySwitch.isChecked =
            pinsPrefs.getBoolean(PinnedCategory.PINNED_CATEGORY_VISIBLE, false)
        pinnedCategorySwitch.jumpDrawablesToCurrentState()

        pinnedCategorySwitch.setOnCheckedChangeListener { _, isChecked ->
            // Turning it on without a category picked opens the picker instead
            if (isChecked && !isPinnedCategoryValid()) {
                pinnedCategorySwitch.isChecked = false
                pinnedCategoryContainer.performClick()

                return@setOnCheckedChangeListener
            }

            pinsPrefs.edit()
                .putBoolean(PinnedCategory.PINNED_CATEGORY_VISIBLE, isChecked)
                .apply()
        }

        pinnedCategoryContainer.setOnClickListener(object : View.OnClickListener {
            private var dialog: PinnedCategoryDialog? = null
            private var showing = false

            override fun onClick(view: View) {
                val dialog = this.dialog ?: PinnedCategoryDialog(activity, pinsPrefs) { isChecked ->
                    pinnedCategorySwitch.isChecked = isChecked

                    isPinnedCategoryValid() && isChecked
                }.also {
                    it.setOnDismissListener {
                        updatePinnedCategoryName()
                        showing = false
                    }

                    this.dialog = it
                }

                if (!showing) {
                    dialog.show()
                    showing = true
                }
            }
        })
    }

    private fun initClockSection() {
        setupSwitch(
            root.findViewById(R.id.clock), root.findViewById(R.id.clock_container),
            settingsPrefs.getBoolean(ClockWidget.CLOCK_WIDGET_KEY, true)
        ) { _, checked ->
            settingsPrefs.edit().putBoolean(ClockWidget.CLOCK_WIDGET_KEY, checked).apply()
        }

        val slider = root.findViewById<Slider>(R.id.background_slider)

        slider.valueFrom = 0f
        slider.valueTo = 255f
        slider.stepSize = 1f

        slider.value = clockPrefs.getInt(StylizedClockView.BACKGROUND_ALPHA_KEY, 0).toFloat()

        slider.addOnChangeListener { _, value, _ ->
            clockPrefs.edit()
                .putInt(StylizedClockView.BACKGROUND_ALPHA_KEY, value.toInt())
                .apply()
        }

        setupSwitch(
            root.findViewById(R.id.imperial_clock),
            root.findViewById(R.id.imperial_clock_container),
            clockPrefs.getBoolean(
                StylizedClockView.IMPERIAL_KEY, Utils.isSystemUsingImperial(activity)
            )
        ) { _, checked ->
            clockPrefs.edit().putBoolean(StylizedClockView.IMPERIAL_KEY, checked).apply()
        }
    }

    private fun initWeatherSection() {
        setupSwitch(
            root.findViewById(R.id.weather), root.findViewById(R.id.weather_container),
            weatherPrefs.getBoolean(Weather.FORECAST_KEY, true)
        ) { _, checked ->
            weatherPrefs.edit().putBoolean(Weather.FORECAST_KEY, checked).apply()

            @Suppress("DEPRECATION")
            LocalBroadcastManager.getInstance(activity)
                .sendBroadcastSync(Intent(Weather.ACTION_REQUEST_UPDATE))
        }

        val locationName = root.findViewById<TextView>(R.id.location_name)
        locationName.text = getLocationString()

        root.findViewById<View>(R.id.location).setOnClickListener(object : View.OnClickListener {
            private var dialog: LocationDialog? = null
            private var showing = false

            override fun onClick(view: View) {
                val dialog = this.dialog ?: LocationDialog(activity).also {
                    it.setOnLocationUpdateListener { locationName.text = getLocationString() }
                    it.setOnDismissListener { showing = false }

                    this.dialog = it
                }

                if (!showing) {
                    dialog.show()
                    showing = true
                }
            }
        })

        val units = root.findViewById<MaterialButtonToggleGroup>(R.id.units)
        val imperial = weatherPrefs.getBoolean(
            Weather.IMPERIAL_KEY, Utils.isSystemUsingImperial(activity)
        )

        units.check(if (imperial) R.id.units_imperial else R.id.units_metric)
        units.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                weatherPrefs.edit()
                    .putBoolean(Weather.IMPERIAL_KEY, checkedId == R.id.units_imperial)
                    .apply()
            }
        }
    }

    private fun initGestureSection() {
        setupSwitch(
            lockSwitch, root.findViewById(R.id.lock_container),
            settingsPrefs.getBoolean(LockDetector.PREFERENCE_ENTRY, false)
        ) { _, checked ->
            settingsPrefs.edit().putBoolean(LockDetector.PREFERENCE_ENTRY, checked).apply()

            if (checked && !Utils.isAccessibilityServiceEnabled(activity)) {
                showAccessibilityPermissionDialog()
            }

            updateLockAnimationState(checked)
        }

        setupSwitch(
            lockAnimSwitch, root.findViewById(R.id.lock_animation_container),
            settingsPrefs.getBoolean(LockDetector.LEGACY_ANIMATION, false)
        ) { _, checked ->
            settingsPrefs.edit().putBoolean(LockDetector.LEGACY_ANIMATION, checked).apply()
        }

        updateLockAnimationState(
            settingsPrefs.getBoolean(LockDetector.PREFERENCE_ENTRY, false)
        )
    }

    override fun show() {
        super.show()

        checkNotificationPermission()
        checkAccessibilityPermission()

        scroller.scrollTo(0, 0)
    }

    private fun checkNotificationPermission() {
        if (!Utils.isNotificationServiceEnabled(activity)) {
            mediaSwitch.isChecked = false
        }
    }

    private fun checkAccessibilityPermission() {
        if (!Utils.isAccessibilityServiceEnabled(activity)) {
            lockSwitch.isChecked = false
        }
    }

    private fun isPinnedCategoryValid(): Boolean {
        val identifier = pinsPrefs.getString(PinnedCategory.PINNED_CATEGORY, null)
            ?: return false

        return try {
            CategoryManager.getInstance().get(UUID.fromString(identifier)) != null
        } catch (exception: IllegalArgumentException) {
            false
        }
    }

    private fun updateLockAnimationState(enabled: Boolean) {
        lockAnimContainer.alpha = if (enabled) 1f else 0.6f

        if (enabled) {
            lockAnimContainer.setOnClickListener { lockAnimSwitch.performClick() }
        } else {
            lockAnimContainer.setOnClickListener(null)
        }
    }

    private fun updatePinnedCategoryName() {
        if (!pinsPrefs.contains(PinnedCategory.PINNED_CATEGORY)) {
            pinnedCategoryName.visibility = View.GONE

            return
        }

        try {
            pinnedCategoryName.text = CategoryManager.getInstance().getCategoryName(
                UUID.fromString(pinsPrefs.getString(PinnedCategory.PINNED_CATEGORY, ""))
            )
            pinnedCategoryName.visibility = View.VISIBLE
        } catch (exception: IllegalArgumentException) {
            pinnedCategoryName.visibility = View.GONE
        } catch (exception: NullPointerException) {
            pinnedCategoryName.visibility = View.GONE
        }
    }

    private fun getLocationString(): String {
        val resources = activity.resources

        val precise = activity.checkSelfPermission(
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                weatherPrefs.getBoolean(Weather.PRECISE_LOCATION, false)

        if (precise) {
            return resources.getString(R.string.precise_location)
        }

        return weatherPrefs.getString(
            Weather.LOCATION_NAME, resources.getString(R.string.location_ip_based)
        ) ?: resources.getString(R.string.location_ip_based)
    }

    private fun showNotificationPermissionDialog() {
        val dialog = NotificationConfigurator(activity)

        dialog.setOnDismissListener { checkNotificationPermission() }
        dialog.show()
    }

    private fun showAccessibilityPermissionDialog() {
        val dialog = AccessibilityConfigurator(activity)

        dialog.setOnDismissListener { checkAccessibilityPermission() }
        dialog.show()
    }

    private fun setupSwitch(
        switchView: MaterialSwitch,
        container: View?,
        defaultValue: Boolean,
        listener: CompoundButton.OnCheckedChangeListener
    ) {
        switchView.isChecked = defaultValue
        switchView.jumpDrawablesToCurrentState()
        switchView.setOnCheckedChangeListener(listener)

        container?.setOnClickListener { switchView.performClick() }
    }

    override fun blurBehind(): Boolean = true

    override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_HALF_EXPANDED
}
