/*
 * Copyright (c) 2018.
 *
 * This file is part of MoneyWallet.
 *
 * MoneyWallet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MoneyWallet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MoneyWallet.  If not, see <http://www.gnu.org/licenses/>.
 */


package com.oriondev.moneywallet.service

import android.app.IntentService
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.api.BackendException
import com.oriondev.moneywallet.api.BackendServiceFactory
import com.oriondev.moneywallet.api.IBackendServiceAPI
import com.oriondev.moneywallet.broadcast.AutoBackupBroadcastReceiver
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.broadcast.NotificationBroadcastReceiver
import com.oriondev.moneywallet.broadcast.RecurrenceBroadcastReceiver
import com.oriondev.moneywallet.model.IFile
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.ExportException
import com.oriondev.moneywallet.storage.database.ImportException
import com.oriondev.moneywallet.storage.database.SQLDatabaseImporter
import com.oriondev.moneywallet.storage.database.backup.AbstractBackupImporter
import com.oriondev.moneywallet.storage.database.backup.BackupManager
import com.oriondev.moneywallet.storage.database.backup.DefaultBackupImporter
import com.oriondev.moneywallet.storage.database.backup.LegacyBackupImporter
import com.oriondev.moneywallet.storage.preference.BackendManager
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.notification.NotificationContract
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.ProgressOutputStream
import com.oriondev.moneywallet.utils.Utils

import org.apache.commons.io.FileUtils

import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Created by andrea on 21/11/18.
 */
class BackupHandlerIntentService : IntentService {

    companion object {

        const val ACTION = "BackupHandlerIntentService::Argument::Action"
        const val BACKEND_ID = "BackupHandlerIntentService::Argument::BackendId"
        const val AUTO_BACKUP = "BackupHandlerIntentService::Argument::AutoBackup"
        const val ONLY_ON_WIFI = "BackupHandlerIntentService::Argument::OnlyOnWifi"
        const val RUN_FOREGROUND = "BackupHandlerIntentService::Argument::RunForeground"
        const val BACKUP_FILE = "BackupHandlerIntentService::Argument::BackupFile"
        const val EXCEPTION = "BackupHandlerIntentService::Argument::Exception"
        const val FOLDER_CONTENT = "BackupHandlerIntentService::Argument::FolderContent"
        const val PARENT_FOLDER = "BackupHandlerIntentService::Argument::ParentFolder"
        const val PASSWORD = "BackupHandlerIntentService::Argument::Password"
        const val PROGRESS_STATUS = "BackupHandlerIntentService::Argument::ProgressStatus"
        const val PROGRESS_VALUE = "BackupHandlerIntentService::Argument::ProgressValue"
        const val CALLER_ID = "BackupHandlerIntentService::Argument::CallerId"

        private const val BACKUP_CACHE_FOLDER = "backups"
        private const val TEMP_FOLDER = "temp"

        private const val ACTION_NONE = 0
        const val ACTION_LIST = 1
        const val ACTION_BACKUP = 2
        const val ACTION_RESTORE = 3

        const val STATUS_BACKUP_CREATION = 1
        const val STATUS_BACKUP_UPLOADING = 2
        const val STATUS_BACKUP_DOWNLOADING = 3
        const val STATUS_BACKUP_RESTORING = 4

        private const val DEFAULT_AUTO_BACKUP = false
        private const val DEFAULT_ONLY_ON_WIFI = false
        private const val DEFAULT_RUN_FOREGROUND = false

        @JvmStatic
        fun startInForeground(context: Context, intent: Intent) {
            intent.putExtra(BackupHandlerIntentService.RUN_FOREGROUND, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private var mBackendServiceAPI: IBackendServiceAPI? = null

    private var mCallerId: String? = null
    private var mReporter: TaskReporter? = null
    private var mNotificationBuilder: NotificationCompat.Builder? = null

    constructor() : super("BackupHandlerIntentService")

    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            // unpack the base intent
            val action = intent.getIntExtra(ACTION, ACTION_NONE)
            val backendId: String? = intent.getStringExtra(BACKEND_ID)
            val runForeground = intent.getBooleanExtra(RUN_FOREGROUND, DEFAULT_RUN_FOREGROUND)
            mCallerId = intent.getStringExtra(CALLER_ID)
            // execute the action in a safe code-block: if an exception is thrown, it
            // is handled by both the local broadcast manager as an error and reported
            // in the notification if it is required
            var exception: Exception? = null
            try {
                // initialize the task reporter
                mReporter = TaskReporter(this)
                // if the notification is required, start the service in foreground
                if (runForeground && (action == ACTION_BACKUP || action == ACTION_RESTORE)) {
                    mNotificationBuilder = mReporter!!.newNotification(NotificationContract.NOTIFICATION_CHANNEL_BACKUP)
                            .setProgress(0, 0, true)
                            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                }
                // send a local message to notify the receivers that the task is started
                notifyTaskStarted(action)
                // check if backend id is available and initialize it
                mBackendServiceAPI = BackendServiceFactory.getServiceAPIById(this, backendId)
                when (action) {
                    ACTION_LIST -> {
                        onActionList(intent)
                    }
                    ACTION_BACKUP -> {
                        onActionBackup(intent)
                    }
                    ACTION_RESTORE -> {
                        onActionRestore(intent)
                    }
                }
            } catch (e: Exception) {
                exception = e
            }
            // handle the exception if the task failed
            if (exception != null) {
                if (exception is BackendException) {
                    if (!exception.isRecoverable()) {
                        // disable auto-backup for this backend id
                        BackendManager.disableAutoBackupAfterFailure(backendId)
                        AutoBackupBroadcastReceiver.scheduleAutoBackupTask(this)
                    }
                }
                notifyTaskFailure(intent, exception)
            }
            // clear the environment
            if (mNotificationBuilder != null) {
                stopForeground(true)
            }
        }
    }

    @Throws(BackendException::class)
    private fun onActionList(intent: Intent) {
        val remoteFolder: IFile? = intent.getParcelableExtra(PARENT_FOLDER)
        val fileList: List<IFile> = mBackendServiceAPI!!.getFolderContent(remoteFolder)
        notifyListTaskFinished(fileList)
    }

    @Throws(ExportException::class, BackendException::class, IOException::class)
    private fun onActionBackup(intent: Intent) {
        val remoteFolder: IFile? = intent.getParcelableExtra(PARENT_FOLDER)
        val password: String? = intent.getStringExtra(PASSWORD)
        val uploaded = BackupOperation.createAndUpload(this, mBackendServiceAPI!!, remoteFolder, password, object : BackupOperation.ProgressListener {

            override fun onProgress(status: Int, progress: Int) {
                notifyTaskProgress(ACTION_BACKUP, status, progress)
            }

        })
        notifyUploadTaskFinished(uploaded)
    }

    @Throws(ImportException::class, BackendException::class, IOException::class)
    private fun onActionRestore(intent: Intent) {
        val remoteFile: IFile? = intent.getParcelableExtra(BACKUP_FILE)
        if (remoteFile != null) {
            val folder: File? = getExternalFilesDir(null)
            val cache = File(folder, BACKUP_CACHE_FOLDER)
            val revision = File(cache, UUID.randomUUID().toString())
            try {
                FileUtils.forceMkdir(revision)
                notifyTaskProgress(ACTION_RESTORE, STATUS_BACKUP_DOWNLOADING, 0)
                val backup = mBackendServiceAPI!!.downloadFile(revision, remoteFile, object : ProgressOutputStream.DownloadProgressListener {

                    override fun onDownloadProgressUpdate(percentage: Int) {
                        val realProgress = (percentage * 70 / 100)
                        notifyTaskProgress(ACTION_RESTORE, STATUS_BACKUP_DOWNLOADING, realProgress)
                    }

                })
                notifyTaskProgress(ACTION_RESTORE, STATUS_BACKUP_RESTORING, 75)
                val password: String? = intent.getStringExtra(PASSWORD)
                // The auto backup no longer runs on this service's serial queue, so a sweep
                // can now be reading the database and the attachment folder that the import
                // is about to replace and empty.
                synchronized(BackupOperation.LOCK) {
                    restoreLocalBackupFile(backup, password)
                }
                notifyTaskProgress(ACTION_RESTORE, STATUS_BACKUP_RESTORING, 100)
                PreferenceManager.setLastTimeDataIsChanged(0L)
                RecurrenceBroadcastReceiver.scheduleRecurrenceTask(this)
                AutoBackupBroadcastReceiver.scheduleAutoBackupTask(this)
                notifyTaskFinished(ACTION_RESTORE)
            } finally {
                FileUtils.deleteQuietly(revision)
            }
        } else {
            throw RuntimeException("Backup file to restore not specified")
        }
    }

    @Throws(ImportException::class, IOException::class)
    private fun restoreLocalBackupFile(backup: File, password: String?) {
        val importer: AbstractBackupImporter
        val fileName = backup.name
        if (fileName.endsWith(BackupManager.BACKUP_EXTENSION_LEGACY)) {
            importer = LegacyBackupImporter(this, backup)
        } else {
            importer = DefaultBackupImporter(this, backup, password)
        }
        val temporaryFolder = File(getExternalFilesDir(null), TEMP_FOLDER)
        FileUtils.forceMkdir(temporaryFolder)
        try {
            val databaseFile = getDatabasePath(SQLDatabaseImporter.DATABASE_NAME)
            importer.importDatabase(temporaryFolder, databaseFile.parentFile)
            // the count on a recurrence notification still showing belongs to the ledger just replaced
            NotificationManagerCompat.from(this).cancel(NotificationContract.NOTIFICATION_ID_RECURRENCE)
            // Announced here, between the two, and nowhere else. The ledger is live from the
            // rename inside importDatabase, so waiting until after the attachments would leave
            // the current wallet and every widget binding naming rows out of the database that
            // has just been replaced whenever the attachment pass fails. Announcing in both
            // places instead is worse. This call writes user state, the current wallet preference
            // and every widget wallet binding, the app stays interactive for the whole attachment
            // extraction, and a second announcement would silently undo whatever the user set
            // during it.
            //
            // The currency cache goes with it. It is held in memory and only this reloads it, so
            // announcing without it makes every screen redraw the restored rows through the
            // replaced database currencies.
            DataContentProvider.notifyDatabaseIsChanged(this)
            CurrencyManager.invalidateCache(this)
            importer.importAttachments(BackupOperation.getAttachmentFolder(this))
        } finally {
            FileUtils.cleanDirectory(temporaryFolder)
        }
    }

    private fun getNotificationContentTitle(action: Int, error: Boolean): String? {
        if (action == ACTION_BACKUP) {
            return getString(if (error) R.string.notification_title_backup_creation_failed else R.string.notification_title_backup_creation)
        } else if (action == ACTION_RESTORE) {
            return getString(if (error) R.string.notification_title_backup_restoring_failed else R.string.notification_title_backup_restoring)
        }
        return null
    }

    private fun getNotificationContentText(status: Int): String? {
        when (status) {
            STATUS_BACKUP_CREATION -> {
                return getString(R.string.notification_content_backup_file_creation)
            }
            STATUS_BACKUP_UPLOADING -> {
                return getString(R.string.notification_content_backup_file_uploading)
            }
            STATUS_BACKUP_DOWNLOADING -> {
                return getString(R.string.notification_content_backup_file_downloading)
            }
            STATUS_BACKUP_RESTORING -> {
                return getString(R.string.notification_content_backup_file_restoring)
            }
        }
        return null
    }

    private fun notifyTaskStarted(action: Int) {
        // notify the local broadcast manager
        val extras = Bundle()
        extras.putInt(ACTION, action)
        extras.putString(CALLER_ID, mCallerId)
        mReporter!!.broadcast(LocalAction.ACTION_BACKUP_SERVICE_STARTED, extras)
        // update the notification if required
        if (mNotificationBuilder != null) {
            mNotificationBuilder!!.setContentTitle(getNotificationContentTitle(action, false))
            ForegroundServices.startDataSync(this, NotificationContract.NOTIFICATION_ID_BACKUP_PROGRESS, mNotificationBuilder!!.build())
        }
    }

    private fun notifyTaskProgress(action: Int, status: Int, progress: Int) {
        // notify the local broadcast manager
        val extras = Bundle()
        extras.putInt(ACTION, action)
        extras.putInt(PROGRESS_STATUS, status)
        extras.putInt(PROGRESS_VALUE, progress)
        extras.putString(CALLER_ID, mCallerId)
        mReporter!!.broadcast(LocalAction.ACTION_BACKUP_SERVICE_RUNNING, extras)
        // update the notification if required
        if (mNotificationBuilder != null) {
            mNotificationBuilder!!.setContentText(getNotificationContentText(status))
            mNotificationBuilder!!.setProgress(100, progress, false)
            ForegroundServices.startDataSync(this, NotificationContract.NOTIFICATION_ID_BACKUP_PROGRESS, mNotificationBuilder!!.build())
        }
    }

    private fun notifyTaskFinished(action: Int) {
        // notify only the local broadcast manager: it is not required to update
        // the notification because it is removed when everything is gone right
        val extras = Bundle()
        extras.putInt(ACTION, action)
        extras.putString(CALLER_ID, mCallerId)
        mReporter!!.broadcast(LocalAction.ACTION_BACKUP_SERVICE_FINISHED, extras)
    }

    private fun notifyListTaskFinished(files: List<IFile>) {
        // notify only the local broadcast manager: it is not required to update
        // the notification because it is removed when everything is gone right
        val extras = Bundle()
        extras.putInt(ACTION, ACTION_LIST)
        extras.putParcelableArrayList(FOLDER_CONTENT, Utils.wrapAsArrayList(files))
        extras.putString(CALLER_ID, mCallerId)
        mReporter!!.broadcast(LocalAction.ACTION_BACKUP_SERVICE_FINISHED, extras)
    }

    private fun notifyUploadTaskFinished(file: IFile) {
        // notify only the local broadcast manager: it is not required to update
        // the notification because it is removed when everything is gone right
        val extras = Bundle()
        extras.putInt(ACTION, ACTION_BACKUP)
        extras.putParcelable(BACKUP_FILE, file)
        extras.putString(CALLER_ID, mCallerId)
        mReporter!!.broadcast(LocalAction.ACTION_BACKUP_SERVICE_FINISHED, extras)
    }

    private fun notifyTaskFailure(baseIntent: Intent, exception: Exception) {
        // notify the local broadcast manager
        val action = baseIntent.getIntExtra(ACTION, ACTION_NONE)
        val extras = Bundle()
        extras.putInt(ACTION, action)
        extras.putSerializable(EXCEPTION, exception)
        extras.putString(CALLER_ID, mCallerId)
        mReporter!!.broadcast(LocalAction.ACTION_BACKUP_SERVICE_FAILED, extras)
        // update the notification if required
        if (mNotificationBuilder != null) {
            mNotificationBuilder = mReporter!!.newNotification(NotificationContract.NOTIFICATION_CHANNEL_ERROR)
                    .setContentTitle(getNotificationContentTitle(action, true))
                    .setCategory(NotificationCompat.CATEGORY_ERROR)
            if (exception is BackendException) {
                if (exception.isRecoverable()) {
                    setupRetryNotification(baseIntent, action, R.string.notification_content_backup_error_backend_recoverable)
                } else {
                    mNotificationBuilder!!.setContentText(getString(R.string.notification_content_backup_error_backend))
                    mNotificationBuilder!!.setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_content_backup_error_backend)))
                }
            } else {
                val message = getString(R.string.notification_content_backup_error_internal, exception.message)
                mNotificationBuilder!!.setContentText(message)
                mNotificationBuilder!!.setStyle(NotificationCompat.BigTextStyle().bigText(message))
            }
            // use the notification service instead of the foreground service
            // because when the intent service has finished the notification
            // is removed even if the stopForeground is set to false
            mReporter!!.showNotification(NotificationContract.NOTIFICATION_ID_BACKUP_ERROR, mNotificationBuilder!!)
        }
    }

    /**
     * Build the error notification body for a recoverable failure that offers a
     * retry action, copying the service arguments into the retry pending intent.
     *
     * Nothing reaches this today. The builder it fills is created only when the
     * service was asked to run in the foreground, and the only caller that asks
     * is the retry action itself, so the first notification carrying that action
     * is never built. The auto backup used to reach it and no longer uses this
     * service. Left in place rather than deleted with the rest, because the
     * screen that starts a backup is the thing that should decide whether a
     * failure it is watching also deserves a notification.
     */
    private fun setupRetryNotification(baseIntent: Intent, action: Int, contentTextRes: Int) {
        // create a copy of the arguments used in this service
        val intentArguments = Bundle()
        intentArguments.putInt(ACTION, action)
        intentArguments.putString(BACKEND_ID, baseIntent.getStringExtra(BACKEND_ID))
        intentArguments.putBoolean(AUTO_BACKUP, baseIntent.getBooleanExtra(AUTO_BACKUP, DEFAULT_AUTO_BACKUP))
        intentArguments.putBoolean(ONLY_ON_WIFI, baseIntent.getBooleanExtra(ONLY_ON_WIFI, DEFAULT_ONLY_ON_WIFI))
        intentArguments.putBoolean(RUN_FOREGROUND, baseIntent.getBooleanExtra(RUN_FOREGROUND, DEFAULT_RUN_FOREGROUND))
        intentArguments.putString(PASSWORD, baseIntent.getStringExtra(PASSWORD))
        intentArguments.putParcelable(PARENT_FOLDER, baseIntent.getParcelableExtra<Parcelable>(PARENT_FOLDER))
        intentArguments.putParcelable(BACKUP_FILE, baseIntent.getParcelableExtra<Parcelable>(BACKUP_FILE))
        // prepare the pending intent for the notification receiver
        val retryIntent = Intent(this, NotificationBroadcastReceiver::class.java)
        retryIntent.setAction(NotificationBroadcastReceiver.ACTION_RETRY_BACKUP_CREATION)
        retryIntent.putExtra(NotificationBroadcastReceiver.ACTION_INTENT_ARGUMENTS, intentArguments)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, retryIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // finish to setup the notification body
        val contentText = getString(contentTextRes)
        mNotificationBuilder!!.setContentText(contentText)
        mNotificationBuilder!!.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
        mNotificationBuilder!!.addAction(R.drawable.ic_refresh_black_24dp, getString(R.string.notification_action_retry), pendingIntent)
    }

}
