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

import android.app.Activity;
import android.os.Looper;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.test.core.app.ActivityScenario;

import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;
import com.oriondev.moneywallet.ui.activity.MainActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowDialog;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * The drawer entries setting lists every entry the user can hide, checked when the drawer
 * shows it. Robolectric draws only the first seven rows of the list, and a row's checked state
 * is set when it is drawn, so only those rows are read or clicked here.
 */
@RunWith(RobolectricTestRunner.class)
public class DrawerEntriesSettingTest {

    private static final String TAG_FRAGMENT = "DrawerEntriesSettingTest::Fragment";

    private static final int ROW_OVERVIEW = 1;
    private static final int ROW_BUDGETS = 3;
    private static final int DRAWN_ROWS = 7;

    private static final String OVERVIEW = String.valueOf(MainActivity.HIDEABLE_ENTRY_IDS[ROW_OVERVIEW]);
    private static final String BUDGETS = String.valueOf(MainActivity.HIDEABLE_ENTRY_IDS[ROW_BUDGETS]);
    private static final String SENTINEL = "sentinel";

    private interface Check {

        void run(AlertDialog dialog);
    }

    @Before
    public void setUp() {
        PreferenceManager.setHiddenDrawerEntries(Collections.<String>emptySet());
    }

    /**
     * Opens the dialog over a settings screen with Budgets hidden, runs the check and tells
     * whether the activity hosting the screen was recreated.
     */
    private static boolean onDialog(Check check) {
        PreferenceManager.setHiddenDrawerEntries(Collections.singleton(BUDGETS));
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            Activity[] first = new Activity[1];
            scenario.onActivity(activity -> {
                first[0] = activity;
                UserInterfaceSettingFragment fragment = new UserInterfaceSettingFragment();
                activity.getSupportFragmentManager()
                        .beginTransaction()
                        .add(android.R.id.content, fragment, TAG_FRAGMENT)
                        .commitNow();
                Preference preference = fragment.findPreference("drawer_entries");
                assertNotNull("the settings screen has no drawer entries row", preference);
                preference.performClick();
                shadowOf(Looper.getMainLooper()).idle();
                AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
                assertNotNull("tapping the row opened no dialog", dialog);
                check.run(dialog);
            });
            Activity[] now = new Activity[1];
            scenario.onActivity(activity -> now[0] = activity);
            return now[0] != first[0];
        }
    }

    private static void pressOk(AlertDialog dialog) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        // AlertController hands the button click to a Handler, and the looper is paused here.
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void aHiddenEntryIsListedUnchecked() {
        onDialog(dialog -> {
            ListView list = dialog.getListView();
            assertEquals(MainActivity.HIDEABLE_ENTRY_IDS.length, list.getAdapter().getCount());
            for (int row = 0; row < DRAWN_ROWS; row++) {
                assertEquals("row " + row, row != ROW_BUDGETS, list.isItemChecked(row));
            }
        });
    }

    @Test
    public void uncheckingAnEntryStoresItAndRecreates() {
        boolean recreated = onDialog(dialog -> {
            dialog.getListView().performItemClick(null, ROW_OVERVIEW, ROW_OVERVIEW);
            pressOk(dialog);
        });
        Set<String> expected = new HashSet<>();
        Collections.addAll(expected, OVERVIEW, BUDGETS);
        assertEquals(expected, PreferenceManager.getHiddenDrawerEntries());
        assertTrue("the activity was not recreated", recreated);
    }

    @Test
    public void okWithNoChangeStoresNothingAndDoesNotRecreate() {
        boolean recreated = onDialog(dialog -> {
            // A value the dialog never writes, so any write at all shows up below.
            PreferenceManager.setHiddenDrawerEntries(Collections.singleton(SENTINEL));
            pressOk(dialog);
        });
        assertEquals(Collections.singleton(SENTINEL), PreferenceManager.getHiddenDrawerEntries());
        assertFalse("the activity was recreated", recreated);
    }
}
