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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.annotation.Nullable;
import android.text.TextUtils;

import com.oriondev.moneywallet.BuildConfig;
import com.oriondev.moneywallet.broadcast.DailyBroadcastReceiver;
import com.oriondev.moneywallet.broadcast.LocalAction;
import com.oriondev.moneywallet.model.Group;
import com.oriondev.moneywallet.model.LockMode;
import com.oriondev.moneywallet.ui.widget.WalletWidgetObserver;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by andrea on 24/01/18.
 */
public class PreferenceManager {

    private static final String FILE_NAME = "preferences";

    private static final String CURRENT_WALLET = "current_wallet_id";
    private static final String ASKED_NOTIFICATION_PERMISSION = "asked_notification_permission";
    private static final String CURRENT_LOCK_MODE = "current_lock_mode";
    private static final String CURRENT_LOCK_CODE = "current_lock_code";
    private static final String LAST_LOCK_TIMESTAMP = "last_lock_timestamp";
    private static final String COLOR_INCOME = "color_income";
    private static final String COLOR_EXPENSE = "color_expense";
    private static final String SHOW_CURRENCY = "show_currency";
    private static final String GROUP_DIGITS = "group_digits";
    private static final String ROUND_DECIMALS = "round_decimals";
    private static final String SHOW_PLUS_MINUS_SYMBOL = "show_plus_minus_symbol";
    private static final String DEBT_FINISHED_ONLY = "debt_finished_only";
    private static final String AUTO_OPEN_CALCULATOR = "auto_open_calculator";
    private static final String DOT_MATRIX_ICONS = "dot_matrix_icons";
    private static final String DATE_FORMAT = "date_format";
    private static final String FIRST_DAY_OF_WEEK = "first_day_of_week";
    private static final String FIRST_DAY_OF_MONTH = "first_day_of_month";
    private static final String GROUP_TYPE = "group_type";
    private static final String EXCHANGE_RATE_SERVICE = "exchange_rate_service";
    private static final String EXCHANGE_RATE_LAST_UPDATE = "exchange_rate_last_update";
    private static final String DAILY_REMINDER = "daily_reminder";
    private static final String FIRST_START = "first_start";
    private static final String SERVICE_API_KEY = "user_api_key_";
    private static final String CONVERTER_LAST_CURRENCY_1 = "converter_currency_iso_1";
    private static final String CONVERTER_LAST_CURRENCY_2 = "converter_currency_iso_2";
    private static final String CSV_IMPORT_MAPPING = "csv_import_mapping_";

    private static final String MAP_TILE_SERVER = "map_tile_server";
    private static final String COLLAPSED_CATEGORIES = "collapsed_categories";
    private static final String COLLAPSED_PERIODS = "collapsed_periods";

    private static final String LAST_DATA_CHANGE_TIME = "last_data_change_time";

    public static final int LOCK_MODE_NONE = 0;
    public static final int LOCK_MODE_PIN = 1;
    public static final int LOCK_MODE_SEQUENCE = 2;
    public static final int LOCK_MODE_FINGERPRINT = 3;

    public static final int DATE_FORMAT_TYPE_0 = 0;
    public static final int DATE_FORMAT_TYPE_1 = 1;
    public static final int DATE_FORMAT_TYPE_2 = 2;
    public static final int DATE_FORMAT_TYPE_3 = 3;
    public static final int DATE_FORMAT_TYPE_4 = 4;
    public static final int DATE_FORMAT_TYPE_5 = 5;
    public static final int DATE_FORMAT_TYPE_6 = 6;
    public static final int DATE_FORMAT_TYPE_7 = 7;
    public static final int DATE_FORMAT_TYPE_8 = 8;

    public static final int GROUP_TYPE_DAILY = 0;
    public static final int GROUP_TYPE_WEEKLY = 1;
    public static final int GROUP_TYPE_MONTHLY = 2;
    public static final int GROUP_TYPE_YEARLY = 3;

    public static final int SERVICE_OPEN_EXCHANGE_RATE = 1;

    public static final int DAILY_REMINDER_DISABLED = -1;

    public static final long NO_CURRENT_WALLET = -1L;
    public static final long TOTAL_WALLET_ID = 0L;

    // Color.BLUE is 1.17:1 on a card, unreadable in both dark modes. These two are what a color
    // that cannot be seen falls back to, and getVisibleColor returns a fallback unchecked, so
    // each has to clear every surface an amount is drawn on by itself.
    private static final int DEFAULT_COLOR_INCOME = 0xFF2196F3;
    private static final int DEFAULT_COLOR_EXPENSE = Color.RED;

    private static SharedPreferences mPreferences;
    private static Context mApplicationContext;

    public static void initialize(Context context) {
        mApplicationContext = context.getApplicationContext();
        mPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public static Context getApplicationContext() {
        return mApplicationContext;
    }

    /**
     * @return the address the user chose for tiles, either a base address or a full template,
     *          or null to use the map library's own default. Not a promise that tiles come from
     *          there: the map re checks it and falls back if it no longer passes.
     */
    public static String getMapTileServer() {
        return mPreferences.getString(MAP_TILE_SERVER, null);
    }

    public static void setMapTileServer(String url) {
        if (TextUtils.isEmpty(url)) {
            mPreferences.edit().remove(MAP_TILE_SERVER).apply();
        } else {
            mPreferences.edit().putString(MAP_TILE_SERVER, url).apply();
        }
    }

    /**
     * @return the ids, as text, of the categories whose children the user has hidden in the
     *          category list and the category picker. Nothing stored means nothing hidden,
     *          which is how the list has always been drawn. Stored once rather than held per
     *          list, so that two lists cannot write each other's hiding away. Each list still
     *          has to re-read it to redraw, which CategoryListFragment does when it resumes.
     */
    public static Set<String> getCollapsedCategories() {
        // A copy, because getStringSet is documented as returning a set the caller must not
        // modify, and the callers here are building the next value out of this one.
        return new HashSet<>(mPreferences.getStringSet(COLLAPSED_CATEGORIES, Collections.<String>emptySet()));
    }

    public static void setCollapsedCategories(Set<String> categoryIds) {
        mPreferences.edit().putStringSet(COLLAPSED_CATEGORIES, categoryIds).apply();
    }

    /**
     * @return the keys of the periods whose transactions the user has hidden on the transactions
     *          list and on a filtered transactions list. A key is the group type number, a colon,
     *          and the header's start date exactly as the list's header cursor reports it, so a
     *          folded month and a folded day that begin on the same date are two different keys.
     *          Nothing stored means nothing hidden, which is how the list has always been drawn.
     *          Stored once, not held per list, so that two lists cannot write each other's hiding
     *          away. Each list still has to read it again to redraw, which
     *          TransactionListFragment does when it resumes.
     */
    public static Set<String> getCollapsedPeriods() {
        // A copy, because getStringSet is documented as returning a set the caller must not
        // modify, and the callers here are building the next value out of this one.
        return new HashSet<>(mPreferences.getStringSet(COLLAPSED_PERIODS, Collections.<String>emptySet()));
    }

    public static void setCollapsedPeriods(Set<String> periodKeys) {
        mPreferences.edit().putStringSet(COLLAPSED_PERIODS, periodKeys).apply();
    }

    public static void setCurrentWallet(Context context, long walletId) {
        setCurrentWallet(context, walletId, null);
    }

    /**
     * @param walletName the name of the wallet being selected, when the caller has it, so a
     *                   toolbar can show it at once instead of waiting for its loader.
     */
    public static void setCurrentWallet(Context context, long walletId, @Nullable String walletName) {
        if (getCurrentWallet() != walletId) {
            mPreferences.edit().putLong(CURRENT_WALLET, walletId).apply();
            notifyCurrentWalletIsChanged(context, walletId, walletName);
        }
    }

    public static void setCurrentLockMode(LockMode lockMode) {
        mPreferences.edit().putInt(CURRENT_LOCK_MODE, lockMode.getValue()).apply();
        // A wallet balance sitting on the home screen is hidden while the app is locked, and
        // turning the lock on writes no transaction, so nothing else would tell the widgets to
        // stop showing it. Here and not at the ten call sites in LockActivity, which is also
        // what setCurrentDailyReminder below does with its alarm.
        WalletWidgetObserver.requestUpdate();
    }

    public static void setCurrentLockCode(String code) {
        mPreferences.edit().putString(CURRENT_LOCK_CODE, code).apply();
    }

    public static void setLastLockTime(long time) {
        mPreferences.edit().putLong(LAST_LOCK_TIMESTAMP, time).apply();
    }

    // These four decide how MoneyFormatter writes an amount, so each of them changes the figure a
    // wallet widget is showing without anything being written to the ledger. Same reason as the
    // lock above, and the same answer.

    public static void setCurrencyEnabled(boolean enabled) {
        mPreferences.edit().putBoolean(SHOW_CURRENCY, enabled).apply();
        WalletWidgetObserver.requestUpdate();
    }

    public static void setGroupDigitsEnabled(boolean enabled) {
        mPreferences.edit().putBoolean(GROUP_DIGITS, enabled).apply();
        WalletWidgetObserver.requestUpdate();
    }

    public static void setRoundDecimalsEnabled(boolean enabled) {
        mPreferences.edit().putBoolean(ROUND_DECIMALS, enabled).apply();
        WalletWidgetObserver.requestUpdate();
    }

    public static void setShowPlusMinusSymbolEnabled(boolean enabled) {
        mPreferences.edit().putBoolean(SHOW_PLUS_MINUS_SYMBOL, enabled).apply();
        WalletWidgetObserver.requestUpdate();
    }

    public static void setAutoOpenCalculatorEnabled(boolean enabled) {
        mPreferences.edit().putBoolean(AUTO_OPEN_CALCULATOR, enabled).apply();
    }

    public static void setDotMatrixIconsEnabled(boolean enabled) {
        mPreferences.edit().putBoolean(DOT_MATRIX_ICONS, enabled).apply();
    }

    /**
     * Stores the icon style once, on the first start of a version that has the setting. A new
     * install gets the dot matrix icons and someone upgrading keeps the classic ones they know,
     * and after this the stored value is only ever changed by the user.
     *
     * @param existingUser whether this install already had data before this start.
     */
    public static void resolveDotMatrixIcons(boolean existingUser) {
        if (!mPreferences.contains(DOT_MATRIX_ICONS)) {
            setDotMatrixIconsEnabled(!existingUser);
        }
    }

    public static void setCurrentDateFormatIndex(int index) {
        mPreferences.edit().putInt(DATE_FORMAT, index).apply();
    }

    public static void setCurrentFirstDayOfWeek(int day) {
        mPreferences.edit().putInt(FIRST_DAY_OF_WEEK, day).apply();
    }

    public static void setCurrentFirstDayOfMonth(int day) {
        mPreferences.edit().putInt(FIRST_DAY_OF_MONTH, day).apply();
    }

    public static void setCurrentGroupType(Group groupType) {
        mPreferences.edit().putInt(GROUP_TYPE, groupType.getType()).apply();
    }

    public static void setCurrentIncomeColor(int color) {
        mPreferences.edit().putInt(COLOR_INCOME, color).apply();
    }

    public static void setCurrentExpenseColor(int color) {
        mPreferences.edit().putInt(COLOR_EXPENSE, color).apply();
    }

    public static void setDateFormat(int index) {
        mPreferences.edit().putInt(DATE_FORMAT, index).apply();
    }

    public static void setCurrentDailyReminder(Context context, int hour) {
        int old = getCurrentDailyReminder();
        mPreferences.edit().putInt(DAILY_REMINDER, hour).apply();
        if (old == DAILY_REMINDER_DISABLED && hour != DAILY_REMINDER_DISABLED) {
            DailyBroadcastReceiver.scheduleDailyNotification(context, hour);
        } else if (old != DAILY_REMINDER_DISABLED) {
            DailyBroadcastReceiver.cancelDailyNotification(context);
            if (hour != DAILY_REMINDER_DISABLED) {
                DailyBroadcastReceiver.scheduleDailyNotification(context, hour);
            }
        }
    }

    public static void setCurrentExchangeRateService(int exchangeRateService) {
        mPreferences.edit().putInt(EXCHANGE_RATE_SERVICE, exchangeRateService).apply();
    }

    public static void setLastExchangeRateUpdateTimestamp(long timestamp) {
        mPreferences.edit().putLong(EXCHANGE_RATE_LAST_UPDATE, timestamp).apply();
    }

    public static void setIsFirstStartDone(boolean done) {
        mPreferences.edit().putBoolean(FIRST_START, done).apply();
    }

    public static void setServiceApiKey(int service, String key) {
        mPreferences.edit().putString(SERVICE_API_KEY + String.valueOf(service), key).apply();
    }

    public static void setCurrencyConverterLastCurrency1(String iso) {
        mPreferences.edit().putString(CONVERTER_LAST_CURRENCY_1, iso).apply();
    }

    public static void setCurrencyConverterLastCurrency2(String iso) {
        mPreferences.edit().putString(CONVERTER_LAST_CURRENCY_2, iso).apply();
    }

    public static void setLastTimeDataIsChanged(long timestamp) {
        mPreferences.edit().putLong(LAST_DATA_CHANGE_TIME, timestamp).apply();
    }

    public static long getCurrentWallet() {
        return mPreferences.getLong(CURRENT_WALLET, NO_CURRENT_WALLET);
    }

    public static LockMode getCurrentLockMode() {
        return LockMode.get(mPreferences.getInt(CURRENT_LOCK_MODE, LOCK_MODE_NONE));
    }

    public static String getCurrentLockCode() {
        return mPreferences.getString(CURRENT_LOCK_CODE, null);
    }

    public static long getLastLockTime() {
        return mPreferences.getLong(LAST_LOCK_TIMESTAMP, 0);
    }

    public static boolean isCurrencyEnabled() {
        return mPreferences.getBoolean(SHOW_CURRENCY, true);
    }

    public static boolean isGroupDigitEnabled() {
        return mPreferences.getBoolean(GROUP_DIGITS, true);
    }

    public static boolean isRoundDecimalsEnabled() {
        return mPreferences.getBoolean(ROUND_DECIMALS, false);
    }

    public static boolean isShowPlusMinusSymbolEnabled() {
        return mPreferences.getBoolean(SHOW_PLUS_MINUS_SYMBOL, false);
    }

    public static boolean isAutoOpenCalculatorEnabled() {
        return mPreferences.getBoolean(AUTO_OPEN_CALCULATOR, false);
    }

    public static boolean isDotMatrixIconsEnabled() {
        return mPreferences.getBoolean(DOT_MATRIX_ICONS, false);
    }

    /**
     * Whether the debt lists are showing only the debts that are finished. Stored once rather
     * than held per tab, so the two debt tabs cannot disagree about it.
     */
    public static boolean isDebtFinishedOnlyEnabled() {
        return mPreferences.getBoolean(DEBT_FINISHED_ONLY, false);
    }

    public static void setDebtFinishedOnlyEnabled(boolean enabled) {
        mPreferences.edit().putBoolean(DEBT_FINISHED_ONLY, enabled).apply();
    }

    public static int getCurrentDateFormatIndex() {
        // TODO: maybe return the default value based on the locale
        return mPreferences.getInt(DATE_FORMAT, DATE_FORMAT_TYPE_2);
    }

    public static int getFirstDayOfWeek() {
        // TODO: maybe return the default value based on the locale
        return mPreferences.getInt(FIRST_DAY_OF_WEEK, Calendar.MONDAY);
    }

    public static int getFirstDayOfMonth() {
        return mPreferences.getInt(FIRST_DAY_OF_MONTH, 1);
    }

    public static Group getCurrentGroupType() {
        int index = mPreferences.getInt(GROUP_TYPE, GROUP_TYPE_MONTHLY);
        if (index < GROUP_TYPE_DAILY || index > GROUP_TYPE_YEARLY) {
            index = GROUP_TYPE_MONTHLY;
        }
        return Group.fromType(index);
    }

    public static int getDefaultColorIncome() {
        return DEFAULT_COLOR_INCOME;
    }

    public static int getDefaultColorExpense() {
        return DEFAULT_COLOR_EXPENSE;
    }

    public static int getCurrentIncomeColor() {
        return mPreferences.getInt(COLOR_INCOME, DEFAULT_COLOR_INCOME);
    }

    public static int getCurrentExpenseColor() {
        return mPreferences.getInt(COLOR_EXPENSE, DEFAULT_COLOR_EXPENSE);
    }

    public static int getCurrentDailyReminder() {
        return mPreferences.getInt(DAILY_REMINDER, DAILY_REMINDER_DISABLED);
    }

    public static boolean hasAskedNotificationPermission() {
        return mPreferences.getBoolean(ASKED_NOTIFICATION_PERMISSION, false);
    }

    public static void setAskedNotificationPermission() {
        mPreferences.edit().putBoolean(ASKED_NOTIFICATION_PERMISSION, true).apply();
    }

    public static int getCurrentExchangeRateService() {
        return mPreferences.getInt(EXCHANGE_RATE_SERVICE, SERVICE_OPEN_EXCHANGE_RATE);
    }

    public static boolean hasCurrentExchangeRateServiceDefaultApiKey() {
        switch (getCurrentExchangeRateService()) {
            case SERVICE_OPEN_EXCHANGE_RATE:
                return !TextUtils.isEmpty(BuildConfig.API_KEY_OPEN_EXCHANGE_RATES) && !"INSERT_API_KEY_HERE".equals(BuildConfig.API_KEY_OPEN_EXCHANGE_RATES);
            default:
                return false;
        }
    }

    public static String getCurrentExchangeRateServiceCustomApiKey() {
        return getServiceApiKey(getCurrentExchangeRateService());
    }

    public static long getLastExchangeRateUpdateTimestamp() {
        return mPreferences.getLong(EXCHANGE_RATE_LAST_UPDATE, 0);
    }

    public static boolean isFirstStartDone() {
        return mPreferences.getBoolean(FIRST_START, false);
    }

    public static String getServiceApiKey(int service) {
        return mPreferences.getString(SERVICE_API_KEY + String.valueOf(service), null);
    }

    /**
     * @return the column settings last used to import a file whose header has this signature, as
     *          CsvImportMapping encoded them, or null when no file with that header was imported.
     */
    public static String getCsvImportMapping(String headerSignature) {
        return mPreferences.getString(CSV_IMPORT_MAPPING + headerSignature, null);
    }

    public static void setCsvImportMapping(String headerSignature, String encodedMapping) {
        mPreferences.edit().putString(CSV_IMPORT_MAPPING + headerSignature, encodedMapping).apply();
    }

    public static String getCurrencyConverterLastCurrency1() {
        return mPreferences.getString(CONVERTER_LAST_CURRENCY_1, null);
    }

    public static String getCurrencyConverterLastCurrency2() {
        return mPreferences.getString(CONVERTER_LAST_CURRENCY_2, null);
    }

    public static long getLastTimeDataIsChanged() {
        return mPreferences.getLong(LAST_DATA_CHANGE_TIME, 0L);
    }

    private static void notifyCurrentWalletIsChanged(Context context, long walletId, @Nullable String walletName) {
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(context);
        Intent intent = new Intent(LocalAction.ACTION_CURRENT_WALLET_CHANGED);
        intent.putExtra(LocalAction.ARGUMENT_WALLET_ID, walletId);
        if (walletName != null) {
            intent.putExtra(LocalAction.ARGUMENT_WALLET_NAME, walletName);
        }
        localBroadcastManager.sendBroadcast(intent);
    }

    public static BroadcastReceiver registerCurrentWalletObserver(Context context, final CurrentWalletController controller) {
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(context);
        IntentFilter intentFilter = new IntentFilter(LocalAction.ACTION_CURRENT_WALLET_CHANGED);
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && TextUtils.equals(intent.getAction(), LocalAction.ACTION_CURRENT_WALLET_CHANGED)) {
                    long currentWalletId = intent.getLongExtra(LocalAction.ARGUMENT_WALLET_ID, -1L);
                    String currentWalletName = intent.getStringExtra(LocalAction.ARGUMENT_WALLET_NAME);
                    controller.onCurrentWalletChanged(currentWalletId, currentWalletName);
                }
            }

        };
        localBroadcastManager.registerReceiver(broadcastReceiver, intentFilter);
        return broadcastReceiver;
    }

    public static void unregisterCurrentWalletObserver(Context context, BroadcastReceiver broadcastReceiver) {
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(context);
        localBroadcastManager.unregisterReceiver(broadcastReceiver);
    }
}