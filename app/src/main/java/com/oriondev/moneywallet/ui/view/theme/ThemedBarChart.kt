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

package com.oriondev.moneywallet.ui.view.theme

import android.content.Context
import android.util.AttributeSet

import com.github.mikephil.charting.charts.BarChart

/**
 * Created by andrea on 21/08/18.
 */
class ThemedBarChart : BarChart, ThemeEngine.ThemeConsumer {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    override fun onApplyTheme(theme: ITheme?) {
        val textColor = theme!!.textColorSecondary
        val iconColor = theme.iconColor
        // style xAxis
        val xAxis = getXAxis()
        xAxis.axisLineColor = iconColor
        xAxis.gridColor = iconColor
        xAxis.textColor = textColor
        // style yAxis left
        val yLeftAxis = getAxisLeft()
        yLeftAxis.zeroLineColor = iconColor
        yLeftAxis.axisLineColor = iconColor
        yLeftAxis.gridColor = iconColor
        yLeftAxis.textColor = textColor
        // style yAxis right
        val yRightAxis = getAxisRight()
        yRightAxis.zeroLineColor = iconColor
        yRightAxis.axisLineColor = iconColor
        yRightAxis.gridColor = iconColor
        yRightAxis.textColor = textColor
        // style legend
        val legend = getLegend()
        legend.textColor = textColor
        // style description
        val description = getDescription()
        description.textColor = textColor
        // style the chart itself
        setNoDataTextColor(textColor)
    }
}