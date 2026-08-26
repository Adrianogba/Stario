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

package adrianogba.stario.launcher.apps

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.Pair
import android.util.Xml
import androidx.core.content.res.ResourcesCompat
import adrianogba.stario.launcher.BuildConfig
import adrianogba.stario.launcher.Stario
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.icons.AdaptiveIconView
import adrianogba.stario.launcher.ui.icons.PathCornerTreatmentAlgorithm
import adrianogba.stario.launcher.ui.utils.UiUtils
import adrianogba.stario.launcher.utils.ImageUtils
import adrianogba.stario.launcher.utils.Utils
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

class IconPackManager private constructor(
    stario: Stario,
    private var listener: OnChangeListener?
) {

    private val iconPacks = ArrayList<IconPack>()
    private val preferences: SharedPreferences = stario.getSharedPreferences(Entry.ICONS)
    private val packageManager: PackageManager = stario.packageManager
    private val launcherApps: LauncherApps =
        stario.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    private var activeIconPack: IconPack? = null

    fun setActiveIconPack(pack: IconPack?) {
        val cornerRadius = preferences.getFloat(
            AdaptiveIconView.CORNER_RADIUS_ENTRY, AdaptiveIconView.DEFAULT_CORNER_RADIUS
        )
        val pathAlgorithm = preferences.getInt(
            PathCornerTreatmentAlgorithm.PATH_ALGORITHM_ENTRY,
            PathCornerTreatmentAlgorithm.DEFAULT_PATH_ALGORITHM_ENTRY
        )

        val editor = preferences.edit()

        editor.clear()
            .putString(ICON_PACK_ENTRY, pack?.application?.info?.packageName)

        if (cornerRadius != AdaptiveIconView.DEFAULT_CORNER_RADIUS) {
            editor.putFloat(AdaptiveIconView.CORNER_RADIUS_ENTRY, cornerRadius)
        }

        if (pathAlgorithm != PathCornerTreatmentAlgorithm.DEFAULT_PATH_ALGORITHM_ENTRY) {
            editor.putInt(PathCornerTreatmentAlgorithm.PATH_ALGORITHM_ENTRY, pathAlgorithm)
        }

        editor.apply()

        activeIconPack = pack

        listener?.onChange()
    }

    fun setIconPackPreference(packageName: String, pack: IconPack?, drawableName: String?) {
        if (pack != null) {
            var json = "{" + JSON_ICON_PACK + ":\"" + pack.application.info.packageName + "\""

            if (drawableName != null) {
                json = json + "," + JSON_ICON_DRAWABLE_NAME + ":\"" + drawableName + "\""
            }

            preferences.edit()
                .putString(packageName, "$json}")
                .apply()
        } else {
            preferences.edit()
                .putString(packageName, BuildConfig.APPLICATION_ID)
                .apply()
        }

        updateIcon(packageName)
    }

    val count: Int
        get() = iconPacks.size

    fun getPack(index: Int): IconPack = iconPacks[index]

    fun getPack(packageName: String?): IconPack? {
        for (pack in iconPacks) {
            if (pack.application.info.packageName == packageName) {
                return pack
            }
        }

        return null
    }

    internal fun updateIcon(packageName: String) {
        Utils.submitTask {
            var pack = activeIconPack
            var drawableName: String? = null

            if (preferences.contains(packageName)) {
                val packagePreference = preferences.getString(packageName, null)

                if (packagePreference != null) {
                    if (packagePreference == BuildConfig.APPLICATION_ID) {
                        pack = null
                    } else {
                        try {
                            val json = JSONObject(packagePreference)

                            val target = getPack(json.get(JSON_ICON_PACK) as String)
                            if (target != null) {
                                pack = target

                                if (json.has(JSON_ICON_DRAWABLE_NAME)) {
                                    drawableName = json.get(JSON_ICON_DRAWABLE_NAME) as String
                                }
                            }
                        } catch (exception: Exception) {
                            Log.e(
                                TAG, "loadDrawable: " +
                                        "Malformed JSON icon store for package " + packageName
                            )
                        }
                    }
                }
            }

            if (pack == null) {
                ProfileManager.getInstance()
                    .updateIcon(packageName, ImageUtils.getIcon(launcherApps, packageName))

                return@submitTask
            }

            pack.loadDrawable(packageName, drawableName).thenAccept { icon ->
                val drawable = icon ?: ImageUtils.getIcon(launcherApps, packageName)

                UiUtils.post { ProfileManager.getInstance().updateIcon(packageName, drawable) }
            }
        }
    }

    @Synchronized
    internal fun remove(application: LauncherApplication?) {
        if (application == null) {
            return
        }

        for (index in iconPacks.indices) {
            if (application == iconPacks[index].application) {
                iconPacks.removeAt(index)

                return
            }
        }
    }

    @Synchronized
    internal fun add(application: LauncherApplication) {
        if (!checkPackValidity(application)) {
            return
        }

        val iconPack = IconPack(application)
        iconPacks.add(iconPack)

        if (application.info.packageName == preferences.getString(ICON_PACK_ENTRY, null)) {
            activeIconPack = iconPack

            listener?.onChange()
        }
    }

    @Synchronized
    internal fun refresh() {
        for (pack in iconPacks) {
            pack.invalidate()
        }
    }

    fun checkPackValidity(application: LauncherApplication): Boolean =
        checkPackValidity(application.info.packageName)

    fun checkPackValidity(packageName: String): Boolean {
        for (action in ICON_PACK_ACTIONS) {
            val resolved = Intent(action)
                .setPackage(packageName)
                .resolveActivity(packageManager)

            if (resolved != null) {
                return true
            }
        }

        return false
    }

    /**
     * @param application [LauncherApplication] for which to return all available icons
     * @return A [CompletableFuture] holding a list of mappings from an existing [IconPack]
     * to the respective icon name and [Drawable]. The first entry has a null pack and a
     * null name: that is the system icon, offered as the way back to the default.
     */
    fun getIcons(
        application: LauncherApplication
    ): CompletableFuture<List<Pair<IconPack?, Pair<String?, Drawable?>>>> {
        val future = CompletableFuture<List<Pair<IconPack?, Pair<String?, Drawable?>>>>()

        Utils.submitTask {
            try {
                val result = ArrayList<Pair<IconPack?, Pair<String?, Drawable?>>>()

                result.add(
                    Pair(
                        null,
                        Pair(null, ImageUtils.getIcon(launcherApps, application.info.packageName))
                    )
                )

                for (pack in iconPacks) {
                    val drawableNames =
                        pack.getDrawableNameList(application.info.packageName).get()

                    if (drawableNames != null) {
                        for (drawableName in drawableNames) {
                            val drawable = pack.getDrawable(drawableName)

                            if (drawable != null) {
                                result.add(Pair(pack, Pair(drawableName, drawable)))
                            }
                        }
                    }
                }

                future.complete(result)
            } catch (exception: ExecutionException) {
                future.complete(ArrayList())
            } catch (exception: InterruptedException) {
                future.complete(ArrayList())
            }
        }

        return future
    }

    @SuppressLint("DiscouragedApi")
    inner class IconPack internal constructor(
        internal val application: LauncherApplication
    ) {

        private val exactComponentDrawable = HashMap<String, MutableList<String>>()
        private val packageNameDrawables = HashMap<String, MutableList<String>>()
        private val completionListeners: MutableList<Runnable> =
            Collections.synchronizedList(ArrayList())

        private var loadTask: CompletableFuture<Boolean>? = null
        private lateinit var resources: Resources
        private var cached = false

        @Synchronized
        fun load(completionListener: Runnable?) {
            if (cached) {
                completionListener?.run()

                return
            }

            val running = loadTask

            if (running != null && !running.isDone && completionListener != null) {
                completionListeners.add(completionListener)

                return
            }

            val task = Utils.submitTask(Callable {
                try {
                    if (completionListener != null) {
                        completionListeners.add(completionListener)
                    }

                    var parser: XmlPullParser? = null

                    resources = packageManager
                        .getResourcesForApplication(application.info.packageName)
                    val appFilterId = resources.getIdentifier(
                        "appfilter", "xml", application.info.packageName
                    )

                    if (appFilterId > 0) {
                        parser = resources.getXml(appFilterId)
                    } else {
                        try {
                            val appFilterStream = resources.assets.open("appfilter.xml")

                            val factory = XmlPullParserFactory.newInstance()
                            factory.isNamespaceAware = true

                            parser = factory.newPullParser()
                            parser.setInput(appFilterStream, Xml.Encoding.UTF_8.toString())
                        } catch (exception: IOException) {
                            Log.d(TAG, "No appfilter.xml file")
                        }
                    }

                    if (parser != null) {
                        parse(parser)
                    }

                    for (index in completionListeners.indices) {
                        completionListeners[index].run()
                    }

                    completionListeners.clear()

                    return@Callable true
                } catch (exception: PackageManager.NameNotFoundException) {
                    Log.d(TAG, "Cannot load icon pack")
                } catch (exception: XmlPullParserException) {
                    Log.d(TAG, "Cannot parse icon pack appfilter.xml")
                } catch (exception: IOException) {
                    Log.e(TAG, "", exception)
                }

                false
            })

            loadTask = task
            task.thenAccept { result -> cached = result }
        }

        private fun parse(parser: XmlPullParser) {
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG &&
                    (parser.name == "item" || parser.name == "calendar")
                ) {
                    var componentName: String? = null
                    var drawableName: String? = null
                    var prefix: String? = null

                    for (i in 0 until parser.attributeCount) {
                        when (parser.getAttributeName(i)) {
                            "component" -> {
                                var value = parser.getAttributeValue(i)

                                if (value.indexOf('{') != -1 &&
                                    value.indexOf('{') + 1 < value.lastIndexOf('}')
                                ) {
                                    value = value.substring(
                                        value.indexOf('{') + 1, value.lastIndexOf('}')
                                    )
                                }

                                componentName = value
                            }

                            "drawable" -> drawableName = parser.getAttributeValue(i)
                            "prefix" -> prefix = parser.getAttributeValue(i)
                        }
                    }

                    if (componentName != null && componentName.contains("/")) {
                        if (drawableName != null) {
                            saveDrawable(componentName, drawableName)
                        }

                        if (prefix != null) {
                            for (day in 1..31) {
                                saveDrawable(componentName, prefix + day)
                            }
                        }
                    }
                }

                eventType = parser.next()
            }
        }

        private fun saveDrawable(componentName: String, drawableName: String) {
            addDrawable(exactComponentDrawable, componentName, drawableName)

            val changedComponent = CHANGED_COMPONENTS[componentName]
            if (changedComponent != null) {
                addDrawable(exactComponentDrawable, changedComponent, drawableName)
            }

            addDrawable(
                packageNameDrawables,
                componentName.substring(0, componentName.indexOf('/')),
                drawableName
            )
        }

        private fun addDrawable(
            target: HashMap<String, MutableList<String>>, key: String, drawableName: String
        ) {
            val drawables = target.getOrPut(key) { ArrayList() }

            if (!drawables.contains(drawableName)) {
                drawables.add(drawableName)
            }
        }

        internal fun getDrawable(drawableName: String): Drawable? {
            val id = resources.getIdentifier(
                drawableName, "drawable", application.info.packageName
            )

            return if (id > 0) ResourcesCompat.getDrawable(resources, id, null) else null
        }

        fun loadDrawable(packageName: String, drawable: String?): CompletableFuture<Drawable?> {
            val future = CompletableFuture<Drawable?>()

            load {
                var drawableName = drawable
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

                if (launchIntent != null) {
                    if (drawableName == null) {
                        val component = launchIntent.component

                        if (component != null) {
                            val drawableNames = exactComponentDrawable[
                                component.packageName + "/" + component.className
                            ]

                            if (!drawableNames.isNullOrEmpty()) {
                                drawableName = drawableNames[0]
                            }
                        }
                    }

                    if (drawableName != null) {
                        future.complete(getDrawable(drawableName))
                    }
                }

                // No-op once the future is already complete, which is what the
                // branch above does when it finds a drawable.
                future.complete(null)
            }

            return future
        }

        internal fun getDrawableNameList(packageName: String):
                CompletableFuture<List<String>?> {
            val future = CompletableFuture<List<String>?>()

            load {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

                if (launchIntent != null) {
                    val component = launchIntent.component

                    if (component != null) {
                        future.complete(packageNameDrawables[component.packageName])
                    }
                }

                future.complete(null)
            }

            return future
        }

        val label: String
            get() = application.getLabel()

        val icon: Drawable?
            get() = application.getIcon()

        fun getComponentCount(): CompletableFuture<Int> {
            val future = CompletableFuture<Int>()

            UiUtils.post {
                if (cached) {
                    future.complete(exactComponentDrawable.size)
                } else {
                    Utils.submitTask {
                        load { future.complete(exactComponentDrawable.size) }
                    }
                }
            }

            return future
        }

        fun invalidate() {
            exactComponentDrawable.clear()
            cached = false

            load(null)
        }

        override fun equals(other: Any?): Boolean =
            other is IconPack && application == other.application

        override fun hashCode(): Int = application.hashCode()
    }

    fun interface OnChangeListener {
        fun onChange()
    }

    companion object {
        const val ICON_PACK_ENTRY: String = "com.stario.ICON_PACK"

        private const val TAG = "IconPackManager"
        private const val JSON_ICON_PACK = "pack"
        private const val JSON_ICON_DRAWABLE_NAME = "drawable"

        /**
         * Every launcher that ever shipped its own icon pack intent. A package that
         * answers any one of them is an icon pack.
         */
        private val ICON_PACK_ACTIONS = arrayOf(
            "org.adw.launcher.THEMES",                        // ADW
            "app.lawnchair.icons.THEMED_ICON",                // Lawnchair 14
            "ch.deletescape.lawnchair.ICONPACK",              // Lawnchair legacy
            "com.novalauncher.THEME",                         // Nova
            "com.gau.go.launcherex.theme",                    // GO
            "ginlemon.smartlauncher.THEMES",                  // Smart Launcher
            "com.tsf.shell.themes",                           // TSF Shell
            "net.oneplus.launcher.icons.ACTION_PICK_ICON",    // OnePlus
            "com.motorola.launcher3.ACTION_ICON_PACK",        // Moto
        )

        /**
         * Applications that changed their launch components along the years.
         * Feel free to update this whenever you find other apps that did so.
         */
        private val CHANGED_COMPONENTS = mapOf(
            "com.google.android.googlequicksearchbox/com.google.android.googlequicksearchbox.SearchActivity"
                    to "com.google.android.googlequicksearchbox/com.google.android.googlequicksearchbox.GoogleAppImplicitMainInfoGatewayInternal",
            "com.google.android.apps.safetyhub/com.google.android.apps.safetyhub.LauncherActivity"
                    to "com.google.android.apps.safetyhub/com.google.android.apps.safetyhub.home.HomePageAppInfoEntry",
        )

        private var instance: IconPackManager? = null

        @JvmStatic
        fun from(activity: ThemedActivity): IconPackManager =
            from(activity.applicationContext)

        @JvmStatic
        fun from(stario: Stario): IconPackManager {
            return instance ?: IconPackManager(stario, null).also { instance = it }
        }

        internal fun from(stario: Stario, listener: OnChangeListener): IconPackManager {
            val existing = instance

            if (existing == null) {
                return IconPackManager(stario, listener).also { instance = it }
            }

            existing.listener = listener

            return existing
        }
    }
}
