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

package com.oriondev.moneywallet.utils

import android.text.TextUtils
import android.widget.ImageView

import com.oriondev.moneywallet.model.ColorIcon
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.model.VectorIcon

import org.json.JSONException
import org.json.JSONObject

import java.util.Locale

/**
 * Created by andrea on 27/01/18.
 */

object IconLoader {

    @JvmField
    val UNKNOWN: Icon = ColorIcon("#FFC107", "?")

    @JvmStatic
    fun getColorIconString(source: String?): String {
        val trimmed = if (source != null) source.trim { it <= ' ' }.replace(" +".toRegex(), " ") else null
        if (!TextUtils.isEmpty(trimmed)) {
            val parts = trimmed!!.split(" ").dropLastWhile { it.isEmpty() }
            if (parts.size >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                val builder = parts[0][0].toString() + parts[1][0]
                return builder.uppercase(Locale.getDefault())
            } else {
                return trimmed.substring(0, 1).uppercase(Locale.getDefault())
            }
        }
        return "?"
    }

    @JvmStatic
    fun parse(icon: String?): Icon? {
        if (icon != null) {
            try {
                val jsonObject = JSONObject(icon)
                when (Icon.getType(jsonObject)!!) {
                    Icon.Type.RESOURCE ->
                        return VectorIcon(jsonObject)
                    Icon.Type.COLOR ->
                        return ColorIcon(jsonObject)
                }
            } catch (e: JSONException) {
                // TODO keep track?
                e.printStackTrace()
            }
        }
        return null
    }

    @JvmStatic
    fun parseAndLoad(encoded: String?, imageView: ImageView?) {
        loadInto(parse(encoded), imageView)
    }

    @JvmStatic
    fun parseAndLoad(encoded: String?, fallback: Icon?, imageView: ImageView?) {
        loadInto(parse(encoded), fallback, imageView)
    }

    @JvmStatic
    fun loadInto(icon: Icon?, imageView: ImageView?) {
        loadInto(icon, UNKNOWN, imageView)
    }

    @JvmStatic
    fun loadInto(icon: Icon?, fallback: Icon?, imageView: ImageView?) {
        if (icon == null || !icon.apply(imageView)) {
            if (fallback != null) {
                fallback.apply(imageView)
            }
        }
    }
}
