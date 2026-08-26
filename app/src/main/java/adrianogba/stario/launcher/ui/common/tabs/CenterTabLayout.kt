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

package adrianogba.stario.launcher.ui.common.tabs

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.viewpager.widget.ViewPager
import com.ogaclejapan.smarttablayout.SmartTabLayout
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.ui.utils.UiUtils

open class CenterTabLayout : SmartTabLayout {
    private var listener: OnLongClickTabListener? = null
    private lateinit var inflater: LayoutInflater
    private var viewPager: ViewPager? = null

    constructor(context: Context) : super(context) {
        setup(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        setup(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) :
            super(context, attrs, defStyle) {
        setup(context)
    }

    private fun setup(context: Context) {
        inflater = UiUtils.unwrapContext(context)!!.layoutInflater

        setCustomTabView { viewGroup, position, adapter ->
            val text = adapter.getPageTitle(position)

            val textView = inflater.inflate(R.layout.tab, viewGroup, false) as TextView
            textView.text = text

            textView.setOnClickListener {
                viewPager?.currentItem = position
            }
            textView.setOnLongClickListener { v ->
                listener?.onLongClick(v, position)

                true
            }

            textView
        }
    }

    override fun setViewPager(viewPager: ViewPager?) {
        this.viewPager = viewPager

        super.setViewPager(viewPager)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false

    fun setOnTabLongClickListener(listener: OnLongClickTabListener?) {
        this.listener = listener
    }

    fun interface OnLongClickTabListener {
        fun onLongClick(tab: View, position: Int)
    }
}
