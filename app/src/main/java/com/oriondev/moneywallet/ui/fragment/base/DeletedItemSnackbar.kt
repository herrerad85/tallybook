package com.oriondev.moneywallet.ui.fragment.base

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View

import androidx.annotation.PluralsRes
import androidx.fragment.app.Fragment

import com.google.android.material.snackbar.Snackbar
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.SQLiteDataException
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog

import java.util.concurrent.Executor

object DeletedItemSnackbar {

    private val mainHandler = Handler(Looper.getMainLooper())

    @JvmStatic
    fun deletedUri(itemUri: Uri): Uri = Uri.withAppendedPath(itemUri, "deleted")

    @JvmStatic
    fun show(host: Fragment, anchor: View, message: CharSequence, itemUris: List<Uri>, executor: Executor) {
        val resolver = host.requireContext().applicationContext.contentResolver
        Snackbar.make(anchor, message, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_undo) {
                    executor.execute {
                        val refused = itemUris.mapNotNull { itemUri ->
                            try {
                                resolver.update(Uri.withAppendedPath(itemUri, "restore"), null, null, null)
                                null
                            } catch (e: SQLiteDataException) {
                                e
                            }
                        }
                        mainHandler.post {
                            if (host.view != null && refused.isNotEmpty()) {
                                showRestoreError(host, refused)
                            }
                        }
                    }
                }
                .show()
    }

    private fun showRestoreError(host: Fragment, refused: List<SQLiteDataException>) {
        val message = refused.map { messageOf(it) }.distinct().singleOrNull()
                ?: R.plurals.message_error_restore_transactions
        ThemedDialog.buildMaterialDialog(host.requireActivity())
                .setTitle(R.string.title_error)
                .setMessage(host.resources.getQuantityString(message, refused.size, refused.size))
                .setPositiveButton(android.R.string.ok, null)
                .show()
    }

    @PluralsRes
    private fun messageOf(e: SQLiteDataException): Int = when (e.errorCode) {
        Contract.ErrorCode.WALLETS_NOT_CONSISTENT -> R.plurals.message_error_restore_transactions_currency
        Contract.ErrorCode.SPLIT_NOT_VALID -> R.plurals.message_error_restore_transactions_category
        else -> R.plurals.message_error_restore_transactions
    }
}
