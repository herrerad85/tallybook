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

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.preference.PreferenceManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static com.oriondev.moneywallet.storage.database.SplitLedger.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class BudgetCategorySumTest {

    private static final String JAN_START = "2026-01-01";
    private static final String JAN_END = "2026-01-31";
    private static final long EXPENSES = 1001;
    private static final long[] EURO = {CASH, BANK};
    private static final long[] CASH_ONLY = {CASH};

    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        PreferenceManager.initialize(mContext);
        TestDatabases.useFreshDatabase(mContext);
        SplitLedger.build(mContext);
        insertBudget(EXPENSES, Schema.BudgetType.EXPENSES, JAN_START, JAN_END, 999, 0, EURO);
    }

    @Test
    public void onlyACategoryBudgetOverTheSameDatesWalletsAndExpenseCategoriesCounts() {
        insertMatchingRules();
        insertBudget(1009, Schema.BudgetType.INCOMES, JAN_START, JAN_END, 5000, 0, EURO);
        assertEquals(Long.valueOf(400), categorySumOf(EXPENSES));
        assertNull(categorySumOf(1009));
    }

    @Test
    public void aParentAndItsChildCountTheParentOnly() {
        insertCategoryBudget(1010, 300, EURO, HOUSEHOLD);
        insertCategoryBudget(1011, 120, CASH_ONLY, HIDDEN);
        assertEquals(Long.valueOf(300), categorySumOf(EXPENSES));
    }

    @Test
    public void theSameCategoryCountsOnceAndAPartialOverlapCountsBoth() {
        insertCategoryBudget(1012, 200, CASH_ONLY, FUEL);
        insertCategoryBudget(1013, 150, EURO, FUEL);
        insertCategoryBudget(1014, 100, CASH_ONLY, GROCERIES, PHARMACY);
        insertCategoryBudget(1015, 250, EURO, GROCERIES, HOUSEHOLD);
        assertEquals(Long.valueOf(150 + 100 + 250), categorySumOf(EXPENSES));
    }

    @Test
    public void theSameCategoryOnSeparateWalletsCountsBoth() {
        insertCategoryBudget(1017, 200, CASH_ONLY, FUEL);
        insertCategoryBudget(1018, 150, new long[] {BANK}, FUEL);
        assertEquals(Long.valueOf(200 + 150), categorySumOf(EXPENSES));
    }

    @Test
    public void twoBudgetsOverTheSameCategoriesWalletsAndLimitCountOnce() {
        insertCategoryBudget(1022, 80, CASH_ONLY, FUEL);
        insertCategoryBudget(1023, 80, CASH_ONLY, FUEL);
        assertEquals(Long.valueOf(80), categorySumOf(EXPENSES));
    }

    @Test
    public void deletedAndUnlinkedWalletsDoNotDecideWhetherACategoryBudgetCounts() {
        database(mContext).execSQL("INSERT INTO wallets (wallet_id, wallet_name, wallet_icon, " +
                "wallet_currency, wallet_count_in_total, uuid, last_edit, deleted) VALUES " +
                "(904, 'Gone', ?, 'EUR', 1, 'wallet-904', 1, 1)", new Object[] {ICON});
        insertCategoryBudget(1024, 60, new long[] {904}, FUEL);
        insertCategoryBudget(1025, 70, new long[] {CASH, 904}, PHARMACY);
        insertCategoryBudget(1026, 30, new long[] {CASH, YEN}, GIFTS);
        database(mContext).execSQL("UPDATE budget_wallets SET deleted = 1 WHERE _budget = 1026 AND _wallet = " + YEN);
        assertEquals(Long.valueOf(70 + 30), categorySumOf(EXPENSES));
    }

    @Test
    public void deletedCategoriesDoNotDecideWhetherACategoryBudgetCounts() {
        database(mContext).execSQL("INSERT INTO categories (category_id, category_name, category_icon, " +
                "category_type, category_show_report, uuid, last_edit, deleted) VALUES " +
                "(919, 'Gone', ?, ?, 1, 'category-919', 1, 1)", new Object[] {ICON, Schema.CategoryType.INCOME});
        insertCategoryBudget(1027, 40, new long[] {BANK}, 919);
        insertCategoryBudget(1028, 20, CASH_ONLY, GROCERIES, 919);
        assertEquals(Long.valueOf(20), categorySumOf(EXPENSES));
    }

    @Test
    public void aCategoryBudgetWhoseDatesCarryATimeStillMatches() {
        insertBudget(1016, Schema.BudgetType.CATEGORY, JAN_START + " 00:00:00", JAN_END + " 23:59:59",
                350, 0, CASH_ONLY, FUEL);
        assertEquals(Long.valueOf(350), categorySumOf(EXPENSES));
    }

    @Test
    public void aFlaggedExpensesBudgetReadsTheSumAsItsLimitAndFallsBackToItsStoredOne() {
        insertMatchingRules();
        insertBudget(1020, Schema.BudgetType.EXPENSES, JAN_START, JAN_END, 999, 0, EURO);
        insertBudget(1021, Schema.BudgetType.EXPENSES, "2026-02-01", "2026-02-28", 777, 0, new long[] {YEN});
        followCategoryBudgets(1020);
        followCategoryBudgets(1021);
        assertEquals(Long.valueOf(400), longOf(1020, Contract.Budget.MONEY));
        assertEquals(Long.valueOf(999), longOf(EXPENSES, Contract.Budget.MONEY));
        assertNull(categorySumOf(1021));
        assertEquals(Long.valueOf(777), longOf(1021, Contract.Budget.MONEY));
    }

    @Test
    public void theSumOfABudgetNotSavedYetFollowsTheDatesAndWalletsAskedFor() {
        insertMatchingRules();
        assertEquals(Long.valueOf(400), sumFor(JAN_START, JAN_END, "<" + CASH + ">,<" + BANK + ">"));
        assertNull(sumFor(JAN_START, JAN_END, "<" + BANK + ">"));
        assertNull(sumFor("2026-02-01", "2026-02-28", "<" + CASH + ">"));
        assertNull(sumFor(JAN_START, JAN_END, null));
    }

    private void followCategoryBudgets(long budget) {
        database(mContext).execSQL("UPDATE budgets SET budget_tag = ? WHERE budget_id = ?",
                new Object[] {Contract.Budget.TAG_FOLLOWS_CATEGORIES, budget});
    }

    private void insertMatchingRules() {
        insertCategoryBudget(1002, 400, CASH_ONLY, GROCERIES);
        insertBudget(1003, Schema.BudgetType.CATEGORY, "2026-01-08", "2026-01-14", 50, 0, EURO, FUEL);
        insertBudget(1004, Schema.BudgetType.CATEGORY, "2025-12-25", "2026-01-24", 70, 0, CASH_ONLY, PHARMACY);
        insertBudget(1005, Schema.BudgetType.CATEGORY, JAN_START, JAN_END, 9000, 0, new long[] {YEN}, PHARMACY);
        insertCategoryBudget(1006, 800, CASH_ONLY, SALARY);
        insertCategoryBudget(1007, 600, CASH_ONLY, GROCERIES, SALARY);
        insertBudget(1008, Schema.BudgetType.CATEGORY, JAN_START, JAN_END, 999, 1, CASH_ONLY, FUEL);
    }

    private void insertCategoryBudget(long id, long money, long[] wallets, long... categories) {
        insertBudget(id, Schema.BudgetType.CATEGORY, JAN_START, JAN_END, money, 0, wallets, categories);
    }

    private void insertBudget(long id, int type, String start, String end, long money, int deleted,
                              long[] wallets, long... categories) {
        database(mContext).execSQL("INSERT INTO budgets (budget_id, budget_type, budget_category, " +
                        "budget_start_date, budget_end_date, budget_money, budget_currency, uuid, " +
                        "last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, 'EUR', ?, 1, ?)",
                new Object[] {id, type, categories.length > 0 ? categories[0] : null, start, end,
                        money, "budget-" + id, deleted});
        for (long wallet : wallets) {
            database(mContext).execSQL("INSERT INTO budget_wallets (_budget, _wallet, uuid, last_edit, " +
                    "deleted) VALUES (?, ?, ?, 1, 0)", new Object[] {id, wallet, "bw-" + id + "-" + wallet});
        }
        for (long category : categories) {
            database(mContext).execSQL("INSERT INTO budget_categories (_budget, _category, uuid, " +
                    "last_edit, deleted) VALUES (?, ?, ?, 1, 0)",
                    new Object[] {id, category, "bc-" + id + "-" + category});
        }
    }

    private Long sumFor(String start, String end, String walletIds) {
        try (Cursor cursor = mContext.getContentResolver().query(DataContentProvider.CONTENT_BUDGET_CATEGORY_SUM,
                null, null, new String[] {start, end, walletIds}, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.isNull(0) ? null : cursor.getLong(0);
        }
    }

    private Long categorySumOf(long budget) {
        try (Cursor cursor = mContext.getContentResolver().query(
                ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budget),
                new String[] {Contract.Budget.CATEGORY_SUM}, null, null, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.isNull(0) ? null : cursor.getLong(0);
        }
    }

    private Long longOf(long budget, String column) {
        try (Cursor cursor = mContext.getContentResolver().query(DataContentProvider.CONTENT_BUDGETS,
                new String[] {column}, Contract.Budget.ID + " = ?", new String[] {String.valueOf(budget)}, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.isNull(0) ? null : cursor.getLong(0);
        }
    }
}
