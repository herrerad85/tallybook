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
import android.database.CursorWrapper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.loader.content.CursorLoader;

import com.oriondev.moneywallet.model.Pair;
import com.oriondev.moneywallet.model.SearchFilter;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.utils.CurrencyManager;

import java.util.HashMap;
import java.util.Map;

/**
 * The transactions a {@link SearchFilter} matches, with their count and totals. The selection is
 * built here and not by the caller, since an amount needs the currencies in use.
 */
public class SearchCursorLoader extends CursorLoader {

    /**
     * What the search strip shows, carried by the cursor this loader delivers.
     */
    public interface Summary {

        int getMatchCount();

        /**
         * @return the currency every match shares, or null when nothing matches or the matches
         *         mix currencies.
         */
        @Nullable
        String getCurrency();

        /**
         * @return the sum of the Out matches in minor units, or null when there is none or the
         *         matches mix currencies.
         */
        @Nullable
        Long getOut();

        /**
         * @return the sum of the In matches in minor units, or null when there is none or the
         *         matches mix currencies.
         */
        @Nullable
        Long getIn();
    }

    private final SearchFilter mFilter;

    public SearchCursorLoader(@NonNull Context context, @NonNull SearchFilter filter) {
        super(context, DataContentProvider.CONTENT_TRANSACTIONS, null, null, null,
                Contract.Transaction.DATE + " DESC");
        mFilter = filter.copy();
    }

    @Override
    public Cursor loadInBackground() {
        // only then, since the wallets query totals every transaction
        Map<String, Integer> decimals = mFilter.isAmountSet() ? loadDecimals(getContext()) : null;
        Pair<String, String[]> selection = mFilter.toSelection(decimals);
        setSelection(selection.getL());
        setSelectionArgs(selection.getR());
        Cursor cursor = super.loadInBackground();
        return cursor != null ? new SummaryCursor(cursor) : null;
    }

    /**
     * The decimals of every wallet currency, archived wallets included, since the transactions
     * query keeps their rows.
     */
    @VisibleForTesting
    static Map<String, Integer> loadDecimals(Context context) {
        Map<String, Integer> decimals = new HashMap<>();
        String[] projection = new String[] {Contract.Wallet.CURRENCY};
        Cursor cursor = context.getContentResolver().query(DataContentProvider.CONTENT_WALLETS, projection, null, null, null);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    String iso = cursor.getString(0);
                    decimals.put(iso, CurrencyManager.getDecimals(CurrencyManager.getCurrency(iso)));
                }
            } finally {
                cursor.close();
            }
        }
        return decimals;
    }

    private static class SummaryCursor extends CursorWrapper implements Summary {

        private final int mMatchCount;
        private final String mCurrency;
        private final Long mOut;
        private final Long mIn;

        private SummaryCursor(Cursor cursor) {
            super(cursor);
            int indexCurrency = cursor.getColumnIndexOrThrow(Contract.Transaction.WALLET_CURRENCY);
            int indexDirection = cursor.getColumnIndexOrThrow(Contract.Transaction.DIRECTION);
            int indexMoney = cursor.getColumnIndexOrThrow(Contract.Transaction.MONEY);
            String currency = null;
            boolean mixed = false;
            long out = 0L;
            long in = 0L;
            boolean hasOut = false;
            boolean hasIn = false;
            while (cursor.moveToNext()) {
                String rowCurrency = cursor.getString(indexCurrency);
                if (cursor.isFirst()) {
                    currency = rowCurrency;
                } else if (!TextUtils.equals(currency, rowCurrency)) {
                    mixed = true;
                    break;
                }
                if (cursor.getInt(indexDirection) == Contract.Direction.INCOME) {
                    in += cursor.getLong(indexMoney);
                    hasIn = true;
                } else {
                    out += cursor.getLong(indexMoney);
                    hasOut = true;
                }
            }
            cursor.moveToPosition(-1);
            boolean single = currency != null && !mixed;
            mMatchCount = cursor.getCount();
            mCurrency = single ? currency : null;
            mOut = single && hasOut ? out : null;
            mIn = single && hasIn ? in : null;
        }

        @Override
        public int getMatchCount() {
            return mMatchCount;
        }

        @Nullable
        @Override
        public String getCurrency() {
            return mCurrency;
        }

        @Nullable
        @Override
        public Long getOut() {
            return mOut;
        }

        @Nullable
        @Override
        public Long getIn() {
            return mIn;
        }
    }
}
