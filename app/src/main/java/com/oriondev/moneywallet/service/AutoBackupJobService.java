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

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.api.BackendException;
import com.oriondev.moneywallet.api.BackendServiceFactory;
import com.oriondev.moneywallet.api.IBackendServiceAPI;
import com.oriondev.moneywallet.api.saf.SAFBackendService;
import com.oriondev.moneywallet.broadcast.AutoBackupBroadcastReceiver;
import com.oriondev.moneywallet.model.DataFormat;
import com.oriondev.moneywallet.model.IFile;
import com.oriondev.moneywallet.storage.database.data.AbstractDataExporter;
import com.oriondev.moneywallet.storage.preference.BackendManager;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.notification.NotificationContract;
import com.oriondev.moneywallet.utils.ProgressInputStream;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the auto backup from the job scheduler.
 *
 * The backup used to run in {@link BackupHandlerIntentService}, started inline. The alarm ran it
 * straight from onReceive; everyone else reached it through the scheduling method, which ran the
 * sweep itself whenever the occurrence was already past. That method was called from
 * Application.onCreate, the boot receiver, the settings dialog, the backup service at two points,
 * and the sweep's own tail call, which is the one that closed the loop. When such a caller runs
 * with the app in the background, which is the case this feature is for
 * even though it is not the only one, a recent Android refuses the foreground service start,
 * and refuses a plain service start too. Tested on API 36; the background restriction on a
 * plain start arrived at 26 and the one on a foreground start at 31, so the oldest devices this
 * app supports were never affected. The scheduler is the platform's own answer for deferrable
 * work, so the
 * sweep runs here, on a thread this class starts, and calls the backup directly.
 */
public class AutoBackupJobService extends JobService {

    private static final String TAG = "AutoBackup";

    private static final AtomicBoolean sSweepInProgress = new AtomicBoolean(false);

    @Override
    public boolean onStartJob(final JobParameters params) {
        final Context context = getApplicationContext();
        if (!sSweepInProgress.compareAndSet(false, true)) {
            // A sweep started by an earlier run of this job is still going. onStopJob asks
            // for the job back, and nothing interrupts the thread, so the two would otherwise
            // overlap: both read the stored timestamp before either advances it, and the same
            // occurrence is exported and uploaded twice.
            Log.d(TAG, "Auto backup sweep already in progress, skipping this run");
            return false;
        }
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    runSweep(context);
                } catch (Throwable throwable) {
                    // This thread belongs to the process, not to the job. An escape here would
                    // take the whole app down, which is the failure this class exists to remove.
                    Log.e(TAG, "Auto backup sweep failed", throwable);
                } finally {
                    sSweepInProgress.set(false);
                    jobFinished(params, false);
                }
            }

        }, "auto-backup-sweep").start();
        // the sweep continues on its own thread after this returns
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // Ask for this job back. The sweep queues the next occurrence itself, and being
        // stopped is the case where it may not get that far. The other callers would
        // eventually queue one, but they all need the app opened or the device rebooted,
        // which is not something a background backup should wait on.
        return true;
    }

    /**
     * Back up every enabled backend whose next occurrence has passed, then queue the next run.
     * Runs on the calling thread.
     */
    public static void runSweep(Context context) {
        Log.d(TAG, "Auto backup sweep running");
        try {
            Set<String> backendIdSet = BackendManager.getAutoBackupEnabledServices();
            if (backendIdSet != null && !backendIdSet.isEmpty()) {
                // Copy before iterating. SharedPreferences.getStringSet hands back its own
                // live instance, and a backend that fails unrecoverably is disabled, which
                // removes an element from that very set.
                for (String backendId : new ArrayList<>(backendIdSet)) {
                    runBackendIfDue(context, backendId);
                }
            }
        } finally {
            // Queue the next occurrence whatever the loop did. The other callers all need the
            // app opened or the device rebooted, so a throw from the loop skipping this line is
            // how the backup stops running unattended. The job that ran this sweep is spent.
            //
            // Not a guarantee, and the gap is the same one runBackendIfDue guards: scheduling
            // reads the stored timestamp and the interval itself, so a preference of the wrong
            // type takes this line down too, and the catch below only keeps it from taking the
            // thread with it. Nothing is queued in that case.
            try {
                AutoBackupBroadcastReceiver.scheduleAutoBackupTask(context);
            } catch (Exception e) {
                Log.e(TAG, "Could not queue the next auto backup", e);
            }
        }
    }

    private static void runBackendIfDue(Context context, String backendId) {
        long lastTimestamp;
        long interval;
        long nextOccurrence;
        try {
            lastTimestamp = BackendManager.getAutoBackupLastTime(backendId);
            interval = AutoBackupBroadcastReceiver.intervalMillis(backendId);
            nextOccurrence = lastTimestamp + interval;
        } catch (Exception e) {
            // Reading a preference of the wrong type throws. Keep it to this backend rather
            // than losing the rest of the sweep with it (issue #177).
            Log.w(TAG, "Auto backup settings unreadable for '" + backendId + "': " + e.getMessage());
            return;
        }
        if (nextOccurrence > System.currentTimeMillis()) {
            return;
        }
        try {
            if (!BackendManager.isAutoBackupWhenDataIsChangedOnly(backendId) || PreferenceManager.getLastTimeDataIsChanged() > lastTimestamp) {
                runBackend(context, backendId);
            }
        } catch (Exception e) {
            // Never let a single misconfigured backend take the sweep down (issue #177).
            Log.w(TAG, "Auto backup failed for '" + backendId + "': " + e.getMessage());
            notifyFailure(context, e);
        } finally {
            if (BackendManager.isAutoBackupEnabled(backendId)) {
                // Consume the occurrence whatever the outcome, and skip straight to the most
                // recent one. Leaving the timestamp behind queues the job again at the
                // minimum latency, so a failing backend would repeat every minute. Advancing
                // by a single interval instead would make a fortnight away one run per missed
                // day. A failure is reported in its own notification.
                //
                // Not written back for a backend that the failure above just disabled: that
                // clears its stored timestamp, and putting one back makes the backup fire
                // immediately if the user ever switches the backend on again.
                BackendManager.setAutoBackupLastTime(backendId, latestPassedOccurrence(nextOccurrence, interval));
            }
        }
    }

    /**
     * The most recent occurrence at or before now, so one missed run is performed rather than
     * one for every occurrence a device slept through.
     */
    private static long latestPassedOccurrence(long occurrence, long interval) {
        long now = System.currentTimeMillis();
        while (occurrence + interval <= now) {
            occurrence += interval;
        }
        return occurrence;
    }

    private static void runBackend(Context context, String backendId) throws Exception {
        String encodedFolder = BackendManager.getAutoBackupFolder(backendId);
        IFile folder = BackendServiceFactory.getFile(backendId, encodedFolder);
        if (SAFBackendService.isOutsideCurrentFolder(context, folder)) {
            // a Local folder disconnected or replaced since this was chosen, so the write
            // would be refused
            folder = null;
        }
        if (folder == null) {
            // Legacy or undecodable backup location (issue #177): skip this backend instead of
            // failing the sweep. The backend stays configured; the folder can be chosen again.
            // The occurrence is consumed either way, so tell the user rather than leaving a
            // backup that never runs to be discovered when it is needed.
            //
            // Nothing stored is a different failure from something stored that will not decode.
            // The settings dialog refuses to save the first, so what reaches here was written by
            // a build before it. Telling that user the location is no longer available sends
            // them looking for a folder that is not there to find.
            boolean nothingStored = encodedFolder == null;
            Log.w(TAG, "Skipping auto backup for '" + backendId + "': "
                    + (nothingStored ? "no backup folder is set" : "backup location not available"));
            notifyMessage(context, context.getString(nothingStored
                    ? R.string.notification_content_backup_error_location_unset
                    : R.string.notification_content_backup_error_location));
            return;
        }
        if (BackendManager.isAutoBackupOnWiFiOnly(backendId) && !isConnectedToWiFi(context)) {
            // Same reasoning as the location above: the occurrence is spent, so say so.
            Log.w(TAG, "Skipping auto backup for '" + backendId + "': not on a WiFi network");
            notifyMessage(context, context.getString(R.string.notification_content_backup_error_wifi_network));
            return;
        }
        IBackendServiceAPI backendServiceAPI;
        try {
            // Built inside the block, so a service API that throws a BackendException the app
            // cannot recover from while it is built turns automatic backup off like any other
            // such failure. Outside it, a backend that is not connected any more, a released
            // grant or a cleared server address stayed switched on and failed the same way the
            // next time a backup ran, while the failure notification said automatic backup had
            // been disabled.
            // BackupHandlerIntentService, which the backup screen uses, builds its backend
            // inside the block that disables, so that failure already turned the service off
            // there.
            backendServiceAPI = BackendServiceFactory.getServiceAPIById(context, backendId);
            BackupOperation.createAndUpload(context, backendServiceAPI, folder, BackendManager.getAutoBackupPassword(backendId), null);
        } catch (BackendException e) {
            if (!e.isRecoverable()) {
                BackendManager.disableAutoBackupAfterFailure(backendId);
            }
            throw e;
        }
        // The listener is not null because the upload wraps the file in a ProgressInputStream,
        // which calls it without a check. A failure does not disable auto backup, since the
        // backup itself is already uploaded.
        try {
            if (BackendManager.isAutoBackupExportCsv(backendId)) {
                exportCsvAndUpload(context, backendServiceAPI, folder);
            }
        } catch (Exception e) {
            Log.w(TAG, "Scheduled CSV export failed for '" + backendId + "': " + e.getMessage());
            notifyMessage(context, context.getString(R.string.notification_content_csv_export_error, e.getMessage()),
                    R.string.notification_title_csv_export_failed, NotificationContract.NOTIFICATION_ID_CSV_EXPORT_ERROR);
        }
    }

    private static void exportCsvAndUpload(Context context, IBackendServiceAPI backendServiceAPI, IFile remoteFolder) throws Exception {
        File localFolder = new File(context.getCacheDir(), "csv_export/" + UUID.randomUUID());
        try {
            File csvFile;
            synchronized (BackupOperation.LOCK) {
                FileUtils.forceMkdir(localFolder);
                String[] optionalColumns = new String[]{AbstractDataExporter.COLUMN_EVENT,
                        AbstractDataExporter.COLUMN_PEOPLE, AbstractDataExporter.COLUMN_PLACE,
                        AbstractDataExporter.COLUMN_NOTE};
                csvFile = ImportExportIntentService.exportTransactions(context, DataFormat.CSV, localFolder,
                        null, null, null, true, optionalColumns).getOutputFile();
            }
            backendServiceAPI.uploadFile(remoteFolder, csvFile, new ProgressInputStream.UploadProgressListener() {

                @Override
                public void onUploadProgressUpdate(int percentage) {
                    // not used
                }

            });
        } finally {
            FileUtils.deleteQuietly(localFolder);
        }
    }

    private static boolean isConnectedToWiFi(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        NetworkInfo networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return networkInfo != null && networkInfo.isConnected();
    }

    /**
     * Report a failure in the wording the auto backup's own notification carried before it
     * moved off the service, so an expired token or an unreachable server is not described to
     * the user as a bug in the app. The user started backup does not share it: that reports
     * through a dialog carrying the raw exception, and always did.
     */
    private static void notifyFailure(Context context, Exception exception) {
        if (exception instanceof BackendException) {
            boolean recoverable = ((BackendException) exception).isRecoverable();
            notifyMessage(context, context.getString(recoverable
                    ? R.string.notification_content_backup_error_backend_recoverable
                    : R.string.notification_content_backup_error_backend));
            return;
        }
        notifyMessage(context, context.getString(R.string.notification_content_backup_error_internal, exception.getMessage()));
    }

    private static void notifyMessage(Context context, String message) {
        notifyMessage(context, message, R.string.notification_title_backup_creation_failed,
                NotificationContract.NOTIFICATION_ID_BACKUP_ERROR);
    }

    private static void notifyMessage(Context context, String message, @StringRes int title, int notificationId) {
        TaskReporter reporter = new TaskReporter(context);
        NotificationCompat.Builder builder = reporter.newNotification(NotificationContract.NOTIFICATION_CHANNEL_ERROR)
                .setContentTitle(context.getString(title))
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message));
        reporter.showNotification(notificationId, builder);
    }
}
