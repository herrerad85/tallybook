/*
 * Copyright (c) 2026.
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
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.SQLiteDataException;
import com.oriondev.moneywallet.ui.activity.ToolbarController;
import com.oriondev.moneywallet.ui.adapter.recycler.TransactionCursorAdapter;
import com.oriondev.moneywallet.ui.fragment.secondary.TransactionItemFragment;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * The selection of a transaction list, drawn on the primary toolbar of the multi panel fragment
 * the list sits in, which is not always the list's own fragment.
 */
public class TransactionSelectionMode implements Toolbar.OnMenuItemClickListener {

    private static final String SS_SELECTED_IDS = "TransactionSelectionMode::SavedState::SelectedIds";

    private static final Executor sDeleteExecutor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "TransactionSelectionDelete"));
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    private final Fragment mListFragment;
    private final MultiPanelFragment mOwner;
    private final TransactionCursorAdapter mAdapter;

    private final List<MenuItem> mHiddenItems = new ArrayList<>();
    private CharSequence mTitle;
    private CharSequence mNavigationDescription;
    private boolean mShown;

    public TransactionSelectionMode(Fragment listFragment, MultiPanelFragment owner, TransactionCursorAdapter adapter) {
        mListFragment = listFragment;
        mOwner = owner;
        mAdapter = adapter;
    }

    public void onSelectionChanged(int count) {
        Toolbar toolbar = mOwner.getPrimaryToolbar();
        if (toolbar == null) {
            return;
        }
        if (count > 0) {
            if (!mShown) {
                show(toolbar);
            }
            toolbar.setTitle(mOwner.getString(R.string.title_transactions_selected, count));
        } else if (mShown) {
            hide(toolbar);
        }
    }

    private void show(Toolbar toolbar) {
        mShown = true;
        mTitle = toolbar.getTitle();
        mNavigationDescription = toolbar.getNavigationContentDescription();
        Menu menu = toolbar.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.isVisible()) {
                item.setVisible(false);
                mHiddenItems.add(item);
            }
        }
        toolbar.inflateMenu(R.menu.menu_transaction_selection);
        toolbar.setOnMenuItemClickListener(this);
        toolbar.setNavigationIcon(R.drawable.ic_clear_black_24dp);
        toolbar.setNavigationContentDescription(R.string.description_close_selection);
        toolbar.setNavigationOnClickListener(v -> finish());
        mOwner.setSelectionMode(this);
    }

    private void hide(Toolbar toolbar) {
        mShown = false;
        Menu menu = toolbar.getMenu();
        menu.removeItem(R.id.action_delete_selected_items);
        menu.removeItem(R.id.action_select_all_items);
        for (MenuItem item : mHiddenItems) {
            item.setVisible(true);
        }
        mHiddenItems.clear();
        toolbar.setOnMenuItemClickListener(mOwner);
        toolbar.setTitle(mTitle);
        toolbar.setNavigationContentDescription(mNavigationDescription);
        // a toolbar has no getter for its navigation listener, so the activity sets it again
        Activity activity = mOwner.getActivity();
        if (activity instanceof ToolbarController) {
            ((ToolbarController) activity).setToolbar(toolbar);
        }
        mOwner.setSelectionMode(null);
    }

    public void finish() {
        mAdapter.clearSelection();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_delete_selected_items) {
            showDeleteDialog(mListFragment.requireActivity());
            return true;
        } else if (itemId == R.id.action_select_all_items) {
            mAdapter.selectAll();
            return true;
        }
        return false;
    }

    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putLongArray(SS_SELECTED_IDS, mAdapter.getSelectedIds());
    }

    public void onRestoreInstanceState(@Nullable Bundle savedInstanceState) {
        long[] ids = savedInstanceState != null ? savedInstanceState.getLongArray(SS_SELECTED_IDS) : null;
        if (ids != null && ids.length > 0) {
            mAdapter.setSelectedIds(ids);
        }
    }

    private void showDeleteDialog(Context context) {
        long[] ids = mAdapter.getSelectedIds();
        ThemedDialog.buildMaterialDialog(context)
                .setTitle(R.string.title_warning)
                .setMessage(context.getResources().getQuantityString(
                        R.plurals.message_delete_selected_transactions, ids.length, ids.length))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> delete(context, ids))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void delete(Context context, long[] ids) {
        ContentResolver resolver = context.getApplicationContext().getContentResolver();
        finish();
        sDeleteExecutor.execute(() -> {
            Set<Long> refused = deleteTransactions(resolver, ids);
            sMainHandler.post(() -> {
                if (mListFragment.getView() == null) {
                    return;
                }
                closePanelShowingAnyOf(ids, refused);
                if (!refused.isEmpty()) {
                    ThemedDialog.buildMaterialDialog(mListFragment.requireActivity())
                            .setTitle(R.string.title_error)
                            .setMessage(R.string.message_error_delete_transaction_of_transfer)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
            });
        });
    }

    /*package-local*/ static Set<Long> deleteTransactions(ContentResolver resolver, long[] ids) {
        Set<Long> refused = new HashSet<>();
        for (long id : ids) {
            try {
                resolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, id), null, null);
            } catch (SQLiteDataException e) {
                refused.add(id);
            }
        }
        return refused;
    }

    private void closePanelShowingAnyOf(long[] ids, Set<Long> refused) {
        for (Fragment fragment : mOwner.getChildFragmentManager().getFragments()) {
            if (!(fragment instanceof TransactionItemFragment)) {
                continue;
            }
            SecondaryPanelFragment panel = (SecondaryPanelFragment) fragment;
            long shownId = panel.getItemId();
            for (long id : ids) {
                if (id == shownId && !refused.contains(id)) {
                    panel.navigateBackSafely();
                    panel.showItemId(0L);
                }
            }
        }
    }
}
