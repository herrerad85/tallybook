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

package com.oriondev.moneywallet.api

/**
 * Created by andrea on 21/11/18.
 */
open class BackendException : Exception {

    private val mIsRecoverable: Boolean

    constructor(message: String?) : this(message, null)

    constructor(message: String?, cause: Throwable?) : this(message, cause, false)

    constructor(message: String?, isRecoverable: Boolean) : this(message, null, isRecoverable)

    constructor(message: String?, cause: Throwable?, isRecoverable: Boolean) : super(message, cause) {
        mIsRecoverable = isRecoverable
    }

    fun isRecoverable(): Boolean {
        return mIsRecoverable
    }
}
