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

package com.oriondev.moneywallet.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat

import com.oriondev.moneywallet.service.BackupHandlerIntentService
import com.oriondev.moneywallet.ui.notification.NotificationContract

/**
 * Created by andrea on 01/12/18.
 */
class NotificationBroadcastReceiver : BroadcastReceiver() {

    companion object {

        const val ACTION_RETRY_BACKUP_CREATION = "NotificationBroadcastReceiver::Action::RetryBackupCreation"

        const val ACTION_INTENT_ARGUMENTS = "NotificationBroadcastReceiver::Intent::Arguments"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != null) {
            if (ACTION_RETRY_BACKUP_CREATION.equals(intent.action)) {
                // cancel the old notification
                val notificationManager = NotificationManagerCompat.from(context)
                notificationManager.cancel(NotificationContract.NOTIFICATION_ID_BACKUP_ERROR)
                // re-start the intent service
                val arguments: Bundle? = intent.getBundleExtra(ACTION_INTENT_ARGUMENTS)
                if (arguments != null) {
                    restartAutoBackupIntentService(context, arguments)
                }
            }
        }
    }

    private fun restartAutoBackupIntentService(context: Context, arguments: Bundle) {
        val intent = Intent(context, BackupHandlerIntentService::class.java)
        intent.putExtras(arguments)
        BackupHandlerIntentService.startInForeground(context, intent)
    }
}
