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

import com.andrognito.patternlockview.PatternLockView
import com.oriondev.moneywallet.R

/**
 * Created by andrea on 20/08/18.
 */
class ThemedPatternLockView : PatternLockView, ThemeEngine.ThemeConsumer {

    private var mBackgroundColor: BackgroundColor? = null

    constructor(context: Context) : super(context) {
        initialize(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context, attrs)
    }

    private fun initialize(context: Context, attrs: AttributeSet?) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ThemedPatternLockView, 0, 0)
        try {
            mBackgroundColor = BackgroundColor.fromValue(typedArray.getInt(R.styleable.ThemedPatternLockView_theme_backgroundColor, 0))
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            typedArray.recycle()
        }
    }

    override fun onApplyTheme(theme: ITheme?) {
        val backgroundColor = getBackgroundColor(theme)
        setCorrectStateColor(Util.visibleOr(theme!!.colorAccent, backgroundColor, theme.getBestColor(backgroundColor)))
        setNormalStateColor(theme.getBestColor(backgroundColor))
        /* TODO: ensure this is visible over background color */
        setWrongStateColor(theme.errorColor)
    }

    private fun getBackgroundColor(theme: ITheme?): Int {
        if (mBackgroundColor != null) {
            if (mBackgroundColor == BackgroundColor.COLOR_PRIMARY) {
                return theme!!.colorPrimaryBackground
            } else {
                return theme!!.colorPrimaryDark
            }
        } else {
            return theme!!.colorWindowBackground
        }
    }
}