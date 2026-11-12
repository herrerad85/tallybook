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

import android.content.Context
import android.text.TextUtils

import com.oriondev.moneywallet.api.BackendException
import com.oriondev.moneywallet.api.IBackendServiceAPI
import com.oriondev.moneywallet.model.IFile
import com.oriondev.moneywallet.storage.database.ExportException
import com.oriondev.moneywallet.storage.database.backup.AbstractBackupExporter
import com.oriondev.moneywallet.storage.database.backup.BackupManager
import com.oriondev.moneywallet.storage.database.backup.DefaultBackupExporter
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.ProgressInputStream

import org.apache.commons.io.FileUtils

import java.io.File
import java.io.IOException
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * The backup half of [BackupHandlerIntentService], lifted out of it so the auto backup
 * can run inside [AutoBackupJobService] instead of starting a service.
 *
 * Tested on an API 36 emulator, neither a foreground nor a plain service can be started while
 * the app is in the background, so the work runs on the caller's own thread. Both callers share
 * this one copy rather than the backup path being written twice.
 */
internal class BackupOperation {

    /**
     * Reports progress to a caller that has somewhere to show it. The auto backup passes null,
     * because a job has no notification of its own to update.
     */
    internal interface ProgressListener {

        fun onProgress(status: Int, progress: Int)
    }

    companion object {

        private const val ATTACHMENT_FOLDER = "attachments"
        private const val BACKUP_CACHE_FOLDER = "backups"
        private const val FILE_DATETIME_PATTERN = "yyyy-MM-dd_HH-mm-ss"
        private const val OUTPUT_FILE = "backup_%s%s"

        /**
         * Held for the length of a backup or a restore, so the two do not run at once. A restore
         * renames its staged file over the live database and empties the attachment folder, and the
         * auto backup no longer shares the serial service queue that used to keep them apart. It
         * guards these two against each other only: attachment writes elsewhere in the app are
         * outside it.
         *
         * Both halves still need it. The export is twenty four separate queries, not one snapshot, so
         * a rename landing in the middle of it would put the old wallets and the new transactions in
         * one file, and a cursor being read across the swap meets a closed database. The attachment
         * folder is worse still, importAttachments empties it before it extracts anything.
         */
        @JvmField
        internal val LOCK = Any()

        /**
         * Export the database and attachments into a local file and upload it to the backend.
         * Runs on the calling thread and returns the uploaded file.
         */
        @JvmStatic
        @Throws(ExportException::class, BackendException::class, IOException::class)
        internal fun createAndUpload(context: Context, backendServiceAPI: IBackendServiceAPI,
                                     remoteFolder: IFile?, password: String?,
                                     listener: ProgressListener?): IFile {
            synchronized(LOCK) {
                return createAndUploadLocked(context, backendServiceAPI, remoteFolder, password, listener)
            }
        }

        @Throws(ExportException::class, BackendException::class, IOException::class)
        private fun createAndUploadLocked(context: Context, backendServiceAPI: IBackendServiceAPI,
                                          remoteFolder: IFile?, password: String?,
                                          listener: ProgressListener?): IFile {
            val cache = File(context.getExternalFilesDir(null), BACKUP_CACHE_FOLDER)
            val revision = File(cache, UUID.randomUUID().toString())
            try {
                FileUtils.forceMkdir(revision)
                notifyProgress(listener, BackupHandlerIntentService.STATUS_BACKUP_CREATION, 0)
                val backup = prepareLocalBackupFile(context, revision, password)
                notifyProgress(listener, BackupHandlerIntentService.STATUS_BACKUP_UPLOADING, 30)
                val uploaded = backendServiceAPI.uploadFile(remoteFolder, backup, object : ProgressInputStream.UploadProgressListener {

                    override fun onUploadProgressUpdate(percentage: Int) {
                        notifyProgress(listener, BackupHandlerIntentService.STATUS_BACKUP_UPLOADING, 30 + (percentage * 70 / 100))
                    }

                })
                notifyProgress(listener, BackupHandlerIntentService.STATUS_BACKUP_UPLOADING, 100)
                return uploaded
            } finally {
                FileUtils.deleteQuietly(revision)
            }
        }

        private fun notifyProgress(listener: ProgressListener?, status: Int, progress: Int) {
            if (listener != null) {
                listener.onProgress(status, progress)
            }
        }

        /**
         * Create a local zip file that contains the database entries according to the backup
         * file specification. If a password is provided, set it to the zip file.
         */
        @Throws(ExportException::class, IOException::class)
        private fun prepareLocalBackupFile(context: Context, folder: File,
                                           password: String?): File {
            val backupFile = createBackupFile(folder, BackupManager.getExtension(!TextUtils.isEmpty(password)))
            val exporter: AbstractBackupExporter = DefaultBackupExporter(context.contentResolver, backupFile, password)
            exporter.exportDatabase(context.filesDir)
            exporter.exportAttachments(getAttachmentFolder(context))
            return backupFile
        }

        private fun createBackupFile(folder: File, extension: String): File {
            val datetime = DateUtils.getDateTimeString(Date(), FILE_DATETIME_PATTERN)
            val name = String.format(Locale.ENGLISH, OUTPUT_FILE, datetime, extension)
            return File(folder, name)
        }

        @JvmStatic
        @Throws(IOException::class)
        internal fun getAttachmentFolder(context: Context): File {
            val folder = File(context.getExternalFilesDir(null), ATTACHMENT_FOLDER)
            FileUtils.forceMkdir(folder)
            return folder
        }
    }
}
