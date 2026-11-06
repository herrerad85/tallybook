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

package com.oriondev.moneywallet.picker

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.ExchangeRate
import com.oriondev.moneywallet.model.MoneyScale
import com.oriondev.moneywallet.ui.fragment.dialog.CurrencyConverterDialog
import com.oriondev.moneywallet.utils.CurrencyManager

/**
 * Created by andrea on 15/03/18.
 */
class CurrencyConverterPicker : Fragment(), CurrencyConverterDialog.Callback {

    private var mController: Controller? = null

    private var mCurrency1: CurrencyUnit? = null
    private var mCurrency2: CurrencyUnit? = null
    private var mConversionRate: Double? = null

    private var mCurrencyConverterDialog: CurrencyConverterDialog? = null

    companion object {

        private const val SS_CURRENCY_1 = "CurrencyConverterPicker::SavedState::Currency1"
        private const val SS_CURRENCY_2 = "CurrencyConverterPicker::SavedState::Currency2"
        private const val SS_RATE = "CurrencyConverterPicker::SavedState::ConversionRate"

        private const val ARG_DEFAULT_CURRENCY_1 = "CurrencyConverterPicker::Arguments::DefaultCurrency1"
        private const val ARG_DEFAULT_CURRENCY_2 = "CurrencyConverterPicker::Arguments::DefaultCurrency2"
        private const val ARG_DEFAULT_RATE = "CurrencyConverterPicker::Arguments::DefaultConversionRate"

        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?,
                         currencyUnit1: CurrencyUnit?,
                         currencyUnit2: CurrencyUnit?,
                         defaultRate: Double): CurrencyConverterPicker {
            var currencyConverterPicker = fragmentManager.findFragmentByTag(tag) as CurrencyConverterPicker?
            if (currencyConverterPicker == null) {
                currencyConverterPicker = CurrencyConverterPicker()
                val arguments = Bundle()
                arguments.putParcelable(ARG_DEFAULT_CURRENCY_1, currencyUnit1)
                arguments.putParcelable(ARG_DEFAULT_CURRENCY_2, currencyUnit2)
                arguments.putDouble(ARG_DEFAULT_RATE, defaultRate)
                currencyConverterPicker.arguments = arguments
                fragmentManager.beginTransaction().add(currencyConverterPicker, tag).commit()
            }
            return currencyConverterPicker
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Controller) {
            mController = context
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            mCurrency1 = savedInstanceState.getParcelable(SS_CURRENCY_1)
            mCurrency2 = savedInstanceState.getParcelable(SS_CURRENCY_2)
            mConversionRate = savedInstanceState.getDouble(SS_RATE)
        } else {
            val arguments: Bundle? = arguments
            if (arguments != null) {
                mCurrency1 = arguments.getParcelable(ARG_DEFAULT_CURRENCY_1)
                mCurrency2 = arguments.getParcelable(ARG_DEFAULT_CURRENCY_2)
                mConversionRate = arguments.getDouble(ARG_DEFAULT_RATE)
            } else {
                mCurrency1 = null
                mCurrency2 = null
                mConversionRate = null
            }
        }
        if (mConversionRate != null && mConversionRate == 0.0) {
            mConversionRate = null
        }
        mCurrencyConverterDialog = childFragmentManager.findFragmentByTag(getDialogTag()) as CurrencyConverterDialog?
        if (mCurrencyConverterDialog == null) {
            mCurrencyConverterDialog = CurrencyConverterDialog.newInstance()
        }
        mCurrencyConverterDialog!!.setCallback(this)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        loadConversionRate(false)
        fireCallbackSafely()
    }

    private fun loadConversionRate(isCurrencyChanged: Boolean) {
        if (mCurrency1 != null && mCurrency2 != null && (mConversionRate == null || isCurrencyChanged)) {
            if (mCurrency1 == mCurrency2) {
                mConversionRate = 1.0
            } else {
                val exchangeRate: ExchangeRate? = CurrencyManager.getExchangeRate(mCurrency1, mCurrency2)
                if (exchangeRate != null) {
                    mConversionRate = exchangeRate.getRate()
                } else {
                    mConversionRate = 0.0
                }
            }
        }
    }

    private fun fireCallbackSafely() {
        mController?.onConversionRateChanged(tag, mCurrency1, mCurrency2, mConversionRate)
    }

    private fun getDialogTag(): String {
        return tag + "::DialogFragment"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SS_CURRENCY_1, mCurrency1)
        outState.putParcelable(SS_CURRENCY_2, mCurrency2)
        val conversionRate = mConversionRate
        if (conversionRate != null) {
            outState.putDouble(SS_RATE, conversionRate)
        }
    }

    fun isReady(): Boolean {
        val conversionRate = mConversionRate
        return mCurrency1 != null && mCurrency2 != null && conversionRate != null && conversionRate.isFinite() && conversionRate > 0.0
    }

    fun setCurrency1(currencyUnit: CurrencyUnit?) {
        val isCurrencyChanged = mCurrency1 != null && mCurrency1 != currencyUnit
        mCurrency1 = currencyUnit
        loadConversionRate(isCurrencyChanged)
        fireCallbackSafely()
    }

    fun setCurrency2(currencyUnit: CurrencyUnit?) {
        val isCurrencyChanged = mCurrency2 != null && mCurrency2 != currencyUnit
        mCurrency2 = currencyUnit
        loadConversionRate(isCurrencyChanged)
        fireCallbackSafely()
    }

    fun showPicker(currentMoney: Long) {
        mCurrencyConverterDialog!!.showPicker(childFragmentManager, getDialogTag(), mCurrency1, mCurrency2, mConversionRate!!)
    }

    fun convert(money: Long): Long {
        return MoneyScale.convert(money, mCurrency1!!.decimals, mCurrency2!!.decimals, mConversionRate!!)
    }

    fun notifyMoneyChanged() {
        fireCallbackSafely()
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
    }

    override fun onExchangeRateChanged(exchangeRate: Double) {
        mConversionRate = exchangeRate
        fireCallbackSafely()
    }

    interface Controller {

        fun onConversionRateChanged(tag: String?, currencyUnit1: CurrencyUnit?, currencyUnit2: CurrencyUnit?, conversionRate: Double?)
    }
}
