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

package com.oriondev.moneywallet.api.disk;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.api.AbstractBackendServiceDelegate;
import com.oriondev.moneywallet.api.BackendException;
import com.oriondev.moneywallet.api.BackendServiceFactory;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

/**
 * Created by andrea on 21/11/18.
 */
public class DiskBackendService extends AbstractBackendServiceDelegate {

    private ActivityResultLauncher<String> mPermissionLauncher;

    public DiskBackendService(BackendServiceStatusListener listener) {
        super(listener);
    }

    @Override
    public String getId() {
        return BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY;
    }

    @Override
    public int getName() {
        return R.string.service_backup_external_memory;
    }

    @Override
    public int getBackupCoverMessage() {
        return R.string.cover_message_backup_external_memory_title;
    }

    @Override
    public int getBackupCoverAction() {
        return R.string.cover_message_backup_external_memory_button;
    }

    /**
     * From Android 13 the platform denies WRITE_EXTERNAL_STORAGE to an app targeting 33 or
     * higher without showing any dialog, so gating on it there leaves the cover screen up
     * forever and {@link #setup} can never clear it.
     */
    @Override
    public boolean isServiceEnabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        String permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
        int result = ContextCompat.checkSelfPermission(context, permission);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public boolean isDisconnectable() {
        // The local external-memory backend cannot be disconnected: there is no remote session
        // or persisted grant to revoke, only a runtime storage permission.
        return false;
    }

    @Override
    public void registerLaunchers(@NonNull final Fragment fragment) {
        mPermissionLauncher = fragment.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                new ActivityResultCallback<Boolean>() {
                    @Override
                    public void onActivityResult(Boolean isGranted) {
                        setBackendServiceEnabled(isGranted);
                        if (!isGranted) {
                            ThemedDialog.buildMaterialDialog(fragment.requireActivity())
                                    .setTitle(R.string.title_warning)
                                    .setMessage(R.string.message_permission_required_not_granted)
                                    .show();
                        }
                    }
                }
        );
    }

    @Override
    public void setup(final ComponentActivity activity) throws BackendException {
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            ThemedDialog.buildMaterialDialog(activity)
                    .setTitle(R.string.title_request_permission)
                    .setMessage(R.string.message_permission_required_external_storage)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                        }

                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else {
            mPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    @Override
    public void teardown(ComponentActivity activity) throws BackendException {
        // action not supported: cannot revoke storage permission
    }

    @Override
    public boolean handlePermissionsResult(Context context, int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // Do nothing. This is handled by the ActivityResultCallback.
        return false;
    }
}