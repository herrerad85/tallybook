package com.oriondev.moneywallet.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.LockMode;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.notification.NotificationContract;
import com.oriondev.moneywallet.utils.DateUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowNotificationManager;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * Runs the recurrence task against the real content provider over a fresh database. The provider
 * insert posts every row a recurrence has due when it is saved and moves its pointer past today,
 * so each recurrence here starts in the future and has its pointer moved back afterwards, which
 * is the state the task finds when a row comes due after the save.
 */
@RunWith(RobolectricTestRunner.class)
public class RecurrenceHandlerIntentServiceTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private static final long DAY = 24L * 60L * 60L * 1000L;

    private Context mContext;
    private ContentResolver mResolver;
    private ShadowNotificationManager mNotifications;
    private long mWalletA;
    private long mWalletB;
    private long mCategory;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(mContext);
        mResolver = mContext.getContentResolver();
        PreferenceManager.setCurrentLockMode(LockMode.NONE);
        NotificationManager manager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancelAll();
        mNotifications = shadowOf(manager);
        mWalletA = insertWallet("A");
        mWalletB = insertWallet("B");
        mCategory = insertCategory("Groceries");
    }

    @After
    public void tearDown() {
        PreferenceManager.setCurrentLockMode(LockMode.NONE);
        ((NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
    }

    @Test
    public void oneDueTransactionNotifiesOnceWithItsDescription() {
        makeDue(insertRecurrentTransaction("Rent"), 5, true);
        runTask();
        assertEquals(1, mNotifications.getAllNotifications().size());
        Notification notification = recurrenceNotification();
        assertEquals(NotificationContract.NOTIFICATION_CHANNEL_RECURRENCE, notification.getChannelId());
        assertEquals(NotificationManager.IMPORTANCE_LOW, ((NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE))
                .getNotificationChannel(NotificationContract.NOTIFICATION_CHANNEL_RECURRENCE).getImportance());
        assertEquals(mContext.getString(R.string.notification_title_recurrence_added), title(notification));
        assertEquals("Rent", text(notification));
    }

    @Test
    public void aDueTransactionWithoutDescriptionShowsItsCategory() {
        makeDue(insertRecurrentTransaction(""), 5, true);
        runTask();
        assertEquals("Groceries", text(recurrenceNotification()));
    }

    @Test
    public void aDueTransferWithoutDescriptionShowsItsWallets() {
        makeDue(insertRecurrentTransfer(""), 5, false);
        runTask();
        Notification notification = recurrenceNotification();
        assertEquals(mContext.getString(R.string.notification_title_recurrence_added), title(notification));
        assertEquals("Transfer: A -> B", text(notification));
    }

    @Test
    public void twoDueRowsInOneRunShowTheCountAndNoLabel() {
        // weekly from ten days back is due ten and three days ago
        makeDue(insertRecurrentTransaction("Rent", "FREQ=WEEKLY"), 10, true);
        runTask();
        Notification notification = recurrenceNotification();
        assertEquals(plural(2), title(notification));
        assertNull(text(notification));
    }

    @Test
    public void aSecondRunAddsToTheNotificationStillShowing() {
        makeDue(insertRecurrentTransaction("Rent"), 5, true);
        runTask();
        assertEquals("Rent", text(recurrenceNotification()));
        makeDue(insertRecurrentTransaction("Gym"), 5, true);
        runTask();
        assertEquals(1, mNotifications.getAllNotifications().size());
        Notification notification = recurrenceNotification();
        assertEquals(plural(2), title(notification));
        assertNull(text(notification));
        makeDue(insertRecurrentTransaction("Phone"), 5, true);
        runTask();
        assertEquals(plural(3), title(recurrenceNotification()));
    }

    @Test
    public void oneServiceRunTwiceCountsOnlyWhatEachRunPosted() {
        // the system reuses one service instance for every piece of queued work
        RecurrenceHandlerIntentService service = Robolectric.buildService(RecurrenceHandlerIntentService.class).create().get();
        makeDue(insertRecurrentTransaction("Rent"), 5, true);
        service.onHandleWork(new Intent());
        Notification before = recurrenceNotification();
        assertEquals("Rent", text(before));
        service.onHandleWork(new Intent());
        assertSame(before, recurrenceNotification());
    }

    @Test
    public void theLockHidesTheLabel() {
        PreferenceManager.setCurrentLockMode(LockMode.PIN);
        makeDue(insertRecurrentTransaction("Rent"), 5, true);
        runTask();
        Notification notification = recurrenceNotification();
        assertEquals(mContext.getString(R.string.notification_title_recurrence_added), title(notification));
        assertNull(text(notification));
    }

    @Test
    public void withoutTheLockThePublicVersionCarriesNoLabel() {
        makeDue(insertRecurrentTransaction("Rent"), 5, true);
        runTask();
        Notification notification = recurrenceNotification();
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility);
        assertNotNull(notification.publicVersion);
        assertEquals(mContext.getString(R.string.notification_title_recurrence_added), title(notification.publicVersion));
        assertNull(text(notification.publicVersion));
    }

    @Test
    public void nothingDueLeavesTheNotificationAlone() {
        runTask();
        assertTrue(mNotifications.getAllNotifications().isEmpty());
        makeDue(insertRecurrentTransaction("Rent"), 5, true);
        runTask();
        Notification before = recurrenceNotification();
        runTask();
        assertSame(before, recurrenceNotification());
        assertEquals(1, mNotifications.getAllNotifications().size());
    }

    @Test
    public void aDueBudgetPeriodAlonePostsNoNotification() {
        String start = DateUtils.getSQLDateString(new Date(System.currentTimeMillis() - 70L * DAY));
        String end = DateUtils.getSQLDateString(new Date(System.currentTimeMillis() - 40L * DAY));
        ContentValues values = new ContentValues();
        values.put(Contract.Budget.TYPE, Contract.BudgetType.EXPENSES.getValue());
        values.put(Contract.Budget.MONEY, 100000L);
        values.put(Contract.Budget.WALLET_IDS, "<" + mWalletA + ">");
        values.put(Contract.Budget.START_DATE, start);
        values.put(Contract.Budget.END_DATE, end);
        values.put(Contract.Budget.RULE, "FREQ=MONTHLY");
        values.put(Contract.Budget.RULE_START, start);
        mResolver.insert(DataContentProvider.CONTENT_BUDGETS, values);
        int before = countRows(DataContentProvider.CONTENT_BUDGETS);
        runTask();
        // the run did open a period, so the silence is not a run that did nothing
        assertTrue(countRows(DataContentProvider.CONTENT_BUDGETS) > before);
        assertTrue(mNotifications.getAllNotifications().isEmpty());
    }

    private void runTask() {
        RecurrenceHandlerIntentService service = Robolectric.buildService(RecurrenceHandlerIntentService.class).create().get();
        service.onHandleWork(new Intent());
    }

    private Notification recurrenceNotification() {
        Notification notification = mNotifications.getNotification(NotificationContract.NOTIFICATION_ID_RECURRENCE);
        assertNotNull(notification);
        return notification;
    }

    private String plural(int count) {
        return mContext.getResources().getQuantityString(R.plurals.notification_title_recurrences_added, count, count);
    }

    private static String title(Notification notification) {
        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        return title != null ? title.toString() : null;
    }

    private static String text(Notification notification) {
        CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        return text != null ? text.toString() : null;
    }

    /**
     * Moves the pointer of a recurrence the given number of days back through the provider, the
     * way the task itself writes it, and checks it landed there.
     */
    private void makeDue(long id, int daysAgo, boolean transaction) {
        Uri uri = ContentUris.withAppendedId(transaction ? DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS
                : DataContentProvider.CONTENT_RECURRENT_TRANSFERS, id);
        String pointer = DateUtils.getSQLDateString(new Date(System.currentTimeMillis() - daysAgo * DAY));
        ContentValues values = new ContentValues();
        values.put(transaction ? Contract.RecurrentTransaction.LAST_OCCURRENCE : Contract.RecurrentTransfer.LAST_OCCURRENCE, pointer);
        values.put(transaction ? Contract.RecurrentTransaction.NEXT_OCCURRENCE : Contract.RecurrentTransfer.NEXT_OCCURRENCE, pointer);
        assertEquals(1, mResolver.update(uri, values, null, null));
        String stored = readString(uri, transaction ? Contract.RecurrentTransaction.NEXT_OCCURRENCE : Contract.RecurrentTransfer.NEXT_OCCURRENCE);
        assertEquals(pointer, stored);
        assertTrue(DateUtils.getDateFromSQLDateString(stored).getTime() < System.currentTimeMillis() - DAY);
    }

    private String readString(Uri uri, String column) {
        Cursor cursor = mResolver.query(uri, null, null, null, null);
        assertNotNull(cursor);
        try {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(cursor.getColumnIndex(column));
        } finally {
            cursor.close();
        }
    }

    private int countRows(Uri uri) {
        Cursor cursor = mResolver.query(uri, null, null, null, null);
        assertNotNull(cursor);
        try {
            return cursor.getCount();
        } finally {
            cursor.close();
        }
    }

    private static String futureStart() {
        return DateUtils.getSQLDateString(new Date(System.currentTimeMillis() + 30L * DAY));
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

    private long insertCategory(String name) {
        ContentValues values = new ContentValues();
        values.put(Contract.Category.NAME, name);
        values.put(Contract.Category.ICON, ICON);
        values.put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.getValue());
        values.put(Contract.Category.SHOW_REPORT, true);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, values));
    }

    private long insertRecurrentTransaction(String description) {
        return insertRecurrentTransaction(description, "FREQ=MONTHLY");
    }

    private long insertRecurrentTransaction(String description, String rule) {
        ContentValues values = new ContentValues();
        values.put(Contract.RecurrentTransaction.MONEY, 1000L);
        values.put(Contract.RecurrentTransaction.DESCRIPTION, description);
        values.put(Contract.RecurrentTransaction.CATEGORY_ID, mCategory);
        values.put(Contract.RecurrentTransaction.DIRECTION, Contract.Direction.EXPENSE);
        values.put(Contract.RecurrentTransaction.WALLET_ID, mWalletA);
        values.put(Contract.RecurrentTransaction.CONFIRMED, true);
        values.put(Contract.RecurrentTransaction.COUNT_IN_TOTAL, true);
        values.put(Contract.RecurrentTransaction.START_DATE, futureStart());
        values.put(Contract.RecurrentTransaction.RULE, rule);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS, values));
    }

    private long insertRecurrentTransfer(String description) {
        ContentValues values = new ContentValues();
        values.put(Contract.RecurrentTransfer.DESCRIPTION, description);
        values.put(Contract.RecurrentTransfer.WALLET_FROM_ID, mWalletA);
        values.put(Contract.RecurrentTransfer.WALLET_TO_ID, mWalletB);
        values.put(Contract.RecurrentTransfer.MONEY_FROM, 1000L);
        values.put(Contract.RecurrentTransfer.MONEY_TO, 1000L);
        values.put(Contract.RecurrentTransfer.MONEY_TAX, 0L);
        values.put(Contract.RecurrentTransfer.CONFIRMED, true);
        values.put(Contract.RecurrentTransfer.COUNT_IN_TOTAL, true);
        values.put(Contract.RecurrentTransfer.START_DATE, futureStart());
        values.put(Contract.RecurrentTransfer.RULE, "FREQ=MONTHLY");
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_RECURRENT_TRANSFERS, values));
    }
}
