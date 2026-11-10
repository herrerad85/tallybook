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

package com.oriondev.moneywallet.ui.activity;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.widget.EditText;

import androidx.loader.content.CursorLoader;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The search field keeps what was typed, spaces and all, but the currencies are looked up
 * without the spaces a keyboard adds around a suggested word.
 */
@RunWith(RobolectricTestRunner.class)
public class CurrencyListSearchTrimTest {

    private ActivityController<CurrencyListActivity> mController;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
    }

    /**
     * ThemeEngine keeps its observers in a static list for the whole run, so a screen left alive
     * here would be recreated by a later test's mode change.
     */
    @After
    public void tearDown() {
        if (mController != null && !mController.get().isDestroyed()) {
            mController.destroy();
        }
    }

    @Test
    public void spacesAroundTheQueryAreLeftOutOfTheLookup() {
        CursorLoader loader = loaderFor(" euro ");
        assertArrayEquals(new String[] {"euro", "euro"}, loader.getSelectionArgs());
    }

    @Test
    public void aQueryOfOnlySpacesListsEveryCurrency() {
        CursorLoader loader = loaderFor("   ");
        assertNull(loader.getSelection());
    }

    @Test
    public void aCustomCurrencyNamedInLowercaseIsListedAmongTheBuiltInNamesByItsFirstLetter() {
        ContentValues values = new ContentValues();
        values.put(Contract.Currency.ISO, "XEC");
        values.put(Contract.Currency.NAME, "euro coin");
        values.put(Contract.Currency.SYMBOL, "ec");
        values.put(Contract.Currency.DECIMALS, 2);
        ApplicationProvider.getApplicationContext().getContentResolver()
                .insert(DataContentProvider.CONTENT_CURRENCIES, values);
        List<String> listed = new ArrayList<>();
        try (Cursor cursor = loaderFor("").loadInBackground()) {
            while (cursor.moveToNext()) {
                listed.add(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Currency.NAME)));
            }
        }
        int custom = listed.indexOf("euro coin");
        assertEquals(Arrays.asList("Euro", "euro coin", "Falkland Islands Pound"),
                listed.subList(custom - 1, custom + 2));
    }

    private CursorLoader loaderFor(String typed) {
        mController = Robolectric.buildActivity(CurrencyListActivity.class).setup();
        CurrencyListActivity activity = mController.get();
        EditText search = activity.findViewById(R.id.search_edit_text);
        search.setText(typed);
        return (CursorLoader) activity.onCreateLoader(0, null);
    }
}
