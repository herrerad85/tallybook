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
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.RadarData

/**
 * Created by andrea on 17/08/18.
 */
class OverviewData {

    private val mBarData: BarData?
    private val mLineData: LineData?
    private val mRadarData: RadarData?
    private val mPeriodMoneyList: MutableList<PeriodMoney>

    constructor(barData: BarData?, lineData: LineData?, radarData: RadarData?, periodMoneyList: MutableList<PeriodMoney>) {
        mBarData = barData
        mLineData = lineData
        mRadarData = radarData
        mPeriodMoneyList = periodMoneyList
    }

    fun getBarData(): BarData? {
        return mBarData
    }

    fun getLineData(): LineData? {
        return mLineData
    }

    fun getRadarData(): RadarData? {
        return mRadarData
    }

    fun getPeriodMoney(index: Int): PeriodMoney {
        return mPeriodMoneyList.get(index)
    }

    fun getPeriodCount(): Int {
        return mPeriodMoneyList.size
    }
}
