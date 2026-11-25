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
import android.content.res.ColorStateList
import com.google.android.material.tabs.TabLayout
import android.util.AttributeSet

import com.oriondev.moneywallet.R

/**
 * Created by andrea on 10/04/18.
 */
class ThemedTabLayout : TabLayout, ThemeEngine.ThemeConsumer {

    companion object {

        private const val UNFOCUSED_ALPHA = 0.5f
    }

    private var mBackgroundColor: BackgroundColor? = null

    constructor(context: Context) : super(context) {
        initialize(context, null, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context, attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize(context, attrs, defStyleAttr)
    }

    private fun initialize(context: Context, attrs: AttributeSet?, defStyleAttr: Int) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ThemedTabLayout, defStyleAttr, 0)
        try {
            mBackgroundColor = BackgroundColor.fromValue(typedArray.getInt(R.styleable.ThemedTabLayout_theme_backgroundColor, 0))
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            typedArray.recycle()
        }
    }

    override fun onApplyTheme(theme: ITheme?) {
        if (mBackgroundColor != null) {
            val background = getBackgroundColor(theme)
            setBackgroundColor(background)
            setIconsColor(theme)
            setTabTextColor(theme)
            setSelectedTabIndicatorColor(theme)
        }
    }

    private fun setIconsColor(theme: ITheme?) {
        val background = getBackgroundColor(theme)
        val color = theme!!.getBestTextColor(background)
        val stateList = ColorStateList(
                arrayOf(
                        intArrayOf(-android.R.attr.state_selected),
                        intArrayOf(android.R.attr.state_selected)
                ),
                intArrayOf(Util.adjustAlpha(color, UNFOCUSED_ALPHA), color)
        )
        for (i in 0 until tabCount) {
            val tab: TabLayout.Tab? = getTabAt(i)
            if (tab != null && tab.icon != null) {
                tab.setIcon(TintHelper.createTintedDrawable(tab.icon, stateList))
            }
        }
    }

    private fun setTabTextColor(theme: ITheme?) {
        val background = getBackgroundColor(theme)
        val color = theme!!.getBestTextColor(background)
        setTabTextColors(Util.adjustAlpha(color, UNFOCUSED_ALPHA), color)
    }

    private fun setSelectedTabIndicatorColor(theme: ITheme?) {
        val background = getBackgroundColor(theme)
        setSelectedTabIndicatorColor(Util.visibleOr(theme!!.colorAccent, background, theme.getBestTextColor(background)))
    }

    private fun getBackgroundColor(theme: ITheme?): Int {
        if (mBackgroundColor != null) {
            return mBackgroundColor!!.getColor(theme!!)
        } else {
            return theme!!.colorPrimaryBackground
        }
    }
}