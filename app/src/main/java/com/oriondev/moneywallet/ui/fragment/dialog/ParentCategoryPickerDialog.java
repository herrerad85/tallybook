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

package com.oriondev.moneywallet.ui.fragment.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Category;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.ui.activity.NewEditCategoryActivity;
import com.oriondev.moneywallet.ui.adapter.recycler.ParentCategorySelectorCursorAdapter;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;

/**
 * Created by andre on 19/03/2018.
 */
public class ParentCategoryPickerDialog extends DialogFragment implements ParentCategorySelectorCursorAdapter.Controller, LoaderManager.LoaderCallbacks<Cursor> {

    private static final String SS_SELECTED_CATEGORY = "ParentCategoryPickerDialog::SavedState::SelectedCategory";
    private static final String SS_CALLER_ID = "ParentCategoryPickerDialog::SavedState::CallerId";
    private static final String SS_CATEGORY_TYPE = "ParentCategoryPickerDialog::SavedState::CategoryType";

    private static final int DEFAULT_LOADER_ID = 1;

    public static ParentCategoryPickerDialog newInstance() {
        return new ParentCategoryPickerDialog();
    }

    private Category mCategory;
    private long mCallerId;
    private Contract.CategoryType mCategoryType;

    private Callback mCallback;

    private RecyclerView mRecyclerView;
    private TextView mMessageTextView;

    private ParentCategorySelectorCursorAdapter mCursorAdapter;

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Activity activity = getActivity();
        if (activity == null) {
            return super.onCreateDialog(savedInstanceState);
        }
        if (savedInstanceState != null) {
            mCategory = savedInstanceState.getParcelable(SS_SELECTED_CATEGORY);
            mCallerId = savedInstanceState.getLong(SS_CALLER_ID);
            mCategoryType = (Contract.CategoryType) savedInstanceState.getSerializable(SS_CATEGORY_TYPE);
        }
        View view = getLayoutInflater().inflate(R.layout.dialog_advanced_list, null);
        AlertDialog dialog = ThemedDialog.buildMaterialDialog(activity)
                .setTitle(R.string.dialog_category_picker_title)
                .setPositiveButton(R.string.action_new, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent(getActivity(), NewEditCategoryActivity.class));
                    }

                })
                .setNegativeButton(android.R.string.cancel, null)
                .setView(view)
                .create();
        mCursorAdapter = new ParentCategorySelectorCursorAdapter(this);
        mRecyclerView = view.findViewById(R.id.recycler_view);
        mMessageTextView = view.findViewById(R.id.message_text_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(activity));
        mRecyclerView.setAdapter(mCursorAdapter);
        mMessageTextView.setText(R.string.message_no_category_found);
        mRecyclerView.setVisibility(View.GONE);
        mMessageTextView.setVisibility(View.GONE);
        LoaderManager.getInstance(this).restartLoader(DEFAULT_LOADER_ID, null, this);
        return dialog;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(SS_SELECTED_CATEGORY, mCategory);
        outState.putLong(SS_CALLER_ID, mCallerId);
        outState.putSerializable(SS_CATEGORY_TYPE, mCategoryType);
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public void showPicker(FragmentManager fragmentManager, String tag, Category category, long callerId, Contract.CategoryType type) {
        mCategory = category;
        mCallerId = callerId;
        mCategoryType = type;
        show(fragmentManager, tag);
    }

    @Override
    public void onCategorySelected(Category category) {
        mCallback.onCategorySelected(category);
        dismiss();
    }

    @Override
    public boolean isCategorySelected(long id) {
        return mCategory != null && mCategory.getId() == id;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Activity activity = getActivity();
        if (activity != null) {
            Uri uri = DataContentProvider.CONTENT_CATEGORIES;
            String[] projection = new String[] {
                    Contract.Category.ID,
                    Contract.Category.NAME,
                    Contract.Category.ICON,
                    Contract.Category.TYPE
            };
            String where = Contract.Category.TYPE + " = ? AND " + Contract.Category.PARENT +
                    " IS NULL AND " + Contract.Category.ID + " != ?";
            String[] whereArgs = new String[] {String.valueOf(mCategoryType.getValue()), String.valueOf(mCallerId)};
            String sortOrder = Contract.Category.NAME + " COLLATE LOCALIZED";
            return new CursorLoader(activity, uri, projection, where, whereArgs, sortOrder);
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mCursorAdapter.swapCursor(data);
        if (data != null && data.getCount() > 0) {
            mRecyclerView.setVisibility(View.VISIBLE);
            mMessageTextView.setVisibility(View.GONE);
        } else {
            mRecyclerView.setVisibility(View.GONE);
            mMessageTextView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mCursorAdapter.swapCursor(null);
    }

    public interface Callback {

        void onCategorySelected(Category category);
    }
}