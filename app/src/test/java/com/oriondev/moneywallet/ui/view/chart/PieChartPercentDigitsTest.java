/*
 * Copyright (c) 2026.
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

package com.oriondev.moneywallet.ui.view.chart;

import android.content.Context;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The percentage the period report pie chart writes on each of its wedges, in the digits and
 * the percent sign of the language the app is in. The view used to build that label by
 * concatenating an int with a literal percent sign and no locale, so a Persian screen read
 * 85% under amounts written in Persian digits. A half percent rounds up the way Math.round
 * did. A wedge of a period whose total is zero, which makes the sweep angle NaN, reads 0%.
 */
@RunWith(RobolectricTestRunner.class)
public class PieChartPercentDigitsTest {

    private static final int PERSIAN_ZERO = 0x06F0;
    private static final int PERSIAN_NINE = 0x06F9;

    private static final float EIGHTY_FIVE_PERCENT_SWEEP = 306f;
    private static final float SEVENTEEN_AND_A_HALF_PERCENT_SWEEP = 63f;

    private Context themed() {
        return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),
                R.style.MoneyWalletAppTheme);
    }

    /**
     * Fails when the locale under test is not the Persian one. The digit assertions below would
     * fail on their own and say why, but a label read in the wrong language tells nothing about
     * the formatter this file is here to check.
     */
    private void requirePersianLocale() {
        assertEquals("the qualifier did not reach the default locale, so nothing below is a check",
                "fa", Locale.getDefault().getLanguage());
    }

    private String wedgeLabel(float sweepAngle) {
        return new PieChart(themed()).percentLabel(sweepAngle);
    }

    private static boolean carries(String text, int from, int to) {
        for (int at = 0; at < text.length(); at++) {
            char character = text.charAt(at);
            if (character >= from && character <= to) {
                return true;
            }
        }
        return false;
    }

    @Test
    @Config(qualifiers = "fa-rIR")
    public void theWedgePercentageIsWrittenInPersianDigits() {
        requirePersianLocale();
        String label = wedgeLabel(EIGHTY_FIVE_PERCENT_SWEEP);
        assertTrue("the pie wedge percentage is empty, so it carries no digits to check",
                label.length() > 0);
        assertTrue("the pie wedge percentage drew " + label
                        + ", which carries no digit from the Persian set",
                carries(label, PERSIAN_ZERO, PERSIAN_NINE));
        assertTrue("the pie wedge percentage drew " + label
                        + ", which carries a Latin digit", !carries(label, '0', '9'));
        assertTrue("the pie wedge percentage drew " + label
                        + ", which carries the ASCII percent sign", label.indexOf('%') < 0);
    }

    @Test
    @Config(qualifiers = "en-rUS")
    public void theWedgePercentageStaysLatinInEnglish() {
        assertEquals("the pie wedge percentage is not the Latin one in English",
                "85%", wedgeLabel(EIGHTY_FIVE_PERCENT_SWEEP));
    }

    @Test
    @Config(qualifiers = "en-rUS")
    public void aHalfPercentRoundsUpAsBefore() {
        assertEquals("a 17.5 percent wedge no longer rounds up to 18%",
                "18%", wedgeLabel(SEVENTEEN_AND_A_HALF_PERCENT_SWEEP));
    }

    @Test
    @Config(qualifiers = "en-rUS")
    public void aZeroTotalWritesZeroPercent() {
        assertEquals("a wedge of a period whose total is zero no longer reads 0%",
                "0%", wedgeLabel(Float.NaN));
    }

}
