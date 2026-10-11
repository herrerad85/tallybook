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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * @param counts the ids of the transactions in each category, by id, or null to count none.
     */
    public static List<Entry> loadCategories(Context context, @Nullable Map<Long, Set<Long>> counts) {
        List<Entry> expense = new ArrayList<>();
        List<Entry> income = new ArrayList<>();
        List<Entry> system = new ArrayList<>();
        Map<Long, Entry> parents = new HashMap<>();
        // a transaction split across a parent and its child, or two children, counts once for the
        // parent, which ticks them all
        Map<Long, Set<Long>> family = new HashMap<>();
        String[] projection = new String[] {Contract.Category.ID, Contract.Category.NAME,
                Contract.Category.ICON, Contract.Category.TYPE, Contract.Category.PARENT};
        Cursor cursor = context.getContentResolver().query(DataContentProvider.CONTENT_CATEGORIES, projection, null, null, MultiCategoryPickerDialog.SORT_ORDER);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    Set<Long> transactions = counts != null && counts.containsKey(id) ? counts.get(id) : new HashSet<>();
                    Contract.CategoryType type = Contract.CategoryType.fromValue(cursor.getInt(3));
                    Entry entry = new Entry(id, cursor.getString(1), cursor.getString(2), type, transactions.size());
                    // null for a top level category, and for one whose parent was deleted
                    Entry parent = cursor.isNull(4) ? null : parents.get(cursor.getLong(4));
                    if (parent != null) {
                        parent.mChildren.add(entry);
                        Set<Long> union = family.get(parent.mId);
                        union.addAll(transactions);
                        parent.mCount = union.size();
                    } else {
                        parents.put(id, entry);
                        family.put(id, new HashSet<>(transactions));
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
     * One pass over the category lines, so a split transaction is found under each category it
     * has a line in.
     */
    private static Map<Long, Set<Long>> countByCategory(Context context) {
        Map<Long, Set<Long>> counts = new HashMap<>();
        String[] projection = new String[] {Contract.Transaction.CATEGORY_ID, Contract.Transaction.ID};
        Cursor cursor = context.getContentResolver().query(DataContentProvider.CONTENT_CATEGORY_LINES, projection, null, null, null);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    counts.computeIfAbsent(cursor.getLong(0), id -> new HashSet<>()).add(cursor.getLong(1));
                }
            } finally {
                cursor.close();
            }
        }
        return counts;
    }
}
