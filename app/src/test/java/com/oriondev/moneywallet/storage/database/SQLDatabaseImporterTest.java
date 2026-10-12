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
import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.database.model.Category;
import com.oriondev.moneywallet.storage.database.model.Transaction;
import com.oriondev.moneywallet.storage.database.model.Wallet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SQLDatabaseImporterTest {

    @Test
    public void aTransactionOfTypeModelIsRestoredAsAPlainOne() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        ContentResolver resolver = context.getContentResolver();

        Wallet wallet = new Wallet();
        wallet.mName = "Cash";
        wallet.mCurrency = "EUR";
        wallet.mUUID = "wallet-1";
        long walletId = SQLDatabaseImporter.insert(resolver, wallet);
        Category category = new Category();
        category.mName = "Food";
        category.mIcon = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"F\"}";
        category.mType = Schema.CategoryType.EXPENSE;
        category.mUUID = "category-1";
        long categoryId = SQLDatabaseImporter.insert(resolver, category);

        long model = SQLDatabaseImporter.insert(resolver,
                transaction("model", Contract.TransactionType.MODEL, walletId, categoryId));
        long debt = SQLDatabaseImporter.insert(resolver,
                transaction("debt", Contract.TransactionType.DEBT, walletId, categoryId));

        assertEquals(Contract.TransactionType.STANDARD, typeOf(resolver, model));
        assertEquals(Contract.TransactionType.DEBT, typeOf(resolver, debt));
    }

    private static Transaction transaction(String uuid, int type, long walletId, long categoryId) {
        Transaction transaction = new Transaction();
        transaction.mMoney = 2500L;
        transaction.mDate = "2026-09-01 12:00:00";
        transaction.mCategory = categoryId;
        transaction.mDirection = Contract.Direction.EXPENSE;
        transaction.mType = type;
        transaction.mWallet = walletId;
        transaction.mUUID = uuid;
        return transaction;
    }

    private static int typeOf(ContentResolver resolver, long transactionId) {
        try (Cursor cursor = resolver.query(SyncContentProvider.CONTENT_TRANSACTION,
                new String[] {Schema.Transaction.TYPE}, Schema.Transaction.ID + " = ?",
                new String[] {String.valueOf(transactionId)}, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }
}
