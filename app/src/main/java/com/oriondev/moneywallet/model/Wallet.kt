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

import android.os.Parcel
import android.os.Parcelable

/**
 * Created by andrea on 09/03/18.
 */
class Wallet : Identifiable, Parcelable {

    private val mId: Long
    val name: String?
    private val mIcon: Icon?
    val currency: CurrencyUnit?
    private val mStartMoney: Long
    private val mTotalMoney: Long

    constructor(id: Long, name: String?, icon: Icon?, currency: CurrencyUnit?, startMoney: Long, totalMoney: Long) {
        mId = id
        this.name = name
        mIcon = icon
        this.currency = currency
        mStartMoney = startMoney
        mTotalMoney = totalMoney
    }

    override fun getId(): Long {
        return mId
    }

    protected constructor(source: Parcel) {
        mId = source.readLong()
        name = source.readString()
        mIcon = source.readParcelable(Icon::class.java.classLoader)
        currency = source.readParcelable(CurrencyUnit::class.java.classLoader)
        mStartMoney = source.readLong()
        mTotalMoney = source.readLong()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(mId)
        dest.writeString(name)
        dest.writeParcelable(mIcon, flags)
        dest.writeParcelable(currency, flags)
        dest.writeLong(mStartMoney)
        dest.writeLong(mTotalMoney)
    }

    companion object {

        @JvmField
        val CREATOR: Parcelable.Creator<Wallet> = object : Parcelable.Creator<Wallet> {
            override fun createFromParcel(source: Parcel): Wallet {
                return Wallet(source)
            }

            override fun newArray(size: Int): Array<Wallet?> {
                return arrayOfNulls(size)
            }
        }
    }
}
