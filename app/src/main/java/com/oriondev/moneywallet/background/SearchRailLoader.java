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

import androidx.annotation.NonNull;

import com.oriondev.moneywallet.model.Pair;
import com.oriondev.moneywallet.model.SearchFilter;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the search rail reads from the database: whether a transaction or a side of a transfer
 * links a person, whether there is more than one wallet or any transfer, whether any row is
 * unconfirmed, and the names the Category, People and Wallet chips show, in their editors' order.
 */
public class SearchRailLoader extends AbstractGenericLoader<SearchRailLoader.Result> {

    public static final String PEOPLE_SORT_ORDER = Contract.Person.NAME + " ASC";

    public static final String WALLET_SORT_ORDER = Contract.Wallet.ARCHIVED + " ASC, "
            + Contract.Wallet.INDEX + " ASC, " + Contract.Wallet.NAME + " ASC";

    public static class Result {

        private final List<SearchCategoryLoader.Entry> mCategories;
        private final Map<Long, String> mPeople;
        private final Map<Long, String> mWallets;
        private final boolean mHasPersonLinks;
        private final boolean mHasWalletChoice;
        private final boolean mHasUnconfirmed;

        private Result(List<SearchCategoryLoader.Entry> categories, Map<Long, String> people,
                       Map<Long, String> wallets, boolean hasPersonLinks, boolean hasWalletChoice,
                       boolean hasUnconfirmed) {
            mCategories = categories;
            mPeople = people;
            mWallets = wallets;
            mHasPersonLinks = hasPersonLinks;
            mHasWalletChoice = hasWalletChoice;
            mHasUnconfirmed = hasUnconfirmed;
        }

        public List<SearchCategoryLoader.Entry> getCategories() {
            return mCategories;
        }

        public Map<Long, String> getPeople() {
            return mPeople;
        }

        public Map<Long, String> getWallets() {
            return mWallets;
        }

        public boolean hasPersonLinks() {
            return mHasPersonLinks;
        }

        public boolean hasWalletChoice() {
            return mHasWalletChoice;
        }

        public boolean hasUnconfirmed() {
            return mHasUnconfirmed;
        }
    }

    public SearchRailLoader(Context context) {
        super(context);
    }

    @Override
    protected Uri getObservedUri() {
        return DataContentProvider.CONTENT_ALL;
    }

    @Override
    public Result loadInBackground() {
        Context context = getContext();
        Map<Long, String> people = loadNames(context, DataContentProvider.CONTENT_PEOPLE,
                Contract.Person.ID, Contract.Person.NAME, PEOPLE_SORT_ORDER);
        Map<Long, String> wallets = loadNames(context, DataContentProvider.CONTENT_WALLETS,
                Contract.Wallet.ID, Contract.Wallet.NAME, WALLET_SORT_ORDER);
        SearchFilter linked = new SearchFilter();
        linked.setPeopleIds(people.keySet());
        boolean hasPersonLinks = !people.isEmpty() && countMatches(context, linked) > 0;
        boolean hasWalletChoice = wallets.size() > 1 || countTransactions(context,
                Contract.Transaction.TYPE + " = ?", String.valueOf(Contract.TransactionType.TRANSFER)) > 0;
        boolean hasUnconfirmed = countTransactions(context, Contract.Transaction.CONFIRMED + " = 0") > 0;
        return new Result(SearchCategoryLoader.loadCategories(context, null), people, wallets,
                hasPersonLinks, hasWalletChoice, hasUnconfirmed);
    }

    /*package-local*/ static int countMatches(Context context, @NonNull SearchFilter filter) {
        Pair<String, String[]> selection = filter.toSelection(null);
        return countTransactions(context, selection.getL(), selection.getR());
    }

    private static int countTransactions(Context context, String selection, String... selectionArgs) {
        String[] projection = new String[] {Contract.Transaction.ID};
        Cursor cursor = context.getContentResolver().query(DataContentProvider.CONTENT_TRANSACTIONS, projection, selection, selectionArgs, null);
        if (cursor == null) {
            return 0;
        }
        try {
            return cursor.getCount();
        } finally {
            cursor.close();
        }
    }

    private static Map<Long, String> loadNames(Context context, Uri uri, String id, String name, String sortOrder) {
        Map<Long, String> names = new LinkedHashMap<>();
        Cursor cursor = context.getContentResolver().query(uri, new String[] {id, name}, null, null, sortOrder);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    names.put(cursor.getLong(0), cursor.getString(1));
                }
            } finally {
                cursor.close();
            }
        }
        return names;
    }
}
