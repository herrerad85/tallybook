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
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.collection.LongSparseArray;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Category;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.ui.adapter.recycler.ParentCategorySelectorCursorAdapter;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;

/**
 * Picks any number of categories at once, for a budget that covers more than one.
 *
 * The list holds to one direction. With nothing chosen it offers every income and expense
 * category; from the first choice on it offers only the ones of that direction, so a budget
 * mixing the two cannot be built at all instead of being refused once it is saved. Clearing the
 * last choice opens it back up. System categories are never offered, which is what the single
 * picker does by default too.
 */
public class MultiCategoryPickerDialog extends DialogFragment implements ParentCategorySelectorCursorAdapter.Controller, LoaderManager.LoaderCallbacks<Cursor> {

    private static final String SS_SELECTED_CATEGORIES = "MultiCategoryPickerDialog::SavedState::SelectedCategories";

    private static final int DEFAULT_LOADER_ID = 1;

    /**
     * By type, then each parent followed by its children.
     */
    public static final String SORT_ORDER = Contract.Category.TYPE + " ASC, " + Contract.Category.GROUP_INDEX +
            " ASC, " + Contract.Category.GROUP_NAME + " ASC, " + Contract.Category.GROUP_ID +
            " ASC, " + Contract.Category.PARENT + " IS NULL DESC, " + Contract.Category.NAME + " ASC";

    public static MultiCategoryPickerDialog newInstance() {
        return new MultiCategoryPickerDialog();
    }

    private Callback mCallback;

    private LongSparseArray<Category> mSelectedCategories;

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
            mSelectedCategories = new LongSparseArray<>();
            Parcelable[] parcelables = savedInstanceState.getParcelableArray(SS_SELECTED_CATEGORIES);
            if (parcelables != null) {
                for (Parcelable parcelable : parcelables) {
                    Category category = (Category) parcelable;
                    mSelectedCategories.append(category.getId(), category);
                }
            }
        }
        View view = getLayoutInflater().inflate(R.layout.dialog_advanced_list, null);
        AlertDialog dialog = ThemedDialog.buildMaterialDialog(activity)
                .setTitle(R.string.dialog_category_picker_title)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mCallback != null) {
                            mCallback.onCategoriesSelected(selectedCategories());
                        }
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
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArray(SS_SELECTED_CATEGORIES, selectedCategories());
    }

    private Category[] selectedCategories() {
        Category[] categories = new Category[mSelectedCategories.size()];
        for (int i = 0; i < mSelectedCategories.size(); i++) {
            categories[i] = mSelectedCategories.valueAt(i);
        }
        return categories;
    }

    /**
     * The direction every category on offer has to be, taken from the ones already chosen.
     *
     * @return the type they share, or null while nothing is chosen.
     */
    private Contract.CategoryType selectedType() {
        return mSelectedCategories.size() > 0 ? mSelectedCategories.valueAt(0).getType() : null;
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public void showPicker(FragmentManager fragmentManager, String tag, Category[] categories) {
        mSelectedCategories = new LongSparseArray<>();
        if (categories != null) {
            for (Category category : categories) {
                mSelectedCategories.append(category.getId(), category);
            }
        }
        show(fragmentManager, tag);
    }

    @NonNull
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
            Contract.CategoryType type = selectedType();
            String selection;
            String[] selectionArgs;
            if (type != null) {
                selection = Contract.Category.TYPE + " = ?";
                selectionArgs = new String[] {String.valueOf(type.getValue())};
            } else {
                selection = Contract.Category.TYPE + " != ?";
                selectionArgs = new String[] {String.valueOf(Contract.CategoryType.SYSTEM.getValue())};
            }
            return new CursorLoader(activity, uri, projection, selection, selectionArgs, SORT_ORDER);
        }
        throw new RuntimeException("Activity is null");
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        mCursorAdapter.swapCursor(cursor);
        if (cursor != null && cursor.getCount() > 0) {
            mRecyclerView.setVisibility(View.VISIBLE);
            mMessageTextView.setVisibility(View.GONE);
        } else {
            mRecyclerView.setVisibility(View.GONE);
            mMessageTextView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        mCursorAdapter.swapCursor(null);
    }

    @Override
    public void onCategorySelected(Category category) {
        Contract.CategoryType before = selectedType();
        if (before != null && category.getType() != before
                && mSelectedCategories.indexOfKey(category.getId()) < 0) {
            // the list is narrowed to one direction by the loader, and a loader runs on another
            // thread, so a second tap landing before the narrowed rows arrive would otherwise put a
            // category of the other direction in beside the first
            return;
        }
        if (mSelectedCategories.indexOfKey(category.getId()) >= 0) {
            mSelectedCategories.remove(category.getId());
        } else {
            mSelectedCategories.append(category.getId(), category);
        }
        if (before != selectedType()) {
            // the first choice narrows the list to that direction, and dropping the last opens it
            // back up, so the rows on offer have to be asked for again
            LoaderManager.getInstance(this).restartLoader(DEFAULT_LOADER_ID, null, this);
        } else {
            mCursorAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean isCategorySelected(long id) {
        return mSelectedCategories.indexOfKey(id) >= 0;
    }

    public interface Callback {

        void onCategoriesSelected(Category[] categories);
    }
}
