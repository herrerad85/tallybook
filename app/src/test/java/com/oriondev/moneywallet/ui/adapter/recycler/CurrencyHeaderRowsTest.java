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

package com.oriondev.moneywallet.ui.adapter.recycler;

import android.database.MatrixCursor;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.storage.wrapper.CurrencyHeaderCursor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The search list with a header before each currency. It lays out as: 0 Euro, 1 id 1, 2 id 2
 * which is a transfer leg, 3 Japanese Yen, 4 id 3, 5 a code this installation does not know,
 * 6 id 4.
 */
@RunWith(RobolectricTestRunner.class)
public class CurrencyHeaderRowsTest {

    private static final String DAY = "2026-01-15 12:00:00";

    private static final String[] COLUMNS = new String[] {
            Contract.Transaction.ID,
            Contract.Transaction.TYPE,
            Contract.Transaction.DATE,
            Contract.Transaction.DIRECTION,
            Contract.Transaction.MONEY,
            Contract.Transaction.WALLET_CURRENCY,
            Contract.Transaction.CONFIRMED,
            Contract.Transaction.COUNT_IN_TOTAL,
            Contract.Transaction.CATEGORY_NAME,
            Contract.Transaction.CATEGORY_ICON,
            Contract.Transaction.CATEGORY_TAG,
            Contract.Transaction.DESCRIPTION
    };

    @Before
    public void loadTheCurrenciesAndClearTheStoredPeriods() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        PreferenceManager.setCollapsedPeriods(Collections.<String>emptySet());
    }

    private static CurrencyHeaderCursor threeCurrencies() {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        addRow(cursor, 1L, Contract.TransactionType.STANDARD, "EUR");
        addRow(cursor, 2L, Contract.TransactionType.TRANSFER, "EUR");
        addRow(cursor, 3L, Contract.TransactionType.STANDARD, "JPY");
        addRow(cursor, 4L, Contract.TransactionType.STANDARD, "XTS");
        return new CurrencyHeaderCursor(cursor);
    }

    private static void addRow(MatrixCursor cursor, long id, int type, String currency) {
        cursor.addRow(new Object[] {
                id, type, DAY, Contract.Direction.EXPENSE, 1000L, currency, 1, 1, "Category", null,
                null, "Description"
        });
    }

    private static RecyclerView listWith(TransactionCursorAdapter adapter) {
        RecyclerView recyclerView = new RecyclerView(new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.MoneyWalletAppTheme));
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        recyclerView.setAdapter(adapter);
        layOut(recyclerView);
        return recyclerView;
    }

    private static void layOut(RecyclerView recyclerView) {
        recyclerView.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
        recyclerView.layout(0, 0, 1080, 2400);
    }

    private static View childAt(RecyclerView recyclerView, int position) {
        View view = recyclerView.getLayoutManager().findViewByPosition(position);
        assertNotNull("nothing was laid out at position " + position, view);
        return view;
    }

    private static TransactionCursorAdapter adapter(Records listener) {
        // as the search screen builds it
        TransactionCursorAdapter adapter = new TransactionCursorAdapter(listener);
        adapter.changeCursor(threeCurrencies());
        return adapter;
    }

    private static Set<Long> selected(TransactionCursorAdapter adapter) {
        Set<Long> ids = new HashSet<>();
        for (long id : adapter.getSelectedIds()) {
            ids.add(id);
        }
        return ids;
    }

    private static Set<Long> ids(Long... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    @Test
    public void theListCountsEveryRowAndEveryHeader() {
        assertEquals(7, adapter(new Records()).getItemCount());
    }

    @Test
    public void eachHeaderReadsTheCurrencyNameAndCodeOrTheCodeAloneWhenUnknown() {
        RecyclerView list = listWith(adapter(new Records()));
        assertEquals("Euro (EUR)", ((TextView) childAt(list, 0)).getText().toString());
        assertEquals("Japanese Yen (JPY)", ((TextView) childAt(list, 3)).getText().toString());
        assertEquals("XTS", ((TextView) childAt(list, 5)).getText().toString());
    }

    @Test
    public void aHeaderIsAnAccessibilityHeadingAndAnItemIsNot() {
        RecyclerView list = listWith(adapter(new Records()));
        assertTrue(ViewCompat.isAccessibilityHeading(childAt(list, 0)));
        assertFalse(ViewCompat.isAccessibilityHeading(childAt(list, 1)));
    }

    @Test
    public void aTapOrALongPressOnAHeaderOpensAndSelectsNothing() {
        Records listener = new Records();
        TransactionCursorAdapter adapter = adapter(listener);
        RecyclerView list = listWith(adapter);
        View header = childAt(list, 3);
        assertFalse(header.hasOnClickListeners());
        assertFalse(header.performClick());
        assertFalse(header.performLongClick());
        assertEquals(-1L, listener.mOpenedId);
        assertFalse(listener.mHeaderClicked);
        assertTrue(selected(adapter).isEmpty());
        childAt(list, 4).performClick();
        assertEquals(3L, listener.mOpenedId);
    }

    @Test
    public void selectAllTakesOnlyTheRowsAndSkipsTheTransferLeg() {
        Records listener = new Records();
        TransactionCursorAdapter adapter = adapter(listener);
        adapter.selectAll();
        assertEquals(ids(1L, 3L, 4L), selected(adapter));
        assertEquals(3, listener.mSelectionCount);
    }

    @Test
    public void aRestoredSelectionKeepsTheRowsOnScreenAndDropsTheRest() {
        Records listener = new Records();
        TransactionCursorAdapter adapter = adapter(listener);
        adapter.setSelectedIds(new long[] {1L, 4L, 99L});
        assertEquals(ids(1L, 4L), selected(adapter));
        assertEquals(2, listener.mSelectionCount);
    }

    @Test
    public void aCurrencyHeaderNeverFoldsWhateverPeriodsAreStored() {
        TransactionCursorAdapter adapter = adapter(new Records());
        // the key a header with no period and no grouping would come to
        PreferenceManager.setCollapsedPeriods(Collections.singleton("0:null"));
        adapter.reloadCollapsedPeriods();
        assertEquals(7, adapter.getItemCount());
    }

    private static class Records implements TransactionCursorAdapter.ActionListener {

        private long mOpenedId = -1L;
        private int mSelectionCount = -1;
        private boolean mHeaderClicked;

        @Override
        public void onHeaderClick(Date startDate, Date endDate) {
            mHeaderClicked = true;
        }

        @Override
        public void onTransactionClick(long id) {
            mOpenedId = id;
        }

        @Override
        public void onSelectionChanged(int count) {
            mSelectionCount = count;
        }
    }
}
