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

package com.oriondev.moneywallet.ui.adapter.recycler;

import com.oriondev.moneywallet.model.Money;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A stretch that only spent counts no income at all, and an amount holding no currency renders as
 * the placeholder for a value nobody knows. On a line labelled "Incomes" that reads as unknown
 * when the answer is a plain zero, so the screen substitutes one.
 *
 * What the substitution must not do is invent a currency. The zero is taken from the currencies
 * the difference was counted in, and a figure that has rows of its own is handed back untouched,
 * so a header counting two currencies never grows a zero for the one a figure has no rows in.
 */
public class HeaderZeroFigureTest {

    private static final String USD = "USD";
    private static final String EUR = "EUR";

    /**
     * The contents are what this asserts on, not the identity. Handing the caller its own object
     * back would satisfy an identity check while quietly adding the zero to it, and returning a
     * copy that left the contents alone would fail one while being perfectly correct.
     */
    @Test
    public void aFigureWithRowsOfItsOwnIsHandedBackUnchanged() {
        Money income = new Money(USD, 4500);
        Money counted = new Money(USD, 4500);
        Money result = TransactionCursorAdapter.orZero(income, counted);
        assertEquals(income.getCurrencyMoneys(), result.getCurrencyMoneys());
        assertEquals(4500, result.getMoney(USD));
    }

    /**
     * The currency has to be asserted by name. Money.getMoney answers zero for a currency it has
     * never heard of, so asking it what it holds for one cannot tell a zero apart from an absence
     * and would pass on a figure carrying some other currency entirely.
     */
    @Test
    public void anEmptyFigureBecomesAZeroInTheCurrencyThatWasCounted() {
        Money counted = new Money(USD, -4500);
        Money zero = TransactionCursorAdapter.orZero(new Money(), counted);
        assertEquals(1, zero.getNumberOfCurrencies());
        assertTrue(zero.getCurrencies().contains(USD));
        assertEquals(0, zero.getMoney(USD));
    }

    @Test
    public void anEmptyFigureTakesEveryCurrencyTheDifferenceWasCountedIn() {
        Money counted = new Money(USD, -4500);
        counted.addMoney(EUR, -700);
        Money zero = TransactionCursorAdapter.orZero(new Money(), counted);
        assertEquals(2, zero.getNumberOfCurrencies());
        assertTrue(zero.getCurrencies().contains(USD));
        assertTrue(zero.getCurrencies().contains(EUR));
        assertEquals(0, zero.getMoney(USD));
        assertEquals(0, zero.getMoney(EUR));
    }

    /**
     * The case the zero is deliberately not extended to. A figure holding one of the two
     * currencies keeps only that one, so the line stays as short as the rows make it.
     */
    @Test
    public void aFigureHoldingOneOfTwoCurrenciesDoesNotGainTheOther() {
        Money income = new Money(EUR, 70000);
        Money counted = new Money(USD, -4500);
        counted.addMoney(EUR, 70000);
        Money result = TransactionCursorAdapter.orZero(income, counted);
        assertEquals(1, result.getNumberOfCurrencies());
        assertTrue(result.getCurrencies().contains(EUR));
    }

    @Test
    public void nothingCountedAtAllStaysUnknown() {
        Money zero = TransactionCursorAdapter.orZero(new Money(), new Money());
        assertEquals(0, zero.getNumberOfCurrencies());
    }

    /**
     * The Transfers figure is hidden when the period moved nothing, and a period whose transfers
     * cancelled moved nothing just as much as one that had none. Asking whether the figure holds
     * any currency would answer no only to the second, and the Total wallet, where both sides of
     * a transfer land and cancel, is the first.
     */
    @Test
    public void aFigureWhoseRowsCancelledCameToNothing() {
        Money transfer = new Money(USD, 40000);
        transfer.addMoney(USD, -40000);
        assertEquals(1, transfer.getNumberOfCurrencies());
        assertTrue(TransactionCursorAdapter.isZero(transfer));
    }

    @Test
    public void aFigureWithNoRowsAtAllCameToNothing() {
        assertTrue(TransactionCursorAdapter.isZero(new Money()));
    }

    /**
     * Several currencies, only one of them left over. A check that stopped at the first entry
     * would call this nothing and hide a figure that has something to say. Money keeps its
     * currencies in a HashMap, so which one is read first is not ours to pick, and both
     * pairings are asserted, since whichever comes first one of them puts the zero in front.
     */
    @Test
    public void aFigureHoldingOneCurrencyOutOfTwoDidNot() {
        Money transfer = new Money(USD, 0);
        transfer.addMoney(EUR, -9200);
        assertFalse(TransactionCursorAdapter.isZero(transfer));
        Money flipped = new Money(EUR, 0);
        flipped.addMoney(USD, -9200);
        assertFalse(TransactionCursorAdapter.isZero(flipped));
    }
}
