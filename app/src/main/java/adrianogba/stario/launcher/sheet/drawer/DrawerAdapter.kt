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

package adrianogba.stario.launcher.sheet.drawer

import android.os.UserHandle
import android.view.ViewGroup
import androidx.annotation.IntRange
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import adrianogba.stario.launcher.apps.ProfileManager
import adrianogba.stario.launcher.apps.interfaces.LauncherProfileListener
import adrianogba.stario.launcher.sheet.drawer.category.Categories
import adrianogba.stario.launcher.sheet.drawer.list.List

@Suppress("DEPRECATION")
class DrawerAdapter(
    private val fragmentManager: FragmentManager
) : FragmentPagerAdapter(fragmentManager) {

    private val fragments = ArrayList<Fragment?>()

    private var transaction: FragmentTransaction? = null

    init {
        val listener = ProfileListener()

        val manager = ProfileManager.getInstance()

        // Will have the same hash, therefore remove then add the updated one
        manager.removeLauncherProfileListener(listener)
        manager.addLauncherProfileListener(listener)
    }

    /**
     * Every instance hashes and compares equal, which is how the constructor
     * can replace an older adapter's registration without holding a reference
     * to it.
     */
    private inner class ProfileListener : LauncherProfileListener {
        override fun onInserted(handle: UserHandle?) {
            val oldCount = count - 1

            if (fragments.size > oldCount - 1) {
                removeFragment(oldCount - 1)

                if (fragments.size > oldCount) {
                    fragments[oldCount] = fragments[oldCount - 1]
                } else {
                    fragments.add(fragments[oldCount - 1])
                }

                fragments[oldCount - 1] = null
            }

            notifyDataSetChanged()
        }

        override fun onRemoved(handle: UserHandle?) {
            for (index in fragments.indices) {
                val fragment = fragments[index]

                if (fragment !is List || handle != fragment.getUserHandle()) {
                    continue
                }

                removeFragment(index)

                for (move in index + 1 until fragments.size) {
                    if (fragments[move] != null) {
                        removeFragment(move)
                    }

                    fragments[move - 1] = fragments[move]
                }

                if (fragments.size > count && fragments[count] != null) {
                    removeFragment(count)
                    fragments[count] = null
                }

                break
            }

            notifyDataSetChanged()
        }

        override fun hashCode(): Int = -1

        override fun equals(other: Any?): Boolean =
            other != null && hashCode() == other.hashCode()
    }

    override fun getItem(position: Int): Fragment {
        while (position >= fragments.size) {
            fragments.add(null)
        }

        if (fragments[position] == null) {
            fragments[position] = createFragment(position)
        }

        return fragments[position]!!
    }

    private fun createFragment(position: Int): Fragment {
        if (position == 0 || position == count - 1) {
            return Fragment()
        }

        if (position == CATEGORIES_POSITION) {
            return Categories()
        }

        val manager = ProfileManager.getInstance().getProfile(position - 2)
            ?: return Fragment()

        return List(manager)
    }

    private fun removeFragment(position: Int) {
        val fragment = fragments[position] ?: return
        val host = fragment.host as? FragmentActivity ?: return

        val lifecycle = host.lifecycle

        if (lifecycle.currentState == Lifecycle.State.RESUMED) {
            // The Java version passed null here with a DataFlowIssue
            // suppression. FragmentPagerAdapter never touches the container.
            @Suppress("UNCHECKED_CAST")
            destroyItem(null as ViewGroup, position, fragment)
            fragmentManager.beginTransaction()
                .remove(fragment)
                .commitNow()

            return
        }

        val pending = transaction ?: fragmentManager.beginTransaction().also { transaction = it }

        pending.remove(fragment)

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                transaction = null

                lifecycle.removeObserver(this)
            }

            override fun onResume(owner: LifecycleOwner) {
                transaction?.commitNow()
                transaction = null

                lifecycle.removeObserver(this)
            }
        })
    }

    fun getFragment(position: Int): Fragment? =
        if (position < 0 || position >= fragments.size) null else fragments[position]

    override fun getItemPosition(item: Any): Int {
        val index = fragments.indexOf(item as? Fragment)

        return if (index >= 0 && index < count) index else POSITION_NONE
    }

    @IntRange(from = PAGES.toLong())
    override fun getCount(): Int = PAGES + ProfileManager.getInstance().size()

    fun reset() {
        collapse()

        if (fragmentManager.isDestroyed) {
            return
        }

        for (fragment in fragmentManager.fragments) {
            (fragment as? ScrollToTop)?.scrollToTop()
        }
    }

    fun collapse(): Boolean {
        if (fragmentManager.isDestroyed) {
            return false
        }

        return fragmentManager.popBackStackImmediate(
            Categories.FOLDER_STACK_ID, FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
    }

    companion object {
        const val CATEGORIES_POSITION = 1

        // category page + 2 empty pages for transitioning
        private const val PAGES = 3
    }
}
