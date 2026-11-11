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

import java.util.Collections

/**
 * Every person with the number of transactions the People type matches with only that person
 * ticked, which is the count the search strip shows for it.
 */
class SearchPeopleLoader : AbstractGenericLoader<MutableList<SearchPeopleLoader.Person>> {

    class Person {

        private val mId: Long
        private val mName: String?
        private val mIcon: String?
        private val mCount: Int

        internal constructor(id: Long, name: String?, icon: String?, count: Int) {
            mId = id
            mName = name
            mIcon = icon
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

        fun getCount(): Int {
            return mCount
        }
    }

    constructor(context: Context) : super(context)

    override fun getObservedUri(): Uri? {
        return DataContentProvider.CONTENT_ALL
    }

    override fun loadInBackground(): MutableList<Person>? {
        val people: MutableList<Person> = ArrayList()
        val projection = arrayOf(Contract.Person.ID, Contract.Person.NAME, Contract.Person.ICON)
        val cursor: Cursor? = context.contentResolver.query(DataContentProvider.CONTENT_PEOPLE, projection, null, null, SearchRailLoader.PEOPLE_SORT_ORDER)
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val filter = SearchFilter()
                    filter.setPeopleIds(Collections.singleton(id))
                    val count = SearchRailLoader.countMatches(context, filter)
                    people.add(Person(id, cursor.getString(1), cursor.getString(2), count))
                }
            } finally {
                cursor.close()
            }
        }
        return people
    }
}
