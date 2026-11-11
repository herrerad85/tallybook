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

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Wallet;
import com.oriondev.moneywallet.picker.WalletPicker;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.ui.activity.NewEditItemActivity;
import com.oriondev.moneywallet.ui.view.text.MaterialEditText;
import com.oriondev.moneywallet.ui.view.text.Validator;
import com.oriondev.moneywallet.ui.view.theme.ThemedCheckBox;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.IconLoader;

/**
 * Asks which wallet a widget being placed should show.
 *
 * The launcher starts this before the widget exists and takes RESULT_CANCELED as a refusal, so
 * the placement is undone if the user backs out. That is why the cancelled result is set first
 * and only replaced once a wallet has actually been chosen.
 */
public class WalletWidgetConfigureActivity extends NewEditItemActivity implements WalletPicker.SingleWalletController {

    private static final String TAG_WALLET_PICKER = "WalletWidgetConfigureActivity::Tag::WalletPicker";

    private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    private MaterialEditText mWalletEditText;
    private ThemedCheckBox mShowWhenLockedCheckBox;

    private WalletPicker mWalletPicker;

    @Override
    protected void onCreateHeaderView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        // The wallet and the one checkbox both sit in the body, so there is nothing to put here.
    }

    @Override
    protected void onCreatePanelView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_panel_widget_configure, parent, true);
        mWalletEditText = view.findViewById(R.id.wallet_edit_text);
        mShowWhenLockedCheckBox = view.findViewById(R.id.show_when_locked_checkbox);
        mWalletEditText.setTextViewMode(true);
        mWalletEditText.addValidator(new Validator() {

            @NonNull
            @Override
            public String getErrorMessage() {
                return getString(R.string.error_input_missing_wallet);
            }

            @Override
            public boolean isValid(@NonNull CharSequence charSequence) {
                // Null until onViewCreated has a widget id to work with, and this activity is
                // exported, so it can be started without one and torn down with the toolbar's
                // save item already live.
                return mWalletPicker != null && mWalletPicker.isSelected();
            }

            @Override
            public boolean autoValidate() {
                return false;
            }

        });
        mWalletEditText.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mWalletPicker.showSingleWalletPicker();
            }

        });
    }

    @Override
    protected void onViewCreated(Bundle savedInstanceState) {
        // Never the inherited edit mode. This activity is exported, because a launcher below
        // Android 12 starts it itself, so any app on the device can send it an intent. The base
        // class throws on an edit mode carrying no id, and there is nothing here to edit anyway,
        // so the mode is fixed before it is read.
        Intent launched = getIntent();
        if (launched != null) {
            launched.putExtra(MODE, Mode.NEW_ITEM);
        }
        super.onViewCreated(savedInstanceState);
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            mAppWidgetId = intent.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        // Set before anything can finish this activity. Backing out has to leave the launcher
        // holding nothing, and the launcher reads that from the result and not from us.
        setResult(Activity.RESULT_CANCELED, resultIntent());
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        // Seeded with whatever this placement already holds, which is nothing the first time and
        // the chosen wallet when the user comes back through the launcher to change it. An empty
        // field there would read as though the setting had been lost.
        mShowWhenLockedCheckBox.setChecked(WalletWidgetPreferences.isShowWhenLocked(this, mAppWidgetId));
        FragmentManager fragmentManager = getSupportFragmentManager();
        mWalletPicker = WalletPicker.createPicker(fragmentManager, TAG_WALLET_PICKER, configuredWallet());
    }

    /**
     * The wallet this placement is set to, or null when it has none or the one it named is gone.
     * A wallet that has been deleted leaves the field empty and the save refused, which is the
     * same place a first time setup starts from.
     */
    private Wallet configuredWallet() {
        long walletId = WalletWidgetPreferences.getWallet(this, mAppWidgetId);
        if (walletId == WalletWidgetPreferences.NO_WALLET) {
            return null;
        }
        String[] projection = new String[] {
                Contract.Wallet.ID,
                Contract.Wallet.NAME,
                Contract.Wallet.ICON,
                Contract.Wallet.CURRENCY,
                Contract.Wallet.START_MONEY,
                Contract.Wallet.TOTAL_MONEY
        };
        Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, walletId);
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor == null) {
            return null;
        }
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new Wallet(
                    cursor.getLong(cursor.getColumnIndex(Contract.Wallet.ID)),
                    cursor.getString(cursor.getColumnIndex(Contract.Wallet.NAME)),
                    IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Wallet.ICON))),
                    CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndex(Contract.Wallet.CURRENCY))),
                    cursor.getLong(cursor.getColumnIndex(Contract.Wallet.START_MONEY)),
                    cursor.getLong(cursor.getColumnIndex(Contract.Wallet.TOTAL_MONEY))
            );
        } finally {
            cursor.close();
        }
    }

    @Override
    protected int getActivityTileRes(Mode mode) {
        return R.string.title_activity_widget_configure;
    }

    @Override
    protected void onSaveChanges(Mode mode) {
        if (!mWalletEditText.validate()) {
            return;
        }
        WalletWidgetPreferences.save(this, mAppWidgetId, mWalletPicker.getCurrentWallet().getId(),
                mShowWhenLockedCheckBox.isChecked());
        // The launcher does not draw a widget it has just been given, so the first picture of it
        // has to be pushed from here. Without this the widget sits blank until something else
        // writes a transaction.
        AppWidgetManager.getInstance(this).updateAppWidget(mAppWidgetId,
                WalletWidgetProvider.buildViews(this, mAppWidgetId));
        setResult(Activity.RESULT_OK, resultIntent());
        finish();
    }

    private Intent resultIntent() {
        Intent intent = new Intent();
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        return intent;
    }

    @Override
    public void onWalletChanged(String tag, Wallet wallet) {
        mWalletEditText.setText(wallet != null ? wallet.getName() : null);
    }
}
