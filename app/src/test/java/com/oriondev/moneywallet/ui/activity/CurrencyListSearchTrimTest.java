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

import android.content.Context;
import android.widget.EditText;

import androidx.loader.content.CursorLoader;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.TestDatabases;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

/**
 * The search field keeps what was typed, spaces and all, but the currencies are looked up
 * without the spaces a keyboard adds around a suggested word.
 */
@RunWith(RobolectricTestRunner.class)
public class CurrencyListSearchTrimTest {

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
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

    private static CursorLoader loaderFor(String typed) {
        CurrencyListActivity activity = Robolectric.buildActivity(CurrencyListActivity.class).setup().get();
        EditText search = activity.findViewById(R.id.search_edit_text);
        search.setText(typed);
        return (CursorLoader) activity.onCreateLoader(0, null);
    }
}
