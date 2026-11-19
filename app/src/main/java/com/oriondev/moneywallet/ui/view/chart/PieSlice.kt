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

package com.oriondev.moneywallet.ui.view.chart

import android.graphics.drawable.Drawable

/**
 * Created by andrea on 13/08/18.
 */
class PieSlice {

    private val mName: String?
    private var mValue: Long
    private val mIcon: Drawable?
    private var mIsExtra = false

    private var mStartAngle = 0f
    private var mSweepAngle = 0f
    private var mIconAngle = 0f

    constructor(name: String?, value: Long, icon: Drawable?) : this(name, value, icon, false)

    constructor(name: String?, value: Long, icon: Drawable?, isExtra: Boolean) {
        mName = name
        mValue = value
        mIcon = icon
        mIsExtra = isExtra
    }

    fun setIconAngle(angle: Float) {
        mIconAngle = angle
    }

    fun increaseIconAngle(degrees: Float) {
        mIconAngle += degrees
    }

    fun decreaseIconAngle(degrees: Float) {
        mIconAngle -= degrees
    }

    fun setValue(value: Long) {
        mValue = value
    }

    fun getValue(): Long {
        return mValue
    }

    fun getName(): String? {
        return mName
    }

    fun getIcon(): Drawable? {
        return mIcon
    }

    fun getIconAngle(): Float {
        return mIconAngle
    }

    fun isExtra(): Boolean {
        return mIsExtra
    }

    fun getStartAngle(): Float {
        return mStartAngle
    }

    fun setStartAngle(startAngle: Float) {
        mStartAngle = startAngle
    }

    fun getSweepAngle(): Float {
        return mSweepAngle
    }

    fun setSweepAngle(sweepAngle: Float) {
        mSweepAngle = sweepAngle
    }
}
