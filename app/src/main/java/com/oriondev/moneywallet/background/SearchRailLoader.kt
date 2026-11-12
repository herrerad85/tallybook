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
import android.net.Uri

import com.oriondev.moneywallet.model.SearchFilter
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider

/**
 * What the search rail reads from the database: whether a transaction or a side of a transfer
 * links a person, whether there is more than one wallet or any transfer, whether any row is
 * unconfirmed, and the names the Category, People and Wallet chips show, in their editors' order.
 */
class SearchRailLoader : AbstractGenericLoader<SearchRailLoader.Result> {

    class Result {

        private val mCategories: MutableList<SearchCategoryLoader.Entry>
        private val mPeople: MutableMap<Long, String?>
        private val mWallets: MutableMap<Long, String?>
        private val mHasPersonLinks: Boolean
        private val mHasWalletChoice: Boolean
        private val mHasUnconfirmed: Boolean

        internal constructor(categories: MutableList<SearchCategoryLoader.Entry>, people: MutableMap<Long, String?>,
                             wallets: MutableMap<Long, String?>, hasPersonLinks: Boolean, hasWalletChoice: Boolean,
                             hasUnconfirmed: Boolean) {
            mCategories = categories
            mPeople = people
            mWallets = wallets
            mHasPersonLinks = hasPersonLinks
            mHasWalletChoice = hasWalletChoice
            mHasUnconfirmed = hasUnconfirmed
        }

        fun getCategories(): MutableList<SearchCategoryLoader.Entry> {
            return mCategories
        }

        fun getPeople(): MutableMap<Long, String?> {
            return mPeople
        }

        fun getWallets(): MutableMap<Long, String?> {
            return mWallets
        }

        fun hasPersonLinks(): Boolean {
            return mHasPersonLinks
        }

        fun hasWalletChoice(): Boolean {
            return mHasWalletChoice
        }

        fun hasUnconfirmed(): Boolean {
            return mHasUnconfirmed
        }
    }

    constructor(context: Context) : super(context)

    override fun getObservedUri(): Uri? {
        return DataContentProvider.CONTENT_ALL
    }

    override fun loadInBackground(): Result? {
        val context: Context = getContext()
        val people = loadNames(context, DataContentProvider.CONTENT_PEOPLE,
                Contract.Person.ID, Contract.Person.NAME, PEOPLE_SORT_ORDER)
        val wallets = loadNames(context, DataContentProvider.CONTENT_WALLETS,
                Contract.Wallet.ID, Contract.Wallet.NAME, WALLET_SORT_ORDER)
        val linked = SearchFilter()
        linked.setPeopleIds(people.keys)
        val hasPersonLinks = !people.isEmpty() && countMatches(context, linked) > 0
        val hasWalletChoice = wallets.size > 1 || countTransactions(context,
                Contract.Transaction.TYPE + " = ?", arrayOf(Contract.TransactionType.TRANSFER.toString())) > 0
        val hasUnconfirmed = countTransactions(context, Contract.Transaction.CONFIRMED + " = 0", arrayOf()) > 0
        return Result(SearchCategoryLoader.loadCategories(context, null), people, wallets,
                hasPersonLinks, hasWalletChoice, hasUnconfirmed)
    }

    companion object {

        const val PEOPLE_SORT_ORDER = Contract.Person.NAME + " COLLATE LOCALIZED ASC"

        const val WALLET_SORT_ORDER = Contract.Wallet.ARCHIVED + " ASC, " +
                Contract.Wallet.INDEX + " ASC, " + Contract.Wallet.NAME + " COLLATE LOCALIZED ASC"

        internal fun countMatches(context: Context, filter: SearchFilter): Int {
            val selection = filter.toSelection(null)
            return countTransactions(context, selection.getL(), selection.getR())
        }

        private fun countTransactions(context: Context, selection: String?, selectionArgs: Array<String?>?): Int {
            val projection = arrayOf(Contract.Transaction.ID)
            val cursor: Cursor? = context.contentResolver.query(DataContentProvider.CONTENT_TRANSACTIONS, projection, selection, selectionArgs, null)
            if (cursor == null) {
                return 0
            }
            try {
                return cursor.count
            } finally {
                cursor.close()
            }
        }

        private fun loadNames(context: Context, uri: Uri, id: String, name: String, sortOrder: String): MutableMap<Long, String?> {
            val names: MutableMap<Long, String?> = LinkedHashMap()
            val cursor: Cursor? = context.contentResolver.query(uri, arrayOf(id, name), null, null, sortOrder)
            if (cursor != null) {
                try {
                    while (cursor.moveToNext()) {
                        names.put(cursor.getLong(0), cursor.getString(1))
                    }
                } finally {
                    cursor.close()
                }
            }
            return names
        }
    }
}
