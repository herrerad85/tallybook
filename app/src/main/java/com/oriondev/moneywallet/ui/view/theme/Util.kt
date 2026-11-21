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

package com.oriondev.moneywallet.ui.view.theme;

import android.graphics.Color;
import androidx.annotation.ColorInt;
import androidx.annotation.FloatRange;
import androidx.core.graphics.ColorUtils;

/**
 * Created by andrea on 10/04/18.
 */
/*package-local*/ class Util {

    @ColorInt
    /*package-local*/ static int adjustAlpha(@ColorInt int color, @SuppressWarnings("SameParameterValue") float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

    @ColorInt
    /*package-local*/ static int shiftColor(@ColorInt int color, @FloatRange(from = 0.0f, to = 2.0f) float by) {
        if (by == 1f) return color;
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= by; // value component
        return Color.HSVToColor(hsv);
    }

    @ColorInt
    /*package-local*/ static int darkenColor(@ColorInt int color) {
        return shiftColor(color, 0.9f);
    }

    /*package-local*/ static boolean isColorLight(@ColorInt int color) {
        if (color == Color.BLACK) {
            return false;
        } else if (color == Color.WHITE || color == Color.TRANSPARENT) {
            return true;
        }
        return (1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255) < 0.4;
    }

    /*package-local*/ static boolean isColorLight(@ColorInt int color, @ColorInt int bgColor) {
        if (Color.alpha(color) < 128) {
            return isColorLight(bgColor);
        }
        return isColorLight(color);
    }

    // A floor for seeing anything at all, under both numbers the accessibility guidelines give,
    // 3:1 for a control and 4.5:1 for text. It has to be: several pairs the default theme itself
    // draws sit between 2 and 3, so a higher floor would repaint the stock app.
    private static final double MIN_CONTRAST = 2d;

    private static boolean isColorVisible(@ColorInt int color, @ColorInt int bgColor) {
        return ColorUtils.calculateContrast(color, bgColor) >= MIN_CONTRAST;
    }

    /*package-local*/ static int visibleOr(@ColorInt int color, @ColorInt int background, @ColorInt int fallback) {
        return isColorVisible(color, background) ? color : fallback;
    }

    /*package-local*/ static int accentAsIcon(ITheme theme) {
        return holdsUpAnywhere(theme, theme.getColorAccent()) ? theme.getColorAccent()
                : theme.getBestColor(theme.getColorWindowForeground());
    }

    /*package-local*/ static int accentAsText(ITheme theme) {
        return holdsUpAnywhere(theme, theme.getColorAccent()) ? theme.getColorAccent()
                : theme.getBestTextColor(theme.getColorWindowForeground());
    }

    // The surfaces a chosen color has to survive when nothing says which one it lands on. A
    // dialog is a card, so this covers a tinted control in a dialog as well as an amount on a row.
    /*package-local*/ static boolean holdsUpAnywhere(ITheme theme, @ColorInt int color) {
        return isColorVisible(color, theme.getColorWindowForeground())
                && isColorVisible(color, theme.getColorCardBackground());
    }

    /*package-local*/ static int stripAlpha(@ColorInt int color) {
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
    }
}