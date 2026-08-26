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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import adrianogba.stario.launcher.Stario
import adrianogba.stario.launcher.apps.interfaces.LauncherProfileListener
import java.util.Collections

class ProfileManager private constructor(stario: Stario) {

    private val profilesMap = HashMap<UserHandle, ProfileApplicationManager>()
    private val profilesList = ArrayList<ProfileApplicationManager>()
    private val listeners: MutableList<LauncherProfileListener> =
        Collections.synchronizedList(ArrayList())

    private val iconPacks: IconPackManager

    init {
        // CategoryData and IconPackManager needs LauncherApplicationManager
        // to be instantiated. Assign the instance in the constructor before
        // everything else to guarantee that the instance will be supplied.
        instance = this

        iconPacks = IconPackManager.from(stario) { update() }

        CategoryManager.from(stario)

        val launcherApps =
            stario.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = stario.getSystemService(Context.USER_SERVICE) as UserManager

        // work profiles will always be created after the owner
        val profiles = launcherApps.profiles
        profiles.sortWith { handle1, handle2 ->
            val diff = userManager.getUserCreationTime(handle1) -
                    userManager.getUserCreationTime(handle2)

            if (diff < 0) -1 else if (diff > 0) 1 else 0
        }

        for (handle in profiles) {
            val manager = ProfileApplicationManager(stario, handle)

            // Carried over as it was. This reads like it was meant to be
            // "owner == null", taking the first profile, since the list is
            // sorted by creation time and the comment above says the owner comes
            // first. As written it never fires on the only construction that
            // happens, so owner falls through to Process.myUserHandle() below
            // and the result is the same. Left alone rather than quietly changed.
            if (owner != null) {
                owner = handle
            }

            profilesMap[handle] = manager
            profilesList.add(manager)

            for (listener in listeners) {
                listener?.onInserted(handle)
            }
        }

        // there should always be a profile, if not, something TERRIBLE happened
        if (owner == null) {
            owner = Process.myUserHandle()
        }
    }

    fun getProfile(handle: UserHandle?): ProfileApplicationManager? {
        return profilesMap[handle ?: owner]
    }

    fun getProfile(index: Int): ProfileApplicationManager? {
        return if (index < 0 || index >= profilesList.size) null else profilesList[index]
    }

    // A property so Kotlin callers keep saying manager.profiles while Java keeps
    // calling getProfiles().
    val profiles: List<ProfileApplicationManager>
        get() = Collections.unmodifiableList(profilesList)

    fun getApplication(packageName: String): LauncherApplication? {
        for (manager in profilesList) {
            val application = manager.get(packageName)

            if (application != null) {
                return application
            }
        }

        return null
    }

    fun update() {
        for (manager in profilesList) {
            manager.update()
        }
    }

    fun updateIcon(packageName: String, icon: Drawable?) {
        for (manager in profilesList) {
            val application = manager.get(packageName)

            if (application != null && icon != null && icon != application.getIcon()) {
                application.icon = icon

                notifyUpdate(packageName)
            }
        }
    }

    fun updateLabel(packageName: String, label: String?) {
        for (manager in profilesList) {
            val application = manager.get(packageName)

            if (application != null) {
                manager.updateLabel(application, label)
            }
        }
    }

    fun notifyUpdate(packageName: String) {
        for (manager in profilesList) {
            val application = manager.get(packageName)

            if (application != null) {
                manager.notifyUpdate(application)
            }
        }
    }

    fun size(): Int = profilesList.size

    fun addLauncherProfileListener(listener: LauncherProfileListener?) {
        if (listener != null) {
            listeners.add(listener)
        }
    }

    fun removeLauncherProfileListener(listener: LauncherProfileListener?) {
        if (listener != null) {
            listeners.remove(listener)
        }
    }

    companion object {
        const val PROFILE_AVAILABLE_EXTRA: String =
            "adrianogba.stario.launcher.PROFILE_AVAILABLE_EXTRA"

        private const val PROFILE_AVAILABLE_INTENT =
            "adrianogba.stario.launcher.PROFILE_AVAILABLE_INTENT"

        private var instance: ProfileManager? = null
        private var owner: UserHandle? = null

        @JvmStatic
        @JvmOverloads
        fun from(stario: Stario, refreshIcons: Boolean = true): ProfileManager {
            val existing = instance

            if (existing == null) {
                val created = ProfileManager(stario)

                stario.registerReceiver(
                    getReceiver(created), getIntentFilter(), Context.RECEIVER_EXPORTED
                )

                return instance!!
            }

            if (refreshIcons) {
                existing.iconPacks.refresh()
                existing.update()
            }

            return existing
        }

        private fun getReceiver(instance: ProfileManager): BroadcastReceiver {
            return object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val handle = intent.getParcelableExtra(
                        Intent.EXTRA_USER, UserHandle::class.java
                    ) ?: return

                    when (intent.action) {
                        Intent.ACTION_MANAGED_PROFILE_ADDED -> {
                            if (instance.profilesMap.containsKey(handle)) {
                                return
                            }

                            val launcherApps = context.getSystemService(
                                Context.LAUNCHER_APPS_SERVICE
                            ) as LauncherApps

                            for (profileHandle in launcherApps.profiles) {
                                if (handle == profileHandle) {
                                    val manager = ProfileApplicationManager(
                                        context.applicationContext as Stario, profileHandle
                                    )

                                    instance.profilesMap[profileHandle] = manager
                                    instance.profilesList.add(manager)

                                    for (listener in instance.listeners) {
                                        listener?.onInserted(profileHandle)
                                    }
                                }
                            }
                        }

                        Intent.ACTION_MANAGED_PROFILE_REMOVED -> {
                            val manager = instance.profilesMap.remove(handle) ?: return

                            instance.profilesList.remove(manager)

                            for (listener in instance.listeners) {
                                listener?.onRemoved(handle)
                            }
                        }

                        Intent.ACTION_MANAGED_PROFILE_AVAILABLE ->
                            broadcastAvailability(context, handle, true)

                        Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE ->
                            broadcastAvailability(context, handle, false)
                    }
                }
            }
        }

        private fun broadcastAvailability(
            context: Context, handle: UserHandle, available: Boolean
        ) {
            val intent = Intent(getProfileAvailabilityIntentAction(handle))
            intent.putExtra(PROFILE_AVAILABLE_EXTRA, available)

            LocalBroadcastManager.getInstance(context).sendBroadcastSync(intent)
        }

        @JvmStatic
        fun getOwner(): UserHandle {
            val owner = owner

            if (instance == null || owner == null) {
                throw IllegalStateException("ProfileManager not initialized")
            }

            return owner
        }

        @JvmStatic
        fun getProfileAvailabilityIntentAction(handle: UserHandle?): String {
            return PROFILE_AVAILABLE_INTENT + (if (handle != null) ":$handle" else "")
        }

        private fun getIntentFilter(): IntentFilter {
            val intentFilter = IntentFilter()
            intentFilter.addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            intentFilter.addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            intentFilter.addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            intentFilter.addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            intentFilter.addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)

            return intentFilter
        }

        @JvmStatic
        fun getInstance(): ProfileManager {
            return instance ?: throw RuntimeException("Applications not initialized.")
        }
    }
}
