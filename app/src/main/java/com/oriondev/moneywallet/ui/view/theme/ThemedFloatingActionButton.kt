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
import android.graphics.drawable.Drawable
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.util.AttributeSet

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.utils.SystemBars

/**
 * Created by andrea on 11/04/18.
 */
class ThemedFloatingActionButton : FloatingActionButton, ThemeEngine.ThemeConsumer {

    private var mInsetBottom: Boolean = true

    private var mInsetApplied: Boolean = false

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        readInsetAttribute(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        readInsetAttribute(context, attrs)
    }

    private fun readInsetAttribute(context: Context, attrs: AttributeSet?) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ThemedFloatingActionButton, 0, 0)
        try {
            mInsetBottom = typedArray.getBoolean(R.styleable.ThemedFloatingActionButton_systemBarInsetBottom, true)
        } finally {
            typedArray.recycle()
        }
    }

    /**
     * Not the constructor: the parent sets the layout params during inflation, after the view is
     * built, so there is no margin to read yet at that point. Once only, because a second pass
     * would read a margin that already carries the inset and treat it as the baseline.
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (mInsetBottom && !mInsetApplied) {
            mInsetApplied = true
            SystemBars.marginBottomAndSides(this)
        }
    }

    override fun onApplyTheme(theme: ITheme?) {
        val isDark = theme!!.isDark
        val colorAccent = theme.colorAccent
        val colorPressed = Util.shiftColor(colorAccent, if (isDark) 0.9f else 1.1f)
        val colorActivated = Util.shiftColor(colorAccent, if (isDark) 1.1f else 0.9f)
        val colorRipple = theme.colorRipple
        val stateList = ColorStateList(
                arrayOf(
                        intArrayOf(-android.R.attr.state_pressed),
                        intArrayOf(android.R.attr.state_pressed)
                ),
                intArrayOf(colorAccent, colorPressed)
        )
        setRippleColor(colorRipple)
        setBackgroundTintList(stateList)
        setIconColor(theme)
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        setIconColor(ThemeEngine.getTheme())
    }

    private fun setIconColor(theme: ITheme) {
        val drawable = getDrawable()
        if (drawable != null) {
            val color = theme.getBestColor(theme.colorAccent)
            super.setImageDrawable(TintHelper.createTintedDrawable(drawable, color))
        }
    }
}