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

import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.ui.fragment.dialog.BudgetTypePickerDialog

/**
 * Created by andrea on 12/03/18.
 */
class BudgetTypePicker : Fragment(), BudgetTypePickerDialog.Callback {

    private var mController: Controller? = null

    private var mCurrentType: Contract.BudgetType? = null

    private var mBudgetTypePickerDialog: BudgetTypePickerDialog? = null

    companion object {

        private const val SS_CURRENT_TYPE = "BudgetTypePicker::SavedState::CurrentType"

        private const val ARG_DEFAULT_TYPE = "BudgetTypePicker::Arguments::DefaultType"

        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?, defaultType: Contract.BudgetType?): BudgetTypePicker {
            var budgetTypePicker = fragmentManager.findFragmentByTag(tag) as BudgetTypePicker?
            if (budgetTypePicker == null) {
                budgetTypePicker = BudgetTypePicker()
                val arguments = Bundle()
                arguments.putSerializable(ARG_DEFAULT_TYPE, defaultType)
                budgetTypePicker.arguments = arguments
                fragmentManager.beginTransaction().add(budgetTypePicker, tag).commit()
            }
            return budgetTypePicker
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
            mCurrentType = savedInstanceState.getSerializable(SS_CURRENT_TYPE) as Contract.BudgetType?
        } else {
            val arguments: Bundle? = arguments
            if (arguments != null) {
                mCurrentType = arguments.getSerializable(ARG_DEFAULT_TYPE) as Contract.BudgetType?
            } else {
                mCurrentType = Contract.BudgetType.EXPENSES
            }
        }
        mBudgetTypePickerDialog = childFragmentManager.findFragmentByTag(getDialogTag()) as BudgetTypePickerDialog?
        if (mBudgetTypePickerDialog == null) {
            mBudgetTypePickerDialog = BudgetTypePickerDialog.newInstance()
        }
        mBudgetTypePickerDialog!!.setCallback(this)
    }

    private fun getDialogTag(): String {
        return tag + "::DialogFragment"
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    private fun fireCallbackSafely() {
        mController?.onTypeChanged(tag, mCurrentType)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(SS_CURRENT_TYPE, mCurrentType)
    }

    fun isSelected(): Boolean {
        return mCurrentType != null
    }

    fun getCurrentType(): Contract.BudgetType? {
        return mCurrentType
    }

    fun showPicker() {
        mBudgetTypePickerDialog!!.showPicker(childFragmentManager, getDialogTag(), mCurrentType)
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
    }

    override fun onBudgetTypeSelected(budgetType: Contract.BudgetType?) {
        mCurrentType = budgetType
        fireCallbackSafely()
    }

    interface Controller {

        fun onTypeChanged(tag: String?, type: Contract.BudgetType?)
    }
}
