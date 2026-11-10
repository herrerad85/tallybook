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

package com.oriondev.moneywallet.api.disk

import android.os.Environment

import com.oriondev.moneywallet.api.AbstractBackendServiceAPI
import com.oriondev.moneywallet.api.BackendException
import com.oriondev.moneywallet.model.IFile
import com.oriondev.moneywallet.model.LocalFile
import com.oriondev.moneywallet.utils.ProgressInputStream
import com.oriondev.moneywallet.utils.ProgressOutputStream

import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by andrea on 22/11/18.
 */
class DiskBackendServiceAPI : AbstractBackendServiceAPI<LocalFile> {

    @Throws(BackendException::class)
    constructor() : super(LocalFile::class.java)

    @Throws(BackendException::class)
    public override fun upload(folder: LocalFile?, file: File, listener: ProgressInputStream.UploadProgressListener?): LocalFile {
        val destination = File(getFolder(folder), file.name)
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            if (!destination.exists() && !destination.createNewFile()) {
                throw IOException("Failed to create the file on disk")
            }
            inputStream = FileInputStream(file)
            outputStream = FileOutputStream(destination)
            IOUtils.copy(inputStream, outputStream)
        } catch (e: IOException) {
            throw BackendException(e.message)
        } finally {
            if (inputStream != null) { try { inputStream.close() } catch (ignore: IOException) {} }
            if (outputStream != null) { try { outputStream.close() } catch (ignore: IOException) {} }
        }
        return LocalFile(destination)
    }

    @Throws(BackendException::class)
    public override fun download(folder: File?, file: LocalFile, listener: ProgressOutputStream.DownloadProgressListener?): File {
        // file is already on disk, we can avoid to copy it to another place
        return file.getFile()
    }

    @Throws(BackendException::class)
    public override fun list(folder: LocalFile?): List<IFile> {
        val fileList: MutableList<IFile> = ArrayList()
        val diskFolder = getFolder(folder)
        if (diskFolder.isDirectory) {
            val files = diskFolder.listFiles()
            if (files != null) {
                for (file in files) {
                    fileList.add(LocalFile(file))
                }
            }
        }
        return fileList
    }

    @Throws(BackendException::class)
    public override fun newFolder(parent: LocalFile?, name: String): LocalFile {
        val folder = File(getFolder(parent), name)
        try {
            FileUtils.forceMkdir(folder)
            return LocalFile(folder)
        } catch (e: IOException) {
            throw BackendException(e.message)
        }
    }

    private fun getFolder(folder: LocalFile?): File {
        if (folder == null) {
            return Environment.getExternalStorageDirectory()
        }
        return folder.getFile()
    }

    companion object {

        /** Not the root, which is unwritable from Android 11. Null if the folder cannot be made. */
        @JvmStatic
        fun getDefaultFolder(): LocalFile? {
            val folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (!folder.isDirectory && !folder.mkdirs()) {
                return null
            }
            return LocalFile(folder)
        }
    }
}
