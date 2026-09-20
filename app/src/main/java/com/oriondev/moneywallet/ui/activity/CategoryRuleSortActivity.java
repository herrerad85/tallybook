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
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.ui.activity.base.SinglePanelSimpleListActivity;
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter;
import com.oriondev.moneywallet.ui.adapter.recycler.CategoryRuleSortCursorAdapter;
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView;

import java.util.List;

/**
 * The order the category rules are matched in, dragged into place by hand. The rule nearest the
 * top is the one a description matching more than one rule is filed under.
 */
public class CategoryRuleSortActivity extends SinglePanelSimpleListActivity implements CategoryRuleSortCursorAdapter.CategoryRuleSortListener {

    private ItemTouchHelper mItemTouchHelper;

    @Override
    protected int getActivityTitleRes() {
        return R.string.title_activity_category_rule_sort;
    }

    @MenuRes
    @Override
    protected int onInflateMenu() {
        return R.menu.menu_save_changes;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_save_changes) {
            saveChanges();
        }
        return false;
    }

    @Override
    protected boolean isFloatingActionButtonEnabled() {
        // floating action button is not used here
        return false;
    }

    @Override
    protected void onPrepareRecyclerView(AdvancedRecyclerView recyclerView) {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        // the list screen's string tells the user to add a rule and there is no button for that
        // here. The sort menu item is offered whatever the rule count is, so a user with no rules
        // reaches this screen
        recyclerView.setEmptyText(R.string.message_no_category_rule_found_sort);
        recyclerView.setEnabled(false);
        // setup item touch helper and attach it to the recycler view
        mItemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {

            @Override
            public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
                return makeMovementFlags(dragFlags, 0);
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                RecyclerView.Adapter adapter = recyclerView.getAdapter();
                if (adapter instanceof CategoryRuleSortCursorAdapter) {
                    return ((CategoryRuleSortCursorAdapter) adapter).moveItem(
                            viewHolder.getAdapterPosition(),
                            target.getAdapterPosition()
                    );
                }
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                // not used here, only drag and drop is handled
            }

            @Override
            public boolean isLongPressDragEnabled() {
                // disable long press to drag: the only way to drag the rules is to click and
                // drag the appropriate action image view at the end of it
                return false;
            }

        });
        mItemTouchHelper.attachToRecyclerView(recyclerView.getRecyclerView());
    }

    @Override
    protected AbstractCursorAdapter onCreateAdapter() {
        return new CategoryRuleSortCursorAdapter(this);
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Uri uri = DataContentProvider.CONTENT_CATEGORY_RULES;
        String[] projection = new String[] {
                Contract.CategoryRule.ID,
                Contract.CategoryRule.PATTERN,
                Contract.CategoryRule.CATEGORY_NAME
        };
        String sortOrder = Contract.CategoryRule.INDEX + " ASC, " + Contract.CategoryRule.ID + " ASC";
        return new CursorLoader(this, uri, projection, null, null, sortOrder);
    }

    @Override
    public void onCategoryRuleDragStarted(RecyclerView.ViewHolder viewHolder) {
        mItemTouchHelper.startDrag(viewHolder);
    }

    private void saveChanges() {
        Uri baseUri = DataContentProvider.CONTENT_CATEGORY_RULES;
        ContentResolver contentResolver = getContentResolver();
        RecyclerView.Adapter adapter = getAdvancedRecyclerView().getRecyclerView().getAdapter();
        if (adapter instanceof CategoryRuleSortCursorAdapter) {
            List<Long> ruleIdsList = ((CategoryRuleSortCursorAdapter) adapter).getSortedCategoryRuleIds();
            for (int i = 0; i < ruleIdsList.size(); i++) {
                Long ruleId = ruleIdsList.get(i);
                Uri uri = ContentUris.withAppendedId(baseUri, ruleId);
                ContentValues contentValues = new ContentValues();
                contentValues.put(Contract.CategoryRule.INDEX, i + 1);
                contentResolver.update(uri, contentValues, null, null);
            }
        }
        setResult(Activity.RESULT_OK);
        finish();
    }
}
