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

package com.oriondev.moneywallet.ui.preference

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import android.text.TextUtils
import android.util.AttributeSet

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog

/**
 * Created by andrea on 13/11/18.
 */

class ThemedInputPreference : Preference {

    private var mContent: String? = null

    private var mHint: String? = null
    private var mAllowEmptyInput: Boolean = false
    private var mInputType: Int = 0

    private var mValue: String? = null

    constructor(context: Context) : super(context) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize()
    }

    private fun initialize() {
        layoutResource = R.layout.layout_preference_material_design
        onPreferenceClickListener = object : Preference.OnPreferenceClickListener {

            override fun onPreferenceClick(preference: Preference): Boolean {
                showDialog()
                return false
            }

        }
    }

    fun setContent(content: String?) {
        mContent = content
    }

    fun setContent(content: Int) {
        mContent = context.getString(content)
    }

    fun setInput(hint: String?, allowEmptyInput: Boolean, inputType: Int) {
        mHint = hint
        mAllowEmptyInput = allowEmptyInput
        mInputType = inputType
    }

    fun setCurrentValue(value: String?) {
        mValue = value
    }

    fun setInput(hint: Int, allowEmptyInput: Boolean, inputType: Int) {
        mHint = context.getString(hint)
        mAllowEmptyInput = allowEmptyInput
        mInputType = inputType
    }

    private fun showDialog() {
        val inputView = LayoutInflater.from(context).inflate(R.layout.dialog_input, null)
        val inputEditText = inputView.findViewById<EditText>(R.id.dialog_input_edit_text)
        inputEditText.hint = mHint
        inputEditText.setText(mValue)
        inputEditText.inputType = mInputType
        val dialog = ThemedDialog.buildMaterialDialog(context)
                .setTitle(title)
                .setMessage(mContent)
                .setView(inputView)
                .setPositiveButton(android.R.string.ok, object : DialogInterface.OnClickListener {

                    override fun onClick(dialog: DialogInterface?, which: Int) {
                        val input = inputEditText.text
                        if (!TextUtils.equals(input, mValue)) {
                            mValue = input.toString()
                            callChangeListener(mValue)
                        }
                    }

                })
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        ThemedDialog.showWithInput(dialog, inputEditText, mAllowEmptyInput)
    }

    // see ThemedPreference.onBindViewHolder
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        ThemeEngine.applyTheme(holder.itemView, true)
    }
}
