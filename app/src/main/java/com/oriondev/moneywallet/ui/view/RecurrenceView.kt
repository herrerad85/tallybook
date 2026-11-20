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

package com.oriondev.moneywallet.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.view.text.MaterialEditText
import com.oriondev.moneywallet.ui.view.theme.ThemedSpinner

import org.dmfs.rfc5545.recur.Freq
import org.dmfs.rfc5545.recur.RecurrenceRule

/**
 * Created by andrea on 07/11/18.
 */
class RecurrenceView : LinearLayout {

    private var mTypeSpinner: ThemedSpinner? = null
    private var mStartDateSpinner: MaterialEditText? = null

    constructor(context: Context) : super(context) {
        initialize(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        initialize(context)
    }

    private fun initialize(context: Context) {
        val view = inflate(context, R.layout.dialog_recurrence_setting_picker, this)
        mTypeSpinner = view.findViewById(R.id.type_spinner)
        mStartDateSpinner = view.findViewById(R.id.start_date_spinner)



        mTypeSpinner!!.adapter = ThemedSpinner.Adapter(context,
                "Repeat daily",
                "Repeat weekly",
                "Repeat monthly",
                "Repeat yearly"
        )
        mStartDateSpinner!!.setTextViewMode(true)
        mStartDateSpinner!!.setOnClickListener(object : OnClickListener {

            override fun onClick(v: View?) {
                // TODO: open picker
            }

        })
        mStartDateSpinner!!.setText(
                "From: Today"
        )
    }

    fun getRecurrenceRule(): RecurrenceRule {
        val recurrenceRule = RecurrenceRule(Freq.MONTHLY)
        // TODO: build the recurrence rule using user settings
        return recurrenceRule
    }
}