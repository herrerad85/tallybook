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

/**
 * Created by andrea on 03/03/18.
 */
enum class Group(private val mValue: Int) {

    DAILY(0), WEEKLY(1), MONTHLY(2), YEARLY(3);

    fun getType(): Int {
        return mValue
    }

    companion object {

        @JvmStatic
        fun fromType(type: Int): Group {
            when (type) {
                0 ->
                    return DAILY
                1 ->
                    return WEEKLY
                2 ->
                    return MONTHLY
                3 ->
                    return YEARLY
            }
            throw IllegalArgumentException("Group type not recognized: " + type.toString())
        }
    }
}
