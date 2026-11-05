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

package com.oriondev.moneywallet.model

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.ColorInt
import android.widget.ImageView

import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.drawable.DotMatrixDrawable
import com.oriondev.moneywallet.ui.drawable.TextDrawable
import com.oriondev.moneywallet.utils.Utils

import org.json.JSONException
import org.json.JSONObject

/**
 * Created by andrea on 23/01/18.
 */
class ColorIcon : Icon {

    private val mColor: String?
    private val mName: String?

    @Throws(JSONException::class)
    constructor(jsonObject: JSONObject?) {
        mColor = jsonObject!!.getString(COLOR)
        mName = jsonObject.optString(NAME)
    }

    constructor(color: Int, name: String?) {
        mColor = Utils.getHexColor(color)
        mName = name
    }

    constructor(color: String?, name: String?) {
        mColor = color
        mName = name
    }

    constructor(icon: ColorIcon, name: String?) {
        mColor = icon.mColor
        mName = name
    }

    @Suppress("WeakerAccess")
    protected constructor(source: Parcel) {
        mColor = source.readString()
        mName = source.readString()
    }

    override fun getType(): Type {
        return Type.COLOR
    }

    @Throws(JSONException::class)
    override fun writeJSON(jsonObject: JSONObject) {
        jsonObject.put(COLOR, mColor)
        jsonObject.put(NAME, mName)
    }

    override fun apply(imageView: ImageView?): Boolean {
        val drawable = getDrawable()
        imageView!!.setImageDrawable(drawable)
        return true
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(mColor)
        dest.writeString(mName)
    }

    @ColorInt
    fun getColor(): Int {
        return Color.parseColor(mColor)
    }

    override fun getDrawable(context: Context?): Drawable? {
        return getDrawable()
    }

    fun getDrawable(): Drawable? {
        val backgroundColor = getColor()
        if (PreferenceManager.isDotMatrixIconsEnabled()) {
            val dotMatrix: Drawable? = DotMatrixDrawable.forText(mName, backgroundColor)
            if (dotMatrix != null) {
                return dotMatrix
            }
        }
        return TextDrawable.builder()
                .beginConfig()
                    .width(60)
                    .height(60)
                    .textColor(Utils.getBestColor(backgroundColor))
                .endConfig()
                .buildRound(mName, getColor())
    }

    companion object {

        private const val COLOR = "color"
        private const val NAME = "name"

        @JvmField
        val CREATOR: Parcelable.Creator<ColorIcon> = object : Parcelable.Creator<ColorIcon> {

            override fun createFromParcel(source: Parcel): ColorIcon {
                return ColorIcon(source)
            }

            override fun newArray(size: Int): Array<ColorIcon?> {
                return arrayOfNulls(size)
            }
        }
    }
}
