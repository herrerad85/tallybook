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

import com.oriondev.moneywallet.model.Place
import com.oriondev.moneywallet.ui.fragment.dialog.PlacePickerDialog

/**
 * Created by andrea on 10/03/18.
 */

class PlacePicker : Fragment(), PlacePickerDialog.Callback {

    private var mController: Controller? = null

    private var mCurrentPlace: Place? = null

    private var mPlacePickerDialog: PlacePickerDialog? = null

    companion object {

        private const val SS_CURRENT_PLACE = "PlacePicker::SavedState::CurrentPlace"

        private const val ARG_DEFAULT_PLACE = "PlacePicker::Arguments::DefaultPlace"

        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?, defaultPlace: Place?): PlacePicker {
            var placePicker = fragmentManager.findFragmentByTag(tag) as PlacePicker?
            if (placePicker == null) {
                placePicker = PlacePicker()
                val arguments = Bundle()
                arguments.putParcelable(ARG_DEFAULT_PLACE, defaultPlace)
                placePicker.arguments = arguments
                fragmentManager.beginTransaction().add(placePicker, tag).commit()
            }
            return placePicker
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
            mCurrentPlace = savedInstanceState.getParcelable(SS_CURRENT_PLACE)
        } else {
            val arguments: Bundle? = arguments
            if (arguments != null) {
                mCurrentPlace = arguments.getParcelable(ARG_DEFAULT_PLACE)
            }
        }
        mPlacePickerDialog = childFragmentManager.findFragmentByTag(getDialogTag()) as PlacePickerDialog?
        if (mPlacePickerDialog == null) {
            mPlacePickerDialog = PlacePickerDialog.newInstance()
        }
        mPlacePickerDialog!!.setCallback(this)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    private fun fireCallbackSafely() {
        mController?.onPlaceChanged(tag, mCurrentPlace)
    }

    private fun getDialogTag(): String {
        return tag + "::DialogFragment"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SS_CURRENT_PLACE, mCurrentPlace)
    }

    fun isSelected(): Boolean {
        return mCurrentPlace != null
    }

    fun setCurrentPlace(place: Place?) {
        mCurrentPlace = place
        fireCallbackSafely()
    }

    fun getCurrentPlace(): Place? {
        return mCurrentPlace
    }

    fun showPicker() {
        mPlacePickerDialog!!.showPicker(childFragmentManager, getDialogTag(), mCurrentPlace)
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
    }

    override fun onPlaceSelected(place: Place?) {
        mCurrentPlace = place
        fireCallbackSafely()
    }

    interface Controller {

        fun onPlaceChanged(tag: String?, place: Place?)
    }
}
