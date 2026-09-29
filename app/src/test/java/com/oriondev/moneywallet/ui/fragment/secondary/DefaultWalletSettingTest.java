/*
 * Copyright (c) 2026.
 *
 * This file is part of MoneyWallet.
 *
 * MoneyWallet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MoneyWallet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MoneyWallet.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.ui.fragment.secondary;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.os.Looper;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowDialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

/**
 * The summary of the default wallet row names the stored wallet, marks it when it is archived,
 * and reads Current wallet when the stored id names no wallet. The dialog behind the row lists an
 * archived default checked, so pressing OK without a choice keeps it.
 */
@RunWith(RobolectricTestRunner.class)
public class DefaultWalletSettingTest {

    private static final String TAG_FRAGMENT = "DefaultWalletSettingTest::Fragment";
    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private ContentResolver mResolver;

    private interface Check {

        void run(Preference row, AlertDialog dialog);
    }

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        PreferenceManager.initialize(context);
        TestDatabases.useFreshDatabase(context);
        mResolver = context.getContentResolver();
    }

    private static String summary() {
        String[] summary = new String[1];
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                UserInterfaceSettingFragment fragment = new UserInterfaceSettingFragment();
                activity.getSupportFragmentManager()
                        .beginTransaction()
                        .add(android.R.id.content, fragment, TAG_FRAGMENT)
                        .commitNow();
                Preference preference = fragment.findPreference("default_wallet");
                assertNotNull("the settings screen has no default wallet row", preference);
                summary[0] = String.valueOf(preference.getSummary());
            });
        }
        return summary[0];
    }

    /**
     * Opens the dialog by tapping the default wallet row and runs the check on both.
     */
    private static void onDialog(Check check) {
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                UserInterfaceSettingFragment fragment = new UserInterfaceSettingFragment();
                activity.getSupportFragmentManager()
                        .beginTransaction()
                        .add(android.R.id.content, fragment, TAG_FRAGMENT)
                        .commitNow();
                Preference preference = fragment.findPreference("default_wallet");
                assertNotNull("the settings screen has no default wallet row", preference);
                preference.performClick();
                shadowOf(Looper.getMainLooper()).idle();
                AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
                assertNotNull("tapping the row opened no dialog", dialog);
                check.run(preference, dialog);
            });
        }
    }

    private static void pressOk(AlertDialog dialog) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        // AlertController hands the button click to a Handler, and the looper is paused here.
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void aUsableDefaultShowsItsName() {
        PreferenceManager.setDefaultWallet(insertWallet("Cash", false));
        assertEquals("Cash", summary());
    }

    @Test
    public void anArchivedDefaultShowsItsNameMarkedArchived() {
        PreferenceManager.setDefaultWallet(insertWallet("Cash", true));
        assertEquals("Cash (archived)", summary());
    }

    @Test
    public void aDeletedDefaultShowsCurrentWallet() {
        long walletId = insertWallet("Cash", false);
        PreferenceManager.setDefaultWallet(walletId);
        mResolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, walletId),
                null, null);
        assertEquals("Current wallet", summary());
    }

    @Test
    public void aDefaultThatDoesNotExistShowsCurrentWallet() {
        insertWallet("Cash", false);
        PreferenceManager.setDefaultWallet(9999L);
        assertEquals("Current wallet", summary());
    }

    @Test
    public void noDefaultShowsCurrentWallet() {
        insertWallet("Cash", false);
        PreferenceManager.setDefaultWallet(PreferenceManager.NO_DEFAULT_WALLET);
        assertEquals("Current wallet", summary());
    }

    @Test
    public void anArchivedDefaultIsListedCheckedUnderItsArchivedName() {
        insertWallet("Bank", false);
        PreferenceManager.setDefaultWallet(insertWallet("Cash", true));
        onDialog((row, dialog) -> {
            ListView list = dialog.getListView();
            int checked = list.getCheckedItemPosition();
            assertEquals(3, list.getAdapter().getCount());
            assertEquals(2, checked);
            assertEquals("Cash (archived)", String.valueOf(list.getAdapter().getItem(checked)));
        });
    }

    @Test
    public void okWithNoChoiceKeepsAnArchivedDefault() {
        insertWallet("Bank", false);
        long archived = insertWallet("Cash", true);
        PreferenceManager.setDefaultWallet(archived);
        onDialog((row, dialog) -> {
            pressOk(dialog);
            assertEquals(archived, PreferenceManager.getDefaultWallet());
            assertEquals("Cash (archived)", String.valueOf(row.getSummary()));
        });
    }

    @Test
    public void choosingCurrentWalletClearsAnArchivedDefault() {
        insertWallet("Bank", false);
        PreferenceManager.setDefaultWallet(insertWallet("Cash", true));
        onDialog((row, dialog) -> {
            dialog.getListView().performItemClick(null, 0, 0);
            pressOk(dialog);
            assertEquals(PreferenceManager.NO_DEFAULT_WALLET, PreferenceManager.getDefaultWallet());
            assertEquals("Current wallet", String.valueOf(row.getSummary()));
        });
    }

    @Test
    public void aUsableDefaultIsListedOnceCheckedAndKeptByOk() {
        insertWallet("Bank", false);
        long usable = insertWallet("Cash", false);
        PreferenceManager.setDefaultWallet(usable);
        onDialog((row, dialog) -> {
            ListView list = dialog.getListView();
            assertEquals(3, list.getAdapter().getCount());
            int listed = 0;
            for (int i = 0; i < list.getAdapter().getCount(); i++) {
                if ("Cash".equals(String.valueOf(list.getAdapter().getItem(i)))) {
                    listed++;
                }
            }
            assertEquals(1, listed);
            int checked = list.getCheckedItemPosition();
            assertEquals("Cash", String.valueOf(list.getAdapter().getItem(checked)));
            pressOk(dialog);
            assertEquals(usable, PreferenceManager.getDefaultWallet());
        });
    }

    @Test
    public void choosingAWalletAndPressingOkSavesItAsTheDefault() {
        insertWallet("Bank", false);
        long cash = insertWallet("Cash", false);
        onDialog((row, dialog) -> {
            ListView list = dialog.getListView();
            assertEquals("Cash", String.valueOf(list.getAdapter().getItem(2)));
            list.performItemClick(null, 2, 2);
            pressOk(dialog);
            assertEquals(cash, PreferenceManager.getDefaultWallet());
            assertEquals("Cash", String.valueOf(row.getSummary()));
        });
    }

    @Test
    public void aDefaultThatDoesNotExistChecksCurrentWalletAndOkClearsIt() {
        insertWallet("Bank", false);
        insertWallet("Cash", false);
        PreferenceManager.setDefaultWallet(9999L);
        onDialog((row, dialog) -> {
            ListView list = dialog.getListView();
            assertEquals(3, list.getAdapter().getCount());
            for (int i = 0; i < list.getAdapter().getCount(); i++) {
                assertNotNull(list.getAdapter().getItem(i));
            }
            assertEquals(0, list.getCheckedItemPosition());
            assertEquals("Current wallet", String.valueOf(list.getAdapter().getItem(0)));
            pressOk(dialog);
            assertEquals(PreferenceManager.NO_DEFAULT_WALLET, PreferenceManager.getDefaultWallet());
        });
    }

    private long insertWallet(String name, boolean archived) {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, name);
        values.put(Contract.Wallet.ICON, ICON);
        values.put(Contract.Wallet.CURRENCY, "EUR");
        values.put(Contract.Wallet.START_MONEY, 0L);
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        values.put(Contract.Wallet.ARCHIVED, archived);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_WALLETS, values));
    }
}
