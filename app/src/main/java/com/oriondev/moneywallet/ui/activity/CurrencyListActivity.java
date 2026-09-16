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

package com.oriondev.moneywallet.ui.activity;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.ui.activity.base.SinglePanelSimpleListActivity;
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter;
import com.oriondev.moneywallet.ui.adapter.recycler.CurrencyCursorAdapter;
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView;
import com.oriondev.moneywallet.utils.CurrencyManager;

/**
 * Created by andrea on 03/02/18.
 */
public class CurrencyListActivity extends SinglePanelSimpleListActivity implements CurrencyCursorAdapter.CurrencyActionListener {

    public static final String ACTIVITY_MODE = "CurrencyListActivity::ActivityMode";
    public static final String RESULT_CURRENCY = "CurrencyListActivity::Result::SelectedCurrency";

    private static final String SS_QUERY = "CurrencyListActivity::SavedState::Query";

    public static final int CURRENCY_MANAGER = 0;
    public static final int CURRENCY_PICKER = 1;

    private int mActivityMode;

    private String mQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // The base runs the first load before the toolbar exists, so a restored query has to be
        // in the field already when it does.
        if (savedInstanceState != null) {
            mQuery = savedInstanceState.getString(SS_QUERY, "");
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SS_QUERY, mQuery);
    }

    @Override
    protected void onToolbarReady(Toolbar toolbar) {
        toolbar.setTitle(null);
        View view = getLayoutInflater().inflate(R.layout.layout_toolbar_search_view, toolbar, true);
        EditText searchEditText = view.findViewById(R.id.search_edit_text);
        searchEditText.setText(mQuery);
        searchEditText.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString();
                // The view state restore after a rotation sets the same text again and would
                // reload for nothing.
                if (!query.equals(mQuery)) {
                    mQuery = query;
                    recreateLoader();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }

        });
    }

    @Override
    protected void onPrepareRecyclerView(AdvancedRecyclerView recyclerView) {
        Intent intent = getIntent();
        if (intent != null) {
            mActivityMode = intent.getIntExtra(ACTIVITY_MODE, CURRENCY_MANAGER);
        } else {
            mActivityMode = CURRENCY_MANAGER;
        }
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setEmptyText(R.string.message_no_currency_found);
        if (mActivityMode == CURRENCY_MANAGER) {
            // The button covers the star of the last row otherwise, and nothing can scroll it out
            // from under it.
            recyclerView.getRecyclerView().addItemDecoration(new RecyclerView.ItemDecoration() {

                @Override
                public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                    boolean lastItem = parent.getChildAdapterPosition(view) == state.getItemCount() - 1;
                    outRect.bottom = lastItem ? getResources().getDimensionPixelSize(R.dimen.currency_list_fab_clearance) : 0;
                }

            });
        }
    }

    @Override
    protected AbstractCursorAdapter onCreateAdapter() {
        return new CurrencyCursorAdapter(this);
    }

    @Override
    @StringRes
    protected int getActivityTitleRes() {
        return R.string.title_activity_currency_list;
    }

    @Override
    protected boolean isFloatingActionButtonEnabled() {
        return mActivityMode == CURRENCY_MANAGER;
    }

    @Override
    protected void onFloatingActionButtonClick() {
        Intent intent = new Intent(this, NewEditCurrencyActivity.class);
        intent.putExtra(NewEditCurrencyActivity.MODE, NewEditCurrencyActivity.Mode.NEW_ITEM);
        startActivity(intent);
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Uri uri = DataContentProvider.CONTENT_CURRENCIES;
        String[] projection = new String[] {
                Contract.Currency.ISO,
                Contract.Currency.NAME,
                Contract.Currency.SYMBOL,
                Contract.Currency.DECIMALS,
                Contract.Currency.FAVOURITE
        };
        String selection = null;
        String[] selectionArgs = null;
        if (!mQuery.isEmpty()) {
            selection = Contract.Currency.NAME + " LIKE '%'||?||'%' OR " +
                    Contract.Currency.ISO + " LIKE '%'||?||'%'";
            selectionArgs = new String[] {mQuery, mQuery};
        }
        String sortBy = Contract.Currency.FAVOURITE + " DESC, " + Contract.Currency.NAME + " ASC";
        return new CursorLoader(this, uri, projection, selection, selectionArgs, sortBy);
    }

    @Override
    public void onCurrencyClick(String iso) {
        if (mActivityMode == CURRENCY_MANAGER) {
            Intent intent = new Intent(this, NewEditCurrencyActivity.class);
            intent.putExtra(NewEditCurrencyActivity.MODE, NewEditCurrencyActivity.Mode.EDIT_ITEM);
            intent.putExtra(NewEditCurrencyActivity.ISO, iso);
            startActivity(intent);
        } else if (mActivityMode == CURRENCY_PICKER) {
            CurrencyUnit currency = CurrencyManager.getCurrency(iso);
            Intent intent = new Intent();
            intent.putExtra(RESULT_CURRENCY, currency);
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    }

    @Override
    public void onCurrencyFavourite(String iso, boolean newValue) {
        ContentValues values = new ContentValues();
        values.put(Contract.Currency.FAVOURITE, newValue);
        Uri uri = Uri.withAppendedPath(DataContentProvider.CONTENT_CURRENCIES, iso);
        getContentResolver().update(uri, values, null, null);
    }
}