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

package com.oriondev.moneywallet.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * Created by andre on 21/03/2018.
 */
class BackupService {

    private val mIdentifier: String?
    private val mIconRes: Int
    private val mNameRes: Int

    constructor(identifier: String?, icon: Int, name: Int) {
        mIdentifier = identifier
        mIconRes = icon
        mNameRes = name
    }

    fun getIdentifier(): String? {
        return mIdentifier
    }

    @DrawableRes
    fun getIconRes(): Int {
        return mIconRes
    }

    @StringRes
    fun getNameRes(): Int {
        return mNameRes
    }
}
