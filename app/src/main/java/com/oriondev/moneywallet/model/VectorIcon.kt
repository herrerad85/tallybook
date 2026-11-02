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
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import android.widget.ImageView

import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.drawable.DotMatrixDrawable

import org.json.JSONException
import org.json.JSONObject

/**
 * Created by andrea on 23/01/18.
 */
class VectorIcon : Icon {

    private val mResourceName: String?

    @Throws(JSONException::class)
    constructor(jsonObject: JSONObject?) {
        mResourceName = jsonObject!!.getString(RESOURCE)
    }

    protected constructor(source: Parcel) {
        mResourceName = source.readString()
    }

    override fun getType(): Type {
        return Type.RESOURCE
    }

    @Throws(JSONException::class)
    override fun writeJSON(jsonObject: JSONObject) {
        jsonObject.put(RESOURCE, mResourceName)
    }

    override fun getDrawable(context: Context?): Drawable? {
        val dotMatrix = getDotMatrix(context)
        if (dotMatrix != null) {
            return dotMatrix
        }
        val drawableId = getDrawableId(context, mResourceName)
        if (drawableId > 0) {
            return ContextCompat.getDrawable(context!!, drawableId)
        }
        return null
    }

    override fun apply(imageView: ImageView?): Boolean {
        val dotMatrix = getDotMatrix(imageView!!.context)
        if (dotMatrix != null) {
            // a row recycled from a classic icon may still have a Glide load in flight
            Glide.with(imageView).clear(imageView)
            imageView.setImageDrawable(dotMatrix)
            return true
        }
        val resourceId = getResource(imageView.context)
        if (resourceId > 0) {
            Glide.with(imageView)
                    .load(resourceId)
                    .transition(DrawableTransitionOptions().crossFade())
                    .into(imageView)
            return true
        } else {
            return false
        }
    }

    private fun getDotMatrix(context: Context?): Drawable? {
        return if (PreferenceManager.isDotMatrixIconsEnabled()) DotMatrixDrawable.forResource(context, mResourceName) else null
    }

    @DrawableRes
    fun getResource(context: Context?): Int {
        return getDrawableId(context, mResourceName)
    }

    fun getResourceName(): String? {
        return mResourceName
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(mResourceName)
    }

    companion object {

        private const val RESOURCE = "resource"

        @JvmField
        val CREATOR: Parcelable.Creator<VectorIcon> = object : Parcelable.Creator<VectorIcon> {

            override fun createFromParcel(source: Parcel): VectorIcon {
                return VectorIcon(source)
            }

            override fun newArray(size: Int): Array<VectorIcon?> {
                return arrayOfNulls(size)
            }
        }
    }
}
