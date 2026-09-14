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
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Category;
import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.model.Wallet;
import com.oriondev.moneywallet.picker.CategoryPicker;
import com.oriondev.moneywallet.picker.WalletPicker;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.SQLiteDataException;
import com.oriondev.moneywallet.ui.activity.ToolbarController;
import com.oriondev.moneywallet.ui.adapter.recycler.TransactionCursorAdapter;
import com.oriondev.moneywallet.ui.fragment.secondary.TransactionItemFragment;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.MoneyFormatter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.IntSupplier;

/**
 * The selection of a transaction list, drawn on the primary toolbar of the multi panel fragment
 * the list sits in, which is not always the list's own fragment.
 */
public class TransactionSelectionMode implements Toolbar.OnMenuItemClickListener {

    private static final String SS_SELECTED_IDS = "TransactionSelectionMode::SavedState::SelectedIds";
    private static final String TAG_WALLET_PICKER = "TransactionSelectionMode::Tag::WalletPicker";
    private static final String TAG_CATEGORY_PICKER = "TransactionSelectionMode::Tag::CategoryPicker";

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
        menu.removeItem(R.id.action_move_selected_items);
        menu.removeItem(R.id.action_change_category_selected_items);
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
        } else if (itemId == R.id.action_move_selected_items) {
            FragmentManager fragmentManager = mListFragment.getChildFragmentManager();
            WalletPicker picker = WalletPicker.createPicker(fragmentManager, TAG_WALLET_PICKER, (Wallet) null);
            fragmentManager.executePendingTransactions();
            picker.showSingleWalletPicker();
            return true;
        } else if (itemId == R.id.action_change_category_selected_items) {
            FragmentManager fragmentManager = mListFragment.getChildFragmentManager();
            CategoryPicker picker = CategoryPicker.createPicker(fragmentManager, TAG_CATEGORY_PICKER, (Category) null);
            fragmentManager.executePendingTransactions();
            picker.showPicker();
            return true;
        } else if (itemId == R.id.action_select_all_items) {
            mAdapter.selectAll();
            return true;
        }
        return false;
    }

    public void onWalletPicked(@Nullable Wallet wallet) {
        // a picker hands back its last value again every time it is restored
        if (wallet == null || !removePicker(TAG_WALLET_PICKER)) {
            return;
        }
        long[] ids = mAdapter.getSelectedIds();
        String iso = wallet.getCurrency() != null ? wallet.getCurrency().getIso() : null;
        Context context = mListFragment.requireActivity();
        Resources resources = context.getResources();
        ContentResolver resolver = context.getApplicationContext().getContentResolver();
        sDeleteExecutor.execute(() -> {
            String message = buildMoveMessage(resources, resolver, ids, wallet.getId(), wallet.getName(), iso);
            sMainHandler.post(() -> {
                if (mListFragment.getView() == null) {
                    return;
                }
                if (!isSameSelection(mAdapter.getSelectedIds(), ids)) {
                    showSelectionChangedToast();
                    return;
                }
                showChangeDialog(mListFragment.requireActivity(), ids, message,
                        () -> moveTransactions(resolver, ids, wallet.getId(), iso));
            });
        });
    }

    public void onCategoryPicked(@Nullable Category category) {
        if (category == null || !removePicker(TAG_CATEGORY_PICKER)) {
            return;
        }
        long[] ids = mAdapter.getSelectedIds();
        Context context = mListFragment.requireActivity();
        Resources resources = context.getResources();
        ContentResolver resolver = context.getApplicationContext().getContentResolver();
        sDeleteExecutor.execute(() -> {
            String message = buildCategoryMessage(resources, resolver, ids, category.getId(), category.getName());
            sMainHandler.post(() -> {
                if (mListFragment.getView() == null) {
                    return;
                }
                if (!isSameSelection(mAdapter.getSelectedIds(), ids)) {
                    showSelectionChangedToast();
                    return;
                }
                showChangeDialog(mListFragment.requireActivity(), ids, message,
                        () -> changeCategory(resolver, ids, category));
            });
        });
    }

    private boolean removePicker(String tag) {
        if (!mListFragment.isAdded()) {
            return false;
        }
        FragmentManager fragmentManager = mListFragment.getChildFragmentManager();
        Fragment picker = fragmentManager.findFragmentByTag(tag);
        if (picker == null) {
            return false;
        }
        sMainHandler.post(() -> {
            if (!fragmentManager.isDestroyed()) {
                fragmentManager.beginTransaction().remove(picker).commitAllowingStateLoss();
            }
        });
        return true;
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

    private void showChangeDialog(Context context, long[] ids, String message, IntSupplier write) {
        ThemedDialog.buildMaterialDialog(context)
                .setTitle(R.string.title_warning)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> change(ids, write))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void change(long[] ids, IntSupplier write) {
        if (!isSameSelection(mAdapter.getSelectedIds(), ids)) {
            showSelectionChangedToast();
            return;
        }
        finish();
        sDeleteExecutor.execute(() -> {
            int skipped = write.getAsInt();
            sMainHandler.post(() -> {
                if (mListFragment.getView() == null || skipped == 0) {
                    return;
                }
                ThemedDialog.buildMaterialDialog(mListFragment.requireActivity())
                        .setTitle(R.string.title_error)
                        .setMessage(mListFragment.getResources().getQuantityString(
                                R.plurals.message_error_change_selected_transactions, skipped, skipped))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            });
        });
    }

    private void showSelectionChangedToast() {
        Toast.makeText(mListFragment.getContext(), R.string.message_selection_changed_nothing_changed, Toast.LENGTH_SHORT).show();
    }

    // the selection can have changed while the count waited behind a write
    /*package-local*/ static boolean isSameSelection(long[] selected, long[] snapshot) {
        if (snapshot.length == 0 || selected.length != snapshot.length) {
            return false;
        }
        Set<Long> members = new HashSet<>();
        for (long id : selected) {
            members.add(id);
        }
        for (long id : snapshot) {
            if (!members.contains(id)) {
                return false;
            }
        }
        return true;
    }

    /*package-local*/ static String buildMoveMessage(Resources resources, ContentResolver resolver, long[] ids,
                                                     long walletId, String walletName, @Nullable String walletIso) {
        CurrencyUnit to = walletIso != null ? CurrencyManager.getCurrency(walletIso) : null;
        int count = 0;
        int otherCurrency = 0;
        try (Cursor rows = queryStored(resolver, ids, Contract.Transaction.TYPE, Contract.Transaction.WALLET_ID,
                Contract.Transaction.WALLET_CURRENCY)) {
            while (rows != null && rows.moveToNext()) {
                if (rows.getLong(rows.getColumnIndexOrThrow(Contract.Transaction.WALLET_ID)) == walletId) {
                    continue;
                }
                CurrencyUnit from = movableFrom(rows, to);
                if (from != null) {
                    count++;
                    if (!from.getIso().equals(to.getIso())) {
                        otherCurrency++;
                    }
                }
            }
        }
        String message = resources.getQuantityString(R.plurals.message_move_selected_transactions,
                count, count, walletName);
        if (otherCurrency > 0) {
            message += " " + resources.getQuantityString(R.plurals.message_move_selected_transactions_other_currency,
                    otherCurrency, otherCurrency);
        }
        return message;
    }

    /*package-local*/ static String buildCategoryMessage(Resources resources, ContentResolver resolver, long[] ids,
                                                         long categoryId, String categoryName) {
        int count = 0;
        try (Cursor rows = queryStored(resolver, ids, Contract.Transaction.TYPE, Contract.Transaction.CATEGORY_ID)) {
            while (rows != null && rows.moveToNext()) {
                if (rows.getLong(rows.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_ID)) != categoryId
                        && isStandard(rows)) {
                    count++;
                }
            }
        }
        return resources.getQuantityString(R.plurals.message_change_category_selected_transactions,
                count, count, categoryName);
    }

    // the ids inlined and not bound, a select all can pass more than the 999 variables older SQLite allows
    private static Cursor queryStored(ContentResolver resolver, long[] ids, String... projection) {
        StringBuilder joined = new StringBuilder();
        for (long id : ids) {
            joined.append(joined.length() > 0 ? "," : "").append(id);
        }
        return resolver.query(DataContentProvider.CONTENT_TRANSACTIONS, projection,
                Contract.Transaction.ID + " IN (" + joined + ")", null, null);
    }

    // anything but a plain transaction belongs to a transfer, a debt or a saving that owns its wallet
    private static boolean isStandard(Cursor row) {
        return row.getInt(row.getColumnIndexOrThrow(Contract.Transaction.TYPE)) == Contract.TransactionType.STANDARD;
    }

    @Nullable
    private static CurrencyUnit movableFrom(Cursor row, @Nullable CurrencyUnit to) {
        CurrencyUnit from = CurrencyManager.getCurrency(row.getString(row.getColumnIndexOrThrow(Contract.Transaction.WALLET_CURRENCY)));
        return isStandard(row) && to != null ? from : null;
    }

    /*package-local*/ static int moveTransactions(ContentResolver resolver, long[] ids, long walletId,
                                                  @Nullable String walletIso) {
        CurrencyUnit to = walletIso != null ? CurrencyManager.getCurrency(walletIso) : null;
        String[] projection = new String[] {Contract.Transaction.TYPE, Contract.Transaction.WALLET_ID,
                Contract.Transaction.WALLET_CURRENCY, Contract.Transaction.MONEY};
        int skipped = 0;
        for (long id : ids) {
            Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, id);
            // the stored row and not the list, which can be older than it
            try (Cursor row = resolver.query(uri, projection, null, null, null)) {
                if (row == null || !row.moveToFirst()
                        || row.getLong(row.getColumnIndexOrThrow(Contract.Transaction.WALLET_ID)) == walletId) {
                    continue;
                }
                CurrencyUnit from = movableFrom(row, to);
                if (from == null) {
                    skipped++;
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put(Contract.Transaction.WALLET_ID, walletId);
                if (from.getDecimals() != to.getDecimals()) {
                    long money = row.getLong(row.getColumnIndexOrThrow(Contract.Transaction.MONEY));
                    values.put(Contract.Transaction.MONEY, MoneyFormatter.normalize(money, from.getDecimals(), to.getDecimals()));
                }
                resolver.update(uri, values, null, null);
            } catch (SQLiteDataException e) {
                skipped++;
            }
        }
        return skipped;
    }

    /*package-local*/ static int changeCategory(ContentResolver resolver, long[] ids, Category category) {
        String[] projection = new String[] {Contract.Transaction.TYPE, Contract.Transaction.CATEGORY_ID};
        int skipped = 0;
        for (long id : ids) {
            Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, id);
            try (Cursor row = resolver.query(uri, projection, null, null, null)) {
                if (row == null || !row.moveToFirst()
                        || row.getLong(row.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_ID)) == category.getId()) {
                    continue;
                }
                if (!isStandard(row)) {
                    skipped++;
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put(Contract.Transaction.CATEGORY_ID, category.getId());
                values.put(Contract.Transaction.DIRECTION, category.getDirection());
                resolver.update(uri, values, null, null);
            } catch (SQLiteDataException e) {
                skipped++;
            }
        }
        return skipped;
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
