package com.oriondev.moneywallet.storage.database;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.utils.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class RecurrentTransferLinkTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private Context mContext;
    private ContentResolver mResolver;
    private long mWalletA;
    private long mWalletB;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(mContext);
        mResolver = mContext.getContentResolver();
        mWalletA = insertWallet("A");
        mWalletB = insertWallet("B");
    }

    @Test
    public void savingARecurringTransferStartingTodayLinksTheOccurrenceItPostsForToday() {
        long decoy = insertRecurrentTransfer("Decoy", mWalletB, mWalletA, "FREQ=MONTHLY", 30);
        assertTrue(storedRecurrences("Decoy").isEmpty());
        long id = insertRecurrentTransfer("Rent", mWalletA, mWalletB, "FREQ=MONTHLY", 0);
        assertNotEquals(1L, id);
        assertNotEquals(decoy, id);
        List<Long> recurrences = storedRecurrences("Rent");
        assertEquals(1, recurrences.size());
        assertEquals(Long.valueOf(id), recurrences.get(0));
        assertEquals(DateUtils.getSQLDateString(new Date()), storedDates("Rent").get(0));
    }

    @Test
    public void savingADailyRecurringTransferStartedThreeDaysBackLinksEveryOccurrenceItPosts() {
        insertRecurrentTransfer("Decoy", mWalletB, mWalletA, "FREQ=DAILY", 30);
        long id = insertRecurrentTransfer("Rent", mWalletA, mWalletB, "FREQ=DAILY", -3);
        List<Long> recurrences = storedRecurrences("Rent");
        assertEquals(4, recurrences.size());
        for (Long recurrence : recurrences) {
            assertEquals(Long.valueOf(id), recurrence);
        }
    }

    @Test
    public void aTransferNamingARecurrenceThatNoLongerExistsIsStillStoredUnlinked() {
        long existing = insertRecurrentTransfer("Decoy", mWalletB, mWalletA, "FREQ=MONTHLY", 30);
        long missing = existing + 1000L;
        Cursor lookup = SplitLedger.database(mContext).rawQuery(
                "SELECT * FROM " + Schema.RecurrentTransfer.TABLE + " WHERE " + Schema.RecurrentTransfer.ID + " = " + missing, null);
        assertFalse(lookup.moveToFirst());
        lookup.close();
        ContentValues values = new TransferContentValuesBuilder()
                .description("Orphan")
                .date(DateUtils.getSQLDateTimeString(new Date()))
                .fromWalletId(mWalletA)
                .toWalletId(mWalletB)
                .taxWalletId(mWalletA)
                .fromMoney(1000L)
                .toMoney(1000L)
                .taxMoney(0L)
                .recurrenceId(missing)
                .confirmed(true)
                .countInTotal(true)
                .build();
        Uri uri = mResolver.insert(DataContentProvider.CONTENT_TRANSFERS, values);
        assertNotNull(uri);
        List<Long> recurrences = storedRecurrences("Orphan");
        assertEquals(1, recurrences.size());
        assertNull(recurrences.get(0));
    }

    private List<Long> storedRecurrences(String description) {
        Cursor cursor = SplitLedger.database(mContext).rawQuery("SELECT " + Schema.Transfer.RECURRENCE + " FROM " + Schema.Transfer.TABLE +
                " WHERE " + Schema.Transfer.DESCRIPTION + " = ? ORDER BY " + Schema.Transfer.DATE, new String[] {description});
        assertNotNull(cursor);
        List<Long> recurrences = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                recurrences.add(cursor.isNull(0) ? null : cursor.getLong(0));
            }
        } finally {
            cursor.close();
        }
        return recurrences;
    }

    private List<String> storedDates(String description) {
        Cursor cursor = SplitLedger.database(mContext).rawQuery("SELECT " + Schema.Transfer.DATE + " FROM " + Schema.Transfer.TABLE +
                " WHERE " + Schema.Transfer.DESCRIPTION + " = ? ORDER BY " + Schema.Transfer.DATE, new String[] {description});
        assertNotNull(cursor);
        List<String> dates = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                dates.add(cursor.getString(0).substring(0, 10));
            }
        } finally {
            cursor.close();
        }
        return dates;
    }

    private long insertWallet(String name) {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, name);
        values.put(Contract.Wallet.ICON, ICON);
        values.put(Contract.Wallet.CURRENCY, "EUR");
        values.put(Contract.Wallet.START_MONEY, 0L);
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        values.put(Contract.Wallet.ARCHIVED, false);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_WALLETS, values));
    }

    private long insertRecurrentTransfer(String description, long from, long to, String rule, int daysFromToday) {
        ContentValues values = new ContentValues();
        values.put(Contract.RecurrentTransfer.DESCRIPTION, description);
        values.put(Contract.RecurrentTransfer.WALLET_FROM_ID, from);
        values.put(Contract.RecurrentTransfer.WALLET_TO_ID, to);
        values.put(Contract.RecurrentTransfer.MONEY_FROM, 1000L);
        values.put(Contract.RecurrentTransfer.MONEY_TO, 1000L);
        values.put(Contract.RecurrentTransfer.MONEY_TAX, 0L);
        values.put(Contract.RecurrentTransfer.CONFIRMED, true);
        values.put(Contract.RecurrentTransfer.COUNT_IN_TOTAL, true);
        Calendar start = Calendar.getInstance();
        start.add(Calendar.DAY_OF_MONTH, daysFromToday);
        values.put(Contract.RecurrentTransfer.START_DATE, DateUtils.getSQLDateString(start.getTime()));
        values.put(Contract.RecurrentTransfer.RULE, rule);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_RECURRENT_TRANSFERS, values));
    }
}
