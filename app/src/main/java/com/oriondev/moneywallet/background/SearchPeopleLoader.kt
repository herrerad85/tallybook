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

package com.oriondev.moneywallet.background;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.oriondev.moneywallet.model.SearchFilter;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every person with the number of transactions the People type matches with only that person
 * ticked, which is the count the search strip shows for it.
 */
public class SearchPeopleLoader extends AbstractGenericLoader<List<SearchPeopleLoader.Person>> {

    public static class Person {

        private final long mId;
        private final String mName;
        private final String mIcon;
        private final int mCount;

        private Person(long id, String name, String icon, int count) {
            mId = id;
            mName = name;
            mIcon = icon;
            mCount = count;
        }

        public long getId() {
            return mId;
        }

        public String getName() {
            return mName;
        }

        public String getIcon() {
            return mIcon;
        }

        public int getCount() {
            return mCount;
        }
    }

    public SearchPeopleLoader(Context context) {
        super(context);
    }

    @Override
    protected Uri getObservedUri() {
        return DataContentProvider.CONTENT_ALL;
    }

    @Override
    public List<Person> loadInBackground() {
        List<Person> people = new ArrayList<>();
        String[] projection = new String[] {Contract.Person.ID, Contract.Person.NAME, Contract.Person.ICON};
        Cursor cursor = getContext().getContentResolver().query(DataContentProvider.CONTENT_PEOPLE, projection, null, null, SearchRailLoader.PEOPLE_SORT_ORDER);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    SearchFilter filter = new SearchFilter();
                    filter.setPeopleIds(Collections.singleton(id));
                    int count = SearchRailLoader.countMatches(getContext(), filter);
                    people.add(new Person(id, cursor.getString(1), cursor.getString(2), count));
                }
            } finally {
                cursor.close();
            }
        }
        return people;
    }
}
