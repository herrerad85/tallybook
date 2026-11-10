package com.oriondev.moneywallet.ui.fragment.dialog;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.utils.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class PickerDialogNameOrderTest {

    private static final String TAG_DIALOG = "PickerDialogNameOrderTest::Dialog";
    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";
    private static final String[] NAMES = {"Zed", "apple", "bob", "\u00c9mile"};
    private static final List<String> IN_ORDER = Arrays.asList("apple", "bob", "\u00c9mile", "Zed");

    private ContentResolver mResolver;

    private interface Show {

        void run(FragmentManager fragmentManager);
    }

    @Before
    public void setUp() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        mResolver = ApplicationProvider.getApplicationContext().getContentResolver();
    }

    @Test
    public void thePeoplePickerListsInNameOrderWhateverTheCaseOrAccentOfTheFirstLetter() {
        for (String name : NAMES) {
            ContentValues values = new ContentValues();
            values.put(Contract.Person.NAME, name);
            values.put(Contract.Person.ICON, ICON);
            mResolver.insert(DataContentProvider.CONTENT_PEOPLE, values);
        }
        PeoplePickerDialog dialog = PeoplePickerDialog.newInstance();
        assertEquals(IN_ORDER, listed(dialog, Contract.Person.NAME,
                fragmentManager -> dialog.showPicker(fragmentManager, TAG_DIALOG, null)));
    }

    @Test
    public void thePlacePickerListsInNameOrderWhateverTheCaseOrAccentOfTheFirstLetter() {
        for (String name : NAMES) {
            ContentValues values = new ContentValues();
            values.put(Contract.Place.NAME, name);
            values.put(Contract.Place.ICON, ICON);
            mResolver.insert(DataContentProvider.CONTENT_PLACES, values);
        }
        PlacePickerDialog dialog = PlacePickerDialog.newInstance();
        assertEquals(IN_ORDER, listed(dialog, Contract.Place.NAME,
                fragmentManager -> dialog.showPicker(fragmentManager, TAG_DIALOG, null)));
    }

    @Test
    public void theEventPickerListsInNameOrderWhateverTheCaseOrAccentOfTheFirstLetter() {
        for (String name : NAMES) {
            ContentValues values = new ContentValues();
            values.put(Contract.Event.NAME, name);
            values.put(Contract.Event.ICON, ICON);
            values.put(Contract.Event.START_DATE, DateUtils.getSQLDateString(new Date()));
            values.put(Contract.Event.END_DATE, DateUtils.getSQLDateString(new Date()));
            mResolver.insert(DataContentProvider.CONTENT_EVENTS, values);
        }
        EventPickerDialog dialog = EventPickerDialog.newInstance();
        assertEquals(IN_ORDER, listed(dialog, Contract.Event.NAME,
                fragmentManager -> dialog.showPicker(fragmentManager, TAG_DIALOG, null, null)));
    }

    @Test
    public void theParentCategoryPickerListsInNameOrderWhateverTheCaseOrAccentOfTheFirstLetter() {
        for (String name : NAMES) {
            insertCategory(name, null);
        }
        ParentCategoryPickerDialog dialog = ParentCategoryPickerDialog.newInstance();
        assertEquals(IN_ORDER, listed(dialog, Contract.Category.NAME,
                fragmentManager -> dialog.showPicker(fragmentManager, TAG_DIALOG, null, 0L,
                        Contract.CategoryType.EXPENSE)));
    }

    @Test
    public void theMultiCategoryPickerListsParentsAndTheirChildrenInNameOrderWhateverTheCaseOrAccentOfTheFirstLetter() {
        insertCategory("Zed", null);
        long apple = insertCategory("apple", null);
        insertCategory("bob", null);
        insertCategory("\u00c9mile", null);
        for (String name : new String[] {"Zinc", "acorn", "berry", "\u00c9clair"}) {
            insertCategory(name, apple);
        }
        MultiCategoryPickerDialog dialog = MultiCategoryPickerDialog.newInstance();
        assertEquals(Arrays.asList("apple", "acorn", "berry", "\u00c9clair", "Zinc", "bob", "\u00c9mile", "Zed"),
                listed(dialog, Contract.Category.NAME,
                        fragmentManager -> dialog.showPicker(fragmentManager, TAG_DIALOG, null)));
    }

    @Test
    public void theWalletPickerListsWalletsAtTheSameIndexInNameOrderWhateverTheCaseOrAccentOfTheFirstLetter() {
        for (String name : NAMES) {
            ContentValues values = new ContentValues();
            values.put(Contract.Wallet.NAME, name);
            values.put(Contract.Wallet.ICON, ICON);
            values.put(Contract.Wallet.CURRENCY, "EUR");
            values.put(Contract.Wallet.START_MONEY, 0L);
            values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
            mResolver.insert(DataContentProvider.CONTENT_WALLETS, values);
        }
        WalletPickerDialog dialog = WalletPickerDialog.newInstance();
        assertEquals(IN_ORDER, listed(dialog, Contract.Wallet.NAME,
                fragmentManager -> dialog.showSinglePicker(fragmentManager, TAG_DIALOG, null)));
    }

    private long insertCategory(String name, Long parent) {
        ContentValues values = new ContentValues();
        values.put(Contract.Category.NAME, name);
        values.put(Contract.Category.ICON, ICON);
        values.put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.getValue());
        values.put(Contract.Category.SHOW_REPORT, true);
        values.put(Contract.Category.PARENT, parent);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, values));
    }

    private static List<String> listed(DialogFragment dialog, String column, Show show) {
        List<String> listed = new ArrayList<>();
        ActivityController<AppCompatActivity> controller =
                Robolectric.buildActivity(AppCompatActivity.class).setup();
        try {
            FragmentManager fragmentManager = controller.get().getSupportFragmentManager();
            show.run(fragmentManager);
            fragmentManager.executePendingTransactions();
            CursorLoader loader = (CursorLoader) LoaderManager.getInstance(dialog).<Cursor>getLoader(1);
            try (Cursor cursor = loader.loadInBackground()) {
                while (cursor.moveToNext()) {
                    listed.add(cursor.getString(cursor.getColumnIndexOrThrow(column)));
                }
            }
        } finally {
            controller.close();
        }
        return listed;
    }
}
