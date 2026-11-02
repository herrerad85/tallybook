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

import android.text.TextUtils

import java.util.HashMap
import java.util.Locale
import java.util.regex.Pattern

/**
 * Created by andrea on 23/01/18.
 */
class Money {

    private var mCurrencies: MutableMap<String?, Long>

    constructor() {
        mCurrencies = HashMap()
    }

    constructor(currency: String?, money: Long) {
        mCurrencies = HashMap()
        mCurrencies.put(currency, money)
    }

    fun addMoney(currency: String?, money: Long) {
        var total = money
        if (mCurrencies.containsKey(currency)) {
            total += mCurrencies.get(currency)!!
        }
        mCurrencies.put(currency, total)
    }

    fun addMoney(money: Money?) {
        if (money != null) {
            for (currency in money.mCurrencies.entries) {
                addMoney(currency.key, currency.value)
            }
        }
    }

    fun removeMoney(currency: String?, money: Long) {
        var total = -money
        if (mCurrencies.containsKey(currency)) {
            total += mCurrencies.get(currency)!!
        }
        mCurrencies.put(currency, total)
    }

    fun removeMoney(money: Money?) {
        if (money != null) {
            for (currency in money.mCurrencies.entries) {
                removeMoney(currency.key, currency.value)
            }
        }
    }

    fun getMoney(currency: String?): Long {
        val money: Long? = mCurrencies.get(currency)
        return if (money != null) money else 0
    }

    val currencyMoneys: MutableMap<String?, Long>
        get() = mCurrencies

    val currencies: MutableSet<String?>
        get() = mCurrencies.keys

    val numberOfCurrencies: Int
        get() = mCurrencies.size

    override fun toString(): String {
        val builder = StringBuilder()
        for (entry in mCurrencies.entries) {
            if (builder.length > 0) {
                builder.append(",")
            }
            builder.append(String.format(Locale.ENGLISH, "%s %d", entry.key, entry.value))
        }
        return builder.toString()
    }

    companion object {

        @JvmStatic
        fun empty(): Money {
            return Money()
        }

        @JvmStatic
        fun parse(string: String?): Money {
            val money = Money()
            if (!TextUtils.isEmpty(string)) {
                val currencies = Pattern.compile(",").split(string)
                for (currency in currencies) {
                    val parts = Pattern.compile(" ").split(currency)
                    money.addMoney(parts[0], parts[1].toLong())
                }
            }
            return money
        }
    }
}
