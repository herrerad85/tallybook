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

package com.oriondev.moneywallet.ui.view.text

import android.content.Context
import androidx.annotation.StringRes

/**
 * Created by andrea on 30/01/18.
 */

class NonEmptyTextValidator : Validator {

    private val mMessage: String
    private val mAuto: Boolean

    constructor(message: String) : this(message, false)

    constructor(message: String, auto: Boolean) {
        mMessage = message
        mAuto = auto
    }

    constructor(context: Context, @StringRes resId: Int) : this(context, resId, false)

    constructor(context: Context, @StringRes resId: Int, auto: Boolean) {
        mMessage = context.getString(resId)
        mAuto = auto
    }

    override fun getErrorMessage(): String {
        return mMessage
    }

    override fun isValid(charSequence: CharSequence): Boolean {
        return !charSequence.toString().trim { it <= ' ' }.isEmpty()
    }

    override fun autoValidate(): Boolean {
        return mAuto
    }
}
