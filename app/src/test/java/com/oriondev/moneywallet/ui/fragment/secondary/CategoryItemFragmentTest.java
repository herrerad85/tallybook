package com.oriondev.moneywallet.ui.fragment.secondary;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.CheckBox;

import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * The reports keep a transaction only when its category and its parent are both included, so a
 * subcategory under a hidden parent reaches no report however its own box reads. This screen used
 * to read that box alone and say the category was in the reports when none of its money was.
 *
 * What each case reads is the line the screen actually shows, so a branch that picks the wrong one
 * of the three fails here whatever the stored settings say.
 */
@RunWith(RobolectricTestRunner.class)
public class CategoryItemFragmentTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";
    private static final String TAG_FRAGMENT = "CategoryItemFragmentTest::Fragment";

    private Context mContext;
    private ContentResolver mResolver;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(mContext);
        mResolver = mContext.getContentResolver();
    }

    @Test
    public void aTopLevelCategoryInTheReportsSaysSo() {
        long groceries = insertCategory("Groceries", null, true);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                CheckBox box = showCategory(activity, groceries);
                assertTrue(box.isChecked());
                assertEquals(mContext.getString(R.string.hint_show_category_report_on),
                        box.getText().toString());
            });
        }
    }

    @Test
    public void aTopLevelCategoryKeptOutSaysSo() {
        long groceries = insertCategory("Groceries", null, false);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                CheckBox box = showCategory(activity, groceries);
                assertFalse(box.isChecked());
                assertEquals(mContext.getString(R.string.hint_show_category_report_off),
                        box.getText().toString());
            });
        }
    }

    @Test
    public void aSubcategoryUnderAnIncludedParentSaysItIsIn() {
        long groceries = insertCategory("Groceries", null, true);
        long snacks = insertCategory("Snacks", groceries, true);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                CheckBox box = showCategory(activity, snacks);
                assertTrue(box.isChecked());
                assertEquals(mContext.getString(R.string.hint_show_category_report_on),
                        box.getText().toString());
            });
        }
    }

    /**
     * The one the issue is about. The box stays ticked because that is the stored setting, and the
     * line beside it names the category holding the money out.
     */
    @Test
    public void aSubcategoryUnderAHiddenParentNamesTheParent() {
        long groceries = insertCategory("Groceries", null, false);
        long snacks = insertCategory("Snacks", groceries, true);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                CheckBox box = showCategory(activity, snacks);
                assertTrue(box.isChecked());
                assertEquals(mContext.getString(R.string.hint_show_category_report_off_parent,
                        "Groceries"), box.getText().toString());
                // both sides of the line above read the same resource, so they move together and
                // a reword that dropped the parent would pass. This is what pins that the name
                // of the category holding the money out actually reaches the screen.
                assertTrue(box.getText().toString().contains("Groceries"));
            });
        }
    }

    /**
     * Its own setting already answers the question, so naming the parent here would tell the user
     * about a second reason they cannot act on from this screen.
     */
    @Test
    public void aSubcategoryKeptOutOnItsOwnDoesNotNameTheParent() {
        long groceries = insertCategory("Groceries", null, false);
        long snacks = insertCategory("Snacks", groceries, false);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                CheckBox box = showCategory(activity, snacks);
                assertFalse(box.isChecked());
                assertEquals(mContext.getString(R.string.hint_show_category_report_off),
                        box.getText().toString());
            });
        }
    }

    private CheckBox showCategory(FragmentActivity activity, long categoryId) {
        CategoryItemFragment fragment = new CategoryItemFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, fragment, TAG_FRAGMENT)
                .commitNow();
        fragment.showItemId(categoryId);
        // the query runs on a background thread, and the wheel goes away only once its result lands
        View wheel = fragment.getView().findViewById(R.id.secondary_panel_progress_wheel);
        for (int i = 0; i < 200 && wheel.getVisibility() != View.GONE; i++) {
            shadowOf(Looper.getMainLooper()).idle();
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
        assertEquals("the category never loaded", View.GONE, wheel.getVisibility());
        return fragment.getView().findViewById(R.id.show_report_check_box);
    }

    private long insertCategory(String name, Long parent, boolean showReport) {
        ContentValues values = new ContentValues();
        values.put(Contract.Category.NAME, name);
        values.put(Contract.Category.ICON, ICON);
        values.put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.getValue());
        values.put(Contract.Category.SHOW_REPORT, showReport);
        if (parent != null) {
            values.put(Contract.Category.PARENT, parent);
        }
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, values));
    }
}
