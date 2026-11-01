/*
 * Copyright (c) 2018.
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

package com.oriondev.moneywallet.utils;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;

/**
 * The resolver behind #126. The wallet chosen in settings wins whenever it is usable, then the
 * wallet you are viewing, then the Total view. isUsable queries the collection uri, so an
 * archived or deleted default is refused and the fallback fires; the archived cases below fail
 * if that query is ever moved to the item uri.
 */
@RunWith(RobolectricTestRunner.class)
public class DefaultWalletTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private Context mContext;
    private ContentResolver mResolver;
    private long mCash;
    private long mBank;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        PreferenceManager.initialize(mContext);
        TestDatabases.useFreshDatabase(mContext);
        mResolver = mContext.getContentResolver();
        mCash = insertWallet("Cash", false);
        mBank = insertWallet("Bank", false);
    }

    @Test
    public void theConfiguredDefaultWinsOverTheViewedWallet() {
        PreferenceManager.setCurrentWallet(mContext, mCash);
        PreferenceManager.setDefaultWallet(mBank);
        assertEquals(mBank, DefaultWallet.resolveNewItemWallet(mResolver));
    }

    @Test
    public void theViewedWalletIsUsedWithNoDefault() {
        PreferenceManager.setCurrentWallet(mContext, mCash);
        assertEquals(mCash, DefaultWallet.resolveNewItemWallet(mResolver));
    }

    @Test
    public void anArchivedDefaultFallsBackToTheViewedWallet() {
        long archived = insertWallet("Old", true);
        PreferenceManager.setCurrentWallet(mContext, mCash);
        PreferenceManager.setDefaultWallet(archived);
        assertEquals(mCash, DefaultWallet.resolveNewItemWallet(mResolver));
    }

    @Test
    public void aDeletedDefaultFallsBackToTheViewedWallet() {
        PreferenceManager.setCurrentWallet(mContext, mCash);
        PreferenceManager.setDefaultWallet(mBank);
        deleteWallet(mBank);
        assertEquals(mCash, DefaultWallet.resolveNewItemWallet(mResolver));
    }

    @Test
    public void aDefaultThatDoesNotExistFallsBackToTheViewedWallet() {
        PreferenceManager.setCurrentWallet(mContext, mCash);
        PreferenceManager.setDefaultWallet(9999L);
        assertEquals(mCash, DefaultWallet.resolveNewItemWallet(mResolver));
    }

    @Test
    public void theConfiguredDefaultAppliesOnTheTotalView() {
        PreferenceManager.setCurrentWallet(mContext, PreferenceManager.TOTAL_WALLET_ID);
        PreferenceManager.setDefaultWallet(mBank);
        assertEquals(mBank, DefaultWallet.resolveNewItemWallet(mResolver));
    }

    @Test
    public void theTotalViewFallsBackToTotalWithNoDefault() {
        PreferenceManager.setCurrentWallet(mContext, PreferenceManager.TOTAL_WALLET_ID);
        assertEquals(PreferenceManager.TOTAL_WALLET_ID, DefaultWallet.resolveNewItemWallet(mResolver));
    }

    @Test
    public void theTotalViewFallsBackWhenTheDefaultIsArchived() {
        long archived = insertWallet("Old", true);
        PreferenceManager.setCurrentWallet(mContext, PreferenceManager.TOTAL_WALLET_ID);
        PreferenceManager.setDefaultWallet(archived);
        assertEquals(PreferenceManager.TOTAL_WALLET_ID, DefaultWallet.resolveNewItemWallet(mResolver));
    }

    @Test
    public void theTotalViewFallsBackWhenTheDefaultIsDeleted() {
        PreferenceManager.setCurrentWallet(mContext, PreferenceManager.TOTAL_WALLET_ID);
        PreferenceManager.setDefaultWallet(mBank);
        deleteWallet(mBank);
        assertEquals(PreferenceManager.TOTAL_WALLET_ID, DefaultWallet.resolveNewItemWallet(mResolver));
    }

    @Test
    public void theTotalViewFallsBackWhenTheDefaultDoesNotExist() {
        PreferenceManager.setCurrentWallet(mContext, PreferenceManager.TOTAL_WALLET_ID);
        PreferenceManager.setDefaultWallet(9999L);
        assertEquals(PreferenceManager.TOTAL_WALLET_ID, DefaultWallet.resolveNewItemWallet(mResolver));
    }

    @Test
    public void anArchivedViewedWalletWithNoDefaultFallsBackToTotal() {
        long archived = insertWallet("Old", true);
        PreferenceManager.setCurrentWallet(mContext, archived);
        assertEquals(PreferenceManager.TOTAL_WALLET_ID, DefaultWallet.resolveNewItemWallet(mResolver));
    }

    @Test
    public void anArchivedViewedWalletWithAnArchivedDefaultFallsBackToTotal() {
        long archived = insertWallet("Old", true);
        PreferenceManager.setCurrentWallet(mContext, archived);
        PreferenceManager.setDefaultWallet(insertWallet("Older", true));
        assertEquals(PreferenceManager.TOTAL_WALLET_ID, DefaultWallet.resolveNewItemWallet(mResolver));
    }

    @Test
    public void noCurrentWalletUsesTheDefaultWhenSet() {
        PreferenceManager.setDefaultWallet(mBank);
        assertEquals(PreferenceManager.NO_CURRENT_WALLET, PreferenceManager.getCurrentWallet());
        assertEquals(mBank, DefaultWallet.resolveNewItemWallet(mResolver));
    }

    @Test
    public void noCurrentWalletAndNoDefaultFallsBackToTotal() {
        assertEquals(PreferenceManager.NO_CURRENT_WALLET, PreferenceManager.getCurrentWallet());
        assertEquals(PreferenceManager.TOTAL_WALLET_ID, DefaultWallet.resolveNewItemWallet(mResolver));
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

    private void deleteWallet(long walletId) {
        Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, walletId);
        mResolver.delete(uri, null, null);
    }
}
