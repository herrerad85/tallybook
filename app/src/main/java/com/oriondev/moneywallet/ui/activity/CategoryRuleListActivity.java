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

import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.ui.activity.base.SinglePanelSimpleListActivity;
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter;
import com.oriondev.moneywallet.ui.adapter.recycler.CategoryRuleCursorAdapter;
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;

/**
 * The rules that file a transaction under a category from what its description holds. A tap opens
 * a rule, a long press offers to delete it, and the button adds one.
 */
public class CategoryRuleListActivity extends SinglePanelSimpleListActivity implements CategoryRuleCursorAdapter.ActionListener {

    @Override
    protected void onPrepareRecyclerView(AdvancedRecyclerView recyclerView) {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setEmptyText(R.string.message_no_category_rule_found);
    }

    @Override
    protected AbstractCursorAdapter onCreateAdapter() {
        return new CategoryRuleCursorAdapter(this);
    }

    @Override
    @StringRes
    protected int getActivityTitleRes() {
        return R.string.title_activity_category_rule_list;
    }

    @MenuRes
    @Override
    protected int onInflateMenu() {
        return R.menu.menu_sort_items;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_sort_items) {
            startActivity(new Intent(this, CategoryRuleSortActivity.class));
        }
        return false;
    }

    @Override
    protected boolean isFloatingActionButtonEnabled() {
        return true;
    }

    @Override
    protected void onFloatingActionButtonClick() {
        Intent intent = new Intent(this, NewEditCategoryRuleActivity.class);
        intent.putExtra(NewEditCategoryRuleActivity.MODE, NewEditCategoryRuleActivity.Mode.NEW_ITEM);
        startActivity(intent);
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
        // the order the rules are matched in, so the list reads the way the lookup does
        String sortBy = Contract.CategoryRule.INDEX + " ASC, " + Contract.CategoryRule.ID + " ASC";
        return new CursorLoader(this, uri, projection, null, null, sortBy);
    }

    @Override
    public void onCategoryRuleClick(long id) {
        Intent intent = new Intent(this, NewEditCategoryRuleActivity.class);
        intent.putExtra(NewEditCategoryRuleActivity.MODE, NewEditCategoryRuleActivity.Mode.EDIT_ITEM);
        intent.putExtra(NewEditCategoryRuleActivity.ID, id);
        startActivity(intent);
    }

    @Override
    public void onCategoryRuleLongClick(final long id, String pattern) {
        ThemedDialog.buildMaterialDialog(this)
                .setTitle(R.string.title_warning)
                .setMessage(getString(R.string.message_delete_category_rule, pattern))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORY_RULES, id);
                        getContentResolver().delete(uri, null, null);
                    }

                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
