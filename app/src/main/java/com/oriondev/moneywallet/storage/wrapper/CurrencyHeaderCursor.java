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

package com.oriondev.moneywallet.storage.wrapper;

import android.database.Cursor;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.oriondev.moneywallet.background.SearchCursorLoader;
import com.oriondev.moneywallet.storage.database.Contract;

/**
 * Search results that mix currencies, with a header row before each currency's run of rows. It
 * carries the count of the rows alone, and no totals, since they would add up different currencies.
 */
public class CurrencyHeaderCursor extends AbstractHeaderCursor<String> implements SearchCursorLoader.Summary {

    public static final String COLUMN_HEADER_CURRENCY = "header_currency";

    private static final int INDEX_ITEM_TYPE = 0;
    private static final int INDEX_HEADER_CURRENCY = 1;

    private final int mMatchCount;

    public CurrencyHeaderCursor(Cursor cursor) {
        super(cursor);
        mMatchCount = cursor.getCount();
        generateHeaders(cursor);
    }

    @Override
    protected void generateHeaders(Cursor cursor) {
        int indexCurrency = cursor.getColumnIndexOrThrow(Contract.Transaction.WALLET_CURRENCY);
        String currency = null;
        while (cursor.moveToNext()) {
            String rowCurrency = cursor.getString(indexCurrency);
            if (cursor.isFirst() || !TextUtils.equals(currency, rowCurrency)) {
                currency = rowCurrency;
                addHeader(currency);
            }
            addItem(cursor.getPosition());
        }
    }

    @Override
    protected String[] getHeaderColumnNames() {
        return new String[] {
                TransactionHeaderCursor.COLUMN_ITEM_TYPE,
                COLUMN_HEADER_CURRENCY
        };
    }

    @Override
    protected String getHeaderString(int index) {
        return index == INDEX_HEADER_CURRENCY && isHeader() ? getHeader() : null;
    }

    @Override
    protected short getHeaderShort(int index) {
        return 0;
    }

    @Override
    protected int getHeaderInt(int index) {
        if (index == INDEX_ITEM_TYPE) {
            return isHeader() ? TransactionHeaderCursor.TYPE_HEADER : TransactionHeaderCursor.TYPE_ITEM;
        }
        return 0;
    }

    @Override
    protected long getHeaderLong(int index) {
        return 0;
    }

    @Override
    protected float getHeaderFloat(int index) {
        return 0;
    }

    @Override
    protected double getHeaderDouble(int index) {
        return 0;
    }

    @Override
    protected boolean isHeaderNull(int index) {
        return index == INDEX_HEADER_CURRENCY && getHeaderString(index) == null;
    }

    @Override
    public int getMatchCount() {
        return mMatchCount;
    }

    @Nullable
    @Override
    public String getCurrency() {
        return null;
    }

    @Nullable
    @Override
    public Long getOut() {
        return null;
    }

    @Nullable
    @Override
    public Long getIn() {
        return null;
    }
}
