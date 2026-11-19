package com.oriondev.moneywallet.ui.activity

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.service.BackupHandlerIntentService
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.DeletedLedger
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.awaitWorker
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.executorOf
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.idleMainLooper
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.shownDialogMessages
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.snackbarsOf
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.whileParked
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView
import com.oriondev.moneywallet.utils.DateFormatter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLog

import java.util.Date
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class RecentlyDeletedActivityTest {

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

    private val worker = executorOf(RecentlyDeletedActivity::class.java, "sDeletedItemExecutor")

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
    fun bothKindsShowAsInTheLedgerWithTheDayTheyWereDeleted() {
        val now = System.currentTimeMillis()
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 1000L))
        val transfer = ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null)
        ledger.flagTransactionDeleted(transaction, now - TimeUnit.DAYS.toMillis(3))
        ledger.flagTransferDeleted(transfer, now - TimeUnit.DAYS.toMillis(1))
        launch { activity ->
            assertEquals(listOf(
                    listOf("Cash -> Bank", "Move", deletedLine(now - TimeUnit.DAYS.toMillis(1))),
                    listOf("Groceries", "Receipt", deletedLine(now - TimeUnit.DAYS.toMillis(3)))),
                    awaitRows(activity, 2).map { listOf(text(it, R.id.primary_text_view),
                            text(it, R.id.secondary_text_view), text(it, R.id.deleted_text_view)) })
            assertEquals(context.getString(R.string.title_activity_recently_deleted),
                    activity.findViewById<Toolbar>(R.id.primary_toolbar).title.toString())
        }
    }

    @Test
    fun restoringATransactionBringsEveryRowOfItBackOffTheMainThread() {
        val transaction = ledger.insertSplitTransaction(cash, groceries, listOf(household), listOf(ann),
                listOf(ledger.insertAttachment("receipt")), null)
        val stored = ledger.transactionRows(transaction)
        ledger.flagTransactionDeleted(transaction)
        val deleted = ledger.transactionRows(transaction)
        launch { activity ->
            val dialog = openRow(activity, 0)
            assertEquals("Receipt", dialogTitle(dialog))
            assertEquals(context.getString(R.string.action_restore), dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
            assertEquals(context.getString(R.string.action_delete_forever), dialog.getButton(AlertDialog.BUTTON_NEUTRAL).text.toString())
            assertEquals(context.getString(android.R.string.cancel), dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text.toString())
            whileParked(worker) {
                click(dialog, AlertDialog.BUTTON_POSITIVE)
                assertEquals("the restore ran inside the tap", deleted, ledger.transactionRows(transaction))
            }
            assertEquals(stored, ledger.transactionRows(transaction))
            awaitRows(activity, 0)
            assertEquals(emptyList<String>(), shownDialogMessages())
            assertEquals(emptyList<View>(), snackbarsOf(activity))
        }
    }

    @Test
    fun restoringATransferBringsItAndItsLegsBack() {
        val transfer = ledger.insertTransfer(cash, bank, cash, listOf(ann), emptyList(), null)
        val stored = ledger.transferRows(transfer)
        ledger.flagTransferDeleted(transfer)
        launch { activity ->
            val dialog = openRow(activity, 0)
            assertEquals("Move", dialogTitle(dialog))
            whileParked(worker) {
                click(dialog, AlertDialog.BUTTON_POSITIVE)
            }
            assertEquals(stored, ledger.transferRows(transfer))
            awaitRows(activity, 0)
        }
    }

    @Test
    fun aTransactionWithNoDescriptionIsTitledByItsCategory() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 1000L).apply {
            putNull(Contract.Transaction.DESCRIPTION)
        })
        ledger.flagTransactionDeleted(transaction)
        launch { activity ->
            assertEquals("Groceries", dialogTitle(openRow(activity, 0)))
        }
    }

    @Test
    fun aTransferWithNoDescriptionIsTitledByItsWallets() {
        val transfer = ContentUris.parseId(ledger.resolver.insert(DataContentProvider.CONTENT_TRANSFERS,
                ledger.transfer(cash, bank, null, emptyList(), emptyList(), null).apply {
                    putNull(Contract.Transfer.DESCRIPTION)
                })!!)
        ledger.flagTransferDeleted(transfer)
        launch { activity ->
            assertEquals("Cash -> Bank", dialogTitle(openRow(activity, 0)))
        }
    }

    @Test
    fun aRefusedRestoreShowsTheMessageTheUndoShowsAndKeepsTheItem() {
        val debt = ledger.insertDebt(cash, 2000L, listOf(ann))
        val payment = ledger.insertTransaction(ledger.transaction(bank,
                ledger.systemCategory(Contract.CategoryTag.PAID_DEBT), 500L).apply {
            put(Contract.Transaction.TYPE, Contract.TransactionType.DEBT)
            put(Contract.Transaction.DEBT_ID, debt)
        })
        ledger.flagTransactionDeleted(payment)
        ledger.database().execSQL("UPDATE wallets SET wallet_currency = 'USD' WHERE wallet_id = $bank")
        val rows = ledger.transactionRows(payment)
        launch { activity ->
            val dialog = openRow(activity, 0)
            whileParked(worker) {
                click(dialog, AlertDialog.BUTTON_POSITIVE)
            }
            assertEquals(listOf(context.resources.getQuantityString(
                    R.plurals.message_error_restore_transactions_currency, 1, 1)), shownDialogMessages())
            assertEquals(rows, ledger.transactionRows(payment))
            assertEquals(1, awaitRows(activity, 1).size)
        }
    }

    @Test
    fun deleteForeverAsksFirstThenRemovesEveryRowOffTheMainThread() {
        val transaction = ledger.insertSplitTransaction(cash, groceries, listOf(household), listOf(ann),
                listOf(ledger.insertAttachment("receipt")), null)
        ledger.flagTransactionDeleted(transaction)
        val deleted = ledger.transactionRows(transaction)
        launch { activity ->
            val dialog = openRow(activity, 0)
            whileParked(worker) {
                click(dialog, AlertDialog.BUTTON_NEUTRAL)
                assertEquals(listOf(context.getString(R.string.message_delete_forever)), shownDialogMessages())
            }
            assertEquals("the item went before the confirm", deleted, ledger.transactionRows(transaction))
            val confirm = ShadowDialog.getLatestDialog() as AlertDialog
            assertEquals(context.getString(R.string.action_delete_forever),
                    confirm.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
            whileParked(worker) {
                click(confirm, AlertDialog.BUTTON_POSITIVE)
                assertEquals("the delete ran inside the tap", deleted, ledger.transactionRows(transaction))
            }
            assertEquals(emptyList<Map<String, Any?>>(), ledger.transactionRows(transaction))
            awaitRows(activity, 0)
        }
    }

    @Test
    fun deleteForeverOnATransferRemovesItsLegs() {
        val transfer = ledger.insertTransfer(cash, bank, cash, listOf(ann), emptyList(), null)
        val legs = ledger.legsOf(transfer)
        ledger.flagTransferDeleted(transfer)
        launch { activity ->
            click(openRow(activity, 0), AlertDialog.BUTTON_NEUTRAL)
            whileParked(worker) {
                click(ShadowDialog.getLatestDialog() as AlertDialog, AlertDialog.BUTTON_POSITIVE)
            }
            assertEquals(0L, ledger.count("transfers", "transfer_id = $transfer"))
            assertEquals(0L, ledger.count("transfer_people", "_transfer = $transfer"))
            assertEquals(0L, ledger.count("transactions", "transaction_id IN (${legs.joinToString(",")})"))
            awaitRows(activity, 0)
        }
    }

    @Test
    fun deleteForeverOnAnItemRestoredSinceTheListWasReadRemovesNothing() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 1000L))
        ledger.flagTransactionDeleted(transaction)
        launch { activity ->
            click(openRow(activity, 0), AlertDialog.BUTTON_NEUTRAL)
            val confirm = ShadowDialog.getLatestDialog() as AlertDialog
            ledger.restoreTransaction(transaction)
            val restored = ledger.transactionRows(transaction)
            whileParked(worker) {
                click(confirm, AlertDialog.BUTTON_POSITIVE)
            }
            assertEquals(restored, ledger.transactionRows(transaction))
            assertEquals(setOf<Any?>(0L), ledger.deletedOf(restored))
        }
    }

    @Test
    fun deleteForeverOnATransactionDeletedAgainSinceTheListWasReadRemovesNothing() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 1000L))
        ledger.flagTransactionDeleted(transaction, DELETED_AT)
        launch { activity ->
            click(openRow(activity, 0), AlertDialog.BUTTON_NEUTRAL)
            val confirm = ShadowDialog.getLatestDialog() as AlertDialog
            ledger.database().execSQL("UPDATE transactions SET deleted = ${DELETED_AT + 1} WHERE transaction_id = $transaction")
            val rows = ledger.transactionRows(transaction)
            whileParked(worker) {
                click(confirm, AlertDialog.BUTTON_POSITIVE)
            }
            assertEquals(rows, ledger.transactionRows(transaction))
            assertEquals(setOf<Any?>(DELETED_AT + 1), ledger.deletedOf(ledger.rows("transactions", "transaction_id = $transaction")))
        }
    }

    @Test
    fun deleteForeverOnATransferDeletedAgainSinceTheListWasReadRemovesNothing() {
        val transfer = ledger.insertTransfer(cash, bank, cash, listOf(ann), emptyList(), null)
        ledger.flagTransferDeleted(transfer, DELETED_AT)
        launch { activity ->
            click(openRow(activity, 0), AlertDialog.BUTTON_NEUTRAL)
            val confirm = ShadowDialog.getLatestDialog() as AlertDialog
            ledger.database().execSQL("UPDATE transfers SET deleted = ${DELETED_AT + 1} WHERE transfer_id = $transfer")
            val rows = ledger.transferRows(transfer)
            whileParked(worker) {
                click(confirm, AlertDialog.BUTTON_POSITIVE)
            }
            assertEquals(rows, ledger.transferRows(transfer))
            assertEquals(setOf<Any?>(DELETED_AT + 1), ledger.deletedOf(ledger.rows("transfers", "transfer_id = $transfer")))
        }
    }

    @Test
    fun restoringATransactionDeletedAgainSinceTheListWasReadRestoresNothingAndSaysNothing() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 1000L))
        ledger.flagTransactionDeleted(transaction, DELETED_AT)
        launch { activity ->
            val dialog = openRow(activity, 0)
            ledger.database().execSQL("UPDATE transactions SET deleted = ${DELETED_AT + 1} WHERE transaction_id = $transaction")
            val rows = ledger.transactionRows(transaction)
            whileParked(worker) {
                click(dialog, AlertDialog.BUTTON_POSITIVE)
            }
            assertEquals(rows, ledger.transactionRows(transaction))
            assertEquals(setOf<Any?>(DELETED_AT + 1), ledger.deletedOf(ledger.rows("transactions", "transaction_id = $transaction")))
            assertEquals(emptyList<String>(), shownDialogMessages())
        }
    }

    @Test
    fun restoringATransferDeletedAgainSinceTheListWasReadRestoresNothingAndSaysNothing() {
        val transfer = ledger.insertTransfer(cash, bank, cash, listOf(ann), emptyList(), null)
        ledger.flagTransferDeleted(transfer, DELETED_AT)
        launch { activity ->
            val dialog = openRow(activity, 0)
            ledger.database().execSQL("UPDATE transfers SET deleted = ${DELETED_AT + 1} WHERE transfer_id = $transfer")
            val rows = ledger.transferRows(transfer)
            whileParked(worker) {
                click(dialog, AlertDialog.BUTTON_POSITIVE)
            }
            assertEquals(rows, ledger.transferRows(transfer))
            assertEquals(setOf<Any?>(DELETED_AT + 1), ledger.deletedOf(ledger.rows("transfers", "transfer_id = $transfer")))
            assertEquals(emptyList<String>(), shownDialogMessages())
        }
    }

    @Test
    fun deleteForeverOnATransactionWhoseIdNowCarriesTheUuidOfAnotherRemovesNothing() {
        val picked = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val other = ledger.insertTransaction(ledger.transaction(cash, groceries, 200L))
        ledger.flagTransactionDeleted(other, DELETED_AT)
        ledger.flagTransactionDeleted(picked, DELETED_AT)
        launch { activity ->
            assertEquals(listOf(other, picked), rowIds(activity, 2))
            click(openRow(activity, 1), AlertDialog.BUTTON_NEUTRAL)
            val confirm = ShadowDialog.getLatestDialog() as AlertDialog
            exchangeUuids("transactions", "transaction_id", picked, other)
            try {
                val rows = ledger.transactionRows(picked) + ledger.transactionRows(other)
                whileParked(worker) {
                    click(confirm, AlertDialog.BUTTON_POSITIVE)
                }
                assertEquals(rows, ledger.transactionRows(picked) + ledger.transactionRows(other))
                assertEquals(setOf<Any?>(DELETED_AT), ledger.deletedOf(ledger.rows("transactions",
                        "transaction_id IN ($picked, $other)")))
            } finally {
                exchangeUuids("transactions", "transaction_id", picked, other)
            }
        }
    }

    @Test
    fun deleteForeverOnATransferWhoseIdNowCarriesTheUuidOfAnotherRemovesNothing() {
        val picked = ledger.insertTransfer(cash, bank, cash, listOf(ann), emptyList(), null)
        val other = ledger.insertTransfer(bank, cash, null, emptyList(), emptyList(), null)
        ledger.flagTransferDeleted(other, DELETED_AT)
        ledger.flagTransferDeleted(picked, DELETED_AT)
        launch { activity ->
            assertEquals(listOf(other, picked), rowIds(activity, 2))
            click(openRow(activity, 1), AlertDialog.BUTTON_NEUTRAL)
            val confirm = ShadowDialog.getLatestDialog() as AlertDialog
            exchangeUuids("transfers", "transfer_id", picked, other)
            try {
                val rows = ledger.transferRows(picked) + ledger.transferRows(other)
                whileParked(worker) {
                    click(confirm, AlertDialog.BUTTON_POSITIVE)
                }
                assertEquals(rows, ledger.transferRows(picked) + ledger.transferRows(other))
                assertEquals(setOf<Any?>(DELETED_AT), ledger.deletedOf(ledger.rows("transfers",
                        "transfer_id IN ($picked, $other)")))
            } finally {
                exchangeUuids("transfers", "transfer_id", picked, other)
            }
        }
    }

    @Test
    fun restoringATransactionWhoseIdNowCarriesTheUuidOfAnotherRestoresNothingAndSaysNothing() {
        val picked = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val other = ledger.insertTransaction(ledger.transaction(cash, groceries, 200L))
        ledger.flagTransactionDeleted(other, DELETED_AT)
        ledger.flagTransactionDeleted(picked, DELETED_AT)
        launch { activity ->
            assertEquals(listOf(other, picked), rowIds(activity, 2))
            val dialog = openRow(activity, 1)
            exchangeUuids("transactions", "transaction_id", picked, other)
            try {
                val rows = ledger.transactionRows(picked) + ledger.transactionRows(other)
                whileParked(worker) {
                    click(dialog, AlertDialog.BUTTON_POSITIVE)
                }
                assertEquals(rows, ledger.transactionRows(picked) + ledger.transactionRows(other))
                assertEquals(setOf<Any?>(DELETED_AT), ledger.deletedOf(ledger.rows("transactions",
                        "transaction_id IN ($picked, $other)")))
                assertEquals(emptyList<String>(), shownDialogMessages())
            } finally {
                exchangeUuids("transactions", "transaction_id", picked, other)
            }
        }
    }

    @Test
    fun restoringATransferWhoseIdNowCarriesTheUuidOfAnotherRestoresNothingAndSaysNothing() {
        val picked = ledger.insertTransfer(cash, bank, cash, listOf(ann), emptyList(), null)
        val other = ledger.insertTransfer(bank, cash, null, emptyList(), emptyList(), null)
        ledger.flagTransferDeleted(other, DELETED_AT)
        ledger.flagTransferDeleted(picked, DELETED_AT)
        launch { activity ->
            assertEquals(listOf(other, picked), rowIds(activity, 2))
            val dialog = openRow(activity, 1)
            exchangeUuids("transfers", "transfer_id", picked, other)
            try {
                val rows = ledger.transferRows(picked) + ledger.transferRows(other)
                whileParked(worker) {
                    click(dialog, AlertDialog.BUTTON_POSITIVE)
                }
                assertEquals(rows, ledger.transferRows(picked) + ledger.transferRows(other))
                assertEquals(setOf<Any?>(DELETED_AT), ledger.deletedOf(ledger.rows("transfers",
                        "transfer_id IN ($picked, $other)")))
                assertEquals(emptyList<String>(), shownDialogMessages())
            } finally {
                exchangeUuids("transfers", "transfer_id", picked, other)
            }
        }
    }

    @Test
    fun aFailingDeleteForeverIsLoggedAndSwallowed() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 1000L))
        ledger.flagTransactionDeleted(transaction)
        val rows = ledger.transactionRows(transaction)
        launch { activity ->
            click(openRow(activity, 0), AlertDialog.BUTTON_NEUTRAL)
            val confirm = ShadowDialog.getLatestDialog() as AlertDialog
            assertFailingDeleteIsLogged {
                click(confirm, AlertDialog.BUTTON_POSITIVE)
            }
        }
        assertEquals(rows, ledger.transactionRows(transaction))
    }

    @Test
    fun aFailingEmptyAllIsLoggedAndSwallowed() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 1000L))
        ledger.flagTransactionDeleted(transaction)
        val rows = ledger.transactionRows(transaction)
        launch { activity ->
            awaitRows(activity, 1)
            activity.onMenuItemClick(emptyAll(activity))
            idleMainLooper()
            val confirm = ShadowDialog.getLatestDialog() as AlertDialog
            assertFailingDeleteIsLogged {
                click(confirm, AlertDialog.BUTTON_POSITIVE)
            }
        }
        assertEquals(rows, ledger.transactionRows(transaction))
    }

    @Test
    fun emptyAllLeavesAnItemDeletedAfterTheNewestOneShown() {
        val shown = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val later = ledger.insertTransaction(ledger.transaction(cash, groceries, 200L))
        ledger.flagTransactionDeleted(shown, DELETED_AT)
        launch { activity ->
            awaitRows(activity, 1)
            ledger.database().execSQL("UPDATE transactions SET deleted = ${DELETED_AT + 1000} WHERE transaction_id = $later")
            val rows = ledger.transactionRows(later)
            activity.onMenuItemClick(emptyAll(activity))
            idleMainLooper()
            whileParked(worker) {
                click(ShadowDialog.getLatestDialog() as AlertDialog, AlertDialog.BUTTON_POSITIVE)
            }
            assertEquals(0L, ledger.count("transactions", "transaction_id = $shown"))
            assertEquals(rows, ledger.transactionRows(later))
            assertEquals(setOf<Any?>(DELETED_AT + 1000), ledger.deletedOf(rows))
        }
    }

    @Test
    fun emptyAllRemovesAnItemShownAsDeletedAfterNow() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        ledger.flagTransactionDeleted(transaction, System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1))
        launch { activity ->
            awaitRows(activity, 1)
            activity.onMenuItemClick(emptyAll(activity))
            idleMainLooper()
            whileParked(worker) {
                click(ShadowDialog.getLatestDialog() as AlertDialog, AlertDialog.BUTTON_POSITIVE)
            }
            assertEquals(0L, ledger.count("transactions", "transaction_id = $transaction"))
            awaitRows(activity, 0)
        }
    }

    @Test
    fun emptyAllAsksFirstThenRemovesEveryDeletedItemAndLeavesTheLiveOnes() {
        val live = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val liveTransfer = ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null)
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 200L))
        val transfer = ledger.insertTransfer(bank, cash, null, emptyList(), emptyList(), null)
        ledger.flagTransactionDeleted(transaction)
        ledger.flagTransferDeleted(transfer)
        val liveRows = ledger.transactionRows(live) + ledger.transferRows(liveTransfer)
        launch { activity ->
            awaitRows(activity, 2)
            val item = emptyAll(activity)
            assertTrue(item.isVisible)
            whileParked(worker) {
                activity.onMenuItemClick(item)
                idleMainLooper()
                assertEquals(listOf(context.getString(R.string.message_empty_recently_deleted)), shownDialogMessages())
                val confirm = ShadowDialog.getLatestDialog() as AlertDialog
                assertEquals(context.getString(R.string.action_empty), confirm.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
                click(confirm, AlertDialog.BUTTON_POSITIVE)
                assertEquals("the delete ran inside the tap", 1L, ledger.count("transactions", "transaction_id = $transaction"))
            }
            assertEquals(0L, ledger.count("transactions", "transaction_id = $transaction"))
            assertEquals(0L, ledger.count("transfers", "transfer_id = $transfer"))
            assertEquals(liveRows, ledger.transactionRows(live) + ledger.transferRows(liveTransfer))
            awaitRows(activity, 0)
            assertFalse(emptyAll(activity).isVisible)
        }
    }

    @Test
    fun emptyAllRemovesEveryShownItemWhenEachWasDeletedAtADifferentTime() {
        val live = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 200L))
        val transfer = ledger.insertTransfer(cash, bank, cash, listOf(ann), emptyList(), null)
        val legs = ledger.legsOf(transfer)
        ledger.flagTransactionDeleted(transaction, DELETED_AT)
        ledger.flagTransferDeleted(transfer, DELETED_AT + 1000)
        val liveRows = ledger.transactionRows(live)
        launch { activity ->
            assertEquals(listOf("Cash -> Bank", "Groceries"), awaitRows(activity, 2).map { text(it, R.id.primary_text_view) })
            activity.onMenuItemClick(emptyAll(activity))
            idleMainLooper()
            assertEquals(listOf(context.getString(R.string.message_empty_recently_deleted)), shownDialogMessages())
            whileParked(worker) {
                click(ShadowDialog.getLatestDialog() as AlertDialog, AlertDialog.BUTTON_POSITIVE)
            }
            assertEquals(0L, ledger.count("transactions", "transaction_id = $transaction"))
            assertEquals(0L, ledger.count("transfers", "transfer_id = $transfer"))
            assertEquals(0L, ledger.count("transfer_people", "_transfer = $transfer"))
            assertEquals(0L, ledger.count("transactions", "transaction_id IN (${legs.joinToString(",")})"))
            assertEquals(liveRows, ledger.transactionRows(live))
            awaitRows(activity, 0)
        }
    }

    @Test
    fun nothingDeletedShowsTheEmptyStateAndHidesEmptyAll() {
        ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        launch { activity ->
            val list = activity.findViewById<AdvancedRecyclerView>(R.id.advanced_recycler_view)
            await { list.textView.visibility == View.VISIBLE }
            assertEquals(context.getString(R.string.message_no_deleted_item_found), list.textView.text.toString())
            assertEquals(View.GONE, list.recyclerView.visibility)
            assertFalse(emptyAll(activity).isVisible)
        }
    }

    @Test
    fun theListFollowsDeletesAndRestoresMadeElsewhere() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val transfer = ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null)
        ledger.flagTransactionDeleted(transaction)
        launch { activity ->
            awaitRows(activity, 1)
            ledger.flagTransferDeleted(transfer)
            awaitRows(activity, 2)
            ledger.restoreTransaction(transaction)
            assertEquals(listOf("Cash -> Bank"), awaitRows(activity, 1).map { text(it, R.id.primary_text_view) })
            ledger.deleteFlaggedItems(System.currentTimeMillis())
            awaitRows(activity, 0)
        }
    }

    @Test
    fun aBackupRestoreElsewhereReloadsTheList() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        launch { activity ->
            val list = activity.findViewById<AdvancedRecyclerView>(R.id.advanced_recycler_view)
            await { list.textView.visibility == View.VISIBLE }
            ledger.database().execSQL("UPDATE transactions SET deleted = 1765000000000 WHERE transaction_id = $transaction")
            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(LocalAction.ACTION_BACKUP_SERVICE_FINISHED)
                    .putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_RESTORE))
            assertEquals(listOf("Groceries"), awaitRows(activity, 1).map { text(it, R.id.primary_text_view) })
        }
    }

    @Test
    fun aFailedBackupRestoreElsewhereReloadsTheList() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        launch { activity ->
            val list = activity.findViewById<AdvancedRecyclerView>(R.id.advanced_recycler_view)
            await { list.textView.visibility == View.VISIBLE }
            ledger.database().execSQL("UPDATE transactions SET deleted = $DELETED_AT WHERE transaction_id = $transaction")
            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(LocalAction.ACTION_BACKUP_SERVICE_FAILED)
                    .putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_RESTORE))
            assertEquals(listOf("Groceries"), awaitRows(activity, 1).map { text(it, R.id.primary_text_view) })
        }
    }

    @Test
    fun aBackupServiceBroadcastForAnotherActionDoesNotReloadTheList() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        launch { activity ->
            val list = activity.findViewById<AdvancedRecyclerView>(R.id.advanced_recycler_view)
            await { list.textView.visibility == View.VISIBLE }
            ledger.database().execSQL("UPDATE transactions SET deleted = $DELETED_AT WHERE transaction_id = $transaction")
            for (action in listOf(LocalAction.ACTION_BACKUP_SERVICE_FINISHED, LocalAction.ACTION_BACKUP_SERVICE_FAILED)) {
                LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(action)
                        .putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_BACKUP))
            }
            for (i in 0 until 20) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(25)
            }
            assertEquals(0, list.recyclerView.adapter!!.itemCount)
            assertEquals(View.VISIBLE, list.textView.visibility)
        }
    }

    private fun assertFailingDeleteIsLogged(action: () -> Unit) {
        val uncaught = mutableListOf<Throwable>()
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        ledger.database().execSQL("ALTER TABLE transfers RENAME TO transfers_aside")
        Thread.setDefaultUncaughtExceptionHandler { _, e -> synchronized(uncaught) { uncaught.add(e) } }
        try {
            ShadowLog.clear()
            action()
            assertTrue("the worker did not finish within 5 seconds", awaitWorker(worker))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(handler)
            ledger.database().execSQL("ALTER TABLE transfers_aside RENAME TO transfers")
        }
        assertEquals(emptyList<Throwable>(), synchronized(uncaught) { uncaught.toList() })
        val errors = ShadowLog.getLogsForTag("RecentlyDeletedActivity").filter { it.type == Log.ERROR }
        assertEquals(1, errors.size)
        assertTrue(errors.single().throwable is SQLiteException)
    }

    private fun exchangeUuids(table: String, idColumn: String, first: Long, second: Long) {
        val uuids = ledger.rows(table, "$idColumn IN ($first, $second)").associate { it[idColumn] to it["uuid"] }
        ledger.database().execSQL("UPDATE $table SET uuid = 'moving' WHERE $idColumn = $first")
        ledger.database().execSQL("UPDATE $table SET uuid = ? WHERE $idColumn = $second", arrayOf(uuids[first]))
        ledger.database().execSQL("UPDATE $table SET uuid = ? WHERE $idColumn = $first", arrayOf(uuids[second]))
    }

    private fun rowIds(activity: RecentlyDeletedActivity, count: Int): List<Long> {
        val list = activity.findViewById<AdvancedRecyclerView>(R.id.advanced_recycler_view).recyclerView
        return awaitRows(activity, count).map { list.adapter!!.getItemId(list.getChildAdapterPosition(it)) }
    }

    private fun launch(action: (RecentlyDeletedActivity) -> Unit) {
        ActivityScenario.launch(RecentlyDeletedActivity::class.java).use { scenario ->
            scenario.onActivity { action(it) }
        }
    }

    private fun emptyAll(activity: RecentlyDeletedActivity) =
            activity.findViewById<Toolbar>(R.id.primary_toolbar).menu.findItem(R.id.action_empty_all)

    private fun deletedLine(deletedAt: Long): String =
            context.getString(R.string.relative_string_deleted_on, DateFormatter.getFormattedDate(Date(deletedAt)))

    private fun openRow(activity: RecentlyDeletedActivity, position: Int): AlertDialog {
        val row = awaitRows(activity, position + 1)[position]
        val before = ShadowDialog.getShownDialogs().size
        assertTrue(row.performClick())
        idleMainLooper()
        assertEquals("the tap opened no dialog", before + 1, ShadowDialog.getShownDialogs().size)
        return ShadowDialog.getLatestDialog() as AlertDialog
    }

    private fun click(dialog: AlertDialog, button: Int) {
        dialog.getButton(button).performClick()
        idleMainLooper()
    }

    private fun dialogTitle(dialog: AlertDialog): String =
            dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)!!.text.toString()

    private fun text(row: View, id: Int): String = row.findViewById<TextView>(id).text.toString()

    private fun awaitRows(activity: RecentlyDeletedActivity, count: Int): List<ViewGroup> {
        val list = activity.findViewById<AdvancedRecyclerView>(R.id.advanced_recycler_view).recyclerView
        await {
            list.adapter!!.itemCount == count && (count == 0 || list.visibility == View.VISIBLE)
        }
        list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
        list.layout(0, 0, 1080, 1920)
        val rows = (0 until list.childCount).map { list.getChildAt(it) as ViewGroup }
        assertEquals(count, rows.size)
        rows.forEach { assertNotEquals(RecyclerView.NO_POSITION, list.getChildAdapterPosition(it)) }
        return rows
    }

    private fun await(condition: () -> Boolean) {
        for (i in 0 until 200) {
            if (condition()) {
                return
            }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(25)
        }
        assertTrue("the list never reached the expected state", condition())
    }
}
