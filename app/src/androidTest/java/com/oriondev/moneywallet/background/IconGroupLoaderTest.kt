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

package com.oriondev.moneywallet.background

import android.content.Context

import androidx.test.platform.app.InstrumentationRegistry

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.HashSet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

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
class IconGroupLoaderTest {

    companion object {

        private const val CATALOG = "resources/icons.json"
    }

    @Test
    @Throws(Exception::class)
    fun everyIconInTheCatalogResolves() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val groups = IconGroupLoader(context).loadInBackground()

        val categories: JSONArray = JSONObject(readCatalog(context)).getJSONArray("categories")
        var declared = 0
        for (i in 0 until categories.length()) {
            declared += categories.getJSONObject(i).getJSONArray("icons").length()
        }
        assertTrue("icons.json declares no icons at all", declared > 0)

        assertNotNull("the loader returned null", groups)
        assertEquals("the loader did not return every group icons.json declares",
                categories.length(), groups!!.size)

        var loaded = 0
        for (group in groups) {
            val name = group.getGroupName()
            assertFalse("the group " + name + " has no icons", group.getGroupIcons().isEmpty())
            val seen: MutableSet<String> = HashSet()
            for (icon in group.getGroupIcons()) {
                assertNotNull("an icon in " + name + " has no drawable behind it, " + icon,
                        icon.getDrawable(context))
                assertTrue(icon.toString() + " is listed twice in " + name, seen.add(icon.toString()))
                loaded++
            }
        }
        assertEquals("the loader did not return every icon icons.json declares", declared, loaded)
    }

    @Throws(IOException::class)
    private fun readCatalog(context: Context): String {
        val builder = StringBuilder()
        val reader = BufferedReader(
                InputStreamReader(context.assets.open(CATALOG)))
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                builder.append(line)
            }
        } finally {
            reader.close()
        }
        return builder.toString()
    }
}
