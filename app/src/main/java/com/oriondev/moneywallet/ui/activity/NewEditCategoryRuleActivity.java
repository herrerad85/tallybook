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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Category;
import com.oriondev.moneywallet.picker.CategoryPicker;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.ui.view.text.MaterialEditText;
import com.oriondev.moneywallet.ui.view.text.NonEmptyTextValidator;
import com.oriondev.moneywallet.ui.view.text.Validator;
import com.oriondev.moneywallet.utils.IconLoader;

/**
 * One rule, a snippet to look for in a transaction description and the category a transaction
 * holding it is filed under.
 */
public class NewEditCategoryRuleActivity extends NewEditItemActivity implements CategoryPicker.Controller {

    private static final String TAG_CATEGORY_PICKER = "NewEditCategoryRuleActivity::Tag::CategoryPicker";

    private MaterialEditText mPatternEditText;
    private MaterialEditText mCategoryEditText;

    private CategoryPicker mCategoryPicker;

    @Override
    protected void onCreateHeaderView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_header_new_edit_category_rule, parent, true);
        mPatternEditText = view.findViewById(R.id.pattern_edit_text);
        mPatternEditText.addValidator(new NonEmptyTextValidator(this, R.string.error_input_pattern_not_valid));
    }

    @Override
    protected void onCreatePanelView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_panel_new_edit_category_rule, parent, true);
        mCategoryEditText = view.findViewById(R.id.category_edit_text);
        mCategoryEditText.setTextViewMode(true);
        mCategoryEditText.addValidator(new Validator() {

            @NonNull
            @Override
            public String getErrorMessage() {
                return getString(R.string.error_input_missing_category);
            }

            @Override
            public boolean isValid(@NonNull CharSequence charSequence) {
                return mCategoryPicker.isSelected();
            }

            @Override
            public boolean autoValidate() {
                return false;
            }

        });
        mCategoryEditText.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                mCategoryPicker.showPicker();
            }

        });
    }

    @Override
    protected void onViewCreated(Bundle savedInstanceState) {
        super.onViewCreated(savedInstanceState);
        Category category = null;
        if (savedInstanceState == null && getMode() == Mode.EDIT_ITEM) {
            ContentResolver contentResolver = getContentResolver();
            Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORY_RULES, getItemId());
            String[] projection = new String[] {
                    Contract.CategoryRule.PATTERN,
                    Contract.CategoryRule.CATEGORY_ID
            };
            Cursor cursor = contentResolver.query(uri, projection, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    mPatternEditText.setText(cursor.getString(cursor.getColumnIndexOrThrow(Contract.CategoryRule.PATTERN)));
                    category = readCategory(contentResolver,
                            cursor.getLong(cursor.getColumnIndexOrThrow(Contract.CategoryRule.CATEGORY_ID)));
                }
                cursor.close();
            }
        }
        FragmentManager fragmentManager = getSupportFragmentManager();
        mCategoryPicker = CategoryPicker.createPicker(fragmentManager, TAG_CATEGORY_PICKER, category);
    }

    /**
     * The category a stored rule names, read in full so the picker opens on it.
     *
     * @param contentResolver resolver to read through.
     * @param categoryId id the rule holds.
     * @return the category, or null when it is no longer there.
     */
    private static Category readCategory(ContentResolver contentResolver, long categoryId) {
        Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, categoryId);
        String[] projection = new String[] {
                Contract.Category.ID,
                Contract.Category.NAME,
                Contract.Category.ICON,
                Contract.Category.TYPE,
                Contract.Category.TAG
        };
        Cursor cursor = contentResolver.query(uri, projection, null, null, null);
        Category category = null;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                category = new Category(
                        cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Category.ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.NAME)),
                        IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.ICON))),
                        Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Category.TYPE))),
                        cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.TAG))
                );
            }
            cursor.close();
        }
        return category;
    }

    @Override
    protected int getActivityTileRes(Mode mode) {
        switch (mode) {
            case NEW_ITEM:
                return R.string.title_activity_new_category_rule;
            case EDIT_ITEM:
                return R.string.title_activity_edit_category_rule;
            default:
                return -1;
        }
    }

    @Override
    protected void onSaveChanges(Mode mode) {
        if (mPatternEditText.validate() && mCategoryEditText.validate()) {
            ContentResolver contentResolver = getContentResolver();
            ContentValues contentValues = new ContentValues();
            contentValues.put(Contract.CategoryRule.PATTERN, mPatternEditText.getTextAsString().trim());
            contentValues.put(Contract.CategoryRule.CATEGORY_ID, mCategoryPicker.getCurrentCategory().getId());
            switch (mode) {
                case NEW_ITEM:
                    // no index is named, so the insert appends the rule after the ones stored
                    contentResolver.insert(DataContentProvider.CONTENT_CATEGORY_RULES, contentValues);
                    break;
                case EDIT_ITEM:
                    Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORY_RULES, getItemId());
                    contentResolver.update(uri, contentValues, null, null);
                    break;
            }
            setResult(RESULT_OK);
            finish();
        }
    }

    @Override
    public void onCategoryChanged(String tag, Category category) {
        mCategoryEditText.setText(category != null ? category.getName() : null);
    }
}
