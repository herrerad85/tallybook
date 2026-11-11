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

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.model.LockMode;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.activity.LauncherActivity;
import com.oriondev.moneywallet.ui.activity.NewEditItemActivity;
import com.oriondev.moneywallet.ui.activity.NewEditTransactionActivity;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.MoneyFormatter;

/**
 * One wallet's balance on the home screen, with a button that opens the transaction editor on
 * that same wallet.
 *
 * A placement holds one wallet, chosen in WalletWidgetConfigureActivity, so three wallets on the
 * home screen means placing this three times. That is what the launcher's own model is built for
 * and it costs nothing beyond the id keyed settings next door, where a list inside one widget
 * would need a RemoteViewsService and a factory to feed it.
 */
public class WalletWidgetProvider extends AppWidgetProvider {

    /**
     * Redraw every placed widget. Reached from WalletWidgetObserver for every write
     * DataContentProvider announces, which is more than can move a balance, from PreferenceManager
     * when a setting the figure depends on changes, and from onUpdate below when the system asks.
     * A restore is none of those and asks for a redraw in forgetConfiguredWallets.
     *
     * The observer folds a run of writes into one redraw only while they keep landing inside its
     * delay. Writes further apart than that get one redraw each, which is what attaching several
     * files one at a time does.
     *
     * Not from the main thread. Asking for the ids is a call into another process and drawing one
     * reads an aggregate over the transaction table.
     */
    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (manager == null) {
            return;
        }
        int[] appWidgetIds = manager.getAppWidgetIds(new ComponentName(context, WalletWidgetProvider.class));
        if (appWidgetIds != null) {
            for (int appWidgetId : appWidgetIds) {
                manager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId));
            }
        }
    }

    /**
     * Asked for by the system at a boot, at an install, after a restore and when the slow period
     * comes round. Handed to the same thread the observer uses, because this arrives in a
     * broadcast, and a broadcast runs on the main thread with seconds to return, which is no place
     * to run one whole table aggregate per placed widget against a database a boot has just opened
     * cold.
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        WalletWidgetObserver.requestUpdate();
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        WalletWidgetPreferences.delete(context, appWidgetIds);
    }

    /**
     * A widget carried onto another device by a restore. The settings here do not travel with it,
     * since backup_rules and data_extraction_rules both carry the database and not the shared
     * preferences, so there is nothing to move across and the widget comes back asking to be set
     * up again.
     *
     * What this has to do is clear the new ids. The launcher hands out ids that a widget on this
     * device may have used before, and a leftover entry under one of them would point the restored
     * widget at a wallet nobody chose for it, in a ledger whose row ids the restore has reassigned
     * anyway.
     */
    @Override
    public void onRestored(Context context, int[] oldWidgetIds, int[] newWidgetIds) {
        WalletWidgetPreferences.delete(context, newWidgetIds);
    }

    /**
     * Every placement forgets which wallet it was pointed at, and they all redraw.
     *
     * For a restored backup, which inserts every wallet fresh, so the row ids a placement is
     * holding come back naming other wallets. Called from DataContentProvider, which is where the
     * app already clears the current wallet for that reason. The one way into this package from
     * outside it, so the id keyed settings stay private to the widget.
     */
    public static void forgetConfiguredWallets(Context context) {
        WalletWidgetPreferences.clearWallets(context);
        WalletWidgetObserver.requestUpdate();
    }

    /*package-local*/ static RemoteViews buildViews(Context context, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_wallet);
        // The app can be held behind a pin, a sequence or a fingerprint, and a balance drawn on
        // the home screen is readable by anyone holding the phone, which walks straight past that
        // lock. So a locked app hides the figure unless this placement was told not to.
        if (PreferenceManager.getCurrentLockMode() != LockMode.NONE
                && !WalletWidgetPreferences.isShowWhenLocked(context, appWidgetId)) {
            return message(context, views, R.string.widget_message_locked, appWidgetId);
        }
        long walletId = WalletWidgetPreferences.getWallet(context, appWidgetId);
        if (walletId == WalletWidgetPreferences.NO_WALLET) {
            return message(context, views, R.string.widget_message_not_configured, appWidgetId);
        }
        String[] projection = new String[] {
                Contract.Wallet.NAME,
                Contract.Wallet.CURRENCY,
                Contract.Wallet.START_MONEY,
                Contract.Wallet.TOTAL_MONEY
        };
        Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, walletId);
        Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);
        String name = null;
        String balance = null;
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    name = cursor.getString(cursor.getColumnIndex(Contract.Wallet.NAME));
                    // The figure the drawer shows is the starting money plus the total, and the
                    // total on its own is not a balance. Reading it the same way here is what
                    // keeps the widget and the app from disagreeing about one wallet.
                    long money = cursor.getLong(cursor.getColumnIndex(Contract.Wallet.START_MONEY))
                            + cursor.getLong(cursor.getColumnIndex(Contract.Wallet.TOTAL_MONEY));
                    // A currency this install cannot resolve is handed over as it is, because
                    // getNotTintedString takes a null one and falls back to two decimals with no
                    // symbol. That is what every other balance in the app does with it, and a
                    // widget that refused where the wallet list prints a figure would be the one
                    // screen calling a readable wallet broken.
                    CurrencyUnit currency = CurrencyManager.getCurrency(
                            cursor.getString(cursor.getColumnIndex(Contract.Wallet.CURRENCY)));
                    balance = MoneyFormatter.getInstance().getNotTintedString(currency, money);
                }
            } finally {
                cursor.close();
            }
        }
        if (name == null || balance == null) {
            // The wallet this placement was pointed at is gone. Also where a restored backup
            // lands, since a restore gives every wallet a fresh id and the ids held here are
            // cleared with the swap, so the widget asks to be set up again instead of quietly
            // showing whichever wallet inherited the old number.
            return message(context, views, R.string.widget_message_wallet_missing, appWidgetId);
        }
        views.setViewVisibility(R.id.widget_message, View.GONE);
        views.setViewVisibility(R.id.widget_content, View.VISIBLE);
        views.setTextViewText(R.id.widget_wallet_name, name);
        views.setTextViewText(R.id.widget_wallet_balance, balance);
        views.setOnClickPendingIntent(R.id.widget_content, openApp(context, appWidgetId));
        views.setOnClickPendingIntent(R.id.widget_add_button, newTransaction(context, appWidgetId, walletId));
        return views;
    }

    private static RemoteViews message(Context context, RemoteViews views, int messageRes, int appWidgetId) {
        views.setViewVisibility(R.id.widget_content, View.GONE);
        views.setViewVisibility(R.id.widget_message, View.VISIBLE);
        views.setTextViewText(R.id.widget_message, context.getString(messageRes));
        // The header names the app and then the wallet, and the wallet has to go with the figure.
        // A locked widget that still said which account it was watching would hand over half of
        // what the lock is there to keep, and the other states have no wallet to name.
        views.setTextViewText(R.id.widget_wallet_name, null);
        views.setOnClickPendingIntent(R.id.widget_message, openApp(context, appWidgetId));
        return views;
    }

    /**
     * Through LauncherActivity and not straight into MainActivity. Its javadoc says every route
     * into the app passes through it, and the shortcut publishing it does leans on that, so a
     * widget that jumped the queue would quietly make that false.
     */
    private static PendingIntent openApp(Context context, int appWidgetId) {
        Intent intent = new Intent(context, LauncherActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return activity(context, requestCode(appWidgetId, 0), intent);
    }

    private static PendingIntent newTransaction(Context context, int appWidgetId, long walletId) {
        Intent intent = new Intent(context, NewEditTransactionActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM);
        intent.putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_STANDARD);
        intent.putExtra(NewEditTransactionActivity.WALLET_ID, walletId);
        return activity(context, requestCode(appWidgetId, 1), intent);
    }

    private static PendingIntent activity(Context context, int requestCode, Intent intent) {
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Two placements showing two wallets need two pending intents that the system tells apart, and
     * the extras are not part of what it compares. Only the request code is, so it carries both
     * the placement and which of this widget's two targets is meant.
     */
    private static int requestCode(int appWidgetId, int target) {
        return appWidgetId * 2 + target;
    }
}
