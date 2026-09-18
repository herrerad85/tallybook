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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.fragment.app.Fragment;
import androidx.appcompat.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.preference.CurrentWalletController;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.activity.ToolbarController;
import com.oriondev.moneywallet.utils.SystemBars;
import com.oriondev.moneywallet.utils.Utils;

/**
 * Created by andrea on 17/08/18.
 */
public abstract class SinglePanelFragment extends Fragment implements Toolbar.OnMenuItemClickListener {

    private static final int CURRENT_WALLET_LOADER_ID = 60002;

    private Toolbar mToolbar;

    private BroadcastReceiver mCurrentWalletObserver;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (showsCurrentWallet()) {
            // an anonymous listener rather than this class implementing CurrentWalletController,
            // because a subclass that implements it would override the callback and stop the
            // toolbar updating
            mCurrentWalletObserver = PreferenceManager.registerCurrentWalletObserver(context, new CurrentWalletController() {

                @Override
                public void onCurrentWalletChanged(long walletId) {
                    onCurrentWalletChanged(walletId, null);
                }

                @Override
                public void onCurrentWalletChanged(long walletId, @Nullable String walletName) {
                    showCurrentWalletInToolbar(true, walletName);
                }

            });
        }
    }

    @Override
    public void onDetach() {
        if (mCurrentWalletObserver != null) {
            PreferenceManager.unregisterCurrentWalletObserver(getActivity(), mCurrentWalletObserver);
            mCurrentWalletObserver = null;
        }
        super.onDetach();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_single_panel, container, false);
        // Only the width qualified copies of these layouts carry one.
        SystemBars.offsetGuidelineByStatusBar(view.findViewById(R.id.toolbar_delimiter_horizontal_guideline));
        mToolbar = view.findViewById(R.id.primary_toolbar);
        ViewGroup parent = Utils.findViewGroupByIds(view,
                R.id.primary_panel_container_frame_layout,
                R.id.primary_panel_container_card_view,
                R.id.primary_panel_container_linear_layout,
                R.id.primary_panel_container_coordinator_layout
        );
        onCreatePanelView(inflater, parent, savedInstanceState);
        FloatingActionButton floatingActionButton = view.findViewById(R.id.floating_action_button);
        onSetupFloatingActionButton(floatingActionButton);
        setupPrimaryToolbar(mToolbar);
        return view;
    }

    protected abstract void onCreatePanelView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState);

    @StringRes
    protected abstract int getTitleRes();

    protected void onSetupFloatingActionButton(FloatingActionButton floatingActionButton) {
        if (floatingActionButton != null) {
            if (isFloatingActionButtonEnabled()) {
                floatingActionButton.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        onFloatingActionButtonClick();
                    }

                });
            } else {
                floatingActionButton.setVisibility(View.GONE);
            }
        }
    }

    protected void setupPrimaryToolbar(Toolbar toolbar) {
        // setup toolbar title and menu (if provided)
        toolbar.setTitle(getTitleRes());
        showCurrentWalletInToolbar(false, null);
        int menuResId = onInflateMenu();
        if (menuResId > 0) {
            toolbar.inflateMenu(menuResId);
            toolbar.setOnMenuItemClickListener(this);
            onMenuCreated(toolbar.getMenu());
        }
        // attach toolbar to the activity
        Activity activity = getActivity();
        if (activity instanceof ToolbarController) {
            ((ToolbarController) activity).setToolbar(toolbar);
        }
    }

    protected void onMenuCreated(Menu menu) {

    }

    protected void setToolbarSubtitle(String subtitle) {
        if (mToolbar != null) {
            mToolbar.setSubtitle(subtitle);
        }
    }

    /**
     * Override to true on a screen whose content is filtered by the wallet selected in the
     * drawer. Naming the wallet is what stops such a screen looking empty for the wrong reason.
     * Leave it false everywhere else: a screen that shows every wallet, or none in particular,
     * would be claiming a scope it does not have. First read from onAttach, so an override
     * cannot depend on anything assigned in onCreate or later.
     */
    protected boolean showsCurrentWallet() {
        return false;
    }

    private final LoaderManager.LoaderCallbacks<Cursor> mCurrentWalletCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {

        @NonNull
        @Override
        public Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {
            Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, PreferenceManager.getCurrentWallet());
            return new CursorLoader(getActivity(), uri, new String[] {Contract.Wallet.NAME}, null, null, null);
        }

        @Override
        public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
            if (mToolbar != null) {
                mToolbar.setSubtitle(Utils.readWalletName(data));
            }
        }

        @Override
        public void onLoaderReset(@NonNull Loader<Cursor> loader) {
            // the subtitle holds a copy of the string, not the cursor
        }

    };

    /**
     * @param reload true when the selected wallet may have changed, so the loader has to be
     *               rebuilt against the new id. False on the view creation path, where init
     *               redelivers the row a retained loader already holds instead of querying
     *               again on every rotation.
     * @param walletName the name the switch carried for the wallet being selected, or null.
     *                   Only read when reload is true.
     */
    private void showCurrentWalletInToolbar(boolean reload, @Nullable String walletName) {
        if (!showsCurrentWallet() || mToolbar == null || !isAdded()) {
            return;
        }
        long walletId = PreferenceManager.getCurrentWallet();
        if (walletId == PreferenceManager.TOTAL_WALLET_ID) {
            // synthetic, there is no row to load
            mToolbar.setSubtitle(R.string.total_wallet_name);
            LoaderManager.getInstance(this).destroyLoader(CURRENT_WALLET_LOADER_ID);
        } else if (walletId == PreferenceManager.NO_CURRENT_WALLET) {
            mToolbar.setSubtitle(null);
            LoaderManager.getInstance(this).destroyLoader(CURRENT_WALLET_LOADER_ID);
        } else if (reload) {
            // the carried name belongs to the wallet being switched to, so it can go up before
            // the load lands and the toolbar keeps its height. With no name, clear, because the
            // load is asynchronous and until it lands the old name would be naming the wrong
            // wallet
            mToolbar.setSubtitle(walletName);
            // a loader rather than a direct query: resolving a wallet row runs a balance
            // aggregate over the transactions table, and it redelivers when the row is renamed
            LoaderManager.getInstance(this).restartLoader(CURRENT_WALLET_LOADER_ID, null, mCurrentWalletCallbacks);
        } else if (isLoaderBuiltForAnotherWallet(walletId)) {
            // a retained loader outlives this fragment instance, so init would redeliver the row
            // it already holds, which belongs to a wallet that is no longer the selected one.
            // No clear needed before the reload, unlike the branch above: this path only runs
            // while the toolbar is freshly inflated, so there is no old name on it yet
            LoaderManager.getInstance(this).restartLoader(CURRENT_WALLET_LOADER_ID, null, mCurrentWalletCallbacks);
        } else {
            LoaderManager.getInstance(this).initLoader(CURRENT_WALLET_LOADER_ID, null, mCurrentWalletCallbacks);
        }
    }

    private boolean isLoaderBuiltForAnotherWallet(long walletId) {
        Loader<Cursor> loader = LoaderManager.getInstance(this).getLoader(CURRENT_WALLET_LOADER_ID);
        if (loader instanceof CursorLoader) {
            return ContentUris.parseId(((CursorLoader) loader).getUri()) != walletId;
        }
        return false;
    }

    @MenuRes
    protected int onInflateMenu() {
        return 0;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        return false;
    }

    protected boolean isFloatingActionButtonEnabled() {
        return true;
    }

    protected void onFloatingActionButtonClick() {
        // override this method if you have to handle the floating action button click event
    }
}