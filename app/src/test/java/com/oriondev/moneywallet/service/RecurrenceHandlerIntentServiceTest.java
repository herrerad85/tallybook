package com.oriondev.moneywallet.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.job.JobScheduler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

import androidx.core.app.JobIntentService;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.LockMode;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.SplitLedger;
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
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
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

    @Test
    public void aDueTransferThatNeedsARateIsHeldAndNotified() {
        long dollars = insertWallet("Dollars", "USD");
        long euros = insertWallet("Euros", "EUR");
        long id = insertRecurrentTransfer("Savings", dollars, euros, 1000L, 0L, "FREQ=DAILY");
        makeDue(id, 3, false);
        String last = readString(recurrentTransferUri(id), Contract.RecurrentTransfer.LAST_OCCURRENCE);
        String next = readString(recurrentTransferUri(id), Contract.RecurrentTransfer.NEXT_OCCURRENCE);
        int before = countRows(DataContentProvider.CONTENT_TRANSFERS);
        runTask();
        assertEquals(before, countRows(DataContentProvider.CONTENT_TRANSFERS));
        assertTrue(postedTransfers(dollars).isEmpty());
        assertEquals(last, readString(recurrentTransferUri(id), Contract.RecurrentTransfer.LAST_OCCURRENCE));
        assertEquals(next, readString(recurrentTransferUri(id), Contract.RecurrentTransfer.NEXT_OCCURRENCE));
        assertNull(mNotifications.getNotification(NotificationContract.NOTIFICATION_ID_RECURRENCE));
        assertEquals(1, mNotifications.getAllNotifications().size());
        assertEquals(23461, NotificationContract.NOTIFICATION_ID_RECURRENCE_NEEDS_RATE);
        Notification notification = needsRateNotification();
        assertEquals(NotificationContract.NOTIFICATION_CHANNEL_RECURRENCE, notification.getChannelId());
        assertEquals("A recurring transfer needs an exchange rate", title(notification));
        assertEquals(mContext.getString(R.string.notification_title_recurrence_needs_rate), title(notification));
        assertEquals("Savings", text(notification));
    }

    @Test
    public void aHeldTransferGivenARatePostsEveryHeldDateOnTheNextRun() {
        long dollars = insertWallet("Dollars", "USD");
        long euros = insertWallet("Euros", "EUR");
        long id = insertRecurrentTransfer("Savings", dollars, euros, 1000L, 0L, "FREQ=DAILY");
        int daysAgo = 3;
        makeDue(id, daysAgo, false);
        String next = readString(recurrentTransferUri(id), Contract.RecurrentTransfer.NEXT_OCCURRENCE);
        runTask();
        assertTrue(postedTransfers(dollars).isEmpty());
        ContentValues values = new ContentValues();
        values.put(Contract.RecurrentTransfer.DESCRIPTION, "Savings");
        values.put(Contract.RecurrentTransfer.WALLET_FROM_ID, dollars);
        values.put(Contract.RecurrentTransfer.WALLET_TO_ID, euros);
        values.put(Contract.RecurrentTransfer.MONEY_FROM, 1000L);
        values.put(Contract.RecurrentTransfer.MONEY_TO, 920L);
        values.put(Contract.RecurrentTransfer.MONEY_TAX, 0L);
        values.put(Contract.RecurrentTransfer.CONFIRMED, true);
        values.put(Contract.RecurrentTransfer.COUNT_IN_TOTAL, true);
        values.put(Contract.RecurrentTransfer.START_DATE, readString(recurrentTransferUri(id), Contract.RecurrentTransfer.START_DATE));
        values.put(Contract.RecurrentTransfer.RULE, "FREQ=DAILY");
        assertEquals(1, mResolver.update(recurrentTransferUri(id), values, null, null));
        assertEquals(next, readString(recurrentTransferUri(id), Contract.RecurrentTransfer.NEXT_OCCURRENCE));
        runTask();
        List<String> expected = dailyRows(next, 920L);
        assertEquals(expected, postedTransfers(dollars));
        String today = DateUtils.getSQLDateString(new Date());
        assertTrue(readString(recurrentTransferUri(id), Contract.RecurrentTransfer.NEXT_OCCURRENCE).compareTo(today) > 0);
    }

    @Test
    public void twoTransfersThatNeedARateShowTheCountAndNoLabel() {
        long dollars = insertWallet("Dollars", "USD");
        long euros = insertWallet("Euros", "EUR");
        makeDue(insertRecurrentTransfer("Savings", dollars, euros, 1000L, 0L, "FREQ=DAILY"), 3, false);
        makeDue(insertRecurrentTransfer("Rent", dollars, euros, 5000L, 0L, "FREQ=DAILY"), 3, false);
        runTask();
        Notification notification = needsRateNotification();
        assertEquals("2 recurring transfers need an exchange rate", title(notification));
        assertEquals(mContext.getResources().getQuantityString(R.plurals.notification_title_recurrences_need_rate, 2, 2), title(notification));
        assertNull(text(notification));
    }

    @Test
    public void theLockHidesTheLabelOfATransferThatNeedsARate() {
        PreferenceManager.setCurrentLockMode(LockMode.PIN);
        long dollars = insertWallet("Dollars", "USD");
        long euros = insertWallet("Euros", "EUR");
        makeDue(insertRecurrentTransfer("Savings", dollars, euros, 1000L, 0L, "FREQ=DAILY"), 3, false);
        runTask();
        Notification notification = needsRateNotification();
        assertEquals(mContext.getString(R.string.notification_title_recurrence_needs_rate), title(notification));
        assertNull(text(notification));
        if (notification.publicVersion != null) {
            assertNull(text(notification.publicVersion));
        }
    }

    @Test
    public void withoutTheLockThePublicVersionOfTheRateNotificationCarriesNoLabel() {
        long dollars = insertWallet("Dollars", "USD");
        long euros = insertWallet("Euros", "EUR");
        makeDue(insertRecurrentTransfer("Savings", dollars, euros, 1000L, 0L, "FREQ=DAILY"), 3, false);
        runTask();
        Notification notification = needsRateNotification();
        assertEquals("Savings", text(notification));
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility);
        assertNotNull(notification.publicVersion);
        assertEquals(mContext.getString(R.string.notification_title_recurrence_needs_rate), title(notification.publicVersion));
        assertNull(text(notification.publicVersion));
    }

    @Test
    public void aSameCurrencyTransferOfNothingStillPosts() {
        long id = insertRecurrentTransfer("Nothing", mWalletA, mWalletB, 0L, 0L, "FREQ=DAILY");
        assertDailyTransferPosts(id, mWalletA, 0L);
    }

    @Test
    public void aSameCurrencyTransferThatReceivesNothingStillPosts() {
        long id = insertRecurrentTransfer("Nothing back", mWalletA, mWalletB, 1000L, 0L, "FREQ=DAILY");
        assertDailyTransferPosts(id, mWalletA, 0L);
    }

    @Test
    public void aCrossCurrencyTransferOfNothingStillPosts() {
        long dollars = insertWallet("Dollars", "USD");
        long id = insertRecurrentTransfer("Nothing", dollars, insertWallet("Euros", "EUR"), 0L, 0L, "FREQ=DAILY");
        assertDailyTransferPosts(id, dollars, 0L);
    }

    @Test
    public void aCrossCurrencyTransferWithAnAmountReceivedStillPosts() {
        long dollars = insertWallet("Dollars", "USD");
        long id = insertRecurrentTransfer("Savings", dollars, insertWallet("Euros", "EUR"), 1000L, 920L, "FREQ=DAILY");
        assertDailyTransferPosts(id, dollars, 920L);
    }

    @Test
    public void aTransferThatNeedsARateDoesNotHoldTheOthers() {
        long dollars = insertWallet("Dollars", "USD");
        long euros = insertWallet("Euros", "EUR");
        long held = insertRecurrentTransfer("Savings", dollars, euros, 1000L, 0L, "FREQ=DAILY");
        long normal = insertRecurrentTransfer("Rent", mWalletA, mWalletB, 1000L, 1000L, "FREQ=DAILY");
        int daysAgo = 3;
        makeDue(held, daysAgo, false);
        makeDue(normal, daysAgo, false);
        String heldLast = readString(recurrentTransferUri(held), Contract.RecurrentTransfer.LAST_OCCURRENCE);
        String heldNext = readString(recurrentTransferUri(held), Contract.RecurrentTransfer.NEXT_OCCURRENCE);
        List<String> expected = dailyRows(readString(recurrentTransferUri(normal), Contract.RecurrentTransfer.NEXT_OCCURRENCE), 1000L);
        runTask();
        assertEquals(expected, postedTransfers(mWalletA));
        String today = DateUtils.getSQLDateString(new Date());
        assertTrue(readString(recurrentTransferUri(normal), Contract.RecurrentTransfer.NEXT_OCCURRENCE).compareTo(today) > 0);
        assertTrue(postedTransfers(dollars).isEmpty());
        assertEquals(heldLast, readString(recurrentTransferUri(held), Contract.RecurrentTransfer.LAST_OCCURRENCE));
        assertEquals(heldNext, readString(recurrentTransferUri(held), Contract.RecurrentTransfer.NEXT_OCCURRENCE));
        assertEquals(2, mNotifications.getAllNotifications().size());
        assertEquals(plural(expected.size()), title(recurrenceNotification()));
        assertEquals("Savings", text(needsRateNotification()));
    }

    @Test
    public void aNormalTransferNextToOneThatNeedsARateStillPosts() {
        long held = insertRecurrentTransfer("Savings", insertWallet("Dollars", "USD"), insertWallet("Euros", "EUR"), 1000L, 0L, "FREQ=DAILY");
        long normal = insertRecurrentTransfer("Rent", mWalletA, mWalletB, 1000L, 1000L, "FREQ=DAILY");
        int daysAgo = 3;
        makeDue(held, daysAgo, false);
        makeDue(normal, daysAgo, false);
        List<String> expected = dailyRows(readString(recurrentTransferUri(normal), Contract.RecurrentTransfer.NEXT_OCCURRENCE), 1000L);
        runTask();
        assertEquals(expected, postedTransfers(mWalletA));
        String today = DateUtils.getSQLDateString(new Date());
        assertTrue(readString(recurrentTransferUri(normal), Contract.RecurrentTransfer.NEXT_OCCURRENCE).compareTo(today) > 0);
        assertNotNull(mNotifications.getNotification(NotificationContract.NOTIFICATION_ID_RECURRENCE));
    }

    @Test
    public void aRunWithOnlyATransferThatNeedsARateDoesNotStartItselfAgain() {
        ReflectionHelpers.<Map<?, ?>>getStaticField(JobIntentService.class, "sClassWorkEnqueuer").clear();
        JobScheduler scheduler = (JobScheduler) mContext.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        makeDue(insertRecurrentTransfer("Savings", insertWallet("Dollars", "USD"), insertWallet("Euros", "EUR"), 1000L, 0L, "FREQ=DAILY"), 3, false);
        int runs = 0;
        do {
            scheduler.cancelAll();
            runTask();
            runs++;
        } while (!scheduler.getAllPendingJobs().isEmpty() && runs < 5);
        assertEquals(1, runs);
        assertTrue(scheduler.getAllPendingJobs().isEmpty());
    }

    @Test
    public void aRunWithOnlyAHeldTransferDueTodayNotifiesItAndDoesNotStartItselfAgain() {
        ReflectionHelpers.<Map<?, ?>>getStaticField(JobIntentService.class, "sClassWorkEnqueuer").clear();
        JobScheduler scheduler = (JobScheduler) mContext.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        long id = insertRecurrentTransfer("Savings", insertWallet("Dollars", "USD"), insertWallet("Euros", "EUR"), 1000L, 0L, "FREQ=DAILY");
        String today = DateUtils.getSQLDateString(new Date());
        ContentValues values = new ContentValues();
        values.put(Contract.RecurrentTransfer.LAST_OCCURRENCE, today);
        values.put(Contract.RecurrentTransfer.NEXT_OCCURRENCE, today);
        assertEquals(1, mResolver.update(recurrentTransferUri(id), values, null, null));
        int runs = 0;
        do {
            scheduler.cancelAll();
            runTask();
            runs++;
        } while (!scheduler.getAllPendingJobs().isEmpty() && runs < 5);
        assertEquals(1, runs);
        assertTrue(scheduler.getAllPendingJobs().isEmpty());
        assertEquals("Savings", text(needsRateNotification()));
        assertEquals(today, readString(recurrentTransferUri(id), Contract.RecurrentTransfer.LAST_OCCURRENCE));
        assertEquals(today, readString(recurrentTransferUri(id), Contract.RecurrentTransfer.NEXT_OCCURRENCE));
    }

    @Test
    public void savingARateClearsTheRateNotificationOnTheNextRun() {
        long dollars = insertWallet("Dollars", "USD");
        long euros = insertWallet("Euros", "EUR");
        long id = insertRecurrentTransfer("Savings", dollars, euros, 1000L, 0L, "FREQ=DAILY");
        makeDue(id, 3, false);
        runTask();
        needsRateNotification();
        ContentValues values = new ContentValues();
        values.put(Contract.RecurrentTransfer.DESCRIPTION, "Savings");
        values.put(Contract.RecurrentTransfer.WALLET_FROM_ID, dollars);
        values.put(Contract.RecurrentTransfer.WALLET_TO_ID, euros);
        values.put(Contract.RecurrentTransfer.MONEY_FROM, 1000L);
        values.put(Contract.RecurrentTransfer.MONEY_TO, 920L);
        values.put(Contract.RecurrentTransfer.MONEY_TAX, 0L);
        values.put(Contract.RecurrentTransfer.CONFIRMED, true);
        values.put(Contract.RecurrentTransfer.COUNT_IN_TOTAL, true);
        values.put(Contract.RecurrentTransfer.START_DATE, readString(recurrentTransferUri(id), Contract.RecurrentTransfer.START_DATE));
        values.put(Contract.RecurrentTransfer.RULE, "FREQ=DAILY");
        assertEquals(1, mResolver.update(recurrentTransferUri(id), values, null, null));
        runTask();
        assertNull(mNotifications.getNotification(NotificationContract.NOTIFICATION_ID_RECURRENCE_NEEDS_RATE));
        recurrenceNotification();
    }

    @Test
    public void oneServiceRunTwiceWithAHeldTransferNamesItOnce() {
        RecurrenceHandlerIntentService service = Robolectric.buildService(RecurrenceHandlerIntentService.class).create().get();
        long dollars = insertWallet("Dollars", "USD");
        long euros = insertWallet("Euros", "EUR");
        makeDue(insertRecurrentTransfer("Savings", dollars, euros, 1000L, 0L, "FREQ=DAILY"), 3, false);
        service.onHandleWork(new Intent());
        service.onHandleWork(new Intent());
        Notification notification = needsRateNotification();
        assertEquals(mContext.getString(R.string.notification_title_recurrence_needs_rate), title(notification));
        assertEquals("Savings", text(notification));
    }

    @Test
    public void aHeldTransferDueInTwoDaysIsNotNotified() {
        long dollars = insertWallet("Dollars", "USD");
        long euros = insertWallet("Euros", "EUR");
        long id = insertRecurrentTransfer("Savings", dollars, euros, 1000L, 0L, "FREQ=DAILY");
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, 2);
        String pointer = DateUtils.getSQLDateString(calendar.getTime());
        ContentValues values = new ContentValues();
        values.put(Contract.RecurrentTransfer.LAST_OCCURRENCE, pointer);
        values.put(Contract.RecurrentTransfer.NEXT_OCCURRENCE, pointer);
        assertEquals(1, mResolver.update(recurrentTransferUri(id), values, null, null));
        runTask();
        assertNull(mNotifications.getNotification(NotificationContract.NOTIFICATION_ID_RECURRENCE_NEEDS_RATE));
        assertEquals(pointer, readString(recurrentTransferUri(id), Contract.RecurrentTransfer.LAST_OCCURRENCE));
        assertEquals(pointer, readString(recurrentTransferUri(id), Contract.RecurrentTransfer.NEXT_OCCURRENCE));
    }

    @Test
    public void aHeldTransferWithoutDescriptionShowsItsWalletsInOrder() {
        long dollars = insertWallet("Dollars", "USD");
        long euros = insertWallet("Euros", "EUR");
        makeDue(insertRecurrentTransfer("", dollars, euros, 1000L, 0L, "FREQ=DAILY"), 3, false);
        runTask();
        assertEquals(mContext.getString(R.string.notification_text_recurrence_transfer, "Dollars", "Euros"), text(needsRateNotification()));
    }

    @Test
    public void everyTransferADueRecurrencePostsIsLinkedToThatRecurrence() {
        long decoy = insertRecurrentTransfer("Decoy", mWalletB, mWalletA, 500L, 500L, "FREQ=DAILY");
        long id = insertRecurrentTransfer("Rent", mWalletA, mWalletB, 1000L, 1000L, "FREQ=DAILY");
        makeDue(id, 3, false);
        List<String> expected = dailyRows(readString(recurrentTransferUri(id), Contract.RecurrentTransfer.NEXT_OCCURRENCE), 1000L);
        runTask();
        assertTrue(expected.size() >= 2);
        assertEquals(expected, postedTransfers(mWalletA));
        assertTrue(postedTransfers(mWalletB).isEmpty());
        assertNotEquals(1L, id);
        assertNotEquals(decoy, id);
        List<Long> recurrences = storedTransferRecurrences();
        assertEquals(expected.size(), recurrences.size());
        for (Long recurrence : recurrences) {
            assertEquals(Long.valueOf(id), recurrence);
        }
    }

    private static List<String> dailyRows(String from, long moneyTo) {
        List<String> rows = new ArrayList<>();
        String today = DateUtils.getSQLDateString(new Date());
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(DateUtils.getDateFromSQLDateString(from));
        while (DateUtils.getSQLDateString(calendar.getTime()).compareTo(today) <= 0) {
            rows.add(DateUtils.getSQLDateString(calendar.getTime()) + " " + moneyTo);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        return rows;
    }

    private void assertDailyTransferPosts(long id, long fromWalletId, long moneyTo) {
        int daysAgo = 3;
        makeDue(id, daysAgo, false);
        List<String> expected = dailyRows(readString(recurrentTransferUri(id), Contract.RecurrentTransfer.NEXT_OCCURRENCE), moneyTo);
        runTask();
        assertEquals(expected, postedTransfers(fromWalletId));
        String today = DateUtils.getSQLDateString(new Date());
        assertTrue(readString(recurrentTransferUri(id), Contract.RecurrentTransfer.NEXT_OCCURRENCE).compareTo(today) > 0);
        assertEquals(plural(expected.size()), title(recurrenceNotification()));
        assertEquals(1, mNotifications.getAllNotifications().size());
    }

    @Test
    public void aRolledExpensesPeriodKeepsFollowingTheCategoryBudgetsInsideIt() {
        String start = DateUtils.getSQLDateString(new Date(System.currentTimeMillis() - 70L * DAY));
        String end = DateUtils.getSQLDateString(new Date(System.currentTimeMillis() - 40L * DAY));
        ContentValues expenses = monthlyBudget(Contract.BudgetType.EXPENSES, 100000L, start, end);
        expenses.put(Contract.Budget.TAG, Contract.Budget.TAG_FOLLOWS_CATEGORIES);
        mResolver.insert(DataContentProvider.CONTENT_BUDGETS, expenses);
        ContentValues category = monthlyBudget(Contract.BudgetType.CATEGORY, 30000L, start, end);
        category.put(Contract.Budget.CATEGORY_IDS, "<" + mCategory + ">");
        mResolver.insert(DataContentProvider.CONTENT_BUDGETS, category);
        runTask();
        SplitLedger.database(mContext).execSQL("UPDATE budgets SET budget_money = 45000 WHERE budget_type = ? " +
                "AND budget_start_date > ?", new Object[] {Contract.BudgetType.CATEGORY.getValue(), start});
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_BUDGETS,
                new String[] {Contract.Budget.START_DATE, Contract.Budget.TAG, Contract.Budget.MONEY},
                Contract.Budget.TYPE + " = ? AND " + Contract.Budget.RULE + " IS NOT NULL",
                new String[] {String.valueOf(Contract.BudgetType.EXPENSES.getValue())}, null);
        assertNotNull(cursor);
        try {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertTrue(start.compareTo(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.START_DATE))) < 0);
            assertEquals(Contract.Budget.TAG_FOLLOWS_CATEGORIES, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.TAG)));
            assertEquals(45000L, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Budget.MONEY)));
        } finally {
            cursor.close();
        }
    }

    private ContentValues monthlyBudget(Contract.BudgetType type, long money, String start, String end) {
        ContentValues values = new ContentValues();
        values.put(Contract.Budget.TYPE, type.getValue());
        values.put(Contract.Budget.MONEY, money);
        values.put(Contract.Budget.WALLET_IDS, "<" + mWalletA + ">");
        values.put(Contract.Budget.START_DATE, start);
        values.put(Contract.Budget.END_DATE, end);
        values.put(Contract.Budget.RULE, "FREQ=MONTHLY");
        values.put(Contract.Budget.RULE_START, start);
        return values;
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

    private Notification needsRateNotification() {
        Notification notification = mNotifications.getNotification(NotificationContract.NOTIFICATION_ID_RECURRENCE_NEEDS_RATE);
        assertNotNull(notification);
        return notification;
    }

    private static Uri recurrentTransferUri(long id) {
        return ContentUris.withAppendedId(DataContentProvider.CONTENT_RECURRENT_TRANSFERS, id);
    }

    private List<String> postedTransfers(long fromWalletId) {
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSFERS,
                new String[] {Contract.Transfer.DATE, Contract.Transfer.TRANSACTION_TO_MONEY},
                Contract.Transfer.TRANSACTION_FROM_WALLET_ID + " = ?", new String[] {String.valueOf(fromWalletId)},
                Contract.Transfer.DATE + " ASC");
        assertNotNull(cursor);
        List<String> rows = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                rows.add(cursor.getString(0).substring(0, 10) + " " + cursor.getLong(1));
            }
        } finally {
            cursor.close();
        }
        return rows;
    }

    private List<Long> storedTransferRecurrences() {
        Cursor cursor = SplitLedger.database(mContext).rawQuery("SELECT transaction_recurrence FROM transfers", null);
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
        return insertWallet(name, "EUR");
    }

    private long insertWallet(String name, String currency) {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, name);
        values.put(Contract.Wallet.ICON, ICON);
        values.put(Contract.Wallet.CURRENCY, currency);
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
        return insertRecurrentTransfer(description, mWalletA, mWalletB, 1000L, 1000L, "FREQ=MONTHLY");
    }

    private long insertRecurrentTransfer(String description, long from, long to, long moneyFrom, long moneyTo, String rule) {
        ContentValues values = new ContentValues();
        values.put(Contract.RecurrentTransfer.DESCRIPTION, description);
        values.put(Contract.RecurrentTransfer.WALLET_FROM_ID, from);
        values.put(Contract.RecurrentTransfer.WALLET_TO_ID, to);
        values.put(Contract.RecurrentTransfer.MONEY_FROM, moneyFrom);
        values.put(Contract.RecurrentTransfer.MONEY_TO, moneyTo);
        values.put(Contract.RecurrentTransfer.MONEY_TAX, 0L);
        values.put(Contract.RecurrentTransfer.CONFIRMED, true);
        values.put(Contract.RecurrentTransfer.COUNT_IN_TOTAL, true);
        values.put(Contract.RecurrentTransfer.START_DATE, futureStart());
        values.put(Contract.RecurrentTransfer.RULE, rule);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_RECURRENT_TRANSFERS, values));
    }
}
