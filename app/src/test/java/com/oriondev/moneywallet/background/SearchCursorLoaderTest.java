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
import com.oriondev.moneywallet.storage.wrapper.CurrencyHeaderCursor;
import com.oriondev.moneywallet.storage.wrapper.TransactionHeaderCursor;
import com.oriondev.moneywallet.ui.activity.NewEditTransactionActivity;

import org.junit.After;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void newestRunsByDateDownAndAnEqualDateByTheLaterRowFirst() {
        sortFixture();
        assertEquals(Arrays.asList("f2", "f3", "b1", "f1", "f4", "r1", "b2"), descriptions(SearchFilter.Sort.NEWEST));
    }

    @Test
    public void oldestRunsByDateUpAndAnEqualDateByTheEarlierRowFirst() {
        sortFixture();
        assertEquals(Arrays.asList("b2", "r1", "f4", "f1", "b1", "f3", "f2"), descriptions(SearchFilter.Sort.OLDEST));
    }

    @Test
    public void categoryRunsByTheRowsOwnCategoryNameThenNewestFirst() {
        sortFixture();
        // Epicerie sits under food, and sorts under its own name ahead of it
        assertEquals(Arrays.asList("b1", "b2", "f2", "f3", "f1", "f4", "r1"), descriptions(SearchFilter.Sort.CATEGORY));
    }

    @Test
    public void largestRunsByAmountDownThenNewestThenTheLaterRowFirst() {
        sortFixture();
        assertEquals(Arrays.asList("r1", "f3", "b1", "f1", "f4", "b2", "f2"), descriptions(SearchFilter.Sort.LARGEST));
    }

    @Test
    public void smallestRunsByAmountUpThenNewestThenTheLaterRowFirst() {
        sortFixture();
        assertEquals(Arrays.asList("f2", "b2", "f3", "b1", "f1", "f4", "r1"), descriptions(SearchFilter.Sort.SMALLEST));
    }

    @Test
    public void anAmountSortOnOneCurrencyAddsNoHeaderAndKeepsTheTotals() {
        long euro = insertWallet("Euro", "EUR", false);
        insertTransaction(euro, Contract.Direction.EXPENSE, 1250L, "Groceries");
        insertTransaction(euro, Contract.Direction.INCOME, 5000L, "Salary");
        for (SearchFilter.Sort sort : new SearchFilter.Sort[] {SearchFilter.Sort.LARGEST, SearchFilter.Sort.SMALLEST}) {
            SearchCursorLoader.Summary summary = load(sorted(sort));
            assertEquals(-1, mCursor.getColumnIndex(TransactionHeaderCursor.COLUMN_ITEM_TYPE));
            assertEquals(2, mCursor.getCount());
            assertEquals(2, summary.getMatchCount());
            assertEquals("EUR", summary.getCurrency());
            assertEquals(Long.valueOf(1250L), summary.getOut());
            assertEquals(Long.valueOf(5000L), summary.getIn());
            mCursor.close();
        }
        mCursor = null;
    }

    @Test
    public void largestOnTwoCurrenciesPutsAHeaderBeforeEachCurrencyInCodeOrder() {
        long yen = insertWallet("Yen", "JPY", false);
        long euro = insertWallet("Euro", "EUR", false);
        insertTransaction(yen, Contract.Direction.EXPENSE, 800L, "Ramen");
        insertTransaction(euro, Contract.Direction.EXPENSE, 1250L, "Groceries");
        insertTransaction(yen, Contract.Direction.INCOME, 90000L, "Gift");
        insertTransaction(euro, Contract.Direction.INCOME, 5000L, "Salary");
        SearchCursorLoader.Summary summary = load(sorted(SearchFilter.Sort.LARGEST));
        assertEquals(Arrays.asList("EUR", "Salary", "Groceries", "JPY", "Gift", "Ramen"), rowsAndHeaders());
        assertEquals(4, summary.getMatchCount());
        assertNull(summary.getCurrency());
        assertNull(summary.getOut());
        assertNull(summary.getIn());
    }

    @Test
    public void smallestOnThreeCurrenciesPutsAHeaderBeforeEachCurrencyInCodeOrder() {
        long usd = insertWallet("Dollar", "USD", false);
        long yen = insertWallet("Yen", "JPY", false);
        long euro = insertWallet("Euro", "EUR", false);
        insertTransaction(usd, Contract.Direction.EXPENSE, 100L, "Gum");
        insertTransaction(yen, Contract.Direction.EXPENSE, 800L, "Ramen");
        insertTransaction(euro, Contract.Direction.EXPENSE, 1250L, "Groceries");
        insertTransaction(usd, Contract.Direction.INCOME, 20L, "Change");
        insertTransaction(euro, Contract.Direction.INCOME, 5000L, "Salary");
        SearchCursorLoader.Summary summary = load(sorted(SearchFilter.Sort.SMALLEST));
        assertEquals(Arrays.asList("EUR", "Groceries", "Salary", "JPY", "Ramen", "USD", "Change", "Gum"), rowsAndHeaders());
        assertEquals(5, summary.getMatchCount());
        assertEquals(8, mCursor.getCount());
        assertNull(summary.getOut());
        assertNull(summary.getIn());
    }

    @Test
    public void theOtherSortsOnMixedCurrenciesAddNoHeader() {
        long yen = insertWallet("Yen", "JPY", false);
        long euro = insertWallet("Euro", "EUR", false);
        insertTransaction(yen, Contract.Direction.EXPENSE, 800L, "Ramen");
        insertTransaction(euro, Contract.Direction.EXPENSE, 1250L, "Groceries");
        for (SearchFilter.Sort sort : new SearchFilter.Sort[] {SearchFilter.Sort.NEWEST, SearchFilter.Sort.OLDEST, SearchFilter.Sort.CATEGORY}) {
            SearchCursorLoader.Summary summary = load(sorted(sort));
            assertEquals(sort.name(), -1, mCursor.getColumnIndex(TransactionHeaderCursor.COLUMN_ITEM_TYPE));
            assertEquals(2, mCursor.getCount());
            assertEquals(2, summary.getMatchCount());
            assertNull(summary.getCurrency());
            mCursor.close();
        }
        mCursor = null;
    }

    @Test
    public void anAmountSortWithNothingMatchedAddsNoHeader() {
        long euro = insertWallet("Euro", "EUR", false);
        insertTransaction(euro, Contract.Direction.EXPENSE, 200L, "Coffee");
        SearchFilter filter = sorted(SearchFilter.Sort.LARGEST);
        filter.setText("nothing like it");
        SearchCursorLoader.Summary summary = load(filter);
        assertEquals(-1, mCursor.getColumnIndex(TransactionHeaderCursor.COLUMN_ITEM_TYPE));
        assertEquals(0, summary.getMatchCount());
    }

    @Test
    public void aSortPickedAfterTheLoaderIsBuiltDoesNotReachIt() {
        long euro = insertWallet("Euro", "EUR", false);
        insertTransaction(euro, Contract.Direction.EXPENSE, 200L, "Coffee");
        insertTransaction(euro, Contract.Direction.EXPENSE, 900L, "Rent");
        SearchFilter filter = new SearchFilter();
        SearchCursorLoader loader = new SearchCursorLoader(mContext, filter);
        filter.setSort(SearchFilter.Sort.OLDEST);
        mCursor = loader.loadInBackground();
        // same date and time, so Newest puts the later row first
        assertEquals(Arrays.asList("Rent", "Coffee"), rowsAndHeaders());
    }

    @Test
    public void theSortIsNotACriterionAndSurvivesACopy() {
        SearchFilter filter = sorted(SearchFilter.Sort.SMALLEST);
        assertNull(filter.toSelection(null).getL());
        assertNull(filter.toSelection(null).getR());
        assertEquals(SearchFilter.Sort.SMALLEST, filter.copy().getSort());
        assertEquals(SearchFilter.Sort.NEWEST, new SearchFilter().getSort());
    }

    private void sortFixture() {
        long euro = insertWallet("Euro", "EUR", false);
        long rent = insertCategory("Rent", null);
        // lower case, so a binary sort would put it after Rent
        long food = insertCategory("food", null);
        // accented, so a binary or NOCASE sort would put it last
        long bakery = insertCategory("\u00c9picerie", food);
        insertRow(euro, rent, 90000L, "2026-01-10 09:00:00", "r1");
        insertRow(euro, food, 1500L, "2026-01-12 12:00:00", "f1");
        insertRow(euro, bakery, 1500L, "2026-01-12 12:00:00", "b1");
        insertRow(euro, food, 300L, "2026-01-15 08:00:00", "f2");
        insertRow(euro, bakery, 700L, "2026-01-05 18:00:00", "b2");
        insertRow(euro, food, 1500L, "2026-01-12 12:00:00", "f3");
        // an equal amount, older than f1, b1 and f3 and inserted after them
        insertRow(euro, food, 1500L, "2026-01-11 10:00:00", "f4");
    }

    private static SearchFilter sorted(SearchFilter.Sort sort) {
        SearchFilter filter = new SearchFilter();
        filter.setSort(sort);
        return filter;
    }

    private List<String> descriptions(SearchFilter.Sort sort) {
        load(sorted(sort));
        return rowsAndHeaders();
    }

    /**
     * Each row's description, and each header's currency in its place.
     */
    private List<String> rowsAndHeaders() {
        int indexType = mCursor.getColumnIndex(TransactionHeaderCursor.COLUMN_ITEM_TYPE);
        int indexCurrency = mCursor.getColumnIndex(CurrencyHeaderCursor.COLUMN_HEADER_CURRENCY);
        int indexDescription = mCursor.getColumnIndexOrThrow(Contract.Transaction.DESCRIPTION);
        List<String> rows = new ArrayList<>();
        mCursor.moveToPosition(-1);
        while (mCursor.moveToNext()) {
            boolean header = indexType != -1 && mCursor.getInt(indexType) == TransactionHeaderCursor.TYPE_HEADER;
            if (header) {
                assertFalse(mCursor.isNull(indexCurrency));
            } else if (indexCurrency != -1) {
                assertTrue(mCursor.isNull(indexCurrency));
            }
            rows.add(mCursor.getString(header ? indexCurrency : indexDescription));
        }
        return rows;
    }

    private long insertCategory(String name, Long parent) {
        ContentValues values = new ContentValues();
        values.put(Contract.Category.NAME, name);
        values.put(Contract.Category.ICON, ICON);
        values.put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.getValue());
        values.put(Contract.Category.SHOW_REPORT, true);
        values.put(Contract.Category.PARENT, parent);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, values));
    }

    private void insertRow(long wallet, long category, long money, String date, String description) {
        ContentValues values = new ContentValues();
        values.put(Contract.Transaction.MONEY, money);
        values.put(Contract.Transaction.DATE, date);
        values.put(Contract.Transaction.DESCRIPTION, description);
        values.put(Contract.Transaction.CATEGORY_ID, category);
        values.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE);
        values.put(Contract.Transaction.TYPE, NewEditTransactionActivity.TYPE_STANDARD);
        values.put(Contract.Transaction.WALLET_ID, wallet);
        values.put(Contract.Transaction.CONFIRMED, true);
        values.put(Contract.Transaction.COUNT_IN_TOTAL, true);
        mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values);
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
