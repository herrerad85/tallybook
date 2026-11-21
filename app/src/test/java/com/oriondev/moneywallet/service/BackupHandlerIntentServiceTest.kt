package com.oriondev.moneywallet.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.os.Looper
import android.util.Log

import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.api.BackendException
import com.oriondev.moneywallet.api.BackendServiceFactory
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.model.LocalFile
import com.oriondev.moneywallet.model.SAFFile
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.DeletedLedger
import com.oriondev.moneywallet.storage.database.ImportException
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.database.backup.DefaultBackupExporter
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.utils.DateUtils

import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.util.Zip4jConstants

import org.apache.commons.io.FileUtils
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowLooper

import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.util.Date

@RunWith(RobolectricTestRunner::class)
class BackupHandlerIntentServiceTest {

    companion object {

        private const val BEFORE = 111L
        private const val RESTORED = 222L
        private const val PASSWORD = "secret"
        private const val DATABASE_ENTRY = "databases/database.json"
        private const val FOLDER_NAME = "Tallybook"
        private const val WORKER = "IntentService[BackupHandlerIntentService]"
    }

    private lateinit var context: Context
    private lateinit var ledger: DeletedLedger
    private lateinit var documents: File
    private lateinit var appFolder: File
    private var wallet = 0L
    private var category = 0L
    private val created = mutableListOf<File>()
    private val snapshots = mutableListOf<Pair<File, Set<String>?>>()
    private val broadcasts = mutableListOf<Intent>()
    private val receiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            broadcasts.add(intent)
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.initialize(context)
        TestDatabases.useFreshDatabase(context)
        ledger = DeletedLedger(context)
        val publicDocuments = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        documents = File(publicDocuments, FOLDER_NAME)
        appFolder = File(context.getExternalFilesDir(null), FOLDER_NAME)
        for (folder in listOf(publicDocuments, documents, appFolder)) {
            snapshots.add(folder to folder.list()?.toSet())
        }
        wallet = ledger.insertWallet("Cash", "EUR")
        category = ledger.insertCategory("Groceries", Contract.CategoryType.EXPENSE)
        val filter = IntentFilter(LocalAction.ACTION_BACKUP_SERVICE_FINISHED)
        filter.addAction(LocalAction.ACTION_BACKUP_SERVICE_FAILED)
        filter.addAction(LocalAction.ACTION_BACKUP_SERVICE_RUNNING)
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter)
    }

    @After
    fun tearDown() {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        DataContentProvider.closeStagingDatabase()
        for (file in created) {
            FileUtils.deleteQuietly(file)
        }
        for ((folder, names) in snapshots.reversed()) {
            if (names == null) {
                FileUtils.deleteQuietly(folder)
            } else {
                folder.listFiles()?.filter { it.name !in names }?.forEach { FileUtils.deleteQuietly(it) }
            }
        }
    }

    @Test
    fun aRestoreSavesTheLedgerFromBeforeItInDocumentsAndThenRestores() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledgerWith(BEFORE)

        restore(backup, null)

        val finished = finished()
        val copy = copies(documents).single()
        assertEquals(copy.absolutePath, finished.getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY))
        assertTrue(copy.name, copy.name.matches(Regex("before_restore_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}\\.mwbx")))
        assertEquals(listOf(BEFORE), moneyIn(copy, null))
        assertEquals(listOf(RESTORED), liveMoney())
        assertNull(appFolder.list())
        val progress = broadcasts.filter { it.action == LocalAction.ACTION_BACKUP_SERVICE_RUNNING }.map {
            it.getIntExtra(BackupHandlerIntentService.PROGRESS_STATUS, 0) to it.getIntExtra(BackupHandlerIntentService.PROGRESS_VALUE, 0)
        }
        val creation = progress.indexOf(BackupHandlerIntentService.STATUS_BACKUP_CREATION to 72)
        assertTrue("$progress", creation >= 0)
        assertEquals("$progress", BackupHandlerIntentService.STATUS_BACKUP_RESTORING to 75, progress[creation + 1])
    }

    @Test
    fun anEncryptedRestoreSavesACopyThatOpensOnlyWithItsPassword() {
        val backup = backupOf(RESTORED, "other.mwbs", PASSWORD)
        ledgerWith(BEFORE)

        restore(backup, PASSWORD)

        finished()
        val copy = copies(documents).single()
        assertTrue(copy.name, copy.name.endsWith(".mwbs"))
        assertEquals(listOf(BEFORE), moneyIn(copy, PASSWORD))
        try {
            moneyIn(copy, null)
            fail("the copy opened without its password")
        } catch (expected: ZipException) {
        }
        assertEquals(listOf(RESTORED), liveMoney())
    }

    @Test
    fun aPlainRestoreSavesAPlainCopy() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledgerWith(BEFORE)

        restore(backup, null)

        finished()
        val copy = copies(documents).single()
        assertTrue(copy.name, copy.name.endsWith(".mwbx"))
        assertFalse(ZipFile(copy).getFileHeader(DATABASE_ENTRY).isEncrypted)
    }

    @Test
    fun aCopyThatCannotBeSavedAnywhereStopsTheRestore() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledgerWith(BEFORE)
        blockFolder(documents)
        blockFolder(appFolder)

        restore(backup, null)

        val exception = failure()
        val message = context.getString(R.string.message_backup_restoring_copy_failed)
        assertTrue(exception.message, exception.message!!.contains(message))
        assertEquals(listOf(BEFORE), liveMoney())
        assertTrue(documents.isFile)
        assertTrue(appFolder.isFile)
    }

    @Test
    fun aCopyThatCannotBeSavedInDocumentsIsSavedInTheAppFolder() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledgerWith(BEFORE)
        blockFolder(documents)

        restore(backup, null)

        val finished = finished()
        val copy = copies(appFolder).single()
        assertEquals(copy.absolutePath, finished.getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY))
        val warning = ShadowLog.getLogsForTag("BeforeRestoreCopy").single()
        assertEquals(Log.WARN, warning.type)
        assertTrue(warning.msg, warning.msg.contains(documents.path))
        assertEquals(listOf(BEFORE), moneyIn(copy, null))
        assertEquals(listOf(RESTORED), liveMoney())
    }

    @Test
    fun aWrongPasswordRestoresNothingAndLeavesNoCopy() {
        val backup = backupOf(RESTORED, "other.mwbs", PASSWORD)
        ledgerWith(BEFORE)

        restore(backup, "wrong")

        assertTrue(failure() is ImportException)
        assertTrue(copies(documents).isEmpty())
        assertTrue(copies(appFolder).isEmpty())
        assertTrue(documents.list()!!.isEmpty())
        assertEquals(listOf(BEFORE), liveMoney())
    }

    @Test
    fun aFailureAfterTheDatabaseWasReplacedKeepsTheCopy() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        val receipt = File(context.cacheDir, "receipt")
        created.add(receipt)
        FileUtils.writeStringToFile(receipt, "receipt", StandardCharsets.UTF_8)
        val parameters = ZipParameters()
        parameters.setRootFolderInZip("attachments/")
        parameters.setEncryptFiles(true)
        parameters.setEncryptionMethod(Zip4jConstants.ENC_METHOD_AES)
        parameters.setAesKeyStrength(Zip4jConstants.AES_STRENGTH_256)
        parameters.setPassword(PASSWORD)
        ZipFile(backup).addFile(receipt, parameters)
        ledgerWith(BEFORE)

        restore(backup, null)

        assertTrue(failure() is ImportException)
        assertEquals(listOf(RESTORED), liveMoney())
        assertEquals(listOf(BEFORE), moneyIn(copies(documents).single(), null))
    }

    @Test
    fun aRestoreKeepsOnlyTheThreeNewestCopiesAndNothingElseIsTouched() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledgerWith(BEFORE)
        FileUtils.forceMkdir(documents)
        val older = listOf("before_restore_2020-01-01_00-00-00.mwbx", "before_restore_2020-01-02_00-00-00.mwbs",
                "before_restore_2020-01-03_00-00-00.mwbx").map { File(documents, it) }
        for (file in older) {
            FileUtils.writeStringToFile(file, file.name, StandardCharsets.UTF_8)
        }
        val unrelatedBackup = File(documents, "backup_2019-01-01_00-00-00.mwbx")
        FileUtils.writeStringToFile(unrelatedBackup, "backup", StandardCharsets.UTF_8)
        val unrelatedFile = File(documents, "notes.txt")
        FileUtils.writeStringToFile(unrelatedFile, "notes", StandardCharsets.UTF_8)
        val unrelatedFolder = File(documents, "before_restore_2019-01-01_00-00-00.mwbx")
        FileUtils.forceMkdir(unrelatedFolder)

        restore(backup, null)

        val copy = File(finished().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY)!!)
        assertEquals(setOf(copy.name, older[1].name, older[2].name, unrelatedBackup.name, unrelatedFile.name,
                unrelatedFolder.name), documents.list()!!.toSet())
        assertEquals("backup", FileUtils.readFileToString(unrelatedBackup, StandardCharsets.UTF_8))
        assertEquals("notes", FileUtils.readFileToString(unrelatedFile, StandardCharsets.UTF_8))
        assertTrue(unrelatedFolder.isDirectory)
        assertEquals(older[2].name, FileUtils.readFileToString(older[2], StandardCharsets.UTF_8))
        assertEquals(listOf(BEFORE), moneyIn(copy, null))
    }

    @Test
    fun aRestoreOfTheOldestCopyKeepsTheFileItRestores() {
        FileUtils.forceMkdir(documents)
        val oldest = File(documents, "before_restore_2020-01-01_00-00-00.mwbx")
        FileUtils.moveFile(backupOf(RESTORED, "other.mwbx", null), oldest)
        created.add(oldest)
        val older = listOf("before_restore_2020-01-02_00-00-00.mwbx", "before_restore_2020-01-03_00-00-00.mwbx").map { File(documents, it) }
        for (file in older) {
            FileUtils.copyFile(oldest, file)
        }
        ledgerWith(BEFORE)

        restore(oldest, null)

        val copy = File(finished().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY)!!)
        assertEquals(listOf(RESTORED), liveMoney())
        assertEquals(setOf(copy.name, oldest.name, older[0].name, older[1].name), documents.list()!!.toSet())
        assertEquals(listOf(RESTORED), moneyIn(oldest, null))
    }

    @Test
    fun aWrongPasswordLeavesTheOlderCopiesAsTheyWere() {
        val backup = backupOf(RESTORED, "other.mwbs", PASSWORD)
        ledgerWith(BEFORE)
        val older = olderCopies("before_restore_2020-01-01_00-00-00.mwbx", "before_restore_2020-01-02_00-00-00.mwbx",
                "before_restore_2020-01-03_00-00-00.mwbx")

        restore(backup, "wrong")

        assertTrue(failure() is ImportException)
        assertEquals(older.map { it.name }.toSet(), documents.list()!!.toSet())
        assertEquals(listOf(BEFORE), liveMoney())
    }

    @Test
    fun aFailureAfterTheDatabaseWasReplacedKeepsTheCopyAmongTheThreeNewest() {
        val backup = backupThatFailsAfterTheDatabaseIsReplaced()
        ledgerWith(BEFORE)
        val older = olderCopies("before_restore_2020-01-01_00-00-00.mwbx", "before_restore_2020-01-02_00-00-00.mwbx",
                "before_restore_2020-01-03_00-00-00.mwbx")

        restore(backup, null)

        assertTrue(failure() is ImportException)
        val copy = File(failed().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY)!!)
        assertEquals(setOf(copy.name, older[1].name, older[2].name), documents.list()!!.toSet())
        assertEquals(listOf(BEFORE), moneyIn(copy, null))
    }

    @Test
    fun aFailureAfterTheDatabaseWasReplacedSaysWhereTheCopyIs() {
        val backup = backupThatFailsAfterTheDatabaseIsReplaced()
        ledgerWith(BEFORE)

        restore(backup, null)

        assertTrue(failure() is ImportException)
        val copy = copies(documents).single()
        assertEquals(copy.absolutePath, failed().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY))
    }

    @Test
    fun aWrongPasswordSaysNothingAboutACopy() {
        val backup = backupOf(RESTORED, "other.mwbs", PASSWORD)
        ledgerWith(BEFORE)

        restore(backup, "wrong")

        assertTrue(failure() is ImportException)
        assertFalse(failed().hasExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY))
    }

    @Test
    fun aKeptCopyIsNotNamedByTheNextFailure() {
        val backup = backupThatFailsAfterTheDatabaseIsReplaced()
        ledgerWith(BEFORE)
        val invalid = restoreIntent(backup, null).putExtra(BackupHandlerIntentService.BACKEND_ID, "invalid")

        handle(restoreIntent(backup, null), invalid)

        val failures = broadcasts.filter { it.action == LocalAction.ACTION_BACKUP_SERVICE_FAILED }
        assertEquals(2, failures.size)
        assertEquals(copies(documents).single().absolutePath, failures[0].getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY))
        assertFalse(failures[1].hasExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY))
    }

    @Test
    fun aRestoreOfACopyOfTheOldestFromAnotherFolderKeepsTheOldest() {
        FileUtils.forceMkdir(documents)
        val oldest = File(documents, "before_restore_2020-01-01_00-00-00.mwbx")
        FileUtils.moveFile(backupOf(RESTORED, "other.mwbx", null), oldest)
        val older = listOf("before_restore_2020-01-02_00-00-00.mwbx", "before_restore_2020-01-03_00-00-00.mwbx").map { File(documents, it) }
        for (file in older) {
            FileUtils.copyFile(oldest, file)
        }
        val downloaded = File(File(context.cacheDir, "downloaded"), oldest.name)
        created.add(downloaded.parentFile!!)
        FileUtils.copyFile(oldest, downloaded)
        ledgerWith(BEFORE)

        restore(downloaded, null)

        val copy = File(finished().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY)!!)
        assertEquals(listOf(RESTORED), liveMoney())
        assertEquals(setOf(copy.name, oldest.name, older[0].name, older[1].name), documents.list()!!.toSet())
        assertEquals(listOf(RESTORED), moneyIn(oldest, null))
    }

    @Test
    fun aRestoreOfADownloadedCopyOfABackupInsideAUuidFolderKeepsTheBackup() {
        FileUtils.forceMkdir(documents)
        val staging = File(documents, "0f8fad5b-d9cb-469f-a165-70867728950e")
        val backup = File(staging, "backup_2020-01-01_00-00-00.mwbx")
        FileUtils.moveFile(backupOf(RESTORED, "other.mwbx", null), backup)
        val downloaded = File(File(context.cacheDir, "downloaded"), backup.name)
        created.add(downloaded.parentFile!!)
        FileUtils.copyFile(backup, downloaded)
        ledgerWith(BEFORE)

        restore(downloaded, null)

        finished()
        assertEquals(listOf(RESTORED), liveMoney())
        assertEquals(listOf(RESTORED), moneyIn(backup, null))
    }

    @Test
    fun aRestoreOfADownloadedCopyWithAWrongPasswordKeepsTheBackupInsideAUuidFolder() {
        FileUtils.forceMkdir(documents)
        val staging = File(documents, "0f8fad5b-d9cb-469f-a165-70867728950e")
        val backup = File(staging, "backup_2020-01-01_00-00-00.mwbs")
        FileUtils.moveFile(backupOf(RESTORED, "other.mwbs", PASSWORD), backup)
        val downloaded = File(File(context.cacheDir, "downloaded"), backup.name)
        created.add(downloaded.parentFile!!)
        FileUtils.copyFile(backup, downloaded)
        ledgerWith(BEFORE)

        restore(downloaded, "wrong")

        assertTrue(failure() is ImportException)
        assertEquals(listOf(BEFORE), liveMoney())
        assertEquals(listOf(RESTORED), moneyIn(backup, PASSWORD))
    }

    @Test
    fun aRestoreRemovesThePartialCopiesLeftByAnEarlierRestore() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledgerWith(BEFORE)
        val partials = olderCopies("before_restore_2020-01-01_00-00-00.mwbx.partial", "before_restore_2020-01-02_00-00-00.mwbs.partial")
        val staging = File(documents, "0f8fad5b-d9cb-469f-a165-70867728950e")
        FileUtils.writeStringToFile(File(staging, "backup_2020-01-01_00-00-00.mwbx"), "backup", StandardCharsets.UTF_8)
        val upperCase = File(documents, "0F8FAD5B-D9CB-469F-A165-70867728950E")
        val other = File(documents, "staging")
        for (folder in listOf(upperCase, other)) {
            FileUtils.writeStringToFile(File(folder, "notes.txt"), "notes", StandardCharsets.UTF_8)
        }

        restore(backup, null)

        val copy = File(finished().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY)!!)
        assertTrue(partials.none { it.exists() })
        assertEquals(setOf(copy.name, staging.name, upperCase.name, other.name), documents.list()!!.toSet())
        assertEquals("backup", FileUtils.readFileToString(File(staging, "backup_2020-01-01_00-00-00.mwbx"), StandardCharsets.UTF_8))
        for (folder in listOf(upperCase, other)) {
            assertEquals("notes", FileUtils.readFileToString(File(folder, "notes.txt"), StandardCharsets.UTF_8))
        }
        assertEquals(listOf(BEFORE), moneyIn(copy, null))
    }

    @Test
    fun aRestoreKeepsTheAutoBackupsInAFolderNamedWithAUuid() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledgerWith(BEFORE)
        val folder = File(documents, "0f8fad5b-d9cb-469f-a165-70867728950e")
        val auto = File(folder, "auto")
        val backups = listOf("backup_2020-01-01_00-00-00.mwbx", "backup_2020-01-02_00-00-00.mwbs").map { File(auto, it) }
        for (file in backups) {
            FileUtils.writeStringToFile(file, file.name, StandardCharsets.UTF_8)
        }

        restore(backup, null)

        val copy = File(finished().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY)!!)
        assertEquals(setOf(copy.name, folder.name), documents.list()!!.toSet())
        assertEquals(setOf(auto.name), folder.list()!!.toSet())
        assertEquals(backups.map { it.name }.toSet(), auto.list()!!.toSet())
        for (file in backups) {
            assertEquals(file.name, FileUtils.readFileToString(file, StandardCharsets.UTF_8))
        }
    }

    @Test
    fun aRestoreKeepsTheBackupsInAFolderNamedWithAUuid() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledgerWith(BEFORE)
        val folder = File(documents, "7c9e6679-7425-40de-944b-e07fc1f90ae7")
        val backups = listOf("backup_2020-01-01_00-00-00.mwbx", "backup_2020-01-02_00-00-00.mwbs").map { File(folder, it) }
        for (file in backups) {
            FileUtils.writeStringToFile(file, file.name, StandardCharsets.UTF_8)
        }

        restore(backup, null)

        val copy = File(finished().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY)!!)
        assertEquals(setOf(copy.name, folder.name), documents.list()!!.toSet())
        assertEquals(backups.map { it.name }.toSet(), folder.list()!!.toSet())
        for (file in backups) {
            assertEquals(file.name, FileUtils.readFileToString(file, StandardCharsets.UTF_8))
        }
    }

    @Test
    fun aRestoreKeepsTheFilesInAFolderNamedWithAUuid() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledgerWith(BEFORE)
        val folder = File(documents, "7c9e6679-7425-40de-944b-e07fc1f90ae7")
        val files = listOf("notes.txt", "statement.pdf").map { File(folder, it) }
        for (file in files) {
            FileUtils.writeStringToFile(file, file.name, StandardCharsets.UTF_8)
        }

        restore(backup, null)

        val copy = File(finished().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY)!!)
        assertEquals(setOf(copy.name, folder.name), documents.list()!!.toSet())
        assertEquals(files.map { it.name }.toSet(), folder.list()!!.toSet())
        for (file in files) {
            assertEquals(file.name, FileUtils.readFileToString(file, StandardCharsets.UTF_8))
        }
    }

    @Test
    fun aRestoreOfADownloadedCopyWithAWrongPasswordKeepsTheBackupInAFolderInsideAUuidFolder() {
        FileUtils.forceMkdir(documents)
        val folder = File(documents, "0f8fad5b-d9cb-469f-a165-70867728950e")
        val backup = File(File(folder, "sub"), "backup_2020-01-01_00-00-00.mwbs")
        FileUtils.moveFile(backupOf(RESTORED, "other.mwbs", PASSWORD), backup)
        val downloaded = File(File(context.cacheDir, "downloaded"), backup.name)
        created.add(downloaded.parentFile!!)
        FileUtils.copyFile(backup, downloaded)
        ledgerWith(BEFORE)

        restore(downloaded, "wrong")

        assertTrue(failure() is ImportException)
        assertEquals(listOf(BEFORE), liveMoney())
        assertEquals(setOf(folder.name), documents.list()!!.toSet())
        assertEquals(listOf(RESTORED), moneyIn(backup, PASSWORD))
    }

    @Test
    fun aRestoreKeepsPartialFilesNotNamedLikeACopy() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledgerWith(BEFORE)
        val others = olderCopies("before_restore_mine.mwbx.partial", "before_restore_2026-13-45_99-99-99.mwbx.partial",
                "backup_2020-01-01_00-00-00.mwbx.partial", "before_restore_2020-01-01_00-00-00.mwb.partial")
        val folder = File(documents, "before_restore_2020-01-03_00-00-00.mwbx.partial")
        FileUtils.writeStringToFile(File(folder, "notes.txt"), "notes", StandardCharsets.UTF_8)

        restore(backup, null)

        val copy = File(finished().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY)!!)
        assertEquals((listOf(copy, folder) + others).map { it.name }.toSet(), documents.list()!!.toSet())
        for (file in others) {
            assertEquals(file.name, FileUtils.readFileToString(file, StandardCharsets.UTF_8))
        }
        assertEquals(setOf("notes.txt"), folder.list()!!.toSet())
        assertEquals("notes", FileUtils.readFileToString(File(folder, "notes.txt"), StandardCharsets.UTF_8))
    }

    @Test
    fun aRestoreLeavesNoPartialFile() {
        val backup = backupOf(RESTORED, "other.mwbs", PASSWORD)
        ledgerWith(BEFORE)

        restore(backup, PASSWORD)

        val copy = File(finished().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY)!!)
        assertEquals(setOf(copy.name), documents.list()!!.toSet())
        assertTrue(documents.list()!!.none { it.endsWith(".partial") })
        assertEquals(listOf(BEFORE), moneyIn(copy, PASSWORD))
        assertEquals(listOf(RESTORED), liveMoney())
    }

    @Test
    fun aRestorePrunesOnlyFilesNamedLikeACopy() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledgerWith(BEFORE)
        val older = olderCopies("before_restore_2020-01-01_00-00-00.mwbx", "before_restore_2020-01-02_00-00-00.mwbs",
                "before_restore_2020-01-03_00-00-00.mwbx")
        val others = olderCopies("before_restore_mine.mwbx", "before_restore_(keep).mwbx",
                "before_restore_2026-13-45_99-99-99.mwbx", "before_restore_2020-01-04_00-00-00.mwb")

        restore(backup, null)

        val copy = File(finished().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY)!!)
        assertEquals((listOf(copy, older[1], older[2]) + others).map { it.name }.toSet(), documents.list()!!.toSet())
        for (file in others) {
            assertEquals(file.name, FileUtils.readFileToString(file, StandardCharsets.UTF_8))
        }
    }

    @Test
    fun aRestoreOfABackupInsideAUuidFolderKeepsItAndTheOtherUuidFolders() {
        FileUtils.forceMkdir(documents)
        val staging = File(documents, "0f8fad5b-d9cb-469f-a165-70867728950e")
        val backup = File(staging, "backup_2020-01-01_00-00-00.mwbx")
        FileUtils.moveFile(backupOf(RESTORED, "other.mwbx", null), backup)
        val other = File(documents, "7c9e6679-7425-40de-944b-e07fc1f90ae7")
        FileUtils.writeStringToFile(File(other, "backup_2020-01-02_00-00-00.mwbx"), "backup", StandardCharsets.UTF_8)
        ledgerWith(BEFORE)

        restore(backup, null)

        val copy = File(finished().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY)!!)
        assertEquals(listOf(RESTORED), liveMoney())
        assertEquals(listOf(RESTORED), moneyIn(backup, null))
        assertEquals(setOf(copy.name, staging.name, other.name), documents.list()!!.toSet())
        assertEquals(listOf(BEFORE), moneyIn(copy, null))
    }

    @Test
    fun aRestoreOfAFileNamedLikeAUuidFolderKeepsThatFolder() {
        FileUtils.forceMkdir(documents)
        val staging = File(documents, "0f8fad5b-d9cb-469f-a165-70867728950e")
        FileUtils.writeStringToFile(File(staging, "backup_2020-01-01_00-00-00.mwbx"), "backup", StandardCharsets.UTF_8)
        val backup = File(documents, staging.name + ".mwbx")
        FileUtils.moveFile(backupOf(RESTORED, "other.mwbx", null), backup)
        ledgerWith(BEFORE)

        restore(backup, null)

        val copy = File(finished().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY)!!)
        assertEquals(listOf(RESTORED), liveMoney())
        assertEquals(setOf(copy.name, staging.name, backup.name), documents.list()!!.toSet())
        assertEquals(listOf(RESTORED), moneyIn(backup, null))
    }

    @Test
    fun aRestoreKeepsAFileNamedLikeACopyByAFileManager() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledgerWith(BEFORE)
        val older = olderCopies("before_restore_2020-01-01_00-00-00.mwbx", "before_restore_2020-01-02_00-00-00.mwbx",
                "before_restore_2020-01-03_00-00-00.mwbx")
        val duplicate = olderCopies("before_restore_2020-01-01_00-00-00 (1).mwbx").single()

        restore(backup, null)

        val copy = File(finished().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY)!!)
        assertEquals(setOf(copy.name, older[1].name, older[2].name, duplicate.name), documents.list()!!.toSet())
        assertEquals(duplicate.name, FileUtils.readFileToString(duplicate, StandardCharsets.UTF_8))
    }

    @Test
    fun aRestoreKeepsAFileNamedLikeAUuidFolder() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledgerWith(BEFORE)
        val file = File(documents, "0f8fad5b-d9cb-469f-a165-70867728950e")
        FileUtils.writeStringToFile(file, "notes", StandardCharsets.UTF_8)

        restore(backup, null)

        val copy = File(finished().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY)!!)
        assertEquals(setOf(copy.name, file.name), documents.list()!!.toSet())
        assertEquals("notes", FileUtils.readFileToString(file, StandardCharsets.UTF_8))
    }

    @Test
    fun aCopyThatCannotBeMovedInDocumentsLeavesNothingThereAndIsSavedInTheAppFolder() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledgerWith(BEFORE)
        val now = System.currentTimeMillis()
        val taken = (-60..600).map {
            File(documents, "before_restore_" + DateUtils.getDateTimeString(Date(now + it * 1000L), BackupOperation.FILE_DATETIME_PATTERN) + ".mwbx")
        }
        for (folder in taken) {
            FileUtils.forceMkdir(folder)
        }

        restore(backup, null)

        val copy = File(finished().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY)!!)
        assertEquals(appFolder, copy.parentFile)
        assertEquals(listOf(BEFORE), moneyIn(copy, null))
        assertEquals(taken.map { it.name }.toSet(), documents.list()!!.toSet())
        assertTrue(taken.all { it.list()!!.isEmpty() })
        assertEquals(listOf(RESTORED), liveMoney())
    }

    @Test
    fun aCopyThatCannotBeWrittenInDocumentsKeepsAFolderNamedLikeItsPartialFile() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledgerWith(BEFORE)
        val now = System.currentTimeMillis()
        val taken = (-60..600).map {
            File(documents, "before_restore_" + DateUtils.getDateTimeString(Date(now + it * 1000L), BackupOperation.FILE_DATETIME_PATTERN) + ".mwbx.partial")
        }
        for (folder in taken) {
            FileUtils.writeStringToFile(File(folder, "notes.txt"), folder.name, StandardCharsets.UTF_8)
        }

        restore(backup, null)

        val copy = File(finished().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY)!!)
        assertEquals(appFolder, copy.parentFile)
        assertEquals(listOf(BEFORE), moneyIn(copy, null))
        assertEquals(taken.map { it.name }.toSet(), documents.list()!!.toSet())
        for (folder in taken) {
            assertEquals(setOf("notes.txt"), folder.list()!!.toSet())
            assertEquals(folder.name, FileUtils.readFileToString(File(folder, "notes.txt"), StandardCharsets.UTF_8))
        }
        assertEquals(listOf(RESTORED), liveMoney())
    }

    @Test
    fun aCopySavedInTheAppFolderRemovesThePartialCopiesLeftThere() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledgerWith(BEFORE)
        val partials = listOf("before_restore_2020-01-01_00-00-00.mwbx.partial", "before_restore_2020-01-02_00-00-00.mwbs.partial").map { File(appFolder, it) }
        for (file in partials) {
            FileUtils.writeStringToFile(file, file.name, StandardCharsets.UTF_8)
        }
        blockFolder(documents)

        restore(backup, null)

        val copy = File(finished().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY)!!)
        assertEquals(appFolder, copy.parentFile)
        assertEquals(listOf(BEFORE), moneyIn(copy, null))
        assertTrue(partials.none { it.exists() })
        assertEquals(setOf(copy.name), appFolder.list()!!.toSet())
        assertEquals(listOf(RESTORED), liveMoney())
    }

    @Test
    fun aCopyThatCannotBeMovedInDocumentsStillRemovesThePartialCopiesLeftThere() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledgerWith(BEFORE)
        val partial = olderCopies("before_restore_2020-01-01_00-00-00.mwbx.partial").single()
        val now = System.currentTimeMillis()
        val taken = (-60..600).map {
            File(documents, "before_restore_" + DateUtils.getDateTimeString(Date(now + it * 1000L), BackupOperation.FILE_DATETIME_PATTERN) + ".mwbx")
        }
        for (folder in taken) {
            FileUtils.forceMkdir(folder)
        }

        restore(backup, null)

        val copy = File(finished().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY)!!)
        assertEquals(appFolder, copy.parentFile)
        assertEquals(listOf(BEFORE), moneyIn(copy, null))
        assertFalse(partial.exists())
        assertEquals(taken.map { it.name }.toSet(), documents.list()!!.toSet())
        assertTrue(taken.all { it.isDirectory })
        assertEquals(listOf(RESTORED), liveMoney())
    }

    @Test
    fun aCopyThatCannotBeMovedInDocumentsKeepsAFileNamedLikeIt() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledgerWith(BEFORE)
        val now = System.currentTimeMillis()
        val taken = (-60..600).map {
            File(documents, "before_restore_" + DateUtils.getDateTimeString(Date(now + it * 1000L), BackupOperation.FILE_DATETIME_PATTERN) + ".mwbx")
        }
        for (file in taken) {
            FileUtils.writeStringToFile(file, file.name, StandardCharsets.UTF_8)
        }

        restore(backup, null)

        val copy = File(finished().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY)!!)
        assertEquals(appFolder, copy.parentFile)
        assertEquals(listOf(BEFORE), moneyIn(copy, null))
        assertEquals(taken.map { it.name }.toSet(), documents.list()!!.toSet())
        for (file in taken) {
            assertEquals(file.name, FileUtils.readFileToString(file, StandardCharsets.UTF_8))
        }
        assertEquals(listOf(RESTORED), liveMoney())
    }

    @Test
    fun aRestoreIntoAnEmptyLedgerSavesACopy() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledger.database().execSQL("DELETE FROM transactions")

        restore(backup, null)

        val copy = copies(documents).single()
        assertEquals(copy.absolutePath, finished().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY))
        assertEquals(emptyList<Long>(), moneyIn(copy, null))
        assertEquals(listOf(RESTORED), liveMoney())
    }

    @Test
    fun aRestoreWithOneOlderCopyDeletesNoCopy() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledgerWith(BEFORE)
        val older = olderCopies("before_restore_2020-01-01_00-00-00.mwbx").single()

        restore(backup, null)

        val copy = File(finished().getStringExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY)!!)
        assertEquals(setOf(copy.name, older.name), documents.list()!!.toSet())
        assertEquals(older.name, FileUtils.readFileToString(older, StandardCharsets.UTF_8))
    }

    @Test
    fun aCountSendsTheCountsOfTheFileAndOfTheAppAndChangesNothing() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledger.insertWallet("Bank", "EUR")
        ledgerWith(BEFORE)
        ledger.insertTransaction(ledger.transaction(wallet, category, RESTORED))
        val downloads = downloadFolder()

        count(backup, null)

        val finished = finished()
        assertEquals(BackupHandlerIntentService.ACTION_COUNT, finished.getIntExtra(BackupHandlerIntentService.ACTION, 0))
        assertEquals(listOf(1, 1, 2, 2), countsIn(finished))
        assertEquals(backup, finished.getParcelableExtra<LocalFile>(BackupHandlerIntentService.BACKUP_FILE)!!.getFile())
        assertNull(finished.getStringExtra(BackupHandlerIntentService.PASSWORD))
        assertEquals("restore", finished.getStringExtra(BackupHandlerIntentService.CALLER_ID))
        assertTrue(downloads.list().isNullOrEmpty())
        assertEquals(listOf(BEFORE, RESTORED), liveMoney())
        assertTrue(copies(documents).isEmpty())
        assertTrue(backup.isFile)
        val progress = broadcasts.filter { it.action == LocalAction.ACTION_BACKUP_SERVICE_RUNNING }.map {
            it.getIntExtra(BackupHandlerIntentService.PROGRESS_STATUS, 0) to it.getIntExtra(BackupHandlerIntentService.PROGRESS_VALUE, 0)
        }
        assertEquals(listOf(BackupHandlerIntentService.STATUS_BACKUP_DOWNLOADING to 0), progress)
    }

    @Test
    fun anEncryptedCountWithItsPasswordSendsTheCountsAndThePassword() {
        val backup = backupOf(RESTORED, "other.mwbs", PASSWORD)
        ledgerWith(BEFORE)
        ledger.insertTransaction(ledger.transaction(wallet, category, RESTORED))
        val downloads = downloadFolder()

        count(backup, PASSWORD)

        val finished = finished()
        assertEquals(listOf(1, 1, 2, 1), countsIn(finished))
        assertEquals(backup, finished.getParcelableExtra<LocalFile>(BackupHandlerIntentService.BACKUP_FILE)!!.getFile())
        assertEquals(PASSWORD, finished.getStringExtra(BackupHandlerIntentService.PASSWORD))
        assertTrue(downloads.list().isNullOrEmpty())
        assertEquals(listOf(BEFORE, RESTORED), liveMoney())
    }

    @Test
    fun aCountWithAWrongPasswordFailsAndLeavesNoDownload() {
        val backup = backupOf(RESTORED, "other.mwbs", PASSWORD)
        ledgerWith(BEFORE)
        val downloads = downloadFolder()

        count(backup, "wrong")

        val exception = failure()
        assertTrue(exception is ImportException)
        assertTrue(exception.message, exception.message!!.startsWith("Wrong Password"))
        assertEquals(BackupHandlerIntentService.ACTION_COUNT, failed().getIntExtra(BackupHandlerIntentService.ACTION, 0))
        assertTrue(downloads.list().isNullOrEmpty())
        assertEquals(listOf(BEFORE), liveMoney())
    }

    @Test
    fun aFinishedCountSendsTheBackendOfItsFile() {
        val backup = backupOf(RESTORED, "other.mwbx", null)

        count(backup, null)

        assertEquals(BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY, finished().getStringExtra(BackupHandlerIntentService.BACKEND_ID))
    }

    @Test
    fun aFailedCountSendsTheBackendOfItsFile() {
        val backup = backupOf(RESTORED, "other.mwbs", PASSWORD)

        count(backup, "wrong")

        assertEquals(BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY, failed().getStringExtra(BackupHandlerIntentService.BACKEND_ID))
    }

    @Test
    fun aCountWithoutAWebDAVServerFailsAndSendsTheWebDAVBackend() {
        val backup = backupOf(RESTORED, "other.mwbx", null)

        handle(restoreIntent(backup, null)
                .putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_COUNT)
                .putExtra(BackupHandlerIntentService.BACKEND_ID, BackendServiceFactory.SERVICE_ID_WEBDAV))

        assertTrue(failure() is BackendException)
        assertEquals(BackupHandlerIntentService.ACTION_COUNT, failed().getIntExtra(BackupHandlerIntentService.ACTION, 0))
        assertEquals(BackendServiceFactory.SERVICE_ID_WEBDAV, failed().getStringExtra(BackupHandlerIntentService.BACKEND_ID))
    }

    @Test
    fun aFinishedCountOfAFileInTheLocalFolderSendsTheLocalFolderBackend() {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        val tree = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ABackups")
        val document = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ABackups/document/primary%3ABackups%2Fother.mwbx")
        val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val preferences = context.getSharedPreferences("storage_access_framework", Context.MODE_PRIVATE)
        preferences.edit().putString("uri", tree.toString()).commit()
        context.contentResolver.takePersistableUriPermission(tree, readWrite)
        try {
            shadowOf(context.contentResolver).registerInputStream(document, FileInputStream(backup))
            val file = SAFFile("{\"uri\":\"$document\",\"name\":\"other.mwbx\",\"size\":${backup.length()},\"isDir\":false}")

            handle(restoreIntent(backup, null)
                    .putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_COUNT)
                    .putExtra(BackupHandlerIntentService.BACKEND_ID, BackendServiceFactory.SERVICE_ID_SAF)
                    .putExtra(BackupHandlerIntentService.BACKUP_FILE, file))

            val finished = finished()
            assertEquals(BackendServiceFactory.SERVICE_ID_SAF, finished.getStringExtra(BackupHandlerIntentService.BACKEND_ID))
            assertEquals(listOf(1, 1, 1, 1), countsIn(finished))
            assertEquals(document, finished.getParcelableExtra<SAFFile>(BackupHandlerIntentService.BACKUP_FILE)!!.getUri())
        } finally {
            context.contentResolver.releasePersistableUriPermission(tree, readWrite)
            preferences.edit().remove("uri").commit()
        }
    }

    @Test
    fun aCountOfAFileThatIsNotAZipFails() {
        val backup = File(context.cacheDir, "broken.mwbx")
        created.add(backup)
        FileUtils.writeStringToFile(backup, "not a zip", StandardCharsets.UTF_8)
        val downloads = downloadFolder()

        count(backup, null)

        assertEquals("Invalid backup file: not a zip file", failure().message)
        assertTrue(downloads.list().isNullOrEmpty())
    }

    @Test
    fun aCountOfABackupFromANewerVersionFails() {
        val exported = backupOf(RESTORED, "other.mwbx", null)
        val json = JSONObject(ZipFile(exported).let { zip ->
            zip.getInputStream(zip.getFileHeader(DATABASE_ENTRY)).use { String(it.readBytes(), StandardCharsets.UTF_8) }
        })
        json.getJSONObject("header").put("version_code", Int.MAX_VALUE)
        val folder = File(context.cacheDir, "newer_export")
        created.add(folder)
        val database = File(folder, "database.json")
        FileUtils.writeStringToFile(database, json.toString(), StandardCharsets.UTF_8)
        val backup = File(context.cacheDir, "newer.mwbx")
        created.add(backup)
        val parameters = ZipParameters()
        parameters.setRootFolderInZip("databases/")
        ZipFile(backup).addFile(database, parameters)

        count(backup, null)

        val exception = failure()
        assertTrue(exception is ImportException)
        assertTrue(exception.message, exception.message!!.startsWith("This backup belongs to a newer version"))
    }

    @Test
    fun aCountSendsFourUnequalCountsEachInItsOwnExtra() {
        ledger.insertWallet("Bank", "EUR")
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledger.insertWallet("Savings", "EUR")
        ledger.insertWallet("Travel", "EUR")
        ledger.insertTransaction(ledger.transaction(wallet, category, BEFORE))
        ledger.insertTransaction(ledger.transaction(wallet, category, BEFORE))

        count(backup, null)

        assertEquals(listOf(1, 2, 3, 4), countsIn(finished()))
    }

    @Test
    fun aCountOfAZipWithoutTheDatabaseEntryFails() {
        val other = File(context.cacheDir, "other.json")
        created.add(other)
        FileUtils.writeStringToFile(other, "{}", StandardCharsets.UTF_8)
        val backup = File(context.cacheDir, "empty.mwbx")
        created.add(backup)
        val parameters = ZipParameters()
        parameters.setRootFolderInZip("databases/")
        ZipFile(backup).addFile(other, parameters)
        val downloads = downloadFolder()

        count(backup, null)

        val exception = failure()
        assertTrue(exception is ImportException)
        assertEquals("Invalid backup file: database file not found", exception.message)
        assertTrue(downloads.list().isNullOrEmpty())
    }

    @Test
    fun aCountOfABrokenDatabaseFileFails() {
        val folder = File(context.cacheDir, "broken_export")
        created.add(folder)
        val database = File(folder, "database.json")
        FileUtils.writeStringToFile(database, "{\"header\":{\"version_code\":6},\"currencies\" []}", StandardCharsets.UTF_8)
        val backup = File(context.cacheDir, "broken.mwbx")
        created.add(backup)
        val parameters = ZipParameters()
        parameters.setRootFolderInZip("databases/")
        ZipFile(backup).addFile(database, parameters)
        val downloads = downloadFolder()

        count(backup, null)

        assertTrue(failure() is ImportException)
        assertTrue(downloads.list().isNullOrEmpty())
    }

    @Test
    fun aRestoredBackupLeavesTheAppWithTheCountsTheFileHad() {
        ledger.insertWallet("Bank", "EUR")
        val backup = backupOf(RESTORED, "other.mwbx", null)
        ledger.insertWallet("Savings", "EUR")
        ledger.insertTransaction(ledger.transaction(wallet, category, BEFORE))
        ledger.insertTransaction(ledger.transaction(wallet, category, BEFORE))
        count(backup, null)
        val counts = countsIn(finished())
        assertEquals(listOf(1, 2, 3, 3), counts)
        broadcasts.clear()

        restore(backup, null)
        finished()
        broadcasts.clear()
        count(backup, null)

        assertEquals(counts.subList(0, 2), countsIn(finished()).subList(2, 4))
    }

    private fun ledgerWith(money: Long) {
        ledger.database().execSQL("DELETE FROM transactions")
        ledger.insertTransaction(ledger.transaction(wallet, category, money))
    }

    private fun backupOf(money: Long, name: String, password: String?): File {
        ledgerWith(money)
        val folder = File(context.cacheDir, "restore_export")
        FileUtils.forceMkdir(folder)
        created.add(folder)
        val backup = File(context.cacheDir, name)
        created.add(backup)
        val exporter = DefaultBackupExporter(context.contentResolver, backup, password)
        exporter.exportDatabase(folder)
        exporter.exportAttachments(BackupOperation.getAttachmentFolder(context))
        return backup
    }

    private fun olderCopies(vararg names: String): List<File> {
        val files = names.map { File(documents, it) }
        for (file in files) {
            FileUtils.writeStringToFile(file, file.name, StandardCharsets.UTF_8)
        }
        return files
    }

    private fun backupThatFailsAfterTheDatabaseIsReplaced(): File {
        val backup = backupOf(RESTORED, "other.mwbx", null)
        val receipt = File(context.cacheDir, "receipt")
        created.add(receipt)
        FileUtils.writeStringToFile(receipt, "receipt", StandardCharsets.UTF_8)
        val parameters = ZipParameters()
        parameters.setRootFolderInZip("attachments/")
        parameters.setEncryptFiles(true)
        parameters.setEncryptionMethod(Zip4jConstants.ENC_METHOD_AES)
        parameters.setAesKeyStrength(Zip4jConstants.AES_STRENGTH_256)
        parameters.setPassword(PASSWORD)
        ZipFile(backup).addFile(receipt, parameters)
        return backup
    }

    private fun blockFolder(folder: File) {
        FileUtils.forceMkdir(folder.parentFile!!)
        FileUtils.writeStringToFile(folder, "not a folder", StandardCharsets.UTF_8)
    }

    private fun restore(backup: File, password: String?) {
        handle(restoreIntent(backup, password))
    }

    private fun restoreIntent(backup: File, password: String?): Intent {
        val intent = Intent(context, BackupHandlerIntentService::class.java)
                .putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_RESTORE)
                .putExtra(BackupHandlerIntentService.BACKEND_ID, BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY)
                .putExtra(BackupHandlerIntentService.BACKUP_FILE, LocalFile(backup))
                .putExtra(BackupHandlerIntentService.CALLER_ID, "restore")
        if (password != null) {
            intent.putExtra(BackupHandlerIntentService.PASSWORD, password)
        }
        return intent
    }

    private fun count(backup: File, password: String?) {
        handle(restoreIntent(backup, password).putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_COUNT))
    }

    private fun countsIn(finished: Intent): List<Int> = listOf(BackupHandlerIntentService.BACKUP_TRANSACTIONS,
            BackupHandlerIntentService.BACKUP_WALLETS, BackupHandlerIntentService.CURRENT_TRANSACTIONS,
            BackupHandlerIntentService.CURRENT_WALLETS).map { finished.getIntExtra(it, -1) }

    private fun downloadFolder(): File {
        val folder = File(context.getExternalFilesDir(null), "backups")
        snapshots.add(folder to folder.list()?.toSet())
        return folder
    }

    private fun handle(vararg intents: Intent) {
        val controller = Robolectric.buildService(BackupHandlerIntentService::class.java, intents.first()).create()
        try {
            for ((index, intent) in intents.withIndex()) {
                controller.withIntent(intent).startCommand(0, index + 1)
            }
            val workers = ShadowLooper.getAllLoopers().filter { it.thread.isAlive && it.thread.name == WORKER }
            assertEquals(1, workers.size)
            shadowOf(workers.single()).idle()
            shadowOf(Looper.getMainLooper()).idle()
        } finally {
            controller.destroy()
        }
    }

    private fun finished(): Intent {
        val failed = broadcasts.firstOrNull { it.action == LocalAction.ACTION_BACKUP_SERVICE_FAILED }
        if (failed != null) {
            throw AssertionError("the restore failed", failed.getSerializableExtra(BackupHandlerIntentService.EXCEPTION) as Throwable)
        }
        val finished = broadcasts.singleOrNull { it.action == LocalAction.ACTION_BACKUP_SERVICE_FINISHED }
        assertNotNull("the restore never finished", finished)
        return finished!!
    }

    private fun failure(): Exception {
        assertTrue(broadcasts.none { it.action == LocalAction.ACTION_BACKUP_SERVICE_FINISHED })
        val failed = broadcasts.single { it.action == LocalAction.ACTION_BACKUP_SERVICE_FAILED }
        return failed.getSerializableExtra(BackupHandlerIntentService.EXCEPTION) as Exception
    }

    private fun failed(): Intent = broadcasts.single { it.action == LocalAction.ACTION_BACKUP_SERVICE_FAILED }

    private fun copies(folder: File): List<File> =
            folder.listFiles()?.filter { it.isFile && it.name.startsWith("before_restore_") } ?: emptyList()

    private fun liveMoney(): List<Long> = ledger.rows("transactions", "1").map { it["transaction_money"] as Long }

    private fun moneyIn(copy: File, password: String?): List<Long> {
        val zip = ZipFile(copy)
        val header = zip.getFileHeader(DATABASE_ENTRY)
        if (password != null) {
            header.setPassword(password.toCharArray())
        }
        val json = zip.getInputStream(header).use { String(it.readBytes(), StandardCharsets.UTF_8) }
        val transactions = JSONObject(json).getJSONArray("transactions")
        return (0 until transactions.length()).map { transactions.getJSONObject(it).getLong("money") }
    }
}
