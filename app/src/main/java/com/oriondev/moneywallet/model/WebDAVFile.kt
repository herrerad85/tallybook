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

package com.oriondev.moneywallet.model

import android.os.Parcel
import android.os.Parcelable

import org.json.JSONException
import org.json.JSONObject

/**
 * A file or folder on a WebDAV server, identified by its path relative to the configured
 * root URL. The path is stored decoded; percent encoding happens when a request URL is built,
 * so the same path can round trip through preferences without double encoding.
 */
class WebDAVFile : IFile {

    private val mName: String?
    private val mPath: String?
    private val mSize: Long
    private val mIsDirectory: Boolean

    constructor(name: String?, path: String?, size: Long, isDirectory: Boolean) {
        mName = name
        mPath = path
        mSize = size
        mIsDirectory = isDirectory
    }

    @Throws(JSONException::class)
    constructor(encoded: String) {
        val jsonObject = JSONObject(encoded)
        mName = jsonObject.getString(NAME)
        mPath = jsonObject.getString(PATH)
        mSize = jsonObject.optLong(SIZE, 0L)
        mIsDirectory = jsonObject.optBoolean(DIRECTORY, false)
    }

    protected constructor(source: Parcel) {
        mName = source.readString()
        mPath = source.readString()
        mSize = source.readLong()
        mIsDirectory = source.readByte().toInt() != 0
    }

    override fun getName(): String? {
        return mName
    }

    override fun getExtension(): String? {
        val index = mName!!.lastIndexOf(".")
        return if (index >= 0) mName.substring(index) else null
    }

    override fun isDirectory(): Boolean {
        return mIsDirectory
    }

    override fun getSize(): Long {
        return mSize
    }

    fun getPath(): String? {
        return mPath
    }

    override fun encodeToString(): String? {
        try {
            val jsonObject = JSONObject()
            jsonObject.put(NAME, mName)
            jsonObject.put(PATH, mPath)
            jsonObject.put(SIZE, mSize)
            jsonObject.put(DIRECTORY, mIsDirectory)
            return jsonObject.toString()
        } catch (e: JSONException) {
            throw RuntimeException("Failed to encode WebDAV file: " + e.message)
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(mName)
        dest.writeString(mPath)
        dest.writeLong(mSize)
        dest.writeByte((if (mIsDirectory) 1 else 0).toByte())
    }

    companion object {

        private const val NAME = "name"
        private const val PATH = "path"
        private const val SIZE = "size"
        private const val DIRECTORY = "directory"

        /**
         * Fail soft, like [SAFFile.decode]. This runs from
         * `BackendServiceFactory.getFile()` against a value that has been sitting in preferences
         * since an older version, so a value we cannot read must return null rather than take the app
         * down on startup.
         */
        @JvmStatic
        fun decode(encoded: String?): WebDAVFile? {
            if (encoded == null || !encoded.trim { it <= ' ' }.startsWith("{")) {
                return null
            }
            try {
                return WebDAVFile(encoded)
            } catch (e: JSONException) {
                return null
            } catch (e: RuntimeException) {
                return null
            }
        }

        @JvmField
        val CREATOR: Parcelable.Creator<WebDAVFile> = object : Parcelable.Creator<WebDAVFile> {

            override fun createFromParcel(source: Parcel): WebDAVFile {
                return WebDAVFile(source)
            }

            override fun newArray(size: Int): Array<WebDAVFile?> {
                return arrayOfNulls(size)
            }
        }
    }
}
