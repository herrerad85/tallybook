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

import com.oriondev.moneywallet.model.Person
import com.oriondev.moneywallet.ui.fragment.dialog.PeoplePickerDialog

import java.util.Arrays

/**
 * Created by andrea on 10/03/18.
 */
class PersonPicker : Fragment(), PeoplePickerDialog.Callback {

    private var mController: Controller? = null

    private var mCurrentPeople: Array<Person?>? = null

    private var mPeoplePickerDialog: PeoplePickerDialog? = null

    companion object {

        private const val SS_CURRENT_PEOPLE = "PersonPicker::SavedState::CurrentPeople"

        private const val ARG_DEFAULT_PEOPLE = "PersonPicker::Arguments::DefaultPeople"

        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?, defaultPeople: Array<Person?>?): PersonPicker {
            var personPicker = fragmentManager.findFragmentByTag(tag) as PersonPicker?
            if (personPicker == null) {
                personPicker = PersonPicker()
                val arguments = Bundle()
                arguments.putParcelableArray(ARG_DEFAULT_PEOPLE, defaultPeople)
                personPicker.arguments = arguments
                fragmentManager.beginTransaction().add(personPicker, tag).commit()
            }
            return personPicker
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
            mCurrentPeople = savedInstanceState.getParcelableArray(SS_CURRENT_PEOPLE)?.let {
                Arrays.copyOf(it, it.size, Array<Person?>::class.java)
            }
        } else {
            val arguments: Bundle? = arguments
            if (arguments != null) {
                mCurrentPeople = arguments.getParcelableArray(ARG_DEFAULT_PEOPLE) as Array<Person?>?
            }
        }
        mPeoplePickerDialog = childFragmentManager.findFragmentByTag(getDialogTag()) as PeoplePickerDialog?
        if (mPeoplePickerDialog == null) {
            mPeoplePickerDialog = PeoplePickerDialog.newInstance()
        }
        mPeoplePickerDialog!!.setCallback(this)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    private fun getDialogTag(): String {
        return tag + "::DialogFragment"
    }

    private fun fireCallbackSafely() {
        mController?.onPeopleChanged(tag, mCurrentPeople)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArray(SS_CURRENT_PEOPLE, mCurrentPeople)
    }

    fun isSelected(): Boolean {
        val currentPeople = mCurrentPeople
        return currentPeople != null && currentPeople.size > 0
    }

    fun setPeople(people: Array<Person?>?) {
        mCurrentPeople = people
        fireCallbackSafely()
    }

    fun getCurrentPeople(): Array<Person?>? {
        return mCurrentPeople
    }

    fun showPicker() {
        mPeoplePickerDialog!!.showPicker(childFragmentManager, getDialogTag(), mCurrentPeople)
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
    }

    override fun onPeopleSelected(people: Array<Person?>?) {
        mCurrentPeople = people
        fireCallbackSafely()
    }

    interface Controller {

        fun onPeopleChanged(tag: String?, people: Array<Person?>?)
    }
}
