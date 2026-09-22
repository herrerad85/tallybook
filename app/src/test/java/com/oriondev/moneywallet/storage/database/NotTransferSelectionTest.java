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

package com.oriondev.moneywallet.storage.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * What {@link Contract.Transaction#NOT_TRANSFER} selects. It is the rule the overview charts and
 * the two flow tabs are filtered by, so a screen that sums one direction on its own leaves money
 * moved between the user's own wallets out and agrees with the header on the transactions list.
 *
 * These run it on real SQLite in the JVM, the way {@link CategoryRuleMatchTest} runs the category
 * lookup, and through {@link SQLDatabase#getTransactions} so the rule meets the column alias the
 * screens actually query by and not a column named here.
 *
 * The rows are written by {@link SQLDatabase#insertTransfer}, which is what the transfer editor
 * saves through, so the tag on each row is the one the app puts there.
 */
@RunWith(RobolectricTestRunner.class)
public class NotTransferSelectionTest {

    private static final String NAME = "transfers.db";

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private static final String DATE = "2019-03-15 10:00:00";

    /** Both legs of one transfer, which is the money that has to drop out. */
    private static final long LEG = 40000L;

    /** The fee on that transfer, money genuinely spent and written with the same type. */
    private static final long FEE = 250L;

    /** An ordinary expense, filed under a category the user made, which carries no tag. */
    private static final long GROCERIES = 1999L;

    private SQLDatabase mDatabase;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        mDatabase = new SQLDatabase(context, NAME);
        long from = insertWallet("Checking");
        long to = insertWallet("Savings");
        insertTransfer(from, to);
        insertExpense(from, insertCategory("Groceries"), GROCERIES);
    }

    @After
    public void tearDown() {
        mDatabase.close();
    }

    /**
     * Four rows go in and two come back. The two legs are the whole of what the rule drops, and
     * every other row a transfer writes or a user leaves stays.
     */
    @Test
    public void bothLegsOfATransferDropOutAndNothingElseDoes() {
        assertEquals("the rule selected " + moneyKept(),
                moneyIn(FEE, GROCERIES), moneyKept());
    }

    /**
     * The fee is the case the rule is written against the tag for. It carries the same
     * transaction type as the two legs and it is money the user spent, so a rule keyed on the
     * type would take it out of the expenses total and off the pie with them.
     */
    @Test
    public void theFeeOnATransferIsMoneySpentAndStays() {
        assertTrue("the rule selected " + moneyKept(), moneyKept().contains(FEE));
    }

    /**
     * A category the user made carries no tag at all, and that is nearly every row on the list.
     * The rule wraps that tag, because a comparison against NULL answers NULL and selects
     * nothing, so without the wrapper the charts would come back holding the transfer fee and
     * the system categories and nothing else.
     */
    @Test
    public void aRowInACategoryTheUserMadeStays() {
        assertTrue("the rule selected " + moneyKept(), moneyKept().contains(GROCERIES));
    }

    /** Every amount the rule selects, smallest first. */
    private List<Long> moneyKept() {
        String[] projection = new String[] {Contract.Transaction.MONEY};
        Cursor cursor = mDatabase.getTransactions(
                projection, Contract.Transaction.NOT_TRANSFER, null, null);
        assertNotNull(cursor);
        try {
            List<Long> money = new ArrayList<>();
            while (cursor.moveToNext()) {
                money.add(cursor.getLong(
                        cursor.getColumnIndexOrThrow(Contract.Transaction.MONEY)));
            }
            Collections.sort(money);
            return money;
        } finally {
            cursor.close();
        }
    }

    private List<Long> moneyIn(long... amounts) {
        List<Long> money = new ArrayList<>();
        for (long amount : amounts) {
            money.add(amount);
        }
        Collections.sort(money);
        return money;
    }

    private void insertTransfer(long from, long to) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Transfer.DESCRIPTION, "Moved");
        contentValues.put(Contract.Transfer.DATE, DATE);
        contentValues.put(Contract.Transfer.TRANSACTION_FROM_WALLET_ID, from);
        contentValues.put(Contract.Transfer.TRANSACTION_FROM_MONEY, LEG);
        contentValues.put(Contract.Transfer.TRANSACTION_TO_WALLET_ID, to);
        contentValues.put(Contract.Transfer.TRANSACTION_TO_MONEY, LEG);
        contentValues.put(Contract.Transfer.TRANSACTION_TAX_WALLET_ID, from);
        contentValues.put(Contract.Transfer.TRANSACTION_TAX_MONEY, FEE);
        contentValues.put(Contract.Transfer.CONFIRMED, true);
        contentValues.put(Contract.Transfer.COUNT_IN_TOTAL, true);
        assertTrue(mDatabase.insertTransfer(contentValues) > 0);
    }

    private void insertExpense(long wallet, Long category, long money) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Transaction.MONEY, money);
        contentValues.put(Contract.Transaction.DATE, DATE);
        contentValues.put(Contract.Transaction.DESCRIPTION, "Spent");
        contentValues.put(Contract.Transaction.CATEGORY_ID, category);
        contentValues.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE);
        contentValues.put(Contract.Transaction.TYPE, Contract.TransactionType.STANDARD);
        contentValues.put(Contract.Transaction.WALLET_ID, wallet);
        contentValues.put(Contract.Transaction.CONFIRMED, true);
        contentValues.put(Contract.Transaction.COUNT_IN_TOTAL, true);
        assertTrue(mDatabase.insertTransaction(contentValues) > 0);
    }

    private long insertWallet(String name) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Wallet.NAME, name);
        contentValues.put(Contract.Wallet.ICON, ICON);
        contentValues.put(Contract.Wallet.CURRENCY, "EUR");
        contentValues.put(Contract.Wallet.START_MONEY, 0L);
        contentValues.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        long id = mDatabase.insertWallet(contentValues);
        assertTrue(id > 0);
        return id;
    }

    private long insertCategory(String name) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Category.NAME, name);
        contentValues.put(Contract.Category.ICON, ICON);
        contentValues.put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.getValue());
        contentValues.put(Contract.Category.SHOW_REPORT, true);
        long id = mDatabase.insertCategory(contentValues);
        assertTrue(id > 0);
        return id;
    }
}
