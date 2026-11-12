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

import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.model.IconGroup
import com.oriondev.moneywallet.model.VectorIcon

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Collections
import java.util.Comparator

/**
 * This class is used by the IconListActivity to fetch all the available icons and organize
 * them inside a list of IconGroup items. The list of available icons is statically described
 * by the icons.json file inside the assets/resources directory of the project.
 *
 * Every icon is either original artwork or an original disc with a glyph from a permissively
 * licensed library composed on top, so a new one is a vector drawable in res/drawable named by an
 * entry in that file. The libraries and their licenses are in THIRD_PARTY_NOTICES.md. Nothing in
 * the build checks that a name in the file resolves, so run the instrumented IconGroupLoaderTest
 * on a device after adding one.
 */
class IconGroupLoader : AbstractGenericLoader<MutableList<IconGroup>> {

    constructor(context: Context) : super(context)

    override fun loadInBackground(): MutableList<IconGroup>? {
        val iconGroups: MutableList<IconGroup> = ArrayList()
        val jsonBuilder = StringBuilder()
        try {
            val inputStream: InputStream = context.assets.open("resources/icons.json")
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))
            while (true) {
                val line: String = bufferedReader.readLine() ?: break
                jsonBuilder.append(line)
            }
            val iconsObj = JSONObject(jsonBuilder.toString())
            val categories = iconsObj.getJSONArray("categories")
            for (i in 0 until categories.length()) {
                iconGroups.add(parseIconGroup(categories.getJSONObject(i)))
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        Collections.sort(iconGroups, AlphabeticIconComparator())
        return iconGroups
    }

    @Throws(JSONException::class)
    private fun parseIconGroup(category: JSONObject): IconGroup {
        val groupName = getStringByName(category.getString("name"))
        val icons = parseIconList(category.getJSONArray("icons"))
        return IconGroup(groupName, icons)
    }

    @Throws(JSONException::class)
    private fun parseIconList(icons: JSONArray): MutableList<Icon> {
        val iconList: MutableList<Icon> = ArrayList()
        for (i in 0 until icons.length()) {
            iconList.add(VectorIcon(icons.getJSONObject(i)))
        }
        return iconList
    }

    private fun getStringByName(name: String): String {
        val context: Context = getContext()
        val packageName = context.packageName
        val resId = context.resources.getIdentifier(name, "string", packageName)
        return context.getString(resId)
    }

    private inner class AlphabeticIconComparator : Comparator<IconGroup> {

        override fun compare(icon1: IconGroup, icon2: IconGroup): Int {
            return icon1.getGroupName()!!.compareTo(icon2.getGroupName()!!)
        }
    }
}
