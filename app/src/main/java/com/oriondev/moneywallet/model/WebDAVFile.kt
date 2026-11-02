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

package com.oriondev.moneywallet.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A file or folder on a WebDAV server, identified by its path relative to the configured
 * root URL. The path is stored decoded; percent encoding happens when a request URL is built,
 * so the same path can round trip through preferences without double encoding.
 */
public class WebDAVFile implements IFile {

    private static final String NAME = "name";
    private static final String PATH = "path";
    private static final String SIZE = "size";
    private static final String DIRECTORY = "directory";

    private final String mName;
    private final String mPath;
    private final long mSize;
    private final boolean mIsDirectory;

    public WebDAVFile(String name, String path, long size, boolean isDirectory) {
        mName = name;
        mPath = path;
        mSize = size;
        mIsDirectory = isDirectory;
    }

    public WebDAVFile(@NonNull String encoded) throws JSONException {
        JSONObject object = new JSONObject(encoded);
        mName = object.getString(NAME);
        mPath = object.getString(PATH);
        mSize = object.optLong(SIZE, 0L);
        mIsDirectory = object.optBoolean(DIRECTORY, false);
    }

    /**
     * Fail soft, like {@link SAFFile#decode(String)}. This runs from
     * {@code BackendServiceFactory.getFile()} against a value that has been sitting in preferences
     * since an older version, so a value we cannot read must return null rather than take the app
     * down on startup.
     */
    @Nullable
    public static WebDAVFile decode(String encoded) {
        if (encoded == null || !encoded.trim().startsWith("{")) {
            return null;
        }
        try {
            return new WebDAVFile(encoded);
        } catch (JSONException | RuntimeException e) {
            return null;
        }
    }

    protected WebDAVFile(Parcel in) {
        mName = in.readString();
        mPath = in.readString();
        mSize = in.readLong();
        mIsDirectory = in.readByte() != 0;
    }

    public static final Creator<WebDAVFile> CREATOR = new Creator<WebDAVFile>() {

        @Override
        public WebDAVFile createFromParcel(Parcel in) {
            return new WebDAVFile(in);
        }

        @Override
        public WebDAVFile[] newArray(int size) {
            return new WebDAVFile[size];
        }
    };

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public String getExtension() {
        int index = mName.lastIndexOf(".");
        return index >= 0 ? mName.substring(index) : null;
    }

    @Override
    public boolean isDirectory() {
        return mIsDirectory;
    }

    @Override
    public long getSize() {
        return mSize;
    }

    public String getPath() {
        return mPath;
    }

    @Override
    public String encodeToString() {
        try {
            JSONObject object = new JSONObject();
            object.put(NAME, mName);
            object.put(PATH, mPath);
            object.put(SIZE, mSize);
            object.put(DIRECTORY, mIsDirectory);
            return object.toString();
        } catch (JSONException e) {
            throw new RuntimeException("Failed to encode WebDAV file: " + e.getMessage());
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeString(mPath);
        dest.writeLong(mSize);
        dest.writeByte((byte) (mIsDirectory ? 1 : 0));
    }
}
