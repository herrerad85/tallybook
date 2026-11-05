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

import com.oriondev.moneywallet.ui.view.chart.PieData

/**
 * Created by andrea on 13/08/18.
 */
class PeriodDetailFlowData {

    private val mTotalMoney: Money
    // private final List<PieData> mPieDataList;
    private val mPieDataList: MutableList<PieData>
    private val mCategoryMoneyList: MutableList<CategoryMoney>

    constructor(totalMoney: Money, pieDataList: MutableList<PieData>, categoryMoneyList: MutableList<CategoryMoney>) {
        mTotalMoney = totalMoney
        mPieDataList = pieDataList
        mCategoryMoneyList = categoryMoneyList
    }

    fun getTotalMoney(): Money {
        return mTotalMoney
    }

    fun getChartData(index: Int): PieData {
        return mPieDataList.get(index)
    }

    fun getChartCount(): Int {
        return mPieDataList.size
    }

    fun getCategory(index: Int): CategoryMoney {
        return mCategoryMoneyList.get(index)
    }

    fun getCategoryCount(): Int {
        return mCategoryMoneyList.size
    }
}
