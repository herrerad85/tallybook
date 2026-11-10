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

package com.oriondev.moneywallet.api.webdav;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.oriondev.moneywallet.api.AbstractBackendServiceAPI;
import com.oriondev.moneywallet.api.BackendException;
import com.oriondev.moneywallet.model.IFile;
import com.oriondev.moneywallet.model.WebDAVFile;
import com.oriondev.moneywallet.utils.ProgressInputStream;
import com.oriondev.moneywallet.utils.ProgressOutputStream;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Runs from background services as well as the backup screen, so nothing here may assume a UI.
 */
public class WebDAVBackendServiceAPI extends AbstractBackendServiceAPI<WebDAVFile> {

    private final WebDAVClient mClient;

    public WebDAVBackendServiceAPI(Context context) throws BackendException {
        super(WebDAVFile.class);
        String url = WebDAVBackendService.getServerUrl(context);
        if (url == null) {
            throw new BackendException("WebDAV backend cannot be initialized: no server configured");
        }
        mClient = new WebDAVClient(
                url,
                WebDAVBackendService.getUsername(context),
                WebDAVBackendService.getPassword(context)
        );
    }

    private static String pathOf(@Nullable WebDAVFile folder) {
        return folder == null ? "" : folder.getPath();
    }

    private static String childPath(@Nullable WebDAVFile folder, String name) {
        String parent = pathOf(folder);
        return parent.isEmpty() ? name : parent + "/" + name;
    }

    @Override
    protected WebDAVFile upload(@Nullable WebDAVFile folder, File file, ProgressInputStream.UploadProgressListener listener) throws BackendException {
        String path = childPath(folder, file.getName());
        try (InputStream in = new ProgressInputStream(file, listener)) {
            mClient.upload(path, in, file.length());
        } catch (FileNotFoundException e) {
            throw new BackendException(String.format("File '%s' doesn't exist", file.getName()), e);
        } catch (IOException e) {
            throw new BackendException(String.format("Couldn't access file '%s'", file.getName()), e, true);
        }
        return new WebDAVFile(file.getName(), path, file.length(), false);
    }

    @Override
    protected File download(File folder, @NonNull WebDAVFile file, ProgressOutputStream.DownloadProgressListener listener) throws BackendException {
        File destination = new File(folder, file.getName());
        try (InputStream in = mClient.openDownload(file.getPath());
             OutputStream out = new ProgressOutputStream(destination, file.getSize(), listener)) {
            IOUtils.copy(in, out);
        } catch (FileNotFoundException e) {
            throw new BackendException(
                    String.format("Couldn't open '%s' for writing", destination.getName()), e, true
            );
        } catch (IOException e) {
            throw new BackendException(
                    String.format("Failed to download %s to %s", file.getName(), destination.getName()), e, true
            );
        }
        return destination;
    }

    @Override
    protected List<IFile> list(@Nullable WebDAVFile folder) throws BackendException {
        return new java.util.ArrayList<IFile>(mClient.list(pathOf(folder)));
    }

    @Override
    protected WebDAVFile newFolder(WebDAVFile parent, String name) throws BackendException {
        String path = childPath(parent, name);
        mClient.makeCollection(path);
        return new WebDAVFile(name, path, 0L, true);
    }
}
