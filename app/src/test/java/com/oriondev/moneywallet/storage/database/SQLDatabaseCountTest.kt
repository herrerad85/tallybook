package com.oriondev.moneywallet.storage.database

import android.content.Context

import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.storage.database.backup.DefaultBackupExporter
import com.oriondev.moneywallet.storage.database.json.JSONDatabaseCount
import com.oriondev.moneywallet.storage.preference.PreferenceManager

import org.apache.commons.io.FileUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.io.File

@RunWith(RobolectricTestRunner::class)
class SQLDatabaseCountTest {

    private lateinit var context: Context
    private lateinit var ledger: DeletedLedger
    private val created = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.initialize(context)
        TestDatabases.useFreshDatabase(context)
        ledger = DeletedLedger(context)
    }

    @After
    fun tearDown() {
        for (file in created) {
            FileUtils.deleteQuietly(file)
        }
    }

    @Test
    fun aLedgerExportedToABackupCountsSixTransactionsAndTwoWalletsInTheFileAndInTheApp() {
        val cash = ledger.insertWallet("Cash", "EUR")
        val bank = ledger.insertWallet("Bank", "EUR")
        val salary = ledger.insertCategory("Salary", Contract.CategoryType.INCOME)
        val groceries = ledger.insertCategory("Groceries", Contract.CategoryType.EXPENSE)
        ledger.insertTransaction(ledger.transaction(cash, salary, 200000L).apply {
            put(Contract.Transaction.DIRECTION, Contract.Direction.INCOME)
        })
        ledger.insertTransaction(ledger.transaction(cash, groceries, 3000L))
        ledger.insertDebt(cash, 10000L, emptyList())
        val saving = ledger.insertSaving(bank)
        ledger.insertTransaction(ledger.transaction(bank, ledger.systemCategory(Contract.CategoryTag.SAVING_DEPOSIT), 500L).apply {
            put(Contract.Transaction.TYPE, Contract.TransactionType.SAVING)
            put(Contract.Transaction.SAVING_ID, saving)
        })
        val transferWithTax = ledger.insertTransfer(cash, bank, cash, emptyList(), emptyList(), null)
        ledger.insertTransfer(bank, cash, null, emptyList(), emptyList(), null)
        ledger.flagTransactionDeleted(ledger.insertTransaction(ledger.transaction(cash, groceries, 700L)))
        ledger.flagTransferDeleted(ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null))
        assertEquals(2L, ledger.count("transactions", "deleted = 0 AND transaction_type = ${Contract.TransactionType.STANDARD}"))
        assertEquals(1L, ledger.count("transactions", "deleted != 0 AND transaction_type = ${Contract.TransactionType.STANDARD}"))
        assertEquals(1L, ledger.count("transactions", "deleted = 0 AND transaction_type = ${Contract.TransactionType.DEBT}"))
        assertEquals(1L, ledger.count("transactions", "deleted = 0 AND transaction_type = ${Contract.TransactionType.SAVING}"))
        assertEquals(2L, ledger.count("transfers", "deleted = 0"))
        assertEquals(1L, ledger.count("transfers", "deleted != 0"))
        assertEquals(3, ledger.legsOf(transferWithTax).size)
        assertEquals(2L, ledger.count("wallets", "deleted = 0"))

        val backup = File(context.cacheDir, "count.mwbx")
        created.add(backup)
        val folder = File(context.cacheDir, "count_export")
        FileUtils.forceMkdir(folder)
        created.add(folder)
        DefaultBackupExporter(context.contentResolver, backup, null).exportDatabase(folder)

        assertEquals(BackupCount(6, 2), SQLDatabaseCount.count(context.contentResolver))
        assertEquals(BackupCount(6, 2), JSONDatabaseCount.count(backup, null))
    }

    @Test
    fun aDeletedWalletIsNotCountedInTheApp() {
        ledger.insertWallet("Cash", "EUR")
        val old = ledger.insertWallet("Old", "EUR")
        ledger.database().execSQL("UPDATE wallets SET deleted = 1 WHERE wallet_id = $old")
        assertEquals(1L, ledger.count("wallets", "deleted != 0"))

        assertEquals(BackupCount(0, 1), SQLDatabaseCount.count(context.contentResolver))
    }
}
