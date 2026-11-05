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

import com.oriondev.moneywallet.model.Group
import com.oriondev.moneywallet.model.OverviewSetting
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.fragment.dialog.OverviewSettingDialog
import com.oriondev.moneywallet.utils.DateUtils

import java.util.Calendar
import java.util.Date

/**
 * Created by andrea on 17/08/18.
 */
class OverviewSettingPicker : Fragment(), OverviewSettingDialog.Callback {

    private var mController: Controller? = null

    private var mOverviewSetting: OverviewSetting? = null

    private var mOverviewSettingDialog: OverviewSettingDialog? = null

    companion object {

        private const val SS_OVERVIEW_SETTING = "OverviewSettingPicker::SavedState::OverviewSetting"

        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?): OverviewSettingPicker {
            var placePicker = fragmentManager.findFragmentByTag(tag) as OverviewSettingPicker?
            if (placePicker == null) {
                placePicker = OverviewSettingPicker()
                fragmentManager.beginTransaction().add(placePicker, tag).commit()
            }
            return placePicker
        }
    }

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
        if (savedInstanceState != null) {
            mOverviewSetting = savedInstanceState.getParcelable(SS_OVERVIEW_SETTING)
        } else {
            val startDate: Date?
            val endDate: Date?
            val calendar = Calendar.getInstance()
            calendar.setFirstDayOfWeek(Calendar.SUNDAY)
            val groupType = PreferenceManager.getCurrentGroupType()
            when (groupType!!) {
                Group.DAILY -> {
                    // we consider the current week
                    val firstDayOfWeek = PreferenceManager.getFirstDayOfWeek()
                    val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                    if (currentDayOfWeek < firstDayOfWeek) {
                        calendar.add(Calendar.DAY_OF_MONTH, -7)
                    }
                    calendar.set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    startDate = calendar.getTime()
                    endDate = DateUtils.addDays(calendar, 6)
                }
                Group.WEEKLY -> {
                    // we consider the current month
                    val firstDayOfMonth = PreferenceManager.getFirstDayOfMonth()
                    val currentDayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
                    if (currentDayOfMonth < firstDayOfMonth) {
                        calendar.add(Calendar.MONTH, -1)
                    }
                    calendar.set(Calendar.DAY_OF_MONTH, firstDayOfMonth)
                    startDate = calendar.getTime()
                    endDate = DateUtils.addMonthAndDay(calendar, 1, -1)
                }
                Group.MONTHLY -> {
                    // we consider the current year
                    val currentYear = calendar.get(Calendar.YEAR)
                    calendar.set(currentYear, Calendar.JANUARY, 1)
                    startDate = calendar.getTime()
                    calendar.set(currentYear, Calendar.DECEMBER, 31)
                    endDate = calendar.getTime()
                }
                Group.YEARLY -> {
                    // we consider the last three years
                    val currentYear = calendar.get(Calendar.YEAR)
                    calendar.set(currentYear - 3, Calendar.JANUARY, 1)
                    startDate = calendar.getTime()
                    calendar.set(currentYear, Calendar.DECEMBER, 31)
                    endDate = calendar.getTime()
                }
                else -> {
                    startDate = calendar.getTime()
                    endDate = calendar.getTime()
                }
            }
            mOverviewSetting = OverviewSetting(startDate, endDate, groupType, OverviewSetting.CashFlow.NET_INCOMES)
        }
        mOverviewSettingDialog = childFragmentManager.findFragmentByTag(getDialogTag()) as OverviewSettingDialog?
        if (mOverviewSettingDialog == null) {
            mOverviewSettingDialog = OverviewSettingDialog.newInstance()
        }
        mOverviewSettingDialog!!.setCallback(this)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SS_OVERVIEW_SETTING, mOverviewSetting)
    }

    private fun fireCallbackSafely() {
        mController?.onOverviewSettingChanged(tag, mOverviewSetting)
    }

    fun getCurrentSettings(): OverviewSetting? {
        return mOverviewSetting
    }

    private fun getDialogTag(): String {
        return tag + "::DialogFragment"
    }

    fun showPicker() {
        mOverviewSettingDialog!!.showPicker(childFragmentManager, getDialogTag(), mOverviewSetting)
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
    }

    override fun onOverviewSettingChanged(overviewSetting: OverviewSetting?) {
        mOverviewSetting = overviewSetting
        fireCallbackSafely()
    }

    interface Controller {

        fun onOverviewSettingChanged(tag: String?, overviewSetting: OverviewSetting?)
    }
}
