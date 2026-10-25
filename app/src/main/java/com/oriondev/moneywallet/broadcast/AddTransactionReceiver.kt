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

package com.oriondev.moneywallet.broadcast

import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.text.TextUtils
import android.util.Log

import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.TransactionContentValuesBuilder
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.NewEditTransactionActivity
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.DefaultWallet
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.IntentExtras

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Date
import java.util.Locale

/**
 * The receiver for a save request from an automation app: one unconfirmed transaction, written
 * only while saving without a tap is on and the request carries the stored code, with nothing
 * written and nothing sent back when any check fails.
 */
class AddTransactionReceiver : BroadcastReceiver() {

    companion object {

        const val EXTRA_CODE = "code"

        private const val TAG = "AddTransactionReceiver"
        private const val CODE_BYTES = 16

        @JvmStatic
        fun newCode(): String {
            val bytes = ByteArray(CODE_BYTES)
            SecureRandom().nextBytes(bytes)
            val code = StringBuilder()
            for (b in bytes) {
                code.append(String.format(Locale.ROOT, "%02x", b))
            }
            return code.toString()
        }

        private fun readWallet(contentResolver: ContentResolver?, walletId: Long): Wallet? {
            val projection = arrayOf(
                    Contract.Wallet.ID,
                    Contract.Wallet.NAME,
                    Contract.Wallet.ICON,
                    Contract.Wallet.CURRENCY,
                    Contract.Wallet.START_MONEY,
                    Contract.Wallet.TOTAL_MONEY
            )
            val cursor: Cursor?
            if (walletId == PreferenceManager.TOTAL_WALLET_ID) {
                cursor = contentResolver!!.query(DataContentProvider.CONTENT_WALLETS, projection,
                        Contract.Wallet.ARCHIVED + " = 0", null,
                        Contract.Wallet.INDEX + " ASC, " + Contract.Wallet.NAME + " ASC")
            } else {
                cursor = contentResolver!!.query(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, walletId),
                        projection, null, null, null)
            }
            var wallet: Wallet? = null
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    wallet = Wallet(
                            cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Wallet.ID)),
                            cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.NAME)),
                            IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.ICON))),
                            CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.CURRENCY))),
                            cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Wallet.START_MONEY)),
                            cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Wallet.TOTAL_MONEY))
                    )
                }
                cursor.close()
            }
            return wallet
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!PreferenceManager.isAutomationSaveEnabled()) {
            Log.w(TAG, "Not saved: saving without a tap is off")
            return
        }
        val storedCode: String? = PreferenceManager.getAutomationCode()
        if (storedCode == null) {
            Log.w(TAG, "Not saved: no code is stored")
            return
        }
        val code: String? = intent.getStringExtra(EXTRA_CODE)
        if (code == null || !MessageDigest.isEqual(storedCode.toByteArray(StandardCharsets.UTF_8),
                code.toByteArray(StandardCharsets.UTF_8))) {
            Log.w(TAG, "Not saved: the code does not match")
            return
        }
        val contentResolver: ContentResolver? = context.contentResolver
        val namedWallet: Long? = IntentExtras.findWalletId(contentResolver,
                intent.getStringExtra(NewEditTransactionActivity.EXTRA_WALLET))
        val wallet: Wallet? = readWallet(contentResolver, if (namedWallet != null) namedWallet
                else DefaultWallet.resolveNewItemWallet(contentResolver))
        if (wallet == null || wallet.currency == null) {
            Log.w(TAG, "Not saved: no wallet")
            return
        }
        val money: Long = IntentExtras.readAmount(intent.extras!!.get(NewEditTransactionActivity.EXTRA_AMOUNT),
                wallet.currency.decimals)
        if (money <= 0L) {
            Log.w(TAG, "Not saved: no amount above zero")
            return
        }
        val description: String? = intent.getStringExtra(NewEditTransactionActivity.EXTRA_DESCRIPTION)
        val categoryId: Long? = IntentExtras.findCategoryId(contentResolver,
                intent.getStringExtra(NewEditTransactionActivity.EXTRA_CATEGORY))
        val category: Category? = if (categoryId != null) IntentExtras.readCategory(contentResolver, categoryId)
                else if (TextUtils.isEmpty(description)) null
                else IntentExtras.readCategoryNamedBy(contentResolver, description)
        if (category == null) {
            Log.w(TAG, "Not saved: no category")
            return
        }
        val contentValues: ContentValues? = TransactionContentValuesBuilder()
                .money(money)
                .date(DateUtils.getSQLDateTimeString(Date()))
                .description(description)
                .categoryId(category.id)
                .direction(category.direction)
                .type(Contract.TransactionType.STANDARD)
                .walletId(wallet.id)
                .note(intent.getStringExtra(NewEditTransactionActivity.EXTRA_NOTE))
                .confirmed(false)
                .countInTotal(true)
                .build()
        contentResolver!!.insert(DataContentProvider.CONTENT_TRANSACTIONS, contentValues)
    }
}
