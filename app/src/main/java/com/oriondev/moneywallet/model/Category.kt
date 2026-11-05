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

import com.oriondev.moneywallet.storage.database.Contract

/**
 * Created by andrea on 02/03/18.
 */
class Category : Parcelable, Identifiable {

    private val mId: Long
    val name: String?
    val icon: Icon?
    val type: Contract.CategoryType?
    val tag: String?

    constructor(id: Long, name: String?, icon: Icon?, type: Contract.CategoryType?) {
        mId = id
        this.name = name
        this.icon = icon
        this.type = type
        tag = null
    }

    constructor(id: Long, name: String?, icon: Icon?, type: Contract.CategoryType?, tag: String?) {
        mId = id
        this.name = name
        this.icon = icon
        this.type = type
        this.tag = tag
    }

    @Suppress("WeakerAccess")
    protected constructor(source: Parcel) {
        mId = source.readLong()
        name = source.readString()
        icon = source.readParcelable(Icon::class.java.classLoader)
        type = source.readSerializable() as Contract.CategoryType?
        tag = source.readString()
    }

    override fun getId(): Long {
        return mId
    }

    val direction: Int
        get() {
            when (type!!) {
                Contract.CategoryType.INCOME ->
                    return 1
                Contract.CategoryType.EXPENSE ->
                    return 0
                Contract.CategoryType.SYSTEM ->
                    if (tag != null) {
                        when (tag) {
                            Contract.CategoryTag.CREDIT,
                            Contract.CategoryTag.PAID_DEBT,
                            Contract.CategoryTag.SAVING_DEPOSIT,
                            Contract.CategoryTag.TAX,
                            Contract.CategoryTag.TRANSFER_TAX,
                            Contract.CategoryTag.DEBT_INTEREST ->
                                return 0
                            Contract.CategoryTag.DEBT,
                            Contract.CategoryTag.PAID_CREDIT,
                            Contract.CategoryTag.SAVING_WITHDRAW,
                            Contract.CategoryTag.CREDIT_INTEREST ->
                                return 1
                        }
                    }
            }
            return 0
        }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(mId)
        dest.writeString(name)
        dest.writeParcelable(icon, flags)
        dest.writeSerializable(type)
        dest.writeString(tag)
    }

    companion object {

        @JvmField
        val CREATOR: Parcelable.Creator<Category> = object : Parcelable.Creator<Category> {
            override fun createFromParcel(source: Parcel): Category {
                return Category(source)
            }

            override fun newArray(size: Int): Array<Category?> {
                return arrayOfNulls(size)
            }
        }
    }
}
