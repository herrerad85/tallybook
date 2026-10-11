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

package com.oriondev.moneywallet.ui.fragment.base;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Category;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.SplitLedger;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.oriondev.moneywallet.storage.database.SplitLedger.*;
import static org.junit.Assert.assertEquals;

/**
 * The bulk actions on the selected rows of a list, over {@link SplitLedger}.
 */
@RunWith(RobolectricTestRunner.class)
public class SplitSelectionModeTest {

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
     * From two decimals to none the total and each part lose their two last digits together, so
     * the lines still add up to the total.
     */
    @Test
    public void aMoveAcrossDecimalsRescalesThePartsWithTheTotal() {
        assertEquals(0, TransactionSelectionMode.moveTransactions(mResolver, new long[] {RECEIPT}, YEN, "JPY"));
        assertEquals(sorted(GROCERIES + ":" + (10000 - 2000 - 3000) / 100, HOUSEHOLD + ":" + 2000 / 100,
                FUEL + ":" + 3000 / 100), linesOf(RECEIPT));
        assertEquals(10000 / 100, totalOf(RECEIPT));
        // a split left in the euro wallet keeps its parts as they were
        assertEquals(sorted(PHARMACY + ":" + 1500, FUEL + ":" + 2500), linesOf(EXACT));
    }

    /** Within the same decimals the parts are not touched. */
    @Test
    public void aMoveWithinTheSameDecimalsKeepsTheParts() {
        assertEquals(0, TransactionSelectionMode.moveTransactions(mResolver, new long[] {RECEIPT}, BANK, "EUR"));
        assertEquals(sorted(GROCERIES + ":" + (10000 - 2000 - 3000), HOUSEHOLD + ":" + 2000,
                FUEL + ":" + 3000), linesOf(RECEIPT));
    }

    /**
     * A split row whose direction the new category would turn is skipped and counted, like a row
     * of a transfer, and the confirmation counts only the rows that change.
     */
    @Test
    public void aSplitRowIsNotMovedToACategoryOfTheOtherDirection() {
        Category salary = new Category(SALARY, "Salary", null, Contract.CategoryType.INCOME);
        long[] ids = new long[] {RECEIPT, PLAIN_CASH};
        assertEquals(mContext.getResources().getQuantityString(
                R.plurals.message_change_category_selected_transactions, 1, 1, "Salary"),
                TransactionSelectionMode.buildCategoryMessage(mContext.getResources(), mResolver, ids, salary));
        TransactionSelectionMode.SkippedRows skipped = TransactionSelectionMode.changeCategory(mResolver, ids, salary);
        assertEquals(0, skipped.mRefused);
        assertEquals(1, skipped.mSplit);
        assertEquals(GROCERIES, longOf(RECEIPT, Contract.Transaction.CATEGORY_ID));
        assertEquals(Contract.Direction.EXPENSE, longOf(RECEIPT, Contract.Transaction.DIRECTION));
        assertEquals(SALARY, longOf(PLAIN_CASH, Contract.Transaction.CATEGORY_ID));
        assertEquals(Contract.Direction.INCOME, longOf(PLAIN_CASH, Contract.Transaction.DIRECTION));
    }

    /**
     * CROSSED is an expense whose one part sits in Salary, so the part is invalid and the row
     * reads as one Household line. Turned to Bonus, an income, the part would become valid and
     * the row would read Bonus and Salary. It is skipped as a split row is.
     */
    @Test
    public void aRowWithOnlyInvalidPartsIsNotMovedToACategoryOfTheOtherDirection() {
        Category bonus = new Category(BONUS, "Bonus", null, Contract.CategoryType.INCOME);
        long[] ids = new long[] {CROSSED};
        assertEquals(mContext.getResources().getQuantityString(
                R.plurals.message_change_category_selected_transactions, 0, 0, "Bonus"),
                TransactionSelectionMode.buildCategoryMessage(mContext.getResources(), mResolver, ids, bonus));
        TransactionSelectionMode.SkippedRows skipped = TransactionSelectionMode.changeCategory(mResolver, ids, bonus);
        assertEquals(0, skipped.mRefused);
        assertEquals(1, skipped.mSplit);
        assertEquals(HOUSEHOLD, longOf(CROSSED, Contract.Transaction.CATEGORY_ID));
        assertEquals(Contract.Direction.EXPENSE, longOf(CROSSED, Contract.Transaction.DIRECTION));
        assertEquals(sorted(HOUSEHOLD + ":" + 2000), linesOf(CROSSED));
    }

    /**
     * A row whose only parts are flagged deleted is not split, so it moves to a category of the
     * other direction like any plain row.
     */
    @Test
    public void aRowWithOnlyDeletedPartsIsMovedToACategoryOfTheOtherDirection() {
        long row = 962;
        SQLiteDatabase db = database(mContext);
        db.execSQL("INSERT INTO transactions (transaction_id, transaction_money, transaction_date, " +
                "transaction_description, transaction_category, transaction_direction, " +
                "transaction_type, transaction_wallet, transaction_confirmed, " +
                "transaction_count_in_total, uuid, last_edit, deleted) VALUES (?, 1500, ?, " +
                "'deleted-parts', ?, ?, ?, ?, 1, 1, 'transaction-962', 1, 0)", new Object[] {row, DATE,
                GROCERIES, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, CASH});
        db.execSQL("INSERT INTO transaction_splits (split_id, _transaction, split_category, " +
                "split_money, uuid, last_edit, deleted) VALUES (963, ?, ?, 500, 'split-963', 1, 1)",
                new Object[] {row, HOUSEHOLD});
        Category salary = new Category(SALARY, "Salary", null, Contract.CategoryType.INCOME);
        long[] ids = new long[] {row};
        assertEquals(mContext.getResources().getQuantityString(
                R.plurals.message_change_category_selected_transactions, 1, 1, "Salary"),
                TransactionSelectionMode.buildCategoryMessage(mContext.getResources(), mResolver, ids, salary));
        TransactionSelectionMode.SkippedRows skipped = TransactionSelectionMode.changeCategory(mResolver, ids, salary);
        assertEquals(0, skipped.mRefused);
        assertEquals(0, skipped.mSplit);
        assertEquals(SALARY, longOf(row, Contract.Transaction.CATEGORY_ID));
        assertEquals(Contract.Direction.INCOME, longOf(row, Contract.Transaction.DIRECTION));
    }

    /** Within the same direction a split row takes the new main category and keeps its parts. */
    @Test
    public void aSplitRowTakesAMainCategoryOfTheSameDirection() {
        Category pharmacy = new Category(PHARMACY, "Pharmacy", null, Contract.CategoryType.EXPENSE);
        TransactionSelectionMode.SkippedRows skipped =
                TransactionSelectionMode.changeCategory(mResolver, new long[] {RECEIPT}, pharmacy);
        assertEquals(0, skipped.mRefused);
        assertEquals(0, skipped.mSplit);
        assertEquals(sorted(PHARMACY + ":" + (10000 - 2000 - 3000), HOUSEHOLD + ":" + 2000,
                FUEL + ":" + 3000), linesOf(RECEIPT));
    }

    /**
     * DEBT_ROW is skipped as a debt and RECEIPT as a split row Salary would turn to income, and
     * each reason gets its own sentence. PLAIN_CASH moves.
     */
    @Test
    public void theSkipMessageNamesASplitRowApartFromADebtRow() {
        Category salary = new Category(SALARY, "Salary", null, Contract.CategoryType.INCOME);
        TransactionSelectionMode.SkippedRows skipped = TransactionSelectionMode.changeCategory(mResolver,
                new long[] {DEBT_ROW, RECEIPT, PLAIN_CASH}, salary);
        assertEquals(1, skipped.mRefused);
        assertEquals(1, skipped.mSplit);
        assertEquals(mContext.getResources().getQuantityString(R.plurals.message_error_change_selected_transactions, 1, 1) +
                "\n\n" + mContext.getResources().getQuantityString(R.plurals.message_error_change_split_transactions, 1, 1),
                TransactionSelectionMode.buildSkippedMessage(mContext.getResources(), skipped));
        assertEquals(mContext.getResources().getQuantityString(R.plurals.message_error_change_split_transactions, 2, 2),
                TransactionSelectionMode.buildSkippedMessage(mContext.getResources(), new TransactionSelectionMode.SkippedRows(0, 2)));
        assertEquals(null, TransactionSelectionMode.buildSkippedMessage(mContext.getResources(), new TransactionSelectionMode.SkippedRows(0, 0)));
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

    private long totalOf(long transaction) {
        return longOf(transaction, Contract.Transaction.MONEY);
    }

    private long longOf(long transaction, String column) {
        try (Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS, new String[] {column},
                Contract.Transaction.ID + " = ?", new String[] {String.valueOf(transaction)}, null)) {
            cursor.moveToFirst();
            return cursor.getLong(0);
        }
    }

    private static List<String> sorted(String... lines) {
        List<String> list = new ArrayList<>();
        Collections.addAll(list, lines);
        Collections.sort(list);
        return list;
    }
}
