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

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.background.IconGroupLoader;
import com.oriondev.moneywallet.model.Icon;
import com.oriondev.moneywallet.model.IconGroup;
import com.oriondev.moneywallet.ui.activity.base.SinglePanelActivity;
import com.oriondev.moneywallet.ui.adapter.recycler.IconAdapter;
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView;

import java.util.List;

/**
 * Created by andrea on 03/02/18.
 */
public class IconListActivity extends SinglePanelActivity implements SwipeRefreshLayout.OnRefreshListener, LoaderManager.LoaderCallbacks<List<IconGroup>>, IconAdapter.Controller {

    public static final String CURRENT_ICON_TYPE = "IconListActivity::Arguments::CurrentIconType";
    public static final String RESULT_ICON = "IconListActivity::Result::SelectedIcon";
    public static final String RESULT_ACTION = "IconListActivity::Result::Action";

    public static final String ACTION_CHANGE_BG_COLOR = "IconListActivity::Action::ChangeBackgroundColor";
    public static final String ACTION_REMOVE_ICON = "IconListActivity::Action::RemoveIcon";

    private static final int ICON_LOADER_ID = 46;

    private static final int ICON_WIDTH_DP = 64;

    private static final String SS_QUERY = "IconListActivity::SavedState::Query";

    private AdvancedRecyclerView mAdvancedRecyclerView;
    private IconAdapter mAdapter;

    private int mIconSpan;

    private String mQuery = "";

    private boolean mLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // The adapter takes the query when the panel is built, so a restored query has to be in
        // the field already by then.
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
                // filter for nothing.
                if (!query.equals(mQuery)) {
                    mQuery = query;
                    mAdapter.setQuery(mQuery);
                    // while the loader runs its own state is showing and onLoadFinished settles it
                    if (!mLoading) {
                        updateListState();
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }

        });
    }

    @Override
    protected void onCreatePanelView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_activity_single_panel_body_list, parent, true);
        mAdvancedRecyclerView = view.findViewById(R.id.advanced_recycler_view);
        mIconSpan = getIconSpanCount();
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, mIconSpan);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {

            @Override
            public int getSpanSize(int position) {
                return mAdapter.isHeader(position) ? mIconSpan : 1;
            }

        });
        mAdvancedRecyclerView.setLayoutManager(gridLayoutManager);
        mAdvancedRecyclerView.setEmptyText(R.string.message_no_icon_found);
        mAdapter = new IconAdapter(this);
        mAdapter.setQuery(mQuery);
        mAdvancedRecyclerView.setAdapter(mAdapter);
        mAdvancedRecyclerView.setOnRefreshListener(this);
        mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.LOADING);
    }

    private int getIconSpanCount() {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        float dpWidth = displayMetrics.widthPixels / displayMetrics.density;
        // on large screen the panel width is not equal to the display width
        // so we have to dynamically calculate the panel width
        int spanCount;
        if (dpWidth < 600) {
            // small screen, probably a smart phone
            spanCount = (int) (dpWidth / ICON_WIDTH_DP);
        } else if (dpWidth >= 600 && dpWidth < 840) {
            // small tablet screen, the max panel width is 540dp
            spanCount = 540 / ICON_WIDTH_DP;
        } else {
            // large tablet screen, the max panel width is 640dp
            spanCount = 640 / ICON_WIDTH_DP;
        }
        return spanCount;
    }

    @Override
    protected void onViewCreated(Bundle savedInstanceState) {
        mLoading = true;
        getSupportLoaderManager().restartLoader(ICON_LOADER_ID, null, this);
    }

    @Override
    protected int getActivityTitleRes() {
        return R.string.title_activity_icon_list;
    }

    @Override
    protected boolean isFloatingActionButtonEnabled() {
        return false;
    }

    @Override
    @MenuRes
    protected int onInflateMenu() {
        return R.menu.menu_icon_list;
    }

    @Override
    protected void onMenuCreated(Menu menu) {
        Icon.Type type = (Icon.Type) getIntent().getSerializableExtra(CURRENT_ICON_TYPE);
        menu.findItem(R.id.action_change_bg_color).setVisible(type == Icon.Type.COLOR);
        menu.findItem(R.id.action_remove_icon).setVisible(type == Icon.Type.RESOURCE);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_change_bg_color) {
            finishWithAction(ACTION_CHANGE_BG_COLOR);
            return true;
        } else if (itemId == R.id.action_remove_icon) {
            finishWithAction(ACTION_REMOVE_ICON);
            return true;
        }
        return false;
    }

    private void finishWithAction(String action) {
        Intent intent = new Intent();
        intent.putExtra(RESULT_ACTION, action);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void onIconClick(Icon icon) {
        Intent intent = new Intent();
        intent.putExtra(RESULT_ICON, icon);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void onRefresh() {
        mLoading = true;
        getSupportLoaderManager().restartLoader(ICON_LOADER_ID, null, this);
        mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.REFRESHING);
    }

    @NonNull
    @Override
    public Loader<List<IconGroup>> onCreateLoader(int id, Bundle args) {
        return new IconGroupLoader(this);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<List<IconGroup>> loader, List<IconGroup> iconGroups) {
        mLoading = false;
        mAdapter.setIconGroups(iconGroups);
        updateListState();
    }

    private void updateListState() {
        if (mAdapter.getItemCount() > 0) {
            mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.READY);
        } else {
            mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.EMPTY);
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<List<IconGroup>> loader) {
        // nothing to release
    }
}