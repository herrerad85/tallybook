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

package com.oriondev.moneywallet.utils

import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Created by andrea on 26/07/18.
 */
class ProgressOutputStream : OutputStream {

    private val mOutputStream: OutputStream
    private val mListener: DownloadProgressListener

    private val mFileSize: Long
    private var mProgress: Long
    private var mLastPercentage: Int

    @Throws(FileNotFoundException::class)
    constructor(file: File, size: Long, listener: DownloadProgressListener) : this(FileOutputStream(file), size, listener)

    @Throws(FileNotFoundException::class)
    constructor(out: OutputStream, size: Long, listener: DownloadProgressListener) : super() {
        mOutputStream = out
        mListener = listener
        mFileSize = size
        mProgress = 0L
        mLastPercentage = 0
    }

    @Throws(IOException::class)
    override fun write(b: Int) {
        mOutputStream.write(b)
        mProgress += 1
        notifyProgress()
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray?) {
        write(b, 0, b!!.size)
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray?, off: Int, len: Int) {
        mOutputStream.write(b, off, len)
        mProgress += len
        notifyProgress()
    }

    @Throws(IOException::class)
    override fun flush() {
        mOutputStream.flush()
    }

    @Throws(IOException::class)
    override fun close() {
        mOutputStream.close()
    }

    private fun notifyProgress() {
        val currentPercentage = ((mProgress * 100) / mFileSize).toInt()
        if (currentPercentage - mLastPercentage > MIN_NOTIFY_RANGE) {
            mLastPercentage = Math.min(currentPercentage, 100)
            mListener.onDownloadProgressUpdate(mLastPercentage)
        }
    }

    interface DownloadProgressListener {

        fun onDownloadProgressUpdate(percentage: Int)
    }

    companion object {

        private const val MIN_NOTIFY_RANGE = 5
    }
}
