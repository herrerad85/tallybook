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

package com.oriondev.moneywallet.ui.widget

import android.content.Context
import android.content.SharedPreferences

/**
 * What one placed widget was configured with. The same widget can be placed once per wallet, so
 * every value here is keyed by the id the launcher handed out for that placement.
 *
 * Its own preference file instead of the app's, because these entries are owned by the launcher's
 * copy of the widget and are deleted with it. Mixing them into the settings the user edits would
 * leave the ids of removed widgets sitting in a file nothing else ever cleans.
 */
object WalletWidgetPreferences {

    const val NO_WALLET = -1L

    private const val FILE = "wallet_widget"
    private const val KEY_WALLET = "wallet::"
    private const val KEY_SHOW_WHEN_LOCKED = "show_when_locked::"

    private fun preferences(context: Context?): SharedPreferences {
        return context!!.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    @JvmStatic
    fun save(context: Context?, appWidgetId: Int, walletId: Long, showWhenLocked: Boolean) {
        preferences(context).edit()
                .putLong(KEY_WALLET + appWidgetId, walletId)
                .putBoolean(KEY_SHOW_WHEN_LOCKED + appWidgetId, showWhenLocked)
                .apply()
    }

    @JvmStatic
    fun getWallet(context: Context?, appWidgetId: Int): Long {
        return preferences(context).getLong(KEY_WALLET + appWidgetId, NO_WALLET)
    }

    @JvmStatic
    fun isShowWhenLocked(context: Context?, appWidgetId: Int): Boolean {
        return preferences(context).getBoolean(KEY_SHOW_WHEN_LOCKED + appWidgetId, false)
    }

    /**
     * Called from onDeleted and from onRestored. Without it the file grows by two entries for
     * every widget the user ever places and removes, and nothing else would ever know those ids
     * are dead.
     */
    @JvmStatic
    fun delete(context: Context?, appWidgetIds: IntArray?) {
        val editor = preferences(context).edit()
        for (appWidgetId in appWidgetIds!!) {
            editor.remove(KEY_WALLET + appWidgetId)
            editor.remove(KEY_SHOW_WHEN_LOCKED + appWidgetId)
        }
        editor.apply()
    }

    /**
     * Forget which wallet every placement was pointed at, keeping the rest of what they hold.
     *
     * For a restored backup. A wallet is stored here by its row id, and a restore inserts every
     * wallet fresh, so the ids come back meaning other wallets. A widget left holding the old
     * number would show a wallet the user never chose and, worse, its button would file new
     * transactions into it. DataContentProvider already clears the current wallet for the same
     * reason on the same path.
     */
    @JvmStatic
    internal fun clearWallets(context: Context?) {
        val preferences = preferences(context)
        val editor = preferences.edit()
        for (key in preferences.all.keys) {
            if (key.startsWith(KEY_WALLET)) {
                editor.remove(key)
            }
        }
        editor.apply()
    }
}
