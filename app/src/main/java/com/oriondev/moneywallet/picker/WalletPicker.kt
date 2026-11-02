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

import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.ui.fragment.dialog.WalletPickerDialog

import java.util.Arrays

/**
 * Created by andrea on 09/03/18.
 */
class WalletPicker : Fragment(), WalletPickerDialog.Callback {

    companion object {

        private const val SS_SINGLE_WALLET = "WalletPicker::SavedState::SingleWallet"
        private const val SS_CURRENT_WALLET = "WalletPicker::SavedState::CurrentWallet"

        private const val ARG_SINGLE_WALLET = "WalletPicker::Arguments::SingleWallet"
        private const val ARG_DEFAULT_WALLET = "WalletPicker::Arguments::DefaultWallet"

        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?, defaultWallet: Wallet?): WalletPicker {
            var walletPicker = fragmentManager.findFragmentByTag(tag) as WalletPicker?
            if (walletPicker == null) {
                walletPicker = WalletPicker()
                val arguments = Bundle()
                arguments.putBoolean(ARG_SINGLE_WALLET, true)
                arguments.putParcelable(ARG_DEFAULT_WALLET, defaultWallet)
                walletPicker.arguments = arguments
                fragmentManager.beginTransaction().add(walletPicker, tag).commit()
            }
            return walletPicker
        }

        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?, defaultWallets: Array<Wallet?>?): WalletPicker {
            var walletPicker = fragmentManager.findFragmentByTag(tag) as WalletPicker?
            if (walletPicker == null) {
                walletPicker = WalletPicker()
                val arguments = Bundle()
                arguments.putBoolean(ARG_SINGLE_WALLET, false)
                arguments.putParcelableArray(ARG_DEFAULT_WALLET, defaultWallets)
                walletPicker.arguments = arguments
                fragmentManager.beginTransaction().add(walletPicker, tag).commit()
            }
            return walletPicker
        }
    }

    private var mSingleWalletController: SingleWalletController? = null
    private var mMultiWalletController: MultiWalletController? = null

    private var mSingleWallet: Boolean = false
    private var mCurrentWallet: Wallet? = null
    private var mCurrentWallets: Array<Wallet?>? = null

    private var mWalletPickerDialog: WalletPickerDialog? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is SingleWalletController) {
            mSingleWalletController = context
        } else if (parentFragment is SingleWalletController) {
            mSingleWalletController = parentFragment as SingleWalletController
        }
        if (context is MultiWalletController) {
            mMultiWalletController = context
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            mSingleWallet = savedInstanceState.getBoolean(SS_SINGLE_WALLET)
            if (mSingleWallet) {
                mCurrentWallet = savedInstanceState.getParcelable(SS_CURRENT_WALLET)
            } else {
                mCurrentWallets = savedInstanceState.getParcelableArray(SS_CURRENT_WALLET)?.let {
                    Arrays.copyOf(it, it.size, Array<Wallet?>::class.java)
                }
            }
        } else {
            val arguments: Bundle? = arguments
            if (arguments != null) {
                mSingleWallet = arguments.getBoolean(ARG_SINGLE_WALLET)
                if (mSingleWallet) {
                    mCurrentWallet = arguments.getParcelable(ARG_DEFAULT_WALLET)
                } else {
                    mCurrentWallets = arguments.getParcelableArray(ARG_DEFAULT_WALLET) as Array<Wallet?>?
                }
            } else {
                mSingleWallet = true
                mCurrentWallets = null
            }
        }
        mWalletPickerDialog = childFragmentManager.findFragmentByTag(getDialogTag()) as WalletPickerDialog?
        if (mWalletPickerDialog == null) {
            mWalletPickerDialog = WalletPickerDialog.newInstance()
        }
        mWalletPickerDialog!!.setCallback(this)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    private fun getDialogTag(): String {
        return tag + "::DialogFragment"
    }

    private fun fireCallbackSafely() {
        if (mSingleWallet) {
            mSingleWalletController?.onWalletChanged(tag, mCurrentWallet)
        } else {
            mMultiWalletController?.onWalletListChanged(tag, mCurrentWallets)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SS_SINGLE_WALLET, mSingleWallet)
        if (mSingleWallet) {
            outState.putParcelable(SS_CURRENT_WALLET, mCurrentWallet)
        } else {
            outState.putParcelableArray(SS_CURRENT_WALLET, mCurrentWallets)
        }
    }

    fun isSelected(): Boolean {
        val currentWallets = mCurrentWallets
        return if (mSingleWallet) mCurrentWallet != null else (currentWallets != null && currentWallets.size > 0)
    }

    fun getCurrentWallet(): Wallet? {
        return mCurrentWallet
    }

    fun getCurrentWallets(): Array<Wallet?>? {
        return mCurrentWallets
    }

    fun showSingleWalletPicker() {
        showSingleWalletPicker(null)
    }

    fun showSingleWalletPicker(currencyIso: String?) {
        mWalletPickerDialog!!.showSinglePicker(childFragmentManager, getDialogTag(), mCurrentWallet, currencyIso)
    }

    fun showMultiWalletPicker() {
        mWalletPickerDialog!!.showMultiPicker(childFragmentManager, getDialogTag(), mCurrentWallets)
    }

    override fun onDetach() {
        super.onDetach()
        mSingleWalletController = null
        mMultiWalletController = null
    }

    override fun onWalletSelected(wallet: Wallet?) {
        mCurrentWallet = wallet
        fireCallbackSafely()
    }

    override fun onWalletsSelected(wallets: Array<Wallet?>?) {
        mCurrentWallets = wallets
        fireCallbackSafely()
    }

    interface SingleWalletController {

        fun onWalletChanged(tag: String?, wallet: Wallet?)
    }

    interface MultiWalletController {

        fun onWalletListChanged(tag: String?, wallets: Array<Wallet?>?)
    }
}
