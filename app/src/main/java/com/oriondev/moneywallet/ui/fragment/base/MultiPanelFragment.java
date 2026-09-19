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
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.fragment.app.Fragment;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.utils.SystemBars;
import com.oriondev.moneywallet.ui.activity.base.ThemedActivity;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.preference.CurrentWalletController;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.activity.DrawerController;
import com.oriondev.moneywallet.ui.activity.ToolbarController;
import com.oriondev.moneywallet.utils.Utils;

/**
 * Created by andrea on 09/02/18.
 */
public abstract class MultiPanelFragment extends Fragment implements MultiPanelController, Toolbar.OnMenuItemClickListener {

    private static final String SAVED_STATE_SECONDARY_PANEL_VISIBLE = "MultiPanelFragment::SecondaryPanelVisible";

    private static final int CURRENT_WALLET_LOADER_ID = 60001;

    private Toolbar mPrimaryToolbar;

    private ViewGroup mPrimaryPanel;
    private ViewGroup mSecondaryPanel;
    private ViewGroup mPrimaryPanelBodyContainer;
    private FloatingActionButton mFloatingActionButton;
    private boolean mExtendedLayout;
    private boolean mSecondaryPanelVisible;

    private BroadcastReceiver mCurrentWalletObserver;

    private TransactionSelectionMode mSelectionMode;

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

    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = onInflateRootLayout(inflater, container, savedInstanceState);
        // Only the width qualified copies of these layouts carry one.
        SystemBars.keepGuidelineBelowToolbar(view.findViewById(R.id.toolbar_delimiter_horizontal_guideline),
                view.findViewById(R.id.primary_toolbar));
        onSetupRootLayout(view);
        onConfigureRootLayout(inflater, container, savedInstanceState);
        setupPrimaryToolbar(mPrimaryToolbar);
        setupPanelVisibility(savedInstanceState);
        return view;
    }

    protected View onInflateRootLayout(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_multi_panel, container, false);
    }

    protected void onSetupRootLayout(View view) {
        mPrimaryToolbar = view.findViewById(R.id.primary_toolbar);
        mPrimaryPanel = Utils.findViewGroupByIds(view,
                R.id.primary_panel_constraint_layout,
                R.id.primary_panel_card_view,
                R.id.primary_panel_coordinator_layout
        );
        mSecondaryPanel = Utils.findViewGroupByIds(view,
                R.id.secondary_panel_frame_layout,
                R.id.secondary_panel_card_view
        );
        mPrimaryPanelBodyContainer = Utils.findViewGroupByIds(view,
                R.id.primary_panel_body_container_frame_layout,
                R.id.primary_panel_body_container_card_view
        );
        mExtendedLayout = view.findViewById(R.id.half_screen_vertical_guideline) != null; // TODO find a way to identify the layout
        mFloatingActionButton = view.findViewById(R.id.floating_action_button);
        if (mFloatingActionButton != null) {
            if (isFloatingActionButtonEnabled()) {
                mFloatingActionButton.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        onFloatingActionButtonClick();
                    }

                });
            } else {
                mFloatingActionButton.setVisibility(View.GONE);
            }
        }
    }

    protected void onConfigureRootLayout(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ViewGroup primaryContainer = mPrimaryPanelBodyContainer != null ? mPrimaryPanelBodyContainer : mPrimaryPanel;
        onCreatePrimaryPanel(inflater, primaryContainer, savedInstanceState);
        // the secondary panel is not the same id in every width qualifier, and a restored child
        // fragment keeps the container id it was added under. Nest one container that carries
        // the same id and the same view type everywhere so that id always resolves
        inflater.inflate(R.layout.layout_secondary_panel_container, mSecondaryPanel, true);
        ViewGroup secondaryContainer = mSecondaryPanel.findViewById(R.id.secondary_panel_container);
        onCreateSecondaryPanel(inflater, secondaryContainer, savedInstanceState);
    }

    protected abstract void onCreatePrimaryPanel(LayoutInflater inflater, @NonNull ViewGroup primaryPanel, @Nullable Bundle savedInstanceState);

    protected abstract void onCreateSecondaryPanel(LayoutInflater inflater, @NonNull ViewGroup secondaryPanel, @Nullable Bundle savedInstanceState);

    protected void setupPrimaryToolbar(Toolbar toolbar) {
        // setup toolbar title and menu (if provided)
        toolbar.setTitle(getTitleRes());
        showCurrentWalletInToolbar(false, null);
        int menuResId = onInflateMenu();
        if (menuResId > 0) {
            toolbar.inflateMenu(menuResId);
            toolbar.setOnMenuItemClickListener(this);
        }
        // attach toolbar to the activity
        Activity activity = getActivity();
        if (activity instanceof ToolbarController) {
            ((ToolbarController) activity).setToolbar(toolbar);
        }
    }

    /**
     * Block the navigation drawer if a drawer controller is registered in the background activity.
     * @param locked state to apply to the drawer.
     */
    protected void setDrawerLocked(boolean locked) {
        Activity activity = getActivity();
        if (activity instanceof DrawerController) {
            ((DrawerController) activity).setDrawerLockMode(
                    locked ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED : DrawerLayout.LOCK_MODE_UNLOCKED
            );
        }
    }

    private void setupPanelVisibility(@Nullable Bundle savedInstanceState) {
        if (mExtendedLayout) {
            // both panels should be visible
            mPrimaryPanel.setVisibility(View.VISIBLE);
            mSecondaryPanel.setVisibility(View.VISIBLE);
            // restore the flag of the secondary panel visibility
            mSecondaryPanelVisible = savedInstanceState != null && savedInstanceState.getBoolean(SAVED_STATE_SECONDARY_PANEL_VISIBLE, false);
            // register the navigation drawer as unlocked
            setDrawerLocked(false);
        } else {
            if (savedInstanceState != null) {
                // take a look at previous state and check if details panel was visible
                if (savedInstanceState.getBoolean(SAVED_STATE_SECONDARY_PANEL_VISIBLE, false)) {
                    showSecondaryPanel();
                } else {
                    hideSecondaryPanel();
                }
            } else {
                // this is a small screen (only one panel at time can be visible) and not exists a
                // previous state to look for: make visible only the primary panel.
                hideSecondaryPanel();
            }
        }
    }

    protected void showSecondaryPanel() {
        mSecondaryPanelVisible = true;
        if (!mExtendedLayout) {
            mPrimaryPanel.setVisibility(View.GONE);
            mSecondaryPanel.setVisibility(View.VISIBLE);
            // lock the navigation drawer
            setDrawerLocked(true);
        }
    }

    protected void hideSecondaryPanel() {
        if (getActivity() instanceof ThemedActivity) {
            ((ThemedActivity) getActivity()).resetStatusBarIconsToAppBar();
        }
        if (!mExtendedLayout) {
            mSecondaryPanelVisible = false;
            mPrimaryPanel.setVisibility(View.VISIBLE);
            mSecondaryPanel.setVisibility(View.GONE);
            // unlock the navigation drawer
            setDrawerLocked(false);
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
            if (mPrimaryToolbar != null) {
                mPrimaryToolbar.setSubtitle(Utils.readWalletName(data));
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
        if (!showsCurrentWallet() || mPrimaryToolbar == null || !isAdded()) {
            return;
        }
        long walletId = PreferenceManager.getCurrentWallet();
        if (walletId == PreferenceManager.TOTAL_WALLET_ID) {
            // synthetic, there is no row to load
            mPrimaryToolbar.setSubtitle(R.string.total_wallet_name);
            LoaderManager.getInstance(this).destroyLoader(CURRENT_WALLET_LOADER_ID);
        } else if (walletId == PreferenceManager.NO_CURRENT_WALLET) {
            mPrimaryToolbar.setSubtitle(null);
            LoaderManager.getInstance(this).destroyLoader(CURRENT_WALLET_LOADER_ID);
        } else if (reload) {
            // the carried name belongs to the wallet being switched to, so it can go up before
            // the load lands and the toolbar keeps its height. With no name, clear, because the
            // load is asynchronous and until it lands the old name would be naming the wrong
            // wallet
            mPrimaryToolbar.setSubtitle(walletName);
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

    protected Toolbar getPrimaryToolbar() {
        return mPrimaryToolbar;
    }

    protected ViewGroup getPrimaryPanel() {
        return mPrimaryPanel;
    }

    @StringRes
    protected abstract int getTitleRes();

    @MenuRes
    protected int onInflateMenu() {
        return 0;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        return false;
    }

    /**
     * Check if we are running using an extended layout.
     * @return true if is an extended layout, false otherwise.
     */
    @Override
    public boolean isExtendedLayout() {
        return mExtendedLayout;
    }

    protected boolean isFloatingActionButtonEnabled() {
        return true;
    }

    protected void onFloatingActionButtonClick() {
        // override this method if you need to intercept the fab click
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SAVED_STATE_SECONDARY_PANEL_VISIBLE, mSecondaryPanelVisible);
    }

    @Override
    public boolean navigateBack() {
        if (closeSecondaryPanel()) {
            return true;
        }
        if (mSelectionMode != null) {
            mSelectionMode.finish();
            return true;
        }
        return false;
    }

    @Override
    public boolean closeSecondaryPanel() {
        if (mSecondaryPanel == null) {
            // The view has not been built yet, so there is no panel to close and
            // nothing for this fragment to do with the back press.
            return false;
        }
        if (!mExtendedLayout) {
            boolean visible = mSecondaryPanel.getVisibility() == View.VISIBLE;
            hideSecondaryPanel();
            return visible;
        }
        return false;
    }

    /*package-local*/ void setSelectionMode(@Nullable TransactionSelectionMode selectionMode) {
        mSelectionMode = selectionMode;
        onSelectionModeChanged(selectionMode != null);
    }

    protected void onSelectionModeChanged(boolean active) {
        // override to hide what the selection title must not share the toolbar with
    }

    protected void finishSelectionMode() {
        if (mSelectionMode != null) {
            mSelectionMode.finish();
        }
    }
}
