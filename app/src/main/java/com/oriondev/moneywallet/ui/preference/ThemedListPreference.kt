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
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import android.util.AttributeSet;
import android.view.View;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;

/**
 * Created by andrea on 25/07/18.
 */
public class ThemedListPreference extends Preference {

    private String[] mEntries;
    private String[] mEntryValues;

    private int mCurrentValueIndex;

    public ThemedListPreference(Context context) {
        super(context);
        initialize();
    }

    public ThemedListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public ThemedListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
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

    public void setEntries(String[] entries) {
        mEntries = entries;
    }

    public void setEntries(int array) {
        mEntries = getContext().getResources().getStringArray(array);
    }

    public void setEntryValues(String[] entryValues) {
        mEntryValues = entryValues;
    }

    public void setValue(int valueIndex) {
        mCurrentValueIndex = valueIndex;
    }

    public String getValue() {
        return mCurrentValueIndex >= 0 ? mEntryValues[mCurrentValueIndex] : null;
    }

    public void setValue(String value) {
        mCurrentValueIndex = indexOf(value);
    }

    private int indexOf(String value) {
        for(int i = 0; i < mEntryValues.length; i++) {
            if (mEntryValues[i].equals(value)) {
                return i;
            }
        }
        return -1;
    }

    private void showDialog() {
        final int[] selected = new int[] {mCurrentValueIndex};
        ThemedDialog.buildMaterialDialog(getContext())
                .setTitle(getTitle())
                .setSingleChoiceItems(mEntries, mCurrentValueIndex, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selected[0] = which;
                    }

                })
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (selected[0] != mCurrentValueIndex) {
                            callChangeListener(mEntryValues[selected[0]]);
                            mCurrentValueIndex = selected[0];
                        }
                    }

                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // see ThemedPreference.onBindViewHolder
    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        ThemeEngine.applyTheme(holder.itemView, true);
    }
}
