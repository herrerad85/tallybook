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

package com.oriondev.moneywallet.picker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.oriondev.moneywallet.model.Category;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.ui.activity.CategoryPickerActivity;
import com.oriondev.moneywallet.ui.fragment.dialog.MultiCategoryPickerDialog;
import com.oriondev.moneywallet.ui.fragment.dialog.ParentCategoryPickerDialog;

/**
 * Created by andrea on 02/02/18.
 */
public class CategoryPicker extends Fragment implements ParentCategoryPickerDialog.Callback, MultiCategoryPickerDialog.Callback {

    private static final String SS_CURRENT_CATEGORY = "ParentCategoryPicker::SavedState::CurrentCategory";
    private static final String SS_CURRENT_CATEGORIES = "ParentCategoryPicker::SavedState::CurrentCategories";
    private static final String SS_MULTI_CATEGORY = "ParentCategoryPicker::SavedState::MultiCategory";
    private static final String ARG_DEFAULT_CATEGORY = "ParentCategoryPicker::Arguments::DefaultCategory";
    private static final String ARG_DEFAULT_CATEGORIES = "ParentCategoryPicker::Arguments::DefaultCategories";
    private static final String ARG_MULTI_CATEGORY = "ParentCategoryPicker::Arguments::MultiCategory";

    private static final int REQUEST_CATEGORY_PICKER = 1;

    private Controller mController;
    private MultiCategoryController mMultiCategoryController;

    private boolean mMultiCategory;
    private Category mCurrentCategory;
    private Category[] mCurrentCategories;

    private ParentCategoryPickerDialog mParentCategoryPickerDialog;
    private MultiCategoryPickerDialog mMultiCategoryPickerDialog;

    public static CategoryPicker createPicker(FragmentManager fragmentManager, String tag, Category defaultCategory) {
        CategoryPicker categoryPicker = (CategoryPicker) fragmentManager.findFragmentByTag(tag);
        if (categoryPicker == null) {
            categoryPicker = new CategoryPicker();
            Bundle arguments = new Bundle();
            arguments.putBoolean(ARG_MULTI_CATEGORY, false);
            arguments.putParcelable(ARG_DEFAULT_CATEGORY, defaultCategory);
            categoryPicker.setArguments(arguments);
            fragmentManager.beginTransaction().add(categoryPicker, tag).commit();
        }
        return categoryPicker;
    }

    /**
     * A picker over any number of categories at once, for a budget that covers more than one.
     * It reports through {@link MultiCategoryController} and leaves
     * {@link #getCurrentCategory()} empty; the single category picker above is untouched and
     * still reports through {@link Controller}.
     *
     * @param fragmentManager manager the picker is added to.
     * @param tag name it is found again by.
     * @param defaultCategories categories chosen already, empty or null on a new item.
     * @return the picker, whether it was created here or found by its tag.
     */
    public static CategoryPicker createPicker(FragmentManager fragmentManager, String tag, Category[] defaultCategories) {
        CategoryPicker categoryPicker = (CategoryPicker) fragmentManager.findFragmentByTag(tag);
        if (categoryPicker == null) {
            categoryPicker = new CategoryPicker();
            Bundle arguments = new Bundle();
            arguments.putBoolean(ARG_MULTI_CATEGORY, true);
            arguments.putParcelableArray(ARG_DEFAULT_CATEGORIES, defaultCategories);
            categoryPicker.setArguments(arguments);
            fragmentManager.beginTransaction().add(categoryPicker, tag).commit();
        }
        return categoryPicker;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof Controller) {
            mController = (Controller) context;
        } else if (getParentFragment() instanceof Controller) {
            mController = (Controller) getParentFragment();
        }
        if (context instanceof MultiCategoryController) {
            mMultiCategoryController = (MultiCategoryController) context;
        } else if (getParentFragment() instanceof MultiCategoryController) {
            mMultiCategoryController = (MultiCategoryController) getParentFragment();
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mMultiCategory = savedInstanceState.getBoolean(SS_MULTI_CATEGORY);
            mCurrentCategory = savedInstanceState.getParcelable(SS_CURRENT_CATEGORY);
            mCurrentCategories = toCategories(savedInstanceState.getParcelableArray(SS_CURRENT_CATEGORIES));
        } else {
            Bundle arguments = getArguments();
            if (arguments != null) {
                mMultiCategory = arguments.getBoolean(ARG_MULTI_CATEGORY);
                mCurrentCategory = arguments.getParcelable(ARG_DEFAULT_CATEGORY);
                mCurrentCategories = toCategories(arguments.getParcelableArray(ARG_DEFAULT_CATEGORIES));
            } else {
                mCurrentCategory = null;
                mCurrentCategories = null;
            }
        }
        mParentCategoryPickerDialog = (ParentCategoryPickerDialog) getChildFragmentManager().findFragmentByTag(getDialogTag());
        if (mParentCategoryPickerDialog == null) {
            mParentCategoryPickerDialog = ParentCategoryPickerDialog.newInstance();
        }
        mParentCategoryPickerDialog.setCallback(this);
        mMultiCategoryPickerDialog = (MultiCategoryPickerDialog) getChildFragmentManager().findFragmentByTag(getMultiDialogTag());
        if (mMultiCategoryPickerDialog == null) {
            mMultiCategoryPickerDialog = MultiCategoryPickerDialog.newInstance();
        }
        mMultiCategoryPickerDialog.setCallback(this);
    }

    /**
     * A parcelable array comes back as {@code Parcelable[]} on some devices whatever was put in
     * it, so the elements are copied one at a time instead of casting the array itself. This is
     * the same guard {@code WalletPicker} carries for the same reason.
     *
     * @param parcelables array read out of a bundle, possibly null.
     * @return the categories in it, or null when there were none.
     */
    private static Category[] toCategories(Parcelable[] parcelables) {
        if (parcelables == null) {
            return null;
        }
        Category[] categories = new Category[parcelables.length];
        for (int i = 0; i < parcelables.length; i++) {
            categories[i] = (Category) parcelables[i];
        }
        return categories;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        fireCallbackSafely();
    }

    private void fireCallbackSafely() {
        if (mMultiCategory) {
            if (mMultiCategoryController != null) {
                mMultiCategoryController.onCategoryListChanged(getTag(), mCurrentCategories);
            }
        } else {
            if (mController != null) {
                mController.onCategoryChanged(getTag(), mCurrentCategory);
            }
        }
    }

    private String getDialogTag() {
        return getTag() + "::DialogFragment";
    }

    private String getMultiDialogTag() {
        return getTag() + "::MultiDialogFragment";
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SS_MULTI_CATEGORY, mMultiCategory);
        outState.putParcelable(SS_CURRENT_CATEGORY, mCurrentCategory);
        outState.putParcelableArray(SS_CURRENT_CATEGORIES, mCurrentCategories);
    }

    public boolean isSelected() {
        if (mMultiCategory) {
            return mCurrentCategories != null && mCurrentCategories.length > 0;
        }
        return mCurrentCategory != null;
    }

    public Category[] getCurrentCategories() {
        return mCurrentCategories;
    }

    public void showMultiPicker() {
        mMultiCategoryPickerDialog.showPicker(getChildFragmentManager(), getMultiDialogTag(), mCurrentCategories);
    }

    public void setCategory(Category category) {
        mCurrentCategory = category;
        fireCallbackSafely();
    }

    public Category getCurrentCategory() {
        return mCurrentCategory;
    }

    public void showPicker() {
        showPicker(true, false);
    }

    public void showPicker(boolean showSubCategories, boolean showSystemCategories) {
        Intent intent = new Intent(getActivity(), CategoryPickerActivity.class);
        intent.putExtra(CategoryPickerActivity.SHOW_SUB_CATEGORIES, showSubCategories);
        intent.putExtra(CategoryPickerActivity.SHOW_SYSTEM_CATEGORIES, showSystemCategories);
        startActivityForResult(intent, REQUEST_CATEGORY_PICKER);
    }

    /**
     * Offers the categories of one type alone, for a choice that has to follow a direction
     * already made elsewhere on the screen.
     */
    public void showPicker(Contract.CategoryType type) {
        Intent intent = new Intent(getActivity(), CategoryPickerActivity.class);
        intent.putExtra(CategoryPickerActivity.SHOW_SUB_CATEGORIES, true);
        intent.putExtra(CategoryPickerActivity.SHOW_SYSTEM_CATEGORIES, false);
        intent.putExtra(CategoryPickerActivity.ONLY_TYPE, type);
        startActivityForResult(intent, REQUEST_CATEGORY_PICKER);
    }

    public void showParentPicker(long categoryId, Contract.CategoryType type) {
        mParentCategoryPickerDialog.showPicker(getChildFragmentManager(), getDialogTag(), mCurrentCategory, categoryId, type);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CATEGORY_PICKER) {
            if (resultCode == Activity.RESULT_OK) {
                mCurrentCategory = data.getParcelableExtra(CategoryPickerActivity.RESULT_CATEGORY);
                fireCallbackSafely();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mController = null;
        mMultiCategoryController = null;
    }

    @Override
    public void onCategoriesSelected(Category[] categories) {
        mCurrentCategories = categories;
        fireCallbackSafely();
    }

    @Override
    public void onCategorySelected(Category category) {
        mCurrentCategory = category;
        fireCallbackSafely();
    }

    public interface Controller {

        void onCategoryChanged(String tag, Category category);
    }

    public interface MultiCategoryController {

        void onCategoryListChanged(String tag, Category[] categories);
    }
}