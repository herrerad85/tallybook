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

package com.oriondev.moneywallet.ui.fragment.secondary;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.format.DateUtils;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.biometric.BiometricManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.broadcast.LocalAction;
import com.oriondev.moneywallet.model.LockMode;
import com.oriondev.moneywallet.service.AbstractCurrencyRateDownloadIntentService;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.utils.SystemBars;
import com.oriondev.moneywallet.ui.activity.CategoryRuleListActivity;
import com.oriondev.moneywallet.ui.activity.CurrencyListActivity;
import com.oriondev.moneywallet.ui.activity.LockActivity;
import com.oriondev.moneywallet.ui.preference.ThemedInputPreference;
import com.oriondev.moneywallet.ui.preference.ThemedListPreference;
import com.oriondev.moneywallet.utils.DateFormatter;
import com.oriondev.moneywallet.utils.Urls;
import com.oriondev.moneywallet.utils.Utils;
import com.oriondev.moneywallet.view.MapViewWrapper;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Created by andrea on 07/03/18.
 */
public class UtilitySettingFragment extends PreferenceFragmentCompat {

    private static final int REQUEST_CODE_LOCK_ACTIVITY = 8239;

    private static final int HOURS_IN_DAY = 24;

    /**
     * A fragment has to register before it is created, so this cannot move to the point of use.
     */
    private final ActivityResultLauncher<String> mNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // nothing to do either way. the reminder is scheduled on a denial too, its
                // alarm just fires into nothing, and the ask is already recorded as spent.
            });

    private ThemedListPreference mDailyReminderPreference;
    private ThemedListPreference mSecurityModeListPreference;
    private Preference mSecurityModeChangeKeyPreference;
    private ThemedListPreference mExchangeRateServiceListPreference;
    private ThemedInputPreference mExchangeRateCustomApiKey;
    private ThemedInputPreference mMapTileServerPreference;
    private Preference mExchangeRateUpdatePreference;
    private Preference mCurrencyManagementPreference;
    private Preference mCategoryRulesPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Activity activity = getActivity();
        if (activity != null) {
            LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(activity);
            broadcastManager.registerReceiver(mLocalBroadcastReceiver, new IntentFilter(LocalAction.ACTION_EXCHANGE_RATES_UPDATED));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Activity activity = getActivity();
        if (activity != null) {
            LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(activity);
            broadcastManager.unregisterReceiver(mLocalBroadcastReceiver);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings_utility);
        mDailyReminderPreference = (ThemedListPreference) findPreference("daily_reminder");
        mSecurityModeListPreference = (ThemedListPreference) findPreference("security_mode");
        mSecurityModeChangeKeyPreference = findPreference("security_change_key");
        mExchangeRateServiceListPreference = (ThemedListPreference) findPreference("exchange_rate_source");
        mExchangeRateCustomApiKey = (ThemedInputPreference) findPreference("exchange_rate_api_key");
        mExchangeRateUpdatePreference = findPreference("exchange_rate_update");
        mCurrencyManagementPreference = findPreference("currency_management");
        mCategoryRulesPreference = findPreference("category_rules");
        mMapTileServerPreference = (ThemedInputPreference) findPreference("map_tile_server");
        if (!MapViewWrapper.supportsCustomTileServer()) {
            PreferenceCategory mapCategory = (PreferenceCategory) findPreference("map_category");
            if (mapCategory != null) {
                getPreferenceScreen().removePreference(mapCategory);
            }
            mMapTileServerPreference = null;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // setup preference logic
        // must precede setupCurrentDailyReminder below: setting the value looks it up in the
        // entry values, and onResume is too late because it runs after this
        setupDailyReminderEntries();
        if (isFingerprintAuthSupported(getActivity())) {
            mSecurityModeListPreference.setEntries(new String[] {
                    getString(R.string.setting_item_security_none),
                    getString(R.string.setting_item_security_pin),
                    getString(R.string.setting_item_security_sequence),
                    getString(R.string.setting_item_security_fingerprint)
            });
            mSecurityModeListPreference.setEntryValues(new String[] {
                    String.valueOf(PreferenceManager.LOCK_MODE_NONE),
                    String.valueOf(PreferenceManager.LOCK_MODE_PIN),
                    String.valueOf(PreferenceManager.LOCK_MODE_SEQUENCE),
                    String.valueOf(PreferenceManager.LOCK_MODE_FINGERPRINT)
            });
        } else {
            mSecurityModeListPreference.setEntries(new String[] {
                    getString(R.string.setting_item_security_none),
                    getString(R.string.setting_item_security_pin),
                    getString(R.string.setting_item_security_sequence)
            });
            mSecurityModeListPreference.setEntryValues(new String[] {
                    String.valueOf(PreferenceManager.LOCK_MODE_NONE),
                    String.valueOf(PreferenceManager.LOCK_MODE_PIN),
                    String.valueOf(PreferenceManager.LOCK_MODE_SEQUENCE)
            });
        }
        mExchangeRateServiceListPreference.setEntries(new String[] {
                getString(R.string.setting_item_utility_exchange_rates_service_oer)
        });
        mExchangeRateServiceListPreference.setEntryValues(new String[] {
                String.valueOf(PreferenceManager.SERVICE_OPEN_EXCHANGE_RATE)
        });
        // setup current (or default) values
        setupCurrentDailyReminder();
        setupCurrentLockMode();
        setupCurrentMapTileServer();
        setupCurrentExchangeRateService();
        setupCurrentExchangeRateCustomApiKey();
        setupCurrentExchangeRateUpdate();
        // attach a listener to get notified when values changes
        mDailyReminderPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int hour = Integer.parseInt((String) newValue);
                PreferenceManager.setCurrentDailyReminder(getActivity(), hour);
                setupCurrentDailyReminder();
                if (hour != PreferenceManager.DAILY_REMINDER_DISABLED) {
                    requestNotificationPermission();
                }
                return false;
            }

        });
        mSecurityModeListPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                String oldValue = ((ThemedListPreference) preference).getValue();
                String value = (String) newValue;
                if (TextUtils.equals(oldValue, value)) {
                    // the value is not changed
                    return false;
                }
                Intent intent = null;
                int integerValue = Integer.parseInt(value);
                switch (integerValue) {
                    case PreferenceManager.LOCK_MODE_NONE:
                        intent = LockActivity.disableLock(getActivity());
                        break;
                    case PreferenceManager.LOCK_MODE_PIN:
                    case PreferenceManager.LOCK_MODE_SEQUENCE:
                    case PreferenceManager.LOCK_MODE_FINGERPRINT:
                        if (Integer.parseInt(oldValue) == PreferenceManager.LOCK_MODE_NONE) {
                            intent = LockActivity.enableLock(getActivity(), LockMode.get(integerValue));
                        } else {
                            intent = LockActivity.changeMode(getActivity(), LockMode.get(integerValue));
                        }
                        break;
                }
                if (intent != null) {
                    startActivityForResult(intent, REQUEST_CODE_LOCK_ACTIVITY);
                }
                return false;
            }

        });
        mSecurityModeChangeKeyPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(LockActivity.changeKey(getActivity()));
                return false;
            }

        });
        mExchangeRateServiceListPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int index = Integer.parseInt((String) newValue);
                PreferenceManager.setCurrentExchangeRateService(index);
                setupCurrentExchangeRateService();
                setupCurrentExchangeRateCustomApiKey();
                return false;
            }

        });
        mExchangeRateCustomApiKey.setInput(R.string.setting_item_utility_exchange_rates_custom_api_key_hint, true, InputType.TYPE_CLASS_TEXT);
        mExchangeRateCustomApiKey.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int service = PreferenceManager.getCurrentExchangeRateService();
                PreferenceManager.setServiceApiKey(service, (String) newValue);
                setupCurrentExchangeRateCustomApiKey();
                return false;
            }

        });
        if (mMapTileServerPreference != null) {
            mMapTileServerPreference.setInput(R.string.setting_hint_map_tile_server, true, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            mMapTileServerPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String url = newValue != null ? ((String) newValue).trim() : null;
                    if (!TextUtils.isEmpty(url) && !Urls.isUsableTileAddress(url)) {
                        // rejected here because at map load a bad address is simply ignored, so
                        // the map quietly keeps using the default and the setting looks applied
                        mMapTileServerPreference.setSummary(R.string.setting_summary_map_tile_server_invalid);
                        // the widget keeps whatever was typed, and that value is both the
                        // dialog's prefill and how it decides something changed. Put the stored one
                        // back, or retyping the same bad address is silently a no op
                        mMapTileServerPreference.setCurrentValue(PreferenceManager.getMapTileServer());
                        return false;
                    }
                    PreferenceManager.setMapTileServer(url);
                    setupCurrentMapTileServer();
                    return false;
                }

            });
            setupCurrentMapTileServer();
        }
        mExchangeRateUpdatePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                Activity activity = getActivity();
                if (activity != null) {
                    Intent intent = AbstractCurrencyRateDownloadIntentService.buildIntent(activity);
                    activity.startService(intent);
                }
                return false;
            }

        });
        mCurrencyManagementPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                Activity activity = getActivity();
                if (activity != null) {
                    Intent intent = new Intent(activity, CurrencyListActivity.class);
                    intent.putExtra(CurrencyListActivity.ACTIVITY_MODE, CurrencyListActivity.CURRENCY_MANAGER);
                    startActivity(intent);
                }
                return false;
            }

        });
        mCategoryRulesPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                Activity activity = getActivity();
                if (activity != null) {
                    startActivity(new Intent(activity, CategoryRuleListActivity.class));
                }
                return false;
            }

        });
    }

    @Override
    public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        RecyclerView recyclerView = super.onCreateRecyclerView(inflater, parent, savedInstanceState);
        recyclerView.setPadding(0, 0, 0, 0);
        // After the reset above, not before it, or the navigation bar clearance is the
        // padding that gets zeroed.
        SystemBars.pad(recyclerView,
                false, getResources().getBoolean(R.bool.secondary_panel_fills_window), true);
        return recyclerView;
    }

    private boolean isFingerprintAuthSupported(Context context) {
        // Hardware present and usable, or present but not yet enrolled (the lock screen then
        // guides the user to set up a fingerprint). Mirrors the old isHardwareDetected() semantics.
        int status = BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG);
        return status == BiometricManager.BIOMETRIC_SUCCESS
                || status == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED;
    }

    @Override
    public void onResume() {
        super.onResume();
        // the 12 or 24 hour choice lives in the system settings and changing it is not a
        // configuration change, so nothing recreates this screen on the way back
        setupDailyReminderEntries();
        setupCurrentDailyReminder();
        // this row renders a time too, from the same place, so leaving it out would have the two
        // rows on one screen disagreeing about the format
        setupCurrentExchangeRateUpdate();
    }

    /**
     * The hours were written out as fixed 24 hour strings, which read wrong on a device set to 12
     * hour and in Persian, which does not use Latin digits. The pattern comes from DateFormatter so
     * this row follows the same decision as every other time in the app rather than deriving it
     * again from the platform.
     */
    private void setupDailyReminderEntries() {
        String[] entries = new String[HOURS_IN_DAY + 1];
        String[] values = new String[HOURS_IN_DAY + 1];
        entries[0] = getString(R.string.setting_item_daily_reminder_none);
        values[0] = String.valueOf(PreferenceManager.DAILY_REMINDER_DISABLED);
        DateFormat format = hourOfDayFormat();
        for (int hour = 0; hour < HOURS_IN_DAY; hour++) {
            entries[hour + 1] = formatHourOfDay(format, hour);
            values[hour + 1] = String.valueOf(hour);
        }
        mDailyReminderPreference.setEntries(entries);
        mDailyReminderPreference.setEntryValues(values);
    }

    /**
     * Formatted in UTC deliberately. These are hours of a day rather than moments in time, and
     * putting an hour into a local calendar means asking for a wall time that does not exist on the
     * morning the clocks go forward. Which hour depends on the zone: where the shift is at 02:00,
     * that hour vanishes from the list and 03:00 appears twice, once a year.
     */
    private static DateFormat hourOfDayFormat() {
        SimpleDateFormat format = new SimpleDateFormat(DateFormatter.getTimePattern(), Locale.getDefault());
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format;
    }

    private static String formatHourOfDay(DateFormat format, int hour) {
        return format.format(new Date(hour * DateUtils.HOUR_IN_MILLIS));
    }

    private void requestNotificationPermission() {
        Activity activity = getActivity();
        if (activity != null && Utils.shouldAskNotificationPermission(activity)) {
            PreferenceManager.setAskedNotificationPermission();
            mNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void setupCurrentDailyReminder() {
        int hour = PreferenceManager.getCurrentDailyReminder();
        mDailyReminderPreference.setValue(String.valueOf(hour));
        if (hour == PreferenceManager.DAILY_REMINDER_DISABLED) {
            mDailyReminderPreference.setSummary(R.string.setting_item_daily_reminder_none);
        } else {
            String summary = getString(R.string.setting_summary_daily_reminder, formatHourOfDay(hourOfDayFormat(), hour));
            mDailyReminderPreference.setSummary(summary);
        }
    }

    private void setupCurrentLockMode() {
        LockMode lockMode = PreferenceManager.getCurrentLockMode();
        mSecurityModeListPreference.setValue(lockMode.getValueAsString());
        switch (lockMode) {
            case NONE:
                mSecurityModeListPreference.setSummary(R.string.setting_item_security_none);
                mSecurityModeChangeKeyPreference.setVisible(false);
                break;
            case PIN:
                mSecurityModeListPreference.setSummary(R.string.setting_item_security_pin);
                mSecurityModeChangeKeyPreference.setTitle(R.string.setting_title_security_change_pin);
                mSecurityModeChangeKeyPreference.setVisible(true);
                break;
            case SEQUENCE:
                mSecurityModeListPreference.setSummary(R.string.setting_item_security_sequence);
                mSecurityModeChangeKeyPreference.setTitle(R.string.setting_title_security_change_sequence);
                mSecurityModeChangeKeyPreference.setVisible(true);
                break;
            case FINGERPRINT:
                mSecurityModeListPreference.setSummary(R.string.setting_item_security_fingerprint);
                mSecurityModeChangeKeyPreference.setVisible(false);
                break;
        }
    }

    private void setupCurrentExchangeRateService() {
        int index = PreferenceManager.getCurrentExchangeRateService();
        mExchangeRateServiceListPreference.setValue(String.valueOf(index));
        switch (index) {
            case PreferenceManager.SERVICE_OPEN_EXCHANGE_RATE:
                mExchangeRateServiceListPreference.setSummary(R.string.setting_item_utility_exchange_rates_service_oer);
                mExchangeRateCustomApiKey.setContent(R.string.setting_item_utility_exchange_rates_service_oer_custom_api_key_message);
                break;
        }
        if (PreferenceManager.hasCurrentExchangeRateServiceDefaultApiKey()) {
            mExchangeRateCustomApiKey.setVisible(false);
        } else {
            mExchangeRateCustomApiKey.setVisible(true);
        }
    }

    private void setupCurrentMapTileServer() {
        if (mMapTileServerPreference == null) {
            return;
        }
        String url = PreferenceManager.getMapTileServer();
        if (TextUtils.isEmpty(url)) {
            mMapTileServerPreference.setSummary(R.string.setting_summary_map_tile_server_default);
        } else {
            mMapTileServerPreference.setSummary(getString(R.string.setting_summary_map_tile_server, url));
        }
        // without this the dialog opens empty even when a server is configured, so an existing
        // address has to be retyped rather than corrected, and pressing ok on the untouched field
        // clears it
        mMapTileServerPreference.setCurrentValue(url);
    }

    private void setupCurrentExchangeRateCustomApiKey() {
        if (!PreferenceManager.hasCurrentExchangeRateServiceDefaultApiKey()) {
            String apiKey = PreferenceManager.getCurrentExchangeRateServiceCustomApiKey();
            if (!TextUtils.isEmpty(apiKey)) {
                mExchangeRateCustomApiKey.setSummary(getString(R.string.setting_summary_exchange_rate_api_key, apiKey));
                mExchangeRateCustomApiKey.setCurrentValue(apiKey);
            } else {
                mExchangeRateCustomApiKey.setSummary(R.string.setting_summary_exchange_rate_api_key_missing);
                mExchangeRateCustomApiKey.setCurrentValue(null);
            }
        } else {
            mExchangeRateCustomApiKey.setSummary(null);
            mExchangeRateCustomApiKey.setCurrentValue(null);
        }
    }

    private void setupCurrentExchangeRateUpdate() {
        long timestamp = PreferenceManager.getLastExchangeRateUpdateTimestamp();
        String summary = DateFormatter.getDateFromToday(new Date(timestamp));
        String fullSummary = getString(R.string.setting_summary_exchange_rate_update, summary);
        mExchangeRateUpdatePreference.setSummary(fullSummary);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_LOCK_ACTIVITY) {
            setupCurrentLockMode();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private BroadcastReceiver mLocalBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (LocalAction.ACTION_EXCHANGE_RATES_UPDATED.equals(intent.getAction())) {
                setupCurrentExchangeRateUpdate();
            }
        }

    };
}