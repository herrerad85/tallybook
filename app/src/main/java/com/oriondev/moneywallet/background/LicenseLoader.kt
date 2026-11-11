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

import com.oriondev.moneywallet.model.License

import org.json.JSONArray
import org.json.JSONException

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Created by andrea on 06/04/18.
 */
class LicenseLoader : AbstractGenericLoader<MutableList<License>> {

    constructor(context: Context) : super(context)

    override fun loadInBackground(): MutableList<License>? {
        val licenses: MutableList<License> = ArrayList()
        licenses.addAll(loadLicenseFile("libraries_base.json"))
        licenses.addAll(loadLicenseFile("libraries_version.json"))
        licenses.addAll(loadLicenseFile("libraries_map.json"))
        return licenses
    }

    private fun loadLicenseFile(resource: String): MutableList<License> {
        val licenses: MutableList<License> = ArrayList()
        val jsonBuilder = StringBuilder()
        try {
            val inputStream: InputStream = context.assets.open("resources/" + resource)
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))
            while (true) {
                val line: String = bufferedReader.readLine() ?: break
                jsonBuilder.append(line)
            }
            val array = JSONArray(jsonBuilder.toString())
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                licenses.add(License(
                        item.getString("name"),
                        item.optString("author"),
                        item.optString("website"),
                        item.optString("version"),
                        item.optString("copyright"),
                        item.optString("license")
                ))
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return licenses
    }
}
