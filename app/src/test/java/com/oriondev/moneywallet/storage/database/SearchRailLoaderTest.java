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

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.background.SearchPeopleLoader;
import com.oriondev.moneywallet.background.SearchRailLoader;
import com.oriondev.moneywallet.ui.activity.NewEditTransactionActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The People counts and the checks behind the rail's hiding rules, over rows written through the
 * provider. Every expected figure is counted from what the rows mean. This lives here for the raw
 * writes that mark a link deleted, which only a sync or a restore makes.
 */
@RunWith(RobolectricTestRunner.class)
public class SearchRailLoaderTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private Context mContext;
    private ContentResolver mResolver;
    private long mCategory;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(mContext);
        mResolver = mContext.getContentResolver();
        ContentValues category = new ContentValues();
        category.put(Contract.Category.NAME, "Misc");
        category.put(Contract.Category.ICON, ICON);
        category.put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.getValue());
        category.put(Contract.Category.SHOW_REPORT, true);
        mCategory = ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, category));
    }

    @Test
    public void aPersonCountsTheirTransactionAndEverySideOfTheirTaxedTransfer() {
        long euro = insertWallet("Euro", false);
        long savings = insertWallet("Savings", false);
        long alice = insertPerson("Alice");
        insertTransaction(euro, "<" + alice + ">", true);
        insertTransaction(euro, null, true);
        // from, to and tax: three rows
        insertTransfer(euro, savings, 50L, "<" + alice + ">");
        assertEquals(4, counts().get("Alice").intValue());
    }

    @Test
    public void anUntaxedTransferCountsItsTwoSides() {
        long euro = insertWallet("Euro", false);
        long savings = insertWallet("Savings", false);
        long bob = insertPerson("Bob");
        insertTransfer(euro, savings, 0L, "<" + bob + ">");
        assertEquals(2, counts().get("Bob").intValue());
    }

    @Test
    public void aDeletedTransactionLinkIsNotCounted() {
        long euro = insertWallet("Euro", false);
        long alice = insertPerson("Alice");
        insertTransaction(euro, "<" + alice + ">", true);
        long unlinked = insertTransaction(euro, "<" + alice + ">", true);
        execSQL("UPDATE " + Schema.TransactionPeople.TABLE
                + " SET " + Schema.TransactionPeople.DELETED + " = 1 WHERE " + Schema.TransactionPeople.TRANSACTION
                + " = " + unlinked);
        assertEquals(1, counts().get("Alice").intValue());
    }

    @Test
    public void aDeletedTransferLinkAndADeletedTransferAreNotCounted() {
        long euro = insertWallet("Euro", false);
        long savings = insertWallet("Savings", false);
        long alice = insertPerson("Alice");
        long unlinked = insertTransfer(euro, savings, 0L, "<" + alice + ">");
        long deleted = insertTransfer(euro, savings, 0L, "<" + alice + ">");
        insertTransfer(euro, savings, 0L, "<" + alice + ">");
        execSQL("UPDATE " + Schema.TransferPeople.TABLE + " SET " + Schema.TransferPeople.DELETED
                + " = 1 WHERE " + Schema.TransferPeople.TRANSFER + " = " + unlinked);
        execSQL("UPDATE " + Schema.Transfer.TABLE + " SET " + Schema.Transfer.DELETED
                + " = 1 WHERE " + Schema.Transfer.ID + " = " + deleted);
        // only the third transfer's two sides
        assertEquals(2, counts().get("Alice").intValue());
    }

    @Test
    public void aPersonOnNothingCountsZero() {
        insertWallet("Euro", false);
        insertPerson("Carol");
        assertEquals(0, counts().get("Carol").intValue());
    }

    @Test
    public void peopleComeInNameOrder() {
        insertPerson("Carol");
        insertPerson("Alice");
        insertPerson("Bob");
        List<String> names = new ArrayList<>();
        for (SearchPeopleLoader.Person person : new SearchPeopleLoader(mContext).loadInBackground()) {
            names.add(person.getName());
        }
        assertEquals(Arrays.asList("Alice", "Bob", "Carol"), names);
        assertEquals(Arrays.asList("Alice", "Bob", "Carol"), new ArrayList<>(rail().getPeople().values()));
    }

    @Test
    public void aPersonLinkedOnlyThroughATransferKeepsPeople() {
        long euro = insertWallet("Euro", false);
        long savings = insertWallet("Savings", false);
        long alice = insertPerson("Alice");
        insertTransaction(euro, null, true);
        insertTransfer(euro, savings, 0L, "<" + alice + ">");
        assertTrue(rail().hasPersonLinks());
    }

    @Test
    public void aPersonOnATransactionKeepsPeople() {
        long euro = insertWallet("Euro", false);
        long alice = insertPerson("Alice");
        insertTransaction(euro, "<" + alice + ">", true);
        assertTrue(rail().hasPersonLinks());
    }

    @Test
    public void peopleOnNothingRuleOutPeople() {
        long euro = insertWallet("Euro", false);
        insertPerson("Alice");
        insertTransaction(euro, null, true);
        assertFalse(rail().hasPersonLinks());
    }

    @Test
    public void noPeopleAtAllRuleOutPeople() {
        long euro = insertWallet("Euro", false);
        insertTransaction(euro, null, true);
        assertFalse(rail().hasPersonLinks());
    }

    @Test
    public void onlyDeletedLinksRuleOutPeople() {
        long euro = insertWallet("Euro", false);
        long savings = insertWallet("Savings", false);
        long alice = insertPerson("Alice");
        long transaction = insertTransaction(euro, "<" + alice + ">", true);
        long transfer = insertTransfer(euro, savings, 0L, "<" + alice + ">");
        execSQL("UPDATE " + Schema.TransactionPeople.TABLE + " SET " + Schema.TransactionPeople.DELETED
                + " = 1 WHERE " + Schema.TransactionPeople.TRANSACTION + " = " + transaction);
        execSQL("UPDATE " + Schema.TransferPeople.TABLE + " SET " + Schema.TransferPeople.DELETED
                + " = 1 WHERE " + Schema.TransferPeople.TRANSFER + " = " + transfer);
        assertFalse(rail().hasPersonLinks());
    }

    @Test
    public void oneWalletAndNoTransferRuleOutWallet() {
        long euro = insertWallet("Euro", false);
        insertTransaction(euro, null, true);
        assertFalse(rail().hasWalletChoice());
    }

    @Test
    public void aSecondWalletKeepsWallet() {
        long euro = insertWallet("Euro", false);
        insertWallet("Old", true);
        insertTransaction(euro, null, true);
        assertTrue(rail().hasWalletChoice());
    }

    @Test
    public void oneWalletWithATransferKeepsWallet() {
        long euro = insertWallet("Euro", false);
        insertTransfer(euro, euro, 0L, null);
        assertTrue(rail().hasWalletChoice());
    }

    @Test
    public void walletsComeInEditorOrderArchivedLast() {
        insertWallet("Attic", true);
        insertWallet("Euro", false);
        insertWallet("Cash", false);
        assertEquals(Arrays.asList("Cash", "Euro", "Attic"), new ArrayList<>(rail().getWallets().values()));
    }

    @Test
    public void noUnconfirmedRowRulesOutStatus() {
        long euro = insertWallet("Euro", false);
        insertTransaction(euro, null, true);
        assertFalse(rail().hasUnconfirmed());
    }

    @Test
    public void anUnconfirmedRowKeepsStatus() {
        long euro = insertWallet("Euro", false);
        insertTransaction(euro, null, true);
        insertTransaction(euro, null, false);
        assertTrue(rail().hasUnconfirmed());
    }

    private Map<String, Integer> counts() {
        Map<String, Integer> counts = new HashMap<>();
        for (SearchPeopleLoader.Person person : new SearchPeopleLoader(mContext).loadInBackground()) {
            counts.put(person.getName(), person.getCount());
        }
        return counts;
    }

    private SearchRailLoader.Result rail() {
        return new SearchRailLoader(mContext).loadInBackground();
    }

    private void execSQL(String sql) {
        SQLDatabase.getShared(mContext).getWritableDatabase().execSQL(sql);
    }

    private long insertWallet(String name, boolean archived) {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, name);
        values.put(Contract.Wallet.ICON, ICON);
        values.put(Contract.Wallet.CURRENCY, "EUR");
        values.put(Contract.Wallet.START_MONEY, 0L);
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        values.put(Contract.Wallet.ARCHIVED, archived);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_WALLETS, values));
    }

    private long insertPerson(String name) {
        ContentValues values = new ContentValues();
        values.put(Contract.Person.NAME, name);
        values.put(Contract.Person.ICON, ICON);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_PEOPLE, values));
    }

    private long insertTransaction(long wallet, String people, boolean confirmed) {
        ContentValues values = new ContentValues();
        values.put(Contract.Transaction.MONEY, 200L);
        values.put(Contract.Transaction.DATE, "2026-01-15 12:00:00");
        values.put(Contract.Transaction.DESCRIPTION, "Row");
        values.put(Contract.Transaction.CATEGORY_ID, mCategory);
        values.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE);
        values.put(Contract.Transaction.TYPE, NewEditTransactionActivity.TYPE_STANDARD);
        values.put(Contract.Transaction.WALLET_ID, wallet);
        values.put(Contract.Transaction.CONFIRMED, confirmed);
        values.put(Contract.Transaction.COUNT_IN_TOTAL, true);
        values.put(Contract.Transaction.PEOPLE_IDS, people);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values));
    }

    /** A tax above zero writes a third row, in the from wallet. */
    private long insertTransfer(long from, long to, long tax, String people) {
        ContentValues values = new ContentValues();
        values.put(Contract.Transfer.DESCRIPTION, "Moved");
        values.put(Contract.Transfer.DATE, "2026-01-15 12:00:00");
        values.put(Contract.Transfer.TRANSACTION_FROM_WALLET_ID, from);
        values.put(Contract.Transfer.TRANSACTION_FROM_MONEY, 1000L);
        values.put(Contract.Transfer.TRANSACTION_TO_WALLET_ID, to);
        values.put(Contract.Transfer.TRANSACTION_TO_MONEY, 1000L);
        values.put(Contract.Transfer.TRANSACTION_TAX_WALLET_ID, from);
        values.put(Contract.Transfer.TRANSACTION_TAX_MONEY, tax);
        values.put(Contract.Transfer.CONFIRMED, true);
        values.put(Contract.Transfer.COUNT_IN_TOTAL, true);
        values.put(Contract.Transfer.PEOPLE_IDS, people);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_TRANSFERS, values));
    }
}
