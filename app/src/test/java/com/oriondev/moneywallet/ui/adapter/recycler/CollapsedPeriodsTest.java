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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Group;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.storage.wrapper.TransactionHeaderCursor;
import com.oriondev.moneywallet.utils.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A header the user has folded keeps its transactions off the list, and every position the list
 * asks about has to be answered against the rows that are left, not against the cursor. The
 * store is one set for the whole application, so a fold written by either of the two lists that
 * draw headers with this adapter is the same fold, and a list only sees it once it reads the
 * store again.
 *
 * Group.DAILY is used throughout. A daily header starts at midnight of the day its rows fall on,
 * so the two headers here are keyed "0:2019-03-15 00:00:00" and "0:2019-03-14 00:00:00".
 */
@RunWith(RobolectricTestRunner.class)
public class CollapsedPeriodsTest {

    private static final String FIRST_DAY = "0:2019-03-15 00:00:00";
    private static final String SECOND_DAY = "0:2019-03-14 00:00:00";
    private static final String FIRST_DAY_AS_A_MONTH = "2:2019-03-15 00:00:00";

    private static final int HEADER = TransactionHeaderCursor.TYPE_HEADER;
    private static final int ITEM = TransactionHeaderCursor.TYPE_ITEM;

    private static final String[] COLUMNS = new String[] {
            Contract.Transaction.ID,
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

    /** Newest first, which is the order the list queries in. */
    private static final String[] DATES = new String[] {
            "2019-03-15 10:00:00",
            "2019-03-15 10:00:00",
            "2019-03-15 10:00:00",
            "2019-03-14 10:00:00",
            "2019-03-14 10:00:00"
    };

    /** Nothing folded, so a case cannot be handed what the one before it wrote. */
    @Before
    public void clearTheStoredPeriods() {
        PreferenceManager.setCollapsedPeriods(Collections.<String>emptySet());
    }

    private MatrixCursor bareCursor() {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        for (int row = 0; row < DATES.length; row++) {
            cursor.addRow(new Object[] {
                    (long) (row + 1), DATES[row], Contract.Direction.EXPENSE, 1000L, "EUR",
                    1, 1, "Category", null, null, "Description"
            });
        }
        return cursor;
    }

    private TransactionHeaderCursor cursor() {
        return new TransactionHeaderCursor(bareCursor(), Group.DAILY, null, null);
    }

    private TransactionCursorAdapter adapter() {
        TransactionCursorAdapter adapter = new TransactionCursorAdapter(new RecordsNothing(), false);
        adapter.changeCursor(cursor());
        return adapter;
    }

    /**
     * Folds the given keys the way another screen would, then asks this adapter to read the store
     * again. This is the way a fold arrives from outside; the arrow's own path is togglePeriod,
     * which theArrowFoldsAndUnfoldsThroughTheAdapter drives instead.
     */
    private void fold(TransactionCursorAdapter adapter, String... keys) {
        PreferenceManager.setCollapsedPeriods(new HashSet<>(Arrays.asList(keys)));
        adapter.reloadCollapsedPeriods();
    }

    private void assertTypes(TransactionCursorAdapter adapter, int... types) {
        assertEquals(types.length, adapter.getItemCount());
        for (int position = 0; position < types.length; position++) {
            assertEquals("wrong type at position " + position,
                    types[position], adapter.getItemViewType(position));
        }
    }

    @Test
    public void withNothingStoredEveryTransactionIsUnderItsHeader() {
        TransactionCursorAdapter adapter = adapter();
        assertTypes(adapter, HEADER, ITEM, ITEM, ITEM, HEADER, ITEM, ITEM);
    }

    @Test
    public void foldingADayLeavesItsHeaderAndTakesItsTransactions() {
        TransactionCursorAdapter adapter = adapter();
        fold(adapter, FIRST_DAY);
        assertTypes(adapter, HEADER, HEADER, ITEM, ITEM);
        assertEquals(4L, adapter.getItemId(2));
        assertEquals(5L, adapter.getItemId(3));
    }

    @Test
    public void foldingTheSameDayAgainBringsItsTransactionsBack() {
        TransactionCursorAdapter adapter = adapter();
        fold(adapter, FIRST_DAY);
        fold(adapter);
        assertTypes(adapter, HEADER, ITEM, ITEM, ITEM, HEADER, ITEM, ITEM);
        assertTrue(PreferenceManager.getCollapsedPeriods().isEmpty());
    }

    @Test
    public void aFoldSurvivesTheListBeingLoadedAgain() {
        TransactionCursorAdapter adapter = adapter();
        fold(adapter, FIRST_DAY);
        adapter.changeCursor(cursor());
        assertTypes(adapter, HEADER, HEADER, ITEM, ITEM);
    }

    @Test
    public void aDayAndTheMonthItOpensAreNotTheSameFold() {
        TransactionCursorAdapter adapter = adapter();
        fold(adapter, FIRST_DAY_AS_A_MONTH);
        assertTypes(adapter, HEADER, ITEM, ITEM, ITEM, HEADER, ITEM, ITEM);
    }

    @Test
    public void aFoldWrittenByAnotherScreenIsSeenOnlyOnceThisListReadsAgain() {
        TransactionCursorAdapter adapter = adapter();
        PreferenceManager.setCollapsedPeriods(new HashSet<>(Collections.singletonList(FIRST_DAY)));
        assertEquals(7, adapter.getItemCount());
        adapter.reloadCollapsedPeriods();
        assertEquals(4, adapter.getItemCount());
    }

    @Test
    public void foldingTheSecondDayLeavesTheFirstDayWhereItWas() {
        TransactionCursorAdapter adapter = adapter();
        fold(adapter, SECOND_DAY);
        assertTypes(adapter, HEADER, ITEM, ITEM, ITEM, HEADER);
        assertEquals(1L, adapter.getItemId(1));
        assertEquals(2L, adapter.getItemId(2));
        assertEquals(3L, adapter.getItemId(3));
    }

    /**
     * The other cases here read types and ids, which an adapter that never applied the mapping in
     * onBindViewHolder still answers correctly. This one binds a real header row, which is where
     * the wrong cursor row reaches a null start date and the list crashes.
     */
    @Test
    public void aFoldedHeaderBindsTheHeaderThatIsOnScreen() {
        TransactionCursorAdapter adapter = adapter();
        fold(adapter, FIRST_DAY);
        // the application context carries the platform theme, not the one the manifest gives the
        // activities, and the row's ripple is an AppCompat attribute that only that one resolves
        FrameLayout parent = new FrameLayout(new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.MoneyWalletAppTheme));
        RecyclerView.ViewHolder onScreen = adapter.onCreateViewHolder(parent, HEADER);
        adapter.onBindViewHolder(onScreen, 1);
        // position 1 on screen is the second day's header, 2019-03-14, since the first day is
        // folded and its three transactions are off the list
        TextView leftTextView = onScreen.itemView.findViewById(R.id.left_text_view);
        String dateRange = leftTextView.getText().toString();
        assertTrue("the header drawn at position 1 was " + dateRange, dateRange.contains("14"));
        assertTrue(toggleDescription(onScreen).startsWith("Hide"));
        assertEquals(180f, toggle(onScreen).getRotation(), 0f);
        RecyclerView.ViewHolder folded = adapter.onCreateViewHolder(parent, HEADER);
        adapter.onBindViewHolder(folded, 0);
        assertTrue(toggleDescription(folded).startsWith("Show"));
        assertEquals(0f, toggle(folded).getRotation(), 0f);
    }

    private static ImageView toggle(RecyclerView.ViewHolder holder) {
        return holder.itemView.findViewById(R.id.period_toggle_image_view);
    }

    private static String toggleDescription(RecyclerView.ViewHolder holder) {
        return String.valueOf(toggle(holder).getContentDescription());
    }

    @Test
    public void theArrowFoldsAndUnfoldsThroughTheAdapter() {
        TransactionCursorAdapter adapter = adapter();
        adapter.togglePeriod(FIRST_DAY);
        assertEquals(4, adapter.getItemCount());
        assertEquals(Collections.singleton(FIRST_DAY), PreferenceManager.getCollapsedPeriods());
        adapter.togglePeriod(FIRST_DAY);
        assertEquals(7, adapter.getItemCount());
        assertTrue(PreferenceManager.getCollapsedPeriods().isEmpty());
    }

    /**
     * A holder built with onCreateViewHolder alone answers NO_POSITION from getAdapterPosition,
     * and every click handler here reads the cursor through that, so a click on such a holder
     * resolves to no row at all. Laying the adapter out in a real RecyclerView is what gives the
     * holders a position, and it is also the only way the arrow's own listener is reached from a
     * view the list built.
     */
    private RecyclerView listWith(TransactionCursorAdapter adapter) {
        RecyclerView recyclerView = new RecyclerView(new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.MoneyWalletAppTheme));
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        recyclerView.setAdapter(adapter);
        layOut(recyclerView);
        return recyclerView;
    }

    /** The one screen the cases here lay a list out on, so a second pass uses the first one's. */
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

    /** The listener the two clicking cases hand the adapter, and the adapter opens the report. */
    private TransactionCursorAdapter adapter(RecordsClicks listener) {
        TransactionCursorAdapter adapter = new TransactionCursorAdapter(listener, true);
        adapter.changeCursor(cursor());
        return adapter;
    }

    /**
     * A transaction row carries the id of the row on screen, not of the cursor row that sat at
     * that position before the fold took three rows out from above it.
     */
    @Test
    public void aTapOnARowUnderAFoldedHeaderOpensThatRow() {
        RecordsClicks listener = new RecordsClicks();
        TransactionCursorAdapter adapter = adapter(listener);
        fold(adapter, FIRST_DAY);
        RecyclerView recyclerView = listWith(adapter);
        // position 2 on screen is the first transaction of the second day, id 4
        childAt(recyclerView, 2).performClick();
        assertEquals(4L, listener.mTransactionId);
        // and position 1 is the second day's header, which opens the report for that day
        childAt(recyclerView, 1).performClick();
        assertNotNull("the header click recorded no date", listener.mHeaderStart);
        // compared as a time, so a date parsed the same way is the same instant
        assertEquals(DateUtils.getDateFromSQLDateTimeString("2019-03-14 00:00:00").getTime(),
                listener.mHeaderStart.getTime());
        // the end of a daily period is the last second of the same day, since the range is built
        // ending at 23:59:59.999 and the header cursor prints it without the thousandths
        assertEquals(DateUtils.getDateFromSQLDateTimeString("2019-03-14 23:59:59").getTime(),
                listener.mHeaderEnd.getTime());
    }

    /**
     * The arrow's own listener, from a tap on the view the list laid out to the stored set.
     *
     * The list is laid out again at the end, because a fold that is never told to the list leaves
     * the stored set and the adapter count right and the rows the list itself is holding stale,
     * and the next scroll on a device reads a row that is no longer on the list.
     */
    @Test
    public void theArrowOnAHeaderFoldsThatHeader() {
        TransactionCursorAdapter adapter = adapter(new RecordsClicks());
        RecyclerView recyclerView = listWith(adapter);
        // position 4 with nothing folded is the second day's header
        childAt(recyclerView, 4).findViewById(R.id.period_toggle_image_view).performClick();
        assertEquals(Collections.singleton(SECOND_DAY), PreferenceManager.getCollapsedPeriods());
        assertEquals(5, adapter.getItemCount());
        layOut(recyclerView);
        // the list is holding those five rows, not the seven it laid out before the fold
        assertEquals(5, recyclerView.getChildCount());
        // position 4 is now the last row, the folded day's own header, which is still a header
        // row; a transaction row carries no left_text_view at all
        TextView leftTextView = childAt(recyclerView, 4).findViewById(R.id.left_text_view);
        assertNotNull("the row at position 4 was not a header", leftTextView);
        assertTrue("the header drawn at position 4 was " + leftTextView.getText(),
                leftTextView.getText().toString().contains("14"));
    }

    /**
     * The calendar and the search results build their cursor with a plain CursorLoader, so it
     * carries no item type column and no header rows. An adapter that showed nothing for such a
     * cursor would leave both screens blank, and none of the other cases here can see that.
     */
    @Test
    public void aCursorWithoutHeadersShowsEveryRowAsAnItem() {
        PreferenceManager.setCollapsedPeriods(new HashSet<>(Collections.singletonList(FIRST_DAY)));
        TransactionCursorAdapter adapter = new TransactionCursorAdapter(new RecordsNothing(), false);
        adapter.changeCursor(bareCursor());
        assertTypes(adapter, ITEM, ITEM, ITEM, ITEM, ITEM);
        assertEquals(1L, adapter.getItemId(0));
        assertEquals(5L, adapter.getItemId(4));
        adapter.reloadCollapsedPeriods();
        assertTypes(adapter, ITEM, ITEM, ITEM, ITEM, ITEM);
        assertEquals(1L, adapter.getItemId(0));
        assertEquals(5L, adapter.getItemId(4));
    }

    @Test
    public void theHeaderCursorKnowsWhichPositionsAreHeadersWithoutMoving() {
        TransactionHeaderCursor cursor = cursor();
        assertTrue(cursor.isHeaderAt(0));
        assertTrue(cursor.isHeaderAt(4));
        assertFalse(cursor.isHeaderAt(1));
        assertFalse(cursor.isHeaderAt(2));
        assertFalse(cursor.isHeaderAt(3));
        assertFalse(cursor.isHeaderAt(5));
        assertFalse(cursor.isHeaderAt(6));
        assertFalse(cursor.isHeaderAt(7));
        assertFalse(cursor.isHeaderAt(-1));
    }

    /**
     * A loader that resets hands the adapter a null cursor through CursorListFragment, and a
     * cursor taken away never reaches onLoadColumnIndices, so the rows built for the old cursor
     * are still held afterwards and only the data valid guard in getItemCount keeps the list
     * from asking for them.
     */
    @Test
    public void aCursorTakenAwayLeavesNothingOnScreen() {
        TransactionCursorAdapter adapter = adapter();
        adapter.changeCursor(null);
        assertEquals(0, adapter.getItemCount());
        TransactionCursorAdapter bare = new TransactionCursorAdapter(new RecordsNothing(), false);
        bare.changeCursor(bareCursor());
        bare.changeCursor(null);
        assertEquals(0, bare.getItemCount());
    }

    /** The adapter needs a listener, and none of these cases clicks anything. */
    private static class RecordsNothing implements TransactionCursorAdapter.ActionListener {

        @Override
        public void onHeaderClick(Date startDate, Date endDate) {
            // never called here
        }

        @Override
        public void onTransactionClick(long id) {
            // never called here
        }

        @Override
        public void onSelectionChanged(int count) {
            // never called here
        }
    }

    /** Keeps what the last click handed it, so a case can say which row the click resolved to. */
    private static class RecordsClicks implements TransactionCursorAdapter.ActionListener {

        private long mTransactionId = -1L;
        private Date mHeaderStart;
        private Date mHeaderEnd;

        @Override
        public void onHeaderClick(Date startDate, Date endDate) {
            mHeaderStart = startDate;
            mHeaderEnd = endDate;
        }

        @Override
        public void onTransactionClick(long id) {
            mTransactionId = id;
        }

        @Override
        public void onSelectionChanged(int count) {
            // selection is TransactionSelectionTest's
        }
    }
}
