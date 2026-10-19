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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.preference.PreferenceManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static com.oriondev.moneywallet.storage.database.SplitLedger.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The progress of a budget counts its confirmed transactions only, as the wallet balance and
 * the other totals do, while the list of the budget still shows the unconfirmed ones. Each
 * budget runs over today and holds one confirmed and one unconfirmed row from yesterday, so
 * the confirmed one is there to show the sum is not just empty.
 */
@RunWith(RobolectricTestRunner.class)
public class BudgetConfirmedTest {

    private static final long CONFIRMED_MONEY = 1234;
    private static final long UNCONFIRMED_MONEY = 5000;

    private Context mContext;
    private ContentResolver mResolver;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        PreferenceManager.initialize(mContext);
        TestDatabases.useFreshDatabase(mContext);
        mResolver = mContext.getContentResolver();
        SplitLedger.build(mContext);
    }

    @Test
    public void anExpensesBudgetCountsOnlyItsConfirmedRows() {
        long budget = insertBudget(990, Schema.BudgetType.EXPENSES, GROCERIES);
        insertTransaction(980, GROCERIES, Schema.Direction.EXPENSE, CONFIRMED_MONEY, true);
        insertTransaction(981, GROCERIES, Schema.Direction.EXPENSE, UNCONFIRMED_MONEY, false);
        assertEquals(CONFIRMED_MONEY, progressOf(budget));
        assertListed(budget, 980, 981);
    }

    @Test
    public void anIncomesBudgetCountsOnlyItsConfirmedRows() {
        long budget = insertBudget(991, Schema.BudgetType.INCOMES, SALARY);
        insertTransaction(982, SALARY, Schema.Direction.INCOME, CONFIRMED_MONEY, true);
        insertTransaction(983, SALARY, Schema.Direction.INCOME, UNCONFIRMED_MONEY, false);
        assertEquals(CONFIRMED_MONEY, progressOf(budget));
        assertListed(budget, 982, 983);
    }

    @Test
    public void aCategoryBudgetCountsOnlyItsConfirmedRows() {
        long budget = insertBudget(992, Schema.BudgetType.CATEGORY, GROCERIES);
        insertTransaction(984, GROCERIES, Schema.Direction.EXPENSE, CONFIRMED_MONEY, true);
        insertTransaction(985, GROCERIES, Schema.Direction.EXPENSE, UNCONFIRMED_MONEY, false);
        assertEquals(-CONFIRMED_MONEY, progressOf(budget));
        assertListed(budget, 984, 985);
    }

    @Test
    public void aBudgetWithOnlyUnconfirmedRowsIsStillListedWithItsWallet() {
        long expenses = insertBudget(993, Schema.BudgetType.EXPENSES, GROCERIES);
        long incomes = insertBudget(994, Schema.BudgetType.INCOMES, SALARY);
        long category = insertBudget(995, Schema.BudgetType.CATEGORY, GROCERIES);
        insertTransaction(986, GROCERIES, Schema.Direction.EXPENSE, UNCONFIRMED_MONEY, false);
        insertTransaction(987, SALARY, Schema.Direction.INCOME, UNCONFIRMED_MONEY, false);
        for (long budget : new long[] {expenses, incomes, category}) {
            assertEquals(0, progressOf(budget));
            assertEquals("<" + CASH + ">", walletsOf(budget));
        }
    }

    /** A budget over the cash wallet from ten days ago to ten days ahead. */
    private long insertBudget(long id, int type, long category) {
        LocalDate today = LocalDate.now();
        database(mContext).execSQL("INSERT INTO budgets (budget_id, budget_type, budget_category, " +
                        "budget_start_date, budget_end_date, budget_money, budget_currency, uuid, " +
                        "last_edit, deleted) VALUES (?, ?, ?, ?, ?, 100000, 'EUR', ?, 1, 0)",
                new Object[] {id, type, category, today.minusDays(10).toString(),
                        today.plusDays(10).toString(), "budget-" + id});
        database(mContext).execSQL("INSERT INTO budget_wallets (_budget, _wallet, uuid, last_edit, " +
                "deleted) VALUES (?, ?, ?, 1, 0)", new Object[] {id, CASH, "bw-" + id});
        if (type == Schema.BudgetType.CATEGORY) {
            database(mContext).execSQL("INSERT INTO budget_categories (_budget, _category, uuid, " +
                    "last_edit, deleted) VALUES (?, ?, ?, 1, 0)", new Object[] {id, category, "bc-" + id});
        }
        return id;
    }

    /** A plain row in the cash wallet, yesterday at noon. */
    private void insertTransaction(long id, long category, int direction, long money, boolean confirmed) {
        database(mContext).execSQL("INSERT INTO transactions (transaction_id, transaction_money, " +
                        "transaction_date, transaction_description, transaction_category, " +
                        "transaction_direction, transaction_type, transaction_wallet, " +
                        "transaction_confirmed, transaction_count_in_total, uuid, last_edit, " +
                        "deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, 1, 0)",
                new Object[] {id, money, LocalDate.now().minusDays(1) + " 12:00:00", description(id),
                        category, direction, Contract.TransactionType.STANDARD, CASH,
                        confirmed ? 1 : 0, "transaction-" + id});
    }

    private long progressOf(long budget) {
        try (Cursor cursor = mResolver.query(DataContentProvider.CONTENT_BUDGETS, new String[] {Contract.Budget.PROGRESS},
                Contract.Budget.ID + " = ?", new String[] {String.valueOf(budget)}, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
        }
    }

    private String walletsOf(long budget) {
        try (Cursor cursor = mResolver.query(DataContentProvider.CONTENT_BUDGETS, new String[] {Contract.Budget.WALLET_IDS},
                Contract.Budget.ID + " = ?", new String[] {String.valueOf(budget)}, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(0);
        }
    }

    private void assertListed(long budget, long... transactions) {
        Set<Long> rows = new HashSet<>();
        Uri uri = Uri.withAppendedPath(ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budget), "transactions");
        try (Cursor cursor = mResolver.query(uri, null, null, null, null)) {
            while (cursor.moveToNext()) {
                rows.add(cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.ID)));
            }
        }
        Set<Long> expected = new HashSet<>();
        for (long transaction : transactions) {
            expected.add(transaction);
        }
        assertEquals(expected, rows);
    }
}
