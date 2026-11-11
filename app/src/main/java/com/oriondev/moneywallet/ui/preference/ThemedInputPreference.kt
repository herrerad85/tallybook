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

package com.oriondev.moneywallet.ui.preference;

import android.content.Context;
import androidx.annotation.NonNull;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import android.text.TextUtils;
import android.util.AttributeSet;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;

/**
 * Created by andrea on 13/11/18.
 */

public class ThemedInputPreference extends Preference {

    private String mContent;

    private String mHint;
    private boolean mAllowEmptyInput;
    private int mInputType;

    private String mValue;

    public ThemedInputPreference(Context context) {
        super(context);
        initialize();
    }

    public ThemedInputPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public ThemedInputPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        setLayoutResource(R.layout.layout_preference_material_design);
        setOnPreferenceClickListener(new OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                showDialog();
                return false;
            }

        });
    }

    public void setContent(String content) {
        mContent = content;
    }

    public void setContent(int content) {
        mContent = getContext().getString(content);
    }

    public void setInput(String hint, boolean allowEmptyInput, int inputType) {
        mHint = hint;
        mAllowEmptyInput = allowEmptyInput;
        mInputType = inputType;
    }

    public void setCurrentValue(String value) {
        mValue = value;
    }

    public void setInput(int hint, boolean allowEmptyInput, int inputType) {
        mHint = getContext().getString(hint);
        mAllowEmptyInput = allowEmptyInput;
        mInputType = inputType;
    }

    private void showDialog() {
        View inputView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_input, null);
        final EditText inputEditText = inputView.findViewById(R.id.dialog_input_edit_text);
        inputEditText.setHint(mHint);
        inputEditText.setText(mValue);
        inputEditText.setInputType(mInputType);
        AlertDialog dialog = ThemedDialog.buildMaterialDialog(getContext())
                .setTitle(getTitle())
                .setMessage(mContent)
                .setView(inputView)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        CharSequence input = inputEditText.getText();
                        if (!TextUtils.equals(input, mValue)) {
                            mValue = input.toString();
                            callChangeListener(mValue);
                        }
                    }

                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        ThemedDialog.showWithInput(dialog, inputEditText, mAllowEmptyInput);
    }

    // see ThemedPreference.onBindViewHolder
    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        ThemeEngine.applyTheme(holder.itemView, true);
    }
}
