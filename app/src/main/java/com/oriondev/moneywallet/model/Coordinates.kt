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
 * Created by andrea on 16/10/18.
 */

class Coordinates : Parcelable {

    private val mLatitude: Double
    private val mLongitude: Double

    constructor(latitude: Double, longitude: Double) {
        mLatitude = latitude
        mLongitude = longitude
    }

    @Suppress("WeakerAccess")
    protected constructor(source: Parcel) {
        mLatitude = source.readDouble()
        mLongitude = source.readDouble()
    }

    fun getLatitude(): Double {
        return mLatitude
    }

    fun getLongitude(): Double {
        return mLongitude
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeDouble(mLatitude)
        dest.writeDouble(mLongitude)
    }

    companion object {

        @JvmField
        val CREATOR: Parcelable.Creator<Coordinates> = object : Parcelable.Creator<Coordinates> {

            override fun createFromParcel(source: Parcel): Coordinates {
                return Coordinates(source)
            }

            override fun newArray(size: Int): Array<Coordinates?> {
                return arrayOfNulls(size)
            }

        }
    }
}
