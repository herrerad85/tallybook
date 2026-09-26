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

package com.oriondev.moneywallet.ui.fragment.dialog;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.widget.SwitchCompat;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.api.BackendServiceFactory;
import com.oriondev.moneywallet.api.saf.SAFBackendService;
import com.oriondev.moneywallet.broadcast.AutoBackupBroadcastReceiver;
import com.oriondev.moneywallet.model.IFile;
import com.oriondev.moneywallet.storage.preference.BackendManager;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.activity.BackendExplorerActivity;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;
import com.oriondev.moneywallet.utils.Utils;

/**
 * Created by andrea on 26/11/18.
 */
public class AutoBackupSettingDialog extends DialogFragment {

    private static final String SS_BACKEND_ID = "AutoBackupSettingDialog::SavedState::BackendId";
    private static final String SS_FOLDER = "AutoBackupSettingDialog::SavedState::Folder";

    private static final int REQUEST_CODE_FOLDER_PICKER = 35625;

    private static final int OFFSET_MIN_HOURS = 24;
    private static final int OFFSET_MAX_HOURS = 168;
    private static final int OFFSET_BETWEEN_HOURS = 4;

    private static final int REQUEST_CODE_NOTIFICATION_PERMISSION = 35626;

    private String mBackendId;

    private SwitchCompat mServiceEnabledSwitchCompat;
    private CheckBox mOnlyWiFiCheckBox;
    private CheckBox mOnlyDataChangedCheckBox;
    private CheckBox mExportCsvCheckBox;
    private TextView mOffsetTextView;
    private SeekBar mOffsetSeekBar;
    private TextView mFolderTextView;
    private TextView mFailureTextView;
    private EditText mPasswordEditText;
    private TextView mCsvPasswordTextView;

    private IFile mFolder;

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Activity activity = getActivity();
        if (activity == null) {
            return super.onCreateDialog(savedInstanceState);
        }
        if (savedInstanceState != null) {
            mBackendId = savedInstanceState.getString(SS_BACKEND_ID);
            mFolder = savedInstanceState.getParcelable(SS_FOLDER);
        }
        View view = ThemedDialog.inflateScrollableView(activity, R.layout.dialog_auto_backup_setting);
        AlertDialog dialog = ThemedDialog.buildMaterialDialog(activity)
                .setTitle(R.string.dialog_auto_backup_title)
                .setView(view)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        mServiceEnabledSwitchCompat = view.findViewById(R.id.auto_backup_enable_switch);
        mOnlyWiFiCheckBox = view.findViewById(R.id.auto_backup_wifi_check_box);
        mOnlyDataChangedCheckBox = view.findViewById(R.id.auto_backup_data_change_check_box);
        mExportCsvCheckBox = view.findViewById(R.id.auto_backup_export_csv_check_box);
        mOffsetTextView = view.findViewById(R.id.auto_backup_offset_text_view);
        mOffsetSeekBar = view.findViewById(R.id.auto_backup_offset_seek_bar);
        mFolderTextView = view.findViewById(R.id.auto_backup_folder_text_view);
        mFailureTextView = view.findViewById(R.id.auto_backup_failure_text_view);
        mPasswordEditText = view.findViewById(R.id.auto_backup_password_edit_text);
        mCsvPasswordTextView = view.findViewById(R.id.auto_backup_csv_password_text_view);
        // set listeners
        mOffsetSeekBar.setMax((OFFSET_MAX_HOURS - OFFSET_MIN_HOURS) / OFFSET_BETWEEN_HOURS);
        mOffsetSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                AutoBackupSettingDialog.this.onProgressChanged(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // not used
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // not used
            }

        });
        mServiceEnabledSwitchCompat.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                AutoBackupSettingDialog.this.onServiceEnabledChanged();
            }

        });
        mExportCsvCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                AutoBackupSettingDialog.this.onCsvPasswordChanged();
            }

        });
        mPasswordEditText.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // not used
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // not used
            }

            @Override
            public void afterTextChanged(Editable s) {
                AutoBackupSettingDialog.this.onCsvPasswordChanged();
            }

        });
        mFolderTextView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Activity activity = getActivity();
                if (activity != null) {
                    Intent intent = new Intent(activity, BackendExplorerActivity.class);
                    intent.putExtra(BackendExplorerActivity.BACKEND_ID, mBackendId);
                    startActivityForResult(intent, REQUEST_CODE_FOLDER_PICKER);
                }
            }

        });
        if (savedInstanceState == null) {
            mServiceEnabledSwitchCompat.setChecked(BackendManager.isAutoBackupEnabled(mBackendId));
            mOnlyWiFiCheckBox.setChecked(BackendManager.isAutoBackupOnWiFiOnly(mBackendId));
            mOnlyDataChangedCheckBox.setChecked(BackendManager.isAutoBackupWhenDataIsChangedOnly(mBackendId));
            mExportCsvCheckBox.setChecked(BackendManager.isAutoBackupExportCsv(mBackendId));
            mOffsetSeekBar.setProgress((BackendManager.getAutoBackupHoursOffset(mBackendId) - OFFSET_MIN_HOURS) / OFFSET_BETWEEN_HOURS);
            mFolder = BackendServiceFactory.getFile(mBackendId, BackendManager.getAutoBackupFolder(mBackendId));
            mPasswordEditText.setText(BackendManager.getAutoBackupPassword(mBackendId));
        }
        if (SAFBackendService.isOutsideCurrentFolder(activity, mFolder)) {
            mFolder = null;
        }
        onProgressChanged(mOffsetSeekBar.getProgress());
        onFolderChanged();
        onServiceEnabledChanged();
        onCsvPasswordChanged();
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        final AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) {
            return;
        }
        // OK does not close the dialog by itself, so a setting it refuses to save leaves the rest
        // of what was typed on screen. Replacing the listener on the button is what drops the
        // dismiss that comes with it, and the button only exists once the dialog has been shown.
        // Tapping outside and the back button are not affected and still cancel.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (onSaveSetting()) {
                    dialog.dismiss();
                }
            }

        });
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SS_BACKEND_ID, mBackendId);
        outState.putParcelable(SS_FOLDER, mFolder);
    }

    public void show(FragmentManager fragmentManager, String tag, String backendId) {
        mBackendId = backendId;
        show(fragmentManager, tag);
    }

    private void onProgressChanged(int progress) {
        int hours = OFFSET_MIN_HOURS + (progress * OFFSET_BETWEEN_HOURS);
        mOffsetTextView.setText(getString(R.string.hint_auto_backup_every_n_hours, hours));
    }

    /**
     * Shows the message about a failure having turned the service off when the switch reads off
     * and a failure is on record for this backend. The switch on screen decides it and not the
     * stored setting, so the message is never shown while the switch reads on.
     */
    private void onServiceEnabledChanged() {
        boolean show = !mServiceEnabledSwitchCompat.isChecked()
                && BackendManager.isAutoBackupDisabledByFailure(mBackendId);
        mFailureTextView.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void onCsvPasswordChanged() {
        boolean show = mExportCsvCheckBox.isChecked() && mPasswordEditText.getText().length() > 0;
        mCsvPasswordTextView.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /**
     * A folder that is stored but cannot be read comes back from
     * {@link BackendServiceFactory#getFile} as null, and so does one that is not stored when the
     * backend has no default folder. The two do not get the same line. The notification the sweep
     * posts already separates them, and a row reading "not chosen" under a notification saying
     * the location is gone would be the app arguing with itself.
     */
    private void onFolderChanged() {
        if (mFolder != null) {
            mFolderTextView.setText(mFolder.getName());
        } else if (BackendManager.getAutoBackupFolder(mBackendId) != null) {
            mFolderTextView.setText(R.string.hint_auto_backup_folder_unavailable);
        } else {
            mFolderTextView.setText(R.string.hint_auto_backup_folder_not_chosen);
        }
    }

    /**
     * Saves the settings, or refuses and returns false. The only other writer of a backup folder
     * is a Local folder change, which clears one only while turning the backend off, and the only
     * other way a backend's enabled state changes is
     * {@link BackendManager#disableAutoBackupAfterFailure}, which turns it off, so nothing outside
     * this screen can leave a backend enabled with no folder.
     *
     * A backend with no usable folder is what the refusal is about. Some backends offer a default
     * one and {@link BackendServiceFactory#getFile} hands it back for a folder that is not stored,
     * which is what fills in {@code mFolder} when this dialog opens, so a null here means there is
     * no folder to back up to and none to fall back on. Saved enabled, that backend can only fail:
     * the sweep asks for the same file, gets null, and reports a failed backup instead.
     */
    private boolean onSaveSetting() {
        // the Local folder can be disconnected while this dialog is open
        if (SAFBackendService.isOutsideCurrentFolder(requireContext(), mFolder)) {
            mFolder = null;
            onFolderChanged();
        }
        if (mServiceEnabledSwitchCompat.isChecked() && mFolder == null) {
            Toast.makeText(getContext(), R.string.message_auto_backup_folder_required, Toast.LENGTH_LONG).show();
            return false;
        }
        BackendManager.setAutoBackupEnabled(mBackendId, mServiceEnabledSwitchCompat.isChecked());
        BackendManager.setAutoBackupOnWiFiOnly(mBackendId, mOnlyWiFiCheckBox.isChecked());
        BackendManager.setAutoBackupWhenDataIsChangedOnly(mBackendId, mOnlyDataChangedCheckBox.isChecked());
        BackendManager.setAutoBackupExportCsv(mBackendId, mExportCsvCheckBox.isChecked());
        BackendManager.setAutoBackupHoursOffset(mBackendId, OFFSET_MIN_HOURS + (mOffsetSeekBar.getProgress() * OFFSET_BETWEEN_HOURS));
        BackendManager.setAutoBackupFolder(mBackendId, mFolder != null ? mFolder.encodeToString() : null);
        BackendManager.setAutoBackupPassword(mBackendId, mPasswordEditText.getText().toString());
        AutoBackupBroadcastReceiver.scheduleAutoBackupTask(getActivity());
        Activity activity = getActivity();
        if (mServiceEnabledSwitchCompat.isChecked() && activity != null
                && Utils.shouldAskNotificationPermission(activity)) {
            // routed through the activity rather than a launcher on this fragment. this click
            // dismisses the dialog on its way out, so a launcher owned by it is unregistered
            // before the user can answer and the result would go nowhere. the result is
            // discarded either way here, and the ask is recorded before it is made.
            PreferenceManager.setAskedNotificationPermission();
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_CODE_NOTIFICATION_PERMISSION);
        }
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_FOLDER_PICKER) {
            if (resultCode == Activity.RESULT_OK) {
                mFolder = data.getParcelableExtra(BackendExplorerActivity.RESULT_FILE);
                onFolderChanged();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}