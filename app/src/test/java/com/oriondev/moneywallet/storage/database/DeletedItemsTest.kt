package com.oriondev.moneywallet.storage.database

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri

import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.storage.preference.PreferenceManager

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeletedItemsTest {

    companion object {

        private const val DELETED_AT = 1765000000000L
    }

    private lateinit var ledger: DeletedLedger
    private var cash = 0L
    private var bank = 0L
    private var groceries = 0L

    private val deletedUri = Uri.withAppendedPath(DataContentProvider.CONTENT_ALL, "deleted")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.initialize(context)
        TestDatabases.useFreshDatabase(context)
        ledger = DeletedLedger(context)
        cash = ledger.insertWallet("Cash", "EUR")
        bank = ledger.insertWallet("Bank", "EUR")
        groceries = ledger.insertCategory("Groceries", Contract.CategoryType.EXPENSE)
    }

    @Test
    fun theListHoldsEachDeletedItemOnceNewestFirst() {
        ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val first = ledger.insertTransaction(ledger.transaction(cash, groceries, 200L))
        val second = ledger.insertTransaction(ledger.transaction(bank, groceries, 300L))
        val transfer = ledger.insertTransfer(cash, bank, cash, emptyList(), emptyList(), null)
        val old = ledger.insertWallet("Old", "EUR")
        val inOldWallet = ledger.insertTransaction(ledger.transaction(old, groceries, 400L))
        ledger.flagTransactionDeleted(first, DELETED_AT + 2)
        ledger.flagTransactionDeleted(second, DELETED_AT + 2)
        ledger.flagTransferDeleted(transfer, DELETED_AT + 1)
        ledger.flagTransactionDeleted(inOldWallet, DELETED_AT + 3)
        ledger.database().execSQL("UPDATE wallets SET deleted = 1 WHERE wallet_id = $old")

        val listed = ledger.view(deletedUri)

        assertEquals(listOf(
                listOf(Contract.DeletedItemType.TRANSACTION.toLong(), second, DELETED_AT + 2),
                listOf(Contract.DeletedItemType.TRANSACTION.toLong(), first, DELETED_AT + 2),
                listOf(Contract.DeletedItemType.TRANSFER.toLong(), transfer, DELETED_AT + 1)),
                listed.map { listOf(it[Contract.DeletedItem.TYPE], it[Contract.DeletedItem.ID], it[Contract.DeletedItem.DELETED_AT]) })
        assertEquals(listOf(second, first, null), listed.map { it[Contract.Transaction.ID] })
        assertEquals(listOf("Groceries", "Groceries", null), listed.map { it[Contract.Transaction.CATEGORY_NAME] })
        assertEquals(listOf(300L, 200L, null), listed.map { it[Contract.Transaction.MONEY] })
        assertEquals(listOf(null, null, transfer), listed.map { it[Contract.Transfer.ID] })
        assertEquals(listOf(null, null, "Cash"), listed.map { it[Contract.Transfer.TRANSACTION_FROM_WALLET_NAME] })
        assertEquals(listOf(null, null, "Bank"), listed.map { it[Contract.Transfer.TRANSACTION_TO_WALLET_NAME] })
        assertEquals(listOf(null, null, 150L), listed.map { it[Contract.Transfer.TRANSACTION_TAX_MONEY] })
    }

    @Test
    fun aRestoredItemIsNotDeletedForever() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val transfer = ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null)
        ledger.flagTransactionDeleted(transaction, DELETED_AT)
        ledger.flagTransferDeleted(transfer, DELETED_AT)
        ledger.restoreTransaction(transaction)
        ledger.restoreTransfer(transfer)
        val transactionRows = ledger.transactionRows(transaction)
        val transferRows = ledger.transferRows(transfer)

        assertEquals(0, ledger.resolver.delete(deletedItemUri(DataContentProvider.CONTENT_TRANSACTIONS, transaction), null, null))
        assertEquals(0, ledger.resolver.delete(deletedItemUri(DataContentProvider.CONTENT_TRANSFERS, transfer), null, null))

        assertEquals(transactionRows, ledger.transactionRows(transaction))
        assertEquals(transferRows, ledger.transferRows(transfer))
        assertEquals(setOf<Any?>(0L), ledger.deletedOf(transactionRows + transferRows))
    }

    @Test
    fun deletingOneItemForeverRemovesEveryRowOfItAndNothingElse() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val other = ledger.insertTransaction(ledger.transaction(cash, groceries, 200L))
        val transfer = ledger.insertTransfer(cash, bank, cash, emptyList(), emptyList(), null)
        val legs = ledger.legsOf(transfer)
        ledger.flagTransactionDeleted(transaction, DELETED_AT)
        ledger.flagTransactionDeleted(other, DELETED_AT)
        ledger.flagTransferDeleted(transfer, DELETED_AT)
        val otherRows = ledger.transactionRows(other)

        assertEquals(1, ledger.resolver.delete(deletedItemUri(DataContentProvider.CONTENT_TRANSACTIONS, transaction), null, null))
        assertEquals(1, ledger.resolver.delete(deletedItemUri(DataContentProvider.CONTENT_TRANSFERS, transfer), null, null))

        assertEquals(emptyList<Map<String, Any?>>(), ledger.transactionRows(transaction))
        assertEquals(0L, ledger.count("transfers", "transfer_id = $transfer"))
        assertEquals(0L, ledger.count("transactions", "transaction_id IN (${legs.joinToString(",")})"))
        assertEquals(otherRows, ledger.transactionRows(other))
    }

    @Test
    fun aTransferLegIsNotDeletedForeverOnItsOwn() {
        val transfer = ledger.insertTransfer(cash, bank, cash, emptyList(), emptyList(), null)
        ledger.flagTransferDeleted(transfer, DELETED_AT)
        val rows = ledger.transferRows(transfer)

        for (leg in ledger.legsOf(transfer)) {
            assertEquals(0, ledger.resolver.delete(deletedItemUri(DataContentProvider.CONTENT_TRANSACTIONS, leg), null, null))
        }

        assertEquals(rows, ledger.transferRows(transfer))
        assertEquals(setOf<Any?>(DELETED_AT), ledger.deletedOf(rows))
    }

    @Test
    fun aPerItemDeleteThatRemovesNothingAnnouncesNothing() {
        val live = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val liveTransfer = ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null)
        val transfer = ledger.insertTransfer(bank, cash, null, emptyList(), emptyList(), null)
        ledger.flagTransferDeleted(transfer, DELETED_AT)
        val leg = ledger.legsOf(transfer).first()
        val rows = everyRow()
        val changed = PreferenceManager.getLastTimeDataIsChanged()
        val announced = mutableListOf<Uri?>()
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                announced.add(uri)
            }
        }
        PreferenceManager.setLastTimeDataIsChanged(1L)
        ledger.resolver.registerContentObserver(DataContentProvider.CONTENT_ALL, true, observer)
        try {
            for (items in listOf(DataContentProvider.CONTENT_TRANSACTIONS, DataContentProvider.CONTENT_TRANSFERS)) {
                for (id in listOf(live, liveTransfer, leg, 999999L)) {
                    assertEquals("$items $id", 0, ledger.resolver.delete(deletedItemUri(items, id), null, null))
                }
            }
            assertEquals(emptyList<Uri?>(), announced)
            assertEquals(1L, PreferenceManager.getLastTimeDataIsChanged())
        } finally {
            ledger.resolver.unregisterContentObserver(observer)
            PreferenceManager.setLastTimeDataIsChanged(changed)
        }
        assertEquals(rows, everyRow())
    }

    @Test
    fun aPerItemDeleteWithATimeRemovesOnlyAnItemThatStillCarriesIt() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val transfer = ledger.insertTransfer(cash, bank, cash, emptyList(), emptyList(), null)
        val legs = ledger.legsOf(transfer)
        ledger.flagTransactionDeleted(transaction, DELETED_AT)
        ledger.flagTransferDeleted(transfer, DELETED_AT)
        val rows = everyRow()

        for (deletedAt in listOf((DELETED_AT + 1).toString(), "later", "0", "-1")) {
            assertEquals(deletedAt, 0, ledger.resolver.delete(deletedItemUri(DataContentProvider.CONTENT_TRANSACTIONS, transaction),
                    null, arrayOf(deletedAt)))
            assertEquals(deletedAt, 0, ledger.resolver.delete(deletedItemUri(DataContentProvider.CONTENT_TRANSFERS, transfer),
                    null, arrayOf(deletedAt)))
        }
        assertEquals(rows, everyRow())

        assertEquals(1, ledger.resolver.delete(deletedItemUri(DataContentProvider.CONTENT_TRANSACTIONS, transaction),
                null, arrayOf(DELETED_AT.toString())))
        assertEquals(1, ledger.resolver.delete(deletedItemUri(DataContentProvider.CONTENT_TRANSFERS, transfer),
                null, arrayOf(DELETED_AT.toString())))

        assertEquals(emptyList<Map<String, Any?>>(), ledger.transactionRows(transaction))
        assertEquals(0L, ledger.count("transfers", "transfer_id = $transfer"))
        assertEquals(0L, ledger.count("transactions", "transaction_id IN (${legs.joinToString(",")})"))
    }

    @Test
    fun aRestoreWithATimeRestoresOnlyAnItemThatStillCarriesIt() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val transfer = ledger.insertTransfer(cash, bank, cash, emptyList(), emptyList(), null)
        ledger.flagTransactionDeleted(transaction, DELETED_AT)
        ledger.flagTransferDeleted(transfer, DELETED_AT)
        val rows = everyRow()

        for (deletedAt in listOf((DELETED_AT + 1).toString(), "later", "0", "-1")) {
            assertEquals(deletedAt, 0, ledger.resolver.update(restoreUri(ledger.transactionUri(transaction)),
                    ContentValues(), null, arrayOf(deletedAt)))
            assertEquals(deletedAt, 0, ledger.resolver.update(restoreUri(ledger.transferUri(transfer)),
                    ContentValues(), null, arrayOf(deletedAt)))
        }
        assertEquals(rows, everyRow())

        assertEquals(1, ledger.resolver.update(restoreUri(ledger.transactionUri(transaction)), ContentValues(), null,
                arrayOf(DELETED_AT.toString())))
        assertEquals(1, ledger.resolver.update(restoreUri(ledger.transferUri(transfer)), ContentValues(), null,
                arrayOf(DELETED_AT.toString())))

        assertEquals(setOf<Any?>(0L), ledger.deletedOf(ledger.transactionRows(transaction) + ledger.transferRows(transfer)))
    }

    @Test
    fun theListCarriesTheUuidOfEachItem() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val transfer = ledger.insertTransfer(cash, bank, cash, emptyList(), emptyList(), null)
        ledger.flagTransactionDeleted(transaction, DELETED_AT + 1)
        ledger.flagTransferDeleted(transfer, DELETED_AT)

        val listed = ledger.view(deletedUri)

        assertEquals(listOf(transaction, transfer), listed.map { it[Contract.DeletedItem.ID] })
        assertEquals(listOf(uuidOf("transactions", "transaction_id", transaction), uuidOf("transfers", "transfer_id", transfer)),
                listed.map { it[Contract.DeletedItem.UUID] })
        assertEquals(2, listed.mapNotNull { it[Contract.DeletedItem.UUID] }.toSet().size)
    }

    @Test
    fun aPerItemDeleteWithAUuidRemovesOnlyTheItemThatCarriesIt() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val timeOnly = ledger.insertTransaction(ledger.transaction(cash, groceries, 200L))
        val noArgs = ledger.insertTransaction(ledger.transaction(cash, groceries, 300L))
        val transfer = ledger.insertTransfer(cash, bank, cash, emptyList(), emptyList(), null)
        val transferTimeOnly = ledger.insertTransfer(bank, cash, null, emptyList(), emptyList(), null)
        val transferNoArgs = ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null)
        val legs = ledger.legsOf(transfer)
        for (id in listOf(transaction, timeOnly, noArgs)) {
            ledger.flagTransactionDeleted(id, DELETED_AT)
        }
        for (id in listOf(transfer, transferTimeOnly, transferNoArgs)) {
            ledger.flagTransferDeleted(id, DELETED_AT)
        }
        val transactionUri = deletedItemUri(DataContentProvider.CONTENT_TRANSACTIONS, transaction)
        val transferUri = deletedItemUri(DataContentProvider.CONTENT_TRANSFERS, transfer)
        val rows = everyRow()

        for (uuid in listOf(uuidOf("transactions", "transaction_id", timeOnly), uuidOf("transfers", "transfer_id", transfer), "", null)) {
            assertEquals("$uuid", 0, ledger.resolver.delete(transactionUri, null, arrayOf(DELETED_AT.toString(), uuid)))
        }
        for (uuid in listOf(uuidOf("transfers", "transfer_id", transferTimeOnly), uuidOf("transactions", "transaction_id", transaction), "", null)) {
            assertEquals("$uuid", 0, ledger.resolver.delete(transferUri, null, arrayOf(DELETED_AT.toString(), uuid)))
        }
        assertEquals(rows, everyRow())

        assertEquals(1, ledger.resolver.delete(transactionUri, null,
                arrayOf(DELETED_AT.toString(), uuidOf("transactions", "transaction_id", transaction))))
        assertEquals(1, ledger.resolver.delete(transferUri, null,
                arrayOf(DELETED_AT.toString(), uuidOf("transfers", "transfer_id", transfer))))
        assertEquals(emptyList<Map<String, Any?>>(), ledger.transactionRows(transaction))
        assertEquals(0L, ledger.count("transfers", "transfer_id = $transfer"))
        assertEquals(0L, ledger.count("transactions", "transaction_id IN (${legs.joinToString(",")})"))
        assertEquals(setOf<Any?>(DELETED_AT), ledger.deletedOf(ledger.transactionRows(timeOnly) + ledger.transactionRows(noArgs) +
                ledger.transferRows(transferTimeOnly) + ledger.transferRows(transferNoArgs)))

        assertEquals(1, ledger.resolver.delete(deletedItemUri(DataContentProvider.CONTENT_TRANSACTIONS, timeOnly), null,
                arrayOf(DELETED_AT.toString())))
        assertEquals(1, ledger.resolver.delete(deletedItemUri(DataContentProvider.CONTENT_TRANSFERS, transferTimeOnly), null,
                arrayOf(DELETED_AT.toString())))
        assertEquals(1, ledger.resolver.delete(deletedItemUri(DataContentProvider.CONTENT_TRANSACTIONS, noArgs), null, null))
        assertEquals(1, ledger.resolver.delete(deletedItemUri(DataContentProvider.CONTENT_TRANSFERS, transferNoArgs), null, null))
        assertEquals(0L, ledger.count("transactions", "transaction_id IN ($timeOnly, $noArgs)"))
        assertEquals(0L, ledger.count("transfers", "transfer_id IN ($transferTimeOnly, $transferNoArgs)"))
    }

    @Test
    fun aRestoreWithAUuidRestoresOnlyTheItemThatCarriesIt() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val timeOnly = ledger.insertTransaction(ledger.transaction(cash, groceries, 200L))
        val noArgs = ledger.insertTransaction(ledger.transaction(cash, groceries, 300L))
        val transfer = ledger.insertTransfer(cash, bank, cash, emptyList(), emptyList(), null)
        val transferTimeOnly = ledger.insertTransfer(bank, cash, null, emptyList(), emptyList(), null)
        val transferNoArgs = ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null)
        for (id in listOf(transaction, timeOnly, noArgs)) {
            ledger.flagTransactionDeleted(id, DELETED_AT)
        }
        for (id in listOf(transfer, transferTimeOnly, transferNoArgs)) {
            ledger.flagTransferDeleted(id, DELETED_AT)
        }
        val transactionUri = restoreUri(ledger.transactionUri(transaction))
        val transferUri = restoreUri(ledger.transferUri(transfer))
        val rows = everyRow()

        for (uuid in listOf(uuidOf("transactions", "transaction_id", timeOnly), uuidOf("transfers", "transfer_id", transfer), "", null)) {
            assertEquals("$uuid", 0, ledger.resolver.update(transactionUri, ContentValues(), null, arrayOf(DELETED_AT.toString(), uuid)))
        }
        for (uuid in listOf(uuidOf("transfers", "transfer_id", transferTimeOnly), uuidOf("transactions", "transaction_id", transaction), "", null)) {
            assertEquals("$uuid", 0, ledger.resolver.update(transferUri, ContentValues(), null, arrayOf(DELETED_AT.toString(), uuid)))
        }
        assertEquals(rows, everyRow())

        assertEquals(1, ledger.resolver.update(transactionUri, ContentValues(), null,
                arrayOf(DELETED_AT.toString(), uuidOf("transactions", "transaction_id", transaction))))
        assertEquals(1, ledger.resolver.update(transferUri, ContentValues(), null,
                arrayOf(DELETED_AT.toString(), uuidOf("transfers", "transfer_id", transfer))))
        assertEquals(setOf<Any?>(0L), ledger.deletedOf(ledger.transactionRows(transaction) + ledger.transferRows(transfer)))
        assertEquals(setOf<Any?>(DELETED_AT), ledger.deletedOf(ledger.transactionRows(timeOnly) + ledger.transactionRows(noArgs) +
                ledger.transferRows(transferTimeOnly) + ledger.transferRows(transferNoArgs)))

        assertEquals(1, ledger.resolver.update(restoreUri(ledger.transactionUri(timeOnly)), ContentValues(), null,
                arrayOf(DELETED_AT.toString())))
        assertEquals(1, ledger.resolver.update(restoreUri(ledger.transferUri(transferTimeOnly)), ContentValues(), null,
                arrayOf(DELETED_AT.toString())))
        assertEquals(1, ledger.restoreTransaction(noArgs))
        assertEquals(1, ledger.restoreTransfer(transferNoArgs))
        assertEquals(setOf<Any?>(0L), ledger.deletedOf(ledger.transactionRows(timeOnly) + ledger.transactionRows(noArgs) +
                ledger.transferRows(transferTimeOnly) + ledger.transferRows(transferNoArgs)))
    }

    private fun uuidOf(table: String, idColumn: String, id: Long): String =
            ledger.rows(table, "$idColumn = $id").single()["uuid"] as String

    private fun everyRow(): List<Map<String, Any?>> = listOf("transactions", "transaction_people", "transaction_splits",
            "transaction_attachment", "transfers", "transfer_people", "transfer_attachment").flatMap { ledger.rows(it, "1") }

    private fun restoreUri(itemUri: Uri): Uri = Uri.withAppendedPath(itemUri, "restore")

    private fun deletedItemUri(items: Uri, id: Long): Uri =
            ContentUris.withAppendedId(Uri.withAppendedPath(items, "deleted"), id)
}
