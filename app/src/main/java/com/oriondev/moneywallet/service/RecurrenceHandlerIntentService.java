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

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;
import androidx.core.app.NotificationCompat;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.broadcast.RecurrenceBroadcastReceiver;
import com.oriondev.moneywallet.model.LockMode;
import com.oriondev.moneywallet.model.RecurrenceSetting;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.SQLiteDataException;
import com.oriondev.moneywallet.storage.database.TransactionContentValuesBuilder;
import com.oriondev.moneywallet.storage.database.TransferContentValuesBuilder;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.activity.LauncherActivity;
import com.oriondev.moneywallet.ui.notification.NotificationContract;
import com.oriondev.moneywallet.utils.DateUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by andrea on 11/11/18.
 */
public class RecurrenceHandlerIntentService extends JobIntentService {

    private static final String TAG = "RecurrenceHandler";

    private static final int JOB_ID = 3564;

    // a daily budget that has been left alone for years would otherwise write one row per day in a
    // single pass; the period that is still open keeps the rule, so the next run carries on
    private static final int MAX_BUDGET_PERIODS_PER_RUN = 400;

    private static final String EXTRA_POSTED_COUNT = "RecurrenceHandler::PostedCount";

    private int mPostedCount;
    private String mFirstPostedLabel;

    public static void enqueueWork(Context context, Intent intent) {
        enqueueWork(context, RecurrenceHandlerIntentService.class, JOB_ID, intent);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        // the rows a recurrence already had due when it was saved are written by the provider
        // insert itself, so only the rows that came due afterwards are counted here
        mPostedCount = 0;
        mFirstPostedLabel = null;
        addMissingRecurrentTransactionOccurrences();
        addMissingRecurrentTransferOccurrences();
        addMissingBudgetPeriods();
        RecurrenceBroadcastReceiver.scheduleRecurrenceTask(this);
        if (mPostedCount > 0) {
            notifyPostedOccurrences();
        }
    }

    private void onOccurrencePosted(String label) {
        if (mPostedCount == 0) {
            mFirstPostedLabel = label;
        }
        mPostedCount++;
    }

    private static String labelOf(String description, String fallback) {
        return description != null && !description.trim().isEmpty() ? description.trim() : fallback;
    }

    private void notifyPostedOccurrences() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        int total = mPostedCount;
        for (StatusBarNotification active : notificationManager.getActiveNotifications()) {
            if (active.getId() == NotificationContract.NOTIFICATION_ID_RECURRENCE) {
                total += active.getNotification().extras.getInt(EXTRA_POSTED_COUNT);
            }
        }
        boolean locked = PreferenceManager.getCurrentLockMode() != LockMode.NONE;
        String title = total == 1 ? getString(R.string.notification_title_recurrence_added)
                : getResources().getQuantityString(R.plurals.notification_title_recurrences_added, total, total);
        String label = total == 1 && !locked ? mFirstPostedLabel : null;
        Bundle extras = new Bundle();
        extras.putInt(EXTRA_POSTED_COUNT, total);
        Intent intent = new Intent(this, LauncherActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NotificationContract.NOTIFICATION_CHANNEL_RECURRENCE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .addExtras(extras)
                .setAutoCancel(true)
                .setContentIntent(pending);
        if (!TextUtils.isEmpty(label)) {
            builder.setContentText(label)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(label));
        }
        if (!locked) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(new NotificationCompat.Builder(this, NotificationContract.NOTIFICATION_CHANNEL_RECURRENCE)
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle(title)
                            .build());
        }
        notificationManager.notify(NotificationContract.NOTIFICATION_ID_RECURRENCE, builder.build());
    }

    /**
     * Open the periods that a repeating budget has come due for. Every period is a budget row of
     * its own, so the ones that have finished stay where they are and are read as history in the
     * expired tab. Only the period that is currently running keeps the rule, which is what stops
     * a finished period opening its successor a second time.
     */
    private void addMissingBudgetPeriods() {
        Uri uri = DataContentProvider.CONTENT_BUDGETS;
        String selection = Contract.Budget.RULE + " IS NOT NULL AND DATE(" + Contract.Budget.END_DATE + ") < DATE('now', 'localtime')";
        Cursor cursor = getContentResolver().query(uri, null, selection, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                try {
                    rollBudgetForward(cursor);
                } catch (SQLiteDataException e) {
                    // a budget the provider refuses, such as one whose wallets no longer agree on
                    // a currency, is left where it is; the rest of the run still has to happen,
                    // and so does the alarm that onHandleWork asks for when it returns
                    Log.e(TAG, "Cannot roll budget forward", e);
                }
            }
            cursor.close();
        }
    }

    private void rollBudgetForward(Cursor cursor) {
        long budgetId = cursor.getLong(cursor.getColumnIndex(Contract.Budget.ID));
        String rule = cursor.getString(cursor.getColumnIndex(Contract.Budget.RULE));
        Date periodStart = DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndex(Contract.Budget.START_DATE)));
        String anchorString = cursor.getString(cursor.getColumnIndex(Contract.Budget.RULE_START));
        // the schedule is counted from the day it is anchored to, not from the day this period
        // happens to start, so a weekly one keeps its weekday and one that repeats every second
        // month keeps its months. A row that arrived without an anchor counts from itself.
        Date anchor = anchorString != null ? DateUtils.getDateFromSQLDateString(anchorString) : periodStart;
        // walk the rule forward until a period is reached that has not finished yet, so a device
        // that was away for several months opens every period it missed instead of only the last
        List<Date[]> periods = new ArrayList<>();
        boolean ruleIsSpent = false;
        while (periods.size() < MAX_BUDGET_PERIODS_PER_RUN) {
            Date nextStart = RecurrenceSetting.nextInstanceAfter(rule, anchor, periodStart);
            Date nextEnd = nextStart != null ? RecurrenceSetting.periodEnd(rule, anchor, nextStart) : null;
            if (nextEnd == null) {
                ruleIsSpent = true;
                break;
            }
            periods.add(new Date[] {nextStart, nextEnd});
            periodStart = nextStart;
            if (!DateUtils.isBeforeToday(nextEnd)) {
                break;
            }
        }
        // The rule leaves the period that has just closed before any period is opened. It reads
        // backwards, and it is the order that matters: a run killed part way through then leaves
        // the budget not repeating, which the owner can turn back on, instead of leaving the rule
        // both here and on a period this run opened. Two rows carrying it are two chains, and the
        // next roll walks both, so the budget doubles on every period from then on.
        ContentValues closed = copyBudget(cursor);
        closed.put(Contract.Budget.START_DATE, cursor.getString(cursor.getColumnIndex(Contract.Budget.START_DATE)));
        closed.put(Contract.Budget.END_DATE, cursor.getString(cursor.getColumnIndex(Contract.Budget.END_DATE)));
        closed.putNull(Contract.Budget.RULE);
        getContentResolver().update(ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budgetId), closed, null, null);
        for (int i = 0; i < periods.size(); i++) {
            ContentValues contentValues = copyBudget(cursor);
            contentValues.put(Contract.Budget.START_DATE, DateUtils.getSQLDateString(periods.get(i)[0]));
            contentValues.put(Contract.Budget.END_DATE, DateUtils.getSQLDateString(periods.get(i)[1]));
            contentValues.put(Contract.Budget.ROLLED_FROM_ID, budgetId);
            // The last period this run reaches carries the rule on, and it is the only one that
            // does. It carries it even when it has already ended, which is what a run stopped by
            // MAX_BUDGET_PERIODS_PER_RUN leaves behind: the next run picks that period up and
            // keeps going. The rule is dropped only once it can open no further period at all.
            if (i == periods.size() - 1 && !ruleIsSpent) {
                contentValues.put(Contract.Budget.RULE, rule);
            } else {
                contentValues.putNull(Contract.Budget.RULE);
            }
            getContentResolver().insert(DataContentProvider.CONTENT_BUDGETS, contentValues);
        }
    }

    /**
     * Everything a budget row carries except its dates, its rule and its currency. Every caller
     * sets the dates and the rule, and the insert takes the currency from the wallets.
     */
    private ContentValues copyBudget(Cursor cursor) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Budget.TYPE, cursor.getInt(cursor.getColumnIndex(Contract.Budget.TYPE)));
        if (cursor.isNull(cursor.getColumnIndex(Contract.Budget.CATEGORY_ID))) {
            contentValues.putNull(Contract.Budget.CATEGORY_ID);
        } else {
            contentValues.put(Contract.Budget.CATEGORY_ID, cursor.getLong(cursor.getColumnIndex(Contract.Budget.CATEGORY_ID)));
        }
        contentValues.put(Contract.Budget.MONEY, cursor.getLong(cursor.getColumnIndex(Contract.Budget.MONEY)));
        contentValues.put(Contract.Budget.TAG, cursor.getString(cursor.getColumnIndex(Contract.Budget.TAG)));
        contentValues.put(Contract.Budget.RULE_START, cursor.getString(cursor.getColumnIndex(Contract.Budget.RULE_START)));
        contentValues.put(Contract.Budget.WALLET_IDS, cursor.getString(cursor.getColumnIndex(Contract.Budget.WALLET_IDS)));
        contentValues.put(Contract.Budget.CATEGORY_IDS, cursor.getString(cursor.getColumnIndex(Contract.Budget.CATEGORY_IDS)));
        return contentValues;
    }

    private void addMissingRecurrentTransactionOccurrences() {
        Uri uri = DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS;
        String selection = Contract.RecurrentTransaction.NEXT_OCCURRENCE + " IS NOT NULL AND DATE(" + Contract.RecurrentTransaction.NEXT_OCCURRENCE + ") <= DATE('now', 'localtime')";
        Cursor cursor = getContentResolver().query(uri, null, selection, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                // get basic information about the recurrence entity
                long transactionId = cursor.getLong(cursor.getColumnIndex(Contract.RecurrentTransaction.ID));
                String firstOccurrenceDateString = cursor.getString(cursor.getColumnIndex(Contract.RecurrentTransaction.NEXT_OCCURRENCE));
                Date firstOccurrenceDate = DateUtils.getDateFromSQLDateString(firstOccurrenceDateString);
                String rule = cursor.getString(cursor.getColumnIndex(Contract.RecurrentTransaction.RULE));
                RecurrenceSetting.OccurrenceUpdate update = RecurrenceSetting.computeOccurrences(rule, firstOccurrenceDate, new Date());
                String label = labelOf(cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.DESCRIPTION)),
                        cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.CATEGORY_NAME)));
                for (Date occurrenceDate : update.getOccurrenceDates()) {
                    TransactionContentValuesBuilder builder = new TransactionContentValuesBuilder()
                            .money(cursor.getLong(cursor.getColumnIndex(Contract.RecurrentTransaction.MONEY)))
                            .date(DateUtils.getSQLDateTimeString(occurrenceDate))
                            .description(cursor.getString(cursor.getColumnIndex(Contract.RecurrentTransaction.DESCRIPTION)))
                            .categoryId(cursor.getLong(cursor.getColumnIndex(Contract.RecurrentTransaction.CATEGORY_ID)))
                            .direction(cursor.getInt(cursor.getColumnIndex(Contract.RecurrentTransaction.DIRECTION)))
                            .type(Contract.TransactionType.STANDARD)
                            .walletId(cursor.getLong(cursor.getColumnIndex(Contract.RecurrentTransaction.WALLET_ID)))
                            .note(cursor.getString(cursor.getColumnIndex(Contract.RecurrentTransaction.NOTE)));
                    if (!cursor.isNull(cursor.getColumnIndex(Contract.RecurrentTransaction.PLACE_ID))) {
                        builder.placeId(cursor.getLong(cursor.getColumnIndex(Contract.RecurrentTransaction.PLACE_ID)));
                    }
                    if (!cursor.isNull(cursor.getColumnIndex(Contract.RecurrentTransaction.EVENT_ID))) {
                        builder.eventId(cursor.getLong(cursor.getColumnIndex(Contract.RecurrentTransaction.EVENT_ID)));
                    }
                    builder.recurrenceId(transactionId)
                            .confirmed(cursor.getInt(cursor.getColumnIndex(Contract.RecurrentTransaction.CONFIRMED)) == 1)
                            .countInTotal(cursor.getInt(cursor.getColumnIndex(Contract.RecurrentTransaction.COUNT_IN_TOTAL)) == 1);
                    ContentValues contentValues = builder.build();
                    if (getContentResolver().insert(DataContentProvider.CONTENT_TRANSACTIONS, contentValues) != null) {
                        onOccurrencePosted(label);
                    }
                }
                ContentValues contentValues = new ContentValues();
                contentValues.put(Contract.RecurrentTransaction.LAST_OCCURRENCE, DateUtils.getSQLDateString(update.getLastOccurrence()));
                contentValues.put(Contract.RecurrentTransaction.NEXT_OCCURRENCE, update.getNextOccurrence() != null ? DateUtils.getSQLDateString(update.getNextOccurrence()) : null);
                Uri contentUri = ContentUris.withAppendedId(DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS, transactionId);
                getContentResolver().update(contentUri, contentValues, null, null);
            }
            cursor.close();
        }
    }

    private void addMissingRecurrentTransferOccurrences() {
        Uri uri = DataContentProvider.CONTENT_RECURRENT_TRANSFERS;
        String selection = Contract.RecurrentTransfer.NEXT_OCCURRENCE + " IS NOT NULL AND DATE(" + Contract.RecurrentTransfer.NEXT_OCCURRENCE + ") <= DATE('now', 'localtime')";
        Cursor cursor = getContentResolver().query(uri, null, selection, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                // get basic information about the recurrence entity
                long recurrenceId = cursor.getLong(cursor.getColumnIndex(Contract.RecurrentTransfer.ID));
                String firstOccurrenceDateString = cursor.getString(cursor.getColumnIndex(Contract.RecurrentTransfer.NEXT_OCCURRENCE));
                Date firstOccurrenceDate = DateUtils.getDateFromSQLDateString(firstOccurrenceDateString);
                String rule = cursor.getString(cursor.getColumnIndex(Contract.RecurrentTransfer.RULE));
                RecurrenceSetting.OccurrenceUpdate update = RecurrenceSetting.computeOccurrences(rule, firstOccurrenceDate, new Date());
                String label = labelOf(cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.DESCRIPTION)),
                        getString(R.string.notification_text_recurrence_transfer,
                                cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.WALLET_FROM_NAME)),
                                cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.WALLET_TO_NAME))));
                for (Date occurrenceDate : update.getOccurrenceDates()) {
                    TransferContentValuesBuilder builder = new TransferContentValuesBuilder()
                            .description(cursor.getString(cursor.getColumnIndex(Contract.RecurrentTransfer.DESCRIPTION)))
                            .date(DateUtils.getSQLDateTimeString(occurrenceDate))
                            .fromWalletId(cursor.getLong(cursor.getColumnIndex(Contract.RecurrentTransfer.WALLET_FROM_ID)))
                            .toWalletId(cursor.getLong(cursor.getColumnIndex(Contract.RecurrentTransfer.WALLET_TO_ID)))
                            .taxWalletId(cursor.getLong(cursor.getColumnIndex(Contract.RecurrentTransfer.WALLET_FROM_ID)))
                            .fromMoney(cursor.getLong(cursor.getColumnIndex(Contract.RecurrentTransfer.MONEY_FROM)))
                            .toMoney(cursor.getLong(cursor.getColumnIndex(Contract.RecurrentTransfer.MONEY_TO)))
                            .taxMoney(cursor.getLong(cursor.getColumnIndex(Contract.RecurrentTransfer.MONEY_TAX)))
                            .note(cursor.getString(cursor.getColumnIndex(Contract.RecurrentTransfer.NOTE)));
                    if (!cursor.isNull(cursor.getColumnIndex(Contract.RecurrentTransfer.PLACE_ID))) {
                        builder.placeId(cursor.getLong(cursor.getColumnIndex(Contract.RecurrentTransfer.PLACE_ID)));
                    }
                    if (!cursor.isNull(cursor.getColumnIndex(Contract.RecurrentTransfer.EVENT_ID))) {
                        builder.eventId(cursor.getLong(cursor.getColumnIndex(Contract.RecurrentTransfer.EVENT_ID)));
                    }
                    builder.recurrenceId(recurrenceId)
                            .confirmed(cursor.getInt(cursor.getColumnIndex(Contract.RecurrentTransfer.CONFIRMED)) == 1)
                            .countInTotal(cursor.getInt(cursor.getColumnIndex(Contract.RecurrentTransfer.COUNT_IN_TOTAL)) == 1);
                    ContentValues contentValues = builder.build();
                    if (getContentResolver().insert(DataContentProvider.CONTENT_TRANSFERS, contentValues) != null) {
                        onOccurrencePosted(label);
                    }
                }
                ContentValues contentValues = new ContentValues();
                contentValues.put(Contract.RecurrentTransfer.LAST_OCCURRENCE, DateUtils.getSQLDateString(update.getLastOccurrence()));
                contentValues.put(Contract.RecurrentTransfer.NEXT_OCCURRENCE, update.getNextOccurrence() != null ? DateUtils.getSQLDateString(update.getNextOccurrence()) : null);
                Uri contentUri = ContentUris.withAppendedId(DataContentProvider.CONTENT_RECURRENT_TRANSFERS, recurrenceId);
                getContentResolver().update(contentUri, contentValues, null, null);
            }
            cursor.close();
        }
    }
}