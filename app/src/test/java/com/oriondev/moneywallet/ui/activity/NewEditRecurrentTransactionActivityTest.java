package com.oriondev.moneywallet.ui.activity;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.utils.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

/**
 * Opens the recurrent transaction editor against the real content provider over a fresh database,
 * the same way the transfer editor test does.
 */
@RunWith(RobolectricTestRunner.class)
public class NewEditRecurrentTransactionActivityTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private static final long DAY = 24L * 60L * 60L * 1000L;

    private ContentResolver mResolver;
    private long mWallet;
    private long mCategory;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        mResolver = context.getContentResolver();
        mWallet = insertWallet("Cash");
        mCategory = insertCategory("Groceries");
    }

    @Test
    @Config(sdk = 33)
    public void aNewRecurrenceAsksForTheNotificationPermissionOnce() {
        try (ActivityScenario<NewEditRecurrentTransactionActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> {
                ShadowActivity.PermissionsRequest request = shadowOf(activity).getLastRequestedPermission();
                assertNotNull(request);
                assertEquals(1, request.requestedPermissions.length);
                assertEquals(Manifest.permission.POST_NOTIFICATIONS, request.requestedPermissions[0]);
            });
        }
    }

    @Test
    @Config(sdk = 33)
    public void anEditedRecurrenceDoesNotAskForTheNotificationPermission() {
        long recurrence = insertRecurrentTransaction();
        try (ActivityScenario<NewEditRecurrentTransactionActivity> scenario = ActivityScenario.launch(editIntent(recurrence))) {
            scenario.onActivity(activity -> assertNull(shadowOf(activity).getLastRequestedPermission()));
        }
    }

    @Test
    @Config(sdk = 33)
    public void aRecreatedNewRecurrenceDoesNotAskAgain() {
        try (ActivityScenario<NewEditRecurrentTransactionActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> assertNotNull(shadowOf(activity).getLastRequestedPermission()));
            scenario.recreate();
            scenario.onActivity(activity -> assertNull(shadowOf(activity).getLastRequestedPermission()));
        }
    }

    @Test
    @Config(sdk = 32)
    public void aNewRecurrenceDoesNotAskBeforeAndroid13() {
        try (ActivityScenario<NewEditRecurrentTransactionActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> assertNull(shadowOf(activity).getLastRequestedPermission()));
        }
    }

    @Test
    @Config(sdk = 33)
    public void aSecondNewRecurrenceDoesNotAskAgain() {
        try (ActivityScenario<NewEditRecurrentTransactionActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> assertNotNull(shadowOf(activity).getLastRequestedPermission()));
        }
        try (ActivityScenario<NewEditRecurrentTransactionActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> assertNull(shadowOf(activity).getLastRequestedPermission()));
        }
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

    // the start date is ahead, so the insert writes no occurrence into the transaction table
    private long insertRecurrentTransaction() {
        ContentValues values = new ContentValues();
        values.put(Contract.RecurrentTransaction.MONEY, 1000L);
        values.put(Contract.RecurrentTransaction.DESCRIPTION, "Rent");
        values.put(Contract.RecurrentTransaction.CATEGORY_ID, mCategory);
        values.put(Contract.RecurrentTransaction.DIRECTION, Contract.Direction.EXPENSE);
        values.put(Contract.RecurrentTransaction.WALLET_ID, mWallet);
        values.put(Contract.RecurrentTransaction.CONFIRMED, true);
        values.put(Contract.RecurrentTransaction.COUNT_IN_TOTAL, true);
        values.put(Contract.RecurrentTransaction.START_DATE,
                DateUtils.getSQLDateString(new Date(System.currentTimeMillis() + 30L * DAY)));
        values.put(Contract.RecurrentTransaction.RULE, "FREQ=MONTHLY");
        return ContentUris.parseId(
                mResolver.insert(DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS, values));
    }

    private static Intent newIntent() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                NewEditRecurrentTransactionActivity.class);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM);
        return intent;
    }

    private static Intent editIntent(long recurrenceId) {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                NewEditRecurrentTransactionActivity.class);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.EDIT_ITEM);
        intent.putExtra(NewEditItemActivity.ID, recurrenceId);
        return intent;
    }
}
