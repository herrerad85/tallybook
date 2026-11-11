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

package com.oriondev.moneywallet.ui.widget;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.HandlerThread;

import com.oriondev.moneywallet.storage.database.DataContentProvider;

/**
 * Pushes a redraw to the placed widgets whenever DataContentProvider announces a write.
 *
 * A balance does not only move when a transaction row is written, and the provider notifies the
 * URI a write came through. A transfer notifies transfers, a debt with a master transaction
 * notifies debts, and deleting a savings goal takes its transactions with it while notifying
 * savings, so all of them put rows in or out of the transaction table the wallet total is summed
 * from. Listening to transactions alone would leave a widget showing the figure from before a
 * transfer until something unrelated was saved.
 *
 * Naming those uris here is a list that has to be kept in step with what every write announces,
 * so this watches the whole provider instead and is told about all of them.
 *
 * This is what keeps the widget level with the app. The slow period in widget_wallet_info covers
 * the other half, a transaction dated ahead coming due, which no write announces.
 */
public class WalletWidgetObserver extends ContentObserver {

    /**
     * Long enough to fold a run of writes into one redraw and short enough that a save the user
     * just made looks immediate. An import is no longer the case that needs it, since those now
     * announce once per list after their transaction commits. Anything that writes a row at a
     * time still is, the recurrence job posting a backlog being the one to picture.
     */
    private static final long COALESCE_DELAY_MILLIS = 250L;

    private static volatile Handler sHandler;
    private static volatile Runnable sUpdate;

    private WalletWidgetObserver(Handler handler) {
        super(handler);
    }

    /**
     * Its own thread, and not the main one. Asking the system for the placed widget ids is a call
     * into another process, and drawing one reads the wallet total, which is an aggregate over the
     * transaction table. Neither belongs on the thread that has to draw the screen the user is
     * looking at while the write that triggered this is still in progress.
     *
     * The thread lives as long as the process, like the observer it serves.
     */
    public static void register(Context context) {
        final Context applicationContext = context.getApplicationContext();
        // Published before the thread that reads them exists, so the thread cannot see half of
        // this set up.
        sUpdate = new Runnable() {

            @Override
            public void run() {
                WalletWidgetProvider.updateAll(applicationContext);
            }

        };
        HandlerThread thread = new HandlerThread("WalletWidgetObserver");
        thread.start();
        sHandler = new Handler(thread.getLooper());
        WalletWidgetObserver observer = new WalletWidgetObserver(sHandler);
        // Descendants, because no write names CONTENT_ALL itself and an observer that did not
        // ask for what sits below it would be told about nothing at all. The whole registration
        // rests on that flag.
        applicationContext.getContentResolver()
                .registerContentObserver(DataContentProvider.CONTENT_ALL, true, observer);
    }

    /**
     * For the things that change the figure without writing anything, the app lock and the four
     * settings that decide how an amount is written. They are all set from a settings screen, so
     * they get the same thread and the same folding as a write does.
     */
    public static void requestUpdate() {
        Handler handler = sHandler;
        Runnable update = sUpdate;
        if (handler == null || update == null) {
            return;
        }
        handler.removeCallbacks(update);
        handler.postDelayed(update, COALESCE_DELAY_MILLIS);
    }

    @Override
    public void onChange(boolean selfChange) {
        requestUpdate();
    }
}
