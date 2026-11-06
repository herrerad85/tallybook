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

import com.oriondev.moneywallet.utils.DateUtils

import java.util.Date

/**
 * Created by andrea on 17/08/18.
 */
class OverviewSetting : Parcelable {

    private val mStartDate: Date?
    private val mEndDate: Date?
    private val mGroupType: Group?
    private val mType: Type?
    private val mCashFlow: CashFlow?
    private val mCategoryId: Long

    constructor(startDate: Date?, endDate: Date?, groupType: Group?, cashFlow: CashFlow?) {
        mStartDate = DateUtils.setTime(startDate, 0, 0, 0, 0)
        mEndDate = DateUtils.setTime(endDate, 23, 59, 59, 999)
        mGroupType = groupType
        mType = Type.CASH_FLOW
        mCashFlow = cashFlow
        mCategoryId = 0L
    }

    constructor(startDate: Date?, endDate: Date?, groupType: Group?, categoryId: Long) {
        mStartDate = DateUtils.setTime(startDate, 0, 0, 0, 0)
        mEndDate = DateUtils.setTime(endDate, 23, 59, 59, 999)
        mGroupType = groupType
        mType = Type.CATEGORY
        mCashFlow = CashFlow.NET_INCOMES
        mCategoryId = categoryId
    }

    private constructor(source: Parcel) {
        mStartDate = source.readSerializable() as Date?
        mEndDate = source.readSerializable() as Date?
        mGroupType = source.readSerializable() as Group?
        mType = source.readSerializable() as Type?
        mCashFlow = source.readSerializable() as CashFlow?
        mCategoryId = source.readLong()
    }

    fun getStartDate(): Date? {
        return mStartDate
    }

    fun getEndDate(): Date? {
        return mEndDate
    }

    fun getGroupType(): Group? {
        return mGroupType
    }

    fun getType(): Type? {
        return mType
    }

    fun getCashFlow(): CashFlow? {
        return mCashFlow
    }

    fun getCategoryId(): Long {
        return mCategoryId
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeSerializable(mStartDate)
        dest.writeSerializable(mEndDate)
        dest.writeSerializable(mGroupType)
        dest.writeSerializable(mType)
        dest.writeSerializable(mCashFlow)
        dest.writeLong(mCategoryId)
    }

    enum class Type {
        CASH_FLOW,
        CATEGORY
    }

    enum class CashFlow {
        INCOMES,
        EXPENSES,
        NET_INCOMES
    }

    companion object {

        @JvmField
        val CREATOR: Parcelable.Creator<OverviewSetting> = object : Parcelable.Creator<OverviewSetting> {

            override fun createFromParcel(source: Parcel): OverviewSetting {
                return OverviewSetting(source)
            }

            override fun newArray(size: Int): Array<OverviewSetting?> {
                return arrayOfNulls(size)
            }

        }
    }
}
