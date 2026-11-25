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
import android.util.AttributeSet

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.view.calendar.MonthView

/**
 * Created by andrea on 13/04/18.
 */
class ThemedMonthView : MonthView, ThemeEngine.ThemeConsumer {

    private var mBackgroundColor: BackgroundColor? = null

    constructor(context: Context) : super(context) {
        initialize(context, null, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context, attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        initialize(context, attrs, defStyle)
    }

    private fun initialize(context: Context, attrs: AttributeSet?, defStyleAttr: Int) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ThemedMonthView, defStyleAttr, 0)
        try {
            mBackgroundColor = BackgroundColor.fromValue(typedArray.getInt(R.styleable.ThemedMonthView_theme_backgroundColor, 0))
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            typedArray.recycle()
        }
    }

    override fun onApplyTheme(theme: ITheme?) {
        if (mBackgroundColor != null) {
            val background = mBackgroundColor!!.getColor(theme!!)
            val color = theme.getBestTextColor(background)
            setBackgroundColor(background)
            setDefaultColor(color)
            setColorSelected(Util.visibleOr(theme.colorAccent, background, color))
            setColorBeforeSelection(color)
            EdgeGlowUtil.setEdgeGlowColor(this, theme.colorPrimary)
        }
    }
}