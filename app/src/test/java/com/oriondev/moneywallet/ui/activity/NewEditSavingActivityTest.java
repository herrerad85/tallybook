package com.oriondev.moneywallet.ui.activity;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Looper;

import androidx.appcompat.widget.Toolbar;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.ui.view.text.MaterialEditText;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * Saves the savings goal editor against the real content provider over a fresh database. The
 * editor has no control for whether a goal is complete, so a save must leave that column as it is.
 */
@RunWith(RobolectricTestRunner.class)
public class NewEditSavingActivityTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private ContentResolver mResolver;
    private long mWallet;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        mResolver = context.getContentResolver();
        mWallet = insertWallet();
    }

    /** Editing an archived goal used to bring it back into the active list. */
    @Test
    public void editingACompleteGoalKeepsItComplete() {
        long saving = insertSaving(true);
        try (ActivityScenario<NewEditSavingActivity> scenario = ActivityScenario.launch(editIntent(saving))) {
            scenario.onActivity(activity -> {
                description(activity).setText("Trip");
                save(activity);
            });
        }
        assertEquals("Trip", storedColumn(saving, Contract.Saving.DESCRIPTION));
        assertEquals("1", storedColumn(saving, Contract.Saving.COMPLETE));
    }

    @Test
    public void editingAnActiveGoalKeepsItActive() {
        long saving = insertSaving(false);
        try (ActivityScenario<NewEditSavingActivity> scenario = ActivityScenario.launch(editIntent(saving))) {
            scenario.onActivity(activity -> {
                description(activity).setText("Trip");
                save(activity);
            });
        }
        assertEquals("Trip", storedColumn(saving, Contract.Saving.DESCRIPTION));
        assertEquals("0", storedColumn(saving, Contract.Saving.COMPLETE));
    }

    @Test
    public void aNewGoalIsStoredActive() {
        try (ActivityScenario<NewEditSavingActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> {
                description(activity).setText("Trip");
                save(activity);
            });
        }
        try (Cursor cursor = mResolver.query(DataContentProvider.CONTENT_SAVINGS,
                new String[] {Contract.Saving.DESCRIPTION, Contract.Saving.COMPLETE}, null, null, null)) {
            assertTrue("no saving was stored", cursor != null && cursor.moveToFirst());
            assertEquals(1, cursor.getCount());
            assertEquals("Trip", cursor.getString(cursor.getColumnIndexOrThrow(Contract.Saving.DESCRIPTION)));
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Saving.COMPLETE)));
        }
    }

    /** Saves through the toolbar menu, the way a tap on the check mark does. */
    private static void save(NewEditSavingActivity activity) {
        Toolbar toolbar = activity.findViewById(R.id.primary_toolbar);
        toolbar.getMenu().performIdentifierAction(R.id.action_save_changes, 0);
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static MaterialEditText description(NewEditSavingActivity activity) {
        return activity.findViewById(R.id.description_edit_text);
    }

    private String storedColumn(long saving, String column) {
        Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_SAVINGS, saving);
        try (Cursor cursor = mResolver.query(uri, new String[] {column}, null, null, null)) {
            assertTrue("the saving row is gone", cursor != null && cursor.moveToFirst());
            return cursor.getString(cursor.getColumnIndexOrThrow(column));
        }
    }

    private static Intent newIntent() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                NewEditSavingActivity.class);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM);
        return intent;
    }

    private static Intent editIntent(long saving) {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                NewEditSavingActivity.class);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.EDIT_ITEM);
        intent.putExtra(NewEditItemActivity.ID, saving);
        return intent;
    }

    private long insertWallet() {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, "Cash");
        values.put(Contract.Wallet.ICON, ICON);
        values.put(Contract.Wallet.CURRENCY, "EUR");
        values.put(Contract.Wallet.START_MONEY, 0L);
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        values.put(Contract.Wallet.ARCHIVED, false);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_WALLETS, values));
    }

    private long insertSaving(boolean complete) {
        ContentValues values = new ContentValues();
        values.put(Contract.Saving.DESCRIPTION, "Holiday");
        values.put(Contract.Saving.ICON, ICON);
        values.put(Contract.Saving.START_MONEY, 0L);
        values.put(Contract.Saving.END_MONEY, 2000L);
        values.put(Contract.Saving.WALLET_ID, mWallet);
        values.put(Contract.Saving.COMPLETE, complete);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_SAVINGS, values));
    }
}
