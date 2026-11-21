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

/**
 * Created by andrea on 13/04/18.
 */
internal enum class BackgroundColor {

    COLOR_PRIMARY(0),
    COLOR_PRIMARY_DARK(1),
    COLOR_ACCENT(2),
    COLOR_WINDOW_BACKGROUND(3),
    COLOR_WINDOW_FOREGROUND(4);

    private var mValue: Int

    constructor(value: Int) {
        mValue = value
    }

    fun getColor(theme: ITheme): Int {
        when (mValue) {
            0 ->
                return theme.colorPrimaryBackground
            1 ->
                return theme.colorPrimaryDark
            2 ->
                return theme.colorAccent
            4 ->
                return theme.colorWindowForeground
            else ->
                return theme.colorWindowBackground
        }
    }

    companion object {

        @JvmStatic
        fun getValue(backgroundColor: BackgroundColor): Int {
            return backgroundColor.mValue
        }

        @JvmStatic
        fun fromValue(value: Int): BackgroundColor? {
            when (value) {
                0 ->
                    return COLOR_PRIMARY
                1 ->
                    return COLOR_PRIMARY_DARK
                2 ->
                    return COLOR_ACCENT
                3 ->
                    return COLOR_WINDOW_BACKGROUND
                4 ->
                    return COLOR_WINDOW_FOREGROUND
                else ->
                    return null
            }
        }
    }
}