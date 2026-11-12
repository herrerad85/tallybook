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

package com.oriondev.moneywallet.service;

import android.app.Activity;
import android.app.IntentService;
import android.content.Intent;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.service.openexchangerates.OpenExchangeRatesCurrencyRateDownloadIntentService;
import com.oriondev.moneywallet.broadcast.LocalAction;
import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.storage.cache.ExchangeRateCache;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.notification.NotificationContract;
import com.oriondev.moneywallet.utils.CurrencyManager;

/**
 * Created by andre on 24/03/2018.
 */
public abstract class AbstractCurrencyRateDownloadIntentService extends IntentService {

    public static Intent buildIntent(Activity activity) {
        int service = PreferenceManager.getCurrentExchangeRateService();
        switch (service) {
            case PreferenceManager.SERVICE_OPEN_EXCHANGE_RATE:
                return new Intent(activity, OpenExchangeRatesCurrencyRateDownloadIntentService.class);
            default:
                throw new IllegalStateException("Unknown exchange rate service: " + service);
        }
    }

    private ExchangeRateCache mCache;
    private NotificationCompat.Builder mBuilder;
    private TaskReporter mReporter;

    public AbstractCurrencyRateDownloadIntentService(String name) {
        super(name);
        mCache = CurrencyManager.getExchangeRateCache();
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        mReporter = new TaskReporter(this);
        mBuilder = mReporter.newNotification(NotificationContract.NOTIFICATION_CHANNEL_EXCHANGE_RATE)
                .setContentTitle(getString(R.string.notification_title_download_exchange_rates))
                .setProgress(0, 0, true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS);
        ForegroundServices.startDataSync(this, NotificationContract.NOTIFICATION_ID_EXCHANGE_RATE_PROGRESS, mBuilder.build());
        Exception exception = null;
        try {
            updateExchangeRates();
            sendBroadcastMessage();
        } catch (Exception e) {
            exception = e;
        } finally {
            stopForeground(true);
        }
        if (exception != null) {
            showError(exception.getMessage());
        }
    }

    protected abstract void updateExchangeRates() throws Exception;

    private void sendBroadcastMessage() {
        PreferenceManager.setLastExchangeRateUpdateTimestamp(System.currentTimeMillis());
        mReporter.broadcast(LocalAction.ACTION_EXCHANGE_RATES_UPDATED);
    }

    protected void storeExchangeRate(CurrencyUnit currency, double rate, long timestamp) {
        mCache.setExchangeRate(currency.getIso(), rate, timestamp);
    }

    protected void setCurrentProgress(String operation, int percentage) {
        mBuilder.setContentText(operation)
                .setProgress(100, percentage, false);
        ForegroundServices.startDataSync(this, NotificationContract.NOTIFICATION_ID_EXCHANGE_RATE_PROGRESS, mBuilder.build());
    }

    private void showError(String error) {
        mBuilder = mReporter.newNotification(NotificationContract.NOTIFICATION_CHANNEL_ERROR)
                .setContentTitle(getString(R.string.notification_title_download_exchange_rates))
                .setContentText(getString(R.string.notification_content_error_message, error))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_content_error_message, error)))
                .setCategory(NotificationCompat.CATEGORY_ERROR);
        mReporter.showNotification(NotificationContract.NOTIFICATION_ID_EXCHANGE_RATE_ERROR, mBuilder);
    }

    public class MissingApiKey extends Exception {

        public MissingApiKey(String message) {
            super(message);
        }
    }
}