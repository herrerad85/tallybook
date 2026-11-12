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
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.oriondev.moneywallet.api.BackendException;
import com.oriondev.moneywallet.api.BackendServiceFactory;
import com.oriondev.moneywallet.api.IBackendServiceAPI;
import com.oriondev.moneywallet.broadcast.LocalAction;
import com.oriondev.moneywallet.model.IFile;
import com.oriondev.moneywallet.utils.Utils;

import java.util.List;

/**
 * Created by andrea on 26/11/18.
 */
public class BackendHandlerIntentService extends IntentService {

    public static final String ACTION = "BackendHandlerIntentService::Argument::Action";
    public static final String BACKEND_ID = "BackendHandlerIntentService::Argument::BackendId";
    public static final String PARENT_FOLDER = "BackendHandlerIntentService::Argument::ParentFolder";
    public static final String ERROR_MESSAGE = "BackendHandlerIntentService::Argument::ErrorMessage";
    public static final String FOLDER_CONTENT = "BackendHandlerIntentService::Argument::FolderContent";
    public static final String FOLDER_NAME = "BackendHandlerIntentService::Argument::FolderName";
    public static final String CREATED_FILE = "BackendHandlerIntentService::Argument::CreatedFile";

    public static final int ACTION_LIST = 0;
    public static final int ACTION_CREATE_FOLDER = 1;

    private String mBackendId;

    private TaskReporter mReporter;

    public BackendHandlerIntentService() {
        super("BackendHandlerIntentService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent != null && intent.hasExtra(BACKEND_ID)) {
            mReporter = new TaskReporter(this);
            mBackendId = intent.getStringExtra(BACKEND_ID);
            switch (intent.getIntExtra(ACTION, ACTION_LIST)) {
                case ACTION_LIST:
                    onActionList(intent);
                    break;
                case ACTION_CREATE_FOLDER:
                    onActionCreateFolder(intent);
                    break;
            }
        }
    }

    private void onActionList(@NonNull Intent intent) {
        notifyTaskStarted(ACTION_LIST);
        IFile remoteFolder = intent.getParcelableExtra(PARENT_FOLDER);
        try {
            IBackendServiceAPI api = BackendServiceFactory.getServiceAPIById(this, mBackendId);
            List<IFile> fileList = api.getFolderContent(remoteFolder);
            notifyListTaskFinished(fileList);
        } catch (BackendException e) {
            notifyTaskFailure(ACTION_LIST, e.getMessage());
        }
    }

    private void onActionCreateFolder(@NonNull Intent intent) {
        notifyTaskStarted(ACTION_CREATE_FOLDER);
        IFile remoteFolder = intent.getParcelableExtra(PARENT_FOLDER);
        String folderName = intent.getStringExtra(FOLDER_NAME);
        try {
            IBackendServiceAPI api = BackendServiceFactory.getServiceAPIById(this, mBackendId);
            IFile createdFolder = api.createFolder(remoteFolder, folderName);
            notifyCreateFolderTaskFinished(createdFolder);
        } catch (BackendException e) {
            notifyTaskFailure(ACTION_CREATE_FOLDER, e.getMessage());
        }
    }

    private void notifyTaskStarted(int action) {
        Bundle extras = new Bundle();
        extras.putInt(ACTION, action);
        mReporter.broadcast(LocalAction.ACTION_BACKEND_SERVICE_STARTED, extras);
    }

    private void notifyListTaskFinished(List<IFile> files) {
        Bundle extras = new Bundle();
        extras.putInt(ACTION, ACTION_LIST);
        extras.putParcelableArrayList(FOLDER_CONTENT, Utils.wrapAsArrayList(files));
        mReporter.broadcast(LocalAction.ACTION_BACKEND_SERVICE_FINISHED, extras);
    }

    private void notifyCreateFolderTaskFinished(IFile file) {
        Bundle extras = new Bundle();
        extras.putInt(ACTION, ACTION_CREATE_FOLDER);
        extras.putParcelable(CREATED_FILE, file);
        mReporter.broadcast(LocalAction.ACTION_BACKEND_SERVICE_FINISHED, extras);
    }

    private void notifyTaskFailure(int action, String message) {
        Bundle extras = new Bundle();
        extras.putInt(ACTION, action);
        extras.putString(ERROR_MESSAGE, message);
        mReporter.broadcast(LocalAction.ACTION_BACKEND_SERVICE_FAILED, extras);
    }
}