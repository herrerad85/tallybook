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

package com.oriondev.moneywallet.ui.fragment.primary;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import android.view.View;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.broadcast.LocalAction;
import com.oriondev.moneywallet.broadcast.Message;
import com.oriondev.moneywallet.model.Category;
import com.oriondev.moneywallet.model.Group;
import com.oriondev.moneywallet.model.Wallet;
import com.oriondev.moneywallet.picker.CategoryPicker;
import com.oriondev.moneywallet.picker.WalletPicker;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.storage.wrapper.TransactionHeaderCursor;
import com.oriondev.moneywallet.ui.activity.PeriodDetailActivity;
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter;
import com.oriondev.moneywallet.ui.adapter.recycler.TransactionCursorAdapter;
import com.oriondev.moneywallet.ui.fragment.base.CursorListFragment;
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelFragment;
import com.oriondev.moneywallet.ui.fragment.base.TransactionSelectionMode;
import com.oriondev.moneywallet.ui.fragment.base.TransactionSwipeCallback;
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView;

import java.util.Date;

/**
 * Created by andrea on 03/03/18.
 */
public class TransactionListFragment extends CursorListFragment implements TransactionCursorAdapter.ActionListener, WalletPicker.SingleWalletController, CategoryPicker.Controller {

    private TransactionCursorAdapter mAdapter;
    private TransactionSelectionMode mSelectionMode;
    private AdvancedRecyclerView mAdvancedRecyclerView;

    @Override
    protected void onPrepareRecyclerView(AdvancedRecyclerView recyclerView) {
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setEmptyText(R.string.message_no_transaction_found);
        mAdvancedRecyclerView = recyclerView;
    }

    @Override
    protected AbstractCursorAdapter onCreateAdapter() {
        // the only one of the four that opens the report from a header click
        mAdapter = new TransactionCursorAdapter(this, true);
        // a page of the transactions pager, whose toolbar belongs to the pager fragment
        mSelectionMode = new TransactionSelectionMode(this, (MultiPanelFragment) getParentFragment(), mAdapter);
        TransactionSwipeCallback.attach(mAdvancedRecyclerView.getRecyclerView(), mAdapter, mSelectionMode);
        return mAdapter;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mSelectionMode.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSelectionMode != null) {
            mSelectionMode.onSaveInstanceState(outState);
        }
    }

    /**
     * Which periods have their transactions hidden is stored once for the whole application, and
     * this list is not the only one reading it, since a filtered transactions list opens over this
     * one. Asking again here is what stops the one underneath drawing a state another has already
     * changed, which would make its next arrow tap do nothing. The rows themselves need no
     * reloading, since the loader is watching the transactions already.
     */
    @Override
    public void onResume() {
        super.onResume();
        mAdapter.reloadCollapsedPeriods();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Activity activity = getActivity();
        if (activity != null) {
            Uri uri = DataContentProvider.CONTENT_TRANSACTIONS;
            long currentWallet = PreferenceManager.getCurrentWallet();
            String selection = walletSelection(currentWallet)
                    + " AND (DATETIME(" + Contract.Transaction.DATE + ") <= DATETIME('now', 'localtime')"
                    + " OR DATETIME(" + Contract.Transaction.DATE + ", '+0 days') = " + Contract.Transaction.DATE + ")";
            String sortOrder = Contract.Transaction.DATE + " DESC";
            Group groupType = PreferenceManager.getCurrentGroupType();
            return new WrappedCursorLoader(activity, uri, null, selection, null,
                    sortOrder, groupType, null, null);
        }
        return null;
    }

    private static String walletSelection(long currentWallet) {
        if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
            return Contract.Transaction.WALLET_COUNT_IN_TOTAL + " = 1";
        }
        return Contract.Transaction.WALLET_ID + " = " + currentWallet;
    }

    @Override
    public void onHeaderClick(Date startDate, Date endDate) {
        Intent intent = new Intent(getActivity(), PeriodDetailActivity.class);
        intent.putExtra(PeriodDetailActivity.START_DATE, startDate);
        intent.putExtra(PeriodDetailActivity.END_DATE, endDate);
        startActivity(intent);
    }

    @Override
    public void onTransactionClick(long id) {
        Activity activity = getActivity();
        if (activity != null) {
            Intent intent = new Intent(LocalAction.ACTION_ITEM_CLICK);
            intent.putExtra(Message.ITEM_ID, id);
            intent.putExtra(Message.ITEM_TYPE, Message.TYPE_TRANSACTION);
            LocalBroadcastManager.getInstance(activity).sendBroadcast(intent);
        }
    }

    @Override
    public void onSelectionChanged(int count) {
        mSelectionMode.onSelectionChanged(count);
    }

    @Override
    public void onWalletChanged(String tag, Wallet wallet) {
        mSelectionMode.onWalletPicked(wallet);
    }

    @Override
    public void onCategoryChanged(String tag, Category category) {
        mSelectionMode.onCategoryPicked(category);
    }

    @Override
    public void onCurrentWalletChanged(long walletId) {
        mAdapter.clearSelection();
        super.onCurrentWalletChanged(walletId);
    }

    @Override
    protected boolean shouldRefreshOnCurrentWalletChange() {
        // this fragment content is dependant on the current
        // wallet when the loader is created, so the query
        // operation must be recreated from the beginning.
        return true;
    }

    private static class WrappedCursorLoader extends CursorLoader {

        private final Group mGroup;
        private final Date mStartDate;
        private final Date mEndDate;

        private WrappedCursorLoader(@NonNull Context context, @NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                                   @Nullable String[] selectionArgs, @Nullable String sortOrder, Group group, Date startDate, Date endDate) {
            super(context, uri, projection, selection, selectionArgs, sortOrder);
            mGroup = group;
            mStartDate = startDate;
            mEndDate = endDate;
        }

        @Override
        public Cursor loadInBackground() {
            Cursor cursor = super.loadInBackground();
            return new TransactionHeaderCursor(cursor, mGroup, mStartDate, mEndDate);
        }
    }
}