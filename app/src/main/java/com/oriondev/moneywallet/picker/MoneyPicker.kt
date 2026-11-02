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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.ui.activity.CalculatorActivity
import com.oriondev.moneywallet.utils.CurrencyManager

/**
 * Created by andrea on 02/02/18.
 */
class MoneyPicker : Fragment() {

    companion object {

        private const val SS_CURRENT_CURRENCY = "MoneyPicker::SavedState::CurrentCurrency"
        private const val SS_CURRENT_MONEY = "MoneyPicker::SavedState::CurrentMoney"
        private const val ARG_DEFAULT_CURRENCY = "MoneyPicker::Arguments::DefaultCurrency"
        private const val ARG_DEFAULT_MONEY = "MoneyPicker::Arguments::DefaultMoney"
        private const val ARG_ALLOW_NEGATIVE = "MoneyPicker::Arguments::AllowNegative"

        private const val REQUEST_MONEY_PICKER = 4546

        /**
         * Creates a picker whose keypad will not confirm a negative amount. A transaction takes its
         * direction from the category it is filed under, so a typed minus is a second direction that
         * contradicts it, and a wallet total then moves the wrong way. Elsewhere the amount is a
         * magnitude with no direction to contradict: a budget target and a debt amount are the ceiling
         * a progress bar is drawn against, where a negative is a target nothing can reach.
         */
        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?, currency: CurrencyUnit?, money: Long): MoneyPicker {
            return createPicker(fragmentManager, tag, currency, money, false)
        }

        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?, currency: CurrencyUnit?, money: Long, allowNegative: Boolean): MoneyPicker {
            var moneyPicker = fragmentManager.findFragmentByTag(tag) as MoneyPicker?
            if (moneyPicker == null) {
                val arguments = Bundle()
                arguments.putParcelable(ARG_DEFAULT_CURRENCY, currency)
                arguments.putLong(ARG_DEFAULT_MONEY, money)
                arguments.putBoolean(ARG_ALLOW_NEGATIVE, allowNegative)
                moneyPicker = MoneyPicker()
                moneyPicker.arguments = arguments
                fragmentManager.beginTransaction().add(moneyPicker, tag).commit()
            } else if (moneyPicker.arguments != null) {
                // A picker found by tag keeps the arguments it was created with, so one that was saved
                // by a build older than this flag comes back without it. The screen asks for the same
                // picker every time it is created, so write the current answer in.
                moneyPicker.arguments?.putBoolean(ARG_ALLOW_NEGATIVE, allowNegative)
            }
            return moneyPicker
        }
    }

    private var mController: Controller? = null

    private var mCurrentCurrency: CurrencyUnit? = null
    private var mCurrentMoney: Long = 0L

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Controller) {
            mController = context
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            mCurrentCurrency = savedInstanceState.getParcelable(SS_CURRENT_CURRENCY)
            mCurrentMoney = savedInstanceState.getLong(SS_CURRENT_MONEY)
        } else {
            val arguments: Bundle? = arguments
            if (arguments != null) {
                mCurrentCurrency = arguments.getParcelable(ARG_DEFAULT_CURRENCY)
                mCurrentMoney = arguments.getLong(ARG_DEFAULT_MONEY)
            } else {
                mCurrentCurrency = CurrencyManager.getDefaultCurrency()
                mCurrentMoney = 0L
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    private fun fireCallbackSafely() {
        mController?.onMoneyChanged(tag, mCurrentCurrency, mCurrentMoney)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SS_CURRENT_CURRENCY, mCurrentCurrency)
        outState.putLong(SS_CURRENT_MONEY, mCurrentMoney)
    }

    fun setCurrency(currency: CurrencyUnit?) {
        mCurrentCurrency = currency
        fireCallbackSafely()
    }

    fun setMoney(money: Long) {
        mCurrentMoney = money
        fireCallbackSafely()
    }

    fun setCurrencyAndMoney(currency: CurrencyUnit?, money: Long) {
        mCurrentCurrency = currency
        mCurrentMoney = money
        fireCallbackSafely()
    }

    fun getCurrentCurrency(): CurrencyUnit? {
        return mCurrentCurrency
    }

    fun getCurrentMoney(): Long {
        return mCurrentMoney
    }

    fun showPicker() {
        // TODO let the user choice in preferences if start the calculator or the bottom sheet keypad
        val intent = Intent(activity, CalculatorActivity::class.java)
        intent.putExtra(CalculatorActivity.ACTIVITY_MODE, CalculatorActivity.MODE_KEYPAD)
        intent.putExtra(CalculatorActivity.CURRENCY, mCurrentCurrency)
        intent.putExtra(CalculatorActivity.MONEY, mCurrentMoney)
        // Read at the point of use rather than kept in a field, so that the answer written in by
        // createPicker above reaches the keypad even when the picker itself was already there.
        val arguments: Bundle? = arguments
        intent.putExtra(CalculatorActivity.ALLOW_NEGATIVE,
                arguments != null && arguments.getBoolean(ARG_ALLOW_NEGATIVE))
        startActivityForResult(intent, REQUEST_MONEY_PICKER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_MONEY_PICKER) {
            if (resultCode == Activity.RESULT_OK) {
                mCurrentMoney = data!!.getLongExtra(CalculatorActivity.MONEY, mCurrentMoney)
                fireCallbackSafely()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
    }

    interface Controller {

        fun onMoneyChanged(tag: String?, currency: CurrencyUnit?, money: Long)
    }
}
