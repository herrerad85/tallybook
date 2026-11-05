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

import com.github.mikephil.charting.data.BarData

/**
 * Created by andrea on 14/08/18.
 */
class PeriodDetailSummaryData {

    private val mNetIncomes: Money
    private val mBarDataList: MutableList<BarData>
    private val mBarDataCurrencies: MutableList<CurrencyUnit?>
    private val mPeriodList: MutableList<PeriodMoney>

    constructor(netIncomes: Money, barDataList: MutableList<BarData>, currencyUnitList: MutableList<CurrencyUnit?>, periodMoneyList: MutableList<PeriodMoney>) {
        mNetIncomes = netIncomes
        mBarDataList = barDataList
        mBarDataCurrencies = currencyUnitList
        mPeriodList = periodMoneyList
    }

    fun getNetIncomes(): Money {
        return mNetIncomes
    }

    fun getChartData(index: Int): BarData {
        return mBarDataList.get(index)
    }

    fun getChartCurrency(index: Int): CurrencyUnit? {
        return mBarDataCurrencies.get(index)
    }

    fun getChartCount(): Int {
        return mBarDataList.size
    }

    fun getPeriodMoney(index: Int): PeriodMoney {
        return mPeriodList.get(index)
    }

    fun getPeriodCount(): Int {
        return mPeriodList.size
    }
}
