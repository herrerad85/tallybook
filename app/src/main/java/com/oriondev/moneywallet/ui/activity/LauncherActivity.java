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

package com.oriondev.moneywallet.ui.activity;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.view.View;
import android.widget.Button;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.broadcast.LocalAction;
import com.oriondev.moneywallet.service.UpgradeLegacyEditionIntentService;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.activity.base.ThemedActivity;
import com.oriondev.moneywallet.ui.view.theme.ITheme;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;
import com.oriondev.moneywallet.utils.SystemBars;

import java.util.Arrays;

/**
 * Created by andrea on 30/07/18.
 */
public class LauncherActivity extends ThemedActivity {

    private static final String SS_UPGRADE_ERROR = "LauncherActivity::SavedState::UpgradeLegacyEditionError";

    private static final int REQUEST_FIRST_START = 273;

    private String mUpgradeLegacyEditionError = null;

    private View mProgressWheel;

    /**
     * Neither screen this activity shows has a toolbar. Both paint the window foreground across the
     * whole window, so that is what is behind both bars and the icons have to be picked for it.
     */
    @Override
    protected int getColorBehindStatusBar(ITheme theme) {
        return theme.getColorWindowForeground();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isMainScreenAtTheBaseOfThisTask()) {
            // the running main screen is left as it was instead of stacking a second one
            finish();
            return;
        }
        if (UpgradeLegacyEditionIntentService.isLegacyEditionDetected(this)) {
            setContentView(R.layout.activity_launcher_legacy_edition_upgrade);
            SystemBars.pad(findViewById(R.id.legacy_upgrade_layout), true, true, true);
            mProgressWheel = findViewById(R.id.progress_wheel);
            // prepare the broadcast receiver
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(LocalAction.ACTION_LEGACY_EDITION_UPGRADE_STARTED);
            intentFilter.addAction(LocalAction.ACTION_LEGACY_EDITION_UPGRADE_FINISHED);
            intentFilter.addAction(LocalAction.ACTION_LEGACY_EDITION_UPGRADE_FAILED);
            LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, intentFilter);
            // start the service
            if (savedInstanceState == null) {
                mProgressWheel.setVisibility(View.INVISIBLE);
                startService(new Intent(this, UpgradeLegacyEditionIntentService.class));
            } else {
                mUpgradeLegacyEditionError = savedInstanceState.getString(SS_UPGRADE_ERROR);
                showUpgradeLegacyEditionErrorMessage();
            }
        } else {
            if (isFirstStart()) {
                setContentView(R.layout.activity_launcher_first_start);
                SystemBars.pad(findViewById(R.id.first_start_layout), true, true, true);
                Button firstStartButton = findViewById(R.id.first_start_button);
                Button restoreBackupButton = findViewById(R.id.restore_backup_button);
                firstStartButton.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(LauncherActivity.this, TutorialActivity.class);
                        startActivityForResult(intent, REQUEST_FIRST_START);
                    }

                });
                restoreBackupButton.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(LauncherActivity.this, BackupListActivity.class);
                        intent.putExtra(BackupListActivity.BACKUP_MODE, BackupListActivity.RESTORE_ONLY);
                        startActivityForResult(intent, REQUEST_FIRST_START);
                    }

                });
            } else {
                startMainActivity();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SS_UPGRADE_ERROR, mUpgradeLegacyEditionError);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
    }

    /**
     * Whether the welcome screen is the right thing to show.
     *
     * The preference cannot answer on its own. shared_prefs is deliberately outside the backup
     * rules because four of the files there hold a secret in the clear, so an Android restore
     * brings the ledger back with the preference missing and the welcome screen has no safe
     * exit: the tutorial inserts a second copy of every default category, and a backup import
     * cleans the attachment folder and renames the restored database away.
     *
     * A wallet is the signal, because a fresh install has none: the database seeds the system
     * categories when it is created and never a wallet. The preference is read first, so the
     * wallets are counted only on a launch that still believes the setup is unfinished.
     *
     * A provider that cannot be reached answers no and records nothing, since
     * {@link MainActivity} is recoverable and the two exits from the welcome screen are not.
     */
    private boolean isFirstStart() {
        if (PreferenceManager.isFirstStartDone()) {
            return false;
        }
        int wallets = walletCount();
        if (wallets == 0) {
            return true;
        }
        if (wallets > 0) {
            PreferenceManager.setIsFirstStartDone(true);
        }
        return false;
    }

    /**
     * The number of wallets, or -1 when the provider could not be reached at all.
     */
    private int walletCount() {
        try (Cursor cursor = getContentResolver().query(DataContentProvider.CONTENT_WALLETS,
                new String[]{Contract.Wallet.ID}, null, null, null)) {
            return cursor != null ? cursor.getCount() : -1;
        }
    }

    @SuppressWarnings("deprecation")
    private boolean isMainScreenAtTheBaseOfThisTask() {
        if (isTaskRoot()) {
            return false;
        }
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.AppTask task : manager.getAppTasks()) {
            ActivityManager.RecentTaskInfo info;
            try {
                info = task.getTaskInfo();
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (info.persistentId == getTaskId()) {
                return info.baseActivity != null && MainActivity.class.getName().equals(info.baseActivity.getClassName());
            }
        }
        return false;
    }

    private void startMainActivity() {
        startActivity(new Intent(this, MainActivity.class));
        publishShortcuts();
        finish();
    }

    /**
     * Publish the launcher shortcuts. Every route into the app passes through
     * {@link #startMainActivity()}, which is why they are published from there, and after the
     * hand off rather than before it so that publishing them is never in front of startup.
     *
     * The intents are built from the activity classes, so the package is this build's own in every
     * variant. The static definition these replace named the release application id as a literal,
     * which is not the package of any build carrying an applicationIdSuffix.
     *
     * The rank is the ordering decision made when these were last touched: the launcher sorts by
     * rank ascending, and recording a transaction is what people open this app to do, while a
     * transfer between their own wallets is the rarer of the two.
     *
     * The ids are the ones the static definition used, so a pinned copy of either is addressed by
     * the same id rather than becoming a second entry beside it.
     */
    private void publishShortcuts() {
        ShortcutInfoCompat transaction = new ShortcutInfoCompat.Builder(this, "fast_transaction")
                .setShortLabel(getString(R.string.title_activity_new_transaction))
                .setLongLabel(getString(R.string.title_activity_new_transaction))
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_add_24dp))
                .setIntent(new Intent(Intent.ACTION_VIEW, null, this, NewEditTransactionActivity.class))
                .setRank(0)
                .build();
        ShortcutInfoCompat transfer = new ShortcutInfoCompat.Builder(this, "fast_transfer")
                .setShortLabel(getString(R.string.title_activity_new_transfer))
                .setLongLabel(getString(R.string.title_activity_new_transfer))
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_transfer_24dp))
                .setIntent(new Intent(Intent.ACTION_VIEW, null, this, NewEditTransferActivity.class))
                .setRank(1)
                .build();
        ShortcutManagerCompat.addDynamicShortcuts(this, Arrays.asList(transaction, transfer));
    }

    private void showUpgradeLegacyEditionErrorMessage() {
        if (mProgressWheel != null) {
            mProgressWheel.setVisibility(View.INVISIBLE);
        }
        // both buttons do the same thing, as the onAny callback this replaces did
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                startMainActivity();
            }

        };
        ThemedDialog.buildMaterialDialog(LauncherActivity.this)
                .setTitle(R.string.title_failed)
                .setMessage(getString(R.string.message_error_legacy_upgrade_failed, mUpgradeLegacyEditionError))
                .setPositiveButton(android.R.string.ok, listener)
                .setNegativeButton(android.R.string.cancel, listener)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_FIRST_START) {
            if (resultCode == RESULT_OK) {
                PreferenceManager.setIsFirstStartDone(true);
                startMainActivity();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getAction() != null) {
                switch (intent.getAction()) {
                    case LocalAction.ACTION_LEGACY_EDITION_UPGRADE_STARTED:
                        if (mProgressWheel != null) {
                            mProgressWheel.setVisibility(View.VISIBLE);
                        }
                        break;
                    case LocalAction.ACTION_LEGACY_EDITION_UPGRADE_FINISHED:
                        startMainActivity();
                        break;
                    case LocalAction.ACTION_LEGACY_EDITION_UPGRADE_FAILED:
                        mUpgradeLegacyEditionError = intent.getStringExtra(UpgradeLegacyEditionIntentService.ERROR_MESSAGE);
                        showUpgradeLegacyEditionErrorMessage();
                        break;
                }
            }
        }

    };
}