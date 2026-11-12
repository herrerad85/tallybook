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


package com.oriondev.moneywallet.service

import android.app.Activity
import android.app.IntentService
import android.content.Intent
import androidx.core.app.NotificationCompat

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.service.openexchangerates.OpenExchangeRatesCurrencyRateDownloadIntentService
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.storage.cache.ExchangeRateCache
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.notification.NotificationContract
import com.oriondev.moneywallet.utils.CurrencyManager

/**
 * Created by andre on 24/03/2018.
 */
abstract class AbstractCurrencyRateDownloadIntentService : IntentService {

    companion object {

        @JvmStatic
        fun buildIntent(activity: Activity?): Intent {
            val service = PreferenceManager.getCurrentExchangeRateService()
            when (service) {
                PreferenceManager.SERVICE_OPEN_EXCHANGE_RATE -> {
                    return Intent(activity, OpenExchangeRatesCurrencyRateDownloadIntentService::class.java)
                }
                else -> {
                    throw IllegalStateException("Unknown exchange rate service: " + service)
                }
            }
        }
    }

    private val mCache: ExchangeRateCache?
    private var mBuilder: NotificationCompat.Builder? = null
    private var mReporter: TaskReporter? = null

    constructor(name: String) : super(name) {
        mCache = CurrencyManager.getExchangeRateCache()
    }

    override fun onHandleIntent(intent: Intent?) {
        mReporter = TaskReporter(this)
        mBuilder = mReporter!!.newNotification(NotificationContract.NOTIFICATION_CHANNEL_EXCHANGE_RATE)
                .setContentTitle(getString(R.string.notification_title_download_exchange_rates))
                .setProgress(0, 0, true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        ForegroundServices.startDataSync(this, NotificationContract.NOTIFICATION_ID_EXCHANGE_RATE_PROGRESS, mBuilder!!.build())
        var exception: Exception? = null
        try {
            updateExchangeRates()
            sendBroadcastMessage()
        } catch (e: Exception) {
            exception = e
        } finally {
            stopForeground(true)
        }
        if (exception != null) {
            showError(exception.message)
        }
    }

    @Throws(Exception::class)
    protected abstract fun updateExchangeRates()

    private fun sendBroadcastMessage() {
        PreferenceManager.setLastExchangeRateUpdateTimestamp(System.currentTimeMillis())
        mReporter!!.broadcast(LocalAction.ACTION_EXCHANGE_RATES_UPDATED)
    }

    protected fun storeExchangeRate(currency: CurrencyUnit, rate: Double, timestamp: Long) {
        mCache!!.setExchangeRate(currency.iso, rate, timestamp)
    }

    protected fun setCurrentProgress(operation: String, percentage: Int) {
        mBuilder!!.setContentText(operation)
                .setProgress(100, percentage, false)
        ForegroundServices.startDataSync(this, NotificationContract.NOTIFICATION_ID_EXCHANGE_RATE_PROGRESS, mBuilder!!.build())
    }

    private fun showError(error: String?) {
        mBuilder = mReporter!!.newNotification(NotificationContract.NOTIFICATION_CHANNEL_ERROR)
                .setContentTitle(getString(R.string.notification_title_download_exchange_rates))
                .setContentText(getString(R.string.notification_content_error_message, error))
                .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_content_error_message, error)))
                .setCategory(NotificationCompat.CATEGORY_ERROR)
        mReporter!!.showNotification(NotificationContract.NOTIFICATION_ID_EXCHANGE_RATE_ERROR, mBuilder!!)
    }

    inner class MissingApiKey : Exception {

        constructor(message: String) : super(message)
    }
}
