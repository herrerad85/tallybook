package com.oriondev.moneywallet.ui.activity;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import androidx.loader.content.CursorLoader;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;

import org.junit.After;
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
public class CategorySortOrderTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private ActivityController<CategorySortActivity> mController;

    @After
    public void tearDown() {
        if (mController != null && !mController.get().isDestroyed()) {
            mController.destroy();
        }
    }

    @Test
    public void categoriesAtTheSameIndexAreOfferedInNameOrderWhateverTheCaseOrAccentOfTheirFirstLetter() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        ContentResolver resolver = context.getContentResolver();
        for (String name : new String[] {"Zed", "apple", "bob", "\u00c9mile"}) {
            ContentValues values = new ContentValues();
            values.put(Contract.Category.NAME, name);
            values.put(Contract.Category.ICON, ICON);
            values.put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.getValue());
            values.put(Contract.Category.SHOW_REPORT, true);
            resolver.insert(DataContentProvider.CONTENT_CATEGORIES, values);
        }
        Intent intent = new Intent(context, CategorySortActivity.class);
        intent.putExtra(CategorySortActivity.TYPE, Contract.CategoryType.EXPENSE);
        mController = Robolectric.buildActivity(CategorySortActivity.class, intent).setup();
        List<String> listed = new ArrayList<>();
        try (Cursor cursor = ((CursorLoader) mController.get().onCreateLoader(0, null)).loadInBackground()) {
            while (cursor.moveToNext()) {
                listed.add(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.NAME)));
            }
        }
        assertEquals(Arrays.asList("apple", "bob", "\u00c9mile", "Zed"), listed);
    }
}
