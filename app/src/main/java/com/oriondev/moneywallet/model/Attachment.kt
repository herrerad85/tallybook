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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import androidx.core.content.FileProvider
import android.text.TextUtils

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog

import java.io.File
import java.util.UUID

/**
 * Created by andrea on 27/03/18.
 */
class Attachment : Parcelable, Identifiable {

    private var mId: Long
    private val mFile: String?
    private val mName: String?
    private val mType: String?
    private val mSize: Long

    private var mStatus: Status?

    constructor(id: Long, file: String?, name: String?, type: String?, size: Long) {
        mId = id
        mFile = file
        mName = name
        mType = type
        mSize = size
        mStatus = Status.READY
    }

    constructor(id: Long, file: String?, name: String?, type: String?, size: Long, status: Status?) {
        mId = id
        mFile = file
        mName = name
        mType = type
        mSize = size
        mStatus = status
    }

    private constructor(source: Parcel) {
        mId = source.readLong()
        mFile = source.readString()
        mName = source.readString()
        mType = source.readString()
        mSize = source.readLong()
        mStatus = source.readSerializable() as Status?
    }

    override fun getId(): Long {
        return mId
    }

    fun setId(id: Long) {
        mId = id
    }

    fun getFile(): String? {
        return mFile
    }

    fun getName(): String? {
        return mName
    }

    fun getType(): String? {
        return mType
    }

    fun getSize(): Long {
        return mSize
    }

    fun getStatus(): Status? {
        return mStatus
    }

    fun setStatus(status: Status?, error: String?) {
        mStatus = status
        // TODO handle error
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(mId)
        dest.writeString(mFile)
        dest.writeString(mName)
        dest.writeString(mType)
        dest.writeLong(mSize)
        dest.writeSerializable(mStatus)
    }

    enum class Status {
        PENDING,
        READY,
        ERROR
    }

    override fun equals(other: Any?): Boolean {
        return other is Attachment && mFile!!.equals(other.mFile)
    }

    override fun hashCode(): Int {
        return mFile!!.hashCode()
    }

    fun getUri(context: Context?): Uri? {
        val authority = context!!.packageName + ".storage.file"
        val folder = File(context.getExternalFilesDir(null), FOLDER_NAME)
        val attachment = File(folder, mFile)
        return FileProvider.getUriForFile(context, authority, attachment)
    }

    fun getActionViewIntent(context: Context?): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(getUri(context), mType)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return intent
    }

    companion object {

        const val FOLDER_NAME = "attachments"

        @JvmField
        val CREATOR: Parcelable.Creator<Attachment> = object : Parcelable.Creator<Attachment> {

            override fun createFromParcel(source: Parcel): Attachment {
                return Attachment(source)
            }

            override fun newArray(size: Int): Array<Attachment?> {
                return arrayOfNulls(size)
            }

        }

        @JvmStatic
        fun generateFileUID(): String {
            return UUID.randomUUID().toString().replace("-", "")
        }

        @JvmStatic
        fun openAttachment(context: Context?, attachment: Attachment?) {
            try {
                val intent = attachment!!.getActionViewIntent(context)
                context!!.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                ThemedDialog.buildMaterialDialog(context)
                        .setTitle(R.string.title_error)
                        .setMessage(R.string.message_error_activity_not_found)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
            }
        }

        @JvmStatic
        fun getIconResByType(type: String?): Int {
            if (!TextUtils.isEmpty(type)) {
                when (type) {
                    "application/pdf" ->
                        return R.drawable.ic_file_pdf_24dp
                    "text/plain" ->
                        return R.drawable.ic_file_document_24dp
                    "image/png",
                    "image/jpg",
                    "image/jpeg",
                    "image/gif",
                    "image/bmp" ->
                        return R.drawable.ic_file_image_24dp
                    "video/mp4" ->
                        return R.drawable.ic_file_video_24dp
                }
            }
            return R.drawable.ic_file_outline_24dp
        }
    }
}
