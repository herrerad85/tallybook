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
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle

import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.model.Attachment
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider

import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by andrea on 27/03/18.
 */
class AttachmentHandlerIntentService : IntentService {

    companion object {

        const val ATTACHMENT = "AttachmentHandlerIntentService::Parameters::Attachment"
        const val ACTION = "AttachmentHandlerIntentService::Parameters::Action"
        const val ERROR = "AttachmentHandlerIntentService::Parameters::Error"

        const val ACTION_CREATE = 1
        const val ACTION_DELETE = 2
    }

    private var mReporter: TaskReporter? = null

    constructor() : super("AttachmentHandlerIntentService")

    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            mReporter = TaskReporter(this)
            val action = intent.getIntExtra(ACTION, 0)
            val attachment: Attachment? = intent.getParcelableExtra(ATTACHMENT)
            notifyOperationStarted(attachment, action)
            try {
                when (action) {
                    ACTION_CREATE -> {
                        val uri = createAttachment(intent.data, attachment)
                        attachment!!.setId(ContentUris.parseId(uri!!))
                    }
                    ACTION_DELETE -> {
                        deleteAttachment(attachment)
                    }
                }
                notifyOperationFinished(attachment, action)
            } catch (e: IOException) {
                notifyOperationFailed(attachment, action, e.message)
            }
        }
    }

    @Throws(IOException::class)
    private fun getAttachmentFile(attachment: Attachment?): File {
        val folder = File(getExternalFilesDir(null), "attachments")
        FileUtils.forceMkdir(folder)
        val file = File(folder, attachment!!.getFile())
        if (!file.exists() && !file.createNewFile()) {
            throw IOException("Failed to create a new file")
        }
        return file
    }

    @Throws(IOException::class)
    private fun createAttachment(uri: Uri?, attachment: Attachment?): Uri? {
        val contentResolver: ContentResolver = getContentResolver()
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            inputStream = contentResolver.openInputStream(uri!!)
            if (inputStream != null) {
                outputStream = FileOutputStream(getAttachmentFile(attachment))
                IOUtils.copy(inputStream, outputStream)
            }
        } finally {
            if (inputStream != null) {
                try {inputStream.close()} catch (ignore: IOException) {}
            }
            if (outputStream != null) {
                try {outputStream.close()} catch (ignore: IOException) {}
            }
        }
        val contentValues = ContentValues()
        contentValues.put(Contract.Attachment.FILE, attachment!!.getFile())
        contentValues.put(Contract.Attachment.NAME, attachment.getName())
        contentValues.put(Contract.Attachment.TYPE, attachment.getType())
        contentValues.put(Contract.Attachment.SIZE, attachment.getSize())
        return contentResolver.insert(DataContentProvider.CONTENT_ATTACHMENTS, contentValues)
    }

    @Throws(IOException::class)
    private fun deleteAttachment(attachment: Attachment?) {
        val contentResolver: ContentResolver = getContentResolver()
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_ATTACHMENTS, attachment!!.getId())
        contentResolver.delete(uri, null, null)
        FileUtils.forceDelete(getAttachmentFile(attachment))
    }

    private fun notifyOperationStarted(attachment: Attachment?, action: Int) {
        val extras = Bundle()
        extras.putParcelable(ATTACHMENT, attachment)
        extras.putInt(ACTION, action)
        mReporter!!.broadcast(LocalAction.ACTION_ATTACHMENT_OP_STARTED, extras)
    }

    private fun notifyOperationFinished(attachment: Attachment?, action: Int) {
        val extras = Bundle()
        extras.putParcelable(ATTACHMENT, attachment)
        extras.putInt(ACTION, action)
        mReporter!!.broadcast(LocalAction.ACTION_ATTACHMENT_OP_FINISHED, extras)
    }

    private fun notifyOperationFailed(attachment: Attachment?, action: Int, error: String?) {
        val extras = Bundle()
        extras.putParcelable(ATTACHMENT, attachment)
        extras.putInt(ACTION, action)
        extras.putString(ERROR, error)
        mReporter!!.broadcast(LocalAction.ACTION_ATTACHMENT_OP_FAILED, extras)
    }
}
