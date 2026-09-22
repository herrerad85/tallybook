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

package com.oriondev.moneywallet.storage.wrapper;

import com.oriondev.moneywallet.model.Group;
import com.oriondev.moneywallet.storage.database.Contract;

import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;

/**
 * A group header on the transactions list carries four figures, what came in, what went out, what
 * moved between the user's own wallets, and the difference. The first three are what a reader
 * compares, so none of them may cancel against another, and the last has to stay what came in,
 * less what went out, plus what moved, or the header contradicts itself.
 *
 * Group.DAILY is used throughout because the other groupings ask PreferenceManager for the first
 * day of the week or the month, which needs a running app. The arithmetic under test does not
 * depend on which grouping built the header.
 */
public class TransactionHeaderMoneyTest {

    private static final String USD = "USD";

    private TransactionHeaderCursor.Header header() {
        Date date = new Date(0);
        return new TransactionHeaderCursor.Header(Group.DAILY, null, null, date);
    }

    @Test
    public void incomeAndExpenseAreBothPositiveAndTheTotalIsTheDifference() {
        TransactionHeaderCursor.Header header = header();
        header.add(USD, 120000, Contract.Direction.INCOME, null);
        header.add(USD, 45000, Contract.Direction.INCOME, null);
        header.add(USD, 60000, Contract.Direction.EXPENSE, null);
        header.add(USD, 4000, Contract.Direction.EXPENSE, null);
        assertEquals(165000, header.getIncome().getMoney(USD));
        assertEquals(64000, header.getExpense().getMoney(USD));
        assertEquals(101000, header.getMoney().getMoney(USD));
    }

    @Test
    public void spendingMoreThanCameInLeavesTheTotalNegativeAndTheOtherTwoUntouched() {
        TransactionHeaderCursor.Header header = header();
        header.add(USD, 1000, Contract.Direction.INCOME, null);
        header.add(USD, 3000, Contract.Direction.EXPENSE, null);
        assertEquals(1000, header.getIncome().getMoney(USD));
        assertEquals(3000, header.getExpense().getMoney(USD));
        assertEquals(-2000, header.getMoney().getMoney(USD));
    }

    /**
     * A figure counts only the rows going its own way, so the other one is left holding no
     * currency at all. What the screen does with that is TransactionCursorAdapter.orZero, and
     * this is the half of it the header owes: a currency here would be one nobody spent or
     * earned, and on a header counting two currencies it would be a second amount to read.
     */
    @Test
    public void aHeaderOfExpensesOnlyLeavesTheIncomeHoldingNothing() {
        TransactionHeaderCursor.Header header = header();
        header.add(USD, 4500, Contract.Direction.EXPENSE, null);
        assertEquals(0, header.getIncome().getNumberOfCurrencies());
        assertEquals(4500, header.getExpense().getMoney(USD));
        assertEquals(-4500, header.getMoney().getMoney(USD));
    }

    @Test
    public void aHeaderOfIncomeOnlyLeavesTheExpenseHoldingNothing() {
        TransactionHeaderCursor.Header header = header();
        header.add(USD, 4500, Contract.Direction.INCOME, null);
        assertEquals(0, header.getExpense().getNumberOfCurrencies());
        assertEquals(4500, header.getIncome().getMoney(USD));
        assertEquals(4500, header.getMoney().getMoney(USD));
    }

    /**
     * Money moved between two of the user's own wallets is neither earned nor spent, so it is
     * held apart from the other two and the difference still comes out where it did before. The
     * amounts here are one wallet's side of a transfer out, which is the only side that wallet
     * has.
     */
    @Test
    public void aTransferLeavesIncomeAndExpenseAloneAndStillReachesTheTotal() {
        TransactionHeaderCursor.Header header = header();
        header.add(USD, 10000, Contract.Direction.INCOME, null);
        header.add(USD, 4000, Contract.Direction.EXPENSE, null);
        header.add(USD, 40000, Contract.Direction.EXPENSE, Contract.CategoryTag.TRANSFER);
        assertEquals(10000, header.getIncome().getMoney(USD));
        assertEquals(4000, header.getExpense().getMoney(USD));
        assertEquals(-40000, header.getTransfer().getMoney(USD));
        assertEquals(-34000, header.getMoney().getMoney(USD));
    }

    /**
     * Both sides of the same transfer land here whenever the header is counting every wallet at
     * once, and they cancel. The screen reads that as nothing to say and shows no transfer
     * figure at all, which is TransactionCursorAdapter.isZero.
     *
     * The dollar has to be asserted present as well as zero. Money.getMoney answers zero for a
     * currency it has never heard of, so on its own that assertion would pass on a header that
     * never counted the two rows at all.
     */
    @Test
    public void bothSidesOfOneTransferCancel() {
        TransactionHeaderCursor.Header header = header();
        header.add(USD, 40000, Contract.Direction.EXPENSE, Contract.CategoryTag.TRANSFER);
        header.add(USD, 40000, Contract.Direction.INCOME, Contract.CategoryTag.TRANSFER);
        assertEquals(0, header.getIncome().getNumberOfCurrencies());
        assertEquals(0, header.getExpense().getNumberOfCurrencies());
        assertEquals(1, header.getTransfer().getNumberOfCurrencies());
        assertEquals(0, header.getTransfer().getMoney(USD));
        assertEquals(0, header.getMoney().getMoney(USD));
    }

    /**
     * A transfer that charged a fee writes a third row, and that fee is money the user genuinely
     * lost. It carries its own tag, so it stays an expense. This is why the tag decides and not
     * the transaction type, which all three rows share.
     */
    @Test
    public void aTransferFeeIsStillAnExpense() {
        TransactionHeaderCursor.Header header = header();
        header.add(USD, 40000, Contract.Direction.EXPENSE, Contract.CategoryTag.TRANSFER);
        header.add(USD, 250, Contract.Direction.EXPENSE, Contract.CategoryTag.TRANSFER_TAX);
        assertEquals(250, header.getExpense().getMoney(USD));
        assertEquals(-40000, header.getTransfer().getMoney(USD));
        assertEquals(-40250, header.getMoney().getMoney(USD));
    }

    /**
     * A header that moved nothing between wallets leaves the transfer figure holding no currency,
     * the same way an expense only header leaves the income one empty.
     */
    @Test
    public void aHeaderWithNoTransfersLeavesTheTransferFigureHoldingNothing() {
        TransactionHeaderCursor.Header header = header();
        header.add(USD, 4500, Contract.Direction.EXPENSE, null);
        assertEquals(0, header.getTransfer().getNumberOfCurrencies());
    }

    @Test
    public void currenciesAreKeptApart() {
        TransactionHeaderCursor.Header header = header();
        header.add(USD, 1000, Contract.Direction.INCOME, null);
        header.add("EUR", 700, Contract.Direction.EXPENSE, null);
        assertEquals(1000, header.getIncome().getMoney(USD));
        assertEquals(0, header.getIncome().getMoney("EUR"));
        assertEquals(700, header.getExpense().getMoney("EUR"));
        assertEquals(1000, header.getMoney().getMoney(USD));
        assertEquals(-700, header.getMoney().getMoney("EUR"));
    }
}
