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

import androidx.annotation.Nullable;

import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.ui.fragment.dialog.MultiCategoryPickerDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every category in the Category editor's order, the expense parents, then the income ones, then
 * the system categories, each parent holding its children. Each carries the number of
 * transactions the Category type matches with only its checkbox tapped, which is the count the
 * search strip shows for it.
 */
public class SearchCategoryLoader extends AbstractGenericLoader<List<SearchCategoryLoader.Entry>> {

    public static class Entry {

        private final long mId;
        private final String mName;
        private final String mIcon;
        private final Contract.CategoryType mType;
        private final List<Entry> mChildren = new ArrayList<>();
        private int mCount;

        private Entry(long id, String name, String icon, Contract.CategoryType type, int count) {
            mId = id;
            mName = name;
            mIcon = icon;
            mType = type;
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

        public Contract.CategoryType getType() {
            return mType;
        }

        public List<Entry> getChildren() {
            return mChildren;
        }

        public int getCount() {
            return mCount;
        }

        /**
         * @return this category's id and every child's, which a tap on its checkbox ticks.
         */
        public List<Long> getIds() {
            List<Long> ids = new ArrayList<>();
            ids.add(mId);
            for (Entry child : mChildren) {
                ids.add(child.mId);
            }
            return ids;
        }
    }

    public SearchCategoryLoader(Context context) {
        super(context);
    }

    @Override
    protected Uri getObservedUri() {
        return DataContentProvider.CONTENT_ALL;
    }

    @Override
    public List<Entry> loadInBackground() {
        return loadCategories(getContext(), countByCategory(getContext()));
    }

    /**
     * @param counts the transactions in each category, by id, or null to count none.
     */
    public static List<Entry> loadCategories(Context context, @Nullable Map<Long, Integer> counts) {
        List<Entry> expense = new ArrayList<>();
        List<Entry> income = new ArrayList<>();
        List<Entry> system = new ArrayList<>();
        Map<Long, Entry> parents = new HashMap<>();
        String[] projection = new String[] {Contract.Category.ID, Contract.Category.NAME,
                Contract.Category.ICON, Contract.Category.TYPE, Contract.Category.PARENT};
        Cursor cursor = context.getContentResolver().query(DataContentProvider.CONTENT_CATEGORIES, projection, null, null, MultiCategoryPickerDialog.SORT_ORDER);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    Integer count = counts != null ? counts.get(id) : null;
                    Contract.CategoryType type = Contract.CategoryType.fromValue(cursor.getInt(3));
                    Entry entry = new Entry(id, cursor.getString(1), cursor.getString(2), type, count != null ? count : 0);
                    // null for a top level category, and for one whose parent was deleted
                    Entry parent = cursor.isNull(4) ? null : parents.get(cursor.getLong(4));
                    if (parent != null) {
                        parent.mChildren.add(entry);
                        parent.mCount += entry.mCount;
                    } else {
                        parents.put(id, entry);
                        (type == Contract.CategoryType.EXPENSE ? expense
                                : type == Contract.CategoryType.INCOME ? income : system).add(entry);
                    }
                }
            } finally {
                cursor.close();
            }
        }
        List<Entry> categories = new ArrayList<>(expense);
        categories.addAll(income);
        categories.addAll(system);
        return categories;
    }

    /**
     * One pass over the rows the strip can show. A transaction has one category, so the rows a set
     * of ids matches are the sum of each id's count.
     */
    private static Map<Long, Integer> countByCategory(Context context) {
        Map<Long, Integer> counts = new HashMap<>();
        String[] projection = new String[] {Contract.Transaction.CATEGORY_ID};
        Cursor cursor = context.getContentResolver().query(DataContentProvider.CONTENT_TRANSACTIONS, projection, null, null, null);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    counts.merge(cursor.getLong(0), 1, Integer::sum);
                }
            } finally {
                cursor.close();
            }
        }
        return counts;
    }
}
