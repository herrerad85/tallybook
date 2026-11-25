/*
 * Copyright (c) 2018.
 *
 * This file is part of MoneyWallet.
 *
 * MoneyWallet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MoneyWallet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MoneyWallet.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.ui.view.theme

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.util.AttributeSet

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.utils.SystemBars

/**
 * Created by andrea on 20/08/18.
 */
class ThemedRecyclerView : RecyclerView, ThemeEngine.ThemeConsumer {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        initialize(context, attrs)
    }

    private fun initialize(context: Context, attrs: AttributeSet?) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ThemedRecyclerView, 0, 0)
        val insetSides: Boolean
        val insetBottom: Boolean
        try {
            insetSides = typedArray.getBoolean(R.styleable.ThemedRecyclerView_systemBarInsetSides, true)
            insetBottom = typedArray.getBoolean(R.styleable.ThemedRecyclerView_systemBarInsetBottom, false)
        } finally {
            typedArray.recycle()
        }
        if (insetBottom) {
            SystemBars.pad(this, false, insetSides, true)
        }
    }

    override fun onApplyTheme(theme: ITheme?) {
        EdgeGlowUtil.setEdgeGlowColor(this, theme!!.colorPrimary)
    }

    companion object {

        @JvmStatic
        fun applyTheme(recyclerView: RecyclerView, theme: ITheme) {
            EdgeGlowUtil.setEdgeGlowColor(recyclerView, theme.colorPrimary)
        }
    }
}