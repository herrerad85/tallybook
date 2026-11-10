package com.oriondev.moneywallet.storage.database;

import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.preference.PreferenceManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static com.oriondev.moneywallet.storage.database.SplitLedger.ICON;
import static com.oriondev.moneywallet.storage.database.SplitLedger.database;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class BudgetCategoryNameOrderTest {

    @Test
    public void aBudgetNamesItsCategoriesInNameOrderWhateverTheCaseOrAccentOfTheirFirstLetter() {
        Context context = ApplicationProvider.getApplicationContext();
        PreferenceManager.initialize(context);
        TestDatabases.useFreshDatabase(context);
        SplitLedger.build(context);
        long[] categories = {970, 971, 972, 973};
        String[] names = {"Zed", "apple", "bob", "Émile"};
        for (int i = 0; i < categories.length; i++) {
            database(context).execSQL("INSERT INTO categories (category_id, category_name, category_icon, " +
                            "category_type, category_show_report, uuid, last_edit, deleted) VALUES " +
                            "(?, ?, ?, ?, 1, ?, 1, 0)",
                    new Object[] {categories[i], names[i], ICON, Schema.CategoryType.EXPENSE,
                            "category-" + categories[i]});
        }
        long budget = SplitLedger.insertCategoryBudget(context, 990, categories);
        try (Cursor cursor = context.getContentResolver().query(DataContentProvider.CONTENT_BUDGETS,
                new String[] {Contract.Budget.CATEGORY_NAME}, Contract.Budget.ID + " = ?",
                new String[] {String.valueOf(budget)}, null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals("apple, bob, Émile, Zed", cursor.getString(0));
        }
    }
}
