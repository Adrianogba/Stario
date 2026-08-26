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

package adrianogba.stario.launcher.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max

class LimitingTranslationFrameLayout : FrameLayout {
    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f

    // Named apart from View.getParent(), which a property called parent would
    // collide with.
    private var attachedParent: View? = null

    private val layoutChangeListener =
        OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            startX = view.paddingLeft.toFloat()
            startY = view.paddingTop.toFloat()
            endX = (view.width - view.paddingRight).toFloat()
            endY = (view.height - view.paddingBottom).toFloat()
        }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val viewParent = parent

        if (viewParent != null) {
            val parentView = viewParent as View
            attachedParent = parentView

            parentView.addOnLayoutChangeListener(layoutChangeListener)
            layoutChangeListener.onLayoutChange(parentView, 0, 0, 0, 0, 0, 0, 0, 0)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        attachedParent?.removeOnLayoutChangeListener(layoutChangeListener)
    }

    override fun setTranslationX(translationX: Float) {
        var value = translationX

        if (value + left < startX) {
            value = startX - left
        } else if (value + right > endX) {
            value = endX - right
        }

        super.setTranslationX(value)
    }

    override fun setTranslationY(translationY: Float) {
        var value = translationY

        val scroll = getParentScroll()
        val range = getParentScrollRange()

        if (value + top + scroll < startY) {
            value = startY - top - scroll
        } else if (value + bottom - (range - scroll) > endY) {
            value = endY - bottom + (range - scroll)
        }

        super.setTranslationY(value)
    }

    private fun getParentScroll(): Int {
        val parent = parent

        if (parent is RecyclerView) {
            return parent.computeVerticalScrollOffset()
        } else if (parent is ScrollView || parent is NestedScrollView) {
            return (parent as View).scrollY
        }

        return 0
    }

    private fun getParentScrollRange(): Int {
        val parent = parent

        if (parent is RecyclerView) {
            return parent.computeVerticalScrollRange() - parent.computeVerticalScrollExtent()
        } else if (parent is ScrollView || parent is NestedScrollView) {
            val scrollView = parent as ViewGroup

            if (scrollView.childCount > 0) {
                val child = scrollView.getChildAt(0)

                return max(
                    0,
                    child.height - (scrollView.height -
                            scrollView.paddingBottom - scrollView.paddingTop)
                )
            }
        }

        return 0
    }
}
