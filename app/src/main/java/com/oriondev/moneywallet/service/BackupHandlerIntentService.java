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

package com.oriondev.moneywallet.service;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.api.BackendException;
import com.oriondev.moneywallet.api.BackendServiceFactory;
import com.oriondev.moneywallet.api.IBackendServiceAPI;
import com.oriondev.moneywallet.broadcast.AutoBackupBroadcastReceiver;
import com.oriondev.moneywallet.broadcast.LocalAction;
import com.oriondev.moneywallet.broadcast.NotificationBroadcastReceiver;
import com.oriondev.moneywallet.broadcast.RecurrenceBroadcastReceiver;
import com.oriondev.moneywallet.model.IFile;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.ExportException;
import com.oriondev.moneywallet.storage.database.ImportException;
import com.oriondev.moneywallet.storage.database.SQLDatabaseImporter;
import com.oriondev.moneywallet.storage.database.backup.AbstractBackupImporter;
import com.oriondev.moneywallet.storage.database.backup.BackupManager;
import com.oriondev.moneywallet.storage.database.backup.DefaultBackupImporter;
import com.oriondev.moneywallet.storage.database.backup.LegacyBackupImporter;
import com.oriondev.moneywallet.storage.preference.BackendManager;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.notification.NotificationContract;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.ProgressOutputStream;
import com.oriondev.moneywallet.utils.Utils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Created by andrea on 21/11/18.
 */
public class BackupHandlerIntentService extends IntentService {

    public static final String ACTION = "BackupHandlerIntentService::Argument::Action";
    public static final String BACKEND_ID = "BackupHandlerIntentService::Argument::BackendId";
    public static final String AUTO_BACKUP = "BackupHandlerIntentService::Argument::AutoBackup";
    public static final String ONLY_ON_WIFI = "BackupHandlerIntentService::Argument::OnlyOnWifi";
    public static final String RUN_FOREGROUND = "BackupHandlerIntentService::Argument::RunForeground";
    public static final String BACKUP_FILE = "BackupHandlerIntentService::Argument::BackupFile";
    public static final String EXCEPTION = "BackupHandlerIntentService::Argument::Exception";
    public static final String FOLDER_CONTENT = "BackupHandlerIntentService::Argument::FolderContent";
    public static final String PARENT_FOLDER = "BackupHandlerIntentService::Argument::ParentFolder";
    public static final String PASSWORD = "BackupHandlerIntentService::Argument::Password";
    public static final String PROGRESS_STATUS = "BackupHandlerIntentService::Argument::ProgressStatus";
    public static final String PROGRESS_VALUE = "BackupHandlerIntentService::Argument::ProgressValue";
    public static final String CALLER_ID = "BackupHandlerIntentService::Argument::CallerId";

    private static final String BACKUP_CACHE_FOLDER = "backups";
    private static final String TEMP_FOLDER = "temp";

    private static final int ACTION_NONE = 0;
    public static final int ACTION_LIST = 1;
    public static final int ACTION_BACKUP = 2;
    public static final int ACTION_RESTORE = 3;

    public static final int STATUS_BACKUP_CREATION = 1;
    public static final int STATUS_BACKUP_UPLOADING = 2;
    public static final int STATUS_BACKUP_DOWNLOADING = 3;
    public static final int STATUS_BACKUP_RESTORING = 4;

    private static final boolean DEFAULT_AUTO_BACKUP = false;
    private static final boolean DEFAULT_ONLY_ON_WIFI = false;
    private static final boolean DEFAULT_RUN_FOREGROUND = false;

    private IBackendServiceAPI mBackendServiceAPI;

    private String mCallerId;
    private TaskReporter mReporter;
    private NotificationCompat.Builder mNotificationBuilder;

    public static void startInForeground(Context context, Intent intent) {
        intent.putExtra(BackupHandlerIntentService.RUN_FOREGROUND, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public BackupHandlerIntentService() {
        super("BackupHandlerIntentService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent != null) {
            // unpack the base intent
            int action = intent.getIntExtra(ACTION, ACTION_NONE);
            String backendId = intent.getStringExtra(BACKEND_ID);
            boolean runForeground = intent.getBooleanExtra(RUN_FOREGROUND, DEFAULT_RUN_FOREGROUND);
            mCallerId = intent.getStringExtra(CALLER_ID);
            // execute the action in a safe code-block: if an exception is thrown, it
            // is handled by both the local broadcast manager as an error and reported
            // in the notification if it is required
            Exception exception = null;
            try {
                // initialize the task reporter
                mReporter = new TaskReporter(this);
                // if the notification is required, start the service in foreground
                if (runForeground && (action == ACTION_BACKUP || action == ACTION_RESTORE)) {
                    mNotificationBuilder = mReporter.newNotification(NotificationContract.NOTIFICATION_CHANNEL_BACKUP)
                            .setProgress(0, 0, true)
                            .setCategory(NotificationCompat.CATEGORY_PROGRESS);
                }
                // send a local message to notify the receivers that the task is started
                notifyTaskStarted(action);
                // check if backend id is available and initialize it
                mBackendServiceAPI = BackendServiceFactory.getServiceAPIById(this, backendId);
                switch (action) {
                    case ACTION_LIST:
                        onActionList(intent);
                        break;
                    case ACTION_BACKUP:
                        onActionBackup(intent);
                        break;
                    case ACTION_RESTORE:
                        onActionRestore(intent);
                        break;
                }
            } catch (Exception e) {
                exception = e;
            }
            // handle the exception if the task failed
            if (exception != null) {
                if (exception instanceof BackendException) {
                    if (!((BackendException) exception).isRecoverable()) {
                        // disable auto-backup for this backend id
                        BackendManager.disableAutoBackupAfterFailure(backendId);
                        AutoBackupBroadcastReceiver.scheduleAutoBackupTask(this);
                    }
                }
                notifyTaskFailure(intent, exception);
            }
            // clear the environment
            if (mNotificationBuilder != null) {
                stopForeground(true);
            }
        }
    }

    private void onActionList(@NonNull Intent intent) throws BackendException {
        IFile remoteFolder = intent.getParcelableExtra(PARENT_FOLDER);
        List<IFile> fileList = mBackendServiceAPI.getFolderContent(remoteFolder);
        notifyListTaskFinished(fileList);
    }

    private void onActionBackup(@NonNull Intent intent) throws ExportException, BackendException, IOException {
        IFile remoteFolder = intent.getParcelableExtra(PARENT_FOLDER);
        String password = intent.getStringExtra(PASSWORD);
        IFile uploaded = BackupOperation.createAndUpload(this, mBackendServiceAPI, remoteFolder, password, new BackupOperation.ProgressListener() {

            @Override
            public void onProgress(int status, int progress) {
                notifyTaskProgress(ACTION_BACKUP, status, progress);
            }

        });
        notifyUploadTaskFinished(uploaded);
    }

    private void onActionRestore(@NonNull Intent intent) throws ImportException, BackendException, IOException {
        IFile remoteFile = intent.getParcelableExtra(BACKUP_FILE);
        if (remoteFile != null) {
            File folder = getExternalFilesDir(null);
            File cache = new File(folder, BACKUP_CACHE_FOLDER);
            File revision = new File(cache, UUID.randomUUID().toString());
            try {
                FileUtils.forceMkdir(revision);
                notifyTaskProgress(ACTION_RESTORE, STATUS_BACKUP_DOWNLOADING, 0);
                File backup = mBackendServiceAPI.downloadFile(revision, remoteFile, new ProgressOutputStream.DownloadProgressListener() {

                    @Override
                    public void onDownloadProgressUpdate(int percentage) {
                        int realProgress = (percentage * 70 / 100);
                        notifyTaskProgress(ACTION_RESTORE, STATUS_BACKUP_DOWNLOADING, realProgress);
                    }

                });
                notifyTaskProgress(ACTION_RESTORE, STATUS_BACKUP_RESTORING, 75);
                String password = intent.getStringExtra(PASSWORD);
                // The auto backup no longer runs on this service's serial queue, so a sweep
                // can now be reading the database and the attachment folder that the import
                // is about to replace and empty.
                synchronized (BackupOperation.LOCK) {
                    restoreLocalBackupFile(backup, password);
                }
                notifyTaskProgress(ACTION_RESTORE, STATUS_BACKUP_RESTORING, 100);
                PreferenceManager.setLastTimeDataIsChanged(0L);
                RecurrenceBroadcastReceiver.scheduleRecurrenceTask(this);
                AutoBackupBroadcastReceiver.scheduleAutoBackupTask(this);
                notifyTaskFinished(ACTION_RESTORE);
            } finally {
                FileUtils.deleteQuietly(revision);
            }
        } else {
            throw new RuntimeException("Backup file to restore not specified");
        }
    }

    private void restoreLocalBackupFile(@NonNull File backup, @Nullable String password) throws ImportException, IOException {
        AbstractBackupImporter importer;
        String fileName = backup.getName();
        if (fileName.endsWith(BackupManager.BACKUP_EXTENSION_LEGACY)) {
            importer = new LegacyBackupImporter(this, backup);
        } else {
            importer = new DefaultBackupImporter(this, backup, password);
        }
        File temporaryFolder = new File(getExternalFilesDir(null), TEMP_FOLDER);
        FileUtils.forceMkdir(temporaryFolder);
        try {
            File databaseFile = getDatabasePath(SQLDatabaseImporter.DATABASE_NAME);
            importer.importDatabase(temporaryFolder, databaseFile.getParentFile());
            // the count on a recurrence notification still showing belongs to the ledger just replaced
            NotificationManagerCompat.from(this).cancel(NotificationContract.NOTIFICATION_ID_RECURRENCE);
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
            DataContentProvider.notifyDatabaseIsChanged(this);
            CurrencyManager.invalidateCache(this);
            importer.importAttachments(BackupOperation.getAttachmentFolder(this));
        } finally {
            FileUtils.cleanDirectory(temporaryFolder);
        }
    }

    private String getNotificationContentTitle(int action, boolean error) {
        if (action == ACTION_BACKUP) {
            return getString(error ? R.string.notification_title_backup_creation_failed : R.string.notification_title_backup_creation);
        } else if (action == ACTION_RESTORE) {
            return getString(error ? R.string.notification_title_backup_restoring_failed : R.string.notification_title_backup_restoring);
        }
        return null;
    }

    private String getNotificationContentText(int status) {
        switch (status) {
            case STATUS_BACKUP_CREATION:
                return getString(R.string.notification_content_backup_file_creation);
            case STATUS_BACKUP_UPLOADING:
                return getString(R.string.notification_content_backup_file_uploading);
            case STATUS_BACKUP_DOWNLOADING:
                return getString(R.string.notification_content_backup_file_downloading);
            case STATUS_BACKUP_RESTORING:
                return getString(R.string.notification_content_backup_file_restoring);
        }
        return null;
    }

    private void notifyTaskStarted(int action) {
        // notify the local broadcast manager
        Bundle extras = new Bundle();
        extras.putInt(ACTION, action);
        extras.putString(CALLER_ID, mCallerId);
        mReporter.broadcast(LocalAction.ACTION_BACKUP_SERVICE_STARTED, extras);
        // update the notification if required
        if (mNotificationBuilder != null) {
            mNotificationBuilder.setContentTitle(getNotificationContentTitle(action, false));
            ForegroundServices.startDataSync(this, NotificationContract.NOTIFICATION_ID_BACKUP_PROGRESS, mNotificationBuilder.build());
        }
    }

    private void notifyTaskProgress(int action, int status, int progress) {
        // notify the local broadcast manager
        Bundle extras = new Bundle();
        extras.putInt(ACTION, action);
        extras.putInt(PROGRESS_STATUS, status);
        extras.putInt(PROGRESS_VALUE, progress);
        extras.putString(CALLER_ID, mCallerId);
        mReporter.broadcast(LocalAction.ACTION_BACKUP_SERVICE_RUNNING, extras);
        // update the notification if required
        if (mNotificationBuilder != null) {
            mNotificationBuilder.setContentText(getNotificationContentText(status));
            mNotificationBuilder.setProgress(100, progress, false);
            ForegroundServices.startDataSync(this, NotificationContract.NOTIFICATION_ID_BACKUP_PROGRESS, mNotificationBuilder.build());
        }
    }

    private void notifyTaskFinished(int action) {
        // notify only the local broadcast manager: it is not required to update
        // the notification because it is removed when everything is gone right
        Bundle extras = new Bundle();
        extras.putInt(ACTION, action);
        extras.putString(CALLER_ID, mCallerId);
        mReporter.broadcast(LocalAction.ACTION_BACKUP_SERVICE_FINISHED, extras);
    }

    private void notifyListTaskFinished(List<IFile> files) {
        // notify only the local broadcast manager: it is not required to update
        // the notification because it is removed when everything is gone right
        Bundle extras = new Bundle();
        extras.putInt(ACTION, ACTION_LIST);
        extras.putParcelableArrayList(FOLDER_CONTENT, Utils.wrapAsArrayList(files));
        extras.putString(CALLER_ID, mCallerId);
        mReporter.broadcast(LocalAction.ACTION_BACKUP_SERVICE_FINISHED, extras);
    }

    private void notifyUploadTaskFinished(IFile file) {
        // notify only the local broadcast manager: it is not required to update
        // the notification because it is removed when everything is gone right
        Bundle extras = new Bundle();
        extras.putInt(ACTION, ACTION_BACKUP);
        extras.putParcelable(BACKUP_FILE, file);
        extras.putString(CALLER_ID, mCallerId);
        mReporter.broadcast(LocalAction.ACTION_BACKUP_SERVICE_FINISHED, extras);
    }

    private void notifyTaskFailure(Intent baseIntent, Exception exception) {
        // notify the local broadcast manager
        int action = baseIntent.getIntExtra(ACTION, ACTION_NONE);
        Bundle extras = new Bundle();
        extras.putInt(ACTION, action);
        extras.putSerializable(EXCEPTION, exception);
        extras.putString(CALLER_ID, mCallerId);
        mReporter.broadcast(LocalAction.ACTION_BACKUP_SERVICE_FAILED, extras);
        // update the notification if required
        if (mNotificationBuilder != null) {
            mNotificationBuilder = mReporter.newNotification(NotificationContract.NOTIFICATION_CHANNEL_ERROR)
                    .setContentTitle(getNotificationContentTitle(action, true))
                    .setCategory(NotificationCompat.CATEGORY_ERROR);
            if (exception instanceof BackendException) {
                if (((BackendException) exception).isRecoverable()) {
                    setupRetryNotification(baseIntent, action, R.string.notification_content_backup_error_backend_recoverable);
                } else {
                    mNotificationBuilder.setContentText(getString(R.string.notification_content_backup_error_backend));
                    mNotificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_content_backup_error_backend)));
                }
            } else {
                String message = getString(R.string.notification_content_backup_error_internal, exception.getMessage());
                mNotificationBuilder.setContentText(message);
                mNotificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(message));
            }
            // use the notification service instead of the foreground service
            // because when the intent service has finished the notification
            // is removed even if the stopForeground is set to false
            mReporter.showNotification(NotificationContract.NOTIFICATION_ID_BACKUP_ERROR, mNotificationBuilder);
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
    private void setupRetryNotification(Intent baseIntent, int action, int contentTextRes) {
        // create a copy of the arguments used in this service
        Bundle intentArguments = new Bundle();
        intentArguments.putInt(ACTION, action);
        intentArguments.putString(BACKEND_ID, baseIntent.getStringExtra(BACKEND_ID));
        intentArguments.putBoolean(AUTO_BACKUP, baseIntent.getBooleanExtra(AUTO_BACKUP, DEFAULT_AUTO_BACKUP));
        intentArguments.putBoolean(ONLY_ON_WIFI, baseIntent.getBooleanExtra(ONLY_ON_WIFI, DEFAULT_ONLY_ON_WIFI));
        intentArguments.putBoolean(RUN_FOREGROUND, baseIntent.getBooleanExtra(RUN_FOREGROUND, DEFAULT_RUN_FOREGROUND));
        intentArguments.putString(PASSWORD, baseIntent.getStringExtra(PASSWORD));
        intentArguments.putParcelable(PARENT_FOLDER, baseIntent.getParcelableExtra(PARENT_FOLDER));
        intentArguments.putParcelable(BACKUP_FILE, baseIntent.getParcelableExtra(BACKUP_FILE));
        // prepare the pending intent for the notification receiver
        Intent retryIntent = new Intent(this, NotificationBroadcastReceiver.class);
        retryIntent.setAction(NotificationBroadcastReceiver.ACTION_RETRY_BACKUP_CREATION);
        retryIntent.putExtra(NotificationBroadcastReceiver.ACTION_INTENT_ARGUMENTS, intentArguments);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, retryIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // finish to setup the notification body
        String contentText = getString(contentTextRes);
        mNotificationBuilder.setContentText(contentText);
        mNotificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(contentText));
        mNotificationBuilder.addAction(R.drawable.ic_refresh_black_24dp, getString(R.string.notification_action_retry), pendingIntent);
    }

}
