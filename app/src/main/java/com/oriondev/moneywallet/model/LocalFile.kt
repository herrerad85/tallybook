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

import java.io.File

/**
 * Created by andre on 21/03/2018.
 */
class LocalFile : IFile {

    private val mFile: File

    constructor(file: File) {
        mFile = file
    }

    constructor(encoded: String?) {
        mFile = File(encoded)
    }

    private constructor(source: Parcel) {
        mFile = File(source.readString())
    }

    override fun getName(): String? {
        return mFile.name
    }

    override fun getExtension(): String? {
        val index = mFile.name.lastIndexOf(".")
        return if (index >= 0) mFile.name.substring(index) else null
    }

    override fun isDirectory(): Boolean {
        return mFile.isDirectory
    }

    override fun getSize(): Long {
        return mFile.length()
    }

    override fun encodeToString(): String? {
        return mFile.path
    }

    fun getFile(): File {
        return mFile
    }

    fun getLocalPath(): String? {
        return mFile.path
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(mFile.path)
    }

    companion object {

        @JvmField
        val CREATOR: Parcelable.Creator<LocalFile> = object : Parcelable.Creator<LocalFile> {
            override fun createFromParcel(source: Parcel): LocalFile {
                return LocalFile(source)
            }

            override fun newArray(size: Int): Array<LocalFile?> {
                return arrayOfNulls(size)
            }
        }
    }
}
