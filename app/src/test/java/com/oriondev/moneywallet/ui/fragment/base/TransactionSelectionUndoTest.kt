package com.oriondev.moneywallet.ui.fragment.base

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.view.View

import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.DeletedLedger
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.BackupListActivity
import com.oriondev.moneywallet.ui.adapter.recycler.TransactionCursorAdapter
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.addHost
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.executorOf
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.idleMainLooper
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.idleMainLooperFor
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.removeHost
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.shownDialogMessages
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.snackbarText
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.snackbarsOf
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.undoOf
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.whileParked

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

import java.util.Date

@RunWith(RobolectricTestRunner::class)
class TransactionSelectionUndoTest {

    companion object {

        private const val DELETED_AT = 1765000000000L
    }

    private lateinit var context: Context
    private lateinit var ledger: DeletedLedger
    private var cash = 0L
    private var bank = 0L
    private var groceries = 0L
    private var household = 0L
    private var fuel = 0L
    private var ann = 0L
    private var debt = 0L

    private val worker = executorOf(TransactionSelectionMode::class.java)

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
    fun everyRowOfTheSetCarriesTheOneTimeGivenAndALegIsRefused() {
        val split = insertSplitTransaction()
        val transfer = ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null)
        val leg = ledger.legsOf(transfer).first()
        val plain = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))

        val deleted = TransactionSelectionMode.deleteTransactions(ledger.resolver, longArrayOf(split, leg, plain), DELETED_AT)

        assertEquals(listOf(split, plain), deleted.mDeleted)
        assertEquals(setOf(leg), deleted.mRefused)
        assertEquals(setOf<Any?>(DELETED_AT), ledger.deletedOf(ledger.transactionRows(split) + ledger.transactionRows(plain)))
        assertEquals(setOf<Any?>(0L), ledger.deletedOf(ledger.transferRows(transfer)))
    }

    @Test
    fun aSelectionIsDeletedInOneTapAndOneUndoBringsBackEveryMarkedRow() {
        val split = insertSplitTransaction()
        val transfer = ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null)
        val leg = ledger.legsOf(transfer).first()
        val plain = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        val stored = ledger.transactionRows(split) + ledger.transactionRows(plain)
        val transferStored = ledger.transferRows(transfer)
        launch { activity ->
            val host = addHost(activity)
            whileParked(worker) {
                tapDelete(host, longArrayOf(split, leg, plain))
                assertNull("a dialog was put up before the delete", ShadowDialog.getLatestDialog())
                assertEquals("the delete ran inside the tap", stored,
                        ledger.transactionRows(split) + ledger.transactionRows(plain))
            }
            val deleted = ledger.deletedOf(ledger.transactionRows(split) + ledger.transactionRows(plain))
            assertEquals(1, deleted.size)
            assertNotEquals(0L, deleted.single())
            assertEquals(transferStored, ledger.transferRows(transfer))
            assertEquals(listOf(context.getString(R.string.message_error_delete_transaction_of_transfer)),
                    shownDialogMessages())
            assertEquals(emptyList<View>(), snackbarsOf(activity))
            refusedDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            idleMainLooper()
            val snackbar = snackbarsOf(activity).single()
            assertSame("the snackbar is not on the primary panel", host.primaryPanel, snackbar.parent)
            assertEquals(context.resources.getQuantityString(R.plurals.snackbar_message_transactions_deleted, 2, 2),
                    snackbarText(snackbar))
            val undo = undoOf(snackbar)
            whileParked(worker) {
                undo.performClick()
                assertEquals("the restore ran inside the tap", deleted,
                        ledger.deletedOf(ledger.transactionRows(split) + ledger.transactionRows(plain)))
            }
            assertEquals(stored, ledger.transactionRows(split) + ledger.transactionRows(plain))
            assertEquals(transferStored, ledger.transferRows(transfer))
            assertEquals(emptyList<String>(), shownDialogMessages())
        }
    }

    @Test
    fun aRestoreRefusedByItsChecksLeavesThatRowDeletedRestoresTheRestAndSaysWhyOnce() {
        val first = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        val payment = insertPayment()
        val second = insertSplitTransaction()
        val stored = ledger.transactionRows(first) + ledger.transactionRows(second)
        launch { activity ->
            val host = addHost(activity)
            whileParked(worker) {
                tapDelete(host, longArrayOf(first, payment, second))
            }
            val paymentDeleted = ledger.transactionRows(payment)
            assertNotEquals(setOf<Any?>(0L), ledger.deletedOf(paymentDeleted))
            moveBankToDollars()
            whileParked(worker) {
                undoOf(snackbarsOf(activity).single()).performClick()
            }
            assertEquals(stored, ledger.transactionRows(first) + ledger.transactionRows(second))
            assertEquals(paymentDeleted, ledger.transactionRows(payment))
            assertEquals(listOf(context.resources.getQuantityString(
                    R.plurals.message_error_restore_transactions_currency, 1, 1)), shownDialogMessages())
        }
    }

    @Test
    fun twoRestoresRefusedByTheirCurrencySayWhyForBoth() {
        val payment = insertPayment()
        val deposit = insertDeposit(ledger.insertSaving(bank))
        launch { activity ->
            val host = addHost(activity)
            whileParked(worker) {
                tapDelete(host, longArrayOf(payment, deposit))
            }
            val deleted = ledger.transactionRows(payment) + ledger.transactionRows(deposit)
            assertNotEquals(setOf<Any?>(0L), ledger.deletedOf(deleted))
            ledger.database().execSQL("UPDATE wallets SET wallet_currency = 'USD' WHERE wallet_id = $bank")
            whileParked(worker) {
                undoOf(snackbarsOf(activity).single()).performClick()
            }
            assertEquals(deleted, ledger.transactionRows(payment) + ledger.transactionRows(deposit))
            assertEquals(listOf(context.resources.getQuantityString(
                    R.plurals.message_error_restore_transactions_currency, 2, 2)), shownDialogMessages())
        }
    }

    @Test
    fun twoRestoresRefusedForDifferentReasonsAreReportedTogetherInOneDialog() {
        val payment = insertPayment()
        val split = insertSplitTransaction()
        launch { activity ->
            val host = addHost(activity)
            whileParked(worker) {
                tapDelete(host, longArrayOf(payment, split))
            }
            val deleted = ledger.transactionRows(payment) + ledger.transactionRows(split)
            assertNotEquals(setOf<Any?>(0L), ledger.deletedOf(deleted))
            moveBankToDollars()
            ledger.database().execSQL("UPDATE categories SET category_type = " +
                    "${Contract.CategoryType.INCOME.value} WHERE category_id = $fuel")
            whileParked(worker) {
                undoOf(snackbarsOf(activity).single()).performClick()
            }
            assertEquals(deleted, ledger.transactionRows(payment) + ledger.transactionRows(split))
            assertEquals(listOf(context.resources.getQuantityString(
                    R.plurals.message_error_restore_transactions, 2, 2)), shownDialogMessages())
        }
    }

    @Test
    fun aRowDeletedEarlierIsLeftOutOfTheSnackbarAndItsUndoWithItsOwnTime() {
        val early = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        val plain = ledger.insertTransaction(ledger.transaction(cash, household, 700L))
        val stored = ledger.transactionRows(plain)
        assertEquals(1, ledger.flagTransactionDeleted(early, DELETED_AT))
        val earlyDeleted = ledger.transactionRows(early)
        launch { activity ->
            val host = addHost(activity)
            whileParked(worker) {
                tapDelete(host, longArrayOf(early, plain))
            }
            assertEquals(earlyDeleted, ledger.transactionRows(early))
            assertNotEquals(setOf<Any?>(0L), ledger.deletedOf(ledger.transactionRows(plain)))
            val snackbar = snackbarsOf(activity).single()
            assertEquals(context.resources.getQuantityString(R.plurals.snackbar_message_transactions_deleted, 1, 1),
                    snackbarText(snackbar))
            whileParked(worker) {
                undoOf(snackbar).performClick()
            }
            assertEquals(stored, ledger.transactionRows(plain))
            assertEquals(earlyDeleted, ledger.transactionRows(early))
            assertEquals(setOf<Any?>(DELETED_AT), ledger.deletedOf(ledger.transactionRows(early)))
            assertEquals(emptyList<String>(), shownDialogMessages())
        }
    }

    @Test
    @Config(sdk = [24])
    fun theSnackbarOfASelectionDeleteStaysUpForTheLongDuration() {
        val plain = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        launch { activity ->
            val host = addHost(activity)
            whileParked(worker) {
                tapDelete(host, longArrayOf(plain))
            }
            val snackbar = snackbarsOf(activity).single()
            idleMainLooperFor(2500L)
            assertEquals("the snackbar went away before the long duration", listOf(snackbar), snackbarsOf(activity))
            idleMainLooperFor(1000L)
            assertEquals("the snackbar stayed past the long duration", emptyList<View>(), snackbarsOf(activity))
        }
    }

    @Test
    fun aRefusedLegHoldsTheSnackbarBackUntilItsDialogIsClosed() {
        val transfer = ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null)
        val leg = ledger.legsOf(transfer).first()
        val plain = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        val stored = ledger.transactionRows(plain)
        launch { activity ->
            val host = addHost(activity)
            whileParked(worker) {
                tapDelete(host, longArrayOf(leg, plain))
            }
            assertEquals(listOf(context.getString(R.string.message_error_delete_transaction_of_transfer)),
                    shownDialogMessages())
            assertEquals(emptyList<View>(), snackbarsOf(activity))
            refusedDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            idleMainLooper()
            assertUndoBringsBackTheOneRow(activity, host, plain, stored)
        }
    }

    @Test
    fun aRefusedLegDialogClosedWithBackStillShowsTheSnackbar() {
        val transfer = ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null)
        val leg = ledger.legsOf(transfer).first()
        val plain = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        val stored = ledger.transactionRows(plain)
        launch { activity ->
            val host = addHost(activity)
            whileParked(worker) {
                tapDelete(host, longArrayOf(leg, plain))
            }
            assertEquals(emptyList<View>(), snackbarsOf(activity))
            refusedDialog().cancel()
            idleMainLooper()
            assertUndoBringsBackTheOneRow(activity, host, plain, stored)
        }
    }

    @Test
    fun aRefusedLegDialogClosedAfterTheScreenIsGoneShowsNoSnackbar() {
        val transfer = ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null)
        val leg = ledger.legsOf(transfer).first()
        val plain = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        launch { activity ->
            val host = addHost(activity)
            whileParked(worker) {
                tapDelete(host, longArrayOf(leg, plain))
            }
            val dialog = refusedDialog()
            removeHost(activity, host)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            idleMainLooper()
            assertEquals(emptyList<String>(), shownDialogMessages())
            assertEquals(emptyList<View>(), snackbarsOf(activity))
            assertNotEquals(setOf<Any?>(0L), ledger.deletedOf(ledger.transactionRows(plain)))
        }
    }

    @Test
    fun aSelectionOfOnlyARefusedLegShowsTheDialogAndNoSnackbar() {
        val transfer = ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null)
        val leg = ledger.legsOf(transfer).first()
        val transferStored = ledger.transferRows(transfer)
        launch { activity ->
            val host = addHost(activity)
            whileParked(worker) {
                tapDelete(host, longArrayOf(leg))
            }
            assertEquals(listOf(context.getString(R.string.message_error_delete_transaction_of_transfer)),
                    shownDialogMessages())
            refusedDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            idleMainLooper()
            assertEquals(emptyList<String>(), shownDialogMessages())
            assertEquals(emptyList<View>(), snackbarsOf(activity))
            assertEquals(transferStored, ledger.transferRows(transfer))
        }
    }

    @Test
    fun aDeleteThatComesBackAfterTheScreenIsGoneShowsNothingAndStillLands() {
        val plain = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        launch { activity ->
            val host = addHost(activity)
            whileParked(worker) {
                tapDelete(host, longArrayOf(plain))
                removeHost(activity, host)
            }
            assertEquals(emptyList<View>(), snackbarsOf(activity))
            assertNull(ShadowDialog.getLatestDialog())
            assertNotEquals(setOf<Any?>(0L), ledger.deletedOf(ledger.transactionRows(plain)))
        }
    }

    @Test
    fun aRefusedRestoreThatComesBackAfterTheScreenIsGoneShowsNothing() {
        val plain = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        val payment = insertPayment()
        val stored = ledger.transactionRows(plain)
        launch { activity ->
            val host = addHost(activity)
            whileParked(worker) {
                tapDelete(host, longArrayOf(plain, payment))
            }
            val paymentDeleted = ledger.transactionRows(payment)
            moveBankToDollars()
            val undo = undoOf(snackbarsOf(activity).single())
            whileParked(worker) {
                undo.performClick()
                removeHost(activity, host)
            }
            assertNull(ShadowDialog.getLatestDialog())
            assertEquals(emptyList<View>(), snackbarsOf(activity))
            assertEquals(stored, ledger.transactionRows(plain))
            assertEquals(paymentDeleted, ledger.transactionRows(payment))
        }
    }

    private fun launch(action: (BackupListActivity) -> Unit) {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { action(it) }
        }
    }

    private fun tapDelete(host: AppBarHost, ids: LongArray) {
        val forwarder = SelectionForwarder()
        val adapter = TransactionCursorAdapter(forwarder)
        val selectionMode = TransactionSelectionMode(host, host, adapter)
        forwarder.selectionMode = selectionMode
        adapter.setSelectedIds(ids)
        selectionMode.onMenuItemClick(host.primaryToolbar.menu.findItem(R.id.action_delete_selected_items))
        DeletedItemScreen.idleMainLooper()
    }

    private fun refusedDialog(): AlertDialog = ShadowDialog.getLatestDialog() as AlertDialog

    private fun assertUndoBringsBackTheOneRow(activity: BackupListActivity, host: AppBarHost, row: Long,
                                              stored: List<Map<String, Any?>>) {
        val snackbar = snackbarsOf(activity).single()
        assertSame("the snackbar is not on the primary panel", host.primaryPanel, snackbar.parent)
        assertEquals("1 transaction deleted", snackbarText(snackbar))
        assertNotEquals(setOf<Any?>(0L), ledger.deletedOf(ledger.transactionRows(row)))
        whileParked(worker) {
            undoOf(snackbar).performClick()
        }
        assertEquals(stored, ledger.transactionRows(row))
        assertEquals(emptyList<String>(), shownDialogMessages())
    }

    private fun insertSplitTransaction(): Long = ledger.insertSplitTransaction(cash, groceries, listOf(household, fuel),
            listOf(ann), listOf(ledger.insertAttachment("receipt")), null)

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

    private class SelectionForwarder : TransactionCursorAdapter.ActionListener {

        var selectionMode: TransactionSelectionMode? = null

        override fun onHeaderClick(startDate: Date, endDate: Date) {
        }

        override fun onTransactionClick(id: Long) {
        }

        override fun onSelectionChanged(count: Int) {
            selectionMode?.onSelectionChanged(count)
        }
    }
}
