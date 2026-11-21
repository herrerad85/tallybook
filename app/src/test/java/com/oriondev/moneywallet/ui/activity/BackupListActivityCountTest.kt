package com.oriondev.moneywallet.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.widget.TextView

import androidx.fragment.app.FragmentActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.service.BackupHandlerIntentService
import com.oriondev.moneywallet.storage.database.ImportException
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.fragment.dialog.GenericProgressDialog
import com.oriondev.moneywallet.ui.fragment.secondary.BackupHandlerFragment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
class BackupListActivityCountTest {

    companion object {

        private const val PROGRESS_DIALOG = "BackupListActivity::tag::GenericProgressDialog"
        private const val FAILURE = "Wrong Password for file: database.json"
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.initialize(context)
        TestDatabases.useFreshDatabase(context)
    }

    @Test
    fun aStartedCountShowsTheRestoreProgressDialog() {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                send(activity, Intent(LocalAction.ACTION_BACKUP_SERVICE_STARTED))

                val progress = activity.supportFragmentManager.findFragmentByTag(PROGRESS_DIALOG) as GenericProgressDialog
                val dialog = progress.requireDialog()
                assertTrue(dialog.isShowing)
                assertEquals(context.getString(R.string.title_backup_restore),
                        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)!!.text.toString())
                assertEquals(context.getString(R.string.message_async_init),
                        dialog.findViewById<TextView>(R.id.dialog_progress_message)!!.text.toString())

                send(activity, Intent(LocalAction.ACTION_BACKUP_SERVICE_RUNNING)
                        .putExtra(BackupHandlerIntentService.PROGRESS_STATUS, BackupHandlerIntentService.STATUS_BACKUP_DOWNLOADING)
                        .putExtra(BackupHandlerIntentService.PROGRESS_VALUE, 40))

                assertEquals(context.getString(R.string.message_backup_status_downloading),
                        dialog.findViewById<TextView>(R.id.dialog_progress_message)!!.text.toString())
            }
        }
    }

    @Test
    fun aFinishedCountClosesTheProgressDialogAndShowsNoOther() {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                send(activity, Intent(LocalAction.ACTION_BACKUP_SERVICE_STARTED))
                val progress = activity.supportFragmentManager.findFragmentByTag(PROGRESS_DIALOG) as GenericProgressDialog

                send(activity, Intent(LocalAction.ACTION_BACKUP_SERVICE_FINISHED))

                assertNull(activity.supportFragmentManager.findFragmentByTag(PROGRESS_DIALOG))
                assertNull(progress.dialog)
                assertEquals(emptyList<Any>(), ShadowDialog.getShownDialogs().filter { it.isShowing })
            }
        }
    }

    @Test
    fun aFailedCountShowsTheRestoreFailureWithTheMessage() {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                send(activity, Intent(LocalAction.ACTION_BACKUP_SERVICE_STARTED))

                send(activity, Intent(LocalAction.ACTION_BACKUP_SERVICE_FAILED)
                        .putExtra(BackupHandlerIntentService.EXCEPTION, ImportException(FAILURE)))

                assertNull(activity.supportFragmentManager.findFragmentByTag(PROGRESS_DIALOG))
                val dialog = ShadowDialog.getLatestDialog()
                assertTrue(dialog.isShowing)
                assertEquals(context.getString(R.string.title_failed),
                        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)!!.text.toString())
                assertEquals(context.getString(R.string.message_backup_restoring_failed, FAILURE),
                        dialog.findViewById<TextView>(android.R.id.message)!!.text.toString())
            }
        }
    }

    private fun send(activity: FragmentActivity, broadcast: Intent) {
        broadcast.putExtra(BackupHandlerIntentService.CALLER_ID, BackupHandlerFragment.BACKUP_SERVICE_CALLER_ID)
                .putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_COUNT)
        LocalBroadcastManager.getInstance(activity).sendBroadcastSync(broadcast)
        activity.supportFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()
    }
}
