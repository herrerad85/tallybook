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

package com.oriondev.moneywallet.api

import com.oriondev.moneywallet.model.IFile
import com.oriondev.moneywallet.utils.ProgressInputStream
import com.oriondev.moneywallet.utils.ProgressOutputStream

import java.io.File

/**
 * Created by andrea on 24/11/18.
 */
@Suppress("UNCHECKED_CAST")
abstract class AbstractBackendServiceAPI<T : IFile> : IBackendServiceAPI {

    private val mType: Class<T>

    @Throws(BackendException::class)
    constructor(type: Class<T>) {
        mType = type
    }

    @Throws(BackendException::class)
    override fun uploadFile(folder: IFile?, file: File, listener: ProgressInputStream.UploadProgressListener?): IFile {
        if (folder == null || mType.isInstance(folder)) {
            return upload(folder as T?, file, listener)
        } else {
            throw ClassCastException("Backend cannot upload a file to a folder that is not an instance of " + mType.name + ". The provided folder is an instance of: " + folder.javaClass.name)
        }
    }

    @Throws(BackendException::class)
    protected abstract fun upload(folder: T?, file: File, listener: ProgressInputStream.UploadProgressListener?): T

    @Throws(BackendException::class)
    override fun downloadFile(folder: File?, file: IFile?, listener: ProgressOutputStream.DownloadProgressListener?): File {
        if (mType.isInstance(file)) {
            return download(folder, file as T, listener)
        } else {
            throw ClassCastException("Backend cannot download a file that is not an instance of " + mType.name + ". The provided file is an instance of: " + folder!!.javaClass.name)
        }
    }

    @Throws(BackendException::class)
    protected abstract fun download(folder: File?, file: T, listener: ProgressOutputStream.DownloadProgressListener?): File

    @Throws(BackendException::class)
    override fun getFolderContent(folder: IFile?): List<IFile> {
        if (folder == null || mType.isInstance(folder)) {
            return list(folder as T?)
        } else {
            throw ClassCastException("Backend cannot list the content of a folder that is not an instance of " + mType.name + ". The provided folder is an instance of: " + folder.javaClass.name)
        }
    }

    @Throws(BackendException::class)
    protected abstract fun list(folder: T?): List<IFile>

    @Throws(BackendException::class)
    override fun createFolder(parent: IFile?, name: String): IFile {
        if (parent == null || mType.isInstance(parent)) {
            return newFolder(parent as T?, name)
        } else {
            throw ClassCastException("Backend cannot create a folder in a folder that is not an instance of " + mType.name + ". The provided folder is an instance of: " + parent.javaClass.name)
        }
    }

    @Throws(BackendException::class)
    protected abstract fun newFolder(parent: T?, name: String): T
}
