/*
 * Copyright (c) 2026.
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


package com.oriondev.moneywallet.service.beforerestore

import android.content.Context
import android.os.Environment
import android.text.TextUtils
import android.util.Log

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.service.BackupOperation
import com.oriondev.moneywallet.storage.database.ExportException
import com.oriondev.moneywallet.storage.database.backup.BackupManager
import com.oriondev.moneywallet.utils.DateUtils

import org.apache.commons.io.FileUtils

import java.io.File
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class BeforeRestoreCopy {

    companion object {

        private const val TAG = "BeforeRestoreCopy"
        private const val FOLDER_NAME = "Tallybook"
        private const val FILE_PREFIX = "before_restore_"
        private const val KEPT_COPIES = 3
        private const val PARTIAL_EXTENSION = ".partial"

        private val EXTENSIONS = arrayOf(BackupManager.BACKUP_EXTENSION_STANDARD, BackupManager.BACKUP_EXTENSION_PROTECTED)

        @JvmStatic
        @Throws(ExportException::class)
        internal fun create(context: Context, password: String?): File {
            val documents = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), FOLDER_NAME)
            return try {
                createIn(context, documents, password)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot save the copy in $documents, saving it in the app folder instead: $e")
                try {
                    createIn(context, File(context.getExternalFilesDir(null), FOLDER_NAME), password)
                } catch (fallback: Exception) {
                    Log.w(TAG, "Cannot save the copy in the app folder either: $fallback")
                    throw ExportException(context.getString(R.string.message_backup_restoring_copy_failed))
                }
            }
        }

        private fun createIn(context: Context, folder: File, password: String?): File {
            deletePartialCopies(folder)
            FileUtils.forceMkdir(folder)
            val datetime = DateUtils.getDateTimeString(Date(), BackupOperation.FILE_DATETIME_PATTERN)
            val copy = File(folder, FILE_PREFIX + datetime + BackupManager.getExtension(!TextUtils.isEmpty(password)))
            val partial = File(folder, copy.name + PARTIAL_EXTENSION)
            try {
                BackupOperation.exportLocalBackupFile(context, partial, password)
                FileUtils.moveFile(partial, copy)
                return copy
            } finally {
                if (partial.isFile) {
                    FileUtils.deleteQuietly(partial)
                }
            }
        }

        private fun deletePartialCopies(folder: File) {
            val files = folder.listFiles() ?: return
            for (file in files) {
                if (file.isFile && file.name.endsWith(PARTIAL_EXTENSION) && isCopy(file.name.removeSuffix(PARTIAL_EXTENSION))) {
                    FileUtils.deleteQuietly(file)
                }
            }
        }

        internal fun deleteOlderCopies(copy: File, restored: String) {
            val files = copy.parentFile?.listFiles() ?: return
            val older = files.filter { it != copy && it.name != restored && isCopy(it) }.sortedByDescending { it.name }
            for (file in older.drop(KEPT_COPIES - 1)) {
                if (!file.delete()) {
                    Log.w(TAG, "Cannot delete the older copy $file")
                }
            }
        }

        private fun isCopy(file: File): Boolean {
            return file.isFile && isCopy(file.name)
        }

        private fun isCopy(name: String): Boolean {
            val extension = EXTENSIONS.firstOrNull { name.endsWith(it) }
            if (!name.startsWith(FILE_PREFIX) || extension == null) {
                return false
            }
            val datetime = name.substring(FILE_PREFIX.length, name.length - extension.length)
            val format = SimpleDateFormat(BackupOperation.FILE_DATETIME_PATTERN, Locale.ENGLISH)
            format.isLenient = false
            val date = format.parse(datetime, ParsePosition(0)) ?: return false
            return format.format(date) == datetime
        }
    }
}
