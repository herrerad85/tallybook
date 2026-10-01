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

package com.oriondev.moneywallet.ui.fragment.multipanel;

import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.background.SearchRailLoader;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public class WalletSearchEditorFragment extends SearchEditorFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final int LOADER_ID = 1;

    private ViewGroup mList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search_editor_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mList = view.findViewById(R.id.search_list_linear_layout);
        CompoundButton transfersOnlySwitch = view.findViewById(R.id.search_transfers_only_switch);
        transfersOnlySwitch.setVisibility(View.VISIBLE);
        transfersOnlySwitch.setChecked(getFilter().isTransfersOnly());
        transfersOnlySwitch.setOnCheckedChangeListener((button, isChecked) -> {
            // a copy, since the setter clears the set this views
            getFilter().setWallets(new TreeSet<>(getFilter().getWalletIds()), isChecked);
            onFilterChanged();
        });
        bindClear(view, () -> getFilter().setWallets(Collections.emptySet(), false));
        LoaderManager.getInstance(this).initLoader(LOADER_ID, null, this);
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {
        String[] projection = new String[] {Contract.Wallet.ID, Contract.Wallet.NAME,
                Contract.Wallet.ICON, Contract.Wallet.ARCHIVED};
        return new CursorLoader(requireContext(), DataContentProvider.CONTENT_WALLETS, projection, null, null, SearchRailLoader.WALLET_SORT_ORDER);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        mList.removeAllViews();
        if (cursor == null) {
            return;
        }
        Set<Long> ticked = getFilter().getWalletIds();
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            long id = cursor.getLong(0);
            String name = cursor.getString(1);
            boolean archived = cursor.getInt(3) == 1;
            String description = archived ? getString(R.string.setting_default_wallet_archived, name) : null;
            addCheckRow(mList, cursor.getString(2), name, null, description, archived, ticked.contains(id), (button, isChecked) -> {
                Set<Long> ids = new TreeSet<>(getFilter().getWalletIds());
                if (isChecked) {
                    ids.add(id);
                } else {
                    ids.remove(id);
                }
                getFilter().setWallets(ids, getFilter().isTransfersOnly());
                onFilterChanged();
            });
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
    }
}
