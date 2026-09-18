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

import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;
import com.oriondev.moneywallet.ui.preference.ThemedListPreference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * The first day of month setting writes its day numbers in the digits of the app's language,
 * while the values it stores stay ASCII, since they are read back with Integer.parseInt. The
 * preference keeps its entries private, so they are read off the list its dialog shows.
 */
@RunWith(RobolectricTestRunner.class)
public class FirstDayOfMonthDigitsTest {

    private static final String TAG_FRAGMENT = "FirstDayOfMonthDigitsTest::Fragment";

    private static final int PERSIAN_ZERO = 0x06F0;
    private static final int PERSIAN_NINE = 0x06F9;

    private interface Check {

        void run(ThemedListPreference preference, AlertDialog dialog);
    }

    private static void onSetting(int storedDay, Check check) {
        PreferenceManager.setCurrentFirstDayOfMonth(storedDay);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                UserInterfaceSettingFragment fragment = new UserInterfaceSettingFragment();
                activity.getSupportFragmentManager()
                        .beginTransaction()
                        .add(android.R.id.content, fragment, TAG_FRAGMENT)
                        .commitNow();
                ThemedListPreference preference = fragment.findPreference("first_day_month");
                assertNotNull("the settings screen has no first day of month row", preference);
                preference.performClick();
                shadowOf(Looper.getMainLooper()).idle();
                AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
                assertNotNull("tapping the row opened no dialog", dialog);
                check.run(preference, dialog);
            });
        }
    }

    private static void choose(AlertDialog dialog, int position) {
        dialog.getListView().performItemClick(null, position, position);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        // AlertController hands the button click to a Handler, and the looper is paused here.
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void assertDigitsIn(String where, CharSequence text, char zero, char nine) {
        assertTrue(where + " is empty, so it carries no digits to check", text.length() > 0);
        for (int at = 0; at < text.length(); at++) {
            char character = text.charAt(at);
            assertTrue(where + " reads " + text + ", which carries the digit " + character
                    + " from outside the expected set", character >= zero && character <= nine);
        }
    }

    private static void assertEntries(AlertDialog dialog, char zero, char nine) {
        ListAdapter list = dialog.getListView().getAdapter();
        assertEquals("the list does not offer the 28 days", 28, list.getCount());
        for (int position = 0; position < list.getCount(); position++) {
            assertDigitsIn("the entry for day " + (position + 1),
                    String.valueOf(list.getItem(position)), zero, nine);
        }
    }

    @Test
    @Config(qualifiers = "fa-rIR")
    public void theDaysAreListedAndSummarizedInPersianDigits() {
        assertEquals("the qualifier did not reach the default locale, so nothing below is a check",
                "fa", Locale.getDefault().getLanguage());
        onSetting(12, (preference, dialog) -> {
            assertEntries(dialog, (char) PERSIAN_ZERO, (char) PERSIAN_NINE);
            assertEquals("۱۲", String.valueOf(preference.getSummary()));
            assertEquals("12", preference.getValue());
            choose(dialog, 14);
            assertEquals(15, PreferenceManager.getFirstDayOfMonth());
            assertEquals("15", preference.getValue());
            assertEquals("۱۵", String.valueOf(preference.getSummary()));
        });
    }

    @Test
    @Config(qualifiers = "en")
    public void theDaysStayAsciiInEnglish() {
        onSetting(12, (preference, dialog) -> {
            assertEntries(dialog, '0', '9');
            assertEquals("12", String.valueOf(preference.getSummary()));
            assertEquals("12", preference.getValue());
            choose(dialog, 14);
            assertEquals(15, PreferenceManager.getFirstDayOfMonth());
            assertEquals("15", String.valueOf(preference.getSummary()));
        });
    }
}
