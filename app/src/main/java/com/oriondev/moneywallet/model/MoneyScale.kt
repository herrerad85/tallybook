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

package com.oriondev.moneywallet.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Canonical conversions between a human readable decimal amount and the bare
 * {@code long} minor units value the app stores for money. The scale of a
 * currency is 10^decimals, with decimals in the range 0..3.
 *
 * Every conversion shifts the decimal point with {@link BigDecimal} so the
 * arithmetic is exact. All of them but one truncate toward zero when narrowing
 * back to a {@code long}, which is the rounding the individual call sites
 * relied on before this was consolidated; {@link #toMinorUnitsRounded} is the
 * exception and says why. The class is intentionally free of Android
 * dependencies so it can be unit tested on the JVM.
 */
public final class MoneyScale {

    private MoneyScale() {
    }

    /**
     * Convert a human amount in major units (for example 64.99) into the stored
     * minor units value (6499 for a two decimal currency). Any fraction beyond
     * the currency scale is truncated toward zero. A null amount maps to zero.
     */
    public static long toMinorUnits(BigDecimal amount, int decimals) {
        if (amount == null) {
            return 0L;
        }
        return amount.movePointRight(decimals).longValue();
    }

    /**
     * The same conversion for an amount that came from somewhere the currency scale was not
     * known: the fraction the currency cannot hold is rounded to the nearest minor unit rather
     * than dropped. Half goes to the even neighbour, the same rounding the amount keypad uses on
     * an answer it computed, so a value the app works out and the same value read from a file
     * land on the same minor unit. An amount typed by hand still drops its fraction, which is a
     * separate decision this does not touch.
     *
     * Dropping the fraction instead loses up to a whole minor unit rather than half of one, and
     * always toward zero, so every row of a file comes out smaller than it was written.
     *
     * A null amount maps to zero. Unlike {@link #toMinorUnits(BigDecimal, int)} this refuses an
     * amount too large to hold, with an {@link ArithmeticException}, rather than keeping the low
     * bits of it: rounding can carry a value over the edge that truncating left inside.
     */
    public static long toMinorUnitsRounded(BigDecimal amount, int decimals) {
        if (amount == null) {
            return 0L;
        }
        return amount.setScale(decimals, RoundingMode.HALF_EVEN).movePointRight(decimals).longValueExact();
    }

    /**
     * Convert a stored minor units value back into a human amount in major
     * units. This is the exact inverse of {@link #toMinorUnits(BigDecimal, int)}
     * for any value that fits the currency scale.
     */
    public static BigDecimal toHumanAmount(long minorUnits, int decimals) {
        return BigDecimal.valueOf(minorUnits).movePointLeft(decimals);
    }

    /**
     * Rescale a stored minor units value by shifting its decimal point right by
     * {@code decimalOffset} places; a negative offset shifts left. Used when a
     * currency changes its number of decimals. Truncates toward zero.
     */
    public static long rescale(long minorUnits, int decimalOffset) {
        return BigDecimal.valueOf(minorUnits).movePointRight(decimalOffset).longValue();
    }

    /**
     * Apply an exchange rate to a stored minor units amount and re-scale the
     * result from the source currency scale to the target currency scale. The
     * rate is applied exactly once and the result is truncated toward zero.
     *
     * The rate is taken through {@link BigDecimal#valueOf(double)} so it is read
     * from its canonical decimal form rather than the raw binary expansion of
     * the double, which is what {@code new BigDecimal(double)} would capture.
     */
    public static long convert(long minorUnits, int fromDecimals, int toDecimals, double rate) {
        return BigDecimal.valueOf(minorUnits)
                .movePointLeft(fromDecimals)
                .multiply(BigDecimal.valueOf(rate))
                .movePointRight(toDecimals)
                .longValue();
    }

    /**
     * Recover the rate {@link #convert(long, int, int, double)} was called with from the pair of
     * stored amounts it produced. Since convert shifts the decimal point between the two currency
     * scales as well as applying the rate, the bare ratio of the stored values is
     * {@code rate x 10^(toDecimals - fromDecimals)} rather than the rate, and feeding that ratio
     * back into convert would apply the shift a second time.
     *
     * Returns zero when the source amount is zero: the ratio is undefined there, and the infinite
     * or NaN double it would otherwise produce cannot be read by
     * {@link BigDecimal#valueOf(double)}. Zero is the value the currency pickers already treat as
     * no rate known.
     */
    public static double deriveRate(long fromMinorUnits, long toMinorUnits, int fromDecimals, int toDecimals) {
        if (fromMinorUnits == 0) {
            return 0D;
        }
        return (double) toMinorUnits / fromMinorUnits * Math.pow(10, fromDecimals - toDecimals);
    }
}
