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
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.model.Group;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.storage.wrapper.TransactionHeaderCursor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.oriondev.moneywallet.storage.database.SplitLedger.*;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The category lines over {@link SplitLedger}, and the readers inside the database that group or
 * filter by category, which are a category budget's progress and list, the list of one category,
 * and the guards on deleting a category or changing its type.
 */
@RunWith(RobolectricTestRunner.class)
public class CategoryLinesTest {

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

    /**
     * Every transaction reads as the lines worked out by hand, which covers each invalid split
     * reading as one line for its whole total, and its lines add up to its total.
     */
    @Test
    public void everyTransactionReadsAsItsLinesAndTheyAddUpToItsTotal() {
        assertLinesAreTheLedgers();
    }

    private void assertLinesAreTheLedgers() {
        Map<Long, List<String>> read = new HashMap<>();
        Map<Long, Long> sums = new HashMap<>();
        Set<Long> keys = new HashSet<>();
        int rows = 0;
        try (Cursor cursor = mResolver.query(DataContentProvider.CONTENT_CATEGORY_LINES,
                new String[] {Contract.Transaction.ID, Contract.Transaction.CATEGORY_ID,
                        Contract.Transaction.MONEY, Contract.Transaction.LINE_ID}, null, null, null)) {
            while (cursor.moveToNext()) {
                rows++;
                long id = cursor.getLong(0);
                long money = cursor.getLong(2);
                assertTrue("a line below zero on " + id, money >= 0);
                assertTrue("a key read twice", keys.add(cursor.getLong(3)));
                read.computeIfAbsent(id, key -> new ArrayList<>()).add(cursor.getLong(1) + ":" + money);
                sums.merge(id, money, Long::sum);
            }
        }
        int expectedRows = 0;
        for (long[] transaction : TRANSACTIONS) {
            List<String> expected = new ArrayList<>();
            for (long[] line : LINES.get(transaction[0])) {
                expected.add(line[0] + ":" + line[1]);
            }
            expectedRows += expected.size();
            Collections.sort(expected);
            List<String> actual = read.get(transaction[0]);
            Collections.sort(actual);
            assertEquals("lines of " + transaction[0], expected, actual);
            assertEquals("sum of " + transaction[0], Long.valueOf(transaction[5]), sums.get(transaction[0]));
        }
        assertEquals(expectedRows, rows);
    }

    /** A line carries its transaction's own columns, and the category columns of its line. */
    @Test
    public void aLineCarriesItsTransactionAndItsOwnCategory() {
        try (Cursor cursor = mResolver.query(DataContentProvider.CONTENT_CATEGORY_LINES, null,
                Contract.Transaction.ID + " = ?", new String[] {String.valueOf(WITH_HIDDEN)},
                Contract.Transaction.LINE_ID)) {
            assertEquals(2, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            // the main line first, since its key is the negated id
            assertEquals(HOUSEHOLD, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_ID)));
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow(Contract.Transaction.SPLIT_ID)));
            assertTrue(cursor.moveToNext());
            assertEquals(HIDDEN, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_ID)));
            assertEquals(940L, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.SPLIT_ID)));
            assertEquals("Hidden", cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_NAME)));
            assertEquals(HOUSEHOLD, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_PARENT_ID)));
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_SHOW_REPORT)));
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_PARENT_SHOW_REPORT)));
            assertEquals(Schema.CategoryType.EXPENSE, cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_TYPE)));
            assertEquals(DATE, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transaction.DATE)));
            assertEquals(CASH, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.WALLET_ID)));
            assertEquals("EUR", cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transaction.WALLET_CURRENCY)));
            assertEquals(description(WITH_HIDDEN), cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transaction.DESCRIPTION)));
            assertEquals(Schema.Direction.EXPENSE, cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Transaction.DIRECTION)));
            assertEquals(Contract.TransactionType.STANDARD, cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Transaction.TYPE)));
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Transaction.CONFIRMED)));
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Transaction.COUNT_IN_TOTAL)));
        }
    }

    /** The transactions themselves still read with their totals and main categories. */
    @Test
    public void theTransactionsStillReadWhole() {
        try (Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS, new String[] {
                Contract.Transaction.ID, Contract.Transaction.CATEGORY_ID, Contract.Transaction.MONEY},
                null, null, null)) {
            assertEquals(TRANSACTIONS.length, cursor.getCount());
            while (cursor.moveToNext()) {
                long[] transaction = transaction(cursor.getLong(0));
                assertEquals(transaction[2], cursor.getLong(1));
                assertEquals(transaction[5], cursor.getLong(2));
            }
        }
    }

    /**
     * The progress of a Household budget is every Household line and every line of its hidden
     * child, which a budget covers and the reports do not. Lines of invalid splits are not here.
     */
    @Test
    public void aCategoryBudgetAddsUpItsLines() {
        long budget = insertCategoryBudget(mContext, 960, HOUSEHOLD);
        long household = 2000 + 2000 + (5000 - 1200) + 1200 + 1000 + 1500 + 2200;
        assertEquals(-household, progressOf(budget));
    }

    /**
     * The list of a Household budget holds each transaction once, carrying its Household money,
     * and adds up to the progress.
     */
    @Test
    public void theListOfACategoryBudgetCarriesEachRowsShareAndAddsUpToItsProgress() {
        long budget = insertCategoryBudget(mContext, 960, HOUSEHOLD);
        Map<Long, Long> expected = new HashMap<>();
        expected.put(RECEIPT, 2000L);
        expected.put(CROSSED, 2000L);
        expected.put(WITH_HIDDEN, 5000L);
        expected.put(TWICE, 1000L + 1500L);
        expected.put(PLAIN_BANK, 2200L);
        Map<Long, Long> rows = new HashMap<>();
        long signed = 0;
        Uri uri = Uri.withAppendedPath(ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budget), "transactions");
        try (Cursor cursor = mResolver.query(uri, null, null, null, null)) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.ID));
                long money = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.MONEY));
                assertEquals("each row once", null, rows.put(id, money));
                assertEquals(HOUSEHOLD, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_ID)));
                signed += cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Transaction.DIRECTION)) == Schema.Direction.INCOME ? money : -money;
            }
        }
        assertEquals(expected, rows);
        assertEquals(progressOf(budget), signed);
    }

    /**
     * A row matching on two parts and not on its main line takes the category of the part with
     * the lower id, and one matching on its main line takes the main category.
     */
    @Test
    public void aRowMatchingOnPartsTakesTheCategoryOfItsFirstPart() {
        long budget = insertCategoryBudget(mContext, 961, FUEL, PHARMACY);
        Map<Long, String> rows = new HashMap<>();
        Uri uri = Uri.withAppendedPath(ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budget), "transactions");
        try (Cursor cursor = mResolver.query(uri, null, null, null, null)) {
            while (cursor.moveToNext()) {
                rows.put(cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.ID)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_ID)) + ":" +
                                cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.MONEY)));
            }
        }
        Map<Long, String> expected = new HashMap<>();
        expected.put(RECEIPT, FUEL + ":" + 3000);
        // parts 932 in Pharmacy and 933 in Fuel, no main line
        expected.put(EXACT, PHARMACY + ":" + (1500 + 2500));
        expected.put(OVER, PHARMACY + ":" + 1000);
        expected.put(NEGATIVE, FUEL + ":" + 3000);
        expected.put(DEBT_ROW, FUEL + ":" + 6000);
        assertEquals(expected, rows);
        assertEquals(-(3000 + 4000 + 1000 + 3000 + 6000), progressOf(budget));
    }

    /**
     * A category flagged deleted, which only a restore leaves behind, still named by a budget.
     * Its lines leave the progress, so they leave the list too. OVER read as one Pharmacy line,
     * and EXACT loses its valid parts since one sits in a deleted category, so it reads as one
     * Groceries line.
     */
    @Test
    public void theListOfACategoryBudgetLeavesOutLinesInADeletedCategory() {
        database(mContext).execSQL("UPDATE categories SET deleted = 1 WHERE category_id = " + PHARMACY);
        long budget = insertCategoryBudget(mContext, 962, FUEL, PHARMACY);
        Map<Long, Long> rows = new HashMap<>();
        long signed = 0;
        Uri uri = Uri.withAppendedPath(ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budget), "transactions");
        try (Cursor cursor = mResolver.query(uri, null, null, null, null)) {
            while (cursor.moveToNext()) {
                long money = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.MONEY));
                rows.put(cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.ID)), money);
                signed += cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Transaction.DIRECTION)) == Schema.Direction.INCOME ? money : -money;
            }
        }
        Map<Long, Long> expected = new HashMap<>();
        expected.put(RECEIPT, 3000L);
        expected.put(NEGATIVE, 3000L);
        expected.put(DEBT_ROW, 6000L);
        assertEquals(expected, rows);
        assertEquals(-(3000 + 3000 + 6000), progressOf(budget));
        assertEquals(progressOf(budget), signed);
    }

    /**
     * The list of one category shows each row once with its share, and the header over it adds
     * up those shares.
     */
    @Test
    public void theListOfACategoryCarriesEachRowsShareAndSoDoesItsHeader() {
        Uri uri = Uri.withAppendedPath(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, HOUSEHOLD), "transactions");
        Map<Long, Long> rows = new HashMap<>();
        try (Cursor cursor = mResolver.query(uri, null, null, null, Contract.Transaction.DATE + " DESC")) {
            while (cursor.moveToNext()) {
                assertEquals("each row once", null, rows.put(
                        cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.ID)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.MONEY))));
            }
        }
        Map<Long, Long> expected = new HashMap<>();
        expected.put(RECEIPT, 2000L);
        expected.put(CROSSED, 2000L);
        // the hidden child is a child of Household, so its part is in this list
        expected.put(WITH_HIDDEN, 5000L);
        expected.put(TWICE, 1000L + 1500L);
        expected.put(PLAIN_BANK, 2200L);
        assertEquals(expected, rows);

        Cursor cursor = mResolver.query(uri, null, null, null, Contract.Transaction.DATE + " DESC");
        TransactionHeaderCursor headers = new TransactionHeaderCursor(cursor, Group.MONTHLY, null, null);
        try {
            assertTrue(headers.moveToFirst());
            assertTrue(headers.isHeaderAt(0));
            long household = 2000 + 2000 + 5000 + 2500 + 2200;
            assertEquals("EUR " + household, headers.getString(headers.getColumnIndexOrThrow(TransactionHeaderCursor.COLUMN_HEADER_EXPENSE)));
            assertEquals("EUR " + -household, headers.getString(headers.getColumnIndexOrThrow(TransactionHeaderCursor.COLUMN_HEADER_MONEY)));
        } finally {
            headers.close();
        }
    }

    /** A category only a live part uses is refused the same way one a transaction uses is. */
    @Test
    public void aCategoryALivePartUsesCannotBeDeleted() {
        try {
            mResolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, BONUS), null, null);
            fail("a category a part uses was deleted");
        } catch (SQLiteDataException expected) {
            assertEquals(Contract.ErrorCode.CATEGORY_IN_SPLIT, expected.getErrorCode());
            // the one part in Bonus is the one of PAY_SPLIT
            assertArrayEquals(new long[] {PAY_SPLIT}, expected.getItemIds());
        }
        assertEquals(1L, count("categories", "category_id = " + BONUS));
        assertEquals(1L, count("transaction_splits", "split_category = " + BONUS));
    }

    /** A part flagged deleted does not hold a category, and leaves with it. */
    @Test
    public void aCategoryOnlyADeletedPartNamesCanBeDeleted() {
        assertEquals(1, mResolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, GIFTS), null, null));
        assertEquals(0L, count("categories", "category_id = " + GIFTS));
        assertEquals(0L, count("transaction_splits", "split_category = " + GIFTS));
    }

    /**
     * A part no screen shows, here one that takes the parts of OVER further past its total, does
     * not hold its category. It leaves with it, and every line still reads as before.
     */
    @Test
    public void aCategoryOnlyAnInvalidPartUsesCanBeDeletedAndNoLineMoves() {
        insertToolsPart(OVER, 50);
        assertLinesAreTheLedgers();
        assertEquals(1, mResolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, TOOLS), null, null));
        assertEquals(0L, count("categories", "category_id = " + TOOLS));
        assertEquals(0L, count("transaction_splits", "split_category = " + TOOLS));
        assertEquals(2L, count("transaction_splits", "_transaction = " + OVER));
        assertLinesAreTheLedgers();
    }

    /**
     * A part that alone takes the parts of EXACT past its total keeps the others from being read,
     * so deleting its category would make them valid and move their money off the main category.
     */
    @Test
    public void aCategoryWhosePartAloneKeepsTheOthersInvalidCannotBeDeleted() {
        insertToolsPart(EXACT, 100);
        try {
            mResolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, TOOLS), null, null);
            fail("deleting the category made the other parts valid");
        } catch (SQLiteDataException expected) {
            assertEquals(Contract.ErrorCode.CATEGORY_IN_SPLIT, expected.getErrorCode());
            assertArrayEquals(new long[] {EXACT}, expected.getItemIds());
        }
        assertEquals(1L, count("transaction_splits", "split_category = " + TOOLS));
    }

    /**
     * The refusal lists the newest transaction first by its date, and on a tie the higher id first.
     * WITH_HIDDEN is moved after the rest, and RECEIPT and TWICE share the fixture's date.
     */
    @Test
    public void aDeleteRefusedListsTheNewestByDateFirst() {
        insertToolsPart(RECEIPT, 100);
        SQLiteDatabase db = database(mContext);
        db.execSQL("INSERT INTO transaction_splits (split_id, _transaction, split_category, " +
                "split_money, uuid, last_edit, deleted) VALUES (972, ?, ?, 100, 'split-tools-twice', 1, 0)",
                new Object[] {TWICE, TOOLS});
        db.execSQL("INSERT INTO transaction_splits (split_id, _transaction, split_category, " +
                "split_money, uuid, last_edit, deleted) VALUES (973, ?, ?, 100, 'split-tools-hidden', 1, 0)",
                new Object[] {WITH_HIDDEN, TOOLS});
        db.execSQL("UPDATE transactions SET transaction_date = '2026-03-01 10:00:00' WHERE transaction_id = ?",
                new Object[] {WITH_HIDDEN});
        try {
            mResolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, TOOLS), null, null);
            fail("a category three parts use was deleted");
        } catch (SQLiteDataException expected) {
            assertEquals(Contract.ErrorCode.CATEGORY_IN_SPLIT, expected.getErrorCode());
            assertArrayEquals(new long[] {WITH_HIDDEN, TWICE, RECEIPT}, expected.getItemIds());
        }
    }

    private static final long TOOLS = 970;

    private void insertToolsPart(long transaction, long money) {
        SQLiteDatabase db = database(mContext);
        db.execSQL("INSERT INTO categories (category_id, category_name, category_icon, category_type, " +
                "category_show_report, uuid, last_edit, deleted) VALUES (?, 'Tools', ?, ?, 1, " +
                "'category-tools', 1, 0)", new Object[] {TOOLS, ICON, Schema.CategoryType.EXPENSE});
        db.execSQL("INSERT INTO transaction_splits (split_id, _transaction, split_category, " +
                "split_money, uuid, last_edit, deleted) VALUES (971, ?, ?, ?, 'split-tools', 1, 0)",
                new Object[] {transaction, TOOLS, money});
    }

    /** Turning the type of a category a live part uses would leave the part in the other direction. */
    @Test
    public void theTypeOfACategoryALivePartUsesCannotChange() {
        assertTypeChangeRefused(BONUS, Schema.CategoryType.EXPENSE);
    }

    /** Turning the type of the main category of a split would turn its direction away from its parts. */
    @Test
    public void theTypeOfTheMainCategoryOfASplitCannotChange() {
        assertTypeChangeRefused(SALARY, Schema.CategoryType.EXPENSE);
    }

    /** A category no live part depends on changes type as before, and so does a rename. */
    @Test
    public void aCategoryNoLivePartDependsOnChangesType() {
        ContentValues values = new ContentValues();
        values.put(Contract.Category.TYPE, Schema.CategoryType.INCOME);
        assertEquals(1, mResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, GIFTS), values, null, null));
        assertEquals(1L, count("categories", "category_id = " + GIFTS + " AND category_type = " + Schema.CategoryType.INCOME));
        values = new ContentValues();
        values.put(Contract.Category.NAME, "Extra");
        values.put(Contract.Category.TYPE, Schema.CategoryType.INCOME);
        assertEquals(1, mResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, BONUS), values, null, null));
        assertEquals(1L, count("categories", "category_id = " + BONUS + " AND category_name = 'Extra'"));
    }

    private void assertTypeChangeRefused(long category, int type) {
        ContentValues values = new ContentValues();
        values.put(Contract.Category.TYPE, type);
        try {
            mResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, category), values, null, null);
            fail("the type changed under a split");
        } catch (SQLiteDataException expected) {
            assertEquals(Contract.ErrorCode.CATEGORY_IN_USE, expected.getErrorCode());
        }
        assertFalse(count("categories", "category_id = " + category + " AND category_type = " + type) > 0);
        assertEquals(0L, count("transactions", "transaction_category = " + category +
                " AND transaction_direction = " + (type == Schema.CategoryType.INCOME
                ? Schema.Direction.INCOME : Schema.Direction.EXPENSE)));
    }

    private long progressOf(long budget) {
        try (Cursor cursor = mResolver.query(DataContentProvider.CONTENT_BUDGETS, new String[] {Contract.Budget.PROGRESS},
                Contract.Budget.ID + " = ?", new String[] {String.valueOf(budget)}, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
        }
    }

    private long count(String table, String selection) {
        try (Cursor cursor = SQLDatabase.getShared(mContext).getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + table + " WHERE " + selection, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
        }
    }
}
