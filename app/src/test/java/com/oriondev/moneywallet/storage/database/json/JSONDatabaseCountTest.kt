package com.oriondev.moneywallet.storage.database.json

import android.content.Context

import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.storage.database.BackupCount
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.ImportException
import com.oriondev.moneywallet.storage.database.SQLDatabaseCount
import com.oriondev.moneywallet.storage.database.SQLDatabaseImporter
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.database.backup.DefaultBackupImporter
import com.oriondev.moneywallet.storage.preference.PreferenceManager

import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.util.Zip4jConstants

import org.apache.commons.io.FileUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.io.File
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
class JSONDatabaseCountTest {

    companion object {

        private const val PASSWORD = "secret"

        private val ARRAYS = listOf(JSONDatabase.Currency.ARRAY, JSONDatabase.Wallet.ARRAY, JSONDatabase.Category.ARRAY,
                JSONDatabase.Event.ARRAY, JSONDatabase.Place.ARRAY, JSONDatabase.Person.ARRAY, JSONDatabase.EventPeople.ARRAY,
                JSONDatabase.Debt.ARRAY, JSONDatabase.DebtPeople.ARRAY, JSONDatabase.Budget.ARRAY, JSONDatabase.BudgetWallet.ARRAY,
                JSONDatabase.BudgetCategory.ARRAY, JSONDatabase.CategoryRule.ARRAY, JSONDatabase.Saving.ARRAY,
                JSONDatabase.RecurrentTransaction.ARRAY, JSONDatabase.RecurrentTransfer.ARRAY, JSONDatabase.Transaction.ARRAY,
                JSONDatabase.TransactionSplit.ARRAY, JSONDatabase.TransactionPeople.ARRAY, JSONDatabase.TransactionModel.ARRAY,
                JSONDatabase.Transfer.ARRAY, JSONDatabase.TransferPeople.ARRAY, JSONDatabase.TransferModel.ARRAY,
                JSONDatabase.Attachment.ARRAY, JSONDatabase.TransactionAttachment.ARRAY, JSONDatabase.TransferAttachment.ARRAY)

        private val FROM_VERSION = mapOf(JSONDatabase.Currency.ARRAY to 2, JSONDatabase.BudgetCategory.ARRAY to 3,
                JSONDatabase.CategoryRule.ARRAY to 4, JSONDatabase.TransactionSplit.ARRAY to 5)

        private const val DATE = "\"date\":\"2026-01-01 10:00:00\""

        private val ROWS = mapOf(
                JSONDatabase.Currency.ARRAY to "[{\"iso\":\"EUR\",\"name\":\"Euro\",\"symbol\":\"E\",\"decimals\":2}]",
                JSONDatabase.Wallet.ARRAY to "[{\"id\":\"cash\",\"name\":\"Cash\",\"currency\":\"EUR\"}," +
                        "{\"id\":\"bank\",\"name\":\"Bank\",\"currency\":\"EUR\",\"deleted\":false}," +
                        "{\"id\":\"old\",\"name\":\"Old\",\"currency\":\"EUR\",\"deleted\":true}]",
                JSONDatabase.Category.ARRAY to "[{\"id\":\"groceries\",\"name\":\"Groceries\",\"icon\":\"food\",\"type\":1}]",
                JSONDatabase.Transaction.ARRAY to "[" +
                        "{\"id\":\"plain\",\"money\":100,$DATE,\"category\":\"groceries\",\"direction\":0,\"type\":0,\"wallet\":\"cash\"}," +
                        "{\"id\":\"later\",\"money\":200,$DATE,\"category\":\"groceries\",\"direction\":0,\"type\":0,\"wallet\":\"bank\"}," +
                        "{\"id\":\"gone\",\"money\":300,$DATE,\"category\":\"groceries\",\"direction\":0,\"type\":0,\"wallet\":\"cash\",\"deleted\":true}," +
                        "{\"id\":\"out\",\"money\":400,$DATE,\"category\":\"groceries\",\"direction\":0,\"type\":1,\"wallet\":\"cash\"}," +
                        "{\"id\":\"in\",\"money\":400,$DATE,\"category\":\"groceries\",\"direction\":1,\"type\":1,\"wallet\":\"bank\"}]",
                JSONDatabase.Transfer.ARRAY to "[{\"id\":\"move\",$DATE,\"from\":\"out\",\"to\":\"in\"}]")
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val created = mutableListOf<File>()

    @Before
    fun setUp() {
        PreferenceManager.initialize(context)
        TestDatabases.useFreshDatabase(context)
    }

    @After
    fun tearDown() {
        DataContentProvider.closeStagingDatabase()
        for (file in created) {
            FileUtils.deleteQuietly(file)
        }
    }

    @Test
    fun aBackupIsCountedWithoutItsDeletedWalletsTransactionsAndTransfers() {
        val backup = backupWith(json(listOf(header(JSONDatabase.VERSION)) + arrays(JSONDatabase.VERSION, mapOf(
                JSONDatabase.Currency.ARRAY to "[{\"iso\":\"EUR\"}]",
                JSONDatabase.Wallet.ARRAY to "[{\"id\":\"cash\"},{\"id\":\"bank\",\"deleted\":false},{\"id\":\"old\",\"deleted\":true}]",
                JSONDatabase.Category.ARRAY to "[{\"id\":\"groceries\",\"type\":1}]",
                JSONDatabase.Transaction.ARRAY to "[{\"id\":\"plain\",\"type\":0},{\"id\":\"untyped\"},{\"id\":\"debt\",\"type\":2}," +
                        "{\"id\":\"saving\",\"type\":3},{\"id\":\"leg\",\"type\":1},{\"id\":\"gone\",\"type\":0,\"deleted\":true}]",
                JSONDatabase.TransactionPeople.ARRAY to "[{\"id\":\"person\"}]",
                JSONDatabase.Transfer.ARRAY to "[{\"id\":\"move\"},{\"id\":\"moved\",\"deleted\":true}]",
                JSONDatabase.TransferPeople.ARRAY to "[{\"id\":\"person\"}]"))))

        assertEquals(BackupCount(5, 2), JSONDatabaseCount.count(backup, null))
    }

    @Test
    fun aBackupWithEveryRowDeletedCountsNothing() {
        val backup = backupWith(json(listOf(header(JSONDatabase.VERSION)) + arrays(JSONDatabase.VERSION, mapOf(
                JSONDatabase.Wallet.ARRAY to "[{\"id\":\"old\",\"deleted\":true}]",
                JSONDatabase.Transaction.ARRAY to "[{\"id\":\"gone\",\"deleted\":true},{\"id\":\"leg\",\"type\":1}]",
                JSONDatabase.Transfer.ARRAY to "[{\"id\":\"moved\",\"deleted\":true}]"))))

        assertEquals(BackupCount(0, 0), JSONDatabaseCount.count(backup, null))
    }

    @Test
    fun aBackupFromANewerVersionIsRefusedByTheImporterCheck() {
        val backup = backupWith(json(listOf(header(JSONDatabase.MAX_SUPPORTED_VERSION + 1)) + arrays(JSONDatabase.VERSION)))

        assertEquals("This backup belongs to a newer version of the application and cannot be imported. " +
                "Please update the application to restore it.", refusal(backup))
    }

    @Test
    fun aBackupOlderThanTheOldestSupportedIsRefusedByTheImporterCheck() {
        val backup = backupWith(json(listOf(header(JSONDatabase.MIN_SUPPORTED_VERSION - 1)) + arrays(JSONDatabase.VERSION)))

        assertEquals("This backup is too old and no more supported by this version of the application. " +
                "It cannot be restored.", refusal(backup))
    }

    @Test
    fun aValidVersionOneFileIsCountedAndRestored() {
        val backup = backupWith(json(listOf(header(1)) + arrays(1)))

        assertEquals(BackupCount(3, 2), JSONDatabaseCount.count(backup, null))
        restore(backup)
        assertEquals(BackupCount(3, 2), SQLDatabaseCount.count(context.contentResolver))
    }

    @Test
    fun aValidVersionTwoFileIsCountedAndRestored() {
        val backup = backupWith(json(listOf(header(2)) + arrays(2)))

        assertEquals(BackupCount(3, 2), JSONDatabaseCount.count(backup, null))
        restore(backup)
        assertEquals(BackupCount(3, 2), SQLDatabaseCount.count(context.contentResolver))
    }

    @Test
    fun aValidVersionThreeFileIsCountedAndRestored() {
        val backup = backupWith(json(listOf(header(3)) + arrays(3)))

        assertEquals(BackupCount(3, 2), JSONDatabaseCount.count(backup, null))
        restore(backup)
        assertEquals(BackupCount(3, 2), SQLDatabaseCount.count(context.contentResolver))
    }

    @Test
    fun aValidVersionFourFileIsCountedAndRestored() {
        val backup = backupWith(json(listOf(header(4)) + arrays(4)))

        assertEquals(BackupCount(3, 2), JSONDatabaseCount.count(backup, null))
        restore(backup)
        assertEquals(BackupCount(3, 2), SQLDatabaseCount.count(context.contentResolver))
    }

    @Test
    fun aValidVersionFiveFileIsCountedAndRestored() {
        val backup = backupWith(json(listOf(header(5)) + arrays(5)))

        assertEquals(BackupCount(3, 2), JSONDatabaseCount.count(backup, null))
        restore(backup)
        assertEquals(BackupCount(3, 2), SQLDatabaseCount.count(context.contentResolver))
    }

    @Test
    fun aValidVersionSixFileIsCountedAndRestored() {
        val backup = backupWith(json(listOf(header(6)) + arrays(6)))

        assertEquals(BackupCount(3, 2), JSONDatabaseCount.count(backup, null))
        restore(backup)
        assertEquals(BackupCount(3, 2), SQLDatabaseCount.count(context.contentResolver))
    }

    @Test
    fun aFileWithoutAHeaderIsRefusedByTheCountAndTheImporter() {
        val backup = backupWith(json(arrays(6)))

        refusal(backup)
        importRefusal(backup)
    }

    @Test
    fun aFileWithItsHeaderUnderAnotherNameIsRefusedByTheCountAndTheImporter() {
        val backup = backupWith(json(listOf("\"backup\":{\"version_code\":6}") + arrays(6)))

        refusal(backup)
        importRefusal(backup)
    }

    @Test
    fun aFileWithItsHeaderLastAndANewerVersionIsRefusedByTheCountAndTheImporter() {
        val backup = backupWith(json(arrays(6) + header(99)))

        refusal(backup)
        importRefusal(backup)
    }

    @Test
    fun aVersionSixFileWithWalletsBeforeCurrenciesIsRefusedByTheCountAndTheImporter() {
        val arrays = arrays(6)
        val backup = backupWith(json(listOf(header(6), arrays[1], arrays[0]) + arrays.drop(2)))

        assertEquals("Wrong array name (expected = 'currencies')", refusal(backup))
        assertEquals("Wrong array name (expected = 'currencies')", importRefusal(backup))
    }

    @Test
    fun aFileWithoutCategoriesIsRefusedByTheCountAndTheImporter() {
        val backup = backupWith(json(listOf(header(6)) + arrays(6).filter { !it.startsWith("\"${JSONDatabase.Category.ARRAY}\"") }))

        assertEquals("Wrong array name (expected = 'categories')", refusal(backup))
        assertEquals("Wrong array name (expected = 'categories')", importRefusal(backup))
    }

    @Test
    fun aFileWithANameAfterTheTransferAttachmentsIsRefusedByTheCountAndTheImporter() {
        val backup = backupWith(json(listOf(header(6)) + arrays(6) + "\"notes\":[]"))

        refusal(backup)
        importRefusal(backup)
    }

    @Test
    fun aVersionOneFileWithCurrenciesIsRefusedByTheCountAndTheImporter() {
        val backup = backupWith(json(listOf(header(1), "\"${JSONDatabase.Currency.ARRAY}\":${ROWS[JSONDatabase.Currency.ARRAY]}") + arrays(1)))

        assertEquals("Wrong array name (expected = 'wallets')", refusal(backup))
        assertEquals("Wrong array name (expected = 'wallets')", importRefusal(backup))
    }

    @Test
    fun aStoredBackupWithOneByteChangedFailsTheCount() {
        val json = json(listOf(header(6)) + arrays(6))
        val backup = backupWith(json, "stored.mwbx", stored(null))
        assertEquals(BackupCount(3, 2), JSONDatabaseCount.count(backup, null))

        flip(backup, json.indexOf("Cash"))

        refusal(backup)
    }

    @Test
    fun anEncryptedBackupWithOneBitChangedFailsTheCount() {
        val json = json(listOf(header(6)) + arrays(6))
        val backup = backupWith(json, "stored.mwbs", stored(PASSWORD))
        assertEquals(BackupCount(3, 2), JSONDatabaseCount.count(backup, PASSWORD))

        flip(backup, 16 + 2 + json.indexOf("Cash"))

        refusal(backup, PASSWORD)
    }

    private fun header(version: Int): String = "\"${JSONDatabase.Header.OBJECT}\":{\"${JSONDatabase.Header.VERSION_CODE}\":$version}"

    private fun arrays(version: Int, rows: Map<String, String> = ROWS): List<String> =
            ARRAYS.filter { version >= (FROM_VERSION[it] ?: 0) }.map { "\"$it\":${rows[it] ?: "[]"}" }

    private fun json(names: List<String>): String = names.joinToString(",", "{", "}")

    private fun refusal(backup: File, password: String? = null): String? {
        try {
            JSONDatabaseCount.count(backup, password)
        } catch (e: ImportException) {
            return e.message
        }
        fail("the backup was counted")
        return null
    }

    private fun importRefusal(backup: File): String? {
        try {
            restore(backup)
        } catch (e: Exception) {
            return e.message
        }
        fail("the backup was restored")
        return null
    }

    private fun restore(backup: File) {
        val folder = File(context.cacheDir, "count_import")
        FileUtils.forceMkdir(folder)
        created.add(folder)
        DefaultBackupImporter(context, backup, null)
                .importDatabase(folder, context.getDatabasePath(SQLDatabaseImporter.DATABASE_NAME).parentFile!!)
    }

    private fun flip(backup: File, offset: Int) {
        val bytes = FileUtils.readFileToByteArray(backup)
        val data = 30 + (bytes[26].toInt() and 0xff) + (bytes[27].toInt() and 0xff shl 8) +
                (bytes[28].toInt() and 0xff) + (bytes[29].toInt() and 0xff shl 8)
        bytes[data + offset] = (bytes[data + offset].toInt() xor 0x01).toByte()
        FileUtils.writeByteArrayToFile(backup, bytes)
    }

    private fun stored(password: String?): ZipParameters {
        val parameters = ZipParameters()
        parameters.setCompressionMethod(Zip4jConstants.COMP_STORE)
        if (password != null) {
            parameters.setEncryptFiles(true)
            parameters.setEncryptionMethod(Zip4jConstants.ENC_METHOD_AES)
            parameters.setAesKeyStrength(Zip4jConstants.AES_STRENGTH_256)
            parameters.setPassword(password)
        }
        return parameters
    }

    private fun backupWith(json: String, name: String = "count.mwbx", parameters: ZipParameters = ZipParameters()): File {
        val folder = File(context.cacheDir, "count_json")
        FileUtils.forceMkdir(folder)
        created.add(folder)
        val database = File(folder, "database.json")
        FileUtils.writeStringToFile(database, json, StandardCharsets.UTF_8)
        val backup = File(context.cacheDir, name)
        created.add(backup)
        FileUtils.deleteQuietly(backup)
        parameters.setRootFolderInZip("databases/")
        ZipFile(backup).addFile(database, parameters)
        return backup
    }
}
