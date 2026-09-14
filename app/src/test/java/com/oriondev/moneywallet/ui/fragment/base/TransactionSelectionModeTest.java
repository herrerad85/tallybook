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
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.database.TransferContentValuesBuilder;
import com.oriondev.moneywallet.utils.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;
import java.util.Date;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The bulk delete loop against the real provider and database, where a transfer leg is refused
 * by SQLDatabase.deleteTransaction itself.
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
        long cash = insertWallet("Cash");
        long bank = insertWallet("Bank");
        long category = insertCategory();
        long before = insertTransaction(cash, category);
        insertTransfer(cash, bank);
        long leg = transferLegId();
        long after = insertTransaction(cash, category);
        Set<Long> refused = TransactionSelectionMode.deleteTransactions(mResolver, new long[] {before, leg, after});
        assertEquals(Collections.singleton(leg), refused);
        assertFalse(rowExists(before));
        assertTrue(rowExists(leg));
        assertFalse(rowExists(after));
    }

    private boolean rowExists(long id) {
        try (Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS,
                new String[] {Contract.Transaction.ID}, Contract.Transaction.ID + " = " + id, null, null)) {
            return cursor != null && cursor.getCount() > 0;
        }
    }

    private long transferLegId() {
        try (Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS,
                new String[] {Contract.Transaction.ID},
                Contract.Transaction.TYPE + " = " + Contract.TransactionType.TRANSFER, null, null)) {
            assertTrue("the transfer wrote no leg", cursor != null && cursor.moveToFirst());
            return cursor.getLong(0);
        }
    }

    private long insertWallet(String name) {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, name);
        values.put(Contract.Wallet.ICON, ICON);
        values.put(Contract.Wallet.CURRENCY, "EUR");
        values.put(Contract.Wallet.START_MONEY, 0L);
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        values.put(Contract.Wallet.ARCHIVED, false);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_WALLETS, values));
    }

    private long insertCategory() {
        ContentValues values = new ContentValues();
        values.put(Contract.Category.NAME, "Groceries");
        values.put(Contract.Category.ICON, ICON);
        values.put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.getValue());
        values.put(Contract.Category.SHOW_REPORT, true);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, values));
    }

    private long insertTransaction(long walletId, long categoryId) {
        ContentValues values = new ContentValues();
        values.put(Contract.Transaction.MONEY, 1250L);
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
