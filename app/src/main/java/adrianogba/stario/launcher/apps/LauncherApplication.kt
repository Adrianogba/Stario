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

import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.UserHandle
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.utils.Utils
import java.util.UUID

/**
 * The fields are @JvmField rather than ordinary properties because the rest of
 * this package writes them directly, the way the Java original did. The public
 * getters below are kept for everything outside the package, which only reads.
 */
class LauncherApplication(
    info: ApplicationInfo,
    handle: UserHandle,
    label: String
) : Comparable<LauncherApplication> {

    @JvmField
    val systemPackage: Boolean = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0

    @JvmField
    var info: ApplicationInfo = info

    @JvmField
    var label: String = label

    @JvmField
    var category: UUID = UUID.randomUUID()

    @JvmField
    var handle: UserHandle = handle

    @JvmField
    var icon: Drawable? = null

    @JvmField
    var notificationCount: Int = 0

    fun launch(activity: ThemedActivity) {
        val main = Utils.getMainActivity(activity, info.packageName, handle) ?: return

        activity.getSystemService(LauncherApps::class.java)
            .startMainActivity(main.componentName, handle, null, null)
    }

    fun getInfo(): ApplicationInfo = info

    fun getLabel(): String = label

    fun getIcon(): Drawable? = icon

    fun getCategory(): UUID = category

    fun getProfile(): UserHandle = handle

    override fun hashCode(): Int = info.packageName.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is LauncherApplication) {
            return false
        }

        return info.packageName == other.info.packageName && handle == other.handle
    }

    override fun compareTo(other: LauncherApplication): Int {
        val result = label.compareTo(other.label)

        return if (result != 0) result else handle.hashCode() - other.handle.hashCode()
    }
}
