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

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.core.graphics.ColorUtils

/**
 * Created by andrea on 10/04/18.
 */
internal object Util {

    @JvmStatic
    @ColorInt
    fun adjustAlpha(@ColorInt color: Int, @Suppress("SameParameterValue") factor: Float): Int {
        val alpha = Math.round(Color.alpha(color) * factor)
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }

    @JvmStatic
    @ColorInt
    fun shiftColor(@ColorInt color: Int, @FloatRange(from = 0.0, to = 2.0) by: Float): Int {
        if (by == 1f) return color
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] *= by // value component
        return Color.HSVToColor(hsv)
    }

    @JvmStatic
    @ColorInt
    fun darkenColor(@ColorInt color: Int): Int {
        return shiftColor(color, 0.9f)
    }

    @JvmStatic
    fun isColorLight(@ColorInt color: Int): Boolean {
        if (color == Color.BLACK) {
            return false
        } else if (color == Color.WHITE || color == Color.TRANSPARENT) {
            return true
        }
        return (1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255) < 0.4
    }

    @JvmStatic
    fun isColorLight(@ColorInt color: Int, @ColorInt bgColor: Int): Boolean {
        if (Color.alpha(color) < 128) {
            return isColorLight(bgColor)
        }
        return isColorLight(color)
    }

    // A floor for seeing anything at all, under both numbers the accessibility guidelines give,
    // 3:1 for a control and 4.5:1 for text. It has to be: several pairs the default theme itself
    // draws sit between 2 and 3, so a higher floor would repaint the stock app.
    private const val MIN_CONTRAST = 2.0

    private fun isColorVisible(@ColorInt color: Int, @ColorInt bgColor: Int): Boolean {
        return ColorUtils.calculateContrast(color, bgColor) >= MIN_CONTRAST
    }

    @JvmStatic
    fun visibleOr(@ColorInt color: Int, @ColorInt background: Int, @ColorInt fallback: Int): Int {
        return if (isColorVisible(color, background)) color else fallback
    }

    @JvmStatic
    fun accentAsIcon(theme: ITheme): Int {
        return if (holdsUpAnywhere(theme, theme.colorAccent)) theme.colorAccent
                else theme.getBestColor(theme.colorWindowForeground)
    }

    @JvmStatic
    fun accentAsText(theme: ITheme): Int {
        return if (holdsUpAnywhere(theme, theme.colorAccent)) theme.colorAccent
                else theme.getBestTextColor(theme.colorWindowForeground)
    }

    // The surfaces a chosen color has to survive when nothing says which one it lands on. A
    // dialog is a card, so this covers a tinted control in a dialog as well as an amount on a row.
    @JvmStatic
    fun holdsUpAnywhere(theme: ITheme, @ColorInt color: Int): Boolean {
        return isColorVisible(color, theme.colorWindowForeground)
                && isColorVisible(color, theme.colorCardBackground)
    }

    @JvmStatic
    fun stripAlpha(@ColorInt color: Int): Int {
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color))
    }
}