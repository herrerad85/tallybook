package com.oriondev.moneywallet.ui.fragment.primary;

import android.content.ContentResolver;
import android.content.ContentUris;
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
public class CategoryListOrderTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    @Test
    public void parentsAndTheChildrenUnderThemAreListedInNameOrderWhateverTheCaseOrAccentOfTheirFirstLetter() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        ContentResolver resolver = context.getContentResolver();
        insertCategory(resolver, "Zed", null);
        long apple = insertCategory(resolver, "apple", null);
        insertCategory(resolver, "bob", null);
        insertCategory(resolver, "\u00c9mile", null);
        for (String name : new String[] {"Zinc", "acorn", "berry", "\u00c9clair"}) {
            insertCategory(resolver, name, apple);
        }
        ActivityController<AppCompatActivity> controller = Robolectric.buildActivity(AppCompatActivity.class).setup();
        CategoryListFragment fragment = CategoryListFragment.newInstance(Contract.CategoryType.EXPENSE, true);
        controller.get().getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();
        List<String> listed = new ArrayList<>();
        try (Cursor cursor = ((CursorLoader) fragment.onCreateLoader(0, null)).loadInBackground()) {
            while (cursor.moveToNext()) {
                listed.add(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.NAME)));
            }
        }
        controller.pause().stop().destroy();
        assertEquals(Arrays.asList("apple", "acorn", "berry", "\u00c9clair", "Zinc", "bob", "\u00c9mile", "Zed"), listed);
    }

    private static long insertCategory(ContentResolver resolver, String name, Long parent) {
        ContentValues values = new ContentValues();
        values.put(Contract.Category.NAME, name);
        values.put(Contract.Category.ICON, ICON);
        values.put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.getValue());
        values.put(Contract.Category.SHOW_REPORT, true);
        values.put(Contract.Category.PARENT, parent);
        return ContentUris.parseId(resolver.insert(DataContentProvider.CONTENT_CATEGORIES, values));
    }
}
