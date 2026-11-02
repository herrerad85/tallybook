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

import com.oriondev.moneywallet.model.RecurrenceSetting
import com.oriondev.moneywallet.ui.fragment.dialog.RecurrencePickerDialog

/**
 * Created by andrea on 07/11/18.
 */

class RecurrencePicker : Fragment(), RecurrencePickerDialog.Callback {

    companion object {

        private const val SS_RECURRENCE_SETTING = "RecurrencePicker::SavedState::RecurrenceSetting"
        private const val SS_CHOSEN = "RecurrencePicker::SavedState::Chosen"

        private const val ARG_RECURRENCE_SETTING = "RecurrencePicker::Argument::RecurrenceSetting"
        private const val ARG_END_TYPE_ENABLED = "RecurrencePicker::Argument::EndTypeEnabled"
        private const val ARG_ONCE_A_PERIOD = "RecurrencePicker::Argument::OnceAPeriod"

        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?, recurrenceSetting: RecurrenceSetting?): RecurrencePicker {
            return createPicker(fragmentManager, tag, recurrenceSetting, true, false)
        }

        /**
         * @param endTypeEnabled false to hide the control that stops the recurrence after a date or a
         *                       number of times, for a caller whose recurrence can only run forever.
         * @param onceAPeriod    true to hide the weekday multi select, for a caller that reads the
         *                       stretch between one occurrence and the next as a length and so needs
         *                       every stretch to be the same.
         */
        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?, recurrenceSetting: RecurrenceSetting?, endTypeEnabled: Boolean, onceAPeriod: Boolean): RecurrencePicker {
            var recurrencePicker = fragmentManager.findFragmentByTag(tag) as RecurrencePicker?
            if (recurrencePicker == null) {
                val arguments = Bundle()
                arguments.putParcelable(ARG_RECURRENCE_SETTING, recurrenceSetting)
                arguments.putBoolean(ARG_END_TYPE_ENABLED, endTypeEnabled)
                arguments.putBoolean(ARG_ONCE_A_PERIOD, onceAPeriod)
                recurrencePicker = RecurrencePicker()
                recurrencePicker.arguments = arguments
                fragmentManager.beginTransaction().add(recurrencePicker, tag).commit()
            }
            return recurrencePicker
        }
    }

    private var mController: Controller? = null

    private var mRecurrenceSetting: RecurrenceSetting? = null

    private var mChosen: Boolean = false

    private var mEndTypeEnabled: Boolean = false

    private var mOnceAPeriod: Boolean = false

    private var mRecurrencePickerDialog: RecurrencePickerDialog? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Controller) {
            mController = context
        } else if (parentFragment is Controller) {
            mController = parentFragment as Controller
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val arguments: Bundle? = arguments
        mEndTypeEnabled = arguments == null || arguments.getBoolean(ARG_END_TYPE_ENABLED, true)
        mOnceAPeriod = arguments != null && arguments.getBoolean(ARG_ONCE_A_PERIOD, false)
        if (savedInstanceState != null) {
            mRecurrenceSetting = savedInstanceState.getParcelable(SS_RECURRENCE_SETTING)
            mChosen = savedInstanceState.getBoolean(SS_CHOSEN, false)
        } else {
            if (arguments != null && arguments.containsKey(ARG_RECURRENCE_SETTING)) {
                mRecurrenceSetting = arguments.getParcelable(ARG_RECURRENCE_SETTING)
            } else {
                throw IllegalStateException("RecurrencePicker not initialized correctly. Please use RecurrencePicker.createPicker(...) instead.")
            }
        }
        mRecurrencePickerDialog = childFragmentManager.findFragmentByTag(getDialogTag()) as RecurrencePickerDialog?
        if (mRecurrencePickerDialog == null) {
            mRecurrencePickerDialog = RecurrencePickerDialog.newInstance()
        }
        mRecurrencePickerDialog!!.setCallback(this)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SS_RECURRENCE_SETTING, mRecurrenceSetting)
        outState.putBoolean(SS_CHOSEN, mChosen)
    }

    /**
     * Whether the schedule the picker holds was chosen in its dialog, as opposed to the one it
     * was created with. A caller that would otherwise anchor the schedule somewhere of its own
     * asks this first, so that a choice already made is not quietly undone.
     */
    fun isChosen(): Boolean {
        return mChosen
    }

    private fun fireCallbackSafely() {
        mController?.onRecurrenceSettingChanged(tag, mRecurrenceSetting)
    }

    fun getCurrentSettings(): RecurrenceSetting? {
        return mRecurrenceSetting
    }

    /**
     * Replace the schedule the picker holds, as if it had been chosen in the dialog.
     */
    fun setCurrentSettings(recurrenceSetting: RecurrenceSetting?) {
        // this is the caller putting a schedule in, not a person choosing one, so it leaves the
        // picker as unchosen: a caller that asks whether a schedule was chosen is asking about
        // the dialog, and answering yes here would stop it seeding the picker ever again
        mRecurrenceSetting = recurrenceSetting
        fireCallbackSafely()
    }

    private fun getDialogTag(): String {
        return tag + "::DialogFragment"
    }

    fun showPicker() {
        mRecurrencePickerDialog!!.showPicker(childFragmentManager, getDialogTag(), mRecurrenceSetting, mEndTypeEnabled, mOnceAPeriod)
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
    }

    override fun onRecurrenceSettingChanged(recurrenceSetting: RecurrenceSetting?) {
        mRecurrenceSetting = recurrenceSetting
        mChosen = true
        fireCallbackSafely()
    }

    interface Controller {

        fun onRecurrenceSettingChanged(tag: String?, recurrenceSetting: RecurrenceSetting?)
    }
}
