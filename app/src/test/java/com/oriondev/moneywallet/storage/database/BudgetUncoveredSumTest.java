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
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.preference.PreferenceManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import static com.oriondev.moneywallet.storage.database.SplitLedger.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class BudgetUncoveredSumTest {

    private static final String JAN_START = "2026-01-01";
    private static final String JAN_END = "2026-01-31";
    private static final String NOV_START = "2025-11-01";
    private static final String NOV_END = "2025-11-30";
    private static final String NOV_DATE = "2025-11-10 12:00:00";
    private static final long EXPENSES = 1001;
    private static final long[] EURO = {CASH, BANK};
    private static final long[] CASH_ONLY = {CASH};

    private static final long CHECKING = 905;
    private static final long SAVINGS = 906;
    private static final long HOME_SUPPLIES = 960;
    private static final long GONE = 961;

    private Context mContext;
    private long mTransaction = 2000;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        PreferenceManager.initialize(mContext);
        TestDatabases.useFreshDatabase(mContext);
        SplitLedger.build(mContext);
        insertBudget(EXPENSES, Schema.BudgetType.EXPENSES, JAN_START, JAN_END, 999, 0, EURO);
    }

    @Test
    public void moneyLentFromTheWalletIsNotInACategoryBudget() {
        insertCategory(HOME_SUPPLIES, "Home supplies", 0);
        insertBudget(1101, Schema.BudgetType.EXPENSES, NOV_START, NOV_END, 999, 0, CASH_ONLY);
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 10000, 0, CASH_ONLY, HOME_SUPPLIES);
        database(mContext).execSQL("UPDATE budgets SET budget_tag = ? WHERE budget_id = 1101",
                new Object[] {Contract.Budget.TAG_FOLLOWS_CATEGORIES});
        insertExpense(CASH, HOME_SUPPLIES, 3000);
        insertTransaction(CASH, systemCategory(Schema.CategoryTag.CREDIT), Schema.Direction.EXPENSE,
                Contract.TransactionType.DEBT, 1, NOV_DATE, 10000);
        assertUncovered(1101, 10000, 3000);
        assertEquals(Long.valueOf(13000), longOf(1101, Contract.Budget.PROGRESS));
        assertEquals(Long.valueOf(10000), longOf(1101, Contract.Budget.CATEGORY_SUM));
        assertEquals(Long.valueOf(10000), longOf(1101, Contract.Budget.SHOWN_MONEY));
    }

    @Test
    public void aTransferOutOfTheBudgetAndItsFeeAreNotInACategoryBudget() {
        insertWallet(CHECKING, "Checking");
        insertWallet(SAVINGS, "Savings");
        insertBudget(1101, Schema.BudgetType.EXPENSES, NOV_START, NOV_END, 999, 0, new long[] {CHECKING});
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 5000, 0, new long[] {CHECKING}, HOUSEHOLD);
        insertExpense(CHECKING, HOUSEHOLD, 3000);
        insertTransfer(CHECKING, SAVINGS, 40000, 250);
        assertUncovered(1101, 40000 + 250, 3000);
    }

    @Test
    public void aTransferBetweenTwoWalletsOfTheBudgetIsNotCountedButItsFeeIs() {
        insertWallet(CHECKING, "Checking");
        insertWallet(SAVINGS, "Savings");
        long[] both = {CHECKING, SAVINGS};
        insertBudget(1101, Schema.BudgetType.EXPENSES, NOV_START, NOV_END, 999, 0, both);
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 5000, 0, both, HOUSEHOLD);
        insertExpense(CHECKING, HOUSEHOLD, 3000);
        insertExpense(SAVINGS, GROCERIES, 1100);
        insertTransfer(CHECKING, SAVINGS, 40000, 250);
        assertUncovered(1101, 1100 + 250, 3000);
    }

    @Test
    public void aTransferToAWalletTakenOutOfTheBudgetAndItsFeeAreNotInACategoryBudget() {
        insertWallet(CHECKING, "Checking");
        insertWallet(SAVINGS, "Savings");
        insertBudget(1101, Schema.BudgetType.EXPENSES, NOV_START, NOV_END, 999, 0, new long[] {CHECKING, SAVINGS});
        removeWallet(1101, SAVINGS);
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 5000, 0, new long[] {CHECKING}, HOUSEHOLD);
        insertExpense(CHECKING, HOUSEHOLD, 3000);
        insertTransfer(CHECKING, SAVINGS, 40000, 250);
        assertUncovered(1101, 40000 + 250, 3000);
    }

    @Test
    public void theLinesOfASplitTransactionAreCoveredOneByOne() {
        insertBudget(1101, Schema.BudgetType.EXPENSES, NOV_START, NOV_END, 999, 0, CASH_ONLY);
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 6000, 0, CASH_ONLY, FUEL);
        long receipt = insertExpense(CASH, GROCERIES, 10000);
        insertPart(970, receipt, HOUSEHOLD, 3000);
        insertPart(971, receipt, FUEL, 5000);
        assertUncovered(1101, 3000 + 2000, 5000);
    }

    @Test
    public void everySplitOfTheLedgerIsCoveredLineByLine() {
        insertBudget(1002, Schema.BudgetType.CATEGORY, JAN_START, JAN_END, 400, 0, EURO, HOUSEHOLD);
        insertBudget(1003, Schema.BudgetType.CATEGORY, JAN_START, JAN_END, 100, 0, CASH_ONLY, FUEL);
        assertUncovered(EXPENSES, 5000 + 1500 + 1000 + 4500 + 1100, linesOf(EURO, HOUSEHOLD) + linesOf(CASH_ONLY, FUEL));
    }

    @Test
    public void aCategoryBudgetOnFewerWalletsLeavesItsCategoryInTheOtherWalletOut() {
        insertBudget(1101, Schema.BudgetType.EXPENSES, NOV_START, NOV_END, 999, 0, EURO);
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 6000, 0, CASH_ONLY, FUEL);
        insertExpense(CASH, FUEL, 2000);
        insertExpense(BANK, FUEL, 1500);
        assertUncovered(1101, 1500, 2000);
    }

    @Test
    public void anExpenseInAWalletOutsideTheBudgetIsLeftOut() {
        insertBudget(1101, Schema.BudgetType.EXPENSES, NOV_START, NOV_END, 999, 0, CASH_ONLY);
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 6000, 0, CASH_ONLY, GROCERIES);
        insertExpense(BANK, FUEL, 500);
        insertExpense(CASH, FUEL, 200);
        insertExpense(CASH, GROCERIES, 100);
        assertUncovered(1101, 200, 100);
    }

    @Test
    public void aWalletTakenOutOfTheBudgetIsLeftOut() {
        insertBudget(1101, Schema.BudgetType.EXPENSES, NOV_START, NOV_END, 999, 0, EURO);
        removeWallet(1101, BANK);
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 6000, 0, CASH_ONLY, GROCERIES);
        insertExpense(BANK, FUEL, 500);
        insertExpense(CASH, FUEL, 200);
        insertExpense(CASH, GROCERIES, 100);
        assertUncovered(1101, 200, 100);
    }

    @Test
    public void aLineInTwoOverlappingCategoryBudgetsIsCoveredOnce() {
        insertBudget(1101, Schema.BudgetType.EXPENSES, NOV_START, NOV_END, 999, 0, EURO);
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 6000, 0, CASH_ONLY, GROCERIES, HOUSEHOLD);
        insertBudget(1103, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 7000, 0, EURO, HOUSEHOLD);
        insertExpense(CASH, HOUSEHOLD, 4000);
        insertExpense(CASH, GROCERIES, 1000);
        insertExpense(BANK, HOUSEHOLD, 700);
        insertExpense(CASH, FUEL, 300);
        assertEquals(Long.valueOf(6000 + 7000), longOf(1101, Contract.Budget.CATEGORY_SUM));
        assertUncovered(1101, 300, 4000 + 1000 + 700);
    }

    @Test
    public void aParentCategoryBudgetCoversTheLinesOfItsChild() {
        insertBudget(1101, Schema.BudgetType.EXPENSES, NOV_START, NOV_END, 999, 0, CASH_ONLY);
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 6000, 0, CASH_ONLY, HOUSEHOLD);
        insertExpense(CASH, HIDDEN, 1200);
        insertExpense(CASH, FUEL, 800);
        assertUncovered(1101, 800, 1200);
    }

    @Test
    public void aCategoryBudgetWithOtherDatesCoversNothing() {
        insertBudget(1101, Schema.BudgetType.EXPENSES, NOV_START, NOV_END, 999, 0, CASH_ONLY);
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 6000, 0, CASH_ONLY, GROCERIES);
        insertBudget(1103, Schema.BudgetType.CATEGORY, "2025-11-02", NOV_END, 6000, 0, CASH_ONLY, FUEL);
        insertExpense(CASH, FUEL, 900);
        insertExpense(CASH, GROCERIES, 100);
        assertUncovered(1101, 900, 100);
    }

    @Test
    public void aCategoryBudgetReachingAWalletOutsideTheBudgetCoversNothing() {
        insertBudget(1101, Schema.BudgetType.EXPENSES, NOV_START, NOV_END, 999, 0, CASH_ONLY);
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 6000, 0, CASH_ONLY, GROCERIES);
        insertBudget(1103, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 7000, 0, new long[] {CASH, YEN}, FUEL);
        insertExpense(CASH, FUEL, 200);
        insertExpense(CASH, GROCERIES, 100);
        assertEquals(Long.valueOf(6000), longOf(1101, Contract.Budget.CATEGORY_SUM));
        assertUncovered(1101, 200, 100);
    }

    @Test
    public void aCategoryBudgetReachingAWalletTakenOutOfTheBudgetCoversNothing() {
        insertBudget(1101, Schema.BudgetType.EXPENSES, NOV_START, NOV_END, 999, 0, EURO);
        removeWallet(1101, BANK);
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 6000, 0, CASH_ONLY, GROCERIES);
        insertBudget(1103, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 7000, 0, EURO, FUEL);
        insertExpense(CASH, FUEL, 200);
        insertExpense(CASH, GROCERIES, 100);
        assertEquals(Long.valueOf(6000), longOf(1101, Contract.Budget.CATEGORY_SUM));
        assertUncovered(1101, 200, 100);
    }

    @Test
    public void aWalletTakenOutOfACategoryBudgetIsNotCovered() {
        insertBudget(1101, Schema.BudgetType.EXPENSES, NOV_START, NOV_END, 999, 0, EURO);
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 6000, 0, EURO, FUEL);
        removeWallet(1102, BANK);
        insertExpense(BANK, FUEL, 500);
        insertExpense(CASH, FUEL, 200);
        assertUncovered(1101, 500, 200);
    }

    @Test
    public void aCategoryTakenOutOfACategoryBudgetIsNotCovered() {
        insertBudget(1101, Schema.BudgetType.EXPENSES, NOV_START, NOV_END, 999, 0, CASH_ONLY);
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 6000, 0, CASH_ONLY, GROCERIES, FUEL);
        removeCategory(1102, FUEL);
        insertExpense(CASH, FUEL, 200);
        insertExpense(CASH, GROCERIES, 100);
        assertUncovered(1101, 200, 100);
    }

    @Test
    public void aLineInADeletedCategoryIsNotCovered() {
        insertCategory(GONE, "Gone", 1);
        insertBudget(1101, Schema.BudgetType.EXPENSES, NOV_START, NOV_END, 999, 0, CASH_ONLY);
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 6000, 0, CASH_ONLY, GROCERIES, GONE);
        insertExpense(CASH, GONE, 600);
        insertExpense(CASH, GROCERIES, 400);
        assertUncovered(1101, 600, 400);
    }

    @Test
    public void incomingAndPendingTransactionsAreLeftOut() {
        insertBudget(1101, Schema.BudgetType.EXPENSES, NOV_START, NOV_END, 999, 0, CASH_ONLY);
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 6000, 0, CASH_ONLY, GROCERIES);
        insertTransaction(CASH, SALARY, Schema.Direction.INCOME, Contract.TransactionType.STANDARD, 1, NOV_DATE, 5000);
        insertTransaction(CASH, GROCERIES, Schema.Direction.INCOME, Contract.TransactionType.STANDARD, 1, NOV_DATE, 50);
        insertTransaction(CASH, FUEL, Schema.Direction.EXPENSE, Contract.TransactionType.STANDARD, 0, NOV_DATE, 700);
        insertExpense(CASH, FUEL, 200);
        insertExpense(CASH, GROCERIES, 100);
        assertUncovered(1101, 200, 100);
    }

    @Test
    public void aTransactionDatedInTheFutureIsLeftOut() {
        insertWallet(CHECKING, "Checking");
        long[] checking = {CHECKING};
        insertBudget(1101, Schema.BudgetType.EXPENSES, NOV_START, "2099-12-31", 999, 0, checking);
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, "2099-12-31", 6000, 0, checking, GROCERIES);
        insertTransaction(CHECKING, FUEL, Schema.Direction.EXPENSE, Contract.TransactionType.STANDARD, 1,
                "2099-06-01 12:00:00", 700);
        insertExpense(CHECKING, FUEL, 250);
        insertExpense(CHECKING, GROCERIES, 100);
        assertUncovered(1101, 250, 100);
    }

    @Test
    public void aTransactionDatedBeforeTheBudgetStartsIsLeftOut() {
        insertBudget(1101, Schema.BudgetType.EXPENSES, NOV_START, NOV_END, 999, 0, CASH_ONLY);
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 6000, 0, CASH_ONLY, GROCERIES);
        insertTransaction(CASH, FUEL, Schema.Direction.EXPENSE, Contract.TransactionType.STANDARD, 1,
                "2025-10-31 12:00:00", 700);
        insertExpense(CASH, FUEL, 200);
        insertExpense(CASH, GROCERIES, 100);
        assertUncovered(1101, 200, 100);
    }

    @Test
    public void itIsZeroWhenEveryLineIsCovered() {
        insertBudget(1101, Schema.BudgetType.EXPENSES, NOV_START, NOV_END, 999, 0, CASH_ONLY);
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 6000, 0, CASH_ONLY, GROCERIES, FUEL);
        insertExpense(CASH, GROCERIES, 100);
        insertExpense(CASH, FUEL, 200);
        assertUncovered(1101, 0, 300);
    }

    @Test
    public void anIncomesBudgetLeavesItNull() {
        insertBudget(1101, Schema.BudgetType.INCOMES, NOV_START, NOV_END, 999, 0, CASH_ONLY);
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 6000, 0, CASH_ONLY, GROCERIES);
        insertTransaction(CASH, SALARY, Schema.Direction.INCOME, Contract.TransactionType.STANDARD, 1, NOV_DATE, 5000);
        insertExpense(CASH, FUEL, 200);
        assertNotCounted(1101, 5000);
    }

    @Test
    public void aCategoryBudgetLeavesItNull() {
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 6000, 0, CASH_ONLY, GROCERIES);
        insertExpense(CASH, GROCERIES, 100);
        assertNotCounted(1102, -100);
    }

    @Test
    public void anExpensesBudgetWithNoCountedCategoryBudgetLeavesItNull() {
        insertBudget(1101, Schema.BudgetType.EXPENSES, NOV_START, NOV_END, 999, 0, CASH_ONLY);
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 6000, 0, new long[] {YEN}, GROCERIES);
        insertExpense(CASH, FUEL, 200);
        assertNull(longOf(1101, Contract.Budget.CATEGORY_SUM));
        assertNotCounted(1101, 200);
    }

    @Test
    public void theListOfBudgetsLeavesItNull() {
        insertBudget(1101, Schema.BudgetType.EXPENSES, NOV_START, NOV_END, 999, 0, CASH_ONLY);
        insertBudget(1102, Schema.BudgetType.CATEGORY, NOV_START, NOV_END, 6000, 0, CASH_ONLY, GROCERIES);
        insertExpense(CASH, FUEL, 200);
        insertExpense(CASH, GROCERIES, 100);
        assertUncovered(1101, 200, 100);
        try (Cursor cursor = mContext.getContentResolver().query(DataContentProvider.CONTENT_BUDGETS,
                new String[] {Contract.Budget.UNCOVERED_SUM, Contract.Budget.PROGRESS}, Contract.Budget.ID + " = ?",
                new String[] {"1101"}, null)) {
            assertTrue(cursor.moveToFirst());
            assertTrue(cursor.isNull(0));
            assertEquals(200 + 100, cursor.getLong(1));
        }
    }

    private void assertUncovered(long budget, long uncovered, long covered) {
        Long[] row = uncoveredAndProgressOf(budget);
        assertEquals(Long.valueOf(uncovered), row[0]);
        assertEquals(Long.valueOf(uncovered + covered), row[1]);
    }

    private void assertNotCounted(long budget, long progress) {
        Long[] row = uncoveredAndProgressOf(budget);
        assertNull(row[0]);
        assertEquals(Long.valueOf(progress), row[1]);
    }

    private Long[] uncoveredAndProgressOf(long budget) {
        try (Cursor cursor = mContext.getContentResolver().query(
                ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budget),
                new String[] {Contract.Budget.UNCOVERED_SUM, Contract.Budget.PROGRESS}, null, null, null)) {
            assertTrue(cursor.moveToFirst());
            return new Long[] {cursor.isNull(0) ? null : cursor.getLong(0), cursor.isNull(1) ? null : cursor.getLong(1)};
        }
    }

    private static long linesOf(long[] wallets, long... categories) {
        long money = 0;
        for (long[] transaction : TRANSACTIONS) {
            if (transaction[3] != Schema.Direction.EXPENSE || !contains(wallets, transaction[1])) {
                continue;
            }
            List<long[]> lines = LINES.get(transaction[0]);
            for (long[] line : lines) {
                long parent = line[0] == HIDDEN ? HOUSEHOLD : 0;
                if (contains(categories, line[0]) || contains(categories, parent)) {
                    money += line[1];
                }
            }
        }
        return money;
    }

    private static boolean contains(long[] values, long value) {
        for (long candidate : values) {
            if (candidate == value) {
                return true;
            }
        }
        return false;
    }

    private long systemCategory(String tag) {
        try (Cursor cursor = database(mContext).rawQuery("SELECT category_id FROM categories WHERE category_tag = ?",
                new String[] {tag})) {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
        }
    }

    private long insertExpense(long wallet, long category, long money) {
        return insertTransaction(wallet, category, Schema.Direction.EXPENSE, Contract.TransactionType.STANDARD, 1,
                NOV_DATE, money);
    }

    private long insertTransaction(long wallet, long category, int direction, int type, int confirmed, String date,
                                   long money) {
        long id = ++mTransaction;
        database(mContext).execSQL("INSERT INTO transactions (transaction_id, transaction_money, " +
                        "transaction_date, transaction_description, transaction_category, " +
                        "transaction_direction, transaction_type, transaction_wallet, " +
                        "transaction_confirmed, transaction_count_in_total, uuid, last_edit, " +
                        "deleted) VALUES (?, ?, ?, 'Spent', ?, ?, ?, ?, ?, 1, ?, 1, 0)",
                new Object[] {id, money, date, category, direction, type, wallet, confirmed, "transaction-" + id});
        return id;
    }

    private void insertPart(long id, long transaction, long category, long money) {
        database(mContext).execSQL("INSERT INTO transaction_splits (split_id, _transaction, split_category, " +
                "split_money, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, 1, 0)",
                new Object[] {id, transaction, category, money, "split-" + id});
    }

    private void insertTransfer(long from, long to, long money, long fee) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Transfer.DESCRIPTION, "Moved");
        contentValues.put(Contract.Transfer.DATE, NOV_DATE);
        contentValues.put(Contract.Transfer.TRANSACTION_FROM_WALLET_ID, from);
        contentValues.put(Contract.Transfer.TRANSACTION_FROM_MONEY, money);
        contentValues.put(Contract.Transfer.TRANSACTION_TO_WALLET_ID, to);
        contentValues.put(Contract.Transfer.TRANSACTION_TO_MONEY, money);
        contentValues.put(Contract.Transfer.TRANSACTION_TAX_WALLET_ID, from);
        contentValues.put(Contract.Transfer.TRANSACTION_TAX_MONEY, fee);
        contentValues.put(Contract.Transfer.CONFIRMED, true);
        contentValues.put(Contract.Transfer.COUNT_IN_TOTAL, true);
        assertTrue(SQLDatabase.getShared(mContext).insertTransfer(contentValues) > 0);
    }

    private void insertWallet(long id, String name) {
        database(mContext).execSQL("INSERT INTO wallets (wallet_id, wallet_name, wallet_icon, " +
                "wallet_currency, wallet_count_in_total, uuid, last_edit, deleted) VALUES " +
                "(?, ?, ?, 'EUR', 1, ?, 1, 0)", new Object[] {id, name, ICON, "wallet-" + id});
    }

    private void insertCategory(long id, String name, int deleted) {
        database(mContext).execSQL("INSERT INTO categories (category_id, category_name, category_icon, " +
                "category_type, category_show_report, uuid, last_edit, deleted) VALUES " +
                "(?, ?, ?, ?, 1, ?, 1, ?)", new Object[] {id, name, ICON, Schema.CategoryType.EXPENSE, "category-" + id,
                deleted});
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

    private void removeWallet(long budget, long wallet) {
        database(mContext).execSQL("UPDATE budget_wallets SET deleted = 1 WHERE _budget = ? AND _wallet = ?",
                new Object[] {budget, wallet});
    }

    private void removeCategory(long budget, long category) {
        database(mContext).execSQL("UPDATE budget_categories SET deleted = 1 WHERE _budget = ? AND _category = ?",
                new Object[] {budget, category});
    }

    private Long longOf(long budget, String column) {
        try (Cursor cursor = mContext.getContentResolver().query(
                ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budget),
                new String[] {column}, null, null, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.isNull(0) ? null : cursor.getLong(0);
        }
    }
}
