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

import android.content.Context;

import androidx.preference.Preference;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;
import com.oriondev.moneywallet.utils.DateFormatter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * The summary of the update exchange rates row reads Not updated yet until rates have been
 * downloaded once, and the time of the last update after that.
 */
@RunWith(RobolectricTestRunner.class)
public class ExchangeRateUpdateSummaryTest {

    private static final String TAG_FRAGMENT = "ExchangeRateUpdateSummaryTest::Fragment";

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        PreferenceManager.initialize(context);
        TestDatabases.useFreshDatabase(context);
    }

    private static String summary() {
        String[] summary = new String[1];
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                UtilitySettingFragment fragment = new UtilitySettingFragment();
                activity.getSupportFragmentManager()
                        .beginTransaction()
                        .add(android.R.id.content, fragment, TAG_FRAGMENT)
                        .commitNow();
                Preference preference = fragment.findPreference("exchange_rate_update");
                assertNotNull("the settings screen has no exchange rate update row", preference);
                summary[0] = String.valueOf(preference.getSummary());
            });
        }
        return summary[0];
    }

    @Test
    public void ratesNeverDownloadedShowNotUpdatedYet() {
        assertEquals("Not updated yet", summary());
    }

    @Test
    public void ratesDownloadedShowTheLastUpdate() {
        PreferenceManager.setLastExchangeRateUpdateTimestamp(1700000000000L);
        Context context = ApplicationProvider.getApplicationContext();
        String expected = context.getString(R.string.setting_summary_exchange_rate_update,
                DateFormatter.getDateFromToday(new Date(1700000000000L)));
        assertEquals(expected, summary());
    }
}
