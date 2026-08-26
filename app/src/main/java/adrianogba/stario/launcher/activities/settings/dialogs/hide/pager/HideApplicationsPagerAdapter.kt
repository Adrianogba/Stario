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

package adrianogba.stario.launcher.activities.settings.dialogs.hide.pager

import android.content.res.Resources
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.apps.ProfileManager
import adrianogba.stario.launcher.utils.Utils
import java.lang.ref.WeakReference

@Suppress("DEPRECATION")
class HideApplicationsPagerAdapter(
    manager: FragmentManager,
    private val resources: Resources
) : FragmentPagerAdapter(manager) {

    private val profileManager: ProfileManager = ProfileManager.getInstance()
    private val registeredFragments = SparseArray<WeakReference<HideApplicationsPage>>()

    override fun getPageTitle(position: Int): CharSequence? {
        val profiles = profileManager.profiles

        if (profiles.size <= 1) {
            return resources.getString(R.string.apps)
        }

        return resources.getString(
            if (Utils.isMainProfile(profiles[position].handle)) R.string.personal
            else R.string.managed
        )
    }

    override fun getItem(position: Int): Fragment =
        HideApplicationsPage(profileManager.getProfile(position))

    override fun getCount(): Int = ProfileManager.getInstance().size()

    override fun setPrimaryItem(container: ViewGroup, position: Int, object_: Any) {
        super.setPrimaryItem(container, position, object_)

        val fragment = object_ as Fragment

        fragment.view?.findViewWithTag<View>("nested")?.isNestedScrollingEnabled = true

        val fragmentManager = fragment.parentFragmentManager

        for (otherFragment in fragmentManager.fragments) {
            if (fragment != otherFragment) {
                otherFragment.view
                    ?.findViewWithTag<View>("nested")?.isNestedScrollingEnabled = false
            }
        }

        container.requestLayout()
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val fragment = super.instantiateItem(container, position) as HideApplicationsPage
        registeredFragments.put(position, WeakReference(fragment))

        return fragment
    }

    override fun destroyItem(container: ViewGroup, position: Int, object_: Any) {
        registeredFragments.remove(position)

        super.destroyItem(container, position, object_)
    }

    fun getRegisteredFragment(position: Int): HideApplicationsPage? {
        return registeredFragments.get(position)?.get()
    }
}
