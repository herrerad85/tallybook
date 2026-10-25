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

package com.oriondev.moneywallet;

import android.app.Application;
import android.content.res.Configuration;
import android.os.LocaleList;

import androidx.annotation.NonNull;

import com.oriondev.moneywallet.broadcast.AutoBackupBroadcastReceiver;
import com.oriondev.moneywallet.broadcast.DailyBroadcastReceiver;
import com.oriondev.moneywallet.broadcast.RecurrenceBroadcastReceiver;
import com.oriondev.moneywallet.storage.database.SystemCategoryLocalizer;
import com.oriondev.moneywallet.storage.preference.BackendManager;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.notification.NotificationContract;
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;
import com.oriondev.moneywallet.ui.widget.WalletWidgetObserver;
import com.oriondev.moneywallet.utils.CurrencyManager;

/**
 * Created by andrea on 17/01/18.
 */
public class App extends Application {

    private LocaleList mLocales;

    @Override
    public void onCreate() {
        super.onCreate();
        mLocales = getResources().getConfiguration().getLocales();
        PreferenceManager.initialize(this);
        BackendManager.initialize(this);
        ThemeEngine.initialize(this);
        CurrencyManager.initialize(this);
        NotificationContract.initializeNotificationChannels(this);
        // startup, not only onConfigurationChanged, for two reasons: every install made before
        // this existed needs one repair whether or not its language ever changes again, and a
        // system language changed while the process was dead delivers no configuration callback
        SystemCategoryLocalizer.relocalize(this);
        // Registered here because a balance is moved by writes from all over the app, and this
        // is the one place every one of them has already started. It gets its own thread and
        // folds a burst of writes into one redraw, so an import does not pay for this per row.
        WalletWidgetObserver.register(this);
        initializeScheduledTimers();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // both sides come from getResources rather than from newConfig, since that is the
        // configuration the channel names resolve against
        LocaleList locales = getResources().getConfiguration().getLocales();
        if (locales.equals(mLocales)) {
            return;
        }
        NotificationContract.initializeNotificationChannels(this);
        SystemCategoryLocalizer.relocalize(this);
        mLocales = locales;
    }

    private void initializeScheduledTimers() {
        // The application may be killed by the OS when resources are needed or by the user for
        // every kind of reasons. When the application is killed all the scheduled operations are
        // canceled by the OS. This is the best place where all those things can be scheduled again.
        DailyBroadcastReceiver.scheduleDailyNotification(this);
        RecurrenceBroadcastReceiver.scheduleRecurrenceTask(this);
        AutoBackupBroadcastReceiver.ensureAutoBackupTaskScheduled(this);
    }
}