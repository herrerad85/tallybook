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
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.model.Category;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.RefusingDataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.database.TransferContentValuesBuilder;
import com.oriondev.moneywallet.utils.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;
import java.util.Date;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The bulk delete, move and category loops against the real provider and database.
 */
@RunWith(RobolectricTestRunner.class)
public class TransactionSelectionModeTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private ContentResolver mResolver;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        mResolver = context.getContentResolver();
    }

    @Test
    public void aRefusedRowIsCountedAndTheRowsAfterItAreStillDeleted() {
        long cash = insertWallet("Cash", "EUR");
        long bank = insertWallet("Bank", "EUR");
        long category = insertCategory(Contract.CategoryType.EXPENSE);
        long before = insertTransaction(cash, category, 1250L);
        insertTransfer(cash, bank);
        long leg = idOfType(Contract.TransactionType.TRANSFER);
        long after = insertTransaction(cash, category, 1250L);
        Set<Long> refused = TransactionSelectionMode.deleteTransactions(mResolver, new long[] {before, leg, after});
        assertEquals(Collections.singleton(leg), refused);
        assertFalse(rowExists(before));
        assertTrue(rowExists(leg));
        assertFalse(rowExists(after));
    }

    @Test
    public void aMoveBetweenCurrenciesWithTheSameDecimalsWritesOnlyTheWallet() {
        long euro = insertWallet("Cash", "EUR");
        long dollar = insertWallet("Bank", "USD");
        long category = insertCategory(Contract.CategoryType.EXPENSE);
        long id = insertTransaction(euro, category, 1250L);
        assertEquals(0, TransactionSelectionMode.moveTransactions(mResolver, new long[] {id}, dollar, "USD"));
        assertEquals(dollar, longOf(id, Contract.Transaction.WALLET_ID));
        assertEquals(1250L, longOf(id, Contract.Transaction.MONEY));
        assertEquals(category, longOf(id, Contract.Transaction.CATEGORY_ID));
        assertEquals(Contract.Direction.EXPENSE, longOf(id, Contract.Transaction.DIRECTION));
    }

    @Test
    public void aMoveFromTwoDecimalsToNoneKeepsTheNumberShownAndTruncates() {
        long dollar = insertWallet("Bank", "USD");
        long yen = insertWallet("Tokyo", "JPY");
        long id = insertTransaction(dollar, insertCategory(Contract.CategoryType.EXPENSE), 50055L);
        assertEquals(0, TransactionSelectionMode.moveTransactions(mResolver, new long[] {id}, yen, "JPY"));
        assertEquals(yen, longOf(id, Contract.Transaction.WALLET_ID));
        assertEquals(500L, longOf(id, Contract.Transaction.MONEY));
    }

    @Test
    public void aMoveFromNoDecimalsToTwoKeepsTheNumberShown() {
        long yen = insertWallet("Tokyo", "JPY");
        long dollar = insertWallet("Bank", "USD");
        long id = insertTransaction(yen, insertCategory(Contract.CategoryType.EXPENSE), 500L);
        assertEquals(0, TransactionSelectionMode.moveTransactions(mResolver, new long[] {id}, dollar, "USD"));
        assertEquals(dollar, longOf(id, Contract.Transaction.WALLET_ID));
        assertEquals(50000L, longOf(id, Contract.Transaction.MONEY));
    }

    @Test
    public void aCategoryChangeWritesTheCategoryAndTheDirectionOfIt() {
        long cash = insertWallet("Cash", "EUR");
        long id = insertTransaction(cash, insertCategory(Contract.CategoryType.EXPENSE), 1250L);
        long salary = insertCategory(Contract.CategoryType.INCOME);
        assertEquals(0, TransactionSelectionMode.changeCategory(mResolver, new long[] {id},
                new Category(salary, "Salary", null, Contract.CategoryType.INCOME)));
        assertEquals(salary, longOf(id, Contract.Transaction.CATEGORY_ID));
        assertEquals(Contract.Direction.INCOME, longOf(id, Contract.Transaction.DIRECTION));
        assertEquals(1250L, longOf(id, Contract.Transaction.MONEY));
        assertEquals(cash, longOf(id, Contract.Transaction.WALLET_ID));
    }

    @Test
    public void transferDebtAndSavingRowsAreNotMovedAndAreCounted() {
        long cash = insertWallet("Cash", "EUR");
        long bank = insertWallet("Bank", "EUR");
        long category = insertCategory(Contract.CategoryType.EXPENSE);
        long[] others = insertTransferDebtAndSaving(cash, bank);
        long plain = insertTransaction(cash, category, 1250L);
        long already = insertTransaction(bank, category, 1250L);
        long[] ids = new long[] {others[0], others[1], others[2], plain, already};
        assertEquals(3, TransactionSelectionMode.moveTransactions(mResolver, ids, bank, "EUR"));
        assertEquals(cash, longOf(others[0], Contract.Transaction.WALLET_ID));
        assertEquals(cash, longOf(others[1], Contract.Transaction.WALLET_ID));
        assertEquals(cash, longOf(others[2], Contract.Transaction.WALLET_ID));
        assertEquals(bank, longOf(plain, Contract.Transaction.WALLET_ID));
    }

    @Test
    public void transferDebtAndSavingRowsKeepTheirCategoryAndAreCounted() {
        long cash = insertWallet("Cash", "EUR");
        long bank = insertWallet("Bank", "EUR");
        long[] others = insertTransferDebtAndSaving(cash, bank);
        long plain = insertTransaction(cash, insertCategory(Contract.CategoryType.EXPENSE), 1250L);
        long target = insertCategory(Contract.CategoryType.EXPENSE);
        long debtCategory = longOf(others[1], Contract.Transaction.CATEGORY_ID);
        long savingCategory = longOf(others[2], Contract.Transaction.CATEGORY_ID);
        long[] ids = new long[] {others[0], others[1], others[2], plain};
        assertEquals(3, TransactionSelectionMode.changeCategory(mResolver, ids,
                new Category(target, "Groceries", null, Contract.CategoryType.EXPENSE)));
        assertEquals(debtCategory, longOf(others[1], Contract.Transaction.CATEGORY_ID));
        assertEquals(savingCategory, longOf(others[2], Contract.Transaction.CATEGORY_ID));
        assertEquals(target, longOf(plain, Contract.Transaction.CATEGORY_ID));
    }

    @Test
    public void aRefusedMoveIsCountedAndTheRowsAfterItStillMove() {
        RefusingDataContentProvider provider = Robolectric.setupContentProvider(
                RefusingDataContentProvider.class, RefusingDataContentProvider.authority());
        long cash = insertWallet("Cash", "EUR");
        long bank = insertWallet("Bank", "EUR");
        long category = insertCategory(Contract.CategoryType.EXPENSE);
        long before = insertTransaction(cash, category, 1250L);
        long refused = insertTransaction(cash, category, 1250L);
        long after = insertTransaction(cash, category, 1250L);
        RefusingDataContentProvider.sRefusedId = refused;
        try {
            assertEquals(1, TransactionSelectionMode.moveTransactions(mResolver, new long[] {before, refused, after}, bank, "EUR"));
        } finally {
            RefusingDataContentProvider.sRefusedId = 0L;
            provider.shutdown();
        }
        assertEquals(bank, longOf(before, Contract.Transaction.WALLET_ID));
        assertEquals(cash, longOf(refused, Contract.Transaction.WALLET_ID));
        assertEquals(bank, longOf(after, Contract.Transaction.WALLET_ID));
    }

    @Test
    public void theMoveMessageCountsOnlyRowsThatMoveAndNamesTheOtherCurrencyOnes() {
        Resources resources = ApplicationProvider.getApplicationContext().getResources();
        long cash = insertWallet("Cash", "EUR");
        long dollar = insertWallet("Dollar", "USD");
        long bank = insertWallet("Bank", "EUR");
        long category = insertCategory(Contract.CategoryType.EXPENSE);
        long euroRow = insertTransaction(cash, category, 1250L);
        long dollarRow = insertTransaction(dollar, category, 1250L);
        long[] others = insertTransferDebtAndSaving(cash, dollar);
        long already = insertTransaction(bank, category, 1250L);
        insertTransaction(dollar, category, 1250L);
        assertEquals("Move 2 transactions to Bank? 1 of them is in another currency. Its amount will be kept as shown, not converted.",
                TransactionSelectionMode.buildMoveMessage(resources, mResolver,
                        new long[] {euroRow, dollarRow, others[0], others[1], others[2], already}, bank, "Bank", "EUR"));
        assertEquals("Move 1 transaction to Bank?",
                TransactionSelectionMode.buildMoveMessage(resources, mResolver,
                        new long[] {euroRow, others[1], already}, bank, "Bank", "EUR"));
        assertEquals("Move 0 transactions to Bank?",
                TransactionSelectionMode.buildMoveMessage(resources, mResolver,
                        new long[] {euroRow, dollarRow}, bank, "Bank", null));
    }

    @Test
    public void theCategoryMessageCountsOnlyRowsThatChange() {
        Resources resources = ApplicationProvider.getApplicationContext().getResources();
        long cash = insertWallet("Cash", "EUR");
        long food = insertCategory(Contract.CategoryType.EXPENSE);
        long salary = insertCategory(Contract.CategoryType.INCOME);
        long first = insertTransaction(cash, food, 1250L);
        long second = insertTransaction(cash, food, 1250L);
        long already = insertTransaction(cash, salary, 1250L);
        long[] others = insertTransferDebtAndSaving(cash, insertWallet("Bank", "EUR"));
        insertTransaction(cash, food, 1250L);
        assertEquals("Change the category of 2 transactions to Salary?",
                TransactionSelectionMode.buildCategoryMessage(resources, mResolver,
                        new long[] {first, second, already, others[0], others[1], others[2]}, salary, "Salary"));
    }

    @Test
    public void aSnapshotMatchesOnlyTheSameNonEmptySelectionInAnyOrder() {
        assertTrue(TransactionSelectionMode.isSameSelection(new long[] {3L, 1L, 2L}, new long[] {1L, 2L, 3L}));
        assertFalse(TransactionSelectionMode.isSameSelection(new long[] {1L, 2L, 4L}, new long[] {1L, 2L, 3L}));
        assertFalse(TransactionSelectionMode.isSameSelection(new long[] {1L, 2L}, new long[] {1L, 2L, 3L}));
        assertFalse(TransactionSelectionMode.isSameSelection(new long[] {}, new long[] {1L}));
        assertFalse(TransactionSelectionMode.isSameSelection(new long[] {}, new long[] {}));
    }

    private long[] insertTransferDebtAndSaving(long fromWalletId, long toWalletId) {
        insertTransfer(fromWalletId, toWalletId);
        long leg = idOfType(Contract.TransactionType.TRANSFER);
        insertDebt(fromWalletId);
        long debt = idOfType(Contract.TransactionType.DEBT);
        long saving = insertSavingTransaction(fromWalletId);
        return new long[] {leg, debt, saving};
    }

    private boolean rowExists(long id) {
        try (Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS,
                new String[] {Contract.Transaction.ID}, Contract.Transaction.ID + " = " + id, null, null)) {
            return cursor != null && cursor.getCount() > 0;
        }
    }

    private long longOf(long id, String column) {
        try (Cursor cursor = mResolver.query(ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, id),
                new String[] {column}, null, null, null)) {
            assertTrue("no row " + id, cursor != null && cursor.moveToFirst());
            return cursor.getLong(0);
        }
    }

    private long idOfType(int type) {
        try (Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS,
                new String[] {Contract.Transaction.ID},
                Contract.Transaction.TYPE + " = " + type, null, null)) {
            assertTrue("no row of type " + type, cursor != null && cursor.moveToFirst());
            return cursor.getLong(0);
        }
    }

    private long insertWallet(String name, String currency) {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, name);
        values.put(Contract.Wallet.ICON, ICON);
        values.put(Contract.Wallet.CURRENCY, currency);
        values.put(Contract.Wallet.START_MONEY, 0L);
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        values.put(Contract.Wallet.ARCHIVED, false);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_WALLETS, values));
    }

    private long insertCategory(Contract.CategoryType type) {
        ContentValues values = new ContentValues();
        values.put(Contract.Category.NAME, "Groceries");
        values.put(Contract.Category.ICON, ICON);
        values.put(Contract.Category.TYPE, type.getValue());
        values.put(Contract.Category.SHOW_REPORT, true);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, values));
    }

    private long insertTransaction(long walletId, long categoryId, long money) {
        ContentValues values = new ContentValues();
        values.put(Contract.Transaction.MONEY, money);
        values.put(Contract.Transaction.DATE, DateUtils.getSQLDateTimeString(new Date()));
        values.put(Contract.Transaction.DESCRIPTION, "Bread");
        values.put(Contract.Transaction.CATEGORY_ID, categoryId);
        values.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE);
        values.put(Contract.Transaction.TYPE, Contract.TransactionType.STANDARD);
        values.put(Contract.Transaction.WALLET_ID, walletId);
        values.put(Contract.Transaction.CONFIRMED, true);
        values.put(Contract.Transaction.COUNT_IN_TOTAL, true);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values));
    }

    private long insertSavingTransaction(long walletId) {
        ContentValues saving = new ContentValues();
        saving.put(Contract.Saving.DESCRIPTION, "Trip");
        saving.put(Contract.Saving.ICON, ICON);
        saving.put(Contract.Saving.START_MONEY, 0L);
        saving.put(Contract.Saving.END_MONEY, 10000L);
        saving.put(Contract.Saving.WALLET_ID, walletId);
        saving.put(Contract.Saving.COMPLETE, false);
        long savingId = ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_SAVINGS, saving));
        ContentValues values = new ContentValues();
        values.put(Contract.Transaction.MONEY, 300L);
        values.put(Contract.Transaction.DATE, DateUtils.getSQLDateTimeString(new Date()));
        values.put(Contract.Transaction.DESCRIPTION, "Deposit");
        values.put(Contract.Transaction.CATEGORY_ID, insertCategory(Contract.CategoryType.EXPENSE));
        values.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE);
        values.put(Contract.Transaction.TYPE, Contract.TransactionType.SAVING);
        values.put(Contract.Transaction.WALLET_ID, walletId);
        values.put(Contract.Transaction.SAVING_ID, savingId);
        values.put(Contract.Transaction.CONFIRMED, true);
        values.put(Contract.Transaction.COUNT_IN_TOTAL, true);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values));
    }

    private void insertDebt(long walletId) {
        ContentValues values = new ContentValues();
        values.put(Contract.Debt.TYPE, Contract.DebtType.DEBT.getValue());
        values.put(Contract.Debt.ICON, ICON);
        values.put(Contract.Debt.DESCRIPTION, "Loan");
        values.put(Contract.Debt.DATE, DateUtils.getSQLDateString(new Date()));
        values.put(Contract.Debt.WALLET_ID, walletId);
        values.put(Contract.Debt.MONEY, 20000L);
        values.put(Contract.Debt.ARCHIVED, false);
        values.put(Contract.Debt.INSERT_MASTER_TRANSACTION, true);
        mResolver.insert(DataContentProvider.CONTENT_DEBTS, values);
    }

    private void insertTransfer(long fromWalletId, long toWalletId) {
        ContentValues values = new TransferContentValuesBuilder()
                .description("Move")
                .date(DateUtils.getSQLDateTimeString(new Date()))
                .fromWalletId(fromWalletId)
                .toWalletId(toWalletId)
                .taxWalletId(fromWalletId)
                .fromMoney(500L)
                .toMoney(500L)
                .taxMoney(0L)
                .note("")
                .confirmed(true)
                .countInTotal(true)
                .build();
        mResolver.insert(DataContentProvider.CONTENT_TRANSFERS, values);
    }
}
