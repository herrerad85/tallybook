package com.oriondev.moneywallet.ui.fragment.primary;

import android.content.Context;
import android.os.Looper;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DeletedLedger;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.storage.wrapper.TransactionHeaderCursor;
import com.oriondev.moneywallet.storage.wrapper.TransferHeaderCursor;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView;
import com.oriondev.moneywallet.utils.DateUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Calendar;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class DateTheDatabaseCannotReadTest {

    private static final String GARBAGE = "garbage";
    private static final String SINGLE_DIGIT_MONTH_AND_DAY = "2026-1-5 10:00:00";

    private Context mContext;
    private DeletedLedger mLedger;
    private long mCurrentWallet;
    private long mCash;
    private long mBank;
    private long mGroceries;
    private Thread.UncaughtExceptionHandler mDefaultUncaughtExceptionHandler;
    private volatile Throwable mUncaughtException;

    @Before
    public void setUp() {
        mDefaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> mUncaughtException = exception);
        mContext = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(mContext);
        mLedger = new DeletedLedger(mContext);
        mCash = mLedger.insertWallet("Cash", "EUR");
        mBank = mLedger.insertWallet("Bank", "EUR");
        mGroceries = mLedger.insertCategory("Groceries", Contract.CategoryType.EXPENSE);
        mCurrentWallet = PreferenceManager.getCurrentWallet();
        PreferenceManager.setCurrentWallet(mContext, mCash);
    }

    @After
    public void tearDown() {
        PreferenceManager.setCurrentWallet(mContext, mCurrentWallet);
        Thread.setDefaultUncaughtExceptionHandler(mDefaultUncaughtExceptionHandler);
    }

    @Test
    public void theTransactionListShowsOnlyTheRowWhoseDateTheDatabaseCanRead() {
        long listed = mLedger.insertTransaction(mLedger.transaction(mCash, mGroceries, 1000L));
        long garbage = mLedger.insertTransaction(mLedger.transaction(mCash, mGroceries, 2000L));
        long singleDigit = mLedger.insertTransaction(mLedger.transaction(mCash, mGroceries, 3000L));
        mLedger.database().execSQL("UPDATE transactions SET transaction_date = ? WHERE transaction_id = ?",
                new Object[] {GARBAGE, garbage});
        mLedger.database().execSQL("UPDATE transactions SET transaction_date = ? WHERE transaction_id = ?",
                new Object[] {SINGLE_DIGIT_MONTH_AND_DAY, singleDigit});
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                RecyclerView list = loadedList(activity, new TransactionListFragment());
                assertEquals(2, list.getAdapter().getItemCount());
                assertEquals(TransactionHeaderCursor.TYPE_HEADER, list.getAdapter().getItemViewType(0));
                assertEquals(TransactionHeaderCursor.TYPE_ITEM, list.getAdapter().getItemViewType(1));
                assertEquals(listed, list.getAdapter().getItemId(1));
            });
        }
    }

    @Test
    public void theTransferListShowsOnlyTheRowWhoseDateTheDatabaseCanRead() {
        long listed = insertTransfer();
        long garbage = insertTransfer();
        long singleDigit = insertTransfer();
        mLedger.database().execSQL("UPDATE transfers SET transfer_date = ? WHERE transfer_id = ?",
                new Object[] {GARBAGE, garbage});
        mLedger.database().execSQL("UPDATE transfers SET transfer_date = ? WHERE transfer_id = ?",
                new Object[] {SINGLE_DIGIT_MONTH_AND_DAY, singleDigit});
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                RecyclerView list = loadedList(activity, new TransferListFragment());
                assertEquals(2, list.getAdapter().getItemCount());
                assertEquals(TransferHeaderCursor.TYPE_HEADER, list.getAdapter().getItemViewType(0));
                assertEquals(TransferHeaderCursor.TYPE_ITEM, list.getAdapter().getItemViewType(1));
                assertEquals(listed, list.getAdapter().getItemId(1));
            });
        }
    }

    @Test
    public void theTransactionListShowsARowDatedAheadOnlyWhenTheAppCanReadItsDate() {
        String aYearAhead = aYearAhead();
        long listed = mLedger.insertTransaction(mLedger.transaction(mCash, mGroceries, 1000L));
        long garbage = mLedger.insertTransaction(mLedger.transaction(mCash, mGroceries, 2000L));
        long singleDigit = mLedger.insertTransaction(mLedger.transaction(mCash, mGroceries, 3000L));
        long datedAhead = mLedger.insertTransaction(mLedger.transaction(mCash, mGroceries, 4000L));
        long dateOnly = mLedger.insertTransaction(mLedger.transaction(mCash, mGroceries, 5000L));
        long separatorT = mLedger.insertTransaction(mLedger.transaction(mCash, mGroceries, 6000L));
        long noSeconds = mLedger.insertTransaction(mLedger.transaction(mCash, mGroceries, 7000L));
        long milliseconds = mLedger.insertTransaction(mLedger.transaction(mCash, mGroceries, 8000L));
        long otherWallet = mLedger.insertTransaction(mLedger.transaction(mBank, mGroceries, 9000L));
        long februaryThirty = mLedger.insertTransaction(mLedger.transaction(mCash, mGroceries, 10000L));
        setTransactionDate(garbage, GARBAGE);
        setTransactionDate(singleDigit, SINGLE_DIGIT_MONTH_AND_DAY);
        setTransactionDate(datedAhead, aYearAhead);
        setTransactionDate(dateOnly, aYearAhead.substring(0, 10));
        setTransactionDate(separatorT, aYearAhead.replace(' ', 'T'));
        setTransactionDate(noSeconds, aYearAhead.substring(0, 16));
        mLedger.database().execSQL("UPDATE transactions SET transaction_date = transaction_date || '.123' WHERE transaction_id = ?",
                new Object[] {milliseconds});
        setTransactionDate(otherWallet, aYearAhead);
        setTransactionDate(februaryThirty, aYearAhead.substring(0, 4) + "-02-30 10:00:00");
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                RecyclerView list = loadedList(activity, new TransactionListFragment());
                assertNoUncaughtException();
                assertEquals(5, list.getAdapter().getItemCount());
                assertEquals(TransactionHeaderCursor.TYPE_HEADER, list.getAdapter().getItemViewType(0));
                assertEquals(TransactionHeaderCursor.TYPE_ITEM, list.getAdapter().getItemViewType(1));
                assertEquals(datedAhead, list.getAdapter().getItemId(1));
                assertEquals(TransactionHeaderCursor.TYPE_HEADER, list.getAdapter().getItemViewType(2));
                assertEquals(TransactionHeaderCursor.TYPE_ITEM, list.getAdapter().getItemViewType(3));
                assertEquals(milliseconds, list.getAdapter().getItemId(3));
                assertEquals(TransactionHeaderCursor.TYPE_ITEM, list.getAdapter().getItemViewType(4));
                assertEquals(listed, list.getAdapter().getItemId(4));
            });
        }
    }

    @Test
    public void theTransferListShowsARowDatedAheadOnlyWhenTheAppCanReadItsDate() {
        String aYearAhead = aYearAhead();
        long savings = mLedger.insertWallet("Savings", "EUR");
        long listed = insertTransfer();
        long garbage = insertTransfer();
        long singleDigit = insertTransfer();
        long datedAhead = insertTransfer();
        long dateOnly = insertTransfer();
        long separatorT = insertTransfer();
        long noSeconds = insertTransfer();
        long milliseconds = insertTransfer();
        long otherWallets = mLedger.insertTransfer(mBank, savings, null, Collections.emptyList(), Collections.emptyList(), null);
        long februaryThirty = insertTransfer();
        setTransferDate(garbage, GARBAGE);
        setTransferDate(singleDigit, SINGLE_DIGIT_MONTH_AND_DAY);
        setTransferDate(datedAhead, aYearAhead);
        setTransferDate(dateOnly, aYearAhead.substring(0, 10));
        setTransferDate(separatorT, aYearAhead.replace(' ', 'T'));
        setTransferDate(noSeconds, aYearAhead.substring(0, 16));
        mLedger.database().execSQL("UPDATE transfers SET transfer_date = transfer_date || '.123' WHERE transfer_id = ?",
                new Object[] {milliseconds});
        setTransferDate(otherWallets, aYearAhead);
        setTransferDate(februaryThirty, aYearAhead.substring(0, 4) + "-02-30 10:00:00");
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                RecyclerView list = loadedList(activity, new TransferListFragment());
                assertNoUncaughtException();
                assertEquals(5, list.getAdapter().getItemCount());
                assertEquals(TransferHeaderCursor.TYPE_HEADER, list.getAdapter().getItemViewType(0));
                assertEquals(TransferHeaderCursor.TYPE_ITEM, list.getAdapter().getItemViewType(1));
                assertEquals(datedAhead, list.getAdapter().getItemId(1));
                assertEquals(TransferHeaderCursor.TYPE_HEADER, list.getAdapter().getItemViewType(2));
                assertEquals(TransferHeaderCursor.TYPE_ITEM, list.getAdapter().getItemViewType(3));
                assertEquals(milliseconds, list.getAdapter().getItemId(3));
                assertEquals(TransferHeaderCursor.TYPE_ITEM, list.getAdapter().getItemViewType(4));
                assertEquals(listed, list.getAdapter().getItemId(4));
            });
        }
    }

    private void assertNoUncaughtException() {
        if (mUncaughtException != null) {
            throw new AssertionError(String.valueOf(mUncaughtException.getCause()), mUncaughtException);
        }
    }

    private static String aYearAhead() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, 1);
        return DateUtils.getSQLDateTimeString(calendar.getTime());
    }

    private void setTransactionDate(long transaction, String date) {
        mLedger.database().execSQL("UPDATE transactions SET transaction_date = ? WHERE transaction_id = ?",
                new Object[] {date, transaction});
    }

    private void setTransferDate(long transfer, String date) {
        mLedger.database().execSQL("UPDATE transfers SET transfer_date = ? WHERE transfer_id = ?",
                new Object[] {date, transfer});
    }

    private long insertTransfer() {
        return mLedger.insertTransfer(mCash, mBank, null, Collections.emptyList(), Collections.emptyList(), null);
    }

    private static RecyclerView loadedList(BackupListActivity activity, Fragment fragment) {
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();
        RecyclerView list = ((AdvancedRecyclerView) fragment.getView()).getRecyclerView();
        for (int i = 0; i < 200 && list.getAdapter().getItemCount() == 0; i++) {
            idle();
        }
        View view = fragment.getView();
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1080, 1920);
        idle();
        return list;
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
        try {
            Thread.sleep(25);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }
}
