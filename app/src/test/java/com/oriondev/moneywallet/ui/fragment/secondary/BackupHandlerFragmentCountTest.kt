package com.oriondev.moneywallet.ui.fragment.secondary

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Environment
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.TextView

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.api.BackendServiceFactory
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.model.LocalFile
import com.oriondev.moneywallet.service.BackupHandlerIntentService
import com.oriondev.moneywallet.storage.database.ImportException
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.BackupListActivity

import org.apache.commons.io.FileUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackupHandlerFragmentCountTest {

    companion object {

        private const val PASSWORD = "secret"
        private const val NO_TRANSACTIONS = "This file has no transactions, so restoring it removes all of yours."
    }

    private lateinit var context: Context
    private lateinit var folder: File
    private var folderExisted = false

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.initialize(context)
        TestDatabases.useFreshDatabase(context)
        folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Tallybook")
        folderExisted = folder.exists()
        FileUtils.forceMkdir(folder)
    }

    @After
    fun tearDown() {
        if (!folderExisted) {
            FileUtils.deleteQuietly(folder)
        }
    }

    @Test
    fun aTapOnAPlainBackupStartsTheCountWithoutADialog() {
        val backup = File(folder, "backup_2026-01-01_10-00-00.mwbx")
        withFragment { activity, fragment ->
            fragment.onFileClick(LocalFile(backup))

            val started = startedAfterTheList(activity).single()
            assertEquals(BackupHandlerIntentService.ACTION_COUNT, started.getIntExtra(BackupHandlerIntentService.ACTION, 0))
            assertEquals(BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY, started.getStringExtra(BackupHandlerIntentService.BACKEND_ID))
            assertEquals(backup, started.getParcelableExtra<LocalFile>(BackupHandlerIntentService.BACKUP_FILE)!!.getFile())
            assertEquals(BackupHandlerFragment.BACKUP_SERVICE_CALLER_ID, started.getStringExtra(BackupHandlerIntentService.CALLER_ID))
            assertFalse(started.hasExtra(BackupHandlerIntentService.PASSWORD))
            assertNull(ShadowDialog.getLatestDialog())
        }
    }

    @Test
    fun aTapOnAProtectedBackupAsksThePasswordAndItsOkStartsTheCountWithIt() {
        val backup = File(folder, "backup_2026-01-01_10-00-00.mwbs")
        withFragment { activity, fragment ->
            fragment.onFileClick(LocalFile(backup))
            startedAfterTheList(activity)

            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertEquals(context.getString(R.string.message_backup_restore_protected), messageOf(dialog))
            assertEquals(View.GONE, dialog.getButton(DialogInterface.BUTTON_NEGATIVE).visibility)
            dialog.findViewById<EditText>(R.id.dialog_input_edit_text)!!.setText(PASSWORD)
            click(dialog, DialogInterface.BUTTON_POSITIVE)

            val started = startedAfterTheList(activity).single()
            assertEquals(BackupHandlerIntentService.ACTION_COUNT, started.getIntExtra(BackupHandlerIntentService.ACTION, 0))
            assertEquals(backup, started.getParcelableExtra<LocalFile>(BackupHandlerIntentService.BACKUP_FILE)!!.getFile())
            assertEquals(PASSWORD, started.getStringExtra(BackupHandlerIntentService.PASSWORD))
            assertEquals(BackupHandlerFragment.BACKUP_SERVICE_CALLER_ID, started.getStringExtra(BackupHandlerIntentService.CALLER_ID))
        }
    }

    @Test
    fun aTapOnALegacyBackupShowsTheLegacyDialogAndItsOkStartsTheRestore() {
        val backup = File(folder, "backup.mwb")
        withFragment { activity, fragment ->
            fragment.onFileClick(LocalFile(backup))
            assertTrue(startedAfterTheList(activity).isEmpty())

            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertEquals(context.getString(R.string.message_backup_restore_legacy), messageOf(dialog))
            click(dialog, DialogInterface.BUTTON_POSITIVE)

            val started = startedAfterTheList(activity).single()
            assertEquals(BackupHandlerIntentService.ACTION_RESTORE, started.getIntExtra(BackupHandlerIntentService.ACTION, 0))
            assertEquals(backup, started.getParcelableExtra<LocalFile>(BackupHandlerIntentService.BACKUP_FILE)!!.getFile())
        }
    }

    @Test
    fun aFinishedCountShowsTheFileNameTheCountsAndTheRestoreText() {
        val backup = File(folder, "backup_2026-01-01_10-00-00.mwbx")
        withFragment { activity, _ ->
            send(activity, finished(backup, null, 2, 2, 1, 1))

            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertEquals("backup_2026-01-01_10-00-00.mwbx\n\n" +
                    "This file has 2 transactions and 2 wallets. Tallybook has 1 transaction and 1 wallet now.\n\n" +
                    context.getString(R.string.message_backup_restore_standard), messageOf(dialog))
            assertEquals(context.getString(R.string.title_backup_restore), titleOf(dialog))
            assertTrue(startedAfterTheList(activity).isEmpty())
        }
    }

    @Test
    fun theOkOfTheCountDialogStartsTheRestoreWithTheSameFileAndPassword() {
        val backup = File(folder, "backup_2026-01-01_10-00-00.mwbs")
        withFragment { activity, _ ->
            send(activity, finished(backup, PASSWORD, 3, 1, 3, 1))

            click(ShadowDialog.getLatestDialog() as AlertDialog, DialogInterface.BUTTON_POSITIVE)

            val started = startedAfterTheList(activity).single()
            assertEquals(BackupHandlerIntentService.ACTION_RESTORE, started.getIntExtra(BackupHandlerIntentService.ACTION, 0))
            assertEquals(BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY, started.getStringExtra(BackupHandlerIntentService.BACKEND_ID))
            assertEquals(backup, started.getParcelableExtra<LocalFile>(BackupHandlerIntentService.BACKUP_FILE)!!.getFile())
            assertEquals(PASSWORD, started.getStringExtra(BackupHandlerIntentService.PASSWORD))
            assertEquals(BackupHandlerFragment.BACKUP_SERVICE_CALLER_ID, started.getStringExtra(BackupHandlerIntentService.CALLER_ID))
        }
    }

    @Test
    fun theOkOfThePlainCountDialogStartsTheRestoreWithoutAPassword() {
        val backup = File(folder, "backup_2026-01-01_10-00-00.mwbx")
        withFragment { activity, _ ->
            send(activity, finished(backup, null, 3, 1, 3, 1))

            click(ShadowDialog.getLatestDialog() as AlertDialog, DialogInterface.BUTTON_POSITIVE)

            val started = startedAfterTheList(activity).single()
            assertEquals(BackupHandlerIntentService.ACTION_RESTORE, started.getIntExtra(BackupHandlerIntentService.ACTION, 0))
            assertEquals(backup, started.getParcelableExtra<LocalFile>(BackupHandlerIntentService.BACKUP_FILE)!!.getFile())
            assertFalse(started.hasExtra(BackupHandlerIntentService.PASSWORD))
        }
    }

    @Test
    fun theCancelOfTheCountDialogStartsNothing() {
        val backup = File(folder, "backup_2026-01-01_10-00-00.mwbs")
        withFragment { activity, _ ->
            send(activity, finished(backup, PASSWORD, 3, 1, 3, 1))

            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            click(dialog, DialogInterface.BUTTON_NEGATIVE)

            assertFalse(dialog.isShowing)
            assertTrue(startedAfterTheList(activity).isEmpty())
        }
    }

    @Test
    fun aFileWithoutTransactionsWarnsWhenTheAppHasSome() {
        assertEquals("backup.mwbx\n\n" +
                "This file has 0 transactions and 1 wallet. Tallybook has 4 transactions and 2 wallets now.\n\n" +
                NO_TRANSACTIONS + "\n\n" + context.getString(R.string.message_backup_restore_standard), confirmAfter(0, 1, 4, 2))
    }

    @Test
    fun aFileWithoutTransactionsDoesNotWarnWhenTheAppHasNoneEither() {
        assertEquals("backup.mwbx\n\n" +
                "This file has 0 transactions and 0 wallets. Tallybook has 0 transactions and 0 wallets now.\n\n" +
                context.getString(R.string.message_backup_restore_standard), confirmAfter(0, 0, 0, 0))
    }

    @Test
    fun aFileWithTransactionsDoesNotWarn() {
        assertEquals("backup.mwbx\n\n" +
                "This file has 1 transaction and 2 wallets. Tallybook has 9 transactions and 1 wallet now.\n\n" +
                context.getString(R.string.message_backup_restore_standard), confirmAfter(1, 2, 9, 1))
    }

    @Test
    fun aFinishedCountForAnotherCallerShowsNothing() {
        withFragment { activity, _ ->
            send(activity, finished(File(folder, "backup.mwbx"), null, 0, 1, 4, 2)
                    .putExtra(BackupHandlerIntentService.CALLER_ID, "another"))

            assertNull(ShadowDialog.getLatestDialog())
            assertTrue(startedAfterTheList(activity).isEmpty())
        }
    }

    @Test
    fun aFailedCountShowsNoConfirmAndListsNothing() {
        withFragment { activity, _ ->
            send(activity, Intent(LocalAction.ACTION_BACKUP_SERVICE_FAILED)
                    .putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_COUNT)
                    .putExtra(BackupHandlerIntentService.EXCEPTION, ImportException("Wrong Password for file: database.json"))
                    .putExtra(BackupHandlerIntentService.CALLER_ID, BackupHandlerFragment.BACKUP_SERVICE_CALLER_ID))

            val messages = ShadowDialog.getShownDialogs().map { messageOf(it) }
            assertEquals(listOf(context.getString(R.string.message_backup_restoring_failed, "Wrong Password for file: database.json")), messages)
            assertTrue(startedAfterTheList(activity).isEmpty())
        }
    }

    @Test
    fun twoTapsOnAPlainBackupStartOneCount() {
        val backup = File(folder, "backup_2026-01-01_10-00-00.mwbx")
        withFragment { activity, fragment ->
            fragment.onFileClick(LocalFile(backup))
            fragment.onFileClick(LocalFile(backup))

            val started = everyStarted(activity).single()
            assertEquals(BackupHandlerIntentService.ACTION_COUNT, started.getIntExtra(BackupHandlerIntentService.ACTION, 0))
        }
    }

    @Test
    fun aTapAfterAFinishedCountStartsTheCountAgain() {
        val backup = File(folder, "backup_2026-01-01_10-00-00.mwbx")
        withFragment { activity, fragment ->
            fragment.onFileClick(LocalFile(backup))
            send(activity, finished(backup, null, 3, 1, 3, 1))
            click(ShadowDialog.getLatestDialog() as AlertDialog, DialogInterface.BUTTON_NEGATIVE)

            fragment.onFileClick(LocalFile(backup))

            val started = everyStarted(activity).single()
            assertEquals(BackupHandlerIntentService.ACTION_COUNT, started.getIntExtra(BackupHandlerIntentService.ACTION, 0))
        }
    }

    @Test
    fun aTapAfterAFailedCountStartsTheCountAgain() {
        val backup = File(folder, "backup_2026-01-01_10-00-00.mwbx")
        withFragment { activity, fragment ->
            fragment.onFileClick(LocalFile(backup))
            send(activity, failed(BackupHandlerFragment.BACKUP_SERVICE_CALLER_ID))

            fragment.onFileClick(LocalFile(backup))

            val started = everyStarted(activity).single()
            assertEquals(BackupHandlerIntentService.ACTION_COUNT, started.getIntExtra(BackupHandlerIntentService.ACTION, 0))
        }
    }

    @Test
    fun twoTapsOnAProtectedBackupAskTwiceAndOnlyTheFirstOkStartsTheCount() {
        val backup = File(folder, "backup_2026-01-01_10-00-00.mwbs")
        withFragment { activity, fragment ->
            fragment.onFileClick(LocalFile(backup))
            fragment.onFileClick(LocalFile(backup))
            assertTrue(everyStarted(activity).isEmpty())
            val dialogs = ShadowDialog.getShownDialogs().map { it as AlertDialog }
            assertEquals(2, dialogs.size)

            for (dialog in dialogs) {
                dialog.findViewById<EditText>(R.id.dialog_input_edit_text)!!.setText(PASSWORD)
                click(dialog, DialogInterface.BUTTON_POSITIVE)
            }

            val started = everyStarted(activity).single()
            assertEquals(BackupHandlerIntentService.ACTION_COUNT, started.getIntExtra(BackupHandlerIntentService.ACTION, 0))
            assertEquals(PASSWORD, started.getStringExtra(BackupHandlerIntentService.PASSWORD))
        }
    }

    @Test
    fun aTapOnALegacyBackupWhileACountIsPendingShowsNothing() {
        withFragment { activity, fragment ->
            fragment.onFileClick(LocalFile(File(folder, "backup_2026-01-01_10-00-00.mwbx")))
            everyStarted(activity)

            fragment.onFileClick(LocalFile(File(folder, "backup.mwb")))

            assertNull(ShadowDialog.getLatestDialog())
            assertTrue(everyStarted(activity).isEmpty())
        }
    }

    @Test
    fun aTapOnAFolderWhileACountIsPendingListsIt() {
        val child = File(folder, "older")
        val childExisted = child.exists()
        FileUtils.forceMkdir(child)
        try {
            withFragment { activity, fragment ->
                fragment.onFileClick(LocalFile(File(folder, "backup_2026-01-01_10-00-00.mwbx")))
                everyStarted(activity)

                fragment.onFileClick(LocalFile(child))

                val started = everyStarted(activity).single()
                assertEquals(BackupHandlerIntentService.ACTION_LIST, started.getIntExtra(BackupHandlerIntentService.ACTION, 0))
                assertEquals(child, started.getParcelableExtra<LocalFile>(BackupHandlerIntentService.PARENT_FOLDER)!!.getFile())
            }
        } finally {
            if (!childExisted) {
                FileUtils.deleteQuietly(child)
            }
        }
    }

    @Test
    fun aCountAnswerForAnotherCallerKeepsTheCountPending() {
        val backup = File(folder, "backup_2026-01-01_10-00-00.mwbx")
        withFragment { activity, fragment ->
            fragment.onFileClick(LocalFile(backup))
            send(activity, finished(backup, null, 3, 1, 3, 1).putExtra(BackupHandlerIntentService.CALLER_ID, "another"))
            send(activity, failed("another"))

            fragment.onFileClick(LocalFile(backup))

            assertTrue(everyStarted(activity).isEmpty())
        }
    }

    @Test
    fun aFailedCountStartsNothing() {
        withFragment { activity, fragment ->
            fragment.onFileClick(LocalFile(File(folder, "backup_2026-01-01_10-00-00.mwbx")))
            send(activity, failed(BackupHandlerFragment.BACKUP_SERVICE_CALLER_ID))

            assertTrue(everyStarted(activity).isEmpty())
        }
    }

    @Test
    fun aFinishedCountStartsNothing() {
        val backup = File(folder, "backup_2026-01-01_10-00-00.mwbx")
        withFragment { activity, fragment ->
            fragment.onFileClick(LocalFile(backup))
            send(activity, finished(backup, null, 3, 1, 3, 1))

            assertTrue(everyStarted(activity).isEmpty())
        }
    }

    @Test
    fun aFileWithTransactionsDoesNotWarnWhenTheAppHasNone() {
        assertEquals("backup.mwbx\n\n" +
                "This file has 2 transactions and 1 wallet. Tallybook has 0 transactions and 0 wallets now.\n\n" +
                context.getString(R.string.message_backup_restore_standard), confirmAfter(2, 1, 0, 0))
    }

    @Test
    fun aFinishedCountForAnotherBackendShowsNothingAndKeepsTheCountPending() {
        val backup = File(folder, "backup_2026-01-01_10-00-00.mwbx")
        withFragment { activity, fragment ->
            fragment.onFileClick(LocalFile(backup))
            send(activity, finished(backup, null, 3, 1, 3, 1)
                    .putExtra(BackupHandlerIntentService.BACKEND_ID, BackendServiceFactory.SERVICE_ID_WEBDAV))

            fragment.onFileClick(LocalFile(backup))

            assertNull(ShadowDialog.getLatestDialog())
            assertTrue(everyStarted(activity).isEmpty())
        }
    }

    @Test
    fun aFailedCountForAnotherBackendKeepsTheCountPending() {
        val backup = File(folder, "backup_2026-01-01_10-00-00.mwbx")
        withFragment { activity, fragment ->
            fragment.onFileClick(LocalFile(backup))
            send(activity, failed(BackupHandlerFragment.BACKUP_SERVICE_CALLER_ID)
                    .putExtra(BackupHandlerIntentService.BACKEND_ID, BackendServiceFactory.SERVICE_ID_WEBDAV))

            fragment.onFileClick(LocalFile(backup))

            assertTrue(everyStarted(activity).isEmpty())
        }
    }

    @Test
    fun aFinishedCountForThisBackendShowsTheConfirmAndEndsTheCount() {
        val backup = File(folder, "backup_2026-01-01_10-00-00.mwbx")
        withFragment { activity, fragment ->
            fragment.onFileClick(LocalFile(backup))
            send(activity, finished(backup, null, 3, 1, 3, 1)
                    .putExtra(BackupHandlerIntentService.BACKEND_ID, BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY))

            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertEquals("backup_2026-01-01_10-00-00.mwbx\n\n" +
                    "This file has 3 transactions and 1 wallet. Tallybook has 3 transactions and 1 wallet now.\n\n" +
                    context.getString(R.string.message_backup_restore_standard), messageOf(dialog))
            click(dialog, DialogInterface.BUTTON_NEGATIVE)
            fragment.onFileClick(LocalFile(backup))

            val started = everyStarted(activity).single()
            assertEquals(BackupHandlerIntentService.ACTION_COUNT, started.getIntExtra(BackupHandlerIntentService.ACTION, 0))
        }
    }

    @Test
    fun theOkOfALegacyDialogShownBeforeACountStartedStartsNoRestore() {
        withFragment { activity, fragment ->
            fragment.onFileClick(LocalFile(File(folder, "backup.mwb")))
            val legacy = ShadowDialog.getLatestDialog() as AlertDialog
            assertEquals(context.getString(R.string.message_backup_restore_legacy), messageOf(legacy))
            fragment.onFileClick(LocalFile(File(folder, "backup_2026-01-01_10-00-00.mwbx")))

            click(legacy, DialogInterface.BUTTON_POSITIVE)

            val started = everyStarted(activity).map { it.getIntExtra(BackupHandlerIntentService.ACTION, 0) }
            assertEquals(listOf(BackupHandlerIntentService.ACTION_COUNT), started)
        }
    }

    @Test
    fun aTapAfterAFailedCountInTheLocalFolderStartsTheCountAgain() {
        val backup = File(folder, "backup_2026-01-01_10-00-00.mwbx")
        withFragment(BackendServiceFactory.SERVICE_ID_SAF) { activity, fragment ->
            fragment.onFileClick(LocalFile(backup))
            val started = startedAfterTheList(activity).single()
            assertEquals(BackupHandlerIntentService.ACTION_COUNT, started.getIntExtra(BackupHandlerIntentService.ACTION, 0))
            assertEquals(BackendServiceFactory.SERVICE_ID_SAF, started.getStringExtra(BackupHandlerIntentService.BACKEND_ID))
            send(activity, failed(BackupHandlerFragment.BACKUP_SERVICE_CALLER_ID)
                    .putExtra(BackupHandlerIntentService.BACKEND_ID, BackendServiceFactory.SERVICE_ID_SAF))

            fragment.onFileClick(LocalFile(backup))

            val again = everyStarted(activity).single()
            assertEquals(BackupHandlerIntentService.ACTION_COUNT, again.getIntExtra(BackupHandlerIntentService.ACTION, 0))
            assertEquals(BackendServiceFactory.SERVICE_ID_SAF, again.getStringExtra(BackupHandlerIntentService.BACKEND_ID))
        }
    }

    @Test
    fun aFinishedCountInTheLocalFolderShowsTheConfirmAndItsOkRestoresFromTheLocalFolder() {
        val backup = File(folder, "backup_2026-01-01_10-00-00.mwbx")
        withFragment(BackendServiceFactory.SERVICE_ID_SAF) { activity, fragment ->
            fragment.onFileClick(LocalFile(backup))
            val started = startedAfterTheList(activity).single()
            assertEquals(BackupHandlerIntentService.ACTION_COUNT, started.getIntExtra(BackupHandlerIntentService.ACTION, 0))
            assertEquals(BackendServiceFactory.SERVICE_ID_SAF, started.getStringExtra(BackupHandlerIntentService.BACKEND_ID))
            send(activity, finished(backup, null, 3, 1, 3, 1)
                    .putExtra(BackupHandlerIntentService.BACKEND_ID, BackendServiceFactory.SERVICE_ID_SAF))

            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertEquals("backup_2026-01-01_10-00-00.mwbx\n\n" +
                    "This file has 3 transactions and 1 wallet. Tallybook has 3 transactions and 1 wallet now.\n\n" +
                    context.getString(R.string.message_backup_restore_standard), messageOf(dialog))
            click(dialog, DialogInterface.BUTTON_POSITIVE)

            val restore = everyStarted(activity).single()
            assertEquals(BackupHandlerIntentService.ACTION_RESTORE, restore.getIntExtra(BackupHandlerIntentService.ACTION, 0))
            assertEquals(BackendServiceFactory.SERVICE_ID_SAF, restore.getStringExtra(BackupHandlerIntentService.BACKEND_ID))
            assertEquals(backup, restore.getParcelableExtra<LocalFile>(BackupHandlerIntentService.BACKUP_FILE)!!.getFile())
        }
    }

    private fun confirmAfter(backupTransactions: Int, backupWallets: Int, currentTransactions: Int, currentWallets: Int): String {
        var message = ""
        withFragment { activity, _ ->
            send(activity, finished(File(folder, "backup.mwbx"), null, backupTransactions, backupWallets, currentTransactions, currentWallets))
            message = messageOf(ShadowDialog.getLatestDialog())
        }
        return message
    }

    private fun finished(backup: File, password: String?, backupTransactions: Int, backupWallets: Int,
                         currentTransactions: Int, currentWallets: Int): Intent {
        val intent = Intent(LocalAction.ACTION_BACKUP_SERVICE_FINISHED)
                .putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_COUNT)
                .putExtra(BackupHandlerIntentService.BACKUP_FILE, LocalFile(backup))
                .putExtra(BackupHandlerIntentService.BACKUP_TRANSACTIONS, backupTransactions)
                .putExtra(BackupHandlerIntentService.BACKUP_WALLETS, backupWallets)
                .putExtra(BackupHandlerIntentService.CURRENT_TRANSACTIONS, currentTransactions)
                .putExtra(BackupHandlerIntentService.CURRENT_WALLETS, currentWallets)
                .putExtra(BackupHandlerIntentService.BACKEND_ID, BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY)
                .putExtra(BackupHandlerIntentService.CALLER_ID, BackupHandlerFragment.BACKUP_SERVICE_CALLER_ID)
        if (password != null) {
            intent.putExtra(BackupHandlerIntentService.PASSWORD, password)
        }
        return intent
    }

    private fun failed(callerId: String): Intent = Intent(LocalAction.ACTION_BACKUP_SERVICE_FAILED)
            .putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_COUNT)
            .putExtra(BackupHandlerIntentService.EXCEPTION, ImportException("Wrong Password for file: database.json"))
            .putExtra(BackupHandlerIntentService.BACKEND_ID, BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY)
            .putExtra(BackupHandlerIntentService.CALLER_ID, callerId)

    private fun send(activity: FragmentActivity, broadcast: Intent) {
        startedAfterTheList(activity)
        LocalBroadcastManager.getInstance(activity).sendBroadcastSync(broadcast)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun click(dialog: AlertDialog, which: Int) {
        dialog.getButton(which).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun messageOf(dialog: Dialog): String = dialog.findViewById<TextView>(android.R.id.message)!!.text.toString()

    private fun titleOf(dialog: Dialog): String =
            dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)!!.text.toString()

    private fun startedAfterTheList(activity: FragmentActivity): List<Intent> {
        val started = mutableListOf<Intent>()
        while (true) {
            val intent = shadowOf(activity).nextStartedService ?: return started
            if (intent.getIntExtra(BackupHandlerIntentService.ACTION, 0) != BackupHandlerIntentService.ACTION_LIST) {
                started.add(intent)
            }
        }
    }

    private fun everyStarted(activity: FragmentActivity): List<Intent> {
        val started = mutableListOf<Intent>()
        while (true) {
            started.add(shadowOf(activity).nextStartedService ?: return started)
        }
    }

    private fun withFragment(backendId: String = BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY,
                             block: (FragmentActivity, BackupHandlerFragment) -> Unit) {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = BackupHandlerFragment.newInstance(backendId, true, true)
                activity.supportFragmentManager
                        .beginTransaction()
                        .add(android.R.id.content, fragment, "BackupHandlerFragmentCountTest")
                        .commitNow()
                startedAfterTheList(activity)
                block(activity, fragment)
            }
        }
    }
}
