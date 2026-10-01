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

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;

/**
 * Text matched in the description, note, event and place of a transaction.
 */
public class TextSearchEditorFragment extends SearchEditorFragment {

    private EditText mEditText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search_editor_text, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mEditText = view.findViewById(R.id.search_text_edit_text);
        ThemedDialog.tintInput(mEditText);
        if (savedInstanceState == null) {
            mEditText.setText(getFilter().getText());
            mEditText.setSelection(mEditText.length());
        }
        Button clearButton = view.findViewById(R.id.search_clear_button);
        clearButton.setTextColor(ThemedDialog.getAccentColor());
        clearButton.setOnClickListener(v -> {
            getFilter().setText(null);
            onFilterChanged();
            close();
        });
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        // after the field restored its own text, which would otherwise count as a change
        mEditText.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                String previous = getFilter().getText();
                getFilter().setText(s.toString());
                if (!TextUtils.equals(previous, getFilter().getText())) {
                    onFilterChanged();
                }
            }

        });
    }

    @Override
    protected boolean showsKeyboardOnOpen() {
        return true;
    }

    @Override
    protected void onOpenedFromChip() {
        final EditText editText = mEditText;
        editText.requestFocus();
        editText.post(() -> {
            InputMethodManager manager = ContextCompat.getSystemService(editText.getContext(), InputMethodManager.class);
            if (manager != null) {
                manager.showSoftInput(editText, 0);
            }
        });
    }
}
