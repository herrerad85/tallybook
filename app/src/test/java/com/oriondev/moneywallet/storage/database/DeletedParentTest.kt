package com.oriondev.moneywallet.storage.database

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri

import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.storage.preference.PreferenceManager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.io.File

@RunWith(RobolectricTestRunner::class)
class DeletedParentTest {

    companion object {

        private const val DELETED_AT = 1765000000000L
    }

    private lateinit var ledger: DeletedLedger
    private var cash = 0L
    private var bank = 0L
    private var groceries = 0L
    private var household = 0L
    private var ann = 0L
    private var bob = 0L

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        PreferenceManager.initialize(context)
        TestDatabases.useFreshDatabase(context)
        ledger = DeletedLedger(context)
        cash = ledger.insertWallet("Cash", "EUR")
        bank = ledger.insertWallet("Bank", "EUR")
        groceries = ledger.insertCategory("Groceries", Contract.CategoryType.EXPENSE)
        household = ledger.insertCategory("Household", Contract.CategoryType.EXPENSE)
        ann = ledger.insertPerson("Ann")
        bob = ledger.insertPerson("Bob")
    }

    @Test
    fun deletingACategoryDeletesForGoodTheDeletedTransactionsFiledUnderIt() {
        val transaction = insertDeleted(ledger.transaction(cash, household, 900L), "receipt")

        assertEquals(1, delete(DataContentProvider.CONTENT_CATEGORIES, household))

        assertGone(transaction, "receipt")
    }

    @Test
    fun deletingTheCategoryOfAPartDeletesForGoodTheDeletedTransactionHoldingIt() {
        val transaction = ledger.insertSplitTransaction(cash, groceries, listOf(household), listOf(ann),
                listOf(ledger.insertAttachment("receipt")), null)
        ledger.flagTransactionDeleted(transaction, DELETED_AT)

        assertEquals(1, delete(DataContentProvider.CONTENT_CATEGORIES, household))

        assertGone(transaction, "receipt")
    }

    @Test
    fun aRefusedCategoryDeleteLeavesTheDeletedTransactionAsItWas() {
        val transaction = insertDeleted(ledger.transaction(cash, household, 900L), "receipt")
        ledger.insertTransaction(ledger.transaction(cash, household, 100L))
        val rows = ledger.transactionRows(transaction)

        try {
            delete(DataContentProvider.CONTENT_CATEGORIES, household)
            fail("a category in use was deleted")
        } catch (e: SQLiteDataException) {
            assertEquals(Contract.ErrorCode.CATEGORY_IN_USE, e.errorCode)
        }

        assertEquals(rows, ledger.transactionRows(transaction))
    }

    @Test
    fun deletingAWalletDeletesForGoodItsDeletedItemsAndEveryLegOfADeletedTransfer() {
        val travel = ledger.insertWallet("Travel", "EUR")
        val transaction = insertDeleted(ledger.transaction(travel, groceries, 900L), "receipt")
        val transfer = ledger.insertTransfer(travel, bank, bank, listOf(ann), listOf(ledger.insertAttachment("slip")), null)
        val legs = ledger.legsOf(transfer)
        ledger.flagTransferDeleted(transfer, DELETED_AT)

        assertEquals(1, delete(DataContentProvider.CONTENT_WALLETS, travel))

        assertGone(transaction, "receipt")
        assertEquals(0L, ledger.count("transfers", "transfer_id = $transfer"))
        assertEquals(0L, ledger.count("transfer_people", "_transfer = $transfer"))
        assertEquals(0L, ledger.count("transactions", "transaction_id IN (${legs.joinToString()})"))
        assertEquals(0L, ledger.count("attachments", "1"))
        assertFalse(File(ledger.attachmentFolder, "slip").exists())
    }

    @Test
    fun aWalletInALiveTransferIsStillRefusedAndItsDeletedItemsAreLeft() {
        val transaction = insertDeleted(ledger.transaction(cash, groceries, 900L), "receipt")
        ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null)
        val rows = ledger.transactionRows(transaction)

        try {
            delete(DataContentProvider.CONTENT_WALLETS, cash)
            fail("a wallet in a transfer was deleted")
        } catch (e: SQLiteDataException) {
            assertEquals(Contract.ErrorCode.WALLET_USED_IN_TRANSFER, e.errorCode)
        }

        assertEquals(rows, ledger.transactionRows(transaction))
    }

    @Test
    fun deletingADebtDeletesForGoodItsDeletedTransactions() {
        val debt = ledger.insertDebt(cash, 2000L, listOf(ann))
        val master = ledger.rows("transactions", "transaction_debt = $debt").single()["transaction_id"] as Long
        ledger.flagTransactionDeleted(master, DELETED_AT)
        val payment = insertDeleted(ledger.transaction(cash, ledger.systemCategory(Contract.CategoryTag.PAID_DEBT), 500L).apply {
            put(Contract.Transaction.TYPE, Contract.TransactionType.DEBT)
            put(Contract.Transaction.DEBT_ID, debt)
        }, "receipt")

        assertEquals(1, delete(DataContentProvider.CONTENT_DEBTS, debt))

        assertGone(payment, "receipt")
        assertEquals(emptyList<Map<String, Any?>>(), ledger.transactionRows(master))
    }

    @Test
    fun deletingASavingDeletesForGoodItsDeletedTransactions() {
        val saving = ledger.insertSaving(cash)
        val deposit = insertDeleted(ledger.transaction(cash, ledger.systemCategory(Contract.CategoryTag.SAVING_DEPOSIT), 800L).apply {
            put(Contract.Transaction.TYPE, Contract.TransactionType.SAVING)
            put(Contract.Transaction.SAVING_ID, saving)
        }, "receipt")

        assertEquals(1, delete(DataContentProvider.CONTENT_SAVINGS, saving))

        assertGone(deposit, "receipt")
    }

    @Test
    fun aDeletedTransactionRestoredAfterItsPersonWasDeletedComesBackWithoutThem() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 900L).apply {
            put(Contract.Transaction.PEOPLE_IDS, ledger.ids(listOf(ann, bob)))
        })
        val row = ledger.rows("transactions", "transaction_id = $transaction")
        ledger.flagTransactionDeleted(transaction, DELETED_AT)

        assertEquals(1, delete(DataContentProvider.CONTENT_PEOPLE, ann))
        assertEquals(1, ledger.restoreTransaction(transaction))

        assertEquals(row, ledger.rows("transactions", "transaction_id = $transaction"))
        assertEquals(listOf(bob), ledger.view(Uri.withAppendedPath(ledger.transactionUri(transaction), "people"))
                .map { it[Contract.Person.ID] })
    }

    @Test
    fun aDeletedTransferRestoredAfterItsPersonWasDeletedComesBackWithoutThem() {
        val transfer = ledger.insertTransfer(cash, bank, null, listOf(ann, bob), emptyList(), null)
        ledger.flagTransferDeleted(transfer, DELETED_AT)

        assertEquals(1, delete(DataContentProvider.CONTENT_PEOPLE, ann))
        assertEquals(1, ledger.restoreTransfer(transfer))

        assertEquals(listOf(bob), ledger.view(Uri.withAppendedPath(ledger.transferUri(transfer), "people"))
                .map { it[Contract.Person.ID] })
    }

    @Test
    fun deletedItemsRestoredAfterTheirEventPlaceAndRecurrenceWereDeletedComeBackWithoutThem() {
        val event = ledger.insertEvent("Party")
        val place = ledger.insertPlace("Market")
        val recurrence = insertRecurrence()
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 900L).apply {
            put(Contract.Transaction.EVENT_ID, event)
            put(Contract.Transaction.PLACE_ID, place)
        })
        ledger.database().execSQL("UPDATE transactions SET transaction_recurrence = $recurrence WHERE " +
                "transaction_id = $transaction")
        val transfer = ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), event)
        val transactionRow = ledger.rows("transactions", "transaction_id = $transaction").single()
        val transferRow = ledger.rows("transfers", "transfer_id = $transfer").single()
        ledger.flagTransactionDeleted(transaction, DELETED_AT)
        ledger.flagTransferDeleted(transfer, DELETED_AT)

        assertEquals(1, delete(DataContentProvider.CONTENT_EVENTS, event))
        assertEquals(1, delete(DataContentProvider.CONTENT_PLACES, place))
        assertEquals(1, delete(DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS, recurrence))
        assertEquals(1, ledger.restoreTransaction(transaction))
        assertEquals(1, ledger.restoreTransfer(transfer))

        assertEquals(transactionRow - "last_edit" + mapOf("transaction_event" to null, "transaction_place" to null,
                "transaction_recurrence" to null), ledger.rows("transactions", "transaction_id = $transaction")
                .single() - "last_edit")
        assertEquals(transferRow - "last_edit" + mapOf("transfer_event" to null),
                ledger.rows("transfers", "transfer_id = $transfer").single() - "last_edit")
    }

    private fun insertRecurrence(): Long {
        ledger.database().execSQL("INSERT INTO recurrent_transactions (recurrent_transaction_money, " +
                "recurrent_transaction_description, recurrent_transaction_category, recurrent_transaction_direction, " +
                "recurrent_transaction_wallet, recurrent_transaction_start_date, recurrent_transaction_last_occurrence, " +
                "recurrent_transaction_rule, uuid, last_edit, deleted) VALUES (900, 'Rent', $groceries, " +
                "${Contract.Direction.EXPENSE}, $cash, '2026-01-01', '2026-01-01', 'FREQ=MONTHLY', 'recurrence', 1, 0)")
        return ledger.rows("recurrent_transactions", "uuid = 'recurrence'").single()["recurrent_transaction_id"] as Long
    }

    private fun insertDeleted(values: ContentValues, file: String): Long {
        val transaction = ledger.insertTransaction(values.apply {
            put(Contract.Transaction.PEOPLE_IDS, ledger.ids(listOf(ann)))
            put(Contract.Transaction.ATTACHMENT_IDS, "<${ledger.insertAttachment(file)}>")
        })
        assertEquals(1, ledger.flagTransactionDeleted(transaction, DELETED_AT))
        return transaction
    }

    private fun assertGone(transaction: Long, file: String) {
        assertEquals(emptyList<Map<String, Any?>>(), ledger.transactionRows(transaction))
        assertEquals(0L, ledger.count("transactions", "transaction_id = $transaction"))
        assertEquals(0L, ledger.count("attachments", "attachment_file = '$file'"))
        assertFalse(File(ledger.attachmentFolder, file).exists())
    }

    private fun delete(list: Uri, id: Long): Int = ledger.resolver.delete(ContentUris.withAppendedId(list, id), null, null)
}
