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
import androidx.appcompat.widget.AppCompatTextView
import android.util.AttributeSet

import com.oriondev.moneywallet.R

/**
 * Created by andrea on 10/04/18.
 */
class ThemedTextView : AppCompatTextView, ThemeEngine.ThemeConsumer {

    private var mTextColor: TextColor? = null

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
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ThemedTextView, defStyleAttr, 0)
        try {
            mTextColor = TextColor.fromValue(typedArray.getInt(R.styleable.ThemedTextView_theme_textColor, 1))
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            typedArray.recycle()
        }
    }

    override fun onApplyTheme(theme: ITheme?) {
        if (mTextColor != null) {
            when (mTextColor) {
                TextColor.PRIMARY ->
                    setTextColor(theme!!.textColorPrimary)
                TextColor.SECONDARY ->
                    setTextColor(theme!!.textColorSecondary)
                TextColor.PRIMARY_INVERSE ->
                    setTextColor(theme!!.textColorPrimaryInverse)
                TextColor.SECONDARY_INVERSE ->
                    setTextColor(theme!!.textColorSecondaryInverse)
                TextColor.COLOR_PRIMARY ->
                    setTextColor(theme!!.colorPrimary)
                TextColor.COLOR_ACCENT ->
                    setTextColor(Util.accentAsText(theme!!))
                TextColor.OVER_COLOR_PRIMARY ->
                    setTextColor(theme!!.getBestTextColor(theme.colorPrimaryBackground))
                TextColor.OVER_COLOR_PRIMARY_DARK ->
                    setTextColor(theme!!.getBestTextColor(theme.colorPrimaryDark))
                TextColor.OVER_COLOR_ACCENT ->
                    setTextColor(theme!!.getBestTextColor(theme.colorAccent))
                else ->
                    System.out.println("{WARN} UNKNOWN")
            }
        } else {
            System.out.println("{WARN} IS NULL WTF?")
        }
    }

    enum class TextColor {
        PRIMARY(0),
        SECONDARY(1),
        PRIMARY_INVERSE(2),
        SECONDARY_INVERSE(3),
        COLOR_PRIMARY(4),
        COLOR_ACCENT(5),
        OVER_COLOR_PRIMARY(6),
        OVER_COLOR_PRIMARY_DARK(7),
        OVER_COLOR_ACCENT(8);

        private var mValue: Int

        constructor(value: Int) {
            mValue = value
        }

        companion object {

            @JvmStatic
            internal fun fromValue(value: Int): TextColor? {
                when (value) {
                    0 ->
                        return PRIMARY
                    1 ->
                        return SECONDARY
                    2 ->
                        return PRIMARY_INVERSE
                    3 ->
                        return SECONDARY_INVERSE
                    4 ->
                        return COLOR_PRIMARY
                    5 ->
                        return COLOR_ACCENT
                    6 ->
                        return OVER_COLOR_PRIMARY
                    7 ->
                        return OVER_COLOR_PRIMARY_DARK
                    8 ->
                        return OVER_COLOR_ACCENT
                    else ->
                        return null
                }
            }
        }
    }
}