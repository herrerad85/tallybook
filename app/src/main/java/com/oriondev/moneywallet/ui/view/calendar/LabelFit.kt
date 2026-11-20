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

package com.oriondev.moneywallet.ui.view.calendar;

import android.text.Layout;
import android.text.TextPaint;

/**
 * One text size for a whole strip of fixed width cells, small enough that every label in it
 * measures inside a cell.
 *
 * Both calendar strips shorten a localized date name to fit a cell they cannot resize. Cutting
 * to a character count was how they used to do it, which assumes a language distinguishes its
 * names within that count, so overflow is sized away here instead. The size is derived once for
 * the strip rather than per cell, because a size per cell would let neighbouring labels render
 * at different sizes.
 */
final class LabelFit {

    private LabelFit() {
    }

    /**
     * @param paint     the label's paint, carrying the text size to start from
     * @param labels    every label the strip can render in the current locale
     * @param available the width a label has inside one cell, in pixels
     * @return the starting size unchanged when the labels already fit at it, and also when
     *         {@code available} is not positive, which is the case before the cell has a width
     */
    static float sizeToFit(TextPaint paint, String[] labels, int available) {
        float size = paint.getTextSize();
        if (available <= 0) {
            return size;
        }
        // Step down and measure again rather than deriving a ratio from the overflow and trusting
        // it. Every label is measured rather than only the one that came out widest at the
        // starting size, which costs nothing at these counts and removes the question of whether
        // that ranking holds at every size.
        TextPaint measured = new TextPaint(paint);
        while (size > 1f && !fits(measured, labels, available)) {
            size -= 0.5f;
            measured.setTextSize(size);
        }
        return size;
    }

    private static boolean fits(TextPaint paint, String[] labels, int available) {
        for (String label : labels) {
            // Rounded up, which is what a TextView does with the width it asks for.
            if (Math.ceil(Layout.getDesiredWidth(label, paint)) > available) {
                return false;
            }
        }
        return true;
    }
}
