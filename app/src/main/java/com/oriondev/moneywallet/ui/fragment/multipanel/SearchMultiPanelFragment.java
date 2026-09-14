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

package com.oriondev.moneywallet.ui.fragment.multipanel;

import android.app.Activity;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Category;
import com.oriondev.moneywallet.model.Wallet;
import com.oriondev.moneywallet.picker.CategoryPicker;
import com.oriondev.moneywallet.picker.WalletPicker;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter;
import com.oriondev.moneywallet.ui.adapter.recycler.TransactionCursorAdapter;
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelCursorListItemFragment;
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment;
import com.oriondev.moneywallet.ui.fragment.base.TransactionSelectionMode;
import com.oriondev.moneywallet.ui.fragment.secondary.TransactionItemFragment;
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The search screen. A result opens in the same transaction panel the transaction list and the
 * calendar open, so a result can be duplicated and deleted and not only edited.
 */
public class SearchMultiPanelFragment extends MultiPanelCursorListItemFragment implements TransactionCursorAdapter.ActionListener, WalletPicker.SingleWalletController, CategoryPicker.Controller {

    private static final String SS_SEARCH_FLAGS = "SearchMultiPanelFragment::SavedState::SearchFlags";

    private static final String SECONDARY_PANEL_TAG = "SearchMultiPanelFragment::Tag::SecondaryPanel";

    private boolean[] mSearchFlags;

    /**
     * The loader reads this instead of its argument bundle. The base starts the loader while it
     * builds the primary panel, which is before setupPrimaryToolbar inflates the search box, so
     * the first load runs against the empty query and matches every transaction.
     */
    private String mQuery = "";

    private EditText mSearchEditText;
    private TransactionSelectionMode mSelectionMode;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mSearchFlags = savedInstanceState.getBooleanArray(SS_SEARCH_FLAGS);
        } else {
            mSearchFlags = new boolean[] {
                    true,   // description
                    true,   // category
                    true,   // date
                    true,   // money
                    true,   // note
                    true,   // event
                    true    // place
            };
        }
    }

    @Override
    protected void setupPrimaryToolbar(Toolbar toolbar) {
        super.setupPrimaryToolbar(toolbar);
        // The search box fills the toolbar and stands where the title would be, so the title the
        // base just set is cleared here to keep the two off the same row.
        toolbar.setTitle(null);
        View view = getLayoutInflater().inflate(R.layout.layout_toolbar_search_view, toolbar, true);
        EditText searchEditText = view.findViewById(R.id.search_edit_text);
        mSearchEditText = searchEditText;
        searchEditText.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mQuery = s.toString();
                recreateLoader();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }

        });
    }

    @Override
    protected void onPrepareRecyclerView(AdvancedRecyclerView recyclerView) {
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setEmptyText(R.string.message_no_transaction_found);
    }

    @Override
    protected AbstractCursorAdapter onCreateAdapter() {
        TransactionCursorAdapter adapter = new TransactionCursorAdapter(this);
        mSelectionMode = new TransactionSelectionMode(this, this, adapter);
        return adapter;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mSelectionMode.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onSelectionModeChanged(boolean active) {
        mSearchEditText.setVisibility(active ? View.GONE : View.VISIBLE);
    }

    @Override
    protected SecondaryPanelFragment onCreateSecondaryPanel() {
        return new TransactionItemFragment();
    }

    @Override
    protected String getSecondaryFragmentTag() {
        return SECONDARY_PANEL_TAG;
    }

    @Override
    protected int getTitleRes() {
        return R.string.title_activity_search_transaction;
    }

    @Override
    protected boolean isFloatingActionButtonEnabled() {
        return false;
    }

    @Override
    protected int onInflateMenu() {
        return R.menu.menu_search_activity;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_filter) {
            showFilterDialog();
        }
        return false;
    }

    private void showFilterDialog() {
        String[] items = new String[] {
                getString(R.string.hint_description),
                getString(R.string.hint_category),
                getString(R.string.hint_date),
                getString(R.string.hint_money),
                getString(R.string.hint_note),
                getString(R.string.hint_event),
                getString(R.string.hint_place)
        };
        final boolean[] checked = mSearchFlags.clone();
        ThemedDialog.buildMaterialDialog(getActivity())
                .setTitle(R.string.dialog_filter_search_title)
                .setMultiChoiceItems(items, checked, new DialogInterface.OnMultiChoiceClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }

                })
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mSearchFlags = checked;
                        recreateLoader();
                    }

                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Activity activity = getActivity();
        if (activity != null) {
            Uri uri = DataContentProvider.CONTENT_TRANSACTIONS;
            StringBuilder selection = new StringBuilder();
            if (mSearchFlags[0]) {appendSelection(selection, Contract.Transaction.DESCRIPTION);}
            if (mSearchFlags[1]) {appendSelection(selection, Contract.Transaction.CATEGORY_NAME);}
            if (mSearchFlags[2]) {appendSelection(selection, Contract.Transaction.DATE);}
            if (mSearchFlags[3]) {appendSelection(selection, Contract.Transaction.MONEY);}
            if (mSearchFlags[4]) {appendSelection(selection, Contract.Transaction.NOTE);}
            if (mSearchFlags[5]) {appendSelection(selection, Contract.Transaction.EVENT_NAME);}
            if (mSearchFlags[6]) {appendSelection(selection, Contract.Transaction.PLACE_NAME);}
            String[] selectionArgs = getSelectionArguments(mQuery);
            String sortOrder = Contract.Transaction.DATE + " DESC";
            return new CursorLoader(activity, uri, null, selection.toString(), selectionArgs, sortOrder);
        }
        return null;
    }

    private void appendSelection(StringBuilder builder, String column) {
        if (builder.length() != 0) {
            builder.append(" OR ");
        }
        builder.append(column);
        builder.append(" LIKE '%'||?||'%'");
    }

    private String[] getSelectionArguments(String query) {
        List<String> arguments = new ArrayList<>();
        for (boolean flag : mSearchFlags) {
            if (flag) {
                arguments.add(query);
            }
        }
        return arguments.toArray(new String[arguments.size()]);
    }

    @Override
    public void onHeaderClick(Date startDate, Date endDate) {
        // this method will never be called by the adapter!
    }

    @Override
    public void onTransactionClick(long id) {
        showItemId(id);
        showSecondaryPanel();
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
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBooleanArray(SS_SEARCH_FLAGS, mSearchFlags);
        if (mSelectionMode != null) {
            mSelectionMode.onSaveInstanceState(outState);
        }
    }
}
