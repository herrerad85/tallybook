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
import android.text.TextUtils

import com.oriondev.moneywallet.ui.drawable.TextDrawable
import com.oriondev.moneywallet.utils.Utils

import java.util.Locale

/**
 * Created by andrea on 31/01/18.
 */
class CurrencyUnit : Parcelable {

    val iso: String?
    val name: String?
    val symbol: String?
    val decimals: Int

    val symbolOrIso: String?
        get() = if (symbol.isNullOrEmpty()) iso else symbol

    constructor(iso: String?, name: String?, symbol: String?, decimals: Int) {
        this.iso = iso
        this.name = name
        this.symbol = symbol
        this.decimals = decimals
    }

    @Suppress("WeakerAccess")
    protected constructor(source: Parcel) {
        iso = source.readString()
        name = source.readString()
        symbol = source.readString()
        decimals = source.readInt()
    }

    fun hasDecimals(): Boolean {
        return decimals > 0
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(iso)
        parcel.writeString(name)
        parcel.writeString(symbol)
        parcel.writeInt(decimals)
    }

    override fun equals(other: Any?): Boolean {
        return other != null && other is CurrencyUnit && (other === this || iso!!.equals(other.iso))
    }

    companion object {

        @JvmField
        val CREATOR: Parcelable.Creator<CurrencyUnit> = object : Parcelable.Creator<CurrencyUnit> {

            override fun createFromParcel(source: Parcel): CurrencyUnit {
                return CurrencyUnit(source)
            }

            override fun newArray(size: Int): Array<CurrencyUnit?> {
                return arrayOfNulls(size)
            }

        }

        @JvmStatic
        @DrawableRes
        fun getCurrencyFlag(context: Context?, iso: String?): Int {
            val resourceName = "ic_flag_" + iso!!.lowercase(Locale.ENGLISH)
            return getIconResourceId(context, resourceName)
        }

        @JvmStatic
        fun getCurrencyDrawable(iso: String?): Drawable? {
            val text = if (TextUtils.isEmpty(iso)) "?" else iso!!.substring(0, 1)
            val color = Utils.getRandomMDColor(iso!!.hashCode())
            return TextDrawable.builder()
                    .beginConfig()
                        .width(60)
                        .height(60)
                        .textColor(Utils.getBestColor(color))
                    .endConfig()
                    .buildRect(text, color)
        }

        private fun getIconResourceId(context: Context?, resource: String?): Int {
            val packageName = context!!.packageName
            return context.resources.getIdentifier(resource, "drawable", packageName)
        }
    }
}
