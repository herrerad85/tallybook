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
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import android.util.AttributeSet
import android.view.View

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog

/**
 * Created by andrea on 25/07/18.
 */
class ThemedListPreference : Preference {

    private var mEntries: Array<String>? = null
    private var mEntryValues: Array<String>? = null

    private var mCurrentValueIndex: Int = 0

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

    fun setEntries(entries: Array<String>?) {
        mEntries = entries
    }

    fun setEntries(array: Int) {
        mEntries = context.resources.getStringArray(array)
    }

    fun setEntryValues(entryValues: Array<String>?) {
        mEntryValues = entryValues
    }

    fun setValue(valueIndex: Int) {
        mCurrentValueIndex = valueIndex
    }

    fun getValue(): String? {
        return if (mCurrentValueIndex >= 0) mEntryValues!![mCurrentValueIndex] else null
    }

    fun setValue(value: String?) {
        mCurrentValueIndex = indexOf(value)
    }

    private fun indexOf(value: String?): Int {
        val entryValues = mEntryValues!!
        for (i in 0 until entryValues.size) {
            if (entryValues[i].equals(value)) {
                return i
            }
        }
        return -1
    }

    private fun showDialog() {
        val selected = intArrayOf(mCurrentValueIndex)
        ThemedDialog.buildMaterialDialog(context)
                .setTitle(title)
                .setSingleChoiceItems(mEntries, mCurrentValueIndex, object : DialogInterface.OnClickListener {

                    override fun onClick(dialog: DialogInterface?, which: Int) {
                        selected[0] = which
                    }

                })
                .setPositiveButton(android.R.string.ok, object : DialogInterface.OnClickListener {

                    override fun onClick(dialog: DialogInterface?, which: Int) {
                        if (selected[0] != mCurrentValueIndex) {
                            callChangeListener(mEntryValues!![selected[0]])
                            mCurrentValueIndex = selected[0]
                        }
                    }

                })
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    // see ThemedPreference.onBindViewHolder
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        ThemeEngine.applyTheme(holder.itemView, true)
    }
}
