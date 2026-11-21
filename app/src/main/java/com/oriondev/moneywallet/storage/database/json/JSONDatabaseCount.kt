package com.oriondev.moneywallet.storage.database.json

import com.oriondev.moneywallet.storage.database.BackupCount
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.ImportException
import com.oriondev.moneywallet.storage.database.backup.DefaultBackupImporter

import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.exception.ZipException

import org.json.JSONException
import org.json.JSONObject

import java.io.File
import java.io.IOException

object JSONDatabaseCount {

    @Throws(ImportException::class)
    fun count(backupFile: File, password: String?): BackupCount {
        try {
            val zipFile = ZipFile(backupFile)
            val header = DefaultBackupImporter.getDatabaseHeader(zipFile, password)
            val importer = JSONDatabaseImporter(zipFile.getInputStream(header))
            try {
                importer.importHeader()
            } finally {
                try {
                    importer.close()
                } catch (ignore: ImportException) {
                }
            }
            return JSONDataStreamReader(zipFile.getInputStream(header)).use { count(it) }
        } catch (e: ZipException) {
            throw ImportException(e.message)
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        } catch (e: IllegalStateException) {
            throw ImportException(e.message)
        }
    }

    private fun count(reader: JSONDataStreamReader): BackupCount {
        if (JSONDatabase.Header.OBJECT != reader.readName()) {
            throw ImportException("Wrong object name (expected = 'header')")
        }
        val version = reader.readObject().getInt(JSONDatabase.Header.VERSION_CODE)
        val factory = JSONDataInputFactory()
        if (version >= 2) {
            count(reader, JSONDatabase.Currency.ARRAY)
        }
        val wallets = count(reader, JSONDatabase.Wallet.ARRAY) { !factory.getWallet(it).mDeleted }
        count(reader, JSONDatabase.Category.ARRAY)
        count(reader, JSONDatabase.Event.ARRAY)
        count(reader, JSONDatabase.Place.ARRAY)
        count(reader, JSONDatabase.Person.ARRAY)
        count(reader, JSONDatabase.EventPeople.ARRAY)
        count(reader, JSONDatabase.Debt.ARRAY)
        count(reader, JSONDatabase.DebtPeople.ARRAY)
        count(reader, JSONDatabase.Budget.ARRAY)
        count(reader, JSONDatabase.BudgetWallet.ARRAY)
        if (version >= 3) {
            count(reader, JSONDatabase.BudgetCategory.ARRAY)
        }
        if (version >= 4) {
            count(reader, JSONDatabase.CategoryRule.ARRAY)
        }
        count(reader, JSONDatabase.Saving.ARRAY)
        count(reader, JSONDatabase.RecurrentTransaction.ARRAY)
        count(reader, JSONDatabase.RecurrentTransfer.ARRAY)
        var transactions = count(reader, JSONDatabase.Transaction.ARRAY) {
            val transaction = factory.getTransaction(it)
            !transaction.mDeleted && transaction.mType != Contract.TransactionType.TRANSFER
        }
        if (version >= 5) {
            count(reader, JSONDatabase.TransactionSplit.ARRAY)
        }
        count(reader, JSONDatabase.TransactionPeople.ARRAY)
        count(reader, JSONDatabase.TransactionModel.ARRAY)
        transactions += count(reader, JSONDatabase.Transfer.ARRAY) { !factory.getTransfer(it).mDeleted }
        count(reader, JSONDatabase.TransferPeople.ARRAY)
        count(reader, JSONDatabase.TransferModel.ARRAY)
        count(reader, JSONDatabase.Attachment.ARRAY)
        count(reader, JSONDatabase.TransactionAttachment.ARRAY, "transaction_attachments")
        count(reader, JSONDatabase.TransferAttachment.ARRAY, "transfer_attachments")
        reader.endObject()
        return BackupCount(transactions, wallets)
    }

    private fun count(reader: JSONDataStreamReader, array: String, expected: String = array,
                      counted: (JSONObject) -> Boolean = { false }): Int {
        if (array != reader.readName()) {
            throw ImportException("Wrong array name (expected = '$expected')")
        }
        var count = 0
        reader.beginArray()
        while (reader.hasArrayAnotherObject()) {
            if (counted(reader.readObject())) {
                count++
            }
        }
        reader.endArray()
        return count
    }
}
