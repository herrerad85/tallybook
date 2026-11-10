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

package com.oriondev.moneywallet.api;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.oriondev.moneywallet.model.IFile;

/**
 * Single source of truth for one backup backend. Each flavor's {@code BackendServiceFactory}
 * holds a list of these instead of four parallel switch statements: the id is the lookup key,
 * the icon is carried here (the delegate has no icon accessor), and everything else is created
 * on demand. The backend name is read back from the delegate so it is not re-declared here.
 */
public abstract class BackendDescriptor {

    private final String mId;
    private final int mIconRes;

    protected BackendDescriptor(String id, @DrawableRes int iconRes) {
        mId = id;
        mIconRes = iconRes;
    }

    public String getId() {
        return mId;
    }

    @DrawableRes
    public int getIconRes() {
        return mIconRes;
    }

    /**
     * The backend name, taken from the delegate's own metadata so it lives in exactly one place.
     */
    @StringRes
    public int getNameRes() {
        return createDelegate(null).getName();
    }

    /**
     * Whether this backend is usable on the current device. Defaults to true.
     */
    public boolean isAvailable() {
        return true;
    }

    /** Where a backup goes when no folder was chosen. Null when there is none to offer. */
    public IFile getDefaultFolder() {
        return null;
    }

    public abstract AbstractBackendServiceDelegate createDelegate(AbstractBackendServiceDelegate.BackendServiceStatusListener listener);

    public abstract IBackendServiceAPI createServiceApi(Context context) throws BackendException;

    public abstract IFile createFile(String encoded);
}
