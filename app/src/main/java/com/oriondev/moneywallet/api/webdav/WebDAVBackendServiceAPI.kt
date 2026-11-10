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

package com.oriondev.moneywallet.api.webdav

import android.content.Context

import com.oriondev.moneywallet.api.AbstractBackendServiceAPI
import com.oriondev.moneywallet.api.BackendException
import com.oriondev.moneywallet.model.IFile
import com.oriondev.moneywallet.model.WebDAVFile
import com.oriondev.moneywallet.utils.ProgressInputStream
import com.oriondev.moneywallet.utils.ProgressOutputStream

import org.apache.commons.io.IOUtils

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Runs from background services as well as the backup screen, so nothing here may assume a UI.
 */
class WebDAVBackendServiceAPI : AbstractBackendServiceAPI<WebDAVFile> {

    private val mClient: WebDAVClient

    @Throws(BackendException::class)
    constructor(context: Context) : super(WebDAVFile::class.java) {
        val url = WebDAVBackendService.getServerUrl(context)
        if (url == null) {
            throw BackendException("WebDAV backend cannot be initialized: no server configured")
        }
        mClient = WebDAVClient(
                url,
                WebDAVBackendService.getUsername(context),
                WebDAVBackendService.getPassword(context)
        )
    }

    @Throws(BackendException::class)
    override fun upload(folder: WebDAVFile?, file: File, listener: ProgressInputStream.UploadProgressListener?): WebDAVFile {
        val path = childPath(folder, file.name)
        try {
            ProgressInputStream(file, listener!!).use { `in` ->
                mClient.upload(path, `in`, file.length())
            }
        } catch (e: FileNotFoundException) {
            throw BackendException(String.format("File '%s' doesn't exist", file.name), e)
        } catch (e: IOException) {
            throw BackendException(String.format("Couldn't access file '%s'", file.name), e, true)
        }
        return WebDAVFile(file.name, path, file.length(), false)
    }

    @Throws(BackendException::class)
    override fun download(folder: File?, file: WebDAVFile, listener: ProgressOutputStream.DownloadProgressListener?): File {
        val destination = File(folder, file.getName())
        try {
            mClient.openDownload(file.getPath()).use { `in` ->
                ProgressOutputStream(destination, file.getSize(), listener!!).use { out ->
                    IOUtils.copy(`in`, out)
                }
            }
        } catch (e: FileNotFoundException) {
            throw BackendException(
                    String.format("Couldn't open '%s' for writing", destination.name), e, true
            )
        } catch (e: IOException) {
            throw BackendException(
                    String.format("Failed to download %s to %s", file.getName(), destination.name), e, true
            )
        }
        return destination
    }

    @Throws(BackendException::class)
    override fun list(folder: WebDAVFile?): List<IFile> {
        return java.util.ArrayList<IFile>(mClient.list(pathOf(folder)))
    }

    @Throws(BackendException::class)
    override fun newFolder(parent: WebDAVFile?, name: String): WebDAVFile {
        val path = childPath(parent, name)
        mClient.makeCollection(path)
        return WebDAVFile(name, path, 0L, true)
    }

    companion object {

        private fun pathOf(folder: WebDAVFile?): String? {
            return if (folder == null) "" else folder.getPath()
        }

        private fun childPath(folder: WebDAVFile?, name: String?): String? {
            val parent = pathOf(folder)
            return if (parent!!.isEmpty()) name else parent + "/" + name
        }
    }
}
