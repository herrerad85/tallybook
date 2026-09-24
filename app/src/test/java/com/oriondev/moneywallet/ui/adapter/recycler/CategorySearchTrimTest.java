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

package com.oriondev.moneywallet.ui.adapter.recycler;

import android.database.MatrixCursor;

import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

/**
 * A keyboard adds a space after a word picked from its suggestions, and the picker has to find
 * the same categories with or without it.
 */
@RunWith(RobolectricTestRunner.class)
public class CategorySearchTrimTest {

    @Before
    public void clearTheStoredCategories() {
        PreferenceManager.setCollapsedCategories(Collections.<String>emptySet());
    }

    @Test
    public void spacesAroundTheQueryFindTheSameCategories() {
        MatrixCursor cursor = new MatrixCursor(new String[] {
                Contract.Category.ID,
                Contract.Category.ICON,
                Contract.Category.NAME,
                Contract.Category.PARENT
        });
        cursor.addRow(new Object[] {1L, null, "Groceries", null});
        cursor.addRow(new Object[] {2L, null, "Snacks", 1L});
        cursor.addRow(new Object[] {3L, null, "Rent", null});
        CategoryCursorAdapter adapter = new CategoryCursorAdapter(null);
        adapter.swapCursor(cursor);

        adapter.setQuery("groceries");
        int expected = adapter.getItemCount();
        assertEquals(2, expected);
        for (String query : new String[] {"groceries ", " groceries", "groceries  "}) {
            adapter.setQuery(query);
            assertEquals("query \"" + query + "\"", expected, adapter.getItemCount());
        }
    }
}
