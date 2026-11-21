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
 * This interface implements all the colors of a theme.
 */
interface ITheme {

    val colorPrimary: Int

    val colorPrimaryDark: Int

    /**
     * What an area with the primary color as its background is painted, black in deep dark.
     * Text, icons and accents drawn in the primary color keep getColorPrimary.
     */
    val colorPrimaryBackground: Int

    val colorAccent: Int

    val mode: ThemeEngine.Mode

    val isDark: Boolean

    val textColorPrimary: Int

    val textColorSecondary: Int

    val textColorPrimaryInverse: Int

    val textColorSecondaryInverse: Int

    val colorCardBackground: Int

    val colorWindowForeground: Int

    val colorWindowBackground: Int

    val colorRipple: Int

    val iconColor: Int

    val hintTextColor: Int

    val errorColor: Int

    val drawerBackgroundColor: Int

    val drawerIconColor: Int

    val drawerSelectedIconColor: Int

    val drawerTextColor: Int

    val drawerSelectedTextColor: Int

    val drawerSelectedItemColor: Int

    fun getBestColor(background: Int): Int

    fun getBestTextColor(background: Int): Int

    fun getBestHintColor(background: Int): Int

    fun getBestIconColor(background: Int): Int

    fun getVisibleColor(color: Int, fallback: Int): Int
}