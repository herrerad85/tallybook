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

package com.oriondev.moneywallet.storage.preference;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by andrea on 04/12/18.
 */
public class BackendManager {

    private static final String FILE_NAME = "backend_preferences";

    private static final String AUTO_BACKUP_ENABLED_SERVICES = "auto_backup_enabled_services";
    private static final String BACKEND_AUTO_BACKUP_ENABLED = "auto_backup_enabled_";
    private static final String BACKEND_AUTO_BACKUP_WIFI_ONLY = "auto_backup_wifi_only_";
    private static final String BACKEND_AUTO_BACKUP_DATA_CHANGED_ONLY = "auto_backup_data_changed_only_";
    private static final String BACKEND_AUTO_BACKUP_EXPORT_CSV = "auto_backup_export_csv_";
    private static final String BACKEND_AUTO_BACKUP_OFFSET = "auto_backup_hour_offset_";
    private static final String BACKEND_AUTO_BACKUP_FOLDER = "auto_backup_folder_";
    private static final String BACKEND_AUTO_BACKUP_PASSWORD = "auto_backup_password_";
    private static final String BACKEND_AUTO_BACKUP_LAST_TIME = "auto_backup_last_time_";
    private static final String BACKEND_AUTO_BACKUP_DISABLED_BY_FAILURE = "auto_backup_disabled_by_failure_";

    private static SharedPreferences mPreferences;

    public static void initialize(Context context) {
        mPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Turns automatic backup off for a backend because a backup, a restore or a listing failed in
     * a way the app treats as not recoverable, and records that the app is the one that turned it
     * off. Nothing else sets that flag, so a user switching the service off in the settings dialog
     * is not mistaken for a failure.
     *
     * The sweep builds its backend before the block that calls this, so a backend that cannot be
     * built at all, a released storage grant or a cleared server address, never gets here from
     * the sweep, and stays enabled while its notification says otherwise. The manual backup,
     * restore and browse path builds its backend inside the block, so the same failure does get
     * here from there.
     *
     * The flag is what the settings dialog has to work with. The saved folder and password survive
     * a failure, so the dialog reopens showing them, and without this it cannot tell the two
     * states apart: a backend the user switched off and a backend a failure switched off look
     * identical in these preferences.
     *
     * Only recorded for a backend that had automatic backup on. The manual backup, restore and
     * browse paths share the caller in {@link
     * com.oriondev.moneywallet.service.BackupHandlerIntentService}, and browsing a backend runs a
     * listing every time that screen resumes, so without this a backend the user never turned
     * automatic backup on for would report that a failure turned it off.
     */
    public static void disableAutoBackupAfterFailure(String backendId) {
        if (isAutoBackupEnabled(backendId)) {
            mPreferences.edit().putBoolean(BACKEND_AUTO_BACKUP_DISABLED_BY_FAILURE + backendId, true).apply();
        }
        setAutoBackupEnabled(backendId, false);
    }

    public static boolean isAutoBackupDisabledByFailure(String backendId) {
        return mPreferences.getBoolean(BACKEND_AUTO_BACKUP_DISABLED_BY_FAILURE + backendId, false);
    }

    public static void setAutoBackupEnabled(String backendId, boolean enabled) {
        mPreferences.edit().putBoolean(BACKEND_AUTO_BACKUP_ENABLED + backendId, enabled).apply();
        Set<String> backendIdSet = mPreferences.getStringSet(AUTO_BACKUP_ENABLED_SERVICES, null);
        if (enabled) {
            if (backendIdSet == null) {
                backendIdSet = new HashSet<>();
            }
            backendIdSet.add(backendId);
            // Turning the service on is what forgets the failure that turned it off. The record
            // describes a service that is off, so keeping it past this point would let it be
            // shown against a service that is running.
            mPreferences.edit()
                    .remove(BACKEND_AUTO_BACKUP_DISABLED_BY_FAILURE + backendId)
                    .apply();
        } else if (backendIdSet != null) {
            backendIdSet.remove(backendId);
            // The folder and the password stay. This is reached from the failure path above as
            // well as from the settings dialog, and the failure path never writes them back, so
            // erasing them threw away what the user chose when a backup, a restore or a listing
            // failed. The last run time still goes, because
            // AutoBackupJobService leans on it being cleared, skipping the write back for a
            // backend a failure has just disabled so that switching the backend on again does
            // not fire a backup at once.
            mPreferences.edit()
                    .remove(BACKEND_AUTO_BACKUP_LAST_TIME + backendId)
                    .apply();
        }
        mPreferences.edit().putStringSet(AUTO_BACKUP_ENABLED_SERVICES, backendIdSet).apply();
    }

    public static void setAutoBackupOnWiFiOnly(String backendId, boolean wifiOnly) {
        mPreferences.edit().putBoolean(BACKEND_AUTO_BACKUP_WIFI_ONLY + backendId, wifiOnly).apply();
    }

    public static void setAutoBackupExportCsv(String backendId, boolean exportCsv) {
        mPreferences.edit().putBoolean(BACKEND_AUTO_BACKUP_EXPORT_CSV + backendId, exportCsv).apply();
    }

    public static void setAutoBackupWhenDataIsChangedOnly(String backendId, boolean dataChangedOnly) {
        mPreferences.edit().putBoolean(BACKEND_AUTO_BACKUP_DATA_CHANGED_ONLY + backendId, dataChangedOnly).apply();
    }

    public static void setAutoBackupHoursOffset(String backendId, int offset) {
        mPreferences.edit().putInt(BACKEND_AUTO_BACKUP_OFFSET + backendId, offset).apply();
    }

    public static void setAutoBackupFolder(String backendId, String folder) {
        mPreferences.edit().putString(BACKEND_AUTO_BACKUP_FOLDER + backendId, folder).apply();
    }

    public static void setAutoBackupPassword(String backendId, String password) {
        mPreferences.edit().putString(BACKEND_AUTO_BACKUP_PASSWORD + backendId, password).apply();
    }

    public static void setAutoBackupLastTime(String backendId, long timestamp) {
        mPreferences.edit().putLong(BACKEND_AUTO_BACKUP_LAST_TIME + backendId, timestamp).apply();
    }

    public static Set<String> getAutoBackupEnabledServices() {
        return mPreferences.getStringSet(AUTO_BACKUP_ENABLED_SERVICES, null);
    }

    public static boolean isAutoBackupEnabled(String backendId) {
        return mPreferences.getBoolean(BACKEND_AUTO_BACKUP_ENABLED + backendId, false);
    }

    public static boolean isAutoBackupOnWiFiOnly(String backendId) {
        return mPreferences.getBoolean(BACKEND_AUTO_BACKUP_WIFI_ONLY + backendId, false);
    }

    public static boolean isAutoBackupExportCsv(String backendId) {
        return mPreferences.getBoolean(BACKEND_AUTO_BACKUP_EXPORT_CSV + backendId, false);
    }

    public static boolean isAutoBackupWhenDataIsChangedOnly(String backendId) {
        return mPreferences.getBoolean(BACKEND_AUTO_BACKUP_DATA_CHANGED_ONLY + backendId, true);
    }

    public static int getAutoBackupHoursOffset(String backendId) {
        return mPreferences.getInt(BACKEND_AUTO_BACKUP_OFFSET + backendId, 48);
    }

    public static String getAutoBackupFolder(String backendId) {
        return mPreferences.getString(BACKEND_AUTO_BACKUP_FOLDER + backendId, null);
    }

    public static String getAutoBackupPassword(String backendId) {
        return mPreferences.getString(BACKEND_AUTO_BACKUP_PASSWORD + backendId, null);
    }

    public static long getAutoBackupLastTime(String backendId) {
        if (!mPreferences.contains(BACKEND_AUTO_BACKUP_LAST_TIME + backendId)) {
            // store the current timestamp to avoid that a recent rescheduling of the auto backup
            // service moves the next occurrence to far
            mPreferences.edit().putLong(BACKEND_AUTO_BACKUP_LAST_TIME + backendId, System.currentTimeMillis()).apply();
        }
        return mPreferences.getLong(BACKEND_AUTO_BACKUP_LAST_TIME + backendId, System.currentTimeMillis());
    }
}