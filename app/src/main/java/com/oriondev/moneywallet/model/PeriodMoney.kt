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

import com.oriondev.moneywallet.utils.DateUtils

import java.util.Date

/**
 * Created by andrea on 14/08/18.
 */
class PeriodMoney {

    private val mStartDate: Date?
    private val mEndDate: Date?
    private val mIncomes: Money
    private val mExpenses: Money
    private val mNetIncomes: Money
    private val mTransfers: Money

    constructor(startDate: Date?, endDate: Date?) {
        mStartDate = startDate
        mEndDate = endDate
        mIncomes = Money()
        mExpenses = Money()
        mNetIncomes = Money()
        mTransfers = Money()
    }

    fun addIncome(currency: String?, money: Long) {
        mIncomes.addMoney(currency, money)
        mNetIncomes.addMoney(currency, money)
    }

    fun addExpense(currency: String?, money: Long) {
        mExpenses.addMoney(currency, money)
        mNetIncomes.removeMoney(currency, money)
    }

    /**
     * Count money moved between two of the user's own wallets. It reaches the net, because a
     * wallet that sent money holds less of it afterwards, and it reaches neither of the other
     * two, because nothing was earned or spent.
     *
     * It is also kept on its own, signed, because the net is the only figure on the summary
     * screen that counts it. The two bar series do not, so a period that only moved money draws
     * both bars at nothing over a net that moved, and this is the figure that says why.
     */
    fun addTransfer(currency: String?, money: Long, incoming: Boolean) {
        if (incoming) {
            mNetIncomes.addMoney(currency, money)
            mTransfers.addMoney(currency, money)
        } else {
            mNetIncomes.removeMoney(currency, money)
            mTransfers.removeMoney(currency, money)
        }
    }

    fun getStartDate(): Date? {
        return mStartDate
    }

    fun getEndDate(): Date? {
        return mEndDate
    }

    fun getIncomes(): Money {
        return mIncomes
    }

    fun getExpenses(): Money {
        return mExpenses
    }

    fun getNetIncomes(): Money {
        return mNetIncomes
    }

    fun getTransfers(): Money {
        return mTransfers
    }

    fun isTimeRange(): Boolean {
        return DateUtils.isTheSameDayAndHour(mStartDate, mEndDate)
    }
}
