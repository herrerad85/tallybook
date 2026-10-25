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

package com.oriondev.moneywallet.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri

import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.MoneyScale
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.data.csv.CsvImportMapping

import java.math.BigDecimal
import java.math.BigInteger

/**
 * Readers for the extras another app puts on an intent: an amount, the name of a wallet or a
 * category, which is matched ignoring case and only counts when exactly one row carries it, and
 * the category the stored rules file a description under.
 */
object IntentExtras {

    /**
     * The amount an extra holds, in minor units of a currency with these decimals, rounded and
     * without its sign: an Integer, Long, Short, Byte, Float, Double, BigDecimal or BigInteger as
     * it is, a string trimmed and read as a dot decimal amount, and zero for an extra of any other
     * type, one that does not read, one whose text is longer than 40 characters, or no extra at
     * all.
     */
    @JvmStatic
    fun readAmount(extra: Any?, decimals: Int): Long {
        try {
            if (!(extra is CharSequence || extra is Int || extra is Long
                    || extra is Short || extra is Byte || extra is Float
                    || extra is Double || extra is BigDecimal
                    || extra is BigInteger)) {
                return 0L
            }
            val unscaled = if (extra is BigDecimal) extra.unscaledValue()
                    else if (extra is BigInteger) extra else null
            if (unscaled != null && unscaled.bitLength() > 133) {
                return 0L
            }
            val text = if (extra is Number) extra.toString() else extra.toString().trim { it <= ' ' }
            if (text.length > 40) {
                return 0L
            }
            val amount = if (extra is Number) BigDecimal(text)
                    else CsvImportMapping.parseAmount(text, false)
            val magnitude = amount.precision() - amount.scale()
            if (magnitude > 19 || magnitude < -decimals) {
                return 0L
            }
            return MoneyScale.toMinorUnitsRounded(amount.abs(), decimals)
        } catch (e: RuntimeException) {
            return 0L
        }
    }

    /**
     * The id of the one wallet that is not archived and carries this name, or null when none or
     * several do.
     */
    @JvmStatic
    fun findWalletId(contentResolver: ContentResolver?, name: String?): Long? {
        if (name == null) {
            return null
        }
        val cursor: Cursor? = contentResolver!!.query(DataContentProvider.CONTENT_WALLETS,
                arrayOf(Contract.Wallet.ID, Contract.Wallet.NAME),
                Contract.Wallet.ARCHIVED + " = 0", null, null)
        return findSingleId(cursor, Contract.Wallet.ID, Contract.Wallet.NAME, name)
    }

    /**
     * The id of the one income or expense category, parent or child, that carries this name, or
     * null when none or several do.
     */
    @JvmStatic
    fun findCategoryId(contentResolver: ContentResolver?, name: String?): Long? {
        if (name == null) {
            return null
        }
        val cursor: Cursor? = contentResolver!!.query(DataContentProvider.CONTENT_CATEGORIES,
                arrayOf(Contract.Category.ID, Contract.Category.NAME),
                Contract.Category.TYPE + " != ?",
                arrayOf(Contract.CategoryType.SYSTEM.getValue().toString()), null)
        return findSingleId(cursor, Contract.Category.ID, Contract.Category.NAME, name)
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
    @JvmStatic
    fun readCategoryNamedBy(contentResolver: ContentResolver?, description: String?): Category? {
        val matchUri: Uri = DataContentProvider.CONTENT_CATEGORY_RULES.buildUpon()
                .appendPath("match")
                .appendPath(description)
                .build()
        var categoryId: Long? = null
        val cursor: Cursor? = contentResolver!!.query(matchUri,
                arrayOf(Contract.CategoryRule.CATEGORY_ID), null, null, null)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                categoryId = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.CategoryRule.CATEGORY_ID))
            }
            cursor.close()
        }
        if (categoryId == null) {
            return null
        }
        return readCategory(contentResolver, categoryId)
    }

    @JvmStatic
    fun readCategory(contentResolver: ContentResolver?, categoryId: Long): Category? {
        val categoryUri: Uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, categoryId)
        val projection = arrayOf(
                Contract.Category.ID,
                Contract.Category.NAME,
                Contract.Category.ICON,
                Contract.Category.TYPE,
                Contract.Category.TAG
        )
        var category: Category? = null
        val cursor: Cursor? = contentResolver!!.query(categoryUri, projection, null, null, null)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                category = Category(
                        cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Category.ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.NAME)),
                        IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.ICON))),
                        Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Category.TYPE))),
                        cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.TAG))
                )
            }
            cursor.close()
        }
        return category
    }

    private fun findSingleId(cursor: Cursor?, idColumn: String, nameColumn: String, name: String): Long? {
        if (cursor == null) {
            return null
        }
        var id: Long? = null
        var matches = 0
        try {
            while (cursor.moveToNext()) {
                if (name.equals(cursor.getString(cursor.getColumnIndexOrThrow(nameColumn)), ignoreCase = true)) {
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(idColumn))
                    matches++
                }
            }
        } finally {
            cursor.close()
        }
        return if (matches == 1) id else null
    }
}
