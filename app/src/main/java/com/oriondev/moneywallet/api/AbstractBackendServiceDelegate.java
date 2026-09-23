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
import android.content.Intent;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

/**
 * Created by andrea on 21/11/18.
 */
public abstract class AbstractBackendServiceDelegate {

    private final BackendServiceStatusListener mListener;

    public AbstractBackendServiceDelegate(BackendServiceStatusListener listener) {
        mListener = listener;
    }

    public abstract String getId();

    @StringRes
    public abstract int getBackupCoverMessage();

    @StringRes
    public abstract int getName();

    @StringRes
    public abstract int getBackupCoverAction();

    public abstract boolean isServiceEnabled(Context context);

    /**
     * Registers the activity result launchers this backend needs. A Fragment refuses a
     * registration once its view has been created, so call this from onAttach or onCreate,
     * never from {@link #setup}, which runs on a click. A backend that registers a launcher
     * here needs this called before its {@link #setup}.
     */
    public void registerLaunchers(@NonNull Fragment fragment) {
        // most backends have nothing to register
    }

    public abstract void setup(ComponentActivity activity) throws BackendException;

    public abstract void teardown(ComponentActivity activity) throws BackendException;

    public boolean handlePermissionsResult(Context context, int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        return false;
    }

    public boolean handleActivityResult(Context context, int requestCode, int resultCode, Intent data) {
        return false;
    }

    /**
     * Whether the user can disconnect (forget/revoke) this backend. Most backends can be
     * disconnected; the local external-memory backend cannot, since it holds no session to drop.
     */
    public boolean isDisconnectable() {
        return true;
    }

    /**
     * Whether {@link #setup} can be run again on an enabled backend to pick a different location.
     */
    public boolean isFolderChangeable() {
        return false;
    }

    /**
     * A line naming where this backend stores its backups, or null if it has none to show. It
     * may query another app's provider, so call it off the main thread.
     */
    @Nullable
    public String describeLocation(Context context) {
        return null;
    }

    protected void setBackendServiceEnabled(boolean enabled) {
        if (mListener != null) {
            mListener.onBackendStatusChange(enabled);
        }
    }

    public interface BackendServiceStatusListener {

        void onBackendStatusChange(boolean enabled);
    }
}