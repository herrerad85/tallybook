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

import android.os.Looper;
import android.widget.ListAdapter;

import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ActivityScenario;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;
import com.oriondev.moneywallet.ui.preference.ThemedListPreference;
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowDialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class ThemeTypeSettingTest {

    private static final String TAG_FRAGMENT = "ThemeTypeSettingTest::Fragment";

    private static final int POSITION_LIGHT = 0;
    private static final int POSITION_DARK = 1;
    private static final int POSITION_DEEP_DARK = 2;
    private static final int POSITION_SYSTEM = 3;

    private interface Check {

        void run(BackupListActivity activity, ThemedListPreference preference);
    }

    private boolean mOriginalFollowSystem;
    private ThemeEngine.Mode mOriginalMode;

    @Before
    public void saveTheMode() {
        mOriginalFollowSystem = ThemeEngine.isFollowingSystem();
        mOriginalMode = ThemeEngine.getTheme().getMode();
    }

    @After
    public void restoreTheMode() {
        if (mOriginalFollowSystem) {
            ThemeEngine.setFollowSystem();
        } else {
            ThemeEngine.setMode(mOriginalMode);
        }
    }

    private static void onSetting(Check check) {
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                UserInterfaceSettingFragment fragment = new UserInterfaceSettingFragment();
                activity.getSupportFragmentManager()
                        .beginTransaction()
                        .add(android.R.id.content, fragment, TAG_FRAGMENT)
                        .commitNow();
                ThemedListPreference preference = fragment.findPreference("theme_type");
                assertNotNull("the settings screen has no theme row", preference);
                check.run(activity, preference);
            });
        }
    }

    private static AlertDialog open(ThemedListPreference preference) {
        preference.performClick();
        shadowOf(Looper.getMainLooper()).idle();
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull("tapping the row opened no dialog", dialog);
        return dialog;
    }

    private static void choose(ThemedListPreference preference, int position) {
        AlertDialog dialog = open(preference);
        dialog.getListView().performItemClick(null, position, position);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void followSystemIsOfferedLast() {
        ThemeEngine.setMode(ThemeEngine.Mode.LIGHT);
        onSetting((activity, preference) -> {
            ListAdapter list = open(preference).getListView().getAdapter();
            assertEquals(4, list.getCount());
            assertEquals(activity.getString(R.string.setting_item_ui_theme_type_system),
                    String.valueOf(list.getItem(POSITION_SYSTEM)));
        });
    }

    @Test
    public void theRowShowsFollowSystemWhenItIsSaved() {
        ThemeEngine.setFollowSystem();
        onSetting((activity, preference) -> {
            assertEquals("system", preference.getValue());
            assertEquals(activity.getString(R.string.setting_item_ui_theme_type_system),
                    String.valueOf(preference.getSummary()));
        });
    }

    @Test
    public void choosingFollowSystemStoresItAndShowsIt() {
        ThemeEngine.setMode(ThemeEngine.Mode.LIGHT);
        onSetting((activity, preference) -> {
            choose(preference, POSITION_SYSTEM);
            assertTrue(ThemeEngine.isFollowingSystem());
            assertEquals("system", preference.getValue());
            assertEquals(activity.getString(R.string.setting_item_ui_theme_type_system),
                    String.valueOf(preference.getSummary()));
        });
    }

    @Test
    public void choosingLightWhileFollowingTheSystemStoresLight() {
        ThemeEngine.setFollowSystem();
        onSetting((activity, preference) -> {
            choose(preference, POSITION_LIGHT);
            assertFalse(ThemeEngine.isFollowingSystem());
            assertEquals(ThemeEngine.Mode.LIGHT, ThemeEngine.getTheme().getMode());
            assertEquals("light", preference.getValue());
        });
    }

    @Test
    public void choosingDarkStoresDark() {
        ThemeEngine.setMode(ThemeEngine.Mode.LIGHT);
        onSetting((activity, preference) -> {
            choose(preference, POSITION_DARK);
            assertFalse(ThemeEngine.isFollowingSystem());
            assertEquals(ThemeEngine.Mode.DARK, ThemeEngine.getTheme().getMode());
        });
    }

    @Test
    public void choosingDeepDarkStoresDeepDark() {
        ThemeEngine.setMode(ThemeEngine.Mode.LIGHT);
        onSetting((activity, preference) -> {
            choose(preference, POSITION_DEEP_DARK);
            assertFalse(ThemeEngine.isFollowingSystem());
            assertEquals(ThemeEngine.Mode.DEEP_DARK, ThemeEngine.getTheme().getMode());
        });
    }
}
