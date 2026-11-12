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

import android.app.IntentService
import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager

import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.broadcast.RecurrenceBroadcastReceiver
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.LegacyEditionImporter
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.utils.CurrencyManager

/**
 * This service is used by the LauncherActivity when a legacy database is detected at startup.
 * The goal is to correctly import all the data coming from the old database and shared preferences
 * into the new data structures.
 */
class UpgradeLegacyEditionIntentService : IntentService {

    companion object {

        const val ERROR_MESSAGE = "UpgradeLegacyEditionIntentService::ErrorMessage"

        @JvmStatic
        fun isLegacyEditionDetected(context: Context): Boolean {
            // check if exists a legacy database to import
            return context.getDatabasePath(LegacyEditionImporter.DATABASE_NAME).exists()
        }
    }

    private val mBroadcastManager: LocalBroadcastManager

    constructor() : super("UpgradeLegacyEditionIntentService") {
        mBroadcastManager = LocalBroadcastManager.getInstance(this)
    }

    override fun onHandleIntent(intent: Intent?) {
        notifyServiceStarted()
        var exception: Exception? = null
        try {
            val importer = LegacyEditionImporter(this)
            importer.importDatabase()
            importer.importAttachments()
            importer.importPreferences()
        } catch (e: Exception) {
            exception = e
        }
        if (exception != null) {
            notifyServiceFailed(exception.message)
        } else {
            DataContentProvider.notifyDatabaseIsChanged(this)
            CurrencyManager.invalidateCache(this)
            PreferenceManager.setLastTimeDataIsChanged(0L)
            RecurrenceBroadcastReceiver.scheduleRecurrenceTask(this)
            notifyServiceFinished()
        }
    }

    private fun notifyServiceStarted() {
        val intent = Intent(LocalAction.ACTION_LEGACY_EDITION_UPGRADE_STARTED)
        mBroadcastManager.sendBroadcast(intent)
    }

    private fun notifyServiceFailed(message: String?) {
        val intent = Intent(LocalAction.ACTION_LEGACY_EDITION_UPGRADE_FAILED)
        intent.putExtra(ERROR_MESSAGE, message)
        mBroadcastManager.sendBroadcast(intent)
    }

    private fun notifyServiceFinished() {
        val intent = Intent(LocalAction.ACTION_LEGACY_EDITION_UPGRADE_FINISHED)
        mBroadcastManager.sendBroadcast(intent)
    }
}
