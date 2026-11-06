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
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.ColorIcon
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.model.VectorIcon
import com.oriondev.moneywallet.ui.activity.IconListActivity
import com.oriondev.moneywallet.ui.fragment.dialog.ColorChooserDialog
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.Utils

/**
 * Created by andrea on 01/02/18.
 */
class IconPicker : Fragment(), ColorChooserDialog.Callback {

    companion object {

        private const val SS_CURRENT_ICON = "IconPicker::SavedState::CurrentIcon"
        private const val SS_LAST_BG_COLOR = "IconPicker::SavedState::LastBackgroundColor"
        private const val ARG_DEFAULT_ICON = "IconPicker::Arguments::DefaultIcon"

        private const val REQUEST_ICON_PICKER = 57

        private const val DEFAULT_BACKGROUND_COLOR = "#000000"

        private const val TAG_COLOR_CHOOSER = "IconPicker::Tag::ColorChooserDialog"

        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?): IconPicker {
            return createPicker(fragmentManager, tag, ColorIcon(DEFAULT_BACKGROUND_COLOR, "?"))
        }

        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?, defaultIcon: Icon?): IconPicker {
            var iconPicker = fragmentManager.findFragmentByTag(tag) as IconPicker?
            if (iconPicker == null) {
                val arguments = Bundle()
                arguments.putParcelable(ARG_DEFAULT_ICON, defaultIcon)
                iconPicker = IconPicker()
                iconPicker.arguments = arguments
                fragmentManager.beginTransaction().add(iconPicker, tag).commit()
            }
            return iconPicker
        }
    }

    private var mController: Controller? = null

    private var mCurrentIcon: Icon? = null
    private var mLastBackgroundColor: String? = null

    private var mBindEditText: EditText? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Controller) {
            mController = context
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            mCurrentIcon = savedInstanceState.getParcelable(SS_CURRENT_ICON)
            mLastBackgroundColor = savedInstanceState.getString(SS_LAST_BG_COLOR)
        } else {
            val arguments: Bundle? = arguments
            if (arguments != null) {
                mCurrentIcon = arguments.getParcelable(ARG_DEFAULT_ICON)
                if (mCurrentIcon != null) {
                    if (mCurrentIcon is ColorIcon) {
                        mLastBackgroundColor = Utils.getHexColor((mCurrentIcon as ColorIcon).getColor())
                    } else {
                        mLastBackgroundColor = DEFAULT_BACKGROUND_COLOR
                    }
                } else {
                    mCurrentIcon = ColorIcon(DEFAULT_BACKGROUND_COLOR, "?")
                    mLastBackgroundColor = DEFAULT_BACKGROUND_COLOR
                }
            } else {
                mCurrentIcon = ColorIcon(DEFAULT_BACKGROUND_COLOR, "?")
                mLastBackgroundColor = DEFAULT_BACKGROUND_COLOR
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    private fun fireCallbackSafely() {
        mController?.onIconChanged(tag, mCurrentIcon)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SS_CURRENT_ICON, mCurrentIcon)
        outState.putString(SS_LAST_BG_COLOR, mLastBackgroundColor)
    }

    fun listenOn(editText: EditText?) {
        mBindEditText = editText
        mBindEditText!!.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(charSequence: CharSequence?, i: Int, i1: Int, i2: Int) {

            }

            override fun onTextChanged(sequence: CharSequence?, i: Int, i1: Int, i2: Int) {
                if (mCurrentIcon is ColorIcon) {
                    val text = IconLoader.getColorIconString(sequence!!.toString())
                    mCurrentIcon = ColorIcon(mCurrentIcon as ColorIcon, text)
                    fireCallbackSafely()
                }
            }

            override fun afterTextChanged(editable: Editable?) {

            }

        })
    }

    fun getCurrentIcon(): Icon? {
        return mCurrentIcon
    }

    fun setCurrentIcon(icon: Icon?) {
        mCurrentIcon = icon
        fireCallbackSafely()
    }

    /**
     * Opens the icon list. Its menu offers to change the background color of a [ColorIcon]
     * or to remove a [VectorIcon].
     */
    fun showPicker() {
        val activity: Activity? = activity
        if (activity != null) {
            val intent = Intent(activity, IconListActivity::class.java)
            val currentIcon = mCurrentIcon
            if (currentIcon != null) {
                intent.putExtra(IconListActivity.CURRENT_ICON_TYPE, currentIcon.getType())
            }
            startActivityForResult(intent, REQUEST_ICON_PICKER)
        }
    }

    private fun openColorPicker() {
        if (!isAdded) {
            return
        }
        val fragmentManager = childFragmentManager
        // showNow adds the dialog before it returns, so a second tap in the same frame finds it.
        if (fragmentManager.findFragmentByTag(TAG_COLOR_CHOOSER) == null) {
            ColorChooserDialog.newInstance(R.string.dialog_color_picker_title_icon_picker_background_color,
                            true, Color.parseColor(mLastBackgroundColor))
                    .showNow(fragmentManager, TAG_COLOR_CHOOSER)
        }
    }

    private fun restoreColorIcon() {
        if (mCurrentIcon !is ColorIcon) {
            val bindEditText = mBindEditText
            var text = if (bindEditText != null) bindEditText.text.toString() else "?"
            text = if (text.isEmpty()) "?" else text
            mCurrentIcon = ColorIcon(mLastBackgroundColor, text.substring(0, 1))
            fireCallbackSafely()
        }
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
    }

    override fun onColorSelection(dialog: ColorChooserDialog?, selectedColor: Int) {
        mLastBackgroundColor = Utils.getHexColor(selectedColor)
        if (mCurrentIcon is ColorIcon) {
            val bindEditText = mBindEditText
            val text = IconLoader.getColorIconString(if (bindEditText != null) bindEditText.text.toString() else null)
            mCurrentIcon = ColorIcon(mLastBackgroundColor, text)
            fireCallbackSafely()
        }
    }

    override fun onColorChooserDismissed(dialog: ColorChooserDialog?) {
        // do nothing here: if the user dismiss the picker, it means that he don't want to change
        // the color at the moment.
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        if (requestCode == REQUEST_ICON_PICKER) {
            if (resultCode == Activity.RESULT_OK && intent != null) {
                val action: String? = intent.getStringExtra(IconListActivity.RESULT_ACTION)
                if (IconListActivity.ACTION_CHANGE_BG_COLOR == action) {
                    // The dialog lives here because it calls back the fragment that hosts it.
                    openColorPicker()
                } else if (IconListActivity.ACTION_REMOVE_ICON == action) {
                    restoreColorIcon()
                } else {
                    mCurrentIcon = intent.getParcelableExtra(IconListActivity.RESULT_ICON)
                    fireCallbackSafely()
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, intent)
        }
    }

    interface Controller {

        fun onIconChanged(tag: String?, icon: Icon?)
    }
}
