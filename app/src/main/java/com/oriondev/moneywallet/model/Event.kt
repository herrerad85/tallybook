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

import java.util.Date

/**
 * Created by andrea on 14/03/18.
 */
class Event : Identifiable, Parcelable {

    private val mId: Long
    private val mName: String?
    private val mIcon: Icon?
    private val mStartDate: Date?
    private val mEndDate: Date?

    constructor(id: Long, name: String?, icon: Icon?, startDate: Date?, endDate: Date?) {
        mId = id
        mName = name
        mIcon = icon
        mStartDate = startDate
        mEndDate = endDate
    }

    protected constructor(source: Parcel) {
        mId = source.readLong()
        mName = source.readString()
        mIcon = source.readParcelable(Icon::class.java.classLoader)
        mStartDate = Date(source.readLong())
        mEndDate = Date(source.readLong())
    }

    override fun getId(): Long {
        return mId
    }

    fun getName(): String? {
        return mName
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(mId)
        dest.writeString(mName)
        dest.writeParcelable(mIcon, flags)
        dest.writeLong(mStartDate!!.time)
        dest.writeLong(mEndDate!!.time)
    }

    companion object {

        @JvmField
        val CREATOR: Parcelable.Creator<Event> = object : Parcelable.Creator<Event> {
            override fun createFromParcel(source: Parcel): Event {
                return Event(source)
            }

            override fun newArray(size: Int): Array<Event?> {
                return arrayOfNulls(size)
            }
        }
    }
}
