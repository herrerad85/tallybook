package com.oriondev.moneywallet.ui.activity

import android.content.Context
import android.content.Intent
import android.widget.TextView

import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.service.BackupHandlerIntentService
import com.oriondev.moneywallet.storage.database.ImportException
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.fragment.secondary.BackupHandlerFragment

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
class BackupListActivityTest {

    companion object {

        private const val PLAIN_COPY = "/storage/emulated/0/Documents/Tallybook/before_restore_2026-01-01_10-00-00.mwbx"
        private const val ENCRYPTED_COPY = "/storage/emulated/0/Documents/Tallybook/before_restore_2026-01-01_10-00-00.mwbs"
        private const val FAILURE = "broken attachment"
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.initialize(context)
        TestDatabases.useFreshDatabase(context)
    }

    @Test
    fun aRestoreWithAPlainCopySaysWhereTheCopyIs() {
        assertEquals(context.getString(R.string.message_backup_restoring_success) + "\n\n" +
                context.getString(R.string.message_backup_restoring_copy_saved, PLAIN_COPY), messageAfterRestore(PLAIN_COPY))
    }

    @Test
    fun aRestoreWithAnEncryptedCopyAlsoSaysItUsesTheSamePassword() {
        assertEquals(context.getString(R.string.message_backup_restoring_success) + "\n\n" +
                context.getString(R.string.message_backup_restoring_copy_saved, ENCRYPTED_COPY) + "\n\n" +
                context.getString(R.string.message_backup_restoring_copy_password), messageAfterRestore(ENCRYPTED_COPY))
    }

    @Test
    fun aRestoreWithoutACopyShowsTheSuccessTextAlone() {
        assertEquals(context.getString(R.string.message_backup_restoring_success), messageAfterRestore(null))
    }

    @Test
    fun aFailedRestoreWithAPlainCopySaysWhereTheCopyIsAfterTheFailure() {
        assertEquals(context.getString(R.string.message_backup_restoring_failed, FAILURE) + "\n\n" +
                context.getString(R.string.message_backup_restoring_copy_saved, PLAIN_COPY), messageAfterFailedRestore(PLAIN_COPY))
    }

    @Test
    fun aFailedRestoreWithAnEncryptedCopyAlsoSaysItUsesTheSamePassword() {
        assertEquals(context.getString(R.string.message_backup_restoring_failed, FAILURE) + "\n\n" +
                context.getString(R.string.message_backup_restoring_copy_saved, ENCRYPTED_COPY) + "\n\n" +
                context.getString(R.string.message_backup_restoring_copy_password), messageAfterFailedRestore(ENCRYPTED_COPY))
    }

    @Test
    fun aFailedRestoreWithoutACopyShowsTheFailureTextAlone() {
        assertEquals(context.getString(R.string.message_backup_restoring_failed, FAILURE), messageAfterFailedRestore(null))
    }

    private fun messageAfterFailedRestore(copy: String?): String {
        return messageAfter(Intent(LocalAction.ACTION_BACKUP_SERVICE_FAILED)
                .putExtra(BackupHandlerIntentService.EXCEPTION, ImportException(FAILURE)), copy)
    }

    private fun messageAfterRestore(copy: String?): String {
        return messageAfter(Intent(LocalAction.ACTION_BACKUP_SERVICE_FINISHED), copy)
    }

    private fun messageAfter(broadcast: Intent, copy: String?): String {
        var message = ""
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                broadcast.putExtra(BackupHandlerIntentService.CALLER_ID, BackupHandlerFragment.BACKUP_SERVICE_CALLER_ID)
                        .putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_RESTORE)
                if (copy != null) {
                    broadcast.putExtra(BackupHandlerIntentService.BEFORE_RESTORE_COPY, copy)
                }
                LocalBroadcastManager.getInstance(activity).sendBroadcastSync(broadcast)
                val dialog = ShadowDialog.getLatestDialog()
                message = dialog.findViewById<TextView>(android.R.id.message)!!.text.toString()
                dialog.dismiss()
            }
        }
        return message
    }
}
