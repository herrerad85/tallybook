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
 * Created by andrea on 10/03/18.
 */

class Place : Parcelable {

    private val mId: Long
    private val mName: String?
    private val mIcon: Icon?
    private val mAddress: String?
    private val mLatitude: Double?
    private val mLongitude: Double?

    constructor(id: Long, name: String?, icon: Icon?, address: String?, latitude: Double?, longitude: Double?) {
        mId = id
        mName = name
        mIcon = icon
        mAddress = address
        mLatitude = latitude
        mLongitude = longitude
    }

    constructor(id: Long, name: String?, icon: Icon?, address: String?, coordinates: Coordinates?) {
        mId = id
        mName = name
        mIcon = icon
        mAddress = address
        mLatitude = if (coordinates != null) coordinates.getLatitude() else null
        mLongitude = if (coordinates != null) coordinates.getLongitude() else null
    }

    fun getId(): Long {
        return mId
    }

    fun getName(): String? {
        return mName
    }

    fun getAddress(): String? {
        return mAddress
    }

    fun hasCoordinates(): Boolean {
        return mLatitude != null && mLongitude != null
    }

    fun getCoordinates(): Coordinates? {
        return if (hasCoordinates()) Coordinates(mLatitude!!, mLongitude!!) else null
    }

    protected constructor(source: Parcel) {
        mId = source.readLong()
        mName = source.readString()
        mIcon = source.readParcelable(Icon::class.java.classLoader)
        mAddress = source.readString()
        if (source.readByte().toInt() == 0) {
            mLatitude = null
        } else {
            mLatitude = source.readDouble()
        }
        if (source.readByte().toInt() == 0) {
            mLongitude = null
        } else {
            mLongitude = source.readDouble()
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(mId)
        dest.writeString(mName)
        dest.writeParcelable(mIcon, flags)
        dest.writeString(mAddress)
        if (mLatitude == null) {
            dest.writeByte(0.toByte())
        } else {
            dest.writeByte(1.toByte())
            dest.writeDouble(mLatitude)
        }
        if (mLongitude == null) {
            dest.writeByte(0.toByte())
        } else {
            dest.writeByte(1.toByte())
            dest.writeDouble(mLongitude)
        }
    }

    companion object {

        @JvmField
        val CREATOR: Parcelable.Creator<Place> = object : Parcelable.Creator<Place> {
            override fun createFromParcel(source: Parcel): Place {
                return Place(source)
            }

            override fun newArray(size: Int): Array<Place?> {
                return arrayOfNulls(size)
            }
        }
    }
}
