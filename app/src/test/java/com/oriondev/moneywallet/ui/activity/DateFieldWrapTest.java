package com.oriondev.moneywallet.ui.activity;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.text.Layout;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.picker.DateTimePicker;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.fragment.secondary.EventItemFragment;
import com.oriondev.moneywallet.ui.view.text.MaterialEditText;
import com.oriondev.moneywallet.utils.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Calendar;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-420dpi", fontScale = 2.0f)
public class DateFieldWrapTest {

    private static final String LONG_DATE = "Wednesday 30 September 2026";
    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
    }

    @Test
    public void theTransactionEditorShowsTheWholeDateAtTheLargestFont() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                NewEditTransactionActivity.class);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM);
        assertWholeDateShown(intent, "NewEditTransactionActivity::Tag::DateTimePicker");
    }

    @Test
    public void theTransferEditorShowsTheWholeDateAtTheLargestFont() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                NewEditTransferActivity.class);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM);
        assertWholeDateShown(intent, "NewEditTransferModelActivity::Tag::DateTimePicker");
    }

    @Test
    public void theDebtEditorShowsBothWholeLongDatesAtTheLargestFont() {
        Intent intent = newItemIntent(NewEditDebtActivity.class);
        intent.putExtra(NewEditDebtActivity.TYPE, Contract.DebtType.DEBT);
        assertWholeLongDatesShown(intent,
                new String[] {"NewEditDebtActivity::Tag::DatePicker",
                        "NewEditDebtActivity::Tag::ExpDatePicker"},
                new int[] {R.id.date_edit_text, R.id.expiration_date_edit_text});
    }

    @Test
    public void theBudgetEditorShowsBothWholeLongDatesAtTheLargestFont() {
        assertWholeLongDatesShown(newItemIntent(NewEditBudgetActivity.class),
                new String[] {"NewEditBudgetActivity::Tag::StartDatePicker",
                        "NewEditBudgetActivity::Tag::EndDatePicker"},
                new int[] {R.id.start_date_edit_text, R.id.end_date_edit_text});
    }

    @Test
    public void theEventEditorShowsBothWholeLongDatesAtTheLargestFont() {
        assertWholeLongDatesShown(newItemIntent(NewEditEventActivity.class),
                new String[] {"NewEditEventActivity::Tag::StartDatePicker",
                        "NewEditEventActivity::Tag::EndDatePicker"},
                new int[] {R.id.start_date_edit_text, R.id.end_date_edit_text});
    }

    @Test
    public void theSavingEditorShowsTheWholeLongDateAtTheLargestFont() {
        assertWholeLongDatesShown(newItemIntent(NewEditSavingActivity.class),
                new String[] {"NewEditSavingActivity::Tag::ExpDatePicker"},
                new int[] {R.id.expiration_date_edit_text});
    }

    @Test
    public void theExportScreenShowsBothWholeLongDatesAtTheLargestFont() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                ImportExportActivity.class);
        intent.putExtra(ImportExportActivity.MODE, ImportExportActivity.MODE_EXPORT);
        assertWholeLongDatesShown(intent,
                new String[] {"ImportExportActivity::Tag::StartDateTimePicker",
                        "ImportExportActivity::Tag::EndDateTimePicker"},
                new int[] {R.id.start_date_edit_text, R.id.end_date_edit_text});
    }

    @Test
    public void theEventDetailsShowBothWholeLongDatesAtTheLargestFont() {
        PreferenceManager.setCurrentDateFormatIndex(PreferenceManager.DATE_FORMAT_TYPE_0);
        Context context = ApplicationProvider.getApplicationContext();
        ContentValues values = new ContentValues();
        values.put(Contract.Event.NAME, "Trip");
        values.put(Contract.Event.ICON, ICON);
        values.put(Contract.Event.START_DATE, DateUtils.getSQLDateString(longDate()));
        values.put(Contract.Event.END_DATE, DateUtils.getSQLDateString(longDate()));
        long event = ContentUris.parseId(context.getContentResolver()
                .insert(DataContentProvider.CONTENT_EVENTS, values));
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                EventItemFragment fragment = new EventItemFragment();
                activity.getSupportFragmentManager()
                        .beginTransaction()
                        .add(android.R.id.content, fragment)
                        .commitNow();
                fragment.showItemId(event);
                View wheel = fragment.requireView().findViewById(R.id.secondary_panel_progress_wheel);
                for (int i = 0; i < 200 && wheel.getVisibility() != View.GONE; i++) {
                    shadowOf(Looper.getMainLooper()).idle();
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException e) {
                        throw new AssertionError(e);
                    }
                }
                assertEquals("the event never loaded", View.GONE, wheel.getVisibility());
                shadowOf(Looper.getMainLooper()).idle();
                assertWholeTextFits(fragment.requireView().findViewById(R.id.start_date_text_view), LONG_DATE);
                assertWholeTextFits(fragment.requireView().findViewById(R.id.end_date_text_view), LONG_DATE);
            });
        }
    }

    private static Intent newItemIntent(Class<?> activity) {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), activity);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM);
        return intent;
    }

    private static Date longDate() {
        Calendar date = Calendar.getInstance();
        date.set(2026, Calendar.SEPTEMBER, 30, 12, 0, 0);
        return date.getTime();
    }

    private static void assertWholeLongDatesShown(Intent intent, String[] pickerTags, int[] fieldIds) {
        PreferenceManager.setCurrentDateFormatIndex(PreferenceManager.DATE_FORMAT_TYPE_0);
        try (ActivityScenario<AppCompatActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                for (String pickerTag : pickerTags) {
                    DateTimePicker picker = (DateTimePicker) activity.getSupportFragmentManager()
                            .findFragmentByTag(pickerTag);
                    picker.setCurrentDateTime(longDate());
                }
                shadowOf(Looper.getMainLooper()).idle();
                for (int fieldId : fieldIds) {
                    assertWholeTextFits(activity.findViewById(fieldId), LONG_DATE);
                }
            });
        }
    }

    private static void assertWholeDateShown(Intent intent, String pickerTag) {
        Calendar date = Calendar.getInstance();
        date.set(2026, Calendar.OCTOBER, 19, 12, 0, 0);
        try (ActivityScenario<AppCompatActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                DateTimePicker picker = (DateTimePicker) activity.getSupportFragmentManager()
                        .findFragmentByTag(pickerTag);
                picker.setCurrentDateTime(date.getTime());
                shadowOf(Looper.getMainLooper()).idle();
                MaterialEditText field = activity.findViewById(R.id.date_edit_text);
                assertWholeTextFits(field, "Mon 19 Oct 2026");
            });
        }
    }

    private static void assertWholeTextFits(TextView field, String expected) {
        assertEquals(expected, field.getText().toString());
        int room = field.getWidth() - field.getCompoundPaddingLeft()
                - field.getCompoundPaddingRight();
        Layout layout = field.getLayout();
        for (int line = 0; line < layout.getLineCount(); line++) {
            assertTrue("\"" + field.getText() + "\" line " + line + " is "
                            + layout.getLineMax(line) + "px in a field with " + room
                            + "px of room, so the rest of the date is cut off",
                    layout.getLineMax(line) <= room);
        }
        int height = field.getHeight() - field.getPaddingTop() - field.getPaddingBottom();
        assertTrue("\"" + field.getText() + "\" needs " + layout.getHeight() + "px and "
                        + "the field has " + height + "px, so the last line is cut off",
                layout.getHeight() <= height);
    }
}
