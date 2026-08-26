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

package adrianogba.stario.launcher.sheet.drawer.search

import android.animation.LayoutTransition
import android.view.ViewGroup
import adrianogba.stario.launcher.hidden.LayoutTransitionHidden
import dev.rikka.tools.refine.Refine

class SearchLayoutTransition : LayoutTransitionHidden() {
    private val transition: LayoutTransition = Refine.unsafeCast(this)
    private var animate = false

    init {
        transition.enableTransitionType(LayoutTransition.CHANGING)
        transition.disableTransitionType(LayoutTransition.APPEARING)
        transition.disableTransitionType(LayoutTransition.DISAPPEARING)
        transition.disableTransitionType(LayoutTransition.CHANGE_APPEARING)
        transition.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING)
    }

    override fun layoutChange(parent: ViewGroup) {
        if (animate) {
            super.layoutChange(parent)
        }
    }

    fun setAnimate(animate: Boolean) {
        this.animate = animate
    }

    fun getUnrefinedTransition(): LayoutTransition = transition
}
