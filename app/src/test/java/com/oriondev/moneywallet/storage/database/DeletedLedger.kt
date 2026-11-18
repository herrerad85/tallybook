package com.oriondev.moneywallet.storage.database

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri

import com.oriondev.moneywallet.model.Attachment

import org.apache.commons.io.FileUtils

import java.io.File
import java.nio.charset.StandardCharsets
import java.time.LocalDate

class DeletedLedger(private val context: Context) {

    companion object {

        const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"

        val DATE = LocalDate.now().minusDays(1).toString() + " 12:00:00"
    }

    val resolver = context.contentResolver!!

    val attachmentFolder = File(context.getExternalFilesDir(null), Attachment.FOLDER_NAME)

    fun database(): SQLiteDatabase = SQLDatabase.getShared(context).writableDatabase

    fun insertWallet(name: String, currency: String): Long = insert(DataContentProvider.CONTENT_WALLETS, ContentValues().apply {
        put(Contract.Wallet.NAME, name)
        put(Contract.Wallet.ICON, ICON)
        put(Contract.Wallet.CURRENCY, currency)
        put(Contract.Wallet.START_MONEY, 0L)
        put(Contract.Wallet.COUNT_IN_TOTAL, true)
        put(Contract.Wallet.ARCHIVED, false)
    })

    fun insertCategory(name: String, type: Contract.CategoryType): Long = insert(DataContentProvider.CONTENT_CATEGORIES, ContentValues().apply {
        put(Contract.Category.NAME, name)
        put(Contract.Category.ICON, ICON)
        put(Contract.Category.TYPE, type.value)
        put(Contract.Category.SHOW_REPORT, true)
    })

    fun insertPerson(name: String): Long = insert(DataContentProvider.CONTENT_PEOPLE, ContentValues().apply {
        put(Contract.Person.NAME, name)
        put(Contract.Person.ICON, ICON)
    })

    fun insertPlace(name: String): Long = insert(DataContentProvider.CONTENT_PLACES, ContentValues().apply {
        put(Contract.Place.NAME, name)
        put(Contract.Place.ICON, ICON)
    })

    fun insertEvent(name: String): Long = insert(DataContentProvider.CONTENT_EVENTS, ContentValues().apply {
        put(Contract.Event.NAME, name)
        put(Contract.Event.ICON, ICON)
        put(Contract.Event.START_DATE, LocalDate.now().minusDays(10).toString())
        put(Contract.Event.END_DATE, LocalDate.now().plusDays(10).toString())
    })

    fun insertAttachment(file: String): Long {
        FileUtils.writeStringToFile(File(attachmentFolder, file), "content of $file", StandardCharsets.UTF_8)
        return insert(DataContentProvider.CONTENT_ATTACHMENTS, ContentValues().apply {
            put(Contract.Attachment.FILE, file)
            put(Contract.Attachment.NAME, "$file.pdf")
            put(Contract.Attachment.TYPE, "application/pdf")
            put(Contract.Attachment.SIZE, 15L)
        })
    }

    fun insertDebt(wallet: Long, money: Long, people: List<Long>): Long = insert(DataContentProvider.CONTENT_DEBTS, ContentValues().apply {
        put(Contract.Debt.TYPE, Contract.DebtType.DEBT.value)
        put(Contract.Debt.ICON, ICON)
        put(Contract.Debt.DESCRIPTION, "Loan")
        put(Contract.Debt.DATE, LocalDate.now().minusDays(2).toString())
        put(Contract.Debt.WALLET_ID, wallet)
        put(Contract.Debt.MONEY, money)
        put(Contract.Debt.ARCHIVED, false)
        put(Contract.Debt.PEOPLE_IDS, ids(people))
        put(Contract.Debt.INSERT_MASTER_TRANSACTION, true)
    })

    fun insertSaving(wallet: Long): Long = insert(DataContentProvider.CONTENT_SAVINGS, ContentValues().apply {
        put(Contract.Saving.DESCRIPTION, "Holiday")
        put(Contract.Saving.ICON, ICON)
        put(Contract.Saving.START_MONEY, 0L)
        put(Contract.Saving.END_MONEY, 100000L)
        put(Contract.Saving.WALLET_ID, wallet)
        put(Contract.Saving.COMPLETE, false)
    })

    fun insertBudget(category: Long, wallet: Long): Long {
        database().execSQL("INSERT INTO budgets (budget_type, budget_category, budget_start_date, budget_end_date, " +
                "budget_money, budget_currency, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, 100000, 'EUR', " +
                "'budget_groceries', 1, 0)", arrayOf<Any>(Schema.BudgetType.CATEGORY, category,
                LocalDate.now().minusDays(10).toString(), LocalDate.now().plusDays(10).toString()))
        val budget = longOf("SELECT budget_id FROM budgets WHERE uuid = 'budget_groceries'")
        database().execSQL("INSERT INTO budget_wallets (_budget, _wallet, uuid, last_edit, deleted) VALUES (?, ?, " +
                "'budget_wallet', 1, 0)", arrayOf<Any>(budget, wallet))
        database().execSQL("INSERT INTO budget_categories (_budget, _category, uuid, last_edit, deleted) VALUES " +
                "(?, ?, 'budget_category', 1, 0)", arrayOf<Any>(budget, category))
        return budget
    }

    fun transaction(wallet: Long, category: Long, money: Long): ContentValues = ContentValues().apply {
        put(Contract.Transaction.MONEY, money)
        put(Contract.Transaction.DATE, DATE)
        put(Contract.Transaction.DESCRIPTION, "Receipt")
        put(Contract.Transaction.CATEGORY_ID, category)
        put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE)
        put(Contract.Transaction.TYPE, Contract.TransactionType.STANDARD)
        put(Contract.Transaction.WALLET_ID, wallet)
        put(Contract.Transaction.NOTE, "Paid in cash")
        put(Contract.Transaction.CONFIRMED, true)
        put(Contract.Transaction.COUNT_IN_TOTAL, true)
    }

    fun insertTransaction(values: ContentValues): Long = insert(DataContentProvider.CONTENT_TRANSACTIONS, values)

    fun insertSplitTransaction(wallet: Long, category: Long, partCategories: List<Long>, people: List<Long>,
                               attachments: List<Long>, event: Long?): Long {
        val parts = partCategories.mapIndexed { index, part -> SplitPartsCodec.Part(part, 300L + index, "part $index") }
        return insertTransaction(transaction(wallet, category, 1000L).apply {
            put(Contract.Transaction.PEOPLE_IDS, ids(people))
            put(Contract.Transaction.ATTACHMENT_IDS, ids(attachments))
            put(Contract.Transaction.SPLIT_PARTS, SplitPartsCodec.encode(parts))
            put(Contract.Transaction.EVENT_ID, event)
        })
    }

    fun insertTransfer(from: Long, to: Long, fee: Long?, people: List<Long>, attachments: List<Long>,
                       event: Long?): Long = insert(DataContentProvider.CONTENT_TRANSFERS,
            transfer(from, to, fee, people, attachments, event))

    fun transfer(from: Long, to: Long, fee: Long?, people: List<Long>, attachments: List<Long>,
                 event: Long?): ContentValues = ContentValues().apply {
        put(Contract.Transfer.DESCRIPTION, "Move")
        put(Contract.Transfer.DATE, DATE)
        put(Contract.Transfer.TRANSACTION_FROM_WALLET_ID, from)
        put(Contract.Transfer.TRANSACTION_FROM_MONEY, 5000L)
        put(Contract.Transfer.TRANSACTION_TO_WALLET_ID, to)
        put(Contract.Transfer.TRANSACTION_TO_MONEY, 5000L)
        put(Contract.Transfer.TRANSACTION_TAX_WALLET_ID, fee ?: from)
        put(Contract.Transfer.TRANSACTION_TAX_MONEY, if (fee != null) 150L else 0L)
        put(Contract.Transfer.NOTE, "Monthly")
        put(Contract.Transfer.EVENT_ID, event)
        put(Contract.Transfer.CONFIRMED, true)
        put(Contract.Transfer.COUNT_IN_TOTAL, true)
        put(Contract.Transfer.PEOPLE_IDS, ids(people))
        put(Contract.Transfer.ATTACHMENT_IDS, ids(attachments))
    }

    fun systemCategory(tag: String): Long = longOf("SELECT category_id FROM categories WHERE category_tag = '$tag'")

    fun flagTransactionDeleted(transaction: Long, deletedAt: Long? = null): Int =
            resolver.delete(Uri.withAppendedPath(transactionUri(transaction), "deleted"), null,
                    deletedAt?.let { arrayOf(it.toString()) })

    fun flagTransferDeleted(transfer: Long, deletedAt: Long? = null): Int =
            resolver.delete(Uri.withAppendedPath(transferUri(transfer), "deleted"), null,
                    deletedAt?.let { arrayOf(it.toString()) })

    fun restoreTransaction(transaction: Long): Int =
            resolver.update(Uri.withAppendedPath(transactionUri(transaction), "restore"), ContentValues(), null, null)

    fun restoreTransfer(transfer: Long): Int =
            resolver.update(Uri.withAppendedPath(transferUri(transfer), "restore"), ContentValues(), null, null)

    fun deleteTransaction(transaction: Long): Int = resolver.delete(transactionUri(transaction), null, null)

    fun deleteTransfer(transfer: Long): Int = resolver.delete(transferUri(transfer), null, null)

    fun deleteFlaggedItems(deletedAt: Long): Int =
            resolver.delete(Uri.withAppendedPath(DataContentProvider.CONTENT_ALL, "deleted"), null,
                    arrayOf(deletedAt.toString()))

    fun transactionUri(transaction: Long): Uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, transaction)

    fun transferUri(transfer: Long): Uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSFERS, transfer)

    fun legsOf(transfer: Long): List<Long> = rows("transfers", "transfer_id = $transfer").single().let { row ->
        listOf("transfer_transaction_from", "transfer_transaction_to", "transfer_transaction_tax").mapNotNull { row[it] as Long? }
    }

    fun transactionRows(transaction: Long): List<Map<String, Any?>> =
            rows("transactions", "transaction_id = $transaction") +
                    rows("transaction_people", "_transaction = $transaction") +
                    rows("transaction_splits", "_transaction = $transaction") +
                    rows("transaction_attachment", "_transaction = $transaction") +
                    rows("attachments", "attachment_id IN (SELECT _attachment FROM transaction_attachment WHERE " +
                            "_transaction = $transaction)")

    fun transferRows(transfer: Long): List<Map<String, Any?>> =
            rows("transfers", "transfer_id = $transfer") +
                    rows("transfer_people", "_transfer = $transfer") +
                    rows("transfer_attachment", "_transfer = $transfer") +
                    rows("attachments", "attachment_id IN (SELECT _attachment FROM transfer_attachment WHERE " +
                            "_transfer = $transfer)") +
                    legsOf(transfer).flatMap { transactionRows(it) }

    fun deletedOf(rows: List<Map<String, Any?>>): Set<Any?> = rows.map { it["deleted"] }.toSet()

    fun count(table: String, where: String): Long = longOf("SELECT COUNT(*) FROM $table WHERE $where")

    fun rows(table: String, where: String): List<Map<String, Any?>> =
            read(database().rawQuery("SELECT * FROM $table WHERE $where ORDER BY rowid", null))

    fun view(uri: Uri, selection: String? = null, vararg selectionArgs: String): List<Map<String, Any?>> =
            read(resolver.query(uri, null, selection, if (selectionArgs.isEmpty()) null else arrayOf(*selectionArgs), null)!!)

    fun viewValue(uri: Uri, column: String, selection: String, vararg selectionArgs: String): Any? =
            view(uri, selection, *selectionArgs).single()[column]

    fun ids(ids: List<Long>): String = ids.joinToString(",") { "<$it>" }

    fun idsOf(ids: Any?): Set<Long> = Regex("<(\\d+)>").findAll(ids as String? ?: "").map { it.groupValues[1].toLong() }.toSet()

    private fun longOf(query: String): Long = database().rawQuery(query, null).use {
        check(it.moveToFirst()) { query }
        it.getLong(0)
    }

    private fun insert(uri: Uri, values: ContentValues): Long = ContentUris.parseId(resolver.insert(uri, values)!!)

    private fun read(cursor: Cursor): List<Map<String, Any?>> = cursor.use {
        val rows = mutableListOf<Map<String, Any?>>()
        while (it.moveToNext()) {
            rows.add(it.columnNames.indices.associate { index ->
                it.getColumnName(index) to when (it.getType(index)) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_INTEGER -> it.getLong(index)
                    Cursor.FIELD_TYPE_FLOAT -> it.getDouble(index)
                    else -> it.getString(index)
                }
            })
        }
        rows
    }
}
