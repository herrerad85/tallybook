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
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet

import com.google.android.material.tabs.TabLayout
import com.oriondev.moneywallet.R

/**
 * Created by andrea on 14/08/18.
 *
 * A page indicator drawn as a row of dots. It is a TabLayout with the selection line removed
 * and one dot as the icon of each tab, so the tabs are the dots.
 *
 * TabLayout attaches itself to the ViewPager it sits inside with no help from the code that
 * inflates it: its onAttachedToWindow calls getParent(), and when the parent is a ViewPager it
 * calls setupWithViewPager on it with autoRefresh on, so an adapter installed later still
 * populates the dots. Both layouts that use this class declare it as a child of the ViewPager.
 */
class ThemedViewPagerIndicator : TabLayout, ThemeEngine.ThemeConsumer {

    private var mDotDiameter: Int = 0

    private var mSelectedDotColor = Color.TRANSPARENT
    private var mUnselectedDotColor = Color.TRANSPARENT

    constructor(context: Context) : super(context) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize()
    }

    private fun initialize() {
        mDotDiameter = resources.getDimensionPixelSize(R.dimen.view_pie_chart_indicator_radius) * 2
        setSelectedTabIndicator(null)
        background = null
        // GRAVITY_CENTER is what gives the tabs a wrapped width and centers the row instead of
        // stretching each tab to an equal share
        tabGravity = GRAVITY_CENTER
        setUnboundedRipple(false)
        applyDotColors()
    }

    /**
     * Turns the tab TabLayout is about to add into a dot. The page title would otherwise be
     * drawn under the dot, so it is kept only as the accessibility label. Each tab gets its own
     * drawable because TabLayout tints the icon through the icon view's drawable state, and one
     * shared instance would give every dot the state of whichever tab was touched last.
     */
    override fun addTab(tab: Tab, position: Int, setSelected: Boolean) {
        tab.setContentDescription(tab.text)
        tab.setText(null)
        tab.setIcon(newDot())
        super.addTab(tab, position, setSelected)
    }

    private fun newDot(): Drawable {
        val dot = GradientDrawable()
        dot.shape = GradientDrawable.OVAL
        dot.setColor(Color.WHITE)
        dot.setSize(mDotDiameter, mDotDiameter)
        return dot
    }

    fun setSelectedDotColor(color: Int) {
        mSelectedDotColor = color
        applyDotColors()
    }

    fun setUnselectedDotColor(color: Int) {
        mUnselectedDotColor = color
        applyDotColors()
    }

    private fun applyDotColors() {
        tabIconTint = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
                intArrayOf(mSelectedDotColor, mUnselectedDotColor)
        )
    }

    override fun onApplyTheme(theme: ITheme?) {
        val background = theme!!.colorWindowForeground
        setSelectedDotColor(Util.visibleOr(theme.colorPrimary, background, theme.getBestColor(background)))
        setUnselectedDotColor(theme.iconColor)
    }
}
