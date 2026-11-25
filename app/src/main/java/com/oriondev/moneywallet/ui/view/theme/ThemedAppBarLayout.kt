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
import android.view.WindowInsets

import androidx.core.view.WindowInsetsCompat

import com.google.android.material.appbar.AppBarLayout

import com.oriondev.moneywallet.R

/**
 * Created by andrea on 25/07/18.
 */
class ThemedAppBarLayout : AppBarLayout, ThemeEngine.ThemeConsumer {

    private var mBackgroundColor: BackgroundColor? = null

    private var mInsetSides: Boolean = false

    private var mBasePaddingLeft: Int = 0

    private var mBasePaddingRight: Int = 0

    constructor(context: Context) : super(context) {
        initialize(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context, attrs)
    }

    private fun initialize(context: Context, attrs: AttributeSet?) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ThemedAppBarLayout, 0, 0)
        var insetTop = true
        var insetSides = true
        try {
            mBackgroundColor = BackgroundColor.fromValue(typedArray.getInt(R.styleable.ThemedAppBarLayout_theme_backgroundColor, 0))
            insetTop = typedArray.getBoolean(R.styleable.ThemedAppBarLayout_systemBarInsetTop, true)
            insetSides = typedArray.getBoolean(R.styleable.ThemedAppBarLayout_systemBarInsetSides, true)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            typedArray.recycle()
        }
        mInsetSides = insetSides
        mBasePaddingLeft = paddingLeft
        mBasePaddingRight = paddingRight
        if (insetTop) {
            // AppBarLayout does the status bar itself when this is set, it grows by the inset,
            // offsets its children down by it, and subtracts it from the range it will scroll. That
            // last part is why this is not padding of ours. A bar with scroll flags carries its own
            // padding away as it collapses, which slid the toolbar under the status bar on the
            // tabbed screens; the inset the library keeps is pinned and cannot scroll off.
            fitsSystemWindows = true
        }
    }

    /**
     * The sides are still ours, because the library only handles the top. Done around the dispatch
     * instead of through a listener, the library installs its own listener in its constructor, and
     * a second one would replace it and take the status bar handling above with it. The background
     * is not shrunk by padding, so the bar still paints its color out to the edge of the display.
     */
    override fun dispatchApplyWindowInsets(insets: WindowInsets?): WindowInsets? {
        if (mInsetSides) {
            val bars = WindowInsetsCompat.toWindowInsetsCompat(insets!!).getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            setPadding(mBasePaddingLeft + bars.left, paddingTop,
                    mBasePaddingRight + bars.right, paddingBottom)
        }
        return super.dispatchApplyWindowInsets(insets)
    }

    override fun onApplyTheme(theme: ITheme?) {
        if (mBackgroundColor != null) {
            val background = getBackgroundColor(theme)
            setBackgroundColor(background)
        }
    }

    private fun getBackgroundColor(theme: ITheme?): Int {
        if (mBackgroundColor != null) {
            if (mBackgroundColor == BackgroundColor.COLOR_PRIMARY) {
                return theme!!.colorPrimaryBackground
            } else {
                return theme!!.colorPrimaryDark
            }
        } else {
            return theme!!.colorPrimaryBackground
        }
    }

    enum class BackgroundColor {
        COLOR_PRIMARY(0),
        COLOR_PRIMARY_DARK(1);

        private var mValue: Int

        constructor(value: Int) {
            mValue = value
        }

        companion object {

            @JvmStatic
            internal fun fromValue(value: Int): BackgroundColor? {
                when (value) {
                    0 ->
                        return COLOR_PRIMARY
                    1 ->
                        return COLOR_PRIMARY_DARK
                    else ->
                        return null
                }
            }
        }
    }
}