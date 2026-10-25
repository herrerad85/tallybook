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

package com.oriondev.moneywallet.utils

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.widget.TextView

import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Money
import com.oriondev.moneywallet.model.MoneyScale
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat

/**
 * Created by andrea on 23/01/18.
 */
class MoneyFormatter {

    private var mCurrencyEnabled: Boolean
    private var mGroupDigitEnabled: Boolean
    private var mRoundDecimalsEnabled: Boolean
    private var mShowSymbolEnabled: Boolean
    private var mDivider: String = DIVIDER_TEXT
    private var mFormatter: NumberFormat

    enum class CurrencyMode {
        ALWAYS_SHOWN,
        ALWAYS_HIDDEN,
        USER_PREFERENCE
    }

    enum class TintMode {
        AUTO_DETECT,
        INCOME,
        EXPENSE
    }

    enum class FlowMode {
        FORCE_POSITIVE,
        FORCE_NEGATIVE,
        AUTO_DETECT
    }

    companion object {

        private const val DEFAULT_MIN_INTEGER_DIGITS = 1
        private const val DEFAULT_MIN_FRACTION_DIGITS_STANDARD = 2
        private const val DEFAULT_MAX_FRACTION_DIGITS_STANDARD = 2
        private const val DEFAULT_MIN_FRACTION_DIGITS_ROUNDING = 0
        private const val DEFAULT_MAX_FRACTION_DIGITS_ROUNDING = 0
        private const val DEFAULT_DIVIDER_POW = 2

        private const val EMPTY_TEXT = " --- "
        private const val DIVIDER_TEXT = " - "

        @JvmStatic
        fun getInstance(): MoneyFormatter {
            return MoneyFormatter()
        }

        @JvmStatic
        fun normalize(money: Long, decimals: Int, finalDecimals: Int): Long {
            val offset = finalDecimals - decimals
            return normalize(money, offset)
        }

        @JvmStatic
        fun normalize(money: Long, decimalOffset: Int): Long {
            return MoneyScale.rescale(money, decimalOffset)
        }
    }

    private constructor() {
        mCurrencyEnabled = PreferenceManager.isCurrencyEnabled()
        mGroupDigitEnabled = PreferenceManager.isGroupDigitEnabled()
        mRoundDecimalsEnabled = PreferenceManager.isRoundDecimalsEnabled()
        mShowSymbolEnabled = PreferenceManager.isShowPlusMinusSymbolEnabled()
        mFormatter = DecimalFormat.getInstance()
        mFormatter.minimumIntegerDigits = DEFAULT_MIN_INTEGER_DIGITS
    }

    private fun colorIn(): Int {
        return ThemeEngine.getTheme().getVisibleColor(PreferenceManager.getCurrentIncomeColor(),
                PreferenceManager.getDefaultColorIncome())
    }

    private fun colorOut(): Int {
        return ThemeEngine.getTheme().getVisibleColor(PreferenceManager.getCurrentExpenseColor(),
                PreferenceManager.getDefaultColorExpense())
    }

    fun setCurrencyEnabled(enabled: Boolean) {
        mCurrencyEnabled = enabled
    }

    fun setGroupDigitEnabled(enabled: Boolean) {
        mGroupDigitEnabled = enabled
    }

    fun setRoundDecimalsEnabled(enabled: Boolean) {
        mRoundDecimalsEnabled = enabled
    }

    fun setShowSymbolEnabled(enabled: Boolean) {
        mShowSymbolEnabled = enabled
    }

    /**
     * What goes between the currencies of an amount that holds more than one.
     */
    fun setDivider(divider: String) {
        mDivider = divider
    }

    fun isCurrencyEnabled(): Boolean {
        return mCurrencyEnabled
    }

    fun isGroupDigitEnabled(): Boolean {
        return mGroupDigitEnabled
    }

    fun isRoundDecimalsEnabled(): Boolean {
        return mRoundDecimalsEnabled
    }

    fun isShowSymbolEnabled(): Boolean {
        return mShowSymbolEnabled
    }

    fun getNotTintedString(money: Money?): String {
        val builder = StringBuilder()
        val numberOfCurrencies = money!!.numberOfCurrencies
        if (numberOfCurrencies == 0) {
            builder.append(EMPTY_TEXT)
        } else {
            val currencyMode = if (money.numberOfCurrencies > 1) CurrencyMode.ALWAYS_SHOWN else CurrencyMode.USER_PREFERENCE
            for (entry in money.currencyMoneys.entries) {
                if (builder.length > 0) {
                    builder.append(mDivider)
                }
                val currencyUnit = CurrencyManager.getCurrency(entry.key)
                val currencyMoney: Long = entry.value
                builder.append(getNotTintedString(currencyUnit, currencyMoney, currencyMode, FlowMode.AUTO_DETECT))
            }
        }
        return builder.toString()
    }

    fun getNotTintedString(currencyUnit: CurrencyUnit?, money: Long): String {
        return getNotTintedString(currencyUnit, money, CurrencyMode.USER_PREFERENCE, FlowMode.AUTO_DETECT)
    }

    fun getNotTintedString(currencyUnit: CurrencyUnit?, money: Long, currencyMode: CurrencyMode?): String {
        return getNotTintedString(currencyUnit, money, currencyMode, FlowMode.AUTO_DETECT)
    }

    private fun getNotTintedString(currencyUnit: CurrencyUnit?, money: Long, currencyMode: CurrencyMode?, flowMode: FlowMode): String {
        val builder = StringBuilder()
        val divider: Double
        if (currencyUnit != null) {
            mFormatter.minimumFractionDigits = currencyUnit.decimals
            mFormatter.maximumFractionDigits = currencyUnit.decimals
            divider = Math.pow(10.0, currencyUnit.decimals.toDouble())
            if (currencyMode === CurrencyMode.ALWAYS_SHOWN || (currencyMode === CurrencyMode.USER_PREFERENCE && mCurrencyEnabled)) {
                builder.append(currencyUnit.symbol)
                builder.append(" ")
            }
        } else {
            mFormatter.minimumFractionDigits = DEFAULT_MIN_FRACTION_DIGITS_STANDARD
            mFormatter.maximumFractionDigits = DEFAULT_MAX_FRACTION_DIGITS_STANDARD
            divider = Math.pow(10.0, DEFAULT_DIVIDER_POW.toDouble())
        }
        mFormatter.isGroupingUsed = mGroupDigitEnabled
        if (mRoundDecimalsEnabled) {
            mFormatter.minimumFractionDigits = DEFAULT_MIN_FRACTION_DIGITS_ROUNDING
            mFormatter.maximumFractionDigits = DEFAULT_MAX_FRACTION_DIGITS_ROUNDING
            mFormatter.roundingMode = RoundingMode.HALF_UP
        }
        if (flowMode === FlowMode.FORCE_NEGATIVE || (flowMode === FlowMode.AUTO_DETECT && money < 0L)) {
            builder.append("-")
        } else if (mShowSymbolEnabled) {
            builder.append("+")
        }
        builder.append(mFormatter.format(Math.abs(money).toDouble() / divider))
        return builder.toString()
    }

    fun getTintedString(money: Money?): SpannableStringBuilder {
        return getTintedString(money, TintMode.AUTO_DETECT)
    }

    fun getTintedString(money: Money?, tintMode: TintMode): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        val numberOfCurrencies = money!!.numberOfCurrencies
        if (numberOfCurrencies == 0) {
            val emptyString = SpannableString(EMPTY_TEXT)
            emptyString.setSpan(ForegroundColorSpan(ThemeEngine.getTheme().textColorPrimary), 0, emptyString.length, 0)
            builder.append(emptyString)
        } else {
            val currencyMode = if (money.numberOfCurrencies > 1) CurrencyMode.ALWAYS_SHOWN else CurrencyMode.USER_PREFERENCE
            for (entry in money.currencyMoneys.entries) {
                if (builder.length > 0) {
                    val divider = SpannableString(mDivider)
                    divider.setSpan(ForegroundColorSpan(ThemeEngine.getTheme().textColorPrimary), 0, divider.length, 0)
                    builder.append(divider)
                }
                val currencyUnit = CurrencyManager.getCurrency(entry.key)
                val currencyMoney: Long = entry.value
                builder.append(getTintedString(currencyUnit, currencyMoney, currencyMode, tintMode))
            }
        }
        return builder
    }

    fun getTintedString(currencyUnit: CurrencyUnit?, money: Long): SpannableString {
        return getTintedString(currencyUnit, money, CurrencyMode.USER_PREFERENCE, TintMode.AUTO_DETECT)
    }

    fun getTintedString(currencyUnit: CurrencyUnit?, money: Long, currencyMode: CurrencyMode?): SpannableString {
        return getTintedString(currencyUnit, money, currencyMode, TintMode.AUTO_DETECT)
    }

    private fun getTintedString(currencyUnit: CurrencyUnit?, money: Long, currencyMode: CurrencyMode?, tintMode: TintMode): SpannableString {
        val flowMode = if (mShowSymbolEnabled) getFlowModeFromTintMode(tintMode) else FlowMode.FORCE_POSITIVE
        val notTinted = getNotTintedString(currencyUnit, money, currencyMode, flowMode)
        val spannableString = SpannableString(notTinted)
        if (tintMode === TintMode.AUTO_DETECT) {
            spannableString.setSpan(ForegroundColorSpan(if (money < 0L) colorOut() else colorIn()), 0, notTinted.length, 0)
        } else if (tintMode === TintMode.INCOME) {
            spannableString.setSpan(ForegroundColorSpan(colorIn()), 0, notTinted.length, 0)
        } else if (tintMode === TintMode.EXPENSE) {
            spannableString.setSpan(ForegroundColorSpan(colorOut()), 0, notTinted.length, 0)
        }
        return spannableString
    }

    private fun getFlowModeFromTintMode(tintMode: TintMode): FlowMode {
        when (tintMode) {
            TintMode.INCOME ->
                return FlowMode.FORCE_POSITIVE
            TintMode.EXPENSE ->
                return FlowMode.FORCE_NEGATIVE
            else -> {}
        }
        return FlowMode.AUTO_DETECT
    }

    fun applyNotTinted(textView: TextView?, money: Money?) {
        val text = getNotTintedString(money)
        textView!!.text = text
    }

    fun applyNotTinted(textView: TextView?, currencyUnit: CurrencyUnit?, money: Long) {
        val text = getNotTintedString(currencyUnit, money, CurrencyMode.USER_PREFERENCE, FlowMode.AUTO_DETECT)
        textView!!.text = text
    }

    fun applyTinted(textView: TextView?, money: Money?) {
        val text = getTintedString(money)
        textView!!.setText(text, TextView.BufferType.SPANNABLE)
    }

    fun applyTinted(textView: TextView?, currencyUnit: CurrencyUnit?, money: Long) {
        val text = getTintedString(currencyUnit, money, CurrencyMode.USER_PREFERENCE, TintMode.AUTO_DETECT)
        textView!!.setText(text, TextView.BufferType.SPANNABLE)
    }

    fun applyTintedIncome(textView: TextView?, money: Money?) {
        val text = getTintedString(money, TintMode.INCOME)
        textView!!.setText(text, TextView.BufferType.SPANNABLE)
    }

    fun applyTintedIncome(textView: TextView?, currencyUnit: CurrencyUnit?, money: Long) {
        val text = getTintedString(currencyUnit, money, CurrencyMode.USER_PREFERENCE, TintMode.INCOME)
        textView!!.setText(text, TextView.BufferType.SPANNABLE)
    }

    fun applyTintedExpense(textView: TextView?, money: Money?) {
        val text = getTintedString(money, TintMode.EXPENSE)
        textView!!.setText(text, TextView.BufferType.SPANNABLE)
    }

    fun applyTintedExpense(textView: TextView?, currencyUnit: CurrencyUnit?, money: Long) {
        val text = getTintedString(currencyUnit, money, CurrencyMode.USER_PREFERENCE, TintMode.EXPENSE)
        textView!!.setText(text, TextView.BufferType.SPANNABLE)
    }
}
