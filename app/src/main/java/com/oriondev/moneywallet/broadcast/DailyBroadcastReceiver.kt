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

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.NewEditTransactionActivity
import com.oriondev.moneywallet.ui.notification.NotificationContract

import java.util.Calendar

/**
 * Created by andrea on 28/07/18.
 */
class DailyBroadcastReceiver : BroadcastReceiver() {

    companion object {

        @JvmStatic
        fun scheduleDailyNotification(context: Context) {
            val hour: Int = PreferenceManager.getCurrentDailyReminder()
            if (hour != PreferenceManager.DAILY_REMINDER_DISABLED) {
                scheduleDailyNotification(context, hour)
            }
        }

        @JvmStatic
        fun scheduleDailyNotification(context: Context?, hour: Int) {
            val pendingIntent = createNotificationIntent(context)
            val alarmManager = context!!.getSystemService(Service.ALARM_SERVICE) as AlarmManager?
            if (alarmManager != null) {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, 0)
                var firstWakeUp: Long = calendar.timeInMillis
                val now: Long = System.currentTimeMillis()
                if (firstWakeUp < now) {
                    firstWakeUp += AlarmManager.INTERVAL_DAY
                }
                alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, firstWakeUp, AlarmManager.INTERVAL_DAY, pendingIntent)
            }
        }

        @JvmStatic
        fun cancelDailyNotification(context: Context?) {
            val pendingIntent = createNotificationIntent(context)
            val alarmManager = context!!.getSystemService(Service.ALARM_SERVICE) as AlarmManager?
            if (alarmManager != null) {
                alarmManager.cancel(pendingIntent)
            }
        }

        private fun createNotificationIntent(context: Context?) =
                PendingIntent.getBroadcast(context, 0, Intent(context, DailyBroadcastReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val i = Intent(context, NewEditTransactionActivity::class.java)
        val pending: PendingIntent? = PendingIntent.getActivity(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, NotificationContract.NOTIFICATION_CHANNEL_REMINDER)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_title_daily_reminder_title))
                .setContentText(context.getString(R.string.notification_title_daily_reminder_text))
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notification_title_daily_reminder_text)))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pending)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        if (notificationManager != null) {
            notificationManager.notify(NotificationContract.NOTIFICATION_ID_REMINDER, builder.build())
        }
        // reschedule the notification for the next time
        scheduleDailyNotification(context, PreferenceManager.getCurrentDailyReminder())
    }
}
