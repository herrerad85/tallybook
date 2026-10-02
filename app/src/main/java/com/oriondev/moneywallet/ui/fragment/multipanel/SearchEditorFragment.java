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

import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.fragment.app.Fragment;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.SearchFilter;
import com.oriondev.moneywallet.ui.activity.SearchActivity;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;
import com.oriondev.moneywallet.utils.IconLoader;

/**
 * One type's editor in the search screen's editor panel. Every change applies at once, so the
 * results follow the value while it is adjusted.
 */
public abstract class SearchEditorFragment extends Fragment {

    private static final float DIMMED_ALPHA = 0.5f;

    /**
     * Read in onCreateView or later, since on a restore this fragment is created before the
     * search screen has read its filter back.
     */
    protected SearchFilter getFilter() {
        return getSearch().getFilter();
    }

    protected void onFilterChanged() {
        getSearch().onFilterChanged();
    }

    protected void close() {
        getSearch().closeEditor();
    }

    /**
     * @return true when {@link #onOpenedFromChip()} raises the keyboard, so the switch to this
     *         editor leaves it up. Asked before this editor is added.
     */
    protected boolean showsKeyboardOnOpen(SearchActivity activity) {
        return false;
    }

    /**
     * Called after a chip tap opened this editor, never on a restore.
     */
    protected void onOpenedFromChip() {
    }

    protected static void focus(EditText editText, boolean showKeyboard) {
        editText.requestFocus();
        if (showKeyboard) {
            editText.post(() -> {
                InputMethodManager manager = ContextCompat.getSystemService(editText.getContext(), InputMethodManager.class);
                if (manager != null) {
                    manager.showSoftInput(editText, 0);
                }
            });
        }
    }

    protected void bindClear(View view, Runnable unset) {
        Button clearButton = view.findViewById(R.id.search_clear_button);
        clearButton.setTextColor(ThemedDialog.getAccentColor());
        clearButton.setOnClickListener(v -> {
            unset.run();
            onFilterChanged();
            close();
        });
    }

    protected void addCheckRow(ViewGroup list, String icon, String name, @Nullable String count,
                               @Nullable String description, boolean dimmed, boolean checked,
                               CompoundButton.OnCheckedChangeListener listener) {
        View row = getLayoutInflater().inflate(R.layout.layout_search_editor_row, list, false);
        CheckBox checkBox = row.findViewById(R.id.search_row_check_box);
        ImageView iconView = row.findViewById(R.id.search_row_icon_image_view);
        TextView nameView = row.findViewById(R.id.search_row_name_text_view);
        IconLoader.parseAndLoad(icon, iconView);
        nameView.setText(name);
        ((TextView) row.findViewById(R.id.search_row_count_text_view)).setText(count);
        if (dimmed) {
            iconView.setAlpha(DIMMED_ALPHA);
            nameView.setAlpha(DIMMED_ALPHA);
        }
        checkBox.setChecked(checked);
        checkBox.setOnCheckedChangeListener(listener);
        row.setOnClickListener(v -> checkBox.toggle());
        row.setContentDescription(description);
        ViewCompat.setAccessibilityDelegate(row, new AccessibilityDelegateCompat() {

            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(CheckBox.class.getName());
                info.setCheckable(true);
                info.setChecked(checkBox.isChecked());
            }

        });
        list.addView(row);
    }

    private SearchMultiPanelFragment getSearch() {
        return (SearchMultiPanelFragment) requireParentFragment();
    }
}
