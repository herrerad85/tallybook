package com.oriondev.moneywallet.storage.database

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri

import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.storage.preference.PreferenceManager

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class DeletedMasterTransactionTest {

    companion object {

        private const val DELETED_AT = 1765000000000L

        private val DATE = LocalDate.now().minusDays(5).toString()
    }

    private lateinit var ledger: DeletedLedger
    private var cash = 0L
    private var bank = 0L
    private var ann = 0L
    private var bob = 0L
    private var cara = 0L
    private var debt = 0L
    private var master = 0L

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        PreferenceManager.initialize(context)
        TestDatabases.useFreshDatabase(context)
        ledger = DeletedLedger(context)
        cash = ledger.insertWallet("Cash", "EUR")
        bank = ledger.insertWallet("Bank", "EUR")
        ann = ledger.insertPerson("Ann")
        bob = ledger.insertPerson("Bob")
        cara = ledger.insertPerson("Cara")
        debt = ledger.insertDebt(cash, 2000L, listOf(ann, bob))
        master = ledger.rows("transactions", "transaction_debt = $debt").single()["transaction_id"] as Long
    }

    @Test
    fun aDeletedMasterTransactionMovesWithItsDebtAndGoesWithTheWalletItMovedTo() {
        ledger.flagTransactionDeleted(master, DELETED_AT)

        assertEquals(1, updateDebt(bank, listOf(ann, bob)))

        assertEquals(bank, wallet(master))
        assertEquals(setOf<Any?>(DELETED_AT), ledger.deletedOf(ledger.transactionRows(master)))
        assertEquals(1, deleteWallet(bank))
        assertEquals(emptyList<Map<String, Any?>>(), ledger.transactionRows(master))
        assertEquals(0L, ledger.count("debts", "debt_id = $debt"))
    }

    @Test
    fun aDeletedMasterTransactionStaysWithItsDebtWhenTheWalletItLeftIsDeleted() {
        val time = (ledger.rows("transactions", "transaction_id = $master").single()["transaction_date"] as String).substring(11)
        ledger.flagTransactionDeleted(master, DELETED_AT)
        assertEquals(1, updateDebt(bank, listOf(ann, bob)))

        assertEquals(1, deleteWallet(cash))

        val rows = ledger.transactionRows(master)
        assertEquals(3, rows.size)
        assertEquals(setOf<Any?>(DELETED_AT), ledger.deletedOf(rows))
        assertEquals(1L, ledger.count("debts", "debt_id = $debt"))
        assertEquals(1, ledger.restoreTransaction(master))
        val row = ledger.view(ledger.transactionUri(master)).single()
        assertEquals(bank, row[Contract.Transaction.WALLET_ID])
        assertEquals(3500L, row[Contract.Transaction.MONEY])
        assertEquals("$DATE $time", row[Contract.Transaction.DATE])
        assertEquals("Car loan", row[Contract.Transaction.DESCRIPTION])
    }

    @Test
    fun aDeletedMasterTransactionKeepsItsPeopleUntilItIsRestored() {
        ledger.flagTransactionDeleted(master, DELETED_AT)
        val people = ledger.rows("transaction_people", "_transaction = $master")

        assertEquals(1, updateDebt(bank, listOf(bob, cara)))

        assertEquals(bank, wallet(master))
        assertEquals(people, ledger.rows("transaction_people", "_transaction = $master"))
        assertEquals(setOf(ann, bob), ledger.idsOf(ledger.viewValue(Uri.withAppendedPath(
                DataContentProvider.CONTENT_TRANSACTIONS, "deleted"), Contract.Transaction.PEOPLE_IDS,
                Contract.Transaction.ID + " = ?", master.toString())))
        assertEquals(1, ledger.restoreTransaction(master))
        assertEquals(setOf(bob, cara), ledger.idsOf(ledger.view(ledger.transactionUri(master)).single()[
                Contract.Transaction.PEOPLE_IDS]))
    }

    @Test
    fun aMasterTransactionDeletedAtOneKeepsItsLinks() {
        val attachment = ledger.insertAttachment("contract")
        ledger.database().execSQL("INSERT INTO transaction_attachment (_transaction, _attachment, uuid, last_edit, " +
                "deleted) VALUES (?, ?, 'master_attachment', 1, 1)", arrayOf<Any>(master, attachment))
        ledger.database().execSQL("UPDATE transactions SET deleted = 1 WHERE transaction_id = $master")
        ledger.database().execSQL("UPDATE transaction_people SET deleted = 1 WHERE _transaction = $master")
        ledger.database().execSQL("UPDATE attachments SET deleted = 1 WHERE attachment_id = $attachment")
        val links = ledger.rows("transaction_people", "_transaction = $master") +
                ledger.rows("transaction_attachment", "_transaction = $master")
        assertEquals(setOf<Any?>(1L), ledger.deletedOf(links))
        assertEquals(3, links.size)

        assertEquals(1, updateDebt(bank, listOf(cara)))

        assertEquals(bank, wallet(master))
        assertEquals(links, ledger.rows("transaction_people", "_transaction = $master") +
                ledger.rows("transaction_attachment", "_transaction = $master"))
    }

    @Test
    fun aDeletedPaymentOfTheDebtIsNotWrittenByItsUpdate() {
        val payment = ledger.insertTransaction(
                ledger.transaction(cash, ledger.systemCategory(Contract.CategoryTag.PAID_DEBT), 500L).apply {
                    put(Contract.Transaction.TYPE, Contract.TransactionType.DEBT)
                    put(Contract.Transaction.DEBT_ID, debt)
                    put(Contract.Transaction.PEOPLE_IDS, ledger.ids(listOf(ann)))
                })
        ledger.flagTransactionDeleted(payment, DELETED_AT)
        val rows = ledger.transactionRows(payment)

        assertEquals(1, updateDebt(bank, listOf(bob, cara)))

        assertEquals(rows, ledger.transactionRows(payment))
        assertEquals(bank, wallet(master))
    }

    @Test
    fun aLiveMasterTransactionFollowsItsDebt() {
        val time = (ledger.rows("transactions", "transaction_id = $master").single()["transaction_date"] as String).substring(11)

        assertEquals(1, updateDebt(bank, listOf(bob, cara)))

        val row = ledger.view(ledger.transactionUri(master)).single()
        assertEquals(bank, row[Contract.Transaction.WALLET_ID])
        assertEquals(3500L, row[Contract.Transaction.MONEY])
        assertEquals("$DATE $time", row[Contract.Transaction.DATE])
        assertEquals("Car loan", row[Contract.Transaction.DESCRIPTION])
        assertEquals("Signed", row[Contract.Transaction.NOTE])
        assertEquals(setOf(bob, cara), ledger.idsOf(row[Contract.Transaction.PEOPLE_IDS]))
        assertEquals(setOf<Any?>(0L), ledger.deletedOf(ledger.transactionRows(master)))
    }

    private fun updateDebt(wallet: Long, people: List<Long>): Int = ledger.resolver.update(
            ContentUris.withAppendedId(DataContentProvider.CONTENT_DEBTS, debt), ContentValues().apply {
        put(Contract.Debt.TYPE, Contract.DebtType.DEBT.value)
        put(Contract.Debt.ICON, DeletedLedger.ICON)
        put(Contract.Debt.DESCRIPTION, "Car loan")
        put(Contract.Debt.DATE, DATE)
        putNull(Contract.Debt.EXPIRATION_DATE)
        put(Contract.Debt.WALLET_ID, wallet)
        put(Contract.Debt.NOTE, "Signed")
        putNull(Contract.Debt.PLACE_ID)
        put(Contract.Debt.MONEY, 3500L)
        put(Contract.Debt.ARCHIVED, false)
        put(Contract.Debt.PEOPLE_IDS, ledger.ids(people))
        put(Contract.Debt.INSERT_MASTER_TRANSACTION, false)
    }, null, null)

    private fun deleteWallet(wallet: Long): Int =
            ledger.resolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, wallet), null, null)

    private fun wallet(transaction: Long): Any? =
            ledger.rows("transactions", "transaction_id = $transaction").single()["transaction_wallet"]
}
