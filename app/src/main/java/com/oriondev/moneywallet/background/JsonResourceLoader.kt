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

import org.json.JSONException
import org.json.JSONObject

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Created by andrea on 08/12/18.
 */
class JsonResourceLoader : AbstractGenericLoader<JSONObject> {

    private val mResourcePath: String

    constructor(context: Context, resource: String?) : super(context) {
        mResourcePath = "resources/" + resource
    }

    override fun loadInBackground(): JSONObject? {
        val jsonBuilder = StringBuilder()
        try {
            val inputStream: InputStream = context.assets.open(mResourcePath)
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))
            while (true) {
                val line: String = bufferedReader.readLine() ?: break
                jsonBuilder.append(line)
            }
            return JSONObject(jsonBuilder.toString())
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return null
    }
}
