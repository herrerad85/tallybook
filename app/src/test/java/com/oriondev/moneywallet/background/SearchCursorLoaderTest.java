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

package com.oriondev.moneywallet.background;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.model.SearchFilter;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.ui.activity.NewEditTransactionActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The count and totals the search loader hands the strip, over rows inserted through the
 * provider. Every expected figure is written from what the rows mean.
 */
@RunWith(RobolectricTestRunner.class)
public class SearchCursorLoaderTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private Context mContext;
    private ContentResolver mResolver;
    private long mCategory;
    private Cursor mCursor;

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

    @After
    public void tearDown() {
        if (mCursor != null) {
            mCursor.close();
        }
    }

    @Test
    public void oneCurrencySumsOutAndInSeparately() {
        long euro = insertWallet("Euro", "EUR", false);
        insertTransaction(euro, Contract.Direction.EXPENSE, 1250L, "Groceries");
        insertTransaction(euro, Contract.Direction.EXPENSE, 300L, "Bus");
        insertTransaction(euro, Contract.Direction.INCOME, 5000L, "Salary");
        SearchCursorLoader.Summary summary = load(new SearchFilter());
        assertEquals(3, summary.getMatchCount());
        assertEquals("EUR", summary.getCurrency());
        assertEquals(Long.valueOf(1550L), summary.getOut());
        assertEquals(Long.valueOf(5000L), summary.getIn());
    }

    @Test
    public void twoCurrenciesKeepTheCountAndDropTheTotals() {
        long euro = insertWallet("Euro", "EUR", false);
        long yen = insertWallet("Yen", "JPY", false);
        insertTransaction(euro, Contract.Direction.EXPENSE, 1250L, "Groceries");
        insertTransaction(yen, Contract.Direction.EXPENSE, 800L, "Ramen");
        insertTransaction(euro, Contract.Direction.INCOME, 5000L, "Salary");
        SearchCursorLoader.Summary summary = load(new SearchFilter());
        assertEquals(3, summary.getMatchCount());
        assertNull(summary.getCurrency());
        assertNull(summary.getOut());
        assertNull(summary.getIn());
    }

    @Test
    public void outOnlyLeavesInUnset() {
        long euro = insertWallet("Euro", "EUR", false);
        insertTransaction(euro, Contract.Direction.EXPENSE, 200L, "Coffee");
        insertTransaction(euro, Contract.Direction.EXPENSE, 350L, "Lunch");
        SearchCursorLoader.Summary summary = load(new SearchFilter());
        assertEquals(2, summary.getMatchCount());
        assertEquals("EUR", summary.getCurrency());
        assertEquals(Long.valueOf(550L), summary.getOut());
        assertNull(summary.getIn());
    }

    @Test
    public void inOnlyLeavesOutUnset() {
        long euro = insertWallet("Euro", "EUR", false);
        insertTransaction(euro, Contract.Direction.INCOME, 5000L, "Salary");
        insertTransaction(euro, Contract.Direction.INCOME, 700L, "Refund");
        SearchCursorLoader.Summary summary = load(new SearchFilter());
        assertEquals(2, summary.getMatchCount());
        assertEquals("EUR", summary.getCurrency());
        assertNull(summary.getOut());
        assertEquals(Long.valueOf(5700L), summary.getIn());
    }

    @Test
    public void anInSideWhoseRowsSumToZeroStillShowsItsTotal() {
        long euro = insertWallet("Euro", "EUR", false);
        insertTransaction(euro, Contract.Direction.EXPENSE, 200L, "Coffee");
        insertTransaction(euro, Contract.Direction.INCOME, 0L, "Free sample");
        SearchCursorLoader.Summary summary = load(new SearchFilter());
        assertEquals(2, summary.getMatchCount());
        assertEquals(Long.valueOf(200L), summary.getOut());
        // a row of that side matched, so its zero shows, unlike a side with no row at all
        assertEquals(Long.valueOf(0L), summary.getIn());
    }

    @Test
    public void anOutSideWhoseRowsSumToZeroStillShowsItsTotal() {
        long euro = insertWallet("Euro", "EUR", false);
        insertTransaction(euro, Contract.Direction.EXPENSE, 0L, "Free sample");
        insertTransaction(euro, Contract.Direction.INCOME, 5000L, "Salary");
        SearchCursorLoader.Summary summary = load(new SearchFilter());
        assertEquals(2, summary.getMatchCount());
        assertEquals(Long.valueOf(0L), summary.getOut());
        assertEquals(Long.valueOf(5000L), summary.getIn());
    }

    @Test
    public void anEmptyResultHasNoCurrencyAndNoTotals() {
        long euro = insertWallet("Euro", "EUR", false);
        insertTransaction(euro, Contract.Direction.EXPENSE, 200L, "Coffee");
        SearchFilter filter = new SearchFilter();
        filter.setText("nothing like it");
        SearchCursorLoader.Summary summary = load(filter);
        assertEquals(0, summary.getMatchCount());
        assertNull(summary.getCurrency());
        assertNull(summary.getOut());
        assertNull(summary.getIn());
    }

    @Test
    public void sumsBeyondTheIntRangeStayExact() {
        long euro = insertWallet("Euro", "EUR", false);
        insertTransaction(euro, Contract.Direction.EXPENSE, 2000000000L, "Plane");
        insertTransaction(euro, Contract.Direction.EXPENSE, 2000000000L, "Boat");
        insertTransaction(euro, Contract.Direction.INCOME, 3000000000L, "Sale");
        SearchCursorLoader.Summary summary = load(new SearchFilter());
        assertEquals(Long.valueOf(4000000000L), summary.getOut());
        assertEquals(Long.valueOf(3000000000L), summary.getIn());
    }

    @Test
    public void theTextSlotNarrowsTheCountAndTheTotals() {
        long euro = insertWallet("Euro", "EUR", false);
        insertTransaction(euro, Contract.Direction.EXPENSE, 200L, "Morning coffee");
        insertTransaction(euro, Contract.Direction.EXPENSE, 90000L, "Rent");
        insertTransaction(euro, Contract.Direction.INCOME, 150L, "Coffee refund");
        SearchFilter filter = new SearchFilter();
        filter.setText("COFFEE");
        SearchCursorLoader.Summary summary = load(filter);
        assertEquals(2, summary.getMatchCount());
        assertEquals(Long.valueOf(200L), summary.getOut());
        assertEquals(Long.valueOf(150L), summary.getIn());
    }

    @Test
    public void aChangeToTheFilterAfterTheLoaderIsBuiltDoesNotReachIt() {
        long euro = insertWallet("Euro", "EUR", false);
        insertTransaction(euro, Contract.Direction.EXPENSE, 200L, "Coffee");
        insertTransaction(euro, Contract.Direction.EXPENSE, 900L, "Rent");
        SearchFilter filter = new SearchFilter();
        SearchCursorLoader loader = new SearchCursorLoader(mContext, filter);
        filter.setText("coffee");
        mCursor = loader.loadInBackground();
        assertEquals(2, ((SearchCursorLoader.Summary) mCursor).getMatchCount());
    }

    @Test
    public void theCursorStillReadsFromTheFirstRowAfterTheWalk() {
        long euro = insertWallet("Euro", "EUR", false);
        insertTransaction(euro, Contract.Direction.EXPENSE, 200L, "Coffee");
        load(new SearchFilter());
        assertEquals(-1, mCursor.getPosition());
        assertEquals(1, mCursor.getCount());
    }

    @Test
    public void decimalsCoverEveryWalletCurrencyArchivedIncluded() {
        insertWallet("Euro", "EUR", false);
        insertWallet("Old yen", "JPY", true);
        Map<String, Integer> expected = new HashMap<>();
        expected.put("EUR", 2);
        expected.put("JPY", 0);
        assertEquals(expected, SearchCursorLoader.loadDecimals(mContext));
    }

    private SearchCursorLoader.Summary load(SearchFilter filter) {
        mCursor = new SearchCursorLoader(mContext, filter).loadInBackground();
        return (SearchCursorLoader.Summary) mCursor;
    }

    private long insertWallet(String name, String currency, boolean archived) {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, name);
        values.put(Contract.Wallet.ICON, ICON);
        values.put(Contract.Wallet.CURRENCY, currency);
        values.put(Contract.Wallet.START_MONEY, 0L);
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        values.put(Contract.Wallet.ARCHIVED, archived);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_WALLETS, values));
    }

    private void insertTransaction(long wallet, int direction, long money, String description) {
        ContentValues values = new ContentValues();
        values.put(Contract.Transaction.MONEY, money);
        values.put(Contract.Transaction.DATE, "2026-01-15 12:00:00");
        values.put(Contract.Transaction.DESCRIPTION, description);
        values.put(Contract.Transaction.CATEGORY_ID, mCategory);
        values.put(Contract.Transaction.DIRECTION, direction);
        values.put(Contract.Transaction.TYPE, NewEditTransactionActivity.TYPE_STANDARD);
        values.put(Contract.Transaction.WALLET_ID, wallet);
        values.put(Contract.Transaction.CONFIRMED, true);
        values.put(Contract.Transaction.COUNT_IN_TOTAL, true);
        mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values);
    }
}
