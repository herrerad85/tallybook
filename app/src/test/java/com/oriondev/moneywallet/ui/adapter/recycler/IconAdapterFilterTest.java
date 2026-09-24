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

import com.oriondev.moneywallet.model.Icon;
import com.oriondev.moneywallet.model.IconGroup;
import com.oriondev.moneywallet.model.VectorIcon;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class IconAdapterFilterTest {

    @Test
    public void filtersOnHeadingAndIconName() throws JSONException {
        IconAdapter adapter = new IconAdapter(null);
        adapter.setIconGroups(Arrays.asList(
                group("Food and drinks", "ic_icon_pizza", "ic_icon_cake", "ic_icon_beer_stein"),
                group("Sport", "ic_icon_dart_board_2", "ic_icon_golf_ball", "ic_icon_basketball")));
        assertEquals(8, adapter.getItemCount());

        adapter.setQuery("food");
        assertEquals(4, adapter.getItemCount());
        assertEquals("ic_icon_pizza", resourceAt(adapter, 1));
        assertEquals("ic_icon_cake", resourceAt(adapter, 2));
        assertEquals("ic_icon_beer_stein", resourceAt(adapter, 3));

        adapter.setQuery("ball");
        assertEquals(3, adapter.getItemCount());
        assertTrue(adapter.isHeader(0));
        assertEquals("Sport", adapter.getHeaderTextAt(0));
        assertFalse(adapter.isHeader(1));
        assertEquals("ic_icon_golf_ball", resourceAt(adapter, 1));
        assertEquals("ic_icon_basketball", resourceAt(adapter, 2));

        adapter.setQuery("dart board");
        assertEquals(2, adapter.getItemCount());

        adapter.setQuery("BEER");
        assertEquals(2, adapter.getItemCount());
        assertEquals("ic_icon_beer_stein", resourceAt(adapter, 1));

        adapter.setQuery("pizza ");
        assertEquals(2, adapter.getItemCount());
        assertEquals("ic_icon_pizza", resourceAt(adapter, 1));

        adapter.setQuery("icon");
        assertEquals(0, adapter.getItemCount());

        adapter.setQuery("");
        assertEquals(8, adapter.getItemCount());
    }

    @Test
    public void matchesIconNamesOnTurkishDevices() throws JSONException {
        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));
        try {
            IconAdapter adapter = new IconAdapter(null);
            adapter.setIconGroups(Arrays.asList(group("Food and drinks", "ic_icon_pizza", "ic_icon_cake")));
            // the Turkish lower case of I is a dotless i, which no icon name contains
            adapter.setQuery("PIZZA");
            assertEquals(2, adapter.getItemCount());
            assertEquals("ic_icon_pizza", resourceAt(adapter, 1));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    public void matchesTranslatedHeadingsOnTurkishDevices() throws JSONException {
        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));
        try {
            IconAdapter adapter = new IconAdapter(null);
            adapter.setIconGroups(Arrays.asList(group("Işık", "ic_icon_lamp", "ic_icon_sun")));
            adapter.setQuery("IŞIK");
            assertEquals(3, adapter.getItemCount());
            adapter.setQuery("ışık");
            assertEquals(3, adapter.getItemCount());
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    private static String resourceAt(IconAdapter adapter, int position) {
        return ((VectorIcon) adapter.getIconAt(position)).getResourceName();
    }

    private static IconGroup group(String name, String... resources) throws JSONException {
        List<Icon> icons = new ArrayList<>();
        for (String resource : resources) {
            icons.add(new VectorIcon(new JSONObject().put("resource", resource)));
        }
        return new IconGroup(name, icons);
    }
}
