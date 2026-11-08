package com.oriondev.moneywallet.ui.fragment.secondary;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.JobIntentService;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.service.RecurrenceHandlerIntentService;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.database.TransferContentValuesBuilder;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment;
import com.oriondev.moneywallet.ui.notification.NotificationContract;
import com.oriondev.moneywallet.utils.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.util.ReflectionHelpers;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

/**
 * The wallet detail panel shows the group a wallet belongs to, and draws no row at all for a
 * wallet in no group, the same way it treats the note.
 */
@RunWith(RobolectricTestRunner.class)
public class WalletItemFragmentTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";
    private static final String TAG_FRAGMENT = "WalletItemFragmentTest::Fragment";

    private static final long DAY = 24L * 60L * 60L * 1000L;
    private static final long MARKER_TIMEOUT_SECONDS = 5L;

    private Context mContext;
    private ContentResolver mResolver;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(mContext);
        mResolver = mContext.getContentResolver();
    }

    @Test
    public void aWalletInAGroupShowsTheGroup() {
        long wallet = insertWallet("Checking", "Savings");
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TextView group = showWallet(activity, wallet);
                assertEquals(View.VISIBLE, group.getVisibility());
                assertEquals("Savings", group.getText().toString());
            });
        }
    }

    @Test
    public void aWalletInNoGroupDrawsNoGroupRow() {
        long wallet = insertWallet("Checking", null);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TextView group = showWallet(activity, wallet);
                assertEquals(View.GONE, group.getVisibility());
            });
        }
    }

    @Test
    public void aPanelReusedForAGroupedWalletShowsTheRowItHidForTheLastOne() {
        long ungrouped = insertWallet("Checking", null);
        long grouped = insertWallet("Brokerage", "Savings");
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                WalletItemFragment fragment = addPanel(activity);
                assertEquals(View.GONE, loadWallet(fragment, ungrouped).getVisibility());
                TextView group = loadWallet(fragment, grouped);
                assertEquals(View.VISIBLE, group.getVisibility());
                assertEquals("Savings", group.getText().toString());
            });
        }
    }

    @Test
    public void deletingAWalletWithAHeldRecurrentTransferClearsTheRateNotification() {
        long dollars = insertWallet("Dollars", "USD", null);
        long euros = insertWallet("Euros", "EUR", null);
        insertHeldRecurrentTransfer(dollars, euros);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                WalletItemFragment fragment = addPanel(activity);
                loadWallet(fragment, dollars);
                clearRecurrenceSchedule();
                postNeedsRateNotification();
                CountDownLatch release = parkDeleteWorker();
                boolean reached;
                try {
                    clickDeleteAndConfirm(fragment);
                } finally {
                    release.countDown();
                    reached = awaitDeleteWorker();
                }
                assertTrue("the delete worker did not reach the marker within 5 seconds", reached);
                idleMainLooper();
                assertEquals(0, countRecurrentTransfers());
                assertNull(needsRateNotification());
                assertEquals(0L, fragment.getItemId());
            });
        }
    }

    @Test
    public void aWalletDeletedAfterThePanelLostItsViewStillClearsTheRateNotification() {
        long dollars = insertWallet("Dollars", "USD", null);
        long euros = insertWallet("Euros", "EUR", null);
        insertHeldRecurrentTransfer(dollars, euros);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                WalletItemFragment fragment = addPanel(activity);
                loadWallet(fragment, dollars);
                clearRecurrenceSchedule();
                postNeedsRateNotification();
                CountDownLatch release = parkDeleteWorker();
                boolean reached;
                try {
                    clickDeleteAndConfirm(fragment);
                } finally {
                    release.countDown();
                    reached = awaitDeleteWorker();
                }
                assertTrue("the delete worker did not reach the marker within 5 seconds", reached);
                activity.getSupportFragmentManager()
                        .beginTransaction()
                        .remove(fragment)
                        .commitNow();
                assertNull(fragment.getView());
                idleMainLooper();
                assertEquals(dollars, fragment.getItemId());
                assertEquals(0, countRecurrentTransfers());
                assertNull(needsRateNotification());
            });
        }
    }

    @Test
    public void aSecondHeldTransferOnOtherWalletsKeepsTheRateNotificationAfterAWalletDelete() {
        long dollars = insertWallet("Dollars", "USD", null);
        long euros = insertWallet("Euros", "EUR", null);
        long pounds = insertWallet("Pounds", "GBP", null);
        long yen = insertWallet("Yen", "JPY", null);
        insertHeldRecurrentTransfer(dollars, euros);
        insertHeldRecurrentTransfer(pounds, yen);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                WalletItemFragment fragment = addPanel(activity);
                loadWallet(fragment, dollars);
                clearRecurrenceSchedule();
                postNeedsRateNotification();
                CountDownLatch release = parkDeleteWorker();
                boolean reached;
                try {
                    clickDeleteAndConfirm(fragment);
                } finally {
                    release.countDown();
                    reached = awaitDeleteWorker();
                }
                assertTrue("the delete worker did not reach the marker within 5 seconds", reached);
                idleMainLooper();
                assertEquals(1, countRecurrentTransfers());
                assertNotNull(needsRateNotification());
                assertEquals(1, recurrenceJobs().size());
            });
        }
    }

    @Test
    public void aWalletDeletedAfterThePanelIsRemovedBeforeTheWorkerRunsStillClearsTheRateNotification() {
        long dollars = insertWallet("Dollars", "USD", null);
        long euros = insertWallet("Euros", "EUR", null);
        insertHeldRecurrentTransfer(dollars, euros);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                WalletItemFragment fragment = addPanel(activity);
                loadWallet(fragment, dollars);
                clearRecurrenceSchedule();
                postNeedsRateNotification();
                CountDownLatch release = parkDeleteWorker();
                boolean reached;
                try {
                    clickDeleteAndConfirm(fragment);
                    activity.getSupportFragmentManager()
                            .beginTransaction()
                            .remove(fragment)
                            .commitNow();
                    assertNull(fragment.getContext());
                } finally {
                    release.countDown();
                    reached = awaitDeleteWorker();
                }
                assertTrue("the delete worker did not reach the marker within 5 seconds", reached);
                idleMainLooper();
                assertEquals(0, countRecurrentTransfers());
                assertNull(needsRateNotification());
            });
        }
    }

    @Test
    public void aRefusedWalletDeleteLeavesTheRateNotification() {
        long dollars = insertWallet("Dollars", "USD", null);
        long euros = insertWallet("Euros", "EUR", null);
        insertHeldRecurrentTransfer(dollars, euros);
        insertTransfer(dollars, euros);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                WalletItemFragment fragment = addPanel(activity);
                loadWallet(fragment, dollars);
                clearRecurrenceSchedule();
                postNeedsRateNotification();
                CountDownLatch release = parkDeleteWorker();
                AlertDialog confirm;
                boolean reached;
                try {
                    confirm = clickDeleteAndConfirm(fragment);
                } finally {
                    release.countDown();
                    reached = awaitDeleteWorker();
                }
                assertTrue("the delete worker did not reach the marker within 5 seconds", reached);
                idleMainLooper();
                assertEquals(1, countRecurrentTransfers());
                assertNotNull(needsRateNotification());
                assertTrue(recurrenceJobs().isEmpty());
                assertEquals(dollars, fragment.getItemId());
                AlertDialog reported = (AlertDialog) ShadowDialog.getLatestDialog();
                assertNotNull(reported);
                assertNotSame(confirm, reported);
                TextView message = reported.findViewById(android.R.id.message);
                assertNotNull(message);
                assertEquals(activity.getString(R.string.message_error_delete_wallet_of_transfer),
                        message.getText().toString());
            });
        }
    }

    private TextView showWallet(FragmentActivity activity, long walletId) {
        return loadWallet(addPanel(activity), walletId);
    }

    private WalletItemFragment addPanel(FragmentActivity activity) {
        WalletItemFragment fragment = new WalletItemFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, fragment, TAG_FRAGMENT)
                .commitNow();
        return fragment;
    }

    private TextView loadWallet(WalletItemFragment fragment, long walletId) {
        fragment.showItemId(walletId);
        // the query runs on a background thread, and the wheel goes away only once its result lands
        View wheel = fragment.getView().findViewById(R.id.secondary_panel_progress_wheel);
        for (int i = 0; i < 200 && wheel.getVisibility() != View.GONE; i++) {
            shadowOf(Looper.getMainLooper()).idle();
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
        assertEquals("the wallet never loaded", View.GONE, wheel.getVisibility());
        return fragment.getView().findViewById(R.id.group_text_view);
    }

    private AlertDialog clickDeleteAndConfirm(WalletItemFragment fragment) {
        Toolbar toolbar = fragment.requireView().findViewById(R.id.secondary_toolbar);
        assertNotNull(toolbar);
        MenuItem delete = toolbar.getMenu().findItem(R.id.action_delete_item);
        assertNotNull(delete);
        fragment.onMenuItemClick(delete);
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idleMainLooper();
        return dialog;
    }

    private CountDownLatch parkDeleteWorker() {
        CountDownLatch release = new CountDownLatch(1);
        submitToDeleteWorker(() -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        return release;
    }

    private boolean awaitDeleteWorker() {
        CountDownLatch reached = new CountDownLatch(1);
        submitToDeleteWorker(reached::countDown);
        try {
            return reached.await(MARKER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void submitToDeleteWorker(Runnable runnable) {
        try {
            Field field = SecondaryPanelFragment.class.getDeclaredField("sDeleteExecutor");
            field.setAccessible(true);
            Executor executor = (Executor) field.get(null);
            executor.execute(runnable);
        } catch (ReflectiveOperationException e) {
            fail("the delete executor could not be reached, " + e);
        }
    }

    private void idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void clearRecurrenceSchedule() {
        ReflectionHelpers.<Map<?, ?>>getStaticField(JobIntentService.class, "sClassWorkEnqueuer").clear();
        ((JobScheduler) mContext.getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancelAll();
        ShadowAlarmManager.reset();
    }

    private List<JobInfo> recurrenceJobs() {
        List<JobInfo> jobs = new ArrayList<>();
        for (JobInfo job : ((JobScheduler) mContext.getSystemService(Context.JOB_SCHEDULER_SERVICE)).getAllPendingJobs()) {
            if (RecurrenceHandlerIntentService.class.getName().equals(job.getService().getClassName())) {
                jobs.add(job);
            }
        }
        return jobs;
    }

    private void postNeedsRateNotification() {
        NotificationManagerCompat.from(mContext).notify(NotificationContract.NOTIFICATION_ID_RECURRENCE_NEEDS_RATE,
                new NotificationCompat.Builder(mContext, NotificationContract.NOTIFICATION_CHANNEL_RECURRENCE)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(mContext.getString(R.string.notification_title_recurrence_needs_rate))
                        .build());
        assertNotNull(needsRateNotification());
    }

    private Notification needsRateNotification() {
        NotificationManager manager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        return shadowOf(manager).getNotification(NotificationContract.NOTIFICATION_ID_RECURRENCE_NEEDS_RATE);
    }

    private int countRecurrentTransfers() {
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_RECURRENT_TRANSFERS, null, null, null, null);
        assertNotNull(cursor);
        try {
            return cursor.getCount();
        } finally {
            cursor.close();
        }
    }

    private void insertHeldRecurrentTransfer(long from, long to) {
        ContentValues values = new ContentValues();
        values.put(Contract.RecurrentTransfer.DESCRIPTION, "Savings");
        values.put(Contract.RecurrentTransfer.WALLET_FROM_ID, from);
        values.put(Contract.RecurrentTransfer.WALLET_TO_ID, to);
        values.put(Contract.RecurrentTransfer.MONEY_FROM, 1000L);
        values.put(Contract.RecurrentTransfer.MONEY_TO, 0L);
        values.put(Contract.RecurrentTransfer.MONEY_TAX, 0L);
        values.put(Contract.RecurrentTransfer.CONFIRMED, true);
        values.put(Contract.RecurrentTransfer.COUNT_IN_TOTAL, true);
        values.put(Contract.RecurrentTransfer.START_DATE, DateUtils.getSQLDateString(new Date(System.currentTimeMillis() + 30 * DAY)));
        values.put(Contract.RecurrentTransfer.RULE, "FREQ=DAILY");
        long id = ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_RECURRENT_TRANSFERS, values));
        String pointer = DateUtils.getSQLDateString(new Date(System.currentTimeMillis() - 3 * DAY));
        ContentValues pointers = new ContentValues();
        pointers.put(Contract.RecurrentTransfer.LAST_OCCURRENCE, pointer);
        pointers.put(Contract.RecurrentTransfer.NEXT_OCCURRENCE, pointer);
        assertEquals(1, mResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_RECURRENT_TRANSFERS, id), pointers, null, null));
    }

    private void insertTransfer(long fromWalletId, long toWalletId) {
        ContentValues values = new TransferContentValuesBuilder()
                .description("Move")
                .date(DateUtils.getSQLDateTimeString(new Date()))
                .fromWalletId(fromWalletId)
                .toWalletId(toWalletId)
                .taxWalletId(fromWalletId)
                .fromMoney(500L)
                .toMoney(460L)
                .taxMoney(0L)
                .note("")
                .confirmed(true)
                .countInTotal(true)
                .build();
        mResolver.insert(DataContentProvider.CONTENT_TRANSFERS, values);
    }

    private long insertWallet(String name, String group) {
        return insertWallet(name, "EUR", group);
    }

    private long insertWallet(String name, String currency, String group) {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, name);
        values.put(Contract.Wallet.ICON, ICON);
        values.put(Contract.Wallet.CURRENCY, currency);
        values.put(Contract.Wallet.START_MONEY, 0L);
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        values.put(Contract.Wallet.ARCHIVED, false);
        values.put(Contract.Wallet.GROUP, group);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_WALLETS, values));
    }
}
