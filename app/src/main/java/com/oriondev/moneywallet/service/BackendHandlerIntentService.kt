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
import android.content.Intent
import android.os.Bundle

import com.oriondev.moneywallet.api.BackendException
import com.oriondev.moneywallet.api.BackendServiceFactory
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.model.IFile
import com.oriondev.moneywallet.utils.Utils

/**
 * Created by andrea on 26/11/18.
 */
class BackendHandlerIntentService : IntentService {

    companion object {

        const val ACTION = "BackendHandlerIntentService::Argument::Action"
        const val BACKEND_ID = "BackendHandlerIntentService::Argument::BackendId"
        const val PARENT_FOLDER = "BackendHandlerIntentService::Argument::ParentFolder"
        const val ERROR_MESSAGE = "BackendHandlerIntentService::Argument::ErrorMessage"
        const val FOLDER_CONTENT = "BackendHandlerIntentService::Argument::FolderContent"
        const val FOLDER_NAME = "BackendHandlerIntentService::Argument::FolderName"
        const val CREATED_FILE = "BackendHandlerIntentService::Argument::CreatedFile"

        const val ACTION_LIST = 0
        const val ACTION_CREATE_FOLDER = 1
    }

    private var mBackendId: String? = null

    private var mReporter: TaskReporter? = null

    constructor() : super("BackendHandlerIntentService")

    override fun onHandleIntent(intent: Intent?) {
        if (intent != null && intent.hasExtra(BACKEND_ID)) {
            mReporter = TaskReporter(this)
            mBackendId = intent.getStringExtra(BACKEND_ID)
            when (intent.getIntExtra(ACTION, ACTION_LIST)) {
                ACTION_LIST -> {
                    onActionList(intent)
                }
                ACTION_CREATE_FOLDER -> {
                    onActionCreateFolder(intent)
                }
            }
        }
    }

    private fun onActionList(intent: Intent) {
        notifyTaskStarted(ACTION_LIST)
        val remoteFolder: IFile? = intent.getParcelableExtra(PARENT_FOLDER)
        try {
            val api = BackendServiceFactory.getServiceAPIById(this, mBackendId)
            val fileList: List<IFile> = api.getFolderContent(remoteFolder)
            notifyListTaskFinished(fileList)
        } catch (e: BackendException) {
            notifyTaskFailure(ACTION_LIST, e.message)
        }
    }

    private fun onActionCreateFolder(intent: Intent) {
        notifyTaskStarted(ACTION_CREATE_FOLDER)
        val remoteFolder: IFile? = intent.getParcelableExtra(PARENT_FOLDER)
        val folderName: String? = intent.getStringExtra(FOLDER_NAME)
        try {
            val api = BackendServiceFactory.getServiceAPIById(this, mBackendId)
            val createdFolder: IFile = api.createFolder(remoteFolder, folderName!!)
            notifyCreateFolderTaskFinished(createdFolder)
        } catch (e: BackendException) {
            notifyTaskFailure(ACTION_CREATE_FOLDER, e.message)
        }
    }

    private fun notifyTaskStarted(action: Int) {
        val extras = Bundle()
        extras.putInt(ACTION, action)
        mReporter!!.broadcast(LocalAction.ACTION_BACKEND_SERVICE_STARTED, extras)
    }

    private fun notifyListTaskFinished(files: List<IFile>) {
        val extras = Bundle()
        extras.putInt(ACTION, ACTION_LIST)
        extras.putParcelableArrayList(FOLDER_CONTENT, Utils.wrapAsArrayList(files))
        mReporter!!.broadcast(LocalAction.ACTION_BACKEND_SERVICE_FINISHED, extras)
    }

    private fun notifyCreateFolderTaskFinished(file: IFile) {
        val extras = Bundle()
        extras.putInt(ACTION, ACTION_CREATE_FOLDER)
        extras.putParcelable(CREATED_FILE, file)
        mReporter!!.broadcast(LocalAction.ACTION_BACKEND_SERVICE_FINISHED, extras)
    }

    private fun notifyTaskFailure(action: Int, message: String?) {
        val extras = Bundle()
        extras.putInt(ACTION, action)
        extras.putString(ERROR_MESSAGE, message)
        mReporter!!.broadcast(LocalAction.ACTION_BACKEND_SERVICE_FAILED, extras)
    }
}
