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

package com.oriondev.moneywallet.ui.fragment.dialog;

import android.os.Looper;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.RecurrenceSetting;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.Date;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

/**
 * The recurrence picker writes its every and times counts in the digits of the app's language,
 * and reads them back into the same numbers. A count it failed to parse would silently come back
 * as 1, so the counts used here are above 1.
 */
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "fa-rIR")
public class RecurrencePickerDigitsTest {

    private static final String TAG_HOST = "RecurrencePickerDigitsTest::Host";
    private static final String TAG_DIALOG = "RecurrencePickerDigitsTest::Dialog";

    private static final int EVERY = 2;
    private static final int TIMES = 3;

    public static class HostFragment extends Fragment {
    }

    private static RecurrenceSetting everyTwoDaysThreeTimes() {
        RecurrenceSetting.Builder builder = new RecurrenceSetting.Builder(new Date(),
                RecurrenceSetting.TYPE_DAILY);
        builder.setOffset(EVERY);
        builder.setEndFor(TIMES);
        return builder.build();
    }

    private static EditText every(RecurrencePickerDialog dialog) {
        return dialog.getDialog().findViewById(R.id.every_number_edit_text);
    }

    private static EditText times(RecurrencePickerDialog dialog) {
        return dialog.getDialog().findViewById(R.id.end_type_for_edit_text);
    }

    private static void assertCounts(RecurrencePickerDialog dialog) {
        assertEquals("fa", Locale.getDefault().getLanguage());
        assertEquals("the every count", "۲", every(dialog).getText().toString());
        assertEquals("the times count", "۳", times(dialog).getText().toString());
        RecurrenceSetting read = dialog.getCurrentRecurrenceSetting();
        assertEquals(RecurrenceSetting.END_FOR, read.getEndType());
        assertEquals("the every count read back", EVERY, read.getOffsetValue());
        assertEquals("the times count read back", TIMES, read.getOccurrenceValue());
    }

    private interface Case {

        void run(ActivityController<AppCompatActivity> controller, RecurrencePickerDialog dialog);
    }

    private static void run(Case body) {
        ActivityController<AppCompatActivity> controller =
                Robolectric.buildActivity(AppCompatActivity.class).setup();
        try {
            HostFragment host = new HostFragment();
            controller.get().getSupportFragmentManager().beginTransaction()
                    .add(host, TAG_HOST).commitNow();
            RecurrencePickerDialog dialog = RecurrencePickerDialog.newInstance();
            dialog.showPicker(host.getChildFragmentManager(), TAG_DIALOG,
                    everyTwoDaysThreeTimes(), true, false);
            host.getChildFragmentManager().executePendingTransactions();
            body.run(controller, dialog);
        } finally {
            controller.close();
        }
    }

    @Test
    public void theCountsAreWrittenInPersianDigitsAndReadBack() {
        run((controller, dialog) -> assertCounts(dialog));
    }

    @Test
    public void theCountsSurviveARecreate() {
        run((controller, dialog) -> {
            controller.recreate();
            shadowOf(Looper.getMainLooper()).idle();
            Fragment host = controller.get().getSupportFragmentManager()
                    .findFragmentByTag(TAG_HOST);
            assertNotNull(host);
            RecurrencePickerDialog restored = (RecurrencePickerDialog) host
                    .getChildFragmentManager().findFragmentByTag(TAG_DIALOG);
            assertNotNull(restored);
            assertCounts(restored);
        });
    }
}
