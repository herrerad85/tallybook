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

import android.database.Cursor;
import android.database.MatrixCursor;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Group;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.storage.wrapper.TransactionHeaderCursor;

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
 * Selection in the transaction lists. The list with headers used here lays out as: 0 header of
 * the 15th, 1 id 1, 2 id 2, 3 id 3 which is a transfer leg, 4 header of the 14th, 5 id 4, 6 id 5.
 * A click reads its row through getAdapterPosition, which answers nothing between a change and
 * the next layout, so the list is laid out again after every change a later click depends on.
 */
@RunWith(RobolectricTestRunner.class)
public class TransactionSelectionTest {

    private static final String FIRST_DAY = "0:2019-03-15 00:00:00";
    private static final String DAY_15 = "2019-03-15 10:00:00";
    private static final String DAY_14 = "2019-03-14 10:00:00";

    private static final int STANDARD = Contract.TransactionType.STANDARD;
    private static final int TRANSFER = Contract.TransactionType.TRANSFER;

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
            Contract.Transaction.DESCRIPTION
    };

    @Before
    public void clearTheStoredPeriods() {
        PreferenceManager.setCollapsedPeriods(Collections.<String>emptySet());
    }

    private static MatrixCursor bareCursor(long[] ids, int[] types, String[] dates) {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        for (int row = 0; row < ids.length; row++) {
            cursor.addRow(new Object[] {
                    ids[row], types[row], dates[row], Contract.Direction.EXPENSE, 1000L, "EUR",
                    1, 1, "Category", null, "Description"
            });
        }
        return cursor;
    }

    private static TransactionHeaderCursor twoDays() {
        return new TransactionHeaderCursor(bareCursor(
                new long[] {1L, 2L, 3L, 4L, 5L},
                new int[] {STANDARD, STANDARD, TRANSFER, STANDARD, STANDARD},
                new String[] {DAY_15, DAY_15, DAY_15, DAY_14, DAY_14}), Group.DAILY, null, null);
    }

    private static TransactionCursorAdapter adapter(Records listener, Cursor cursor) {
        TransactionCursorAdapter adapter = new TransactionCursorAdapter(listener, true);
        adapter.changeCursor(cursor);
        return adapter;
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
    public void aLongPressSelectsARowAndATapThenTogglesInsteadOfOpeningIt() {
        Records listener = new Records();
        TransactionCursorAdapter adapter = adapter(listener, twoDays());
        RecyclerView list = listWith(adapter);
        assertTrue(childAt(list, 1).performLongClick());
        assertEquals(ids(1L), selected(adapter));
        assertEquals(1, listener.mSelectionCount);
        layOut(list);
        assertTrue(childAt(list, 1).isActivated());
        assertFalse(childAt(list, 2).isActivated());
        childAt(list, 2).performClick();
        assertEquals(ids(1L, 2L), selected(adapter));
        layOut(list);
        childAt(list, 1).performClick();
        assertEquals(ids(2L), selected(adapter));
        assertEquals(-1L, listener.mOpenedId);
        layOut(list);
        childAt(list, 2).performClick();
        assertTrue(selected(adapter).isEmpty());
        assertEquals(0, listener.mSelectionCount);
        layOut(list);
        // nothing selected, so a tap opens the row again
        childAt(list, 1).performClick();
        assertEquals(1L, listener.mOpenedId);
    }

    @Test
    public void aTransferLegCannotBeSelectedByLongPressOrByTap() {
        Records listener = new Records();
        TransactionCursorAdapter adapter = adapter(listener, twoDays());
        RecyclerView list = listWith(adapter);
        assertFalse(childAt(list, 3).performLongClick());
        assertTrue(selected(adapter).isEmpty());
        childAt(list, 1).performLongClick();
        layOut(list);
        childAt(list, 3).performClick();
        assertEquals(ids(1L), selected(adapter));
        assertEquals(-1L, listener.mOpenedId);
    }

    @Test
    public void aHeaderCannotBeSelectedAndItsTapStillOpensTheReport() {
        Records listener = new Records();
        TransactionCursorAdapter adapter = adapter(listener, twoDays());
        RecyclerView list = listWith(adapter);
        assertFalse(childAt(list, 0).performLongClick());
        assertTrue(selected(adapter).isEmpty());
        assertEquals(-1, listener.mSelectionCount);
        childAt(list, 1).performLongClick();
        layOut(list);
        childAt(list, 4).performClick();
        assertTrue(listener.mHeaderClicked);
        assertEquals(ids(1L), selected(adapter));
    }

    @Test
    public void selectAllSkipsRowsInACollapsedPeriodAndTransferLegs() {
        Records listener = new Records();
        TransactionCursorAdapter adapter = adapter(listener, twoDays());
        adapter.selectAll();
        assertEquals(ids(1L, 2L, 4L, 5L), selected(adapter));
        assertEquals(4, listener.mSelectionCount);
        adapter.clearSelection();
        PreferenceManager.setCollapsedPeriods(Collections.singleton(FIRST_DAY));
        adapter.reloadCollapsedPeriods();
        adapter.selectAll();
        assertEquals(ids(4L, 5L), selected(adapter));
    }

    @Test
    public void withoutHeadersSelectAllTakesEveryRowButTheTransferLegs() {
        TransactionCursorAdapter adapter = adapter(new Records(), bareCursor(
                new long[] {1L, 2L, 3L},
                new int[] {STANDARD, TRANSFER, STANDARD},
                new String[] {DAY_15, DAY_15, DAY_14}));
        adapter.selectAll();
        assertEquals(ids(1L, 3L), selected(adapter));
    }

    @Test
    public void theSelectionIsKeptByIdAcrossACursorSwapAndDropsIdsThatAreGone() {
        Records listener = new Records();
        TransactionCursorAdapter adapter = adapter(listener, twoDays());
        adapter.setSelectedIds(new long[] {2L, 4L});
        assertEquals(2, listener.mSelectionCount);
        // id 4 is gone, and id 2 moves up from position 2 to position 1
        adapter.changeCursor(new TransactionHeaderCursor(bareCursor(
                new long[] {2L, 7L, 5L},
                new int[] {STANDARD, STANDARD, STANDARD},
                new String[] {DAY_15, DAY_15, DAY_14}), Group.DAILY, null, null));
        assertEquals(ids(2L), selected(adapter));
        assertEquals(1, listener.mSelectionCount);
        RecyclerView list = listWith(adapter);
        assertTrue(childAt(list, 1).isActivated());
        assertFalse(childAt(list, 2).isActivated());
    }

    @Test
    public void aSelectedRowIsDroppedWhenItsPeriodIsCollapsedAndExpandingDoesNotBringItBack() {
        Records listener = new Records();
        TransactionCursorAdapter adapter = adapter(listener, twoDays());
        adapter.setSelectedIds(new long[] {1L, 4L});
        assertEquals(2, listener.mSelectionCount);
        adapter.togglePeriod(FIRST_DAY);
        assertEquals(ids(4L), selected(adapter));
        assertEquals(1, listener.mSelectionCount);
        adapter.togglePeriod(FIRST_DAY);
        assertEquals(ids(4L), selected(adapter));
        assertEquals(1, listener.mSelectionCount);
    }

    @Test
    public void aPeriodCollapsedInTheOtherListDropsItsSelectedRowsHereOnReload() {
        Records listener = new Records();
        TransactionCursorAdapter adapter = adapter(listener, twoDays());
        adapter.setSelectedIds(new long[] {1L, 4L});
        PreferenceManager.setCollapsedPeriods(Collections.singleton(FIRST_DAY));
        adapter.reloadCollapsedPeriods();
        assertEquals(ids(4L), selected(adapter));
        assertEquals(1, listener.mSelectionCount);
    }

    @Test
    public void aRestoredSelectionWaitsForTheFirstLoadWhenThePeriodsAreReloadedBeforeIt() {
        Records listener = new Records();
        TransactionCursorAdapter adapter = new TransactionCursorAdapter(listener, true);
        adapter.setSelectedIds(new long[] {1L, 4L});
        PreferenceManager.setCollapsedPeriods(Collections.singleton(FIRST_DAY));
        adapter.reloadCollapsedPeriods();
        assertEquals(ids(1L, 4L), selected(adapter));
        adapter.changeCursor(twoDays());
        assertEquals(ids(4L), selected(adapter));
        assertEquals(1, listener.mSelectionCount);
    }

    @Test
    public void withoutHeadersACursorSwapDropsASelectedIdThatIsGoneAndKeepsTheOneStillThere() {
        Records listener = new Records();
        TransactionCursorAdapter adapter = adapter(listener, bareCursor(
                new long[] {1L, 2L, 3L},
                new int[] {STANDARD, STANDARD, STANDARD},
                new String[] {DAY_15, DAY_15, DAY_14}));
        adapter.setSelectedIds(new long[] {1L, 3L});
        adapter.changeCursor(bareCursor(
                new long[] {3L, 6L},
                new int[] {STANDARD, STANDARD},
                new String[] {DAY_15, DAY_14}));
        assertEquals(ids(3L), selected(adapter));
        assertEquals(1, listener.mSelectionCount);
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
