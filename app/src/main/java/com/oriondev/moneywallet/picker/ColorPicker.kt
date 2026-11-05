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
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.fragment.dialog.ColorChooserDialog

/**
 * Created by andrea on 15/04/18.
 */
class ColorPicker : Fragment(), ColorChooserDialog.Callback {

    companion object {

        private const val SS_CURRENT_COLOR = "ColorPicker::SavedState::CurrentColor"
        private const val SS_ACCENT_PALETTE = "ColorPicker::SavedState::AccentPalette"
        private const val ARG_DEFAULT_COLOR = "ColorPicker::Arguments::DefaultColor"
        private const val ARG_ACCENT_PALETTE = "ColorPicker::Arguments::AccentPalette"

        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?, color: Int, accentPalette: Boolean, controller: Controller?): ColorPicker {
            var picker = fragmentManager.findFragmentByTag(tag) as ColorPicker?
            if (picker == null) {
                picker = ColorPicker()
                val arguments = Bundle()
                arguments.putInt(ARG_DEFAULT_COLOR, color)
                arguments.putBoolean(ARG_ACCENT_PALETTE, accentPalette)
                picker.arguments = arguments
                fragmentManager.beginTransaction().add(picker, tag).commit()
            }
            picker.setController(controller)
            return picker
        }
    }

    private var mController: Controller? = null

    private var mCurrentColor: Int = 0
    private var mAccentPalette: Boolean = false
    private var mPreviewed: Boolean = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Controller) {
            mController = context
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            mCurrentColor = savedInstanceState.getInt(SS_CURRENT_COLOR)
            mAccentPalette = savedInstanceState.getBoolean(SS_ACCENT_PALETTE)
        } else {
            val arguments: Bundle? = arguments
            if (arguments != null) {
                mCurrentColor = arguments.getInt(ARG_DEFAULT_COLOR)
                mAccentPalette = arguments.getBoolean(ARG_ACCENT_PALETTE)
            } else {
                mCurrentColor = Color.BLACK
                mAccentPalette = false
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely(true)
    }

    private fun fireCallbackSafely(autoFired: Boolean) {
        mController?.onColorChanged(tag, mCurrentColor, autoFired)
    }

    private fun getDialogTag(): String {
        return tag + "::DialogFragment"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(SS_CURRENT_COLOR, mCurrentColor)
        outState.putBoolean(SS_ACCENT_PALETTE, mAccentPalette)
    }

    fun setController(controller: Controller?) {
        mController = controller
    }

    fun showPicker() {
        val fragmentManager = childFragmentManager
        val tag = getDialogTag()
        // showNow adds the dialog before it returns, so a second tap in the same frame finds it.
        if (fragmentManager.findFragmentByTag(tag) == null) {
            ColorChooserDialog.newInstance(R.string.dialog_color_picker_title, mAccentPalette, mCurrentColor)
                    .showNow(fragmentManager, tag)
        }
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
    }

    override fun onColorSelection(dialog: ColorChooserDialog?, selectedColor: Int) {
        mCurrentColor = selectedColor
        fireCallbackSafely(false)
    }

    override fun onColorPreview(dialog: ColorChooserDialog?, color: Int): Boolean {
        val controller = mController
        mPreviewed = controller != null && controller.onColorPreview(tag, color)
        return mPreviewed
    }

    /** Only a picker that showed something ends anything, since ending it is not per picker. */
    override fun onColorChooserDismissed(dialog: ColorChooserDialog?) {
        val controller = mController
        if (mPreviewed && controller != null) {
            mPreviewed = false
            controller.onColorPreviewEnded()
        }
    }

    interface Controller {

        fun onColorChanged(tag: String?, color: Int, autoFired: Boolean)

        /** @return true if the color is now showing somewhere, so the chooser can drop its dim. */
        fun onColorPreview(tag: String?, color: Int): Boolean

        fun onColorPreviewEnded()
    }
}
