package com.oriondev.moneywallet.ui.adapter.recycler;

import android.database.MatrixCursor;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Group;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.wrapper.TransferHeaderCursor;
import com.oriondev.moneywallet.utils.DateUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Calendar;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assume.assumeTrue;

@RunWith(RobolectricTestRunner.class)
public class DatedAheadRowTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private static final String[] TRANSACTION_COLUMNS = new String[] {
            Contract.Transaction.ID,
            Contract.Transaction.TYPE,
            Contract.Transaction.DATE,
            Contract.Transaction.DIRECTION,
            Contract.Transaction.MONEY,
            Contract.Transaction.WALLET_CURRENCY,
            Contract.Transaction.CATEGORY_NAME,
            Contract.Transaction.CATEGORY_ICON,
            Contract.Transaction.DESCRIPTION,
            Contract.Transaction.CONFIRMED
    };

    private static final String[] TRANSFER_COLUMNS = new String[] {
            Contract.Transfer.ID,
            Contract.Transfer.DATE,
            Contract.Transfer.DESCRIPTION,
            Contract.Transfer.TRANSACTION_FROM_WALLET_NAME,
            Contract.Transfer.TRANSACTION_FROM_WALLET_CURRENCY,
            Contract.Transfer.TRANSACTION_TO_WALLET_NAME,
            Contract.Transfer.TRANSACTION_TO_WALLET_ICON,
            Contract.Transfer.TRANSACTION_FROM_MONEY
    };

    private static String yearsFromNow(int years) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, years);
        return DateUtils.getSQLDateTimeString(calendar.getTime());
    }

    private static String minutesFromNow(int minutes) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, minutes);
        return DateUtils.getSQLDateTimeString(calendar.getTime());
    }

    private static Object[] transaction(long id, String date, int confirmed) {
        return new Object[] {
                id, Contract.TransactionType.STANDARD, date, Contract.Direction.EXPENSE,
                1000L, "EUR", "Category", null, "Description", confirmed
        };
    }

    private static Object[] transfer(long id, String date) {
        return new Object[] {id, date, "Description", "Cash", "EUR", "Bank", ICON, 1000L};
    }

    private static TransactionCursorAdapter transactionAdapter() {
        return new TransactionCursorAdapter(new TransactionCursorAdapter.ActionListener() {

            @Override
            public void onHeaderClick(Date startDate, Date endDate) {
            }

            @Override
            public void onTransactionClick(long id) {
            }

            @Override
            public void onSelectionChanged(int count) {
            }
        });
    }

    private static RecyclerView listWith(RecyclerView.Adapter<?> adapter) {
        RecyclerView recyclerView = new RecyclerView(new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.MoneyWalletAppTheme));
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        recyclerView.setAdapter(adapter);
        recyclerView.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
        recyclerView.layout(0, 0, 1080, 2400);
        return recyclerView;
    }

    private static View viewAt(RecyclerView recyclerView, int position, int id) {
        View row = recyclerView.getLayoutManager().findViewByPosition(position);
        assertNotNull("nothing was laid out at position " + position, row);
        return row.findViewById(id);
    }

    private static void assertDatedAhead(RecyclerView list, View marker) {
        assertEquals(View.VISIBLE, marker.getVisibility());
        assertEquals(list.getContext().getString(R.string.description_dated_ahead), marker.getContentDescription());
    }

    private static void assertNotDatedAhead(View marker) {
        assertEquals(View.GONE, marker.getVisibility());
        assertNull(marker.getContentDescription());
    }

    @Test
    public void theCalendarShowsOnlyOnATransactionDatedAhead() {
        MatrixCursor cursor = new MatrixCursor(TRANSACTION_COLUMNS);
        cursor.addRow(transaction(1L, yearsFromNow(1), 1));
        cursor.addRow(transaction(2L, yearsFromNow(-1), 1));
        TransactionCursorAdapter adapter = transactionAdapter();
        adapter.changeCursor(cursor);
        RecyclerView list = listWith(adapter);
        assertDatedAhead(list, viewAt(list, 0, R.id.future_image_view));
        assertNotDatedAhead(viewAt(list, 1, R.id.future_image_view));
    }

    @Test
    public void anUnconfirmedTransactionDatedAheadShowsTheClockAndTheCalendar() {
        MatrixCursor cursor = new MatrixCursor(TRANSACTION_COLUMNS);
        cursor.addRow(transaction(1L, yearsFromNow(1), 0));
        TransactionCursorAdapter adapter = transactionAdapter();
        adapter.changeCursor(cursor);
        RecyclerView list = listWith(adapter);
        assertEquals(View.VISIBLE, viewAt(list, 0, R.id.unconfirmed_image_view).getVisibility());
        assertDatedAhead(list, viewAt(list, 0, R.id.future_image_view));
    }

    @Test
    public void theCalendarShowsOnlyOnATransferDatedAhead() {
        MatrixCursor cursor = new MatrixCursor(TRANSFER_COLUMNS);
        cursor.addRow(transfer(1L, yearsFromNow(1)));
        cursor.addRow(transfer(2L, yearsFromNow(-1)));
        TransferCursorAdapter adapter = new TransferCursorAdapter(null);
        adapter.changeCursor(new TransferHeaderCursor(cursor, Group.DAILY));
        RecyclerView list = listWith(adapter);
        assertDatedAhead(list, viewAt(list, 1, R.id.future_image_view));
        assertNotDatedAhead(viewAt(list, 3, R.id.future_image_view));
    }

    @Test
    public void theCalendarShowsOnATransactionDatedAheadByMinutes() {
        String date = minutesFromNow(10);
        assumeTrue(date.compareTo(DateUtils.getSQLDateTimeString(new Date())) > 0);
        MatrixCursor cursor = new MatrixCursor(TRANSACTION_COLUMNS);
        cursor.addRow(transaction(1L, date, 1));
        TransactionCursorAdapter adapter = transactionAdapter();
        adapter.changeCursor(cursor);
        RecyclerView list = listWith(adapter);
        assertDatedAhead(list, viewAt(list, 0, R.id.future_image_view));
    }

    @Test
    public void theCalendarShowsOnATransferDatedAheadByMinutes() {
        String date = minutesFromNow(10);
        assumeTrue(date.compareTo(DateUtils.getSQLDateTimeString(new Date())) > 0);
        MatrixCursor cursor = new MatrixCursor(TRANSFER_COLUMNS);
        cursor.addRow(transfer(1L, date));
        TransferCursorAdapter adapter = new TransferCursorAdapter(null);
        adapter.changeCursor(new TransferHeaderCursor(cursor, Group.DAILY));
        RecyclerView list = listWith(adapter);
        assertDatedAhead(list, viewAt(list, 1, R.id.future_image_view));
    }

    @Test
    public void theCalendarDoesNotShowOnATransactionDatedMinutesAgo() {
        String date = minutesFromNow(-10);
        assumeTrue(date.compareTo(DateUtils.getSQLDateTimeString(new Date())) < 0);
        MatrixCursor cursor = new MatrixCursor(TRANSACTION_COLUMNS);
        cursor.addRow(transaction(1L, date, 1));
        TransactionCursorAdapter adapter = transactionAdapter();
        adapter.changeCursor(cursor);
        RecyclerView list = listWith(adapter);
        assertNotDatedAhead(viewAt(list, 0, R.id.future_image_view));
    }

    @Test
    public void theCalendarDoesNotShowOnATransferDatedMinutesAgo() {
        String date = minutesFromNow(-10);
        assumeTrue(date.compareTo(DateUtils.getSQLDateTimeString(new Date())) < 0);
        MatrixCursor cursor = new MatrixCursor(TRANSFER_COLUMNS);
        cursor.addRow(transfer(1L, date));
        TransferCursorAdapter adapter = new TransferCursorAdapter(null);
        adapter.changeCursor(new TransferHeaderCursor(cursor, Group.DAILY));
        RecyclerView list = listWith(adapter);
        assertNotDatedAhead(viewAt(list, 1, R.id.future_image_view));
    }

    @Test
    public void theCalendarDoesNotShowOnATransactionDatedWithAnOffset() {
        MatrixCursor cursor = new MatrixCursor(TRANSACTION_COLUMNS);
        cursor.addRow(transaction(1L, minutesFromNow(60) + "+14:00", 1));
        TransactionCursorAdapter adapter = transactionAdapter();
        adapter.changeCursor(cursor);
        RecyclerView list = listWith(adapter);
        assertNotDatedAhead(viewAt(list, 0, R.id.future_image_view));
    }

    @Test
    public void theCalendarDoesNotShowOnATransferDatedWithAnOffset() {
        MatrixCursor cursor = new MatrixCursor(TRANSFER_COLUMNS);
        cursor.addRow(transfer(1L, minutesFromNow(60) + "+14:00"));
        TransferCursorAdapter adapter = new TransferCursorAdapter(null);
        adapter.changeCursor(new TransferHeaderCursor(cursor, Group.DAILY));
        RecyclerView list = listWith(adapter);
        assertNotDatedAhead(viewAt(list, 1, R.id.future_image_view));
    }

    @Test
    public void aRecycledTransactionRowHidesTheCalendarWhenItsNextRowIsNotDatedAhead() {
        MatrixCursor aheadCursor = new MatrixCursor(TRANSACTION_COLUMNS);
        aheadCursor.addRow(transaction(1L, yearsFromNow(1), 1));
        TransactionCursorAdapter adapter = transactionAdapter();
        adapter.changeCursor(aheadCursor);
        RecyclerView list = listWith(adapter);
        RecyclerView.ViewHolder before = list.findViewHolderForAdapterPosition(0);
        assertNotNull(before);
        assertDatedAhead(list, before.itemView.findViewById(R.id.future_image_view));
        MatrixCursor backCursor = new MatrixCursor(TRANSACTION_COLUMNS);
        backCursor.addRow(transaction(2L, yearsFromNow(-1), 1));
        adapter.changeCursor(backCursor);
        list.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 1080, 2400);
        RecyclerView.ViewHolder after = list.findViewHolderForAdapterPosition(0);
        assertNotNull(after);
        assertSame("the row was not reused, so this did not test a rebind", before.itemView, after.itemView);
        assertNotDatedAhead(after.itemView.findViewById(R.id.future_image_view));
    }

    @Test
    public void aRecycledTransferRowHidesTheCalendarWhenItsNextRowIsNotDatedAhead() {
        MatrixCursor aheadCursor = new MatrixCursor(TRANSFER_COLUMNS);
        aheadCursor.addRow(transfer(1L, yearsFromNow(1)));
        TransferCursorAdapter adapter = new TransferCursorAdapter(null);
        adapter.changeCursor(new TransferHeaderCursor(aheadCursor, Group.DAILY));
        RecyclerView list = listWith(adapter);
        RecyclerView.ViewHolder before = list.findViewHolderForAdapterPosition(1);
        assertNotNull(before);
        assertDatedAhead(list, before.itemView.findViewById(R.id.future_image_view));
        MatrixCursor backCursor = new MatrixCursor(TRANSFER_COLUMNS);
        backCursor.addRow(transfer(2L, yearsFromNow(-1)));
        adapter.changeCursor(new TransferHeaderCursor(backCursor, Group.DAILY));
        list.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 1080, 2400);
        RecyclerView.ViewHolder after = list.findViewHolderForAdapterPosition(1);
        assertNotNull(after);
        assertSame("the row was not reused, so this did not test a rebind", before.itemView, after.itemView);
        assertNotDatedAhead(after.itemView.findViewById(R.id.future_image_view));
    }
}
