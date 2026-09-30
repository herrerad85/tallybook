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

package com.oriondev.moneywallet.ui.fragment.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Wallet;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.ui.activity.NewEditWalletActivity;
import com.oriondev.moneywallet.ui.adapter.recycler.WalletSelectorCursorAdapter;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;

/**
 * Created by andre on 18/03/2018.
 */
public class WalletPickerDialog extends DialogFragment implements LoaderManager.LoaderCallbacks<Cursor>, WalletSelectorCursorAdapter.Controller {

    private static final String SS_SINGLE_PICKER = "WalletPickerDialog::SavedState::SinglePicker";
    private static final String SS_SELECTED_WALLETS = "WalletPickerDialog::SavedState::SelectedWallets";
    private static final String SS_CURRENCY_ISO = "WalletPickerDialog::SavedState::CurrencyIso";

    private static final int DEFAULT_LOADER_ID = 1;

    public static WalletPickerDialog newInstance() {
        return new WalletPickerDialog();
    }

    private Callback mCallback;

    private boolean mSinglePicker;
    private LongSparseArray<Wallet> mSelectedWallets;
    private String mCurrencyIso;

    private RecyclerView mRecyclerView;
    private TextView mMessageTextView;

    private WalletSelectorCursorAdapter mCursorAdapter;

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Activity activity = getActivity();
        if (activity == null) {
            return super.onCreateDialog(savedInstanceState);
        }
        if (savedInstanceState != null) {
            mSelectedWallets = new LongSparseArray<>();
            mSinglePicker = savedInstanceState.getBoolean(SS_SINGLE_PICKER);
            mCurrencyIso = savedInstanceState.getString(SS_CURRENCY_ISO);
            Wallet[] wallets = (Wallet[]) savedInstanceState.getParcelableArray(SS_SELECTED_WALLETS);
            if (wallets != null) {
                for (Wallet wallet : wallets) {
                    mSelectedWallets.append(wallet.getId(), wallet);
                }
            }
        }
        View view = getLayoutInflater().inflate(R.layout.dialog_advanced_list, null);
        MaterialAlertDialogBuilder builder = ThemedDialog.buildMaterialDialog(activity)
                .setTitle(R.string.dialog_wallet_picker_title)
                .setView(view);
        if (mSinglePicker) {
            builder.setPositiveButton(R.string.action_new, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startActivity(new Intent(getActivity(), NewEditWalletActivity.class));
                        }

                    })
                    .setNegativeButton(android.R.string.cancel, null);
        } else {
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (mCallback != null) {
                                Wallet[] wallets = new Wallet[mSelectedWallets.size()];
                                for (int i = 0; i < mSelectedWallets.size(); i++) {
                                    wallets[i] = mSelectedWallets.valueAt(i);
                                }
                                mCallback.onWalletsSelected(wallets);
                            }
                        }

                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .setNeutralButton(R.string.action_new, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startActivity(new Intent(getActivity(), NewEditWalletActivity.class));
                        }

                    });
        }
        AlertDialog dialog = builder.create();
        mCursorAdapter = new WalletSelectorCursorAdapter(this);
        mRecyclerView = view.findViewById(R.id.recycler_view);
        mMessageTextView = view.findViewById(R.id.message_text_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(activity));
        mRecyclerView.setAdapter(mCursorAdapter);
        mMessageTextView.setText(R.string.message_no_wallet_found);
        mRecyclerView.setVisibility(View.GONE);
        mMessageTextView.setVisibility(View.GONE);
        LoaderManager.getInstance(this).restartLoader(DEFAULT_LOADER_ID, null, this);
        return dialog;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Wallet[] wallets = new Wallet[mSelectedWallets.size()];
        for (int i = 0; i < mSelectedWallets.size(); i++) {
            wallets[i] = mSelectedWallets.valueAt(i);
        }
        outState.putBoolean(SS_SINGLE_PICKER, mSinglePicker);
        outState.putParcelableArray(SS_SELECTED_WALLETS, wallets);
        outState.putString(SS_CURRENCY_ISO, mCurrencyIso);
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public void showSinglePicker(FragmentManager fragmentManager, String tag, Wallet wallet) {
        showSinglePicker(fragmentManager, tag, wallet, null);
    }

    public void showSinglePicker(FragmentManager fragmentManager, String tag, Wallet wallet, String currencyIso) {
        mSinglePicker = true;
        mCurrencyIso = currencyIso;
        mSelectedWallets = new LongSparseArray<>();
        if (wallet != null) {
            mSelectedWallets.append(wallet.getId(), wallet);
        }
        show(fragmentManager, tag);
    }

    public void showMultiPicker(FragmentManager fragmentManager, String tag, Wallet[] wallets) {
        mSinglePicker = false;
        mSelectedWallets = new LongSparseArray<>();
        if (wallets != null && wallets.length > 0) {
            for (Wallet wallet : wallets) {
                mSelectedWallets.append(wallet.getId(), wallet);
            }
        }
        show(fragmentManager, tag);
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Activity activity = getActivity();
        if (activity != null) {
            Uri uri = DataContentProvider.CONTENT_WALLETS;
            String[] projection = new String[] {
                    Contract.Wallet.ID,
                    Contract.Wallet.NAME,
                    Contract.Wallet.ICON,
                    Contract.Wallet.CURRENCY,
                    Contract.Wallet.COUNT_IN_TOTAL,
                    Contract.Wallet.START_MONEY,
                    Contract.Wallet.TOTAL_MONEY
            };
            String selection = Contract.Wallet.ARCHIVED + " = 0";
            String[] selectionArgs = null;
            if (mCurrencyIso != null) {
                selection += " AND " + Contract.Wallet.CURRENCY + " = ?";
                selectionArgs = new String[] {mCurrencyIso};
            }
            String sortOrder = Contract.Wallet.INDEX + " ASC, " + Contract.Wallet.NAME + " ASC";
            return new CursorLoader(activity, uri, projection, selection, selectionArgs, sortOrder);
        }
        throw new RuntimeException("Activity is null");
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        mCursorAdapter.swapCursor(cursor);
        if (cursor != null && cursor.getCount() > 0) {
            mRecyclerView.setVisibility(View.VISIBLE);
            mMessageTextView.setVisibility(View.GONE);
        } else {
            mRecyclerView.setVisibility(View.GONE);
            mMessageTextView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        mCursorAdapter.swapCursor(null);
    }

    @Override
    public void onWalletClick(Wallet wallet) {
        if (mSinglePicker) {
            mCallback.onWalletSelected(wallet);
            dismiss();
        } else {
            if (mSelectedWallets.indexOfKey(wallet.getId()) >= 0) {
                mSelectedWallets.remove(wallet.getId());
            } else {
                mSelectedWallets.append(wallet.getId(), wallet);
            }
            mCursorAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean isWalletSelected(long id) {
        return mSelectedWallets.indexOfKey(id) >= 0;
    }

    public interface Callback {

        void onWalletSelected(Wallet wallet);

        void onWalletsSelected(Wallet[] wallets);
    }
}