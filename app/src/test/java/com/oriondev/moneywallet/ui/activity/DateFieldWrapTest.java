package com.oriondev.moneywallet.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.text.Layout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.picker.DateTimePicker;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.ui.view.text.MaterialEditText;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-420dpi", fontScale = 2.0f)
public class DateFieldWrapTest {

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
                assertEquals("Mon 19 Oct 2026", field.getText().toString());
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
            });
        }
    }
}
