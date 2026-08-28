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

package adrianogba.stario.launcher.activities.settings

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.app.Dialog
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.CompoundButton
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.materialswitch.MaterialSwitch
import adrianogba.stario.launcher.BuildConfig
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.activities.pages.PageManager
import adrianogba.stario.launcher.activities.settings.dialogs.HomeScreenDialog
import adrianogba.stario.launcher.activities.settings.dialogs.hide.HideApplicationsDialog
import adrianogba.stario.launcher.activities.settings.dialogs.icons.IconsDialog
import adrianogba.stario.launcher.activities.settings.dialogs.language.LanguageDialog
import adrianogba.stario.launcher.activities.settings.dialogs.license.LicensesDialog
import adrianogba.stario.launcher.activities.settings.dialogs.search.engine.SearchEngineDialog
import adrianogba.stario.launcher.activities.settings.dialogs.search.results.SearchResultsDialog
import adrianogba.stario.launcher.activities.settings.dialogs.theme.ThemeDialog
import adrianogba.stario.launcher.apps.IconPackManager
import adrianogba.stario.launcher.apps.ProfileManager
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.preferences.Language
import adrianogba.stario.launcher.preferences.Vibrations
import adrianogba.stario.launcher.sheet.drawer.search.SearchEngine
import adrianogba.stario.launcher.sheet.drawer.search.SearchFragment
import adrianogba.stario.launcher.sheet.drawer.search.recyclers.adapters.WebAdapter
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.Measurements
import adrianogba.stario.launcher.ui.dialogs.DialogBackgroundDimmingController
import adrianogba.stario.launcher.ui.utils.LayoutSizeObserver
import adrianogba.stario.launcher.ui.utils.UiUtils

class Settings : ThemedActivity() {

    // Data
    private lateinit var settingsPrefs: SharedPreferences
    private lateinit var searchPrefs: SharedPreferences
    private lateinit var iconsPrefs: SharedPreferences
    private lateinit var themePrefs: SharedPreferences
    private lateinit var powerManager: PowerManager
    private lateinit var resources: Resources
    private var isBatterySaverOn = false

    // Views
    private lateinit var collapsingToolbarLayout: CollapsingToolbarLayout
    private lateinit var scroller: NestedScrollView
    private lateinit var titleLandscape: View
    private var content: ViewGroup? = null

    // Dynamic views
    private lateinit var lowSpecSwitch: MaterialSwitch
    private lateinit var searchEngineContainer: View
    private lateinit var searchEngineName: TextView
    private lateinit var lowSpecContainer: View
    private lateinit var iconPackName: TextView
    private lateinit var hideCount: TextView

    // Theme. Kept apart from the other dialogs because ThemeDialog refuses the
    // usual dismiss listener and has to survive the activity being recreated.
    private var themeDialog: ThemeDialog? = null
    private var themeDialogShowing = false

    // Misc
    private lateinit var homeRoleLauncher: ActivityResultLauncher<Intent>
    private var batterySaverReceiver: BroadcastReceiver? = null

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings)
        postponeEnterTransition()

        init()

        initViews()
        setupWindowInsets()
        setupLifecycleObservers()

        initGeneralSection()
        initDisplaySection()
        initSearchSection()
        initMiscSection()
        initFooterLinks()

        handleOrientation()

        root!!.post { startPostponedEnterTransition() }
    }

    override fun onDestroy() {
        super.onDestroy()

        batterySaverReceiver?.let { unregisterReceiver(it) }
    }

    override fun onConfigurationChanged(configuration: Configuration) {
        // Prevent layout transition glitches during rotation
        content?.layoutTransition = null

        super.onConfigurationChanged(configuration)

        handleOrientation()
        content?.post { content?.layoutTransition = LayoutTransition() }
    }

    private fun init() {
        resources = getResources()
        val stario = applicationContext

        settingsPrefs = stario.getSettings()
        iconsPrefs = stario.getSharedPreferences(Entry.ICONS)
        searchPrefs = stario.getSharedPreferences(Entry.SEARCH)
        themePrefs = stario.getSharedPreferences(Entry.THEME)

        powerManager = getSystemService(POWER_SERVICE) as PowerManager

        homeRoleLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                finishAfterTransition()
            }
        }
    }

    private fun initViews() {
        content = findViewById(R.id.content)
        scroller = findViewById(R.id.scroller)
        iconPackName = findViewById(R.id.pack_name)
        hideCount = findViewById(R.id.hidden_count)
        lowSpecSwitch = findViewById(R.id.low_spec)
        searchEngineName = findViewById(R.id.engine_name)
        titleLandscape = findViewById(R.id.title_landscape)
        searchEngineContainer = findViewById(R.id.search_engine)
        lowSpecContainer = findViewById(R.id.low_spec_container)
        collapsingToolbarLayout = findViewById(R.id.collapsing_toolbar)

        val appBar = findViewById<AppBarLayout>(R.id.app_bar)
        val titlePortrait = findViewById<TextView>(R.id.title_portrait)

        LayoutSizeObserver.attach(titlePortrait, LayoutSizeObserver.HEIGHT,
            object : LayoutSizeObserver.OnChange {
                override fun onChange(view: View, watchFlags: Int) {
                    titlePortrait.pivotY = titlePortrait.height / 2f
                }
            })

        appBar.addOnOffsetChangedListener(
            AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
                // The text should scale from 30sp to 20sp
                val factor = 1 - 0.333f *
                        (-verticalOffset.toFloat() / appBarLayout.totalScrollRange)

                if (!factor.isNaN()) {
                    titlePortrait.scaleX = factor
                    titlePortrait.scaleY = factor
                }
            })
    }

    private fun setupWindowInsets() {
        val container = findViewById<View>(R.id.container)

        Measurements.addStatusBarListener { value ->
            container.setPadding(
                container.paddingLeft, value,
                container.paddingRight, Measurements.getNavHeight()
            )
        }

        Measurements.addNavListener { value ->
            container.setPadding(
                container.paddingLeft, Measurements.getSysUIHeight(),
                container.paddingRight, value
            )
        }

        UiUtils.Notch.applyNotchMargin(
            findViewById(R.id.coordinator), UiUtils.Notch.Treatment.CENTER
        )
    }

    private fun setupLifecycleObservers() {
        isBatterySaverOn = powerManager.isPowerSaveMode

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                isBatterySaverOn = powerManager.isPowerSaveMode
                updateLowSpecState()
            }
        }
        batterySaverReceiver = receiver

        registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
    }

    /**
     * Wires a settings row to the dialog it opens.
     *
     * Every row in this screen was its own anonymous click listener holding a
     * lazily built dialog and a showing flag, eight copies of the same fifteen
     * lines. This is that once: build on first click, refuse to stack a second
     * one, and hand back control when it closes.
     */
    private fun <D : Dialog> bindDialogRow(
        row: View,
        create: () -> D,
        onDismiss: () -> Unit = {}
    ) {
        var dialog: D? = null
        var showing = false

        row.setOnClickListener {
            val instance = dialog ?: create().also { created ->
                created.setOnDismissListener {
                    showing = false
                    onDismiss()
                }

                dialog = created
            }

            if (!showing) {
                instance.show()
                showing = true
            }
        }
    }

    // Settings
    private fun initGeneralSection() {
        // Home Screen
        bindDialogRow(findViewById(R.id.home), { HomeScreenDialog(this) })

        // Page Manager
        findViewById<View>(R.id.pages).setOnClickListener {
            startActivity(
                Intent(this, PageManager::class.java),
                ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
            )
        }

        // Language
        findViewById<TextView>(R.id.language_name).setText(Language.current().displayName)

        var languageDialog: LanguageDialog? = null
        var languageDialogShowing = false

        findViewById<View>(R.id.language).setOnClickListener {
            val instance = languageDialog ?: LanguageDialog(this).also { created ->
                created.setOnLanguageSelected { stateChanged ->
                    languageDialogShowing = false

                    if (stateChanged) {
                        restart()
                    }
                }

                languageDialog = created
            }

            if (!languageDialogShowing) {
                instance.show()
                languageDialogShowing = true
            }
        }

        // Theme
        val themeName = findViewById<TextView>(R.id.theme_name)
        themeName.setText(getThemeType()!!.displayName)

        if (themePrefs.getBoolean(ThemedActivity.FORCE_DARK, false)) {
            themeName.append(" " + resources.getString(R.string.dark))
        }

        findViewById<View>(R.id.theme).setOnClickListener { showThemeDialog() }

        // A theme change recreates this activity so the new colours are actually
        // visible, and the dialog has to come back up on the other side of that
        // or the flip looks like it closed settings.
        if (ThemeDialog.pendingReopen) {
            ThemeDialog.pendingReopen = false

            root!!.post { showThemeDialog() }
        }
    }

    private fun showThemeDialog() {
        if (themeDialogShowing) {
            return
        }

        var dialog = themeDialog

        if (dialog == null) {
            dialog = ThemeDialog(this)

            dialog.setOnDismissListener(ThemeDialog.OnDismissListener { stateChanged ->
                themeDialogShowing = false

                if (stateChanged) {
                    // The launcher resolves its own theme when it inflates, so
                    // it only picks this up on a full restart.
                    restart()
                }
            })

            themeDialog = dialog
        }

        dialog.show()
        themeDialogShowing = true
    }

    private fun initDisplaySection() {
        // Hidden Apps. A DialogFragment rather than an ActionDialog, so it
        // needs the fragment manager on the first show.
        updateHiddenAppsCount()

        var hideDialog: HideApplicationsDialog? = null
        var hideDialogShowing = false

        findViewById<View>(R.id.hidden_apps).setOnClickListener {
            val instance = hideDialog ?: HideApplicationsDialog().also { created ->
                created.setOnHideListener {
                    updateHiddenAppsCount()
                    hideDialogShowing = false
                }

                hideDialog = created
            }

            if (!hideDialogShowing) {
                if (!instance.isAdded) {
                    instance.show(supportFragmentManager, "HideApplications")
                } else {
                    instance.show()
                }

                hideDialogShowing = true
            }
        }

        // Icons
        updateIconPackName()
        bindDialogRow(
            findViewById(R.id.icons), { IconsDialog(this) }, ::updateIconPackName
        )
    }

    private fun initSearchSection() {
        updateEngineName()

        // Search Engine
        bindDialogRow(
            searchEngineContainer, { SearchEngineDialog(this) }, ::updateEngineName
        )

        // Search Results
        val searchResultsContainer = findViewById<View>(R.id.search_results_container)
        val resultsSwitch = findViewById<MaterialSwitch>(R.id.search_results)

        bindDialogRow(searchResultsContainer, {
            SearchResultsDialog(this).apply {
                setStatusListener { checked -> resultsSwitch.isChecked = checked }
            }
        })

        setupSwitch(
            resultsSwitch, searchPrefs.getBoolean(WebAdapter.SEARCH_RESULTS, false)
        ) { button, checked ->
            val key = searchPrefs.getString(WebAdapter.KAGI_API_KEY, null)

            if (checked && key.isNullOrEmpty()) {
                searchResultsContainer.performClick()
                button.isChecked = false
            } else {
                searchPrefs.edit()
                    .putBoolean(WebAdapter.SEARCH_RESULTS, checked)
                    .apply()
                updateEngineName()
            }
        }

        // Search Hidden Apps
        setupSwitch(
            findViewById(R.id.search_hidden_apps),
            findViewById<View>(R.id.search_hidden_apps_container),
            searchPrefs.getBoolean(SearchFragment.SEARCH_HIDDEN_APPS, false)
        ) { _, checked ->
            searchPrefs.edit()
                .putBoolean(SearchFragment.SEARCH_HIDDEN_APPS, checked)
                .apply()
        }
    }

    private fun initMiscSection() {
        updateLowSpecState()
        lowSpecSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatterySaverOn) {
                settingsPrefs.edit()
                    .putBoolean(DialogBackgroundDimmingController.LOW_SPEC_KEY, isChecked)
                    .apply()
            }
        }
        lowSpecSwitch.jumpDrawablesToCurrentState()

        setupSwitch(
            findViewById(R.id.vibrations),
            findViewById<View>(R.id.vibrations_container),
            settingsPrefs.getBoolean(Vibrations.PREFERENCE_ENTRY, true)
        ) { _, checked ->
            settingsPrefs.edit()
                .putBoolean(Vibrations.PREFERENCE_ENTRY, checked)
                .apply()
        }

        findViewById<View>(R.id.restart).setOnClickListener { restart() }
        findViewById<View>(R.id.def_launcher)
            .setOnClickListener { requestDefaultLauncherRole() }
    }

    @SuppressLint("SetTextI18n")
    private fun initFooterLinks() {
        findViewById<TextView>(R.id.version).text =
            BuildConfig.VERSION_NAME + " • Adriano Pontes"

        findViewById<View>(R.id.about).setOnClickListener {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")

            startActivity(intent)
        }

        bindDialogRow(findViewById(R.id.licenses), { LicensesDialog(this) })

        setupUrlButton(R.id.github, "https://github.com/Adrianogba/Stario")
        setupUrlButton(R.id.website, "https://adrianogba.github.io")
    }

    // Helpers
    private fun setupSwitch(
        switchView: MaterialSwitch,
        container: View? = null,
        defaultValue: Boolean,
        listener: CompoundButton.OnCheckedChangeListener
    ) {
        switchView.isChecked = defaultValue
        switchView.jumpDrawablesToCurrentState()
        switchView.setOnCheckedChangeListener(listener)

        container?.setOnClickListener { switchView.performClick() }
    }

    private fun setupSwitch(
        switchView: MaterialSwitch,
        defaultValue: Boolean,
        listener: CompoundButton.OnCheckedChangeListener
    ) = setupSwitch(switchView, null, defaultValue, listener)

    private fun setupUrlButton(viewId: Int, url: String) {
        findViewById<View>(viewId).setOnClickListener { view ->
            view.startAnimation(AnimationUtils.loadAnimation(this, R.anim.bounce_small))

            startActivity(Intent(Intent.ACTION_DEFAULT, Uri.parse(url)))
        }
    }

    private fun requestDefaultLauncherRole() {
        val roleManager = getSystemService(RoleManager::class.java)

        if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            return
        }

        if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
            val intent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

            startActivity(intent)
        } else {
            homeRoleLauncher.launch(
                roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME),
                ActivityOptionsCompat.makeBasic()
            )
        }
    }

    private fun updateLowSpecState() {
        lowSpecContainer.alpha = if (isBatterySaverOn) 0.6f else 1f

        if (isBatterySaverOn) {
            lowSpecContainer.setOnClickListener(null)
            lowSpecSwitch.isChecked = true
        } else {
            lowSpecContainer.setOnClickListener { lowSpecSwitch.performClick() }
            lowSpecSwitch.isChecked = settingsPrefs.getBoolean(
                DialogBackgroundDimmingController.LOW_SPEC_KEY, false
            )
        }
    }

    private fun updateEngineName() {
        searchEngineName.text = SearchEngine.getEngine(applicationContext).toString()

        val resultsEnabled = searchPrefs.getBoolean(WebAdapter.SEARCH_RESULTS, false)
        searchEngineContainer.isEnabled = !resultsEnabled
        searchEngineContainer.alpha = if (resultsEnabled) 0.6f else 1f
    }

    private fun updateIconPackName() {
        val packPackageName = iconsPrefs.getString(IconPackManager.ICON_PACK_ENTRY, null)

        if (packPackageName != null) {
            val iconPackApp = ProfileManager.getInstance().getApplication(packPackageName)

            if (iconPackApp != null) {
                iconPackName.text = iconPackApp.getLabel()

                return
            }
        }

        iconPackName.setText(R.string.default_text)
    }

    @SuppressLint("SetTextI18n")
    private fun updateHiddenAppsCount() {
        var count = 0

        for (manager in ProfileManager.getInstance().profiles) {
            count += manager.actualSize - manager.size
        }

        hideCount.text = resources.getString(R.string.hidden_apps) + ": " + count
    }

    // Utils
    private fun handleOrientation() {
        scroller.stopNestedScroll()
        scroller.isNestedScrollingEnabled = false

        val params = collapsingToolbarLayout.layoutParams as AppBarLayout.LayoutParams

        if (Measurements.isLandscape()) {
            params.height = 0
            params.scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL

            collapsingToolbarLayout.isTitleEnabled = false
            titleLandscape.visibility = View.VISIBLE
        } else {
            params.height = Measurements.dpToPx(Measurements.HEADER_SIZE_DP.toFloat())
            params.scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                    AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED or
                    AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP

            collapsingToolbarLayout.isTitleEnabled = true
            titleLandscape.visibility = View.GONE
        }

        collapsingToolbarLayout.layoutParams = params

        scroller.isNestedScrollingEnabled = true
        scroller.scrollY = 0
    }

    private fun restart() {
        val intent = packageManager.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID)
            ?: return

        val mainIntent = Intent.makeRestartActivityTask(intent.component)
        mainIntent.setPackage(BuildConfig.APPLICATION_ID)

        startActivity(mainIntent)
        System.exit(0)
    }

    override val isOpaque: Boolean
        get() = true

    override val isAffectedByBackGesture: Boolean
        get() = true
}
