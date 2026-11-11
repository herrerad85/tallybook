/*
 * Copyright (c) 2026.
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

package com.oriondev.moneywallet.background

import android.content.Context
import android.database.Cursor
import android.database.CursorWrapper
import android.text.TextUtils

import androidx.annotation.VisibleForTesting
import androidx.loader.content.CursorLoader

import com.oriondev.moneywallet.model.SearchFilter
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.wrapper.CurrencyHeaderCursor
import com.oriondev.moneywallet.utils.CurrencyManager

/**
 * The transactions a [SearchFilter] matches, in its sort, with their count and totals. The
 * selection is built here and not by the caller, since an amount needs the currencies in use.
 */
class SearchCursorLoader : CursorLoader {

    /**
     * What the search strip shows, carried by the cursor this loader delivers.
     */
    interface Summary {

        fun getMatchCount(): Int

        /**
         * @return the currency every match shares, or null when nothing matches or the matches
         *         mix currencies.
         */
        fun getCurrency(): String?

        /**
         * @return the sum of the Out matches in minor units, or null when there is none or the
         *         matches mix currencies.
         */
        fun getOut(): Long?

        /**
         * @return the sum of the In matches in minor units, or null when there is none or the
         *         matches mix currencies.
         */
        fun getIn(): Long?
    }

    private val mFilter: SearchFilter

    constructor(context: Context, filter: SearchFilter) : super(context, DataContentProvider.CONTENT_TRANSACTIONS, null, null, null, null) {
        mFilter = filter.copy()
    }

    override fun loadInBackground(): Cursor? {
        // only then, since the wallets query totals every transaction
        val decimals: MutableMap<String, Int>? = if (mFilter.isAmountSet()) loadDecimals(context) else null
        val selection = mFilter.toSelection(decimals)
        // with a category ticked, a split transaction matches once and carries the money of its
        // parts in the ticked categories
        setUri(if (mFilter.getCategoryIds().isEmpty()) DataContentProvider.CONTENT_TRANSACTIONS
                else DataContentProvider.CONTENT_CATEGORY_SHARES)
        setSelection(selection.getL())
        setSelectionArgs(selection.getR())
        setSortOrder(mFilter.getSort()!!.getOrderBy())
        val cursor = super.loadInBackground()
        if (cursor == null) {
            return null
        }
        if (mFilter.getSort()!!.isByAmount() && mixesCurrencies(cursor)) {
            return CurrencyHeaderCursor(cursor)
        }
        return SummaryCursor(cursor)
    }

    companion object {

        /**
         * Read from the first and the last row alone, which holds for rows ordered by currency first,
         * as the amount sorts are, and leaves the one walk to the cursor built after it.
         */
        private fun mixesCurrencies(cursor: Cursor): Boolean {
            val indexCurrency = cursor.getColumnIndexOrThrow(Contract.Transaction.WALLET_CURRENCY)
            var mixed = false
            if (cursor.moveToFirst()) {
                val first = cursor.getString(indexCurrency)
                cursor.moveToLast()
                mixed = !TextUtils.equals(first, cursor.getString(indexCurrency))
            }
            cursor.moveToPosition(-1)
            return mixed
        }

        /**
         * The decimals of every wallet currency, archived wallets included, since the transactions
         * query keeps their rows.
         */
        @VisibleForTesting
        @JvmStatic
        fun loadDecimals(context: Context): MutableMap<String, Int> {
            val decimals: MutableMap<String, Int> = HashMap()
            val projection = arrayOf(Contract.Wallet.CURRENCY)
            val cursor: Cursor? = context.contentResolver.query(DataContentProvider.CONTENT_WALLETS, projection, null, null, null)
            if (cursor != null) {
                try {
                    while (cursor.moveToNext()) {
                        val iso = cursor.getString(0)
                        decimals.put(iso, CurrencyManager.getDecimals(CurrencyManager.getCurrency(iso)))
                    }
                } finally {
                    cursor.close()
                }
            }
            return decimals
        }
    }

    private class SummaryCursor : CursorWrapper, Summary {

        private val mMatchCount: Int
        private val mCurrency: String?
        private val mOut: Long?
        private val mIn: Long?

        constructor(cursor: Cursor) : super(cursor) {
            val indexCurrency = cursor.getColumnIndexOrThrow(Contract.Transaction.WALLET_CURRENCY)
            val indexDirection = cursor.getColumnIndexOrThrow(Contract.Transaction.DIRECTION)
            val indexMoney = cursor.getColumnIndexOrThrow(Contract.Transaction.MONEY)
            var currency: String? = null
            var mixed = false
            var out = 0L
            var `in` = 0L
            var hasOut = false
            var hasIn = false
            while (cursor.moveToNext()) {
                val rowCurrency = cursor.getString(indexCurrency)
                if (cursor.isFirst) {
                    currency = rowCurrency
                } else if (!TextUtils.equals(currency, rowCurrency)) {
                    mixed = true
                    break
                }
                if (cursor.getInt(indexDirection) == Contract.Direction.INCOME) {
                    `in` += cursor.getLong(indexMoney)
                    hasIn = true
                } else {
                    out += cursor.getLong(indexMoney)
                    hasOut = true
                }
            }
            cursor.moveToPosition(-1)
            val single = currency != null && !mixed
            mMatchCount = cursor.count
            mCurrency = if (single) currency else null
            mOut = if (single && hasOut) out else null
            mIn = if (single && hasIn) `in` else null
        }

        override fun getMatchCount(): Int {
            return mMatchCount
        }

        override fun getCurrency(): String? {
            return mCurrency
        }

        override fun getOut(): Long? {
            return mOut
        }

        override fun getIn(): Long? {
            return mIn
        }
    }
}
