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

package adrianogba.stario.launcher.ui.utils.animation

import android.view.View
import android.view.ViewGroup
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.Fade
import androidx.transition.Transition
import androidx.transition.TransitionPropagation
import androidx.transition.TransitionSet
import androidx.transition.TransitionValues
import com.google.android.material.transition.MaterialElevationScale
import adrianogba.stario.launcher.R

class FragmentTransition @JvmOverloads constructor(
    growing: Boolean,
    exclusions: List<View>? = null
) : TransitionSet() {

    private class StaggerPropagation : TransitionPropagation() {

        override fun getStartDelay(
            sceneRoot: ViewGroup, transition: Transition,
            startValues: TransitionValues?, endValues: TransitionValues?
        ): Long {
            if (endValues == null || !endValues.values.containsKey(PROP_STAGGER_INDEX)) {
                return 0
            }

            val staggerIndex = endValues.values[PROP_STAGGER_INDEX]
            val index = if (staggerIndex is Int) staggerIndex else 0

            return index * STAGGER_DELAY_MS
        }

        override fun captureValues(transitionValues: TransitionValues) {
            val tag = transitionValues.view.getTag(R.id.stagger_order_tag)
            transitionValues.values[PROP_STAGGER_INDEX] = if (tag is Int) tag else 0
        }

        override fun getPropagationProperties(): Array<String> {
            return arrayOf(PROP_STAGGER_INDEX)
        }

        private companion object {
            private const val PROP_STAGGER_INDEX =
                "adrianogba.stario.launcher:propagation:staggerIndex"
            private const val STAGGER_DELAY_MS = 20L
        }
    }

    init {
        ordering = ORDERING_TOGETHER

        addTransition(Fade())
        addTransition(MaterialElevationScale(growing))

        exclusions?.forEach { excludeTarget(it, true) }

        interpolator = FastOutSlowInInterpolator()
        duration = Animation.LONG.duration.toLong()
        propagation = StaggerPropagation()
    }
}
