package com.oriondev.moneywallet.ui.fragment.secondary

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Environment
import android.os.Looper
import android.widget.TextView

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.api.BackendServiceFactory
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.model.BackupService
import com.oriondev.moneywallet.model.LocalFile
import com.oriondev.moneywallet.service.BackupHandlerIntentService
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.BackupListActivity
import com.oriondev.moneywallet.ui.fragment.multipanel.BackupMultiPanelFragment

import org.apache.commons.io.FileUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
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
class BackupMultiPanelCountTest {

    companion object {

        private const val TAG_FRAGMENT_BACKUP = "BackupListActivity::tag::BackupMultiPanelFragment"
        private const val TAG_FRAGMENT_SERVICE = "BackupMultiPanelFragment::Fragment::"
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
    fun aFinishedCountAfterASwitchToAnotherBackendShowsNoConfirmThereAndTheNewFragmentRestoresFromItsBackend() {
        val backup = File(folder, "backup_2026-01-01_10-00-00.mwbx")
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val first = showBackupHandler(activity, BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY)
                first.onFileClick(LocalFile(backup))
                assertEquals(BackupHandlerIntentService.ACTION_COUNT,
                        startedAfterTheList(activity).single().getIntExtra(BackupHandlerIntentService.ACTION, 0))

                showBackupHandler(activity, BackendServiceFactory.SERVICE_ID_SAF)
                assertTrue(first.isRemoving || first.isDetached || !first.isAdded)
                send(activity, finished(backup, BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY))

                assertNull(ShadowDialog.getLatestDialog())

                val second = showBackupHandler(activity, BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY)
                assertNotSame(first, second)
                everyStarted(activity)
                send(activity, finished(backup, BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY))
                click(ShadowDialog.getLatestDialog() as AlertDialog, DialogInterface.BUTTON_POSITIVE)

                val restore = startedAfterTheList(activity).single()
                assertEquals(BackupHandlerIntentService.ACTION_RESTORE, restore.getIntExtra(BackupHandlerIntentService.ACTION, 0))
                assertEquals(BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY, restore.getStringExtra(BackupHandlerIntentService.BACKEND_ID))

                second.onFileClick(LocalFile(backup))

                assertEquals(BackupHandlerIntentService.ACTION_COUNT,
                        startedAfterTheList(activity).single().getIntExtra(BackupHandlerIntentService.ACTION, 0))
            }
        }
    }

    @Test
    fun aCountStartedBeforeARecreateShowsTheConfirmInTheRestoredFragmentAndItsOkRestoresFromItsBackend() {
        val backup = File(folder, "backup_2026-01-01_10-00-00.mwbx")
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = showBackupHandler(activity, BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY)
                fragment.onFileClick(LocalFile(backup))
                assertEquals(BackupHandlerIntentService.ACTION_COUNT,
                        startedAfterTheList(activity).single().getIntExtra(BackupHandlerIntentService.ACTION, 0))
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                shadowOf(Looper.getMainLooper()).idle()
                assertNotNull(backupMultiPanel(activity).childFragmentManager
                        .findFragmentByTag(TAG_FRAGMENT_SERVICE + BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY))
                everyStarted(activity)
                send(activity, finished(backup, BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY))

                val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                assertTrue(dialog.findViewById<TextView>(android.R.id.message)!!.text.toString().startsWith("backup_2026-01-01_10-00-00.mwbx"))
                click(dialog, DialogInterface.BUTTON_POSITIVE)

                val restore = startedAfterTheList(activity).single()
                assertEquals(BackupHandlerIntentService.ACTION_RESTORE, restore.getIntExtra(BackupHandlerIntentService.ACTION, 0))
                assertEquals(BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY, restore.getStringExtra(BackupHandlerIntentService.BACKEND_ID))
            }
        }
    }

    private fun backupMultiPanel(activity: FragmentActivity): BackupMultiPanelFragment =
            activity.supportFragmentManager.findFragmentByTag(TAG_FRAGMENT_BACKUP) as BackupMultiPanelFragment

    private fun showBackupHandler(activity: FragmentActivity, backendId: String): BackupHandlerFragment {
        val multiPanel = backupMultiPanel(activity)
        multiPanel.onBackupServiceClick(BackupService(backendId, 0, 0))
        multiPanel.childFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()
        return multiPanel.childFragmentManager.findFragmentByTag(TAG_FRAGMENT_SERVICE + backendId) as BackupHandlerFragment
    }

    private fun finished(backup: File, backendId: String): Intent = Intent(LocalAction.ACTION_BACKUP_SERVICE_FINISHED)
            .putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_COUNT)
            .putExtra(BackupHandlerIntentService.BACKUP_FILE, LocalFile(backup))
            .putExtra(BackupHandlerIntentService.BACKUP_TRANSACTIONS, 3)
            .putExtra(BackupHandlerIntentService.BACKUP_WALLETS, 1)
            .putExtra(BackupHandlerIntentService.BACKEND_ID, backendId)
            .putExtra(BackupHandlerIntentService.CALLER_ID, BackupHandlerFragment.BACKUP_SERVICE_CALLER_ID)

    private fun send(activity: FragmentActivity, broadcast: Intent) {
        LocalBroadcastManager.getInstance(activity).sendBroadcastSync(broadcast)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun click(dialog: AlertDialog, which: Int) {
        dialog.getButton(which).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

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
}
