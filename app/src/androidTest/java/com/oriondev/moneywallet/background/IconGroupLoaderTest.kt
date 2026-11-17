/*
 * Copyright (c) 2018.
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

package com.oriondev.moneywallet.background;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.oriondev.moneywallet.model.Icon;
import com.oriondev.moneywallet.model.IconGroup;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * IconGroupLoader builds the picker's list from assets/resources/icons.json, and nothing in the
 * build checks that a name in that file resolves to anything. This runs the check through the
 * loader itself, so it sees what the picker sees.
 *
 * Four ways the file and the resources can disagree, and the build stays green for all four.
 * A syntax error makes the JSONObject constructor throw before the first group is read, so
 * loadInBackground catches JSONException, returns an empty list, and the picker shows its empty
 * state. A missing key or a wrong type inside the file throws the same JSONException partway
 * through the loop that fills the list, and because the catch sits outside that loop the loader
 * returns the groups it had already read and the rest disappear with nothing on screen to say so.
 * A group naming a string that is not there makes getStringByName pass 0 to Context.getString,
 * which throws Resources.NotFoundException, which loadInBackground does not catch. An icon naming
 * a drawable that is not there resolves to id 0, so IconAdapter falls back to IconLoader.UNKNOWN
 * and the tile shows an amber question mark.
 *
 * The second of those is why this counts what the file declares and compares it to what the
 * loader returned. A truncated list is otherwise indistinguishable from a complete one, and the
 * six groups added most recently sit at the end of the file, so they are the ones a partial read
 * drops first.
 *
 * It runs on a device on purpose. Resources.getIdentifier is case sensitive and File.exists is not
 * on Windows, so a name differing only in case passes any check made against the file system and
 * still fails here.
 */
public class IconGroupLoaderTest {

    private static final String CATALOG = "resources/icons.json";

    @Test
    public void everyIconInTheCatalogResolves() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        List<IconGroup> groups = new IconGroupLoader(context).loadInBackground();

        JSONArray categories = new JSONObject(readCatalog(context)).getJSONArray("categories");
        int declared = 0;
        for (int i = 0; i < categories.length(); i++) {
            declared += categories.getJSONObject(i).getJSONArray("icons").length();
        }
        assertTrue("icons.json declares no icons at all", declared > 0);

        assertNotNull("the loader returned null", groups);
        assertEquals("the loader did not return every group icons.json declares",
                categories.length(), groups.size());

        int loaded = 0;
        for (IconGroup group : groups) {
            String name = group.getGroupName();
            assertFalse("the group " + name + " has no icons", group.getGroupIcons().isEmpty());
            Set<String> seen = new HashSet<>();
            for (Icon icon : group.getGroupIcons()) {
                assertNotNull("an icon in " + name + " has no drawable behind it, " + icon,
                        icon.getDrawable(context));
                assertTrue(icon + " is listed twice in " + name, seen.add(icon.toString()));
                loaded++;
            }
        }
        assertEquals("the loader did not return every icon icons.json declares", declared, loaded);
    }

    private String readCatalog(Context context) throws IOException {
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(CATALOG)));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        } finally {
            reader.close();
        }
        return builder.toString();
    }
}
