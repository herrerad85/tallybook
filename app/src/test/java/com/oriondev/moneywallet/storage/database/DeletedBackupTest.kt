package com.oriondev.moneywallet.storage.database

import android.content.Context
import android.net.Uri

import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.storage.database.backup.DefaultBackupExporter
import com.oriondev.moneywallet.storage.database.backup.DefaultBackupImporter
import com.oriondev.moneywallet.storage.preference.PreferenceManager

import org.apache.commons.io.FileUtils
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class DeletedBackupTest {

    companion object {

        private const val DELETED_AT = 1765000000000L

        private const val DATABASE_ENTRY = "databases/database.json"

        private val FILES = listOf("receipt", "invoice", "slip", "statement")

        private val TABLES = listOf("transactions", "transaction_splits", "transaction_people", "transfers",
                "transfer_people", "attachments", "transaction_attachment", "transfer_attachment")
    }

    private lateinit var context: Context
    private lateinit var ledger: DeletedLedger
    private var transaction = 0L
    private var transfer = 0L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.initialize(context)
        TestDatabases.useFreshDatabase(context)
        ledger = DeletedLedger(context)
        val cash = ledger.insertWallet("Cash", "EUR")
        val bank = ledger.insertWallet("Bank", "EUR")
        val groceries = ledger.insertCategory("Groceries", Contract.CategoryType.EXPENSE)
        val household = ledger.insertCategory("Household", Contract.CategoryType.EXPENSE)
        val fuel = ledger.insertCategory("Fuel", Contract.CategoryType.EXPENSE)
        val people = listOf(ledger.insertPerson("Ann"), ledger.insertPerson("Bob"))
        transaction = ledger.insertSplitTransaction(cash, groceries, listOf(household, fuel), people,
                listOf(ledger.insertAttachment(FILES[0]), ledger.insertAttachment(FILES[1])), null)
        transfer = ledger.insertTransfer(cash, bank, cash, people,
                listOf(ledger.insertAttachment(FILES[2]), ledger.insertAttachment(FILES[3])), null)
    }

    @After
    fun tearDown() {
        DataContentProvider.closeStagingDatabase()
    }

    @Test
    fun aBackupCarriesDeletedItemsAndARestoreBringsThemBackWhole() {
        ledger.flagTransactionDeleted(transaction, DELETED_AT)
        ledger.flagTransferDeleted(transfer, DELETED_AT + 1)
        val transactionRows = stored(ledger.transactionRows(transaction))
        val transferRows = stored(ledger.transferRows(transfer))
        val transactionUuid = uuidOf("transactions", "transaction_id = $transaction")
        val transferUuid = uuidOf("transfers", "transfer_id = $transfer")

        val backup = exportBackup()
        ZipFile(backup).use { zip ->
            for (file in FILES) {
                assertTrue(file, zip.getEntry("attachments/$file") != null)
            }
        }
        FileUtils.cleanDirectory(ledger.attachmentFolder)
        restore(backup)

        val restoredTransaction = idOf("transactions", "transaction_id", transactionUuid)
        val restoredTransfer = idOf("transfers", "transfer_id", transferUuid)
        assertEquals(transactionRows, stored(ledger.transactionRows(restoredTransaction)))
        assertEquals(transferRows, stored(ledger.transferRows(restoredTransfer)))
        assertEquals(9, transactionRows.size)
        assertEquals(10, transferRows.size)
        assertEquals(setOf<Any?>(DELETED_AT), ledger.deletedOf(ledger.transactionRows(restoredTransaction)))
        assertEquals(setOf<Any?>(DELETED_AT + 1), ledger.deletedOf(ledger.transferRows(restoredTransfer)))
        for (file in FILES) {
            assertEquals("content of $file", FileUtils.readFileToString(File(ledger.attachmentFolder, file),
                    StandardCharsets.UTF_8))
        }

        assertEquals(1, ledger.restoreTransaction(restoredTransaction))
        assertEquals(1, ledger.restoreTransfer(restoredTransfer))
        val transactionUri = ledger.transactionUri(restoredTransaction)
        assertEquals(1, ledger.view(transactionUri).size)
        assertEquals(2, ledger.view(Uri.withAppendedPath(transactionUri, "people")).size)
        assertEquals(2, ledger.view(Uri.withAppendedPath(transactionUri, "splits")).size)
        assertEquals(2, ledger.view(Uri.withAppendedPath(transactionUri, "attachments")).size)
        val transferUri = ledger.transferUri(restoredTransfer)
        assertEquals(150L, ledger.view(transferUri).single()[Contract.Transfer.TRANSACTION_TAX_MONEY])
        assertEquals(2, ledger.view(Uri.withAppendedPath(transferUri, "people")).size)
        assertEquals(2, ledger.view(Uri.withAppendedPath(transferUri, "attachments")).size)
    }

    @Test
    fun aFileWithDeletedRowsAndNoTimeImportsThemWithOneImportTime() {
        ledger.flagTransactionDeleted(transaction, DELETED_AT)
        ledger.flagTransferDeleted(transfer, DELETED_AT + 1)
        val transactionUuid = uuidOf("transactions", "transaction_id = $transaction")
        val transferUuid = uuidOf("transfers", "transfer_id = $transfer")
        val backup = withoutDeletedAt(exportBackup())

        val start = System.currentTimeMillis()
        restore(backup)
        val end = System.currentTimeMillis()

        val rows = ledger.transactionRows(idOf("transactions", "transaction_id", transactionUuid)) +
                ledger.transferRows(idOf("transfers", "transfer_id", transferUuid))
        assertEquals(19, rows.size)
        val deletedAt = ledger.deletedOf(rows).single() as Long
        assertTrue("$deletedAt", deletedAt in start..end)
    }

    @Test
    fun aLedgerWithNothingDeletedExportsEveryRowAndNoTime() {
        val json = readDatabaseJson(exportBackup())

        assertFalse(json.contains("deleted_at"))
        val database = JSONObject(json)
        for (table in TABLES) {
            val objects = database.getJSONArray(table)
            assertEquals(table, ledger.count(table, "deleted = 0"), objects.length().toLong())
            assertEquals(table, ledger.count(table, "1"), objects.length().toLong())
            for (index in 0 until objects.length()) {
                assertFalse(table, objects.getJSONObject(index).getBoolean("deleted"))
            }
        }
    }

    private fun stored(rows: List<Map<String, Any?>>): Set<Pair<Any?, Any?>> = rows.map { it["uuid"] to it["deleted"] }.toSet()

    private fun uuidOf(table: String, where: String): String = ledger.rows(table, where).single()["uuid"] as String

    private fun idOf(table: String, column: String, uuid: String): Long = ledger.rows(table, "uuid = '$uuid'").single()[column] as Long

    private fun exportBackup(): File {
        val folder = File(context.cacheDir, "deleted_export")
        assertTrue(folder.mkdirs() || folder.isDirectory)
        val backup = File(context.cacheDir, "deleted.mwbx")
        val exporter = DefaultBackupExporter(context.contentResolver, backup, null)
        exporter.exportDatabase(folder)
        exporter.exportAttachments(ledger.attachmentFolder)
        return backup
    }

    private fun restore(backup: File) {
        val folder = File(context.cacheDir, "deleted_import")
        assertTrue(folder.mkdirs() || folder.isDirectory)
        val importer = DefaultBackupImporter(context, backup, null)
        importer.importDatabase(folder, context.getDatabasePath(SQLDatabaseImporter.DATABASE_NAME).parentFile!!)
        importer.importAttachments(ledger.attachmentFolder)
    }

    private fun readDatabaseJson(backup: File): String = ZipFile(backup).use { zip ->
        zip.getInputStream(zip.getEntry(DATABASE_ENTRY)).use { String(it.readBytes(), StandardCharsets.UTF_8) }
    }

    private fun withoutDeletedAt(backup: File): File {
        val database = JSONObject(readDatabaseJson(backup))
        var removed = 0
        for (table in TABLES) {
            val objects: JSONArray = database.getJSONArray(table)
            for (index in 0 until objects.length()) {
                if (objects.getJSONObject(index).remove("deleted_at") != null) {
                    removed++
                }
            }
        }
        assertEquals(19, removed)
        val rewritten = File(context.cacheDir, "deleted_without_time.mwbx")
        ZipFile(backup).use { zip ->
            ZipOutputStream(FileOutputStream(rewritten)).use { out ->
                for (entry in zip.entries()) {
                    out.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == DATABASE_ENTRY) {
                        out.write(database.toString().toByteArray(StandardCharsets.UTF_8))
                    } else {
                        zip.getInputStream(entry).use { it.copyTo(out) }
                    }
                    out.closeEntry()
                }
            }
        }
        return rewritten
    }
}
