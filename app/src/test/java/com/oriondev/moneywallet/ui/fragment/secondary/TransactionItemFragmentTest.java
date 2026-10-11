package com.oriondev.moneywallet.ui.fragment.secondary;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.SplitLedger;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.database.TransferContentValuesBuilder;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;
import com.oriondev.moneywallet.ui.activity.NewEditTransactionActivity;
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.DateUtils;
import com.oriondev.moneywallet.utils.MoneyFormatter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowDialog;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

/**
 * The delete behind the panel's trash menu item runs on a worker thread. Every case parks that
 * worker behind a latch before the click, so the delete the click submits waits in the worker
 * queue and can neither touch the database nor post anything back until the test counts the latch
 * down. That fixes the order instead of leaving it to timing.
 *
 * The cases pin these things. The row is still stored when the click returns, and the panel is
 * blank once the posted callback runs. A fragment taken off the screen before the callback lands
 * keeps the item it was showing. A panel pointed at a second item before the callback lands keeps
 * that second item and stays on the main layout. The toolbar menu ignores an edit tap while the
 * delete is going, and lets one through when nothing is going. Selecting the very same row again
 * while its delete is in flight leaves the toolbar menu dead, since that row is still doomed.
 * Selecting a second row and then the doomed row again leaves the menu dead too, so the panel
 * cannot queue a second delete of the row already going. The menu comes back on the second row
 * once the delete lands, and the panel keeps that second row. A delete the database refuses
 * reports its error even when the panel has since been pointed at another item.
 *
 * Every parked case releases the worker and waits for a marker inside a finally block, and asserts
 * on the result of that wait after the block, so a case that fails an assertion still reports the
 * assertion it failed.
 *
 * The fragment has no MultiPanelController parent here, so navigateBackSafely does nothing and no
 * panel is closed in any of these cases.
 */
@RunWith(RobolectricTestRunner.class)
public class TransactionItemFragmentTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";
    private static final String TAG_FRAGMENT = "TransactionItemFragmentTest::Fragment";

    private static final long MARKER_TIMEOUT_SECONDS = 5L;

    private ContentResolver mResolver;
    private long mWalletId;
    private long mTransactionId;
    private long mSecondTransactionId;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        mResolver = context.getContentResolver();
        mWalletId = insertWallet("Cash");
        long categoryId = insertCategory();
        mTransactionId = insertTransaction(mWalletId, categoryId, "Bread");
        mSecondTransactionId = insertTransaction(mWalletId, categoryId, "Milk");
    }

    @Test
    public void theRowGoesAwayOffTheMainThreadAndThePanelBlanksWhenTheCallbackRuns() {
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TransactionItemFragment fragment = showTransaction(activity, mTransactionId);
                View fragmentView = fragment.requireView();
                CountDownLatch release = parkDeleteWorker();
                boolean reached;
                try {
                    clickDeleteAndConfirm(fragment);
                    assertTrue("the row went away inside the click", rowExists(mTransactionId));
                } finally {
                    release.countDown();
                    reached = awaitDeleteWorker();
                }
                assertTrue("the delete worker did not reach the marker within 5 seconds", reached);
                idleMainLooper();
                assertFalse("the row was still stored after the callback", rowExists(mTransactionId));
                assertEquals(0L, fragment.getItemId());
                assertEquals(View.VISIBLE, fragmentView
                        .findViewById(R.id.empty_screen_secondary_panel_layout).getVisibility());
            });
        }
    }

    @Test
    public void aFragmentTakenOffTheScreenBeforeTheCallbackKeepsItsItem() {
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TransactionItemFragment fragment = showTransaction(activity, mTransactionId);
                CountDownLatch release = parkDeleteWorker();
                boolean reached;
                try {
                    clickDeleteAndConfirm(fragment);
                    activity.getSupportFragmentManager()
                            .beginTransaction()
                            .remove(fragment)
                            .commitNow();
                } finally {
                    release.countDown();
                    reached = awaitDeleteWorker();
                }
                assertTrue("the delete worker did not reach the marker within 5 seconds", reached);
                idleMainLooper();
                assertEquals(mTransactionId, fragment.getItemId());
            });
        }
    }

    @Test
    public void aPanelPointedAtAnotherItemBeforeTheCallbackKeepsThatItem() {
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TransactionItemFragment fragment = showTransaction(activity, mTransactionId);
                View fragmentView = fragment.requireView();
                CountDownLatch release = parkDeleteWorker();
                boolean reached;
                try {
                    clickDeleteAndConfirm(fragment);
                    fragment.showItemId(mSecondTransactionId);
                    idleMainLooper();
                } finally {
                    release.countDown();
                    reached = awaitDeleteWorker();
                }
                assertTrue("the delete worker did not reach the marker within 5 seconds", reached);
                idleMainLooper();
                assertEquals(mSecondTransactionId, fragment.getItemId());
                assertTrue("the second row went away", rowExists(mSecondTransactionId));
                assertFalse("the first row was still stored", rowExists(mTransactionId));
                assertEquals(View.VISIBLE, fragmentView
                        .findViewById(R.id.main_screen_secondary_panel_scroll_view).getVisibility());
            });
        }
    }

    @Test
    public void theToolbarMenuIgnoresAnEditWhileTheDeleteIsInFlight() {
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TransactionItemFragment fragment = showTransaction(activity, mTransactionId);
                Toolbar toolbar = fragment.requireView().findViewById(R.id.secondary_toolbar);
                assertNotNull(toolbar);
                CountDownLatch release = parkDeleteWorker();
                boolean reached;
                try {
                    clickDeleteAndConfirm(fragment);
                    toolbar.getMenu().performIdentifierAction(R.id.action_edit_item, 0);
                    idleMainLooper();
                    assertNull("the edit went through while the delete was in flight",
                            shadowOf(activity).getNextStartedActivity());
                } finally {
                    release.countDown();
                    reached = awaitDeleteWorker();
                }
                assertTrue("the delete worker did not reach the marker within 5 seconds", reached);
                idleMainLooper();
                assertFalse("the row was still stored after the callback", rowExists(mTransactionId));
            });
        }
    }

    @Test
    public void theSameRowSelectedAgainDuringTheDeleteKeepsTheToolbarDead() {
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TransactionItemFragment fragment = showTransaction(activity, mTransactionId);
                Toolbar toolbar = fragment.requireView().findViewById(R.id.secondary_toolbar);
                assertNotNull(toolbar);
                CountDownLatch release = parkDeleteWorker();
                boolean reached;
                try {
                    clickDeleteAndConfirm(fragment);
                    fragment.showItemId(mTransactionId);
                    idleMainLooper();
                    toolbar.getMenu().performIdentifierAction(R.id.action_edit_item, 0);
                    idleMainLooper();
                    assertNull("the edit went through on the row whose delete was in flight",
                            shadowOf(activity).getNextStartedActivity());
                } finally {
                    release.countDown();
                    reached = awaitDeleteWorker();
                }
                assertTrue("the delete worker did not reach the marker within 5 seconds", reached);
                idleMainLooper();
                assertFalse("the row was still stored after the callback", rowExists(mTransactionId));
                assertEquals(0L, fragment.getItemId());
            });
        }
    }

    @Test
    public void theDoomedRowSelectedAgainAfterAnotherOneKeepsTheToolbarDead() {
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TransactionItemFragment fragment = showTransaction(activity, mTransactionId);
                Toolbar toolbar = fragment.requireView().findViewById(R.id.secondary_toolbar);
                assertNotNull(toolbar);
                CountDownLatch release = parkDeleteWorker();
                boolean reached;
                try {
                    clickDeleteAndConfirm(fragment);
                    fragment.showItemId(mSecondTransactionId);
                    idleMainLooper();
                    fragment.showItemId(mTransactionId);
                    idleMainLooper();
                    toolbar.getMenu().performIdentifierAction(R.id.action_edit_item, 0);
                    idleMainLooper();
                    assertNull("the edit went through on the row whose delete was in flight",
                            shadowOf(activity).getNextStartedActivity());
                } finally {
                    release.countDown();
                    reached = awaitDeleteWorker();
                }
                assertTrue("the delete worker did not reach the marker within 5 seconds", reached);
                idleMainLooper();
                assertFalse("the row was still stored after the callback", rowExists(mTransactionId));
                assertEquals(0L, fragment.getItemId());
            });
        }
    }

    @Test
    public void theToolbarMenuComesBackOnTheOtherItemOnceTheDeleteLands() {
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TransactionItemFragment fragment = showTransaction(activity, mTransactionId);
                Toolbar toolbar = fragment.requireView().findViewById(R.id.secondary_toolbar);
                assertNotNull(toolbar);
                CountDownLatch release = parkDeleteWorker();
                boolean reached;
                try {
                    clickDeleteAndConfirm(fragment);
                    fragment.showItemId(mSecondTransactionId);
                    idleMainLooper();
                } finally {
                    release.countDown();
                    reached = awaitDeleteWorker();
                }
                assertTrue("the delete worker did not reach the marker within 5 seconds", reached);
                idleMainLooper();
                toolbar.getMenu().performIdentifierAction(R.id.action_edit_item, 0);
                idleMainLooper();
                Intent started = shadowOf(activity).getNextStartedActivity();
                assertNotNull("the edit was still ignored after the delete landed", started);
                assertNotNull(started.getComponent());
                assertEquals(NewEditTransactionActivity.class.getName(),
                        started.getComponent().getClassName());
                assertEquals(mSecondTransactionId, fragment.getItemId());
                assertTrue("the second row went away", rowExists(mSecondTransactionId));
            });
        }
    }

    @Test
    public void aRefusedDeleteIsReportedWhenThePanelHasMovedOn() {
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                long secondWalletId = insertWallet("Bank");
                long transferId = insertTransfer(mWalletId, secondWalletId);
                long legId = transferFromTransactionId(transferId);
                TransactionItemFragment fragment = showTransaction(activity, legId);
                CountDownLatch release = parkDeleteWorker();
                AlertDialog confirm;
                boolean reached;
                try {
                    confirm = clickDeleteAndConfirm(fragment);
                    fragment.showItemId(mSecondTransactionId);
                    idleMainLooper();
                } finally {
                    release.countDown();
                    reached = awaitDeleteWorker();
                }
                assertTrue("the delete worker did not reach the marker within 5 seconds", reached);
                idleMainLooper();
                assertTrue("the leg of the transfer was deleted", rowExists(legId));
                assertEquals(mSecondTransactionId, fragment.getItemId());
                AlertDialog reported = (AlertDialog) ShadowDialog.getLatestDialog();
                assertNotNull(reported);
                assertNotSame("no error dialog was put up", confirm, reported);
                assertTrue("the error dialog was not showing", reported.isShowing());
                TextView message = reported.findViewById(android.R.id.message);
                assertNotNull(message);
                assertEquals(activity.getString(R.string.message_error_delete_transaction_of_transfer),
                        message.getText().toString());
            });
        }
    }

    @Test
    public void theToolbarMenuOpensTheEditorWhenNoDeleteIsInFlight() {
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TransactionItemFragment fragment = showTransaction(activity, mTransactionId);
                Toolbar toolbar = fragment.requireView().findViewById(R.id.secondary_toolbar);
                assertNotNull(toolbar);
                toolbar.getMenu().performIdentifierAction(R.id.action_edit_item, 0);
                idleMainLooper();
                Intent started = shadowOf(activity).getNextStartedActivity();
                assertNotNull("the toolbar menu never reached the fragment", started);
                assertNotNull(started.getComponent());
                assertEquals(NewEditTransactionActivity.class.getName(),
                        started.getComponent().getClassName());
            });
        }
    }

    /**
     * RECEIPT is 10000 under Groceries with parts of 2000 in Household and 3000 in Fuel, and a
     * part flagged deleted that is not listed.
     */
    @Test
    public void aSplitRowListsItsPartsAndWhatIsLeftUnderTheMainCategory() {
        Context context = ApplicationProvider.getApplicationContext();
        SplitLedger.build(context);
        SplitLedger.database(context).execSQL("UPDATE transaction_splits SET split_note = 'soap' WHERE split_id = 930");
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TransactionItemFragment fragment = showTransaction(activity, SplitLedger.RECEIPT);
                assertEquals(activity.getString(R.string.split_part_with_note, "Household", euro(2000L), "soap") + "\n" +
                        activity.getString(R.string.split_part, "Fuel", euro(3000L)) + "\n" +
                        activity.getString(R.string.split_remainder, "Groceries", euro(10000L - 2000L - 3000L)),
                        awaitSplitText(fragment));
            });
        }
    }

    /** CROSSED holds a Salary part on an expense, which no total counts. */
    @Test
    public void invalidPartsAreListedWithALineSayingTheyAreNotCounted() {
        SplitLedger.build(ApplicationProvider.getApplicationContext());
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TransactionItemFragment fragment = showTransaction(activity, SplitLedger.CROSSED);
                assertEquals(activity.getString(R.string.split_part, "Salary", euro(500L)) + "\n" +
                        activity.getString(R.string.split_not_counted), awaitSplitText(fragment));
            });
        }
    }

    /**
     * Coming back to the foreground, the loader hands the fragment the same parts cursor it
     * already read to the end, the way it does when the user backs out of the editor.
     */
    @Test
    public void thePartsStayListedWhenTheScreenComesBackToTheForeground() {
        SplitLedger.build(ApplicationProvider.getApplicationContext());
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            String[] shown = new String[1];
            scenario.onActivity(activity -> shown[0] = awaitSplitText(showTransaction(activity, SplitLedger.RECEIPT)));
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> {
                idleMainLooper();
                TransactionItemFragment fragment = (TransactionItemFragment)
                        activity.getSupportFragmentManager().findFragmentByTag(TAG_FRAGMENT);
                assertNotNull(fragment);
                TextView split = fragment.requireView().findViewById(R.id.split_text_view);
                assertEquals(View.VISIBLE, split.getVisibility());
                assertEquals(shown[0], split.getText().toString());
            });
        }
    }

    /** The parts and the row come back through two loaders, so this waits for the line to fill. */
    private String awaitSplitText(TransactionItemFragment fragment) {
        TextView split = fragment.requireView().findViewById(R.id.split_text_view);
        for (int i = 0; i < 200 && split.getVisibility() != View.VISIBLE; i++) {
            idleMainLooper();
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
        assertEquals("the parts never showed", View.VISIBLE, split.getVisibility());
        return split.getText().toString();
    }

    private static String euro(long money) {
        return MoneyFormatter.getInstance().getNotTintedString(CurrencyManager.getCurrency("EUR"), money);
    }

    /**
     * Holds the one worker thread the panel deletes on, so work handed to it after this call sits
     * in its queue until the returned latch is counted down. The executor is a private static
     * field shared by every test in the run, so each caller has to release it in a finally block
     * or the next delete waits forever.
     */
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

    /**
     * Waits until a marker handed to the delete worker has run. The worker is one thread serving
     * a queue in order, so a marker submitted after a delete only runs once that delete has
     * returned, and the delete posts its callback to the main handler before it returns. So when
     * this call comes back the callback is already sitting in the main queue and one idle round
     * dispatches it. It sits in the same finally block as the release, so a case that fails an
     * assertion cannot leave its delete running into the next case.
     *
     * It reports the timeout as a returned false and never fails from inside that finally block,
     * since an AssertionError thrown there would replace the one the try block was already
     * carrying and hide the assertion the case actually failed. Each case stores the returned
     * value and asserts on it right after the block.
     */
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

    private TransactionItemFragment showTransaction(FragmentActivity activity, long transactionId) {
        TransactionItemFragment fragment = new TransactionItemFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, fragment, TAG_FRAGMENT)
                .commitNow();
        fragment.showItemId(transactionId);
        idleMainLooper();
        return fragment;
    }

    /**
     * AlertController hands a button press to the dialog's listener through a message on the main
     * looper, so the queue is drained once here to get the press across. The worker is parked
     * before this call, so the delete only lands in the worker queue and no result of it can
     * reach the main queue while this drain is running. The confirm dialog is returned so a
     * caller can tell it apart from a dialog the delete itself puts up later.
     */
    private AlertDialog clickDeleteAndConfirm(TransactionItemFragment fragment) {
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

    private boolean rowExists(long transactionId) {
        Cursor cursor = mResolver.query(
                ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, transactionId),
                new String[] {"*"}, null, null, null);
        if (cursor == null) {
            return false;
        }
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    /**
     * Reads the id of the transaction the transfer takes the money from. The transfer query
     * exposes it as Contract.Transfer.TRANSACTION_FROM_ID, so no guess about which of the
     * transfer type transactions belongs to this transfer is needed.
     */
    private long transferFromTransactionId(long transferId) {
        Cursor cursor = mResolver.query(
                ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSFERS, transferId),
                new String[] {Contract.Transfer.TRANSACTION_FROM_ID}, null, null, null);
        assertNotNull(cursor);
        assertTrue("the transfer row was not stored", cursor.moveToFirst());
        long transactionId = cursor.getLong(
                cursor.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_ID));
        cursor.close();
        return transactionId;
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

    private long insertCategory() {
        ContentValues values = new ContentValues();
        values.put(Contract.Category.NAME, "Groceries");
        values.put(Contract.Category.ICON, ICON);
        values.put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.getValue());
        values.put(Contract.Category.SHOW_REPORT, true);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, values));
    }

    private long insertTransaction(long walletId, long categoryId, String description) {
        ContentValues values = new ContentValues();
        values.put(Contract.Transaction.MONEY, 1250L);
        values.put(Contract.Transaction.DATE, DateUtils.getSQLDateTimeString(new Date()));
        values.put(Contract.Transaction.DESCRIPTION, description);
        values.put(Contract.Transaction.CATEGORY_ID, categoryId);
        values.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE);
        values.put(Contract.Transaction.TYPE, NewEditTransactionActivity.TYPE_STANDARD);
        values.put(Contract.Transaction.WALLET_ID, walletId);
        values.put(Contract.Transaction.CONFIRMED, true);
        values.put(Contract.Transaction.COUNT_IN_TOTAL, true);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values));
    }

    /**
     * Inserts a transfer the same way the editor does, through the builder that owns the column
     * set. The tax amount has to be written even when it is zero, since insertTransfer reads it
     * as a long to decide whether a tax transaction is needed.
     */
    private long insertTransfer(long fromWalletId, long toWalletId) {
        ContentValues values = new TransferContentValuesBuilder()
                .description("Move")
                .date(DateUtils.getSQLDateTimeString(new Date()))
                .fromWalletId(fromWalletId)
                .toWalletId(toWalletId)
                .taxWalletId(fromWalletId)
                .fromMoney(500L)
                .toMoney(500L)
                .taxMoney(0L)
                .note("")
                .confirmed(true)
                .countInTotal(true)
                .build();
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_TRANSFERS, values));
    }
}
