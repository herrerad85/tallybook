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

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.SearchFilter;
import com.oriondev.moneywallet.ui.activity.SearchActivity;
import com.oriondev.moneywallet.ui.view.theme.ITheme;
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;

import java.math.BigDecimal;

/**
 * A typed amount, read in each wallet's own currency, on money out, money in or either.
 */
public class AmountSearchEditorFragment extends SearchEditorFragment {

    private static final String SS_OP = "AmountSearchEditorFragment::SavedState::Op";
    private static final String SS_SIDE = "AmountSearchEditorFragment::SavedState::Side";

    // in the order of SearchFilter.AmountOp and SearchFilter.AmountSide
    private static final int[] OP_NAMES = new int[] {R.string.search_amount_exactly,
            R.string.search_amount_at_least, R.string.search_amount_at_most,
            R.string.search_amount_between};
    private static final int[] OP_SHORT_NAMES = new int[] {R.string.search_amount_exactly,
            R.string.search_amount_at_least, R.string.search_amount_at_most,
            R.string.search_amount_between_short};
    private static final int[] SIDE_CHIP_IDS = new int[] {R.id.search_amount_either_chip,
            R.id.search_amount_out_chip, R.id.search_amount_in_chip};

    private SearchFilter.AmountOp mOp;
    private Chip mOpChip;
    private ChipGroup mSideChipGroup;
    private EditText mAmountEditText;
    private View mAndTextView;
    private EditText mAmountToEditText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search_editor_amount, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mOpChip = view.findViewById(R.id.search_amount_op_chip);
        mSideChipGroup = view.findViewById(R.id.search_amount_side_chip_group);
        mAmountEditText = view.findViewById(R.id.search_amount_edit_text);
        mAndTextView = view.findViewById(R.id.search_amount_and_text_view);
        mAmountToEditText = view.findViewById(R.id.search_amount_to_edit_text);
        SearchFilter filter = getFilter();
        mOp = SearchFilter.AmountOp.EXACTLY;
        SearchFilter.AmountSide side = SearchFilter.AmountSide.EITHER;
        if (savedInstanceState != null) {
            mOp = SearchFilter.AmountOp.values()[savedInstanceState.getInt(SS_OP)];
            side = SearchFilter.AmountSide.values()[savedInstanceState.getInt(SS_SIDE)];
        } else if (filter.isAmountSet()) {
            mOp = filter.getAmountOp();
            side = filter.getAmountSide();
            mAmountEditText.setText(filter.getAmount().toPlainString());
            mAmountEditText.setSelection(mAmountEditText.length());
            if (filter.getAmountTo() != null) {
                mAmountToEditText.setText(filter.getAmountTo().toPlainString());
            }
        }
        mSideChipGroup.check(SIDE_CHIP_IDS[side.ordinal()]);
        styleOpChip();
        styleChoices(mSideChipGroup);
        ThemedDialog.tintInput(mAmountEditText);
        ThemedDialog.tintInput(mAmountToEditText);
        bindOp();
        // with no close icon listener a tap on the arrow is a tap on the chip
        mOpChip.setOnClickListener(v -> showOps());
        bindClear(view, () -> getFilter().setAmount(null, null, null, null));
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        // after the fields restored their own text, which would otherwise count as a change
        TextWatcher watcher = new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                apply();
            }

        };
        mAmountEditText.addTextChangedListener(watcher);
        mAmountToEditText.addTextChangedListener(watcher);
        mSideChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> apply());
    }

    private void showOps() {
        String[] names = new String[OP_NAMES.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = getString(OP_NAMES[i]);
        }
        ThemedDialog.buildMaterialDialog(requireContext())
                .setSingleChoiceItems(names, mOp.ordinal(), (dialog, which) -> {
                    dialog.dismiss();
                    mOp = SearchFilter.AmountOp.values()[which];
                    bindOp();
                    apply();
                })
                .show();
    }

    private void bindOp() {
        mOpChip.setText(OP_SHORT_NAMES[mOp.ordinal()]);
        mOpChip.setContentDescription(getString(R.string.search_amount_op_description, getString(OP_NAMES[mOp.ordinal()])));
        bindBetween();
    }

    private void bindBetween() {
        int visibility = mOp == SearchFilter.AmountOp.BETWEEN ? View.VISIBLE : View.GONE;
        mAndTextView.setVisibility(visibility);
        mAmountToEditText.setVisibility(visibility);
    }

    private void apply() {
        BigDecimal amountTo = mOp == SearchFilter.AmountOp.BETWEEN ? parseAmount(mAmountToEditText.getText()) : null;
        getFilter().setAmount(mOp, getSide(), parseAmount(mAmountEditText.getText()), amountTo);
        onFilterChanged();
    }

    private SearchFilter.AmountSide getSide() {
        return SearchFilter.AmountSide.values()[indexOf(SIDE_CHIP_IDS, mSideChipGroup.getCheckedChipId())];
    }

    private static int indexOf(int[] ids, int id) {
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == id) {
                return i;
            }
        }
        throw new IllegalStateException("No choice is checked");
    }

    /**
     * @return the number, with a dot, a comma or an Arabic decimal separator as the point, or null.
     */
    @Nullable
    private static BigDecimal parseAmount(CharSequence text) {
        StringBuilder ascii = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int digit = Character.digit(c, 10);
            if (c == '.' || c == ',' || c == '\u066B') {
                ascii.append('.');
            } else if (digit >= 0) {
                ascii.append((char) ('0' + digit));
            } else {
                ascii.append(c);
            }
        }
        String number = ascii.toString();
        // BigDecimal alone would also take a sign, an exponent and the digits of other scripts
        return number.matches("[0-9]+[.]?[0-9]*|[.][0-9]+") ? new BigDecimal(number) : null;
    }

    private void styleOpChip() {
        ITheme theme = ThemeEngine.getTheme();
        int accent = ThemedDialog.getAccentColor();
        int foreground = theme.getBestTextColor(accent);
        mOpChip.setChipBackgroundColor(ColorStateList.valueOf(accent));
        mOpChip.setChipStrokeWidth(0f);
        mOpChip.setTextColor(foreground);
        mOpChip.setCloseIconTint(ColorStateList.valueOf(foreground));
        mOpChip.setRippleColor(ColorStateList.valueOf(theme.getColorRipple()));
    }

    @Override
    protected boolean showsKeyboardOnOpen(SearchActivity activity) {
        return activity.amountShowsKeyboardOnOpen();
    }

    // in landscape a full screen keyboard would cover the choices, so a tap on the field raises it
    @Override
    protected void onOpenedFromChip() {
        focus(mAmountEditText, showsKeyboardOnOpen((SearchActivity) requireActivity()));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SS_OP, mOp.ordinal());
        outState.putInt(SS_SIDE, getSide().ordinal());
    }
}
