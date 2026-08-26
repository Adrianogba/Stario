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

package adrianogba.stario.launcher.ui.recyclers.autogrid

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.ui.recyclers.overscroll.OverScrollRecyclerView

class AutoGridRecyclerView : OverScrollRecyclerView {
    private var manager: LayoutManager? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) :
            super(context, attrs, defStyle)

    override fun setLayoutManager(layout: LayoutManager?) {
        super.setLayoutManager(layout)

        this.manager = layout
    }

    override fun setAdapter(adapter: Adapter<*>?) {
        super.setAdapter(adapter)

        (manager as? AutoGridLayoutManager)?.setAdapter(getAdapter())
    }
}
