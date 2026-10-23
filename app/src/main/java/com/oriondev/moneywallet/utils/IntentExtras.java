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

package com.oriondev.moneywallet.utils;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;

import com.oriondev.moneywallet.model.Category;
import com.oriondev.moneywallet.model.MoneyScale;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.data.csv.CsvImportMapping;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Readers for the extras another app puts on an intent: an amount, the name of a wallet or a
 * category, which is matched ignoring case and only counts when exactly one row carries it, and
 * the category the stored rules file a description under.
 */
public class IntentExtras {

    private IntentExtras() {
    }

    /**
     * The amount an extra holds, in minor units of a currency with these decimals, rounded and
     * without its sign: an Integer, Long, Short, Byte, Float, Double, BigDecimal or BigInteger as
     * it is, a string trimmed and read as a dot decimal amount, and zero for an extra of any other
     * type, one that does not read, one whose text is longer than 40 characters, or no extra at
     * all.
     */
    public static long readAmount(Object extra, int decimals) {
        try {
            if (!(extra instanceof CharSequence || extra instanceof Integer || extra instanceof Long
                    || extra instanceof Short || extra instanceof Byte || extra instanceof Float
                    || extra instanceof Double || extra instanceof BigDecimal
                    || extra instanceof BigInteger)) {
                return 0L;
            }
            BigInteger unscaled = extra instanceof BigDecimal ? ((BigDecimal) extra).unscaledValue()
                    : extra instanceof BigInteger ? (BigInteger) extra : null;
            if (unscaled != null && unscaled.bitLength() > 133) {
                return 0L;
            }
            String text = extra instanceof Number ? extra.toString() : String.valueOf(extra).trim();
            if (text.length() > 40) {
                return 0L;
            }
            BigDecimal amount = extra instanceof Number ? new BigDecimal(text)
                    : CsvImportMapping.parseAmount(text, false);
            int magnitude = amount.precision() - amount.scale();
            if (magnitude > 19 || magnitude < -decimals) {
                return 0L;
            }
            return MoneyScale.toMinorUnitsRounded(amount.abs(), decimals);
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    /**
     * The id of the one wallet that is not archived and carries this name, or null when none or
     * several do.
     */
    public static Long findWalletId(ContentResolver contentResolver, String name) {
        if (name == null) {
            return null;
        }
        Cursor cursor = contentResolver.query(DataContentProvider.CONTENT_WALLETS,
                new String[] {Contract.Wallet.ID, Contract.Wallet.NAME},
                Contract.Wallet.ARCHIVED + " = 0", null, null);
        return findSingleId(cursor, Contract.Wallet.ID, Contract.Wallet.NAME, name);
    }

    /**
     * The id of the one income or expense category, parent or child, that carries this name, or
     * null when none or several do.
     */
    public static Long findCategoryId(ContentResolver contentResolver, String name) {
        if (name == null) {
            return null;
        }
        Cursor cursor = contentResolver.query(DataContentProvider.CONTENT_CATEGORIES,
                new String[] {Contract.Category.ID, Contract.Category.NAME},
                Contract.Category.TYPE + " != ?",
                new String[] {String.valueOf(Contract.CategoryType.SYSTEM.getValue())}, null);
        return findSingleId(cursor, Contract.Category.ID, Contract.Category.NAME, name);
    }

    /**
     * The category the stored rules file this description under, or null when none of them does.
     * The description travels as one path segment, which is what carries a slash or a space in it
     * through to the lookup whole.
     *
     * @param contentResolver resolver to read through.
     * @param description the description of the transaction.
     * @return the category, or null when no rule matches or its category is gone.
     */
    public static Category readCategoryNamedBy(ContentResolver contentResolver, String description) {
        Uri matchUri = DataContentProvider.CONTENT_CATEGORY_RULES.buildUpon()
                .appendPath("match")
                .appendPath(description)
                .build();
        Long categoryId = null;
        Cursor cursor = contentResolver.query(matchUri,
                new String[] {Contract.CategoryRule.CATEGORY_ID}, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                categoryId = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.CategoryRule.CATEGORY_ID));
            }
            cursor.close();
        }
        if (categoryId == null) {
            return null;
        }
        return readCategory(contentResolver, categoryId);
    }

    public static Category readCategory(ContentResolver contentResolver, long categoryId) {
        Uri categoryUri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, categoryId);
        String[] projection = new String[] {
                Contract.Category.ID,
                Contract.Category.NAME,
                Contract.Category.ICON,
                Contract.Category.TYPE,
                Contract.Category.TAG
        };
        Category category = null;
        Cursor cursor = contentResolver.query(categoryUri, projection, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                category = new Category(
                        cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Category.ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.NAME)),
                        IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.ICON))),
                        Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Category.TYPE))),
                        cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.TAG))
                );
            }
            cursor.close();
        }
        return category;
    }

    private static Long findSingleId(Cursor cursor, String idColumn, String nameColumn, String name) {
        if (cursor == null) {
            return null;
        }
        Long id = null;
        int matches = 0;
        try {
            while (cursor.moveToNext()) {
                if (name.equalsIgnoreCase(cursor.getString(cursor.getColumnIndexOrThrow(nameColumn)))) {
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(idColumn));
                    matches++;
                }
            }
        } finally {
            cursor.close();
        }
        return matches == 1 ? id : null;
    }
}
