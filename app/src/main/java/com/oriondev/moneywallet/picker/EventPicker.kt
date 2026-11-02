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

import com.oriondev.moneywallet.model.Event
import com.oriondev.moneywallet.ui.fragment.dialog.EventPickerDialog

import java.util.Date

/**
 * Created by andrea on 10/03/18.
 */
class EventPicker : Fragment(), EventPickerDialog.Callback {

    private var mController: Controller? = null

    private var mCurrentEvent: Event? = null

    private var mEventPickerDialog: EventPickerDialog? = null

    companion object {

        private const val SS_CURRENT_EVENT = "EventPicker::SavedState::CurrentEvent"

        private const val ARG_DEFAULT_EVENT = "EventPicker::Arguments::DefaultEvent"

        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?, defaultEvent: Event?): EventPicker {
            var eventPicker = fragmentManager.findFragmentByTag(tag) as EventPicker?
            if (eventPicker == null) {
                eventPicker = EventPicker()
                val arguments = Bundle()
                arguments.putParcelable(ARG_DEFAULT_EVENT, defaultEvent)
                eventPicker.arguments = arguments
                fragmentManager.beginTransaction().add(eventPicker, tag).commit()
            }
            return eventPicker
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
            mCurrentEvent = savedInstanceState.getParcelable(SS_CURRENT_EVENT)
        } else {
            val arguments: Bundle? = arguments
            if (arguments != null) {
                mCurrentEvent = arguments.getParcelable(ARG_DEFAULT_EVENT)
            }
        }
        mEventPickerDialog = childFragmentManager.findFragmentByTag(getDialogTag()) as EventPickerDialog?
        if (mEventPickerDialog == null) {
            mEventPickerDialog = EventPickerDialog.newInstance()
        }
        mEventPickerDialog!!.setCallback(this)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    private fun fireCallbackSafely() {
        mController?.onEventChanged(tag, mCurrentEvent)
    }

    private fun getDialogTag(): String {
        return tag + "::DialogFragment"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SS_CURRENT_EVENT, mCurrentEvent)
    }

    fun isSelected(): Boolean {
        return mCurrentEvent != null
    }

    fun setCurrentEvent(event: Event?) {
        mCurrentEvent = event
        fireCallbackSafely()
    }

    fun getCurrentEvent(): Event? {
        return mCurrentEvent
    }

    fun showPicker(filterDate: Date?) {
        mEventPickerDialog!!.showPicker(childFragmentManager, getDialogTag(), mCurrentEvent, filterDate)
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
    }

    override fun onEventSelected(event: Event?) {
        mCurrentEvent = event
        fireCallbackSafely()
    }

    interface Controller {

        fun onEventChanged(tag: String?, event: Event?)
    }
}
