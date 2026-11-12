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

import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.fragment.dialog.MultiCategoryPickerDialog

/**
 * Every category in the Category editor's order, the expense parents, then the income ones, then
 * the system categories, each parent holding its children. Each carries the number of
 * transactions the Category type matches with only its checkbox tapped, which is the count the
 * search strip shows for it.
 */
class SearchCategoryLoader : AbstractGenericLoader<MutableList<SearchCategoryLoader.Entry>> {

    class Entry {

        private val mId: Long
        private val mName: String?
        private val mIcon: String?
        private val mType: Contract.CategoryType?
        private val mChildren: MutableList<Entry> = ArrayList()
        internal var mCount: Int

        internal constructor(id: Long, name: String?, icon: String?, type: Contract.CategoryType?, count: Int) {
            mId = id
            mName = name
            mIcon = icon
            mType = type
            mCount = count
        }

        fun getId(): Long {
            return mId
        }

        fun getName(): String? {
            return mName
        }

        fun getIcon(): String? {
            return mIcon
        }

        fun getType(): Contract.CategoryType? {
            return mType
        }

        fun getChildren(): MutableList<Entry> {
            return mChildren
        }

        fun getCount(): Int {
            return mCount
        }

        /**
         * @return this category's id and every child's, which a tap on its checkbox ticks.
         */
        fun getIds(): MutableList<Long> {
            val ids: MutableList<Long> = ArrayList()
            ids.add(mId)
            for (child in mChildren) {
                ids.add(child.mId)
            }
            return ids
        }
    }

    constructor(context: Context) : super(context)

    override fun getObservedUri(): Uri? {
        return DataContentProvider.CONTENT_ALL
    }

    override fun loadInBackground(): MutableList<Entry>? {
        return loadCategories(context, countByCategory(context))
    }

    companion object {

        /**
         * @param counts the ids of the transactions in each category, by id, or null to count none.
         */
        @JvmStatic
        fun loadCategories(context: Context, counts: MutableMap<Long, MutableSet<Long>>?): MutableList<Entry> {
            val expense: MutableList<Entry> = ArrayList()
            val income: MutableList<Entry> = ArrayList()
            val system: MutableList<Entry> = ArrayList()
            val parents: MutableMap<Long, Entry> = HashMap()
            // a transaction split across a parent and its child, or two children, counts once for the
            // parent, which ticks them all
            val family: MutableMap<Long, MutableSet<Long>> = HashMap()
            val projection = arrayOf(Contract.Category.ID, Contract.Category.NAME,
                    Contract.Category.ICON, Contract.Category.TYPE, Contract.Category.PARENT)
            val cursor: Cursor? = context.contentResolver.query(DataContentProvider.CONTENT_CATEGORIES, projection, null, null, MultiCategoryPickerDialog.SORT_ORDER)
            if (cursor != null) {
                try {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val transactions: MutableSet<Long>? = if (counts != null && counts.containsKey(id)) counts.get(id) else HashSet()
                        val type = Contract.CategoryType.fromValue(cursor.getInt(3))
                        val entry = Entry(id, cursor.getString(1), cursor.getString(2), type, transactions!!.size)
                        // null for a top level category, and for one whose parent was deleted
                        val parent = if (cursor.isNull(4)) null else parents.get(cursor.getLong(4))
                        if (parent != null) {
                            parent.getChildren().add(entry)
                            val union = family.get(parent.getId())
                            union!!.addAll(transactions)
                            parent.mCount = union.size
                        } else {
                            parents.put(id, entry)
                            family.put(id, HashSet(transactions))
                            (if (type === Contract.CategoryType.EXPENSE) expense
                                    else if (type === Contract.CategoryType.INCOME) income else system).add(entry)
                        }
                    }
                } finally {
                    cursor.close()
                }
            }
            val categories: MutableList<Entry> = ArrayList(expense)
            categories.addAll(income)
            categories.addAll(system)
            return categories
        }

        /**
         * One pass over the category lines, so a split transaction is found under each category it
         * has a line in.
         */
        private fun countByCategory(context: Context): MutableMap<Long, MutableSet<Long>> {
            val counts: MutableMap<Long, MutableSet<Long>> = HashMap()
            val projection = arrayOf(Contract.Transaction.CATEGORY_ID, Contract.Transaction.ID)
            val cursor: Cursor? = context.contentResolver.query(DataContentProvider.CONTENT_CATEGORY_LINES, projection, null, null, null)
            if (cursor != null) {
                try {
                    while (cursor.moveToNext()) {
                        counts.computeIfAbsent(cursor.getLong(0)) { id -> HashSet() }.add(cursor.getLong(1))
                    }
                } finally {
                    cursor.close()
                }
            }
            return counts
        }
    }
}
