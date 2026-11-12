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
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;

import com.oriondev.moneywallet.broadcast.LocalAction;
import com.oriondev.moneywallet.model.Attachment;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by andrea on 27/03/18.
 */
public class AttachmentHandlerIntentService extends IntentService {

    public static final String ATTACHMENT = "AttachmentHandlerIntentService::Parameters::Attachment";
    public static final String ACTION = "AttachmentHandlerIntentService::Parameters::Action";
    public static final String ERROR = "AttachmentHandlerIntentService::Parameters::Error";

    public static final int ACTION_CREATE = 1;
    public static final int ACTION_DELETE = 2;

    private TaskReporter mReporter;

    public AttachmentHandlerIntentService() {
        super("AttachmentHandlerIntentService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent != null) {
            mReporter = new TaskReporter(this);
            int action = intent.getIntExtra(ACTION, 0);
            Attachment attachment = intent.getParcelableExtra(ATTACHMENT);
            notifyOperationStarted(attachment, action);
            try {
                switch (action) {
                    case ACTION_CREATE:
                        Uri uri = createAttachment(intent.getData(), attachment);
                        attachment.setId(ContentUris.parseId(uri));
                        break;
                    case ACTION_DELETE:
                        deleteAttachment(attachment);
                        break;
                }
                notifyOperationFinished(attachment, action);
            } catch (IOException e) {
                notifyOperationFailed(attachment, action, e.getMessage());
            }
        }
    }

    private File getAttachmentFile(Attachment attachment) throws IOException {
        File folder = new File(getExternalFilesDir(null), "attachments");
        FileUtils.forceMkdir(folder);
        File file = new File(folder, attachment.getFile());
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("Failed to create a new file");
        }
        return file;
    }

    private Uri createAttachment(Uri uri, Attachment attachment) throws IOException {
        ContentResolver contentResolver = getContentResolver();
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = contentResolver.openInputStream(uri);
            if (inputStream != null) {
                outputStream = new FileOutputStream(getAttachmentFile(attachment));
                IOUtils.copy(inputStream, outputStream);
            }
        } finally {
            if (inputStream != null) {
                try {inputStream.close();} catch (IOException ignore) {}
            }
            if (outputStream != null) {
                try {outputStream.close();} catch (IOException ignore) {}
            }
        }
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Attachment.FILE, attachment.getFile());
        contentValues.put(Contract.Attachment.NAME, attachment.getName());
        contentValues.put(Contract.Attachment.TYPE, attachment.getType());
        contentValues.put(Contract.Attachment.SIZE, attachment.getSize());
        return contentResolver.insert(DataContentProvider.CONTENT_ATTACHMENTS, contentValues);
    }

    private void deleteAttachment(Attachment attachment) throws IOException {
        ContentResolver contentResolver = getContentResolver();
        Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_ATTACHMENTS, attachment.getId());
        contentResolver.delete(uri, null, null);
        FileUtils.forceDelete(getAttachmentFile(attachment));
    }

    private void notifyOperationStarted(Attachment attachment, int action) {
        Bundle extras = new Bundle();
        extras.putParcelable(ATTACHMENT, attachment);
        extras.putInt(ACTION, action);
        mReporter.broadcast(LocalAction.ACTION_ATTACHMENT_OP_STARTED, extras);
    }

    private void notifyOperationFinished(Attachment attachment, int action) {
        Bundle extras = new Bundle();
        extras.putParcelable(ATTACHMENT, attachment);
        extras.putInt(ACTION, action);
        mReporter.broadcast(LocalAction.ACTION_ATTACHMENT_OP_FINISHED, extras);
    }

    private void notifyOperationFailed(Attachment attachment, int action, String error) {
        Bundle extras = new Bundle();
        extras.putParcelable(ATTACHMENT, attachment);
        extras.putInt(ACTION, action);
        extras.putString(ERROR, error);
        mReporter.broadcast(LocalAction.ACTION_ATTACHMENT_OP_FAILED, extras);
    }
}