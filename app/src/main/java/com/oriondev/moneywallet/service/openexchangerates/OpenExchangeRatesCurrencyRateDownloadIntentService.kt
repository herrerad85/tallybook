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


package com.oriondev.moneywallet.service.openexchangerates

import android.text.TextUtils

import com.oriondev.moneywallet.BuildConfig
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.service.AbstractCurrencyRateDownloadIntentService
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.utils.CurrencyManager

import org.apache.commons.io.IOUtils
import org.json.JSONException
import org.json.JSONObject

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Created by andre on 25/03/2018.
 */
class OpenExchangeRatesCurrencyRateDownloadIntentService : AbstractCurrencyRateDownloadIntentService {

    companion object {

        private const val INVALID_API_KEY = "INSERT_API_KEY_HERE"
    }

    constructor() : super("OpenExchangeRatesCurrencyRateDownloadIntentService")

    @Throws(Exception::class)
    override fun updateExchangeRates() {
        setCurrentProgress(getString(R.string.notification_title_download_exchange_rates), 0)
        val response = downloadLatestExchangeRates()
        if (!response.optBoolean("error", false)) {
            storeResponse(response)
        } else {
            throw IOException(response.optString("message", "unknown"))
        }
    }

    @Throws(MissingApiKey::class)
    private fun buildUrl(): String {
        var apiKey: String? = BuildConfig.API_KEY_OPEN_EXCHANGE_RATES
        if (TextUtils.isEmpty(apiKey) || INVALID_API_KEY.equals(apiKey)) {
            // the developer has not provided a valid api key during the build,
            // we can check if the user has provided an api key in preferences
            apiKey = PreferenceManager.getServiceApiKey(PreferenceManager.SERVICE_OPEN_EXCHANGE_RATE)
        }
        if (!TextUtils.isEmpty(apiKey)) {
            return "https://openexchangerates.org/api/latest.json?app_id=" + apiKey
        } else {
            throw MissingApiKey("missing API KEY for OpenExchangeRates.org")
        }
    }

    @Throws(Exception::class)
    private fun downloadLatestExchangeRates(): JSONObject {
        var inputStream: InputStream? = null
        try {
            val url = URL(buildUrl())
            val connection = url.openConnection() as HttpURLConnection
            connection.setReadTimeout(10000 /* milliseconds */)
            connection.setConnectTimeout(15000 /* milliseconds */)
            connection.setRequestMethod("GET")
            connection.setDoInput(true)
            connection.connect()
            if (connection.getResponseCode() != 401) {
                inputStream = connection.getInputStream()
                return JSONObject(IOUtils.toString(inputStream, "UTF-8"))
            } else {
                throw MissingApiKey("Invalid api-key")
            }
        } catch (e: IOException) {
            throw IOException(getString(R.string.message_error_network_connection))
        } catch (e: JSONException) {
            throw JSONException(getString(R.string.message_error_invalid_response))
        } catch (e: MissingApiKey) {
            throw JSONException(getString(R.string.message_error_missing_api_key))
        } finally {
            if (inputStream != null) {
                inputStream.close()
            }
        }
    }

    @Throws(Exception::class)
    private fun storeResponse(response: JSONObject) {
        val base: CurrencyUnit? = CurrencyManager.getCurrency(response.getString("base"))
        val timestamp = response.getLong("timestamp")
        val rates = response.getJSONObject("rates")
        val currencies = CurrencyManager.getCurrencies()
        var progress = 35f
        val offset = (100f - progress) / currencies.size
        for (currency in currencies) {
            progress += offset
            setCurrentProgress(getString(R.string.notification_content_caching_data), progress.toInt())
            if (rates.has(currency.iso)) {
                val rate = rates.getDouble(currency.iso)
                storeExchangeRate(currency, rate, timestamp)
            }
        }
    }
}
