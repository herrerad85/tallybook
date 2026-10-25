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

package com.oriondev.moneywallet.broadcast;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import com.oriondev.moneywallet.service.RecurrenceHandlerIntentService;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.SyncContentProvider;
import com.oriondev.moneywallet.utils.DateUtils;

import java.util.Date;

/**
 * Created by andrea on 10/11/18.
 */
public class RecurrenceBroadcastReceiver extends BroadcastReceiver {

    public static void scheduleRecurrenceTask(Context context) {
        cancelPendingIntent(context);
        ContentResolver contentResolver = context.getContentResolver();
        Date nextOccurrence1 = getMinNextOccurrence(contentResolver,
                DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS, Contract.RecurrentTransaction.NEXT_OCCURRENCE,
                Contract.RecurrentTransaction.NEXT_OCCURRENCE + " IS NOT NULL");
        Date nextOccurrence2 = getMinNextOccurrence(contentResolver,
                DataContentProvider.CONTENT_RECURRENT_TRANSFERS, Contract.RecurrentTransfer.NEXT_OCCURRENCE,
                Contract.RecurrentTransfer.NEXT_OCCURRENCE + " IS NOT NULL");
        // A repeating budget opens its next period the day after the current one ends, and it is
        // the only thing that wakes the task for a user who has no recurring transaction at all.
        // This asks the budgets table itself and not DataContentProvider, whose budget rows carry
        // a spend total gathered from every transaction in range: reading one date out of that
        // cost 396ms against 450 budgets and 5000 transactions on an emulator, and this runs from
        // Application.onCreate. The same figure off the table is 1ms.
        Date nextOccurrence3 = getMinNextOccurrence(contentResolver, SyncContentProvider.CONTENT_BUDGET,
                "DATE(" + Contract.Budget.END_DATE + ", '+1 day')", Contract.ROLLABLE_BUDGET_SELECTION);
        Date nextOccurrence = getMinDate(getMinDate(nextOccurrence1, nextOccurrence2), nextOccurrence3);
        if (nextOccurrence != null) {
            System.out.println("[ALARM] Next occurrence is at: " + nextOccurrence.toString());
            if (DateUtils.isBeforeNow(nextOccurrence)) {
                startBackgroundTask(context);
            } else {
                schedulePendingIntent(context, nextOccurrence);
            }
        }
    }

    private static Date getMinNextOccurrence(ContentResolver contentResolver, Uri uri, String nextOccurrence, String selection) {
        String[] projection = new String[] {
                "MIN(" + nextOccurrence + ")"
        };
        Cursor cursor = contentResolver.query(uri, projection, selection, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    String nextOccurrenceString = cursor.getString(0);
                    if (!TextUtils.isEmpty(nextOccurrenceString)) {
                        return DateUtils.getDateFromSQLDateString(nextOccurrenceString);
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    private static Date getMinDate(Date date1, Date date2) {
        if (date1 != null) {
            if (date2 != null) {
                if (DateUtils.isBefore(date1, date2)) {
                    return date1;
                } else {
                    return date2;
                }
            } else {
                return date1;
            }
        } else if (date2 != null) {
            return date2;
        }
        return null;
    }

    private static void schedulePendingIntent(Context context, Date date) {
        PendingIntent pendingIntent = createPendingIntent(context);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Service.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, date.getTime(), pendingIntent);
            System.out.println("[ALARM] RecurrenceTask scheduled at: " + date.toString());
        }
    }

    private static void cancelPendingIntent(Context context) {
        PendingIntent pendingIntent = createPendingIntent(context);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Service.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }

    private static PendingIntent createPendingIntent(Context context) {
        Intent intent = new Intent(context, RecurrenceBroadcastReceiver.class);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        startBackgroundTask(context);
    }

    private static void startBackgroundTask(Context context) {
        System.out.println("[ALARM] RecurrenceTask fired now");
        RecurrenceHandlerIntentService.enqueueWork(context, new Intent());
    }
}