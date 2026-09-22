/*
 * Copyright (c) 2018.
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

import com.oriondev.moneywallet.model.Group;
import com.oriondev.moneywallet.model.Money;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.utils.DateUtils;

import java.util.Date;

/**
 * Created by andrea on 03/03/18.
 */
public class TransactionHeaderCursor extends AbstractHeaderCursor<TransactionHeaderCursor.Header> {

    public static final String COLUMN_ITEM_TYPE = "item_type";
    public static final String COLUMN_HEADER_START_DATE = "header_start_date";
    public static final String COLUMN_HEADER_END_DATE = "header_end_date";
    public static final String COLUMN_HEADER_MONEY = "header_money";
    public static final String COLUMN_HEADER_INCOME = "header_income";
    public static final String COLUMN_HEADER_EXPENSE = "header_expense";
    public static final String COLUMN_HEADER_TRANSFER = "header_transfer";
    public static final String COLUMN_HEADER_GROUP_TYPE = "header_group_type";

    public final static int TYPE_HEADER = 0;
    public final static int TYPE_ITEM = 1;

    private static final int INDEX_ITEM_TYPE = 0;
    private static final int INDEX_HEADER_START_DATE = 1;
    private static final int INDEX_HEADER_END_DATE = 2;
    private static final int INDEX_HEADER_MONEY = 3;
    private static final int INDEX_HEADER_INCOME = 4;
    private static final int INDEX_HEADER_EXPENSE = 5;
    private static final int INDEX_HEADER_TRANSFER = 6;
    private static final int INDEX_HEADER_GROUP_TYPE = 7;

    private final Group mGroup;
    private final Date mLowerBound;
    private final Date mUpperBound;

    public TransactionHeaderCursor(Cursor cursor, Group group, Date lowerBound, Date upperBound) {
        super(cursor);
        mGroup = group;
        mLowerBound = lowerBound;
        mUpperBound = upperBound;
        generateHeaders(cursor);
    }

    @Override
    protected void generateHeaders(Cursor cursor) {
        int indexTransactionDirection = cursor.getColumnIndex(Contract.Transaction.DIRECTION);
        int indexTransactionDate = cursor.getColumnIndex(Contract.Transaction.DATE);
        int indexTransactionMoney = cursor.getColumnIndex(Contract.Transaction.MONEY);
        int indexCurrency = cursor.getColumnIndex(Contract.Transaction.WALLET_CURRENCY);
        int indexTransactionConfirmed = cursor.getColumnIndex(Contract.Transaction.CONFIRMED);
        int indexTransactionCountInTotal = cursor.getColumnIndex(Contract.Transaction.COUNT_IN_TOTAL);
        int indexCategoryTag = cursor.getColumnIndex(Contract.Transaction.CATEGORY_TAG);
        if (cursor.moveToFirst()) {
            Header header = null;
            do {
                String dateTime = cursor.getString(indexTransactionDate);
                Date date = DateUtils.getDateFromSQLDateTimeString(dateTime);
                if (header == null) {
                    header = new Header(mGroup, mLowerBound, mUpperBound, date);
                    addHeader(header);
                } else {
                    if (!header.isInBounds(date)) {
                        header = new Header(mGroup, mLowerBound, mUpperBound, date);
                        addHeader(header);
                    }
                }
                addItem(cursor.getPosition());
                if (cursor.getInt(indexTransactionConfirmed) == 1 && cursor.getInt(indexTransactionCountInTotal) == 1) {
                    String currency = cursor.getString(indexCurrency);
                    long money = cursor.getLong(indexTransactionMoney);
                    int direction = cursor.getInt(indexTransactionDirection);
                    header.add(currency, money, direction, cursor.getString(indexCategoryTag));
                }
            } while (cursor.moveToNext());
        }
    }

    @Override
    protected String[] getHeaderColumnNames() {
        return new String[] {
                COLUMN_ITEM_TYPE,
                COLUMN_HEADER_START_DATE,
                COLUMN_HEADER_END_DATE,
                COLUMN_HEADER_MONEY,
                COLUMN_HEADER_INCOME,
                COLUMN_HEADER_EXPENSE,
                COLUMN_HEADER_TRANSFER,
                COLUMN_HEADER_GROUP_TYPE
        };
    }

    @Override
    protected String getHeaderString(int index) {
        if (isHeader()) {
            Header header = getHeader();
            switch (index) {
                case INDEX_HEADER_START_DATE:
                    return DateUtils.getSQLDateTimeString(header.getStartDate());
                case INDEX_HEADER_END_DATE:
                    return DateUtils.getSQLDateTimeString(header.getEndDate());
                case INDEX_HEADER_MONEY:
                    return header.getMoney().toString();
                case INDEX_HEADER_INCOME:
                    return header.getIncome().toString();
                case INDEX_HEADER_EXPENSE:
                    return header.getExpense().toString();
                case INDEX_HEADER_TRANSFER:
                    return header.getTransfer().toString();
            }
        }
        return null;
    }

    @Override
    protected short getHeaderShort(int index) {
        return 0;
    }

    @Override
    protected int getHeaderInt(int index) {
        switch (index) {
            case INDEX_ITEM_TYPE:
                return isHeader() ? TYPE_HEADER : TYPE_ITEM;
            case INDEX_HEADER_GROUP_TYPE:
                return mGroup.getType();
            default:
                return 0;
        }
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
        return false;
    }

    /*package-local*/ static class Header extends DateRangeHeader {

        private final Money mMoney;
        private final Money mIncome;
        private final Money mExpense;
        private final Money mTransfer;

        /*package-local*/ Header(Group group, Date lowerBound, Date upperBound, Date date) {
            super(group, lowerBound, upperBound, date);
            mMoney = new Money();
            mIncome = new Money();
            mExpense = new Money();
            mTransfer = new Money();
        }

        /**
         * Count one row into this header. The total is the difference, can come out either sign,
         * and every row reaches it.
         *
         * Money moved between two of the user's own wallets is neither earned nor spent, so a
         * row in the transfer category is held apart from the other two and counted as a plus
         * or a minus of its own. Incomes and expenses only ever grow, so a row that is not a
         * transfer adds to one of them and leaves the other alone. The three together still
         * come to the total.
         *
         * The tag is what tells a transfer apart and not the transaction type, because a transfer
         * with a fee writes a third row carrying that same type and the fee is money genuinely
         * spent.
         *
         * A debt taken on lands in the incoming figure, which is unchanged. PeriodDetailSummaryLoader,
         * the report a reader reaches from this header, splits a row the same way, so a row lands
         * on the same side in both. It counts fewer rows than this does, since it drops a category
         * kept out of the reports and a header counts every row on the list under it.
         */
        /*package-local*/ void add(String currency, long money, int direction, String categoryTag) {
            long signed = direction == Contract.Direction.INCOME ? money : -money;
            mMoney.addMoney(currency, signed);
            if (Contract.CategoryTag.TRANSFER.equals(categoryTag)) {
                mTransfer.addMoney(currency, signed);
            } else if (direction == Contract.Direction.INCOME) {
                mIncome.addMoney(currency, money);
            } else {
                mExpense.addMoney(currency, money);
            }
        }

        /*package-local*/ Money getMoney() {
            return mMoney;
        }

        /*package-local*/ Money getIncome() {
            return mIncome;
        }

        /*package-local*/ Money getExpense() {
            return mExpense;
        }

        /*package-local*/ Money getTransfer() {
            return mTransfer;
        }
    }
}