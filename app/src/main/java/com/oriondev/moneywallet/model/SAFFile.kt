package com.oriondev.moneywallet.model

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable

import org.json.JSONException
import org.json.JSONObject

import androidx.documentfile.provider.DocumentFile

/**
 * Represents a file/document with regards to the android storage access framework.
 */
class SAFFile : IFile {

    private val mUri: Uri
    private var mName: String?
    private var mSize: Long
    private var mIsDir: Boolean

    constructor(encoded: String?) {
        try {
            val jsonObject = JSONObject(encoded)
            mUri = Uri.parse(jsonObject.getString(URI))
            mName = jsonObject.getString(NAME)
            mSize = jsonObject.getLong(SIZE)
            mIsDir = jsonObject.getBoolean(IS_DIRECTORY)
        } catch (e: JSONException) {
            throw RuntimeException("Cannot decode file from string: " + e.message)
        }
    }

    constructor(documentFile: DocumentFile) {
        mIsDir = documentFile.isDirectory
        mUri = documentFile.uri
        mSize = documentFile.length()
        mName = documentFile.name
    }

    constructor(source: Parcel) {
        mUri = Uri.parse(source.readString())
        mName = source.readString()
        mSize = source.readLong()
        mIsDir = source.readByte().toInt() != 0
    }

    override fun getName(): String? {
        return mName
    }

    override fun getExtension(): String? {
        val index = getName()!!.lastIndexOf(".")
        return if (index >= 0) getName()!!.substring(index) else null
    }

    override fun isDirectory(): Boolean {
        return mIsDir
    }

    override fun getSize(): Long {
        return mSize
    }

    override fun encodeToString(): String? {
        try {
            val jsonObject = JSONObject()
            jsonObject.put(URI, mUri.toString())
            jsonObject.put(NAME, mName)
            jsonObject.put(SIZE, mSize)
            jsonObject.put(IS_DIRECTORY, mIsDir)
            return jsonObject.toString()
        } catch (e: JSONException) {
            throw RuntimeException("Cannot encode file to string: " + e.message)
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, i: Int) {
        dest.writeString(mUri.toString())
        dest.writeString(mName)
        dest.writeLong(mSize)
        dest.writeByte((if (mIsDir) 1 else 0).toByte())
    }

    fun getUri(): Uri {
        return mUri
    }

    companion object {

        private const val URI = "uri"
        private const val NAME = "name"
        private const val SIZE = "size"
        private const val IS_DIRECTORY = "isDir"

        @JvmField
        val CREATOR: Parcelable.Creator<SAFFile> = object : Parcelable.Creator<SAFFile> {
            override fun createFromParcel(source: Parcel): SAFFile {
                return SAFFile(source)
            }

            override fun newArray(size: Int): Array<SAFFile?> {
                return arrayOfNulls(size)
            }
        }

        /**
         * Returns true only when the value looks like an encoded SAFFile descriptor (a JSON object),
         * as opposed to a legacy raw path (e.g. "/storage/emulated/0") persisted by older versions.
         * Pure check with no Android dependencies, so it is unit-testable.
         */
        @JvmStatic
        fun looksEncoded(encoded: String?): Boolean {
            return encoded != null && encoded.trim { it <= ' ' }.startsWith("{")
        }

        /**
         * Decodes an encoded descriptor, returning null instead of throwing when the value is missing,
         * a legacy path, or otherwise undecodable. Lets callers fail soft rather than crash at startup
         * (see issue #177, where a legacy auto-backup path crashed the app in Application.onCreate).
         */
        @JvmStatic
        fun decode(encoded: String?): SAFFile? {
            if (!looksEncoded(encoded)) {
                return null
            }
            try {
                return SAFFile(encoded)
            } catch (e: RuntimeException) {
                return null
            }
        }
    }
}
