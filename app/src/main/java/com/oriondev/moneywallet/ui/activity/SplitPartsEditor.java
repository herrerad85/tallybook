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

package com.oriondev.moneywallet.ui.activity;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Category;
import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.picker.CategoryPicker;
import com.oriondev.moneywallet.picker.MoneyPicker;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.SplitPartsCodec;
import com.oriondev.moneywallet.ui.view.text.MaterialEditText;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;
import com.oriondev.moneywallet.utils.MoneyFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code mPartCategoryTarget} and {@code mPartMoneyTarget} hold the part each part picker writes
 * into, the size of the list while one is being added and -1 for none. One each, because a
 * picker hands its last value back again after a recreate, and it has to land on the part it was
 * opened for.
 */
/*package-local*/ class SplitPartsEditor {

    private static final String SS_PART_CATEGORIES = "NewEditTransactionActivity::SavedState::PartCategories";
    private static final String SS_PART_MONEY = "NewEditTransactionActivity::SavedState::PartMoney";
    private static final String SS_PART_NOTES = "NewEditTransactionActivity::SavedState::PartNotes";
    private static final String SS_PART_CATEGORY_TARGET = "NewEditTransactionActivity::SavedState::PartCategoryTarget";
    private static final String SS_PART_MONEY_TARGET = "NewEditTransactionActivity::SavedState::PartMoneyTarget";

    private final Activity mActivity;
    private final ViewGroup mSplitPartsLayout;
    private final MaterialEditText mSplitRemainingEditText;
    private final MaterialEditText mSplitEditText;
    private final MaterialEditText mCategoryEditText;

    private TransactionEditorRules mRules;
    private MoneyPicker mMoneyPicker;
    private CategoryPicker mCategoryPicker;
    private CategoryPicker mPartCategoryPicker;
    private MoneyPicker mPartMoneyPicker;

    private final List<Part> mParts = new ArrayList<>();

    private int mPartCategoryTarget = -1;
    private int mPartMoneyTarget = -1;

    private final MoneyFormatter mMoneyFormatter = MoneyFormatter.getInstance();

    /*package-local*/ SplitPartsEditor(Activity activity, ViewGroup splitPartsLayout,
                                       MaterialEditText splitRemainingEditText,
                                       MaterialEditText splitEditText, MaterialEditText categoryEditText) {
        mActivity = activity;
        mSplitPartsLayout = splitPartsLayout;
        mSplitRemainingEditText = splitRemainingEditText;
        mSplitEditText = splitEditText;
        mCategoryEditText = categoryEditText;
        mSplitRemainingEditText.setTextViewMode(true);
        mSplitRemainingEditText.setClickable(false);
        mSplitEditText.setTextViewMode(true);
        mSplitEditText.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                pickPartCategory(mParts.size());
            }

        });
    }

    /*package-local*/ void attach(TransactionEditorRules rules, MoneyPicker moneyPicker,
                                  CategoryPicker categoryPicker, CategoryPicker partCategoryPicker,
                                  MoneyPicker partMoneyPicker) {
        mRules = rules;
        mMoneyPicker = moneyPicker;
        mCategoryPicker = categoryPicker;
        mPartCategoryPicker = partCategoryPicker;
        mPartMoneyPicker = partMoneyPicker;
        showParts();
    }

    /*package-local*/ void addPart(Category category, long money, String note) {
        mParts.add(new Part(category, money, note));
    }

    /*package-local*/ void restoreState(Bundle savedInstanceState) {
        ArrayList<Category> partCategories = savedInstanceState.getParcelableArrayList(SS_PART_CATEGORIES);
        long[] partMoney = savedInstanceState.getLongArray(SS_PART_MONEY);
        String[] partNotes = savedInstanceState.getStringArray(SS_PART_NOTES);
        if (partCategories != null && partMoney != null && partNotes != null) {
            for (int i = 0; i < partCategories.size(); i++) {
                mParts.add(new Part(partCategories.get(i), partMoney[i], partNotes[i]));
            }
        }
        mPartCategoryTarget = savedInstanceState.getInt(SS_PART_CATEGORY_TARGET, -1);
        mPartMoneyTarget = savedInstanceState.getInt(SS_PART_MONEY_TARGET, -1);
    }

    /*package-local*/ void saveState(Bundle outState) {
        ArrayList<Category> partCategories = new ArrayList<>();
        long[] partMoney = new long[mParts.size()];
        String[] partNotes = new String[mParts.size()];
        for (int i = 0; i < mParts.size(); i++) {
            partCategories.add(mParts.get(i).mCategory);
            partMoney[i] = mParts.get(i).mMoney;
            partNotes[i] = mParts.get(i).mNote;
        }
        outState.putParcelableArrayList(SS_PART_CATEGORIES, partCategories);
        outState.putLongArray(SS_PART_MONEY, partMoney);
        outState.putStringArray(SS_PART_NOTES, partNotes);
        outState.putInt(SS_PART_CATEGORY_TARGET, mPartCategoryTarget);
        outState.putInt(SS_PART_MONEY_TARGET, mPartMoneyTarget);
    }

    /**
     * Draws the parts, what they leave under the main category, and the action that adds one.
     * The amounts are minor units of the wallet on screen, the same ones the total is held in, so
     * a wallet of another currency shows the same digits and nothing is rescaled.
     */
    /*package-local*/ void showParts() {
        boolean offered = mRules.offersSplit();
        boolean split = offered && !mParts.isEmpty();
        mSplitEditText.setVisibility(offered ? View.VISIBLE : View.GONE);
        mSplitPartsLayout.setVisibility(split ? View.VISIBLE : View.GONE);
        mSplitRemainingEditText.setVisibility(split ? View.VISIBLE : View.GONE);
        if (!offered) {
            return;
        }
        mSplitEditText.setHint(split ? R.string.hint_split_add_part : R.string.hint_split_into_categories);
        mSplitPartsLayout.removeAllViews();
        CurrencyUnit currency = mMoneyPicker.getCurrentCurrency();
        long remaining = mMoneyPicker.getCurrentMoney();
        for (int i = 0; i < mParts.size(); i++) {
            Part part = mParts.get(i);
            MaterialEditText view = (MaterialEditText) mActivity.getLayoutInflater().inflate(
                    R.layout.layout_split_part, mSplitPartsLayout, false);
            view.setTextViewMode(true);
            view.setFloatingLabelText(nameOf(part.mCategory));
            String money = mMoneyFormatter.getNotTintedString(currency, part.mMoney);
            view.setText(TextUtils.isEmpty(part.mNote) ? money : mActivity.getString(R.string.split_part, money, part.mNote));
            final int index = i;
            view.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    showPartActions(index);
                }

            });
            mSplitPartsLayout.addView(view);
            remaining -= part.mMoney;
        }
        mSplitRemainingEditText.setFloatingLabelText(mActivity.getString(R.string.hint_split_remaining,
                nameOf(mCategoryPicker.getCurrentCategory())));
        mSplitRemainingEditText.setText(mMoneyFormatter.getNotTintedString(currency, remaining));
    }

    private static String nameOf(Category category) {
        return category != null && category.getName() != null ? category.getName() : "?";
    }

    private void showPartActions(final int index) {
        String[] actions = new String[] {
                mActivity.getString(R.string.hint_category),
                mActivity.getString(R.string.hint_money),
                mActivity.getString(R.string.hint_note)
        };
        ThemedDialog.buildMaterialDialog(mActivity)
                .setTitle(nameOf(mParts.get(index).mCategory))
                .setItems(actions, (dialog, which) -> {
                    if (index >= mParts.size()) {
                        return;
                    }
                    if (which == 0) {
                        pickPartCategory(index);
                    } else if (which == 1) {
                        pickPartMoney(index);
                    } else {
                        editPartNote(index);
                    }
                })
                .setNeutralButton(R.string.action_remove, (dialog, which) -> {
                    if (index < mParts.size()) {
                        mParts.remove(index);
                        mPartCategoryTarget = -1;
                        mPartMoneyTarget = -1;
                        showParts();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Opens the categories of the main category's type alone, for the part at this index or, at
     * the size of the list, for a new one, which is added once a category comes back.
     */
    private void pickPartCategory(int index) {
        Category main = mCategoryPicker.getCurrentCategory();
        if (main == null) {
            mCategoryEditText.validate();
            return;
        }
        mPartCategoryTarget = index;
        mPartCategoryPicker.setCategory(index < mParts.size() ? mParts.get(index).mCategory : null);
        mPartCategoryPicker.showPicker(partTypeOf(main));
    }

    private void pickPartMoney(int index) {
        mPartMoneyTarget = index;
        mPartMoneyPicker.setCurrencyAndMoney(mMoneyPicker.getCurrentCurrency(), mParts.get(index).mMoney);
        mPartMoneyPicker.showPicker();
    }

    private void editPartNote(final int index) {
        View inputView = LayoutInflater.from(mActivity).inflate(R.layout.dialog_input, null);
        final EditText inputEditText = inputView.findViewById(R.id.dialog_input_edit_text);
        inputEditText.setHint(R.string.hint_note);
        inputEditText.setText(mParts.get(index).mNote);
        AlertDialog dialog = ThemedDialog.buildMaterialDialog(mActivity)
                .setTitle(R.string.hint_note)
                .setView(inputView)
                .setPositiveButton(android.R.string.ok, (dialogInterface, which) -> {
                    if (index < mParts.size()) {
                        String note = inputEditText.getText().toString().trim();
                        mParts.get(index).mNote = TextUtils.isEmpty(note) ? null : note;
                        showParts();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        ThemedDialog.showWithInput(dialog, inputEditText, true);
    }

    /*package-local*/ void onPartCategoryChanged(Category category) {
        if (category == null || mPartCategoryTarget < 0 || mPartCategoryTarget > mParts.size()) {
            return;
        }
        if (mPartCategoryTarget == mParts.size()) {
            mParts.add(new Part(category, 0L, null));
            showParts();
            pickPartMoney(mPartCategoryTarget);
        } else {
            mParts.get(mPartCategoryTarget).mCategory = category;
            showParts();
        }
    }

    /*package-local*/ void onPartMoneyChanged(long money) {
        if (mPartMoneyTarget >= 0 && mPartMoneyTarget < mParts.size()) {
            mParts.get(mPartMoneyTarget).mMoney = money;
            showParts();
        }
    }

    private static Contract.CategoryType partTypeOf(Category main) {
        return main.getDirection() == Contract.Direction.INCOME ? Contract.CategoryType.INCOME
                : Contract.CategoryType.EXPENSE;
    }

    /**
     * Refuses parts the ledger would not count, each reason with its own message. A part in a
     * category of the other type is how a direction flip on a split row is stopped, since the
     * direction follows the main category and the parts keep theirs.
     */
    /*package-local*/ boolean validate() {
        if (!mRules.offersSplit() || mParts.isEmpty()) {
            return true;
        }
        int error = 0;
        Contract.CategoryType type = partTypeOf(mCategoryPicker.getCurrentCategory());
        long sum = 0L;
        for (Part part : mParts) {
            if (part.mMoney <= 0L) {
                error = R.string.error_split_part_not_positive;
                break;
            }
            if (part.mCategory.getType() != type) {
                error = R.string.error_split_part_wrong_type;
                break;
            }
            sum = part.mMoney > Long.MAX_VALUE - sum ? Long.MAX_VALUE : sum + part.mMoney;
        }
        if (error == 0 && sum > mMoneyPicker.getCurrentMoney()) {
            error = R.string.error_split_total_below_parts;
        }
        if (error == 0) {
            return true;
        }
        ThemedDialog.buildMaterialDialog(mActivity)
                .setTitle(R.string.title_error)
                .setMessage(error)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        return false;
    }

    /*package-local*/ String encodeParts() {
        List<SplitPartsCodec.Part> parts = new ArrayList<>();
        for (Part part : mParts) {
            parts.add(new SplitPartsCodec.Part(part.mCategory.getId(), part.mMoney, part.mNote));
        }
        return SplitPartsCodec.encode(parts);
    }

    private static class Part {

        private Category mCategory;
        private long mMoney;
        private String mNote;

        private Part(Category category, long money, String note) {
            mCategory = category;
            mMoney = money;
            mNote = note;
        }
    }
}
