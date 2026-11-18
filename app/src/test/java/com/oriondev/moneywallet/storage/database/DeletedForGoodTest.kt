package com.oriondev.moneywallet.storage.database

import android.content.Context
import android.net.Uri

import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.storage.preference.PreferenceManager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.io.File

@RunWith(RobolectricTestRunner::class)
class DeletedForGoodTest {

    companion object {

        private const val DELETED_AT = 1765000000000L
    }

    private lateinit var context: Context
    private lateinit var ledger: DeletedLedger
    private var cash = 0L
    private var bank = 0L
    private var groceries = 0L
    private var household = 0L
    private var ann = 0L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.initialize(context)
        TestDatabases.useFreshDatabase(context)
        ledger = DeletedLedger(context)
        cash = ledger.insertWallet("Cash", "EUR")
        bank = ledger.insertWallet("Bank", "EUR")
        groceries = ledger.insertCategory("Groceries", Contract.CategoryType.EXPENSE)
        household = ledger.insertCategory("Household", Contract.CategoryType.EXPENSE)
        ann = ledger.insertPerson("Ann")
    }

    @Test
    fun aDeletedTransactionDeletedForGoodLeavesNoRowAndLosesItsFileOnTheCommit() {
        val transaction = ledger.insertSplitTransaction(cash, groceries, listOf(household), listOf(ann),
                listOf(ledger.insertAttachment("receipt")), null)
        val attachment = ledger.rows("transaction_attachment", "_transaction = $transaction").single()["_attachment"]
        ledger.flagTransactionDeleted(transaction, DELETED_AT)

        DataContentProvider.runInOneTransaction(context) {
            assertEquals(1, ledger.deleteTransaction(transaction))
            assertTrue(File(ledger.attachmentFolder, "receipt").exists())
        }

        assertEquals(emptyList<Map<String, Any?>>(), ledger.transactionRows(transaction))
        assertEquals(0L, ledger.count("attachments", "attachment_id = $attachment"))
        assertFalse(File(ledger.attachmentFolder, "receipt").exists())
    }

    @Test
    fun aDeletedTransferDeletedForGoodLeavesNoLeg() {
        val transfer = ledger.insertTransfer(cash, bank, bank, listOf(ann), listOf(ledger.insertAttachment("slip")), null)
        val legs = ledger.legsOf(transfer)
        ledger.flagTransferDeleted(transfer, DELETED_AT)

        ledger.deleteTransfer(transfer)

        assertEquals(0L, ledger.count("transfers", "transfer_id = $transfer"))
        assertEquals(0L, ledger.count("transfer_people", "_transfer = $transfer"))
        assertEquals(0L, ledger.count("transfer_attachment", "_transfer = $transfer"))
        assertEquals(0L, ledger.count("transactions", "transaction_id IN (${legs.joinToString()})"))
        assertEquals(0L, ledger.count("attachments", "1"))
        assertFalse(File(ledger.attachmentFolder, "slip").exists())
    }

    @Test
    fun theDeletedItemsUpToATimeAreDeletedForGoodAndTheLaterOnesAreLeft() {
        val early = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val transfer = ledger.insertTransfer(cash, bank, null, emptyList(), listOf(ledger.insertAttachment("slip")), null)
        val legs = ledger.legsOf(transfer)
        val late = ledger.insertTransaction(ledger.transaction(cash, groceries, 300L))
        val live = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        ledger.flagTransactionDeleted(early, DELETED_AT - 10)
        ledger.flagTransferDeleted(transfer, DELETED_AT)
        ledger.flagTransactionDeleted(late, DELETED_AT + 1)

        assertEquals(2, ledger.deleteFlaggedItems(DELETED_AT))

        assertEquals(0L, ledger.count("transactions", "transaction_id IN ($early, ${legs.joinToString()})"))
        assertEquals(0L, ledger.count("transfers", "transfer_id = $transfer"))
        assertFalse(File(ledger.attachmentFolder, "slip").exists())
        assertEquals(setOf<Any?>(DELETED_AT + 1), ledger.deletedOf(ledger.transactionRows(late)))
        assertEquals(setOf<Any?>(0L), ledger.deletedOf(ledger.transactionRows(live)))
    }

    @Test
    fun aFeeOfADeletedTransferCannotBeDeletedByItself() {
        val transfer = ledger.insertTransfer(cash, bank, bank, listOf(ann), emptyList(), null)
        val fee = ledger.legsOf(transfer)[2]
        ledger.flagTransferDeleted(transfer, DELETED_AT)
        val rows = ledger.transferRows(transfer)

        for (delete in listOf({ ledger.deleteTransaction(fee) }, { ledger.flagTransactionDeleted(fee) })) {
            try {
                delete()
                fail("a fee of a deleted transfer was deleted by itself")
            } catch (e: SQLiteDataException) {
                assertEquals(Contract.ErrorCode.TRANSACTION_USED_IN_TRANSFER, e.errorCode)
            }
        }

        assertEquals(rows, ledger.transferRows(transfer))
        assertEquals(1, ledger.restoreTransfer(transfer))
        assertEquals(3, ledger.legsOf(transfer).size)
        assertEquals(setOf<Any?>(0L), ledger.deletedOf(ledger.transferRows(transfer)))
    }

    @Test
    fun theFeeOfADeletedTransferGoesWithItForGood() {
        val first = ledger.insertTransfer(cash, bank, bank, emptyList(), emptyList(), null)
        val second = ledger.insertTransfer(cash, bank, cash, emptyList(), emptyList(), null)
        val legs = ledger.legsOf(first) + ledger.legsOf(second)
        assertEquals(6, legs.size)
        ledger.flagTransferDeleted(first, DELETED_AT)
        ledger.flagTransferDeleted(second, DELETED_AT)

        ledger.deleteTransfer(first)
        assertEquals(1, ledger.deleteFlaggedItems(DELETED_AT))

        assertEquals(0L, ledger.count("transfers", "transfer_id IN ($first, $second)"))
        assertEquals(0L, ledger.count("transactions", "transaction_id IN (${legs.joinToString()})"))
    }

    @Test
    fun aDeletedLegOfALiveTransferIsLeftByTheCleanUp() {
        val transfer = ledger.insertTransfer(cash, bank, bank, listOf(ann), emptyList(), null)
        val leg = ledger.legsOf(transfer)[0]
        ledger.database().execSQL("UPDATE transactions SET deleted = $DELETED_AT WHERE transaction_id = $leg")
        val rows = ledger.transferRows(transfer)

        assertEquals(0, ledger.deleteFlaggedItems(DELETED_AT))

        assertEquals(rows, ledger.transferRows(transfer))
    }

    @Test
    fun aCleanUpWithNoUsableTimeDeletesNothing() {
        val deleted = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val early = ledger.insertTransaction(ledger.transaction(cash, groceries, 200L))
        val transfer = ledger.insertTransfer(cash, bank, bank, emptyList(), emptyList(), null)
        ledger.flagTransactionDeleted(deleted, DELETED_AT)
        ledger.flagTransactionDeleted(early, DELETED_AT)
        ledger.flagTransferDeleted(transfer, DELETED_AT)
        ledger.database().execSQL("UPDATE transactions SET deleted = -$DELETED_AT WHERE transaction_id = $early")
        val rows = ledger.rows("transactions", "1") + ledger.rows("transfers", "1")
        val uri = Uri.withAppendedPath(DataContentProvider.CONTENT_ALL, "deleted")

        assertEquals(0, ledger.resolver.delete(uri, null, null))
        assertEquals(0, ledger.resolver.delete(uri, null, emptyArray()))
        for (deletedAt in listOf("abc", "", "0", "-1", "-$DELETED_AT")) {
            assertEquals(0, ledger.resolver.delete(uri, null, arrayOf(deletedAt)))
        }

        assertEquals(rows, ledger.rows("transactions", "1") + ledger.rows("transfers", "1"))
    }
}
