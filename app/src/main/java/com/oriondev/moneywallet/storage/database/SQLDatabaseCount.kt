package com.oriondev.moneywallet.storage.database

import android.content.ContentResolver
import android.net.Uri

data class BackupCount(val transactions: Int, val wallets: Int)

object SQLDatabaseCount {

    fun count(contentResolver: ContentResolver): BackupCount {
        val transactions = count(contentResolver, SyncContentProvider.CONTENT_TRANSACTION, Schema.Transaction.ID,
                Schema.Transaction.DELETED + " = 0 AND " + Schema.Transaction.TYPE + " != " + Contract.TransactionType.TRANSFER)
        val transfers = count(contentResolver, SyncContentProvider.CONTENT_TRANSFER, Schema.Transfer.ID,
                Schema.Transfer.DELETED + " = 0")
        val wallets = count(contentResolver, SyncContentProvider.CONTENT_WALLETS, Schema.Wallet.ID,
                Schema.Wallet.DELETED + " = 0")
        return BackupCount(transactions + transfers, wallets)
    }

    private fun count(contentResolver: ContentResolver, uri: Uri, column: String, selection: String): Int =
            contentResolver.query(uri, arrayOf(column), selection, null, null)!!.use { it.count }
}
