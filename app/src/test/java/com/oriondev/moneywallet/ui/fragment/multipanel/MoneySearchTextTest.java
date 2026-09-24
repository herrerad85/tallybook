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

package com.oriondev.moneywallet.ui.fragment.multipanel;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The search matches the money column with LIKE, and that column holds a whole number of minor
 * units, so 53,40 is stored as 5340. An amount typed the way people write it has to reach the
 * query as those digits or it never finds anything.
 */
public class MoneySearchTextTest {

    @Test
    public void decimalSeparatorsAreDropped() {
        assertEquals("5340", SearchMultiPanelFragment.moneySearchText("53,40"));
        assertEquals("53", SearchMultiPanelFragment.moneySearchText("53."));
    }

    @Test
    public void currencySymbolsAndSignsAreDropped() {
        assertEquals("5", SearchMultiPanelFragment.moneySearchText("\u20AC 5"));
        assertEquals("647", SearchMultiPanelFragment.moneySearchText("\u20AC -6,47"));
        assertEquals("647", SearchMultiPanelFragment.moneySearchText("+6,47"));
    }

    @Test
    public void leadingZerosAreDropped() {
        assertEquals("50", SearchMultiPanelFragment.moneySearchText("0,50"));
        assertEquals("0", SearchMultiPanelFragment.moneySearchText("0,00"));
    }

    @Test
    public void groupingSeparatorsAreDropped() {
        assertEquals("123456", SearchMultiPanelFragment.moneySearchText("1\u202F234,56"));
        assertEquals("123456", SearchMultiPanelFragment.moneySearchText("1\u00A0234,56"));
        assertEquals("123456", SearchMultiPanelFragment.moneySearchText("1'234.56"));
    }

    @Test
    public void persianDigitsBecomeAsciiDigits() {
        assertEquals("647", SearchMultiPanelFragment.moneySearchText("\u06F6\u066B\u06F4\u06F7"));
    }

    @Test
    public void plainDigitsStayTheSame() {
        assertEquals("5340", SearchMultiPanelFragment.moneySearchText("5340"));
    }

    @Test
    public void aQueryWithoutDigitsStaysTheSame() {
        assertEquals("", SearchMultiPanelFragment.moneySearchText(""));
        assertEquals(".", SearchMultiPanelFragment.moneySearchText("."));
        assertEquals("\u20AC", SearchMultiPanelFragment.moneySearchText("\u20AC"));
    }

    @Test
    public void datesTimesAndReferencesStayTheSame() {
        assertEquals("01-15", SearchMultiPanelFragment.moneySearchText("01-15"));
        assertEquals("2024-01-15", SearchMultiPanelFragment.moneySearchText("2024-01-15"));
        assertEquals("12:30", SearchMultiPanelFragment.moneySearchText("12:30"));
        assertEquals("15/01", SearchMultiPanelFragment.moneySearchText("15/01"));
        assertEquals("123-45", SearchMultiPanelFragment.moneySearchText("123-45"));
    }

    @Test
    public void aQueryWithLettersStaysTheSame() {
        assertEquals("abc 5", SearchMultiPanelFragment.moneySearchText("abc 5"));
        assertEquals("CHF 5", SearchMultiPanelFragment.moneySearchText("CHF 5"));
    }
}
