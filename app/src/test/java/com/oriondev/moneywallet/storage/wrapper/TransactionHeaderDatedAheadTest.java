package com.oriondev.moneywallet.storage.wrapper;

import android.database.MatrixCursor;

import com.oriondev.moneywallet.model.Group;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.utils.DateUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Calendar;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

@RunWith(RobolectricTestRunner.class)
public class TransactionHeaderDatedAheadTest {

    private static final long AHEAD_ID = 1L;
    private static final long BACK_ID = 2L;

    private static final String[] COLUMNS = new String[] {
            Contract.Transaction.ID,
            Contract.Transaction.DATE,
            Contract.Transaction.DIRECTION,
            Contract.Transaction.MONEY,
            Contract.Transaction.WALLET_CURRENCY,
            Contract.Transaction.CONFIRMED,
            Contract.Transaction.COUNT_IN_TOTAL,
            Contract.Transaction.CATEGORY_TAG
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

    private static TransactionHeaderCursor aheadAndBack() {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.addRow(new Object[] {AHEAD_ID, yearsFromNow(1), Contract.Direction.EXPENSE, 4000L, "EUR", 1, 1, null});
        cursor.addRow(new Object[] {BACK_ID, yearsFromNow(-1), Contract.Direction.EXPENSE, 1000L, "EUR", 1, 1, null});
        return new TransactionHeaderCursor(cursor, Group.DAILY, null, null);
    }

    private static String headerString(TransactionHeaderCursor headers, int position, String column) {
        assertTrue(headers.isHeaderAt(position));
        assertTrue(headers.moveToPosition(position));
        return headers.getString(headers.getColumnIndexOrThrow(column));
    }

    @Test
    public void aRowDatedAheadAddsNothingToItsHeader() {
        TransactionHeaderCursor headers = aheadAndBack();
        assertEquals("", headerString(headers, 0, TransactionHeaderCursor.COLUMN_HEADER_EXPENSE));
        assertEquals("", headerString(headers, 0, TransactionHeaderCursor.COLUMN_HEADER_MONEY));
        assertEquals("EUR 1000", headerString(headers, 2, TransactionHeaderCursor.COLUMN_HEADER_EXPENSE));
        assertEquals("EUR -1000", headerString(headers, 2, TransactionHeaderCursor.COLUMN_HEADER_MONEY));
    }

    @Test
    public void aRowDatedAheadIsStillListedUnderItsOwnHeader() {
        TransactionHeaderCursor headers = aheadAndBack();
        assertEquals(4, headers.getCount());
        assertTrue(headers.isHeaderAt(0));
        assertFalse(headers.isHeaderAt(1));
        assertTrue(headers.isHeaderAt(2));
        assertFalse(headers.isHeaderAt(3));
        assertTrue(headers.moveToPosition(1));
        assertEquals(AHEAD_ID, headers.getLong(headers.getColumnIndexOrThrow(Contract.Transaction.ID)));
        assertTrue(headers.moveToPosition(3));
        assertEquals(BACK_ID, headers.getLong(headers.getColumnIndexOrThrow(Contract.Transaction.ID)));
    }

    @Test
    public void aRowDatedAheadByMinutesAddsNothingToItsHeader() {
        String date = minutesFromNow(10);
        assumeTrue(date.compareTo(DateUtils.getSQLDateTimeString(new Date())) > 0);
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.addRow(new Object[] {AHEAD_ID, date, Contract.Direction.EXPENSE, 4000L, "EUR", 1, 1, null});
        TransactionHeaderCursor headers = new TransactionHeaderCursor(cursor, Group.DAILY, null, null);
        assertEquals(2, headers.getCount());
        assertEquals("", headerString(headers, 0, TransactionHeaderCursor.COLUMN_HEADER_EXPENSE));
        assertEquals("", headerString(headers, 0, TransactionHeaderCursor.COLUMN_HEADER_MONEY));
        assertFalse(headers.isHeaderAt(1));
        assertTrue(headers.moveToPosition(1));
        assertEquals(AHEAD_ID, headers.getLong(headers.getColumnIndexOrThrow(Contract.Transaction.ID)));
    }

    @Test
    public void aRowDatedMinutesAgoAddsToItsHeader() {
        String date = minutesFromNow(-10);
        assumeTrue(date.compareTo(DateUtils.getSQLDateTimeString(new Date())) < 0);
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.addRow(new Object[] {BACK_ID, date, Contract.Direction.EXPENSE, 1000L, "EUR", 1, 1, null});
        TransactionHeaderCursor headers = new TransactionHeaderCursor(cursor, Group.DAILY, null, null);
        assertEquals(2, headers.getCount());
        assertEquals("EUR 1000", headerString(headers, 0, TransactionHeaderCursor.COLUMN_HEADER_EXPENSE));
        assertEquals("EUR -1000", headerString(headers, 0, TransactionHeaderCursor.COLUMN_HEADER_MONEY));
        assertFalse(headers.isHeaderAt(1));
        assertTrue(headers.moveToPosition(1));
        assertEquals(BACK_ID, headers.getLong(headers.getColumnIndexOrThrow(Contract.Transaction.ID)));
    }

    @Test
    public void aRowDatedWithAnOffsetAddsToItsHeader() {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.addRow(new Object[] {BACK_ID, minutesFromNow(60) + "+14:00", Contract.Direction.EXPENSE, 1000L, "EUR", 1, 1, null});
        TransactionHeaderCursor headers = new TransactionHeaderCursor(cursor, Group.DAILY, null, null);
        assertEquals(2, headers.getCount());
        assertFalse(headers.isHeaderAt(1));
        assertTrue(headers.moveToPosition(1));
        assertEquals(BACK_ID, headers.getLong(headers.getColumnIndexOrThrow(Contract.Transaction.ID)));
        assertEquals("EUR 1000", headerString(headers, 0, TransactionHeaderCursor.COLUMN_HEADER_EXPENSE));
        assertEquals("EUR -1000", headerString(headers, 0, TransactionHeaderCursor.COLUMN_HEADER_MONEY));
    }
}
