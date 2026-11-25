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
import android.graphics.drawable.Drawable
import androidx.annotation.MenuRes
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.appcompat.widget.Toolbar
import android.util.AttributeSet

import com.oriondev.moneywallet.R

/**
 * Created by andrea on 10/04/18.
 */
class ThemedToolbar : Toolbar, ThemeEngine.ThemeConsumer {

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
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ThemedToolbar, defStyleAttr, 0)
        try {
            mBackgroundColor = BackgroundColor.fromValue(typedArray.getInt(R.styleable.ThemedToolbar_theme_backgroundColor, 0))
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
            setTitleTextColor(theme)
            setOverflowButtonColor(theme)
            setNavigationIconColor(theme)
            setMenuIconColor(theme)
        }
    }

    override fun setNavigationIcon(icon: Drawable?) {
        super.setNavigationIcon(icon)
        setNavigationIconColor(ThemeEngine.getTheme())
    }

    override fun inflateMenu(@MenuRes resId: Int) {
        super.inflateMenu(resId)
        setMenuIconColor(ThemeEngine.getTheme())
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

    private fun setTitleTextColor(theme: ITheme?) {
        val background = getBackgroundColor(theme)
        val color = theme!!.getBestTextColor(background)
        setTitleTextColor(color)
        setSubtitleTextColor(color)
    }

    private fun setOverflowButtonColor(theme: ITheme?) {
        val drawable = overflowIcon
        if (drawable != null) {
            val background = getBackgroundColor(theme)
            val color = theme!!.getBestTextColor(background)
            super.setOverflowIcon(TintHelper.createTintedDrawable(drawable, color))
        }
    }

    private fun setNavigationIconColor(theme: ITheme?) {
        val drawable = navigationIcon
        if (drawable != null) {
            val background = getBackgroundColor(theme)
            val color = theme!!.getBestTextColor(background)
            if (drawable is DrawerArrowDrawable) {
                drawable.setColor(color)
                super.setNavigationIcon(drawable)
            } else {
                super.setNavigationIcon(TintHelper.createTintedDrawable(drawable, color))
            }
        }
    }

    private fun setMenuIconColor(theme: ITheme?) {
        val menu = getMenu()
        if (menu != null) {
            val background = getBackgroundColor(theme)
            val color = theme!!.getBestTextColor(background)
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                val drawable = item.icon
                if (drawable != null) {
                    item.setIcon(TintHelper.createTintedDrawable(drawable, color))
                }
            }
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