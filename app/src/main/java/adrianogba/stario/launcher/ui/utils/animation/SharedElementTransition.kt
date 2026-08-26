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

import android.util.Pair
import android.view.View
import android.view.animation.PathInterpolator
import androidx.transition.ChangeBounds
import androidx.transition.ChangeTransform
import androidx.transition.Transition
import androidx.transition.TransitionSet

class SharedElementTransition(targets: List<View>) : TransitionSet() {

    init {
        ordering = ORDERING_TOGETHER

        val iconChangeBounds = ChangeBounds()
        iconChangeBounds.setResizeClip(true)

        addTransition(iconChangeBounds)

        // https://issuetracker.google.com/issues/339169168
        // It has not been fixed...
        val changeTransform = ChangeTransform()
        // This has to be false to avoid the issue
        changeTransform.setReparentWithOverlay(false)
        changeTransform.addListener(object : Transition.TransitionListener {
            private val startingVisibility =
                HashMap<Transition, MutableSet<Pair<View, Int>>>()

            private fun reset(transition: Transition) {
                val forTransition = startingVisibility.remove(transition) ?: return

                for (pair in forTransition) {
                    pair.first.visibility = pair.second
                }
            }

            override fun onTransitionStart(transition: Transition) {
                for (target in targets) {
                    val forTransition = startingVisibility
                        .computeIfAbsent(transition) { HashSet() }
                    forTransition.add(Pair(target, target.visibility))

                    target.visibility = View.INVISIBLE
                }
            }

            override fun onTransitionEnd(transition: Transition) {
                reset(transition)
            }

            override fun onTransitionCancel(transition: Transition) {
                reset(transition)
            }

            override fun onTransitionPause(transition: Transition) {
            }

            override fun onTransitionResume(transition: Transition) {
            }
        })

        addTransition(changeTransform)

        setPathMotion(SharedElementMotion())
        interpolator = PathInterpolator(0.3f, 0.9f, 0.3f, 0.95f)

        duration = Animation.LONG.duration.toLong()
    }

    // ChangeTransform is not seekable, suppress the logcat warning
    override fun isSeekingSupported(): Boolean = true
}
