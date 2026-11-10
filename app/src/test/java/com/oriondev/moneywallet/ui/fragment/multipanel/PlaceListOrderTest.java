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
public class PlaceListOrderTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"P\"}";

    @Test
    public void thePlacesAreListedFromAToZ() {
        assertEquals(Arrays.asList("Bluebell Diner", "Corner Coffee", "Terminal B"),
                listed("Corner Coffee", "Terminal B", "Bluebell Diner"));
    }

    @Test
    public void thePlacesAreListedInNameOrderWhateverTheCaseOrAccentOfTheirFirstLetter() {
        assertEquals(Arrays.asList("apple", "bob", "\u00c9mile", "Zed"),
                listed("Zed", "apple", "bob", "\u00c9mile"));
    }

    private static List<String> listed(String... names) {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        ContentResolver resolver = context.getContentResolver();
        for (String name : names) {
            ContentValues values = new ContentValues();
            values.put(Contract.Place.NAME, name);
            values.put(Contract.Place.ICON, ICON);
            resolver.insert(DataContentProvider.CONTENT_PLACES, values);
        }
        ActivityController<AppCompatActivity> controller = Robolectric.buildActivity(AppCompatActivity.class).setup();
        PlaceMultiPanelFragment fragment = new PlaceMultiPanelFragment();
        controller.get().getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();
        List<String> listed = new ArrayList<>();
        try (Cursor cursor = ((CursorLoader) fragment.onCreateLoader(0, null)).loadInBackground()) {
            while (cursor.moveToNext()) {
                listed.add(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Place.NAME)));
            }
        }
        controller.pause().stop().destroy();
        return listed;
    }
}
