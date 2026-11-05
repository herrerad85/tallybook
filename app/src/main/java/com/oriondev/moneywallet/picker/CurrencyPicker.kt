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
import com.oriondev.moneywallet.ui.activity.CurrencyListActivity
import com.oriondev.moneywallet.utils.CurrencyManager

/**
 * Created by andrea on 02/02/18.
 */
class CurrencyPicker : Fragment() {

    companion object {

        private const val SS_CURRENT_CURRENCY = "CurrencyPicker::SavedState::CurrentCurrency"
        private const val ARG_DEFAULT_CURRENCY = "CurrencyPicker::Arguments::DefaultCurrency"

        private const val REQUEST_CURRENCY_PICKER = 56

        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?): CurrencyPicker {
            return createPicker(fragmentManager, tag, CurrencyManager.getDefaultCurrency())
        }

        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?, defaultCurrency: CurrencyUnit?): CurrencyPicker {
            var currencyPicker = fragmentManager.findFragmentByTag(tag) as CurrencyPicker?
            if (currencyPicker == null) {
                currencyPicker = CurrencyPicker()
                val arguments = Bundle()
                arguments.putParcelable(ARG_DEFAULT_CURRENCY, defaultCurrency)
                currencyPicker.arguments = arguments
                fragmentManager.beginTransaction().add(currencyPicker, tag).commit()
            }
            return currencyPicker
        }
    }

    private var mController: Controller? = null

    private var mCurrentCurrency: CurrencyUnit? = null

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
        } else {
            val arguments: Bundle? = arguments
            if (arguments != null) {
                mCurrentCurrency = arguments.getParcelable(ARG_DEFAULT_CURRENCY)
            } else {
                mCurrentCurrency = CurrencyManager.getDefaultCurrency()
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    private fun fireCallbackSafely() {
        mController?.onCurrencyChanged(tag, mCurrentCurrency)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SS_CURRENT_CURRENCY, mCurrentCurrency)
    }

    fun setCurrency(currency: CurrencyUnit?) {
        mCurrentCurrency = currency
        fireCallbackSafely()
    }

    fun isSelected(): Boolean {
        return mCurrentCurrency != null
    }

    fun getCurrentCurrency(): CurrencyUnit? {
        return mCurrentCurrency
    }

    fun showPicker() {
        val intent = Intent(activity, CurrencyListActivity::class.java)
        intent.putExtra(CurrencyListActivity.ACTIVITY_MODE, CurrencyListActivity.CURRENCY_PICKER)
        startActivityForResult(intent, REQUEST_CURRENCY_PICKER)
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        if (requestCode == REQUEST_CURRENCY_PICKER) {
            if (resultCode == Activity.RESULT_OK) {
                mCurrentCurrency = intent!!.getParcelableExtra(CurrencyListActivity.RESULT_CURRENCY)
                fireCallbackSafely()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, intent)
        }
    }

    interface Controller {

        fun onCurrencyChanged(tag: String?, currency: CurrencyUnit?)
    }
}
