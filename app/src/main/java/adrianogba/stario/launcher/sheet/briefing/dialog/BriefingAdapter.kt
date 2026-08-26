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

package adrianogba.stario.launcher.sheet.briefing.dialog

import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.PagerAdapter
import adrianogba.stario.launcher.sheet.briefing.dialog.page.FeedPage
import adrianogba.stario.launcher.sheet.briefing.dialog.page.feed.BriefingFeedList
import adrianogba.stario.launcher.themes.ThemedActivity
import java.lang.ref.WeakReference

@Suppress("DEPRECATION")
class BriefingAdapter(
    activity: ThemedActivity,
    fragmentManager: FragmentManager
) : FragmentPagerAdapter(fragmentManager) {

    private val registeredFragments = HashMap<Int, WeakReference<FeedPage>>()
    private val list: BriefingFeedList = BriefingFeedList.from(activity)

    override fun getItem(position: Int): Fragment = FeedPage(position)

    override fun getCount(): Int = list.size()

    override fun getItemPosition(object_: Any): Int = PagerAdapter.POSITION_NONE

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val fragment = super.instantiateItem(container, position) as FeedPage
        registeredFragments[position] = WeakReference(fragment)

        return fragment
    }

    override fun getItemId(position: Int): Long {
        val feed = list.get(position)

        return if (feed != null) feed.getRSSLink().hashCode().toLong() else position.toLong()
    }

    override fun destroyItem(container: ViewGroup, position: Int, object_: Any) {
        registeredFragments.remove(position)

        super.destroyItem(container, position, object_)
    }

    override fun getPageTitle(position: Int): CharSequence? = list.get(position).title

    fun getRegisteredFragment(position: Int): FeedPage? {
        return registeredFragments[position]?.get()
    }

    fun reset(vararg skipPositions: Int) {
        for (entry in registeredFragments.entries) {
            var skipped = false

            for (skippedPosition in skipPositions) {
                if (entry.key == skippedPosition) {
                    skipped = true

                    break
                }
            }

            if (!skipped) {
                entry.value.get()?.reset()
            }
        }
    }
}
