package com.oriondev.moneywallet.ui.fragment.base

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.view.View

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.DeletedLedger
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.BackupListActivity
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.addHost
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.addPanel
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.executorOf
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.idleMainLooper
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.idleMainLooperFor
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.removeHost
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.shownDialogMessages
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.snackbarText
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.snackbarsOf
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.snackbarsUnder
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.undoOf
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.whileParked
import com.oriondev.moneywallet.ui.fragment.secondary.TransactionItemFragment
import com.oriondev.moneywallet.ui.fragment.secondary.TransferItemFragment
import com.oriondev.moneywallet.ui.fragment.secondary.WalletItemFragment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
class SecondaryPanelUndoTest {

    private lateinit var context: Context
    private lateinit var ledger: DeletedLedger
    private var cash = 0L
    private var bank = 0L
    private var groceries = 0L
    private var household = 0L
    private var fuel = 0L
    private var ann = 0L
    private var debt = 0L

    private val worker = executorOf(SecondaryPanelFragment::class.java)

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
        fuel = ledger.insertCategory("Fuel", Contract.CategoryType.EXPENSE)
        ann = ledger.insertPerson("Ann")
        debt = ledger.insertDebt(cash, 2000L, listOf(ann))
    }

    @Test
    fun oneTapDeletesATransactionAndEveryPartOfItAndUndoBringsItBack() {
        val transaction = ledger.insertSplitTransaction(cash, groceries, listOf(household, fuel), listOf(ann),
                listOf(ledger.insertAttachment("receipt")), null)
        val stored = ledger.transactionRows(transaction)
        launch { activity ->
            val host = addHost(activity)
            val panel = TransactionItemFragment()
            addPanel(host, panel, transaction)
            whileParked(worker) {
                tapDelete(panel)
                assertNull("a dialog was put up before the delete", ShadowDialog.getLatestDialog())
                assertEquals("the delete ran inside the tap", stored, ledger.transactionRows(transaction))
            }
            val deleted = ledger.deletedOf(ledger.transactionRows(transaction))
            assertEquals(1, deleted.size)
            assertNotEquals(0L, deleted.single())
            assertEquals(0, listedTransactions(transaction))
            assertPanelClosed(host, panel)
            val snackbar = snackbarsOf(activity).single()
            assertSame("the snackbar is not on the primary panel", host.primaryPanel, snackbar.parent)
            assertEquals(context.getString(R.string.snackbar_message_transaction_deleted), snackbarText(snackbar))
            val undo = undoOf(snackbar)
            assertEquals(context.getString(R.string.action_undo), undo.text.toString())
            whileParked(worker) {
                undo.performClick()
                assertEquals("the restore ran inside the tap", deleted,
                        ledger.deletedOf(ledger.transactionRows(transaction)))
            }
            assertEquals(stored, ledger.transactionRows(transaction))
            assertEquals(1, listedTransactions(transaction))
            assertEquals(emptyList<String>(), shownDialogMessages())
        }
    }

    @Test
    fun oneTapDeletesATransferAndItsLegsAndUndoBringsItBack() {
        val transfer = ledger.insertTransfer(cash, bank, cash, listOf(ann), listOf(ledger.insertAttachment("slip")), null)
        val stored = ledger.transferRows(transfer)
        launch { activity ->
            val host = addHost(activity)
            val panel = TransferItemFragment()
            addPanel(host, panel, transfer)
            whileParked(worker) {
                tapDelete(panel)
                assertNull("a dialog was put up before the delete", ShadowDialog.getLatestDialog())
                assertEquals("the delete ran inside the tap", stored, ledger.transferRows(transfer))
            }
            val deleted = ledger.deletedOf(ledger.transferRows(transfer))
            assertEquals(1, deleted.size)
            assertNotEquals(0L, deleted.single())
            assertEquals(3, ledger.legsOf(transfer).size)
            assertEquals(0, listedTransfers(transfer))
            assertPanelClosed(host, panel)
            val snackbar = snackbarsOf(activity).single()
            assertSame("the snackbar is not on the primary panel", host.primaryPanel, snackbar.parent)
            assertEquals(context.getString(R.string.snackbar_message_transfer_deleted), snackbarText(snackbar))
            val undo = undoOf(snackbar)
            whileParked(worker) {
                undo.performClick()
                assertEquals("the restore ran inside the tap", deleted, ledger.deletedOf(ledger.transferRows(transfer)))
            }
            assertEquals(stored, ledger.transferRows(transfer))
            assertEquals(1, listedTransfers(transfer))
        }
    }

    @Test
    fun aDeleteThatMarksNothingShowsNoSnackbar() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        launch { activity ->
            val host = addHost(activity)
            val panel = TransactionItemFragment()
            addPanel(host, panel, transaction)
            ledger.database().execSQL("UPDATE transactions SET deleted = 1765000000000 WHERE transaction_id = $transaction")
            whileParked(worker) {
                tapDelete(panel)
            }
            assertEquals(emptyList<View>(), snackbarsOf(activity))
            assertEquals(emptyList<String>(), shownDialogMessages())
            assertEquals(setOf<Any?>(1765000000000L), ledger.deletedOf(ledger.rows("transactions", "transaction_id = $transaction")))
        }
    }

    @Test
    fun aLegOfATransferIsRefusedWithTheDialogAndNoSnackbar() {
        val transfer = ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null)
        val leg = ledger.legsOf(transfer).first()
        launch { activity ->
            val host = addHost(activity)
            val panel = TransactionItemFragment()
            addPanel(host, panel, leg)
            whileParked(worker) {
                tapDelete(panel)
            }
            assertEquals(listOf(context.getString(R.string.message_error_delete_transaction_of_transfer)),
                    shownDialogMessages())
            assertEquals(emptyList<View>(), snackbarsOf(activity))
            assertEquals(setOf<Any?>(0L), ledger.deletedOf(ledger.transferRows(transfer)))
            assertEquals(leg, panel.itemId)
        }
    }

    @Test
    fun aRestoreRefusedByTheCurrencyOfTheDebtLeavesThePaymentDeletedAndSaysWhy() {
        val payment = insertPayment()
        launch { activity ->
            val host = addHost(activity)
            val panel = TransactionItemFragment()
            addPanel(host, panel, payment)
            whileParked(worker) {
                tapDelete(panel)
            }
            val deleted = ledger.transactionRows(payment)
            assertNotEquals(setOf<Any?>(0L), ledger.deletedOf(deleted))
            moveBankToDollars()
            whileParked(worker) {
                undoOf(snackbarsUnder(host.primaryPanel).single()).performClick()
            }
            assertEquals(deleted, ledger.transactionRows(payment))
            assertEquals(listOf(context.resources.getQuantityString(
                    R.plurals.message_error_restore_transactions_currency, 1, 1)), shownDialogMessages())
        }
    }

    @Test
    fun aRestoreRefusedByTheCurrencyOfTheSavingLeavesTheDepositDeletedAndSaysWhy() {
        val deposit = insertDeposit(ledger.insertSaving(bank))
        launch { activity ->
            val host = addHost(activity)
            val panel = TransactionItemFragment()
            addPanel(host, panel, deposit)
            whileParked(worker) {
                tapDelete(panel)
            }
            val deleted = ledger.transactionRows(deposit)
            assertNotEquals(setOf<Any?>(0L), ledger.deletedOf(deleted))
            ledger.database().execSQL("UPDATE wallets SET wallet_currency = 'USD' WHERE wallet_id = $bank")
            whileParked(worker) {
                undoOf(snackbarsUnder(host.primaryPanel).single()).performClick()
            }
            assertEquals(deleted, ledger.transactionRows(deposit))
            assertEquals(listOf(context.resources.getQuantityString(
                    R.plurals.message_error_restore_transactions_currency, 1, 1)), shownDialogMessages())
        }
    }

    @Test
    fun aRestoreRefusedByTheCategoryOfAPartLeavesItDeletedAndSaysWhy() {
        val transaction = ledger.insertSplitTransaction(cash, groceries, listOf(household, fuel), listOf(ann),
                emptyList(), null)
        launch { activity ->
            val host = addHost(activity)
            val panel = TransactionItemFragment()
            addPanel(host, panel, transaction)
            whileParked(worker) {
                tapDelete(panel)
            }
            val deleted = ledger.transactionRows(transaction)
            assertNotEquals(setOf<Any?>(0L), ledger.deletedOf(deleted))
            ledger.database().execSQL("UPDATE categories SET category_type = " +
                    "${Contract.CategoryType.INCOME.value} WHERE category_id = $fuel")
            whileParked(worker) {
                undoOf(snackbarsUnder(host.primaryPanel).single()).performClick()
            }
            assertEquals(deleted, ledger.transactionRows(transaction))
            assertEquals(listOf(context.resources.getQuantityString(
                    R.plurals.message_error_restore_transactions_category, 1, 1)), shownDialogMessages())
        }
    }

    @Test
    fun aPanelMovedToAnotherItemWhileTheDeleteIsInFlightStillOffersTheUndoOfTheFirst() {
        val first = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        val second = ledger.insertTransaction(ledger.transaction(cash, household, 700L))
        val stored = ledger.transactionRows(first)
        val secondStored = ledger.transactionRows(second)
        launch { activity ->
            val host = addHost(activity)
            val panel = TransactionItemFragment()
            addPanel(host, panel, first)
            whileParked(worker) {
                tapDelete(panel)
                panel.showItemId(second)
                idleMainLooper()
            }
            assertNotEquals(setOf<Any?>(0L), ledger.deletedOf(ledger.transactionRows(first)))
            assertEquals(second, panel.itemId)
            val snackbar = snackbarsOf(activity).single()
            assertSame("the snackbar is not on the primary panel", host.primaryPanel, snackbar.parent)
            assertEquals(context.getString(R.string.snackbar_message_transaction_deleted), snackbarText(snackbar))
            whileParked(worker) {
                undoOf(snackbar).performClick()
            }
            assertEquals(stored, ledger.transactionRows(first))
            assertEquals(secondStored, ledger.transactionRows(second))
            assertEquals(second, panel.itemId)
            assertEquals(emptyList<String>(), shownDialogMessages())
        }
    }

    @Test
    @Config(sdk = [24])
    fun theSnackbarOfAPanelDeleteStaysUpForTheLongDuration() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        launch { activity ->
            val host = addHost(activity)
            val panel = TransactionItemFragment()
            addPanel(host, panel, transaction)
            whileParked(worker) {
                tapDelete(panel)
            }
            val snackbar = snackbarsOf(activity).single()
            idleMainLooperFor(2500L)
            assertEquals("the snackbar went away before the long duration", listOf(snackbar), snackbarsOf(activity))
            idleMainLooperFor(1000L)
            assertEquals("the snackbar stayed past the long duration", emptyList<View>(), snackbarsOf(activity))
        }
    }

    @Test
    fun aDeleteThatComesBackAfterTheScreenIsGoneShowsNothingAndStillLands() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        launch { activity ->
            val host = addHost(activity)
            val panel = TransactionItemFragment()
            addPanel(host, panel, transaction)
            whileParked(worker) {
                tapDelete(panel)
                removeHost(activity, host)
            }
            assertEquals(emptyList<View>(), snackbarsOf(activity))
            assertNull(ShadowDialog.getLatestDialog())
            assertNotEquals(setOf<Any?>(0L), ledger.deletedOf(ledger.transactionRows(transaction)))
        }
    }

    @Test
    fun aRefusedRestoreThatComesBackAfterTheScreenIsGoneShowsNothing() {
        val payment = insertPayment()
        launch { activity ->
            val host = addHost(activity)
            val panel = TransactionItemFragment()
            addPanel(host, panel, payment)
            whileParked(worker) {
                tapDelete(panel)
            }
            val deleted = ledger.transactionRows(payment)
            moveBankToDollars()
            val undo = undoOf(snackbarsUnder(host.primaryPanel).single())
            whileParked(worker) {
                undo.performClick()
                removeHost(activity, host)
            }
            assertNull(ShadowDialog.getLatestDialog())
            assertEquals(emptyList<View>(), snackbarsOf(activity))
            assertEquals(deleted, ledger.transactionRows(payment))
        }
    }

    @Test
    fun aWalletPanelStillAsksAndDeletesForGoodWithNoSnackbar() {
        val wallet = ledger.insertWallet("Spare", "EUR")
        launch { activity ->
            val host = addHost(activity)
            val panel = WalletItemFragment()
            addPanel(host, panel, wallet)
            whileParked(worker) {
                tapDelete(panel)
                val confirm = ShadowDialog.getLatestDialog() as AlertDialog
                confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                idleMainLooper()
            }
            assertEquals(0L, ledger.count("wallets", "wallet_id = $wallet"))
            assertPanelClosed(host, panel)
            assertEquals(emptyList<View>(), snackbarsOf(activity))
        }
    }

    private fun launch(action: (BackupListActivity) -> Unit) {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { action(it) }
        }
    }

    private fun tapDelete(panel: SecondaryPanelFragment) {
        val toolbar = panel.requireView().findViewById<Toolbar>(R.id.secondary_toolbar)
        toolbar.menu.performIdentifierAction(R.id.action_delete_item, 0)
        idleMainLooper()
    }

    private fun assertPanelClosed(host: AppBarHost, panel: SecondaryPanelFragment) {
        assertEquals(0L, panel.itemId)
        assertEquals(View.VISIBLE, host.primaryPanel.visibility)
        assertEquals(View.GONE, host.requireView().findViewById<View>(R.id.secondary_panel_frame_layout).visibility)
    }

    private fun listedTransactions(id: Long): Int =
            ledger.view(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID + " = ?", id.toString()).size

    private fun listedTransfers(id: Long): Int =
            ledger.view(DataContentProvider.CONTENT_TRANSFERS, Contract.Transfer.ID + " = ?", id.toString()).size

    private fun insertPayment(): Long = ledger.insertTransaction(
            ledger.transaction(bank, ledger.systemCategory(Contract.CategoryTag.PAID_DEBT), 500L).apply {
                put(Contract.Transaction.TYPE, Contract.TransactionType.DEBT)
                put(Contract.Transaction.DEBT_ID, debt)
            })

    private fun insertDeposit(saving: Long): Long = ledger.insertTransaction(
            ledger.transaction(cash, ledger.systemCategory(Contract.CategoryTag.SAVING_DEPOSIT), 800L).apply {
                put(Contract.Transaction.TYPE, Contract.TransactionType.SAVING)
                put(Contract.Transaction.SAVING_ID, saving)
            })

    private fun moveBankToDollars() {
        assertEquals(1, ledger.resolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, bank),
                ContentValues().apply { put(Contract.Wallet.CURRENCY, "USD") }, null, null))
    }
}
