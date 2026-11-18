package com.oriondev.moneywallet.storage.database.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri

import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.DeletedLedger
import com.oriondev.moneywallet.storage.database.SplitPartsCodec
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.database.data.csv.CSVDataImporter
import com.oriondev.moneywallet.storage.preference.PreferenceManager

import org.apache.commons.io.FileUtils
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.io.File
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
class DeletedRowImportTest {

    companion object {

        private const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"

        private const val HEADER = "wallet,currency,category,datetime,money,description\n"
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.initialize(context)
        TestDatabases.useFreshDatabase(context)
    }

    @Test
    fun aDeletedRowCountsAsImportedUntilItIsDeletedForGood() {
        val file = write(HEADER +
                "Checking,EUR,Food,2026-01-02 10:00:00,-5.00,Shop\n" +
                "Checking,EUR,Food,2026-01-03 10:00:00,-7.00,Bakery\n")
        assertEquals(0, import(file))
        val shop = idOf("Shop")
        assertEquals(1, context.contentResolver.delete(Uri.withAppendedPath(transactionUri(shop), "deleted"), null, null))

        assertEquals(2, import(file))
        assertEquals(2, rowsOf("Checking"))

        assertEquals(1, context.contentResolver.delete(transactionUri(shop), null, null))
        assertEquals(1, import(file))
        assertEquals(2, rowsOf("Checking"))
        assertEquals(1, transactions("Shop").size)
    }

    @Test
    fun theLinesOfADeletedSplitTransactionCountAsImported() {
        val wallet = insert(DataContentProvider.CONTENT_WALLETS, ContentValues().apply {
            put(Contract.Wallet.NAME, "Checking")
            put(Contract.Wallet.ICON, ICON)
            put(Contract.Wallet.CURRENCY, "EUR")
            put(Contract.Wallet.START_MONEY, 0L)
            put(Contract.Wallet.COUNT_IN_TOTAL, true)
            put(Contract.Wallet.ARCHIVED, false)
        })
        val food = category("Food")
        val fuel = category("Fuel")
        val receipt = insert(DataContentProvider.CONTENT_TRANSACTIONS, ContentValues().apply {
            put(Contract.Transaction.MONEY, 1000L)
            put(Contract.Transaction.DATE, "2026-01-02 10:00:00")
            put(Contract.Transaction.DESCRIPTION, "Shop")
            put(Contract.Transaction.CATEGORY_ID, food)
            put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE)
            put(Contract.Transaction.TYPE, Contract.TransactionType.STANDARD)
            put(Contract.Transaction.WALLET_ID, wallet)
            put(Contract.Transaction.CONFIRMED, true)
            put(Contract.Transaction.COUNT_IN_TOTAL, true)
            put(Contract.Transaction.SPLIT_PARTS, SplitPartsCodec.encode(listOf(SplitPartsCodec.Part(fuel, 300L, null))))
        })
        val file = write(HEADER +
                "Checking,EUR,Food,2026-01-02 10:00:00,-7.00,Shop\n" +
                "Checking,EUR,Fuel,2026-01-02 10:00:00,-3.00,Shop\n")
        assertEquals(2, import(file))
        assertEquals(1, context.contentResolver.delete(Uri.withAppendedPath(transactionUri(receipt), "deleted"), null, null))

        assertEquals(2, import(file))

        assertEquals(listOf(receipt), transactions("Shop"))
    }

    @Test
    fun aDeletedRowInAnotherWalletDoesNotCountAsImported() {
        val ledger = DeletedLedger(context)
        ledger.insertWallet("Cash", "EUR")
        val bank = ledger.insertWallet("Bank", "EUR")
        val deleted = ledger.insertTransaction(ledger.transaction(bank, category("Food"), 500L))
        assertEquals(1, ledger.flagTransactionDeleted(deleted))
        val file = write(HEADER + "Cash,EUR,Food,${DeletedLedger.DATE},-5.00,Receipt\n")

        assertEquals(0, import(file))

        assertEquals(1, rowsOf("Cash"))
        assertEquals(1, rowsOf("Bank"))
    }

    @Test
    fun theLegsOfALiveTransferCountAsImportedOnlyForTheFee() {
        importLegs(false)
    }

    @Test
    fun theLegsOfADeletedTransferCountAsTheyDoWhileItIsLive() {
        importLegs(true)
    }

    private fun importLegs(deleted: Boolean) {
        val ledger = DeletedLedger(context)
        val cash = ledger.insertWallet("Cash", "EUR")
        val bank = ledger.insertWallet("Bank", "EUR")
        val transfer = ledger.insertTransfer(cash, bank, cash, emptyList(), emptyList(), null)
        if (deleted) {
            assertEquals(1, ledger.flagTransferDeleted(transfer))
        }
        val file = write(HEADER +
                "Cash,EUR,Transfer,${DeletedLedger.DATE},-50.00,Move\n" +
                "Cash,EUR,Fee,${DeletedLedger.DATE},-1.50,Move\n")

        assertEquals(1, import(file))

        assertEquals(3, rowsOf("Cash"))
        assertEquals(4, transactions("Move").size)
    }

    private fun import(file: File): Int {
        val importer = CSVDataImporter(context, file)
        try {
            importer.importData()
            return importer.alreadySavedRows
        } finally {
            importer.close()
        }
    }

    private fun write(contents: String): File {
        val file = File(context.cacheDir, "ledger.csv")
        FileUtils.writeStringToFile(file, contents, StandardCharsets.UTF_8)
        return file
    }

    private fun category(name: String): Long = insert(DataContentProvider.CONTENT_CATEGORIES, ContentValues().apply {
        put(Contract.Category.NAME, name)
        put(Contract.Category.ICON, ICON)
        put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.value)
        put(Contract.Category.SHOW_REPORT, true)
    })

    private fun insert(uri: Uri, values: ContentValues): Long = ContentUris.parseId(context.contentResolver.insert(uri, values)!!)

    private fun transactionUri(transaction: Long): Uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, transaction)

    private fun idOf(description: String): Long = transactions(description).single()

    private fun transactions(description: String): List<Long> =
            ids(DataContentProvider.CONTENT_TRANSACTIONS, description) +
                    ids(Uri.withAppendedPath(DataContentProvider.CONTENT_TRANSACTIONS, "deleted"), description)

    private fun ids(uri: Uri, description: String): List<Long> = context.contentResolver.query(uri,
            arrayOf(Contract.Transaction.ID), Contract.Transaction.DESCRIPTION + " = ?", arrayOf(description),
            null)!!.use { cursor ->
        generateSequence { if (cursor.moveToNext()) cursor.getLong(0) else null }.toList()
    }

    private fun rowsOf(wallet: String): Int = context.contentResolver.query(DataContentProvider.CONTENT_WALLETS,
            arrayOf(Contract.Wallet.ID), Contract.Wallet.NAME + " = ?", arrayOf(wallet), null)!!.use { cursor ->
        cursor.moveToFirst()
        val walletId = cursor.getLong(0)
        transactionsIn(DataContentProvider.CONTENT_TRANSACTIONS, walletId) +
                transactionsIn(Uri.withAppendedPath(DataContentProvider.CONTENT_TRANSACTIONS, "deleted"), walletId)
    }

    private fun transactionsIn(uri: Uri, wallet: Long): Int = context.contentResolver.query(uri,
            arrayOf(Contract.Transaction.ID), Contract.Transaction.WALLET_ID + " = ?", arrayOf(wallet.toString()),
            null)!!.use { it.count }
}
