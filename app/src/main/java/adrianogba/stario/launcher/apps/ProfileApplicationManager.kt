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

import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.UserHandle
import android.util.Log
import adrianogba.stario.launcher.BuildConfig
import adrianogba.stario.launcher.Stario
import adrianogba.stario.launcher.apps.interfaces.LauncherApplicationListener
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.ui.utils.UiUtils
import adrianogba.stario.launcher.utils.Utils
import java.util.Collections

class ProfileApplicationManager internal constructor(
    stario: Stario,
    @JvmField val handle: UserHandle
) {

    private val visibleApplicationList: MutableList<LauncherApplication> =
        Collections.synchronizedList(ArrayList())
    private val applicationList: MutableList<LauncherApplication> =
        Collections.synchronizedList(ArrayList())
    private val applicationMap = HashMap<String, LauncherApplication>()
    private val listeners: MutableList<LauncherApplicationListener> =
        Collections.synchronizedList(ArrayList())
    private val readyListeners = ArrayList<OnLoadReadyListener>()
    private val hiddenApplications: SharedPreferences
    private val applicationLabels: SharedPreferences
    private val packageManager: PackageManager
    private val iconPacks: IconPackManager

    private var loaded = false

    init {
        applicationLabels = stario.getSharedPreferences(Entry.APPLICATION_LABELS)
        hiddenApplications = stario.getSharedPreferences(
            Entry.HIDDEN_APPS, handle.hashCode().toString()
        )
        packageManager = stario.packageManager

        val launcherApps = stario.getSystemService(LauncherApps::class.java)
        launcherApps.registerCallback(getReceiver(launcherApps))

        iconPacks = IconPackManager.from(stario)
        Utils.submitTask { loadApplications(stario) }
    }

    private fun getReceiver(launcherApps: LauncherApps): LauncherApps.Callback {
        return object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                if (handle != user || BuildConfig.APPLICATION_ID == packageName) {
                    return
                }

                removeApplication(packageName)
            }

            override fun onPackageAdded(packageName: String, user: UserHandle) {
                if (handle != user || BuildConfig.APPLICATION_ID == packageName) {
                    return
                }

                val applicationInfo = getApplicationInfo(packageName)

                if (applicationInfo != null && applicationInfo.enabled) {
                    addApplication(createApplication(applicationInfo))
                }
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                if (handle != user || BuildConfig.APPLICATION_ID == packageName) {
                    return
                }

                val applicationInfo = getApplicationInfo(packageName)

                if (applicationInfo == null) {
                    removeApplication(packageName)

                    return
                }

                val application = get(packageName)

                if (application == null) {
                    addApplication(createApplication(applicationInfo))
                } else if (!applicationInfo.enabled) {
                    removeApplication(packageName)
                } else {
                    application.info = applicationInfo
                    updateApplication(application)
                }
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>, user: UserHandle, replacing: Boolean
            ) {
                for (packageName in packageNames) {
                    if (replacing) {
                        onPackageChanged(packageName, user)
                    } else {
                        onPackageAdded(packageName, user)
                    }
                }
            }

            override fun onPackagesUnavailable(
                packageNames: Array<out String>, user: UserHandle, replacing: Boolean
            ) {
                for (packageName in packageNames) {
                    if (!replacing) {
                        onPackageRemoved(packageName, user)
                    }
                }
            }

            // The empty activity list is checked twice, once here and once inside the
            // try below. The first check wins, so the removeApplication call guarded by
            // the second one never runs. Left as it was rather than changed blind: a
            // package losing its last launcher activity is dropped by
            // onPackagesUnavailable anyway.
            private fun getApplicationInfo(packageName: String): ApplicationInfo? {
                val containsPackage = applicationMap.containsKey(packageName)

                if (launcherApps.getActivityList(packageName, handle).isEmpty()) {
                    return null
                }

                try {
                    val applicationInfo =
                        launcherApps.getApplicationInfo(packageName, 0, handle)

                    if (launcherApps.getActivityList(packageName, handle).isEmpty()) {
                        // Doesn't have any activity that specifies
                        // Intent.ACTION_MAIN and Intent.CATEGORY_LAUNCHER

                        if (containsPackage) {
                            removeApplication(packageName)
                        }

                        return null
                    }

                    return applicationInfo
                } catch (exception: PackageManager.NameNotFoundException) {
                    Log.e(TAG, "Package $packageName does not exist.")

                    return null
                }
            }
        }
    }

    private fun loadApplications(stario: Stario) {
        val launcherApps = stario.getSystemService(LauncherApps::class.java)
        val activityInfoList = launcherApps.getActivityList(null, handle)

        val iconPackApps = ArrayList<ApplicationInfo>()
        val otherApps = ArrayList<ApplicationInfo>()

        for (activityInfo in activityInfoList) {
            val applicationInfo = activityInfo.applicationInfo

            if (BuildConfig.APPLICATION_ID == applicationInfo.packageName) {
                continue
            }

            if (iconPacks.checkPackValidity(applicationInfo.packageName)) {
                iconPackApps.add(applicationInfo)
            } else {
                otherApps.add(applicationInfo)
            }
        }

        for (appInfo in iconPackApps) {
            if (!applicationMap.containsKey(appInfo.packageName)) {
                addApplication(createApplication(appInfo))
            }
        }

        for (application in applicationList) {
            iconPacks.updateIcon(application.info.packageName)
        }

        for (appInfo in otherApps) {
            if (!applicationMap.containsKey(appInfo.packageName)) {
                addApplication(createApplication(appInfo))
            }
        }

        loaded = true
        UiUtils.post {
            for (listener in readyListeners) {
                listener.onReady(this)
            }

            readyListeners.clear()
        }
    }

    // A property so Kotlin callers keep saying manager.isReady while Java keeps
    // calling isReady().
    val isReady: Boolean
        get() = loaded

    fun addOnReadyListener(listener: OnLoadReadyListener?) {
        if (listener != null) {
            if (loaded) {
                listener.onReady(this)
            } else {
                readyListeners.add(listener)
            }
        }
    }

    fun updateApplication(application: LauncherApplication) {
        iconPacks.updateIcon(application.info.packageName)

        // might double update, which is fine if loading the icon takes a while
        notifyUpdate(application)
    }

    internal fun update() {
        for (index in applicationList.indices) {
            updateApplication(applicationList[index])
        }
    }

    fun updateLabel(application: LauncherApplication, label: String?) {
        val oldLabel = application.label

        if (label != null) {
            if (label.isNotEmpty() && application.label != label) {
                application.label = label
            }
        } else {
            application.label = application.info.loadLabel(packageManager).toString()
        }

        if (oldLabel != application.label) {
            applicationLabels.edit()
                .putString(application.info.packageName, label)
                .apply()

            if (isVisibleToUser(application.info.packageName)) {
                notifyUpdate(application)
            }
        }
    }

    internal fun notifyUpdate(application: LauncherApplication) {
        for (listener in listeners) {
            listener.onUpdated(application)
        }
    }

    private fun createApplication(applicationInfo: ApplicationInfo): LauncherApplication {
        val application = LauncherApplication(
            applicationInfo, handle, getLabel(applicationInfo)
        )

        application.category = CategoryManager.getInstance()
            .getCategoryIdentifier(applicationInfo, handle)

        return application
    }

    private fun getLabel(applicationInfo: ApplicationInfo): String {
        return applicationLabels.getString(applicationInfo.packageName, null)
            ?: applicationInfo.loadLabel(packageManager).toString()
    }

    /**
     * @param index The index of the application
     * @return The [LauncherApplication] at index, or null if the index is out of range
     */
    fun get(index: Int): LauncherApplication? {
        return if (index >= 0 && index < visibleApplicationList.size) {
            visibleApplicationList[index]
        } else {
            null
        }
    }

    /**
     * @param index The index of the application
     * @param hidden If true, account for hidden items
     * @return The [LauncherApplication] at index, or null if the index is out of range
     */
    fun get(index: Int, hidden: Boolean): LauncherApplication? {
        if (!hidden) {
            return get(index)
        }

        return if (index >= 0 && index < applicationList.size) {
            applicationList[index]
        } else {
            null
        }
    }

    /**
     * @param packageName The package name of the application
     * @return The [LauncherApplication] for that package, or null if it is not installed
     */
    fun get(packageName: String?): LauncherApplication? {
        return if (packageName == null) null else applicationMap[packageName]
    }

    // Properties rather than functions: Java still calls getActualSize() and
    // getSize(), and Kotlin callers already say .actualSize and .size.
    val actualSize: Int
        get() = applicationList.size

    val size: Int
        get() = visibleApplicationList.size

    @Synchronized
    private fun addApplication(application: LauncherApplication) {
        applicationMap[application.info.packageName] = application

        addApplicationToList(application, applicationList)

        if (isVisibleToUser(application.info.packageName)) {
            addApplicationToList(application, visibleApplicationList)

            for (listener in listeners) {
                listener.onInserted(application)
            }
        }

        iconPacks.add(application)
        iconPacks.updateIcon(application.info.packageName)

        CategoryManager.getInstance().addApplication(application)
    }

    @Synchronized
    private fun addApplicationToList(
        applicationToAdd: LauncherApplication, list: MutableList<LauncherApplication>
    ) {
        var left = 0
        var right = list.size - 1

        while (left <= right) {
            val middle = (left + right) / 2

            val application = list[middle]
            val compareValue = application.label
                .compareTo(applicationToAdd.label, ignoreCase = true)

            if (compareValue < 0) {
                left = middle + 1
            } else if (compareValue > 0) {
                right = middle - 1
            } else if (application.info.packageName != applicationToAdd.info.packageName) {
                list.add(middle, applicationToAdd)

                return
            } else {
                return // same package found
            }
        }

        list.add(left, applicationToAdd)
    }

    @Synchronized
    private fun removeApplication(packageName: String) {
        val application = applicationMap[packageName] ?: return

        for (listener in listeners) {
            listener.onPrepareRemoval()
        }

        applicationMap.remove(packageName)
        visibleApplicationList.remove(application)
        applicationList.remove(application)

        iconPacks.remove(application)
        CategoryManager.getInstance().removeApplication(application)

        for (listener in listeners) {
            listener.onRemoved(application)
        }
    }

    @Synchronized
    fun showApplication(application: LauncherApplication) {
        hiddenApplications.edit()
            .remove(application.info.packageName)
            .apply()

        if (!visibleApplicationList.contains(application)) {
            addApplicationToList(application, visibleApplicationList)
            CategoryManager.getInstance().addApplication(application)

            for (listener in listeners) {
                listener.onShowed(application)
            }
        }
    }

    @Synchronized
    fun hideApplication(application: LauncherApplication) {
        hiddenApplications.edit()
            .putBoolean(application.info.packageName, true)
            .apply()

        for (listener in listeners) {
            listener.onPrepareHiding()
        }

        visibleApplicationList.remove(application)
        CategoryManager.getInstance().removeApplication(application)

        for (listener in listeners) {
            listener.onHidden(application)
        }
    }

    fun addApplicationListener(listener: LauncherApplicationListener?) {
        if (listener != null) {
            listeners.add(listener)
        }
    }

    fun removeApplicationListener(listener: LauncherApplicationListener?) {
        if (listener != null) {
            listeners.remove(listener)
        }
    }

    fun isVisibleToUser(application: LauncherApplication?): Boolean {
        return application != null && isVisibleToUser(application.info.packageName)
    }

    fun isVisibleToUser(packageName: String): Boolean {
        return !hiddenApplications.contains(packageName)
    }

    /**
     * @return Index of application in the hidden list
     */
    fun indexOf(application: LauncherApplication?): Int = applicationList.indexOf(application)

    fun interface OnLoadReadyListener {
        fun onReady(manager: ProfileApplicationManager)
    }

    private companion object {
        const val TAG = "ProfileApplicationManager"
    }
}
