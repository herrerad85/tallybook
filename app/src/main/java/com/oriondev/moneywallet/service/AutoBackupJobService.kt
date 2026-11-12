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

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.util.Log

import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.api.BackendException
import com.oriondev.moneywallet.api.BackendServiceFactory
import com.oriondev.moneywallet.api.IBackendServiceAPI
import com.oriondev.moneywallet.api.saf.SAFBackendService
import com.oriondev.moneywallet.broadcast.AutoBackupBroadcastReceiver
import com.oriondev.moneywallet.model.DataFormat
import com.oriondev.moneywallet.model.IFile
import com.oriondev.moneywallet.storage.database.data.AbstractDataExporter
import com.oriondev.moneywallet.storage.preference.BackendManager
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.notification.NotificationContract
import com.oriondev.moneywallet.utils.ProgressInputStream

import org.apache.commons.io.FileUtils

import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs the auto backup from the job scheduler.
 *
 * The backup used to run in [BackupHandlerIntentService], started inline. The alarm ran it
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
class AutoBackupJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        val context = applicationContext
        if (!sSweepInProgress.compareAndSet(false, true)) {
            // A sweep started by an earlier run of this job is still going. onStopJob asks
            // for the job back, and nothing interrupts the thread, so the two would otherwise
            // overlap: both read the stored timestamp before either advances it, and the same
            // occurrence is exported and uploaded twice.
            Log.d(TAG, "Auto backup sweep already in progress, skipping this run")
            return false
        }
        Thread(object : Runnable {

            override fun run() {
                try {
                    runSweep(context)
                } catch (throwable: Throwable) {
                    // This thread belongs to the process, not to the job. An escape here would
                    // take the whole app down, which is the failure this class exists to remove.
                    Log.e(TAG, "Auto backup sweep failed", throwable)
                } finally {
                    sSweepInProgress.set(false)
                    jobFinished(params, false)
                }
            }

        }, "auto-backup-sweep").start()
        // the sweep continues on its own thread after this returns
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // Ask for this job back. The sweep queues the next occurrence itself, and being
        // stopped is the case where it may not get that far. The other callers would
        // eventually queue one, but they all need the app opened or the device rebooted,
        // which is not something a background backup should wait on.
        return true
    }

    companion object {

        private const val TAG = "AutoBackup"

        private val sSweepInProgress = AtomicBoolean(false)

        /**
         * Back up every enabled backend whose next occurrence has passed, then queue the next run.
         * Runs on the calling thread.
         */
        @JvmStatic
        fun runSweep(context: Context) {
            Log.d(TAG, "Auto backup sweep running")
            try {
                val backendIdSet: Set<String>? = BackendManager.getAutoBackupEnabledServices()
                if (backendIdSet != null && !backendIdSet.isEmpty()) {
                    // Copy before iterating. SharedPreferences.getStringSet hands back its own
                    // live instance, and a backend that fails unrecoverably is disabled, which
                    // removes an element from that very set.
                    for (backendId in ArrayList(backendIdSet)) {
                        runBackendIfDue(context, backendId)
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
                    AutoBackupBroadcastReceiver.scheduleAutoBackupTask(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not queue the next auto backup", e)
                }
            }
        }

        private fun runBackendIfDue(context: Context, backendId: String?) {
            val lastTimestamp: Long
            val interval: Long
            val nextOccurrence: Long
            try {
                lastTimestamp = BackendManager.getAutoBackupLastTime(backendId)
                interval = AutoBackupBroadcastReceiver.intervalMillis(backendId)
                nextOccurrence = lastTimestamp + interval
            } catch (e: Exception) {
                // Reading a preference of the wrong type throws. Keep it to this backend rather
                // than losing the rest of the sweep with it (issue #177).
                Log.w(TAG, "Auto backup settings unreadable for '" + backendId + "': " + e.message)
                return
            }
            if (nextOccurrence > System.currentTimeMillis()) {
                return
            }
            try {
                if (!BackendManager.isAutoBackupWhenDataIsChangedOnly(backendId) || PreferenceManager.getLastTimeDataIsChanged() > lastTimestamp) {
                    runBackend(context, backendId)
                }
            } catch (e: Exception) {
                // Never let a single misconfigured backend take the sweep down (issue #177).
                Log.w(TAG, "Auto backup failed for '" + backendId + "': " + e.message)
                notifyFailure(context, e)
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
                    BackendManager.setAutoBackupLastTime(backendId, latestPassedOccurrence(nextOccurrence, interval))
                }
            }
        }

        /**
         * The most recent occurrence at or before now, so one missed run is performed rather than
         * one for every occurrence a device slept through.
         */
        private fun latestPassedOccurrence(occurrence: Long, interval: Long): Long {
            var latest = occurrence
            val now = System.currentTimeMillis()
            while (latest + interval <= now) {
                latest += interval
            }
            return latest
        }

        @Throws(Exception::class)
        private fun runBackend(context: Context, backendId: String?) {
            val encodedFolder: String? = BackendManager.getAutoBackupFolder(backendId)
            var folder: IFile? = BackendServiceFactory.getFile(backendId, encodedFolder)
            if (SAFBackendService.isOutsideCurrentFolder(context, folder)) {
                // a Local folder disconnected or replaced since this was chosen, so the write
                // would be refused
                folder = null
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
                val nothingStored = encodedFolder == null
                Log.w(TAG, "Skipping auto backup for '" + backendId + "': "
                        + (if (nothingStored) "no backup folder is set" else "backup location not available"))
                notifyMessage(context, context.getString(if (nothingStored)
                        R.string.notification_content_backup_error_location_unset
                        else R.string.notification_content_backup_error_location))
                return
            }
            if (BackendManager.isAutoBackupOnWiFiOnly(backendId) && !isConnectedToWiFi(context)) {
                // Same reasoning as the location above: the occurrence is spent, so say so.
                Log.w(TAG, "Skipping auto backup for '" + backendId + "': not on a WiFi network")
                notifyMessage(context, context.getString(R.string.notification_content_backup_error_wifi_network))
                return
            }
            val backendServiceAPI: IBackendServiceAPI
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
                backendServiceAPI = BackendServiceFactory.getServiceAPIById(context, backendId)
                BackupOperation.createAndUpload(context, backendServiceAPI, folder, BackendManager.getAutoBackupPassword(backendId), null)
            } catch (e: BackendException) {
                if (!e.isRecoverable()) {
                    BackendManager.disableAutoBackupAfterFailure(backendId)
                }
                throw e
            }
            // The listener is not null because the upload wraps the file in a ProgressInputStream,
            // which calls it without a check. A failure does not disable auto backup, since the
            // backup itself is already uploaded.
            try {
                if (BackendManager.isAutoBackupExportCsv(backendId)) {
                    exportCsvAndUpload(context, backendServiceAPI, folder)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Scheduled CSV export failed for '" + backendId + "': " + e.message)
                notifyMessage(context, context.getString(R.string.notification_content_csv_export_error, e.message),
                        R.string.notification_title_csv_export_failed, NotificationContract.NOTIFICATION_ID_CSV_EXPORT_ERROR)
            }
        }

        @Throws(Exception::class)
        private fun exportCsvAndUpload(context: Context, backendServiceAPI: IBackendServiceAPI, remoteFolder: IFile?) {
            val localFolder = File(context.cacheDir, "csv_export/" + UUID.randomUUID())
            try {
                val csvFile = synchronized(BackupOperation.LOCK) {
                    FileUtils.forceMkdir(localFolder)
                    val optionalColumns = arrayOf(AbstractDataExporter.COLUMN_EVENT,
                            AbstractDataExporter.COLUMN_PEOPLE, AbstractDataExporter.COLUMN_PLACE,
                            AbstractDataExporter.COLUMN_NOTE)
                    ImportExportIntentService.exportTransactions(context, DataFormat.CSV, localFolder,
                            null, null, null, true, optionalColumns).getOutputFile()
                }
                backendServiceAPI.uploadFile(remoteFolder, csvFile, object : ProgressInputStream.UploadProgressListener {

                    override fun onUploadProgressUpdate(percentage: Int) {
                        // not used
                    }

                })
            } finally {
                FileUtils.deleteQuietly(localFolder)
            }
        }

        private fun isConnectedToWiFi(context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            if (connectivityManager == null) {
                return false
            }
            // Not getNetworkInfo, which reports WiFi as not connected while this app's network
            // access is blocked, and a job can start before that block lifts. The default network
            // callback still arrives while blocked, and it names the network this app's traffic
            // uses, so WiFi that is connected but not in use does not count.
            val defaultNetwork = AtomicReference<Network>()
            val answered = CountDownLatch(1)
            val callback: ConnectivityManager.NetworkCallback = object : ConnectivityManager.NetworkCallback() {

                override fun onAvailable(network: Network) {
                    defaultNetwork.set(network)
                    answered.countDown()
                }

            }
            connectivityManager.registerDefaultNetworkCallback(callback)
            try {
                answered.await(5, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                connectivityManager.unregisterNetworkCallback(callback)
            }
            val network: Network? = defaultNetwork.get()
            if (network == null) {
                // with no default network nothing arrives, which is not WiFi
                return false
            }
            val capabilities: NetworkCapabilities? = connectivityManager.getNetworkCapabilities(network)
            if (capabilities == null) {
                return false
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return true
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                // On Android 9 a VPN that does not declare the networks under it reports only the
                // VPN transport, even over WiFi, so ask the old way. A VPN that declares several
                // networks reports the transports of all of them, so WiFi with cellular asks too.
                val networkInfo: NetworkInfo? = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
                return networkInfo != null && networkInfo.isConnected()
            }
            return false
        }

        /**
         * Report a failure in the wording the auto backup's own notification carried before it
         * moved off the service, so an expired token or an unreachable server is not described to
         * the user as a bug in the app. The user started backup does not share it: that reports
         * through a dialog carrying the raw exception, and always did.
         */
        private fun notifyFailure(context: Context, exception: Exception) {
            if (exception is BackendException) {
                val recoverable = exception.isRecoverable()
                notifyMessage(context, context.getString(if (recoverable)
                        R.string.notification_content_backup_error_backend_recoverable
                        else R.string.notification_content_backup_error_backend))
                return
            }
            notifyMessage(context, context.getString(R.string.notification_content_backup_error_internal, exception.message))
        }

        private fun notifyMessage(context: Context, message: String) {
            notifyMessage(context, message, R.string.notification_title_backup_creation_failed,
                    NotificationContract.NOTIFICATION_ID_BACKUP_ERROR)
        }

        private fun notifyMessage(context: Context, message: String, @StringRes title: Int, notificationId: Int) {
            val reporter = TaskReporter(context)
            val builder = reporter.newNotification(NotificationContract.NOTIFICATION_CHANNEL_ERROR)
                    .setContentTitle(context.getString(title))
                    .setCategory(NotificationCompat.CATEGORY_ERROR)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            reporter.showNotification(notificationId, builder)
        }
    }
}
