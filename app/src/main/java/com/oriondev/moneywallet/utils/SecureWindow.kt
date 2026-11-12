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

package com.oriondev.moneywallet.utils

import android.view.Window
import android.view.WindowManager

import com.oriondev.moneywallet.model.LockMode
import com.oriondev.moneywallet.storage.preference.PreferenceManager

object SecureWindow {

    @JvmStatic
    fun update(window: Window) {
        val locked = PreferenceManager.getCurrentLockMode() != LockMode.NONE
        val secure = (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        if (locked == secure) {
            return
        }
        if (locked) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
