package com.oriondev.moneywallet.ui.fragment.secondary

import android.content.Context
import android.content.Intent
import android.os.Environment

import androidx.fragment.app.FragmentActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.api.BackendException
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackupHandlerFragmentRestoreTest {

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
    fun aFinishedRestoreListsTheCurrentFolderAgain() {
        assertEquals(folder, listedAfter(Intent(LocalAction.ACTION_BACKUP_SERVICE_FINISHED), BackupHandlerFragment.BACKUP_SERVICE_CALLER_ID))
    }

    @Test
    fun aFailedRestoreThatKeptACopyListsTheCurrentFolderAgain() {
        val failed = Intent(LocalAction.ACTION_BACKUP_SERVICE_FAILED)
                .putExtra(BackupHandlerIntentService.EXCEPTION, ImportException("broken attachment"))
                .putExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY, File(folder, "before_restore_2020-01-01_00-00-00.mwbx").absolutePath)
        assertEquals(folder, listedAfter(failed, BackupHandlerFragment.BACKUP_SERVICE_CALLER_ID))
    }

    @Test
    fun aFailedRestoreWithoutACopyListsNothing() {
        val failed = Intent(LocalAction.ACTION_BACKUP_SERVICE_FAILED)
                .putExtra(BackupHandlerIntentService.EXCEPTION, BackendException("unauthorized"))
        assertNull(listedAfter(failed, BackupHandlerFragment.BACKUP_SERVICE_CALLER_ID))
    }

    @Test
    fun aFailedRestoreThatKeptACopyForAnotherCallerListsNothing() {
        val failed = Intent(LocalAction.ACTION_BACKUP_SERVICE_FAILED)
                .putExtra(BackupHandlerIntentService.EXCEPTION, ImportException("broken attachment"))
                .putExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY, File(folder, "before_restore_2020-01-01_00-00-00.mwbx").absolutePath)
        assertNull(listedAfter(failed, "another"))
    }

    @Test
    fun aRestoreForAnotherCallerListsNothing() {
        assertNull(listedAfter(Intent(LocalAction.ACTION_BACKUP_SERVICE_FINISHED), "another"))
        assertNull(listedAfter(Intent(LocalAction.ACTION_BACKUP_SERVICE_FAILED)
                .putExtra(BackupHandlerIntentService.EXCEPTION, ImportException("broken attachment")), "another"))
    }

    private fun listedAfter(broadcast: Intent, callerId: String): File? {
        var listed: File? = null
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = showBackupHandler(activity)
                fragment.onFileClick(LocalFile(folder))
                assertEquals(folder, (lastStartedList(activity)!!.getParcelableExtra<LocalFile>(BackupHandlerIntentService.PARENT_FOLDER))!!.getFile())
                broadcast.putExtra(BackupHandlerIntentService.CALLER_ID, callerId)
                        .putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_RESTORE)
                LocalBroadcastManager.getInstance(activity).sendBroadcastSync(broadcast)
                listed = lastStartedList(activity)?.getParcelableExtra<LocalFile>(BackupHandlerIntentService.PARENT_FOLDER)?.getFile()
            }
        }
        return listed
    }

    private fun lastStartedList(activity: FragmentActivity): Intent? {
        var last: Intent? = null
        while (true) {
            val started = shadowOf(activity).nextStartedService ?: return last
            assertEquals(BackupHandlerIntentService.ACTION_LIST, started.getIntExtra(BackupHandlerIntentService.ACTION, 0))
            last = started
        }
    }

    private fun showBackupHandler(activity: FragmentActivity): BackupHandlerFragment {
        val fragment = BackupHandlerFragment.newInstance(BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY, true, true)
        activity.supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, fragment, "BackupHandlerFragmentRestoreTest")
                .commitNow()
        return fragment
    }
}
