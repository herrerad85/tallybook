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
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.oriondev.moneywallet.storage.database.SplitLedger.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Parts written through the provider with a transaction, over {@link SplitLedger}. Every figure
 * expected below is worked out from the ledger's rows and the parts each case writes.
 */
@RunWith(RobolectricTestRunner.class)
public class SplitWriteTest {

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
    public void anInsertWithPartsStoresThemAndReadsAsSplit() throws Exception {
        ContentValues values = newExpense(9000L);
        values.put(Contract.Transaction.SPLIT_PARTS, parts(part(HOUSEHOLD, 2000, "soap"), part(FUEL, 1500, null)));
        long id = ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values));
        assertEquals(sorted(HOUSEHOLD + ":2000:soap", FUEL + ":1500:null"), partsOf(id));
        assertEquals(sorted(GROCERIES + ":" + (9000 - 2000 - 1500), HOUSEHOLD + ":2000", FUEL + ":1500"), linesOf(id));
        // Groceries for what is left, Household and Fuel
        assertEquals(3L, splitCategoriesOf(id));
    }

    /**
     * A part in the main category itself, with money left over: the lines are Groceries for what
     * is left, Groceries for the part and Household, which is two categories.
     */
    @Test
    public void aPartInTheMainCategoryIsCountedOnce() throws Exception {
        ContentValues values = newExpense(9000L);
        values.put(Contract.Transaction.SPLIT_PARTS, parts(part(GROCERIES, 1000, null), part(HOUSEHOLD, 2000, null)));
        long id = ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values));
        assertEquals(sorted(GROCERIES + ":" + (9000 - 1000 - 2000), GROCERIES + ":1000", HOUSEHOLD + ":2000"), linesOf(id));
        assertEquals(2L, splitCategoriesOf(id));
    }

    /** The per category list reads the shares view, which carries the count too. */
    @Test
    public void theSharesCarryTheCountOfCategories() {
        try (Cursor cursor = mResolver.query(DataContentProvider.CONTENT_CATEGORY_SHARES,
                new String[] {Contract.Transaction.ID, Contract.Transaction.SPLIT_CATEGORIES},
                Contract.Transaction.CATEGORY_ID + " = ?", new String[] {String.valueOf(HOUSEHOLD)},
                Contract.Transaction.ID)) {
            List<String> rows = new ArrayList<>();
            while (cursor.moveToNext()) {
                rows.add(cursor.getLong(0) + ":" + cursor.getLong(1));
            }
            // the rows with a Household line. CROSSED and PLAIN_BANK read as unsplit, WITH_HIDDEN
            // as Household and Hidden. OVER is filed under Pharmacy, and its parts are not read
            assertEquals(Arrays.asList(RECEIPT + ":3", CROSSED + ":0", WITH_HIDDEN + ":2", TWICE + ":2",
                    PLAIN_BANK + ":0"), rows);
        }
    }

    @Test
    public void anUpdateWithPartsReplacesEveryPartTheRowHad() throws Exception {
        ContentValues values = new ContentValues();
        values.put(Contract.Transaction.SPLIT_PARTS, parts(part(PHARMACY, 4000, "drops")));
        assertEquals(1, update(RECEIPT, values));
        // the part flagged deleted goes too, the row holds exactly the list
        assertEquals(sorted(PHARMACY + ":4000:drops:0"), allPartsOf(RECEIPT));
        assertEquals(sorted(GROCERIES + ":" + (10000 - 4000), PHARMACY + ":4000"), linesOf(RECEIPT));
    }

    @Test
    public void anUpdateWithoutThePartsLeavesThemAlone() {
        List<String> before = allPartsOf(RECEIPT);
        ContentValues values = new ContentValues();
        values.put(Contract.Transaction.DESCRIPTION, "edited");
        values.put(Contract.Transaction.MONEY, 12000L);
        assertEquals(1, update(RECEIPT, values));
        assertEquals(before, allPartsOf(RECEIPT));
        assertEquals(sorted(GROCERIES + ":" + (12000 - 2000 - 3000), HOUSEHOLD + ":2000", FUEL + ":3000"), linesOf(RECEIPT));
    }

    @Test
    public void anEmptyListLeavesTheRowUnsplit() {
        ContentValues values = new ContentValues();
        values.put(Contract.Transaction.SPLIT_PARTS, "[]");
        assertEquals(1, update(RECEIPT, values));
        assertEquals(Collections.<String>emptyList(), allPartsOf(RECEIPT));
        assertEquals(sorted(GROCERIES + ":10000"), linesOf(RECEIPT));
        assertEquals(0L, splitCategoriesOf(RECEIPT));
    }

    @Test
    public void anEmptyListIsTakenOnARowThatCannotBeSplit() {
        ContentValues values = new ContentValues();
        values.put(Contract.Transaction.SPLIT_PARTS, "[]");
        assertEquals(1, update(DEBT_ROW, values));
        assertEquals(Collections.<String>emptyList(), allPartsOf(DEBT_ROW));
    }

    @Test
    public void aPartOfZeroIsRefused() throws Exception {
        assertUpdateRefused(RECEIPT, parts(part(HOUSEHOLD, 2000, null), part(FUEL, 0, null)));
    }

    @Test
    public void aPartBelowZeroIsRefused() throws Exception {
        assertUpdateRefused(RECEIPT, parts(part(HOUSEHOLD, -1, null)));
    }

    @Test
    public void partsAboveTheTotalAreRefused() throws Exception {
        assertUpdateRefused(RECEIPT, parts(part(HOUSEHOLD, 6000, null), part(FUEL, 4001, null)));
    }

    /** The same parts are refused once the total of the same write drops under them. */
    @Test
    public void aTotalLoweredUnderThePartsIsRefused() throws Exception {
        ContentValues values = new ContentValues();
        values.put(Contract.Transaction.MONEY, 4999L);
        values.put(Contract.Transaction.SPLIT_PARTS, parts(part(HOUSEHOLD, 2000, null), part(FUEL, 3000, null)));
        assertRefusedAndUnchanged(RECEIPT, values);
    }

    @Test
    public void aPartOfTheOtherDirectionIsRefused() throws Exception {
        assertUpdateRefused(RECEIPT, parts(part(SALARY, 1000, null)));
    }

    /** Turning the row to income keeps the expense parts, which are then of the other direction. */
    @Test
    public void aDirectionFlipUnderExpensePartsIsRefused() throws Exception {
        ContentValues values = new ContentValues();
        values.put(Contract.Transaction.CATEGORY_ID, SALARY);
        values.put(Contract.Transaction.DIRECTION, Contract.Direction.INCOME);
        values.put(Contract.Transaction.SPLIT_PARTS, parts(part(HOUSEHOLD, 2000, null)));
        assertRefusedAndUnchanged(RECEIPT, values);
    }

    @Test
    public void aPartInADeletedCategoryIsRefused() throws Exception {
        SplitLedger.database(mContext).execSQL("UPDATE categories SET deleted = 1 WHERE category_id = " + SPARE);
        assertUpdateRefused(RECEIPT, parts(part(SPARE, 1000, null)));
    }

    /** Added plainly, two halves of the largest long wrap below zero and pass the total of 10000. */
    @Test
    public void partsAddingUpPastTheLargestLongAreRefused() throws Exception {
        long half = Long.MAX_VALUE / 2 + 1;
        assertUpdateRefused(RECEIPT, parts(part(HOUSEHOLD, half, null), part(FUEL, half, null)));
    }

    @Test
    public void aPartInASystemCategoryIsRefused() throws Exception {
        long system;
        try (Cursor cursor = SplitLedger.database(mContext).rawQuery(
                "SELECT category_id FROM categories WHERE category_type = " + Schema.CategoryType.SYSTEM, null)) {
            assertTrue("no system category", cursor.moveToFirst());
            system = cursor.getLong(0);
        }
        assertUpdateRefused(RECEIPT, parts(part(system, 1000, null)));
    }

    @Test
    public void partsOnARowThatIsNotStandardAreRefused() throws Exception {
        assertUpdateRefused(DEBT_ROW, parts(part(GROCERIES, 1000, null)));
    }

    /** A refused insert leaves neither the row nor any part behind. */
    @Test
    public void aRefusedInsertTakesTheRowBackOut() throws Exception {
        long transactions = count("transactions", "1");
        long parts = count("transaction_splits", "1");
        ContentValues values = newExpense(3000L);
        values.put(Contract.Transaction.SPLIT_PARTS, parts(part(HOUSEHOLD, 2000, null), part(FUEL, 1001, null)));
        try {
            mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values);
            fail("parts above the total were stored");
        } catch (SQLiteDataException expected) {
            assertEquals(Contract.ErrorCode.SPLIT_NOT_VALID, expected.getErrorCode());
        }
        assertEquals(transactions, count("transactions", "1"));
        assertEquals(parts, count("transaction_splits", "1"));
    }

    // fixtures

    private ContentValues newExpense(long money) {
        ContentValues values = new ContentValues();
        values.put(Contract.Transaction.MONEY, money);
        values.put(Contract.Transaction.DATE, DATE);
        values.put(Contract.Transaction.DESCRIPTION, "new");
        values.put(Contract.Transaction.CATEGORY_ID, GROCERIES);
        values.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE);
        values.put(Contract.Transaction.TYPE, Contract.TransactionType.STANDARD);
        values.put(Contract.Transaction.WALLET_ID, CASH);
        values.put(Contract.Transaction.CONFIRMED, true);
        values.put(Contract.Transaction.COUNT_IN_TOTAL, true);
        return values;
    }

    private static JSONObject part(long category, long money, String note) throws JSONException {
        JSONObject part = new JSONObject();
        part.put(Contract.TransactionSplit.PART_CATEGORY, category);
        part.put(Contract.TransactionSplit.PART_MONEY, money);
        part.put(Contract.TransactionSplit.PART_NOTE, note != null ? note : JSONObject.NULL);
        return part;
    }

    private static String parts(JSONObject... parts) {
        JSONArray array = new JSONArray();
        for (JSONObject part : parts) {
            array.put(part);
        }
        return array.toString();
    }

    private int update(long id, ContentValues values) {
        Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, id);
        return mResolver.update(uri, values, null, null);
    }

    private void assertUpdateRefused(long id, String parts) {
        ContentValues values = new ContentValues();
        // a change to the row in the same write, which the refusal has to take back too
        values.put(Contract.Transaction.DESCRIPTION, "refused");
        values.put(Contract.Transaction.SPLIT_PARTS, parts);
        assertRefusedAndUnchanged(id, values);
    }

    private void assertRefusedAndUnchanged(long id, ContentValues values) {
        List<String> parts = allPartsOf(id);
        List<String> lines = linesOf(id);
        long[] row = transaction(id);
        try {
            update(id, values);
            fail("the parts were taken");
        } catch (SQLiteDataException expected) {
            assertEquals(Contract.ErrorCode.SPLIT_NOT_VALID, expected.getErrorCode());
        }
        assertEquals(parts, allPartsOf(id));
        assertEquals(lines, linesOf(id));
        assertEquals(1L, count("transactions", "transaction_id = " + id + " AND transaction_money = " +
                row[5] + " AND transaction_category = " + row[2] + " AND transaction_direction = " +
                row[3] + " AND transaction_description = '" + description(id) + "'"));
    }

    // reading back

    /** The live parts of a row as category:money:note. */
    private List<String> partsOf(long transaction) {
        return rows("SELECT split_category || ':' || split_money || ':' || IFNULL(split_note, 'null') " +
                "FROM transaction_splits WHERE deleted = 0 AND _transaction = " + transaction);
    }

    /** Every part a row has, flagged deleted or not. */
    private List<String> allPartsOf(long transaction) {
        return rows("SELECT split_category || ':' || split_money || ':' || IFNULL(split_note, 'null') || ':' || " +
                "deleted FROM transaction_splits WHERE _transaction = " + transaction);
    }

    private List<String> rows(String sql) {
        List<String> rows = new ArrayList<>();
        try (Cursor cursor = SplitLedger.database(mContext).rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                rows.add(cursor.getString(0));
            }
        }
        Collections.sort(rows);
        return rows;
    }

    private List<String> linesOf(long transaction) {
        List<String> lines = new ArrayList<>();
        try (Cursor cursor = mResolver.query(DataContentProvider.CONTENT_CATEGORY_LINES,
                new String[] {Contract.Transaction.CATEGORY_ID, Contract.Transaction.MONEY},
                Contract.Transaction.ID + " = ?", new String[] {String.valueOf(transaction)}, null)) {
            while (cursor.moveToNext()) {
                lines.add(cursor.getLong(0) + ":" + cursor.getLong(1));
            }
        }
        Collections.sort(lines);
        return lines;
    }

    private long splitCategoriesOf(long transaction) {
        try (Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS,
                new String[] {Contract.Transaction.SPLIT_CATEGORIES}, Contract.Transaction.ID + " = ?",
                new String[] {String.valueOf(transaction)}, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
        }
    }

    private long count(String table, String selection) {
        try (Cursor cursor = SplitLedger.database(mContext).rawQuery(
                "SELECT COUNT(*) FROM " + table + " WHERE " + selection, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
        }
    }

    private static List<String> sorted(String... rows) {
        List<String> list = new ArrayList<>();
        Collections.addAll(list, rows);
        Collections.sort(list);
        return list;
    }
}
