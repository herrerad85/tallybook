package com.oriondev.moneywallet.ui.fragment.multipanel;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.appcompat.app.AppCompatActivity;
import androidx.loader.content.CursorLoader;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class PersonListOrderTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"P\"}";

    @Test
    public void thePeopleAreListedInNameOrderWhateverTheCaseOrAccentOfTheirFirstLetter() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        ContentResolver resolver = context.getContentResolver();
        for (String name : new String[] {"Zed", "apple", "bob", "\u00c9mile"}) {
            ContentValues values = new ContentValues();
            values.put(Contract.Person.NAME, name);
            values.put(Contract.Person.ICON, ICON);
            resolver.insert(DataContentProvider.CONTENT_PEOPLE, values);
        }
        ActivityController<AppCompatActivity> controller = Robolectric.buildActivity(AppCompatActivity.class).setup();
        PersonMultiPanelFragment fragment = new PersonMultiPanelFragment();
        controller.get().getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();
        List<String> listed = new ArrayList<>();
        try (Cursor cursor = ((CursorLoader) fragment.onCreateLoader(0, null)).loadInBackground()) {
            while (cursor.moveToNext()) {
                listed.add(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Person.NAME)));
            }
        }
        controller.pause().stop().destroy();
        assertEquals(Arrays.asList("apple", "bob", "\u00c9mile", "Zed"), listed);
    }
}
