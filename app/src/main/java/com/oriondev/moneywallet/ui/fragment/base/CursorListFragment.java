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

package com.oriondev.moneywallet.ui.fragment.base;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.oriondev.moneywallet.storage.preference.CurrentWalletController;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter;
import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView;
import com.oriondev.moneywallet.utils.SystemBars;

/**
 * Created by andrea on 11/02/18.
 */
public abstract class CursorListFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener, LoaderManager.LoaderCallbacks<Cursor>, CurrentWalletController {

    private static final int DEFAULT_LOADER_ID = 24;

    private AdvancedRecyclerView mAdvancedRecyclerView;
    private AbstractCursorAdapter mAbstractCursorAdapter;

    private BroadcastReceiver mBroadcastReceiver;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mBroadcastReceiver = PreferenceManager.registerCurrentWalletObserver(context, this);
    }

    @Override
    public void onDetach() {
        PreferenceManager.unregisterCurrentWalletObserver(getActivity(), mBroadcastReceiver);
        super.onDetach();
    }

    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mAdvancedRecyclerView = new AdvancedRecyclerView(getActivity());
        // Built here and not inflated, so the layout attribute that asks for the navigation bar
        // clearance never reaches it. These are the pager pages behind most of the drawer, so
        // without this the last row of the app's main lists cannot be scrolled clear of the bar.
        SystemBars.pad(mAdvancedRecyclerView.getRecyclerView(),
                false, getResources().getBoolean(R.bool.panel_fills_window), true);
        onPrepareRecyclerView(mAdvancedRecyclerView);
        mAbstractCursorAdapter = onCreateAdapter();
        mAdvancedRecyclerView.setAdapter(mAbstractCursorAdapter);
        mAdvancedRecyclerView.setOnRefreshListener(this);
        mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.LOADING);
        return mAdvancedRecyclerView;
    }

    protected abstract void onPrepareRecyclerView(AdvancedRecyclerView recyclerView);

    protected abstract AbstractCursorAdapter onCreateAdapter();

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        LoaderManager.getInstance(this).restartLoader(DEFAULT_LOADER_ID, null, this);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        mAbstractCursorAdapter.changeCursor(cursor);
        showListState();
    }

    /**
     * Put the list into READY or EMPTY by what the adapter shows, which can be fewer rows than
     * the cursor holds.
     */
    protected void showListState() {
        if (mAbstractCursorAdapter.getItemCount() > 0) {
            mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.READY);
        } else {
            mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.EMPTY);
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        mAbstractCursorAdapter.changeCursor(null);
    }

    @Override
    public void onRefresh() {
        LoaderManager.getInstance(this).restartLoader(DEFAULT_LOADER_ID, null, this);
        mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.REFRESHING);
    }

    /**
     * Change the text shown when the list comes back with nothing in it. The view takes it at
     * once, so this reaches an empty list that is already on screen as well as the next one.
     */
    protected void setEmptyText(@StringRes int stringRes) {
        if (mAdvancedRecyclerView != null) {
            mAdvancedRecyclerView.setEmptyText(stringRes);
        }
    }

    /**
     * Run the query behind this list again, for when something outside the list changes what it
     * should hold. Does nothing before the view exists, since the query is run from
     * {@link #onViewCreated} anyway once it does.
     */
    public void reloadList() {
        if (mAdvancedRecyclerView != null) {
            LoaderManager.getInstance(this).restartLoader(DEFAULT_LOADER_ID, null, this);
            mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.LOADING);
        }
    }

    protected boolean shouldRefreshOnCurrentWalletChange() {
        return false;
    }

    @Override
    public void onCurrentWalletChanged(long walletId) {
        if (shouldRefreshOnCurrentWalletChange()) {
            LoaderManager.getInstance(this).restartLoader(DEFAULT_LOADER_ID, null, this);
            mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.LOADING);
        }
    }
}