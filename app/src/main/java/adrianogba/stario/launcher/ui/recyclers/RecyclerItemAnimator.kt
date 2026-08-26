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

package adrianogba.stario.launcher.ui.recyclers

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.ViewPropertyAnimator
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.ui.utils.animation.Animation
import java.util.Collections

open class RecyclerItemAnimator(
    private val flags: Int,
    private val animation: Animation
) : DefaultItemAnimator() {

    private val pendingRemovals: MutableList<RecyclerView.ViewHolder> =
        Collections.synchronizedList(ArrayList())
    private val pendingAdditions: MutableList<RecyclerView.ViewHolder> =
        Collections.synchronizedList(ArrayList())
    private val removeAnimations: MutableList<RecyclerView.ViewHolder> =
        Collections.synchronizedList(ArrayList())
    private val addAnimations: MutableList<RecyclerView.ViewHolder> =
        Collections.synchronizedList(ArrayList())

    init {
        if ((flags and CHANGING) != CHANGING) {
            changeDuration = 0L
            moveDuration = 0L
        }
    }

    override fun runPendingAnimations() {
        super.runPendingAnimations()

        for (holder in pendingRemovals) {
            animateRemoveImplementation(holder)
        }

        pendingRemovals.clear()

        for (holder in pendingAdditions) {
            animateAddImplementation(holder)
        }

        pendingAdditions.clear()
    }

    override fun endAnimation(item: RecyclerView.ViewHolder) {
        if (pendingRemovals.remove(item)) {
            resetToTarget(item)

            dispatchRemoveFinished(item)
        }

        if (pendingAdditions.remove(item)) {
            resetToTarget(item)

            dispatchAddFinished(item)
        }

        removeAnimations.remove(item)
        addAnimations.remove(item)

        super.endAnimation(item)
    }

    override fun endAnimations() {
        // The two dispatches below are swapped: the remove list reports add
        // finished and the add list reports remove finished. Carried over as it
        // was rather than quietly corrected, since the pairing is what
        // RecyclerView's bookkeeping has been living with.
        val removeIterator = removeAnimations.iterator()
        while (removeIterator.hasNext()) {
            val holder = removeIterator.next()

            resetToTarget(holder)

            dispatchAddFinished(holder)
            removeIterator.remove()
        }

        val addIterator = addAnimations.iterator()
        while (addIterator.hasNext()) {
            val holder = addIterator.next()

            resetToTarget(holder)

            dispatchRemoveFinished(holder)
            addIterator.remove()
        }

        super.endAnimations()
    }

    override fun isRunning(): Boolean =
        super.isRunning() || removeAnimations.isNotEmpty() || addAnimations.isNotEmpty()

    final override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
        endAnimation(holder)

        if ((flags and APPEARANCE) == APPEARANCE) {
            holder.itemView.scaleY = removedScaleY
            holder.itemView.scaleX = removedScaleX
            holder.itemView.alpha = removedAlpha

            holder.itemView.translationZ = 0f
        }

        pendingAdditions.add(holder)

        return true
    }

    protected open fun animateAddImplementation(holder: RecyclerView.ViewHolder) {
        val view = holder.itemView
        val animator = view.animate()

        addAnimations.add(holder)

        animator.alpha(targetAlpha)
            .scaleY(targetScaleY)
            .scaleX(targetScaleX)
            .setDuration(durationFor(APPEARANCE))
            .setInterpolator(DecelerateInterpolator(3f))
            .setListener(
                ItemAnimationListener(
                    holder, animator, addAnimations, resetTranslationZ = false,
                    onStarting = { dispatchAddStarting(it) },
                    onFinished = { dispatchAddFinished(it) }
                )
            )
    }

    final override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
        endAnimation(holder)

        if ((flags and DISAPPEARANCE) == DISAPPEARANCE) {
            holder.itemView.scaleY = targetScaleY
            holder.itemView.scaleX = targetScaleX
            holder.itemView.alpha = targetAlpha

            // keep in front without adding elevation shadow
            holder.itemView.translationZ = -100_000_000f
        }

        pendingRemovals.add(holder)

        return true
    }

    protected open fun animateRemoveImplementation(holder: RecyclerView.ViewHolder) {
        val view = holder.itemView
        val animator = view.animate()

        removeAnimations.add(holder)

        animator.alpha(removedAlpha)
            .scaleY(removedScaleY)
            .scaleX(removedScaleX)
            .setDuration(durationFor(DISAPPEARANCE))
            .setInterpolator(DecelerateInterpolator(3f))
            .setListener(
                ItemAnimationListener(
                    holder, animator, removeAnimations, resetTranslationZ = true,
                    onStarting = { dispatchRemoveStarting(it) },
                    onFinished = { dispatchRemoveFinished(it) }
                )
            )
    }

    private fun durationFor(flag: Int): Long =
        if ((flags and flag) == flag) animation.duration.toLong() else 0

    private fun resetToTarget(holder: RecyclerView.ViewHolder) {
        holder.itemView.alpha = targetAlpha
        holder.itemView.scaleX = targetScaleX
        holder.itemView.scaleY = targetScaleY
    }

    /**
     * The add and remove listeners only differed in which list they cleared,
     * which pair of dispatch calls they made, and whether they reset
     * translationZ, so they are one class taking those three as arguments.
     */
    private inner class ItemAnimationListener(
        private val holder: RecyclerView.ViewHolder,
        private val animator: ViewPropertyAnimator,
        private val tracking: MutableList<RecyclerView.ViewHolder>,
        private val resetTranslationZ: Boolean,
        private val onStarting: (RecyclerView.ViewHolder) -> Unit,
        private val onFinished: (RecyclerView.ViewHolder) -> Unit
    ) : AnimatorListenerAdapter() {

        private fun cleanup() {
            animator.setListener(null)

            resetToTarget(holder)

            if (resetTranslationZ) {
                holder.itemView.translationZ = 0f
            }

            onFinished(holder)
            tracking.remove(holder)

            if (!isRunning()) {
                dispatchAnimationsFinished()
            }
        }

        override fun onAnimationStart(animation: Animator) {
            onStarting(holder)
        }

        override fun onAnimationCancel(animation: Animator) {
            cleanup()
        }

        override fun onAnimationEnd(animation: Animator) {
            cleanup()
        }
    }

    // Properties rather than getters: Java still calls getTargetAlpha() and the
    // one Kotlin subclass overrides them as properties.
    open val targetAlpha: Float
        get() = 1f

    open val targetScaleX: Float
        get() = 1f

    open val targetScaleY: Float
        get() = 1f

    open val removedAlpha: Float
        get() = 0f

    open val removedScaleX: Float
        get() = 0.9f

    open val removedScaleY: Float
        get() = 0.9f

    fun getFlags(): Int = flags

    companion object {
        const val APPEARANCE = 0b100
        const val DISAPPEARANCE = 0b010
        const val CHANGING = 0b001
    }
}
