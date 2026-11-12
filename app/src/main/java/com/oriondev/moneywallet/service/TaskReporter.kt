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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.oriondev.moneywallet.R;

/**
 * Small helper shared by the intent services to report task state.
 *
 * It centralises the two cross-cutting mechanisms that every service used to
 * re-implement on its own: sending a local broadcast (action plus optional
 * extras) and creating/posting the progress or error notification. Each service
 * keeps full ownership of its own task logic, extra keys, notification content
 * and foreground timing; this class only owns the plumbing that was identical
 * everywhere.
 */
class TaskReporter {

    private final Context mContext;
    private final LocalBroadcastManager mBroadcastManager;

    TaskReporter(Context context) {
        mContext = context;
        mBroadcastManager = LocalBroadcastManager.getInstance(context);
    }

    /**
     * Send a local broadcast carrying only the action string.
     */
    void broadcast(String action) {
        mBroadcastManager.sendBroadcast(new Intent(action));
    }

    /**
     * Send a local broadcast carrying the action string and the given extras.
     * The extras are copied verbatim into the intent, so the receivers observe
     * exactly the keys, types and values the caller put into the bundle.
     */
    void broadcast(String action, Bundle extras) {
        Intent intent = new Intent(action);
        intent.putExtras(extras);
        mBroadcastManager.sendBroadcast(intent);
    }

    /**
     * Create a notification builder pre-configured with the shared small icon.
     * The caller sets everything else (title, text, progress, category, style,
     * actions) so per-service notification differences are preserved.
     */
    NotificationCompat.Builder newNotification(String channelId) {
        return new NotificationCompat.Builder(mContext, channelId)
                .setSmallIcon(R.drawable.ic_notification);
    }

    /**
     * Post (or update) a notification through the notification manager. Used for
     * the error notifications that must survive the end of the foreground task.
     */
    void showNotification(int id, NotificationCompat.Builder builder) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mContext);
        if (notificationManager.areNotificationsEnabled()) {
            notificationManager.notify(id, builder.build());
        }
    }
}
