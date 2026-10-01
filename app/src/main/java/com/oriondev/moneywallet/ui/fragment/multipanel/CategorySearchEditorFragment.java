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
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.tabs.TabLayout;
import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.background.SearchCategoryLoader;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.ui.view.theme.ITheme;
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;
import com.oriondev.moneywallet.utils.IconLoader;

import java.text.NumberFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The expense and income categories, one tab each, every parent able to show its children, with
 * the system categories under them in both tabs. A parent's checkbox ticks it with every child.
 */
public class CategorySearchEditorFragment extends SearchEditorFragment implements LoaderManager.LoaderCallbacks<List<SearchCategoryLoader.Entry>> {

    private static final String SS_TAB = "CategorySearchEditorFragment::SavedState::Tab";
    private static final String SS_EXPANDED = "CategorySearchEditorFragment::SavedState::Expanded";

    private static final int LOADER_ID = 1;

    private static final int TAB_INCOME = 1;

    private static final float CHILD_INDENT_DP = 32f;

    private int mTab;
    private final Set<Long> mExpanded = new HashSet<>();

    private TabLayout mTabLayout;
    private ViewGroup mList;

    /**
     * Null until the first load lands.
     */
    private List<SearchCategoryLoader.Entry> mCategories;

    private final Map<SearchCategoryLoader.Entry, View> mRows = new LinkedHashMap<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mTab = savedInstanceState.getInt(SS_TAB);
            long[] expanded = savedInstanceState.getLongArray(SS_EXPANDED);
            if (expanded != null) {
                for (long id : expanded) {
                    mExpanded.add(id);
                }
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search_editor_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mList = view.findViewById(R.id.search_list_linear_layout);
        mTabLayout = view.findViewById(R.id.search_category_tab_layout);
        mTabLayout.setVisibility(View.VISIBLE);
        mTabLayout.addTab(mTabLayout.newTab().setText(R.string.menu_category_tab_expense));
        mTabLayout.addTab(mTabLayout.newTab().setText(R.string.menu_category_tab_income), mTab == TAB_INCOME);
        ScrollView scroll = view.findViewById(R.id.search_list_scroll_view);
        mTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {

            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                mTab = tab.getPosition();
                bindList();
                scroll.scrollTo(0, 0);
                // scrollTo leaves a running fling going, and its next frame would carry the new list back down. A fling with no velocity replaces it.
                scroll.fling(0);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }

        });
        bindClear(view, () -> getFilter().setCategoryIds(Collections.emptySet()));
        LoaderManager.getInstance(this).initLoader(LOADER_ID, null, this);
    }

    @NonNull
    @Override
    public Loader<List<SearchCategoryLoader.Entry>> onCreateLoader(int id, @Nullable Bundle args) {
        return new SearchCategoryLoader(requireContext());
    }

    @Override
    public void onLoadFinished(@NonNull Loader<List<SearchCategoryLoader.Entry>> loader, List<SearchCategoryLoader.Entry> categories) {
        mCategories = categories;
        bindList();
    }

    @Override
    public void onLoaderReset(@NonNull Loader<List<SearchCategoryLoader.Entry>> loader) {
    }

    private void bindList() {
        mList.removeAllViews();
        mRows.clear();
        if (mCategories == null) {
            return;
        }
        Contract.CategoryType type = mTab == TAB_INCOME ? Contract.CategoryType.INCOME : Contract.CategoryType.EXPENSE;
        boolean heading = false;
        for (SearchCategoryLoader.Entry category : mCategories) {
            if (category.getType() == type) {
                addRow(category, false, mList.getChildCount());
                if (mExpanded.contains(category.getId())) {
                    for (SearchCategoryLoader.Entry child : category.getChildren()) {
                        addRow(child, true, mList.getChildCount());
                    }
                }
            } else if (category.getType() == Contract.CategoryType.SYSTEM) {
                if (!heading) {
                    addHeading();
                    heading = true;
                }
                addRow(category, false, mList.getChildCount());
            }
        }
        bindChecks();
    }

    private void addHeading() {
        TextView heading = new TextView(requireContext());
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        heading.setPaddingRelative(padding, padding, padding, padding / 2);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        heading.setTextColor(ThemeEngine.getTheme().getTextColorSecondary());
        heading.setText(R.string.menu_category_tab_system);
        ViewCompat.setAccessibilityHeading(heading, true);
        mList.addView(heading);
    }

    private void addRow(SearchCategoryLoader.Entry category, boolean child, int index) {
        View row = getLayoutInflater().inflate(R.layout.layout_search_editor_category_row, mList, false);
        MaterialCheckBox checkBox = row.findViewById(R.id.search_row_check_box);
        ITheme theme = ThemeEngine.getTheme();
        int accent = ThemedDialog.getAccentColor();
        checkBox.setButtonTintList(new ColorStateList(new int[][] {
                new int[] {com.google.android.material.R.attr.state_indeterminate},
                new int[] {android.R.attr.state_checked},
                new int[] {}
        }, new int[] {accent, accent, theme.getTextColorSecondary()}));
        checkBox.setButtonIconTintList(ColorStateList.valueOf(theme.getBestTextColor(accent)));
        IconLoader.parseAndLoad(category.getIcon(), row.findViewById(R.id.search_row_icon_image_view));
        ((TextView) row.findViewById(R.id.search_row_name_text_view)).setText(category.getName());
        int count = category.getCount();
        ((TextView) row.findViewById(R.id.search_row_count_text_view)).setText(NumberFormat.getIntegerInstance().format(count));
        row.setContentDescription(getResources().getQuantityString(R.plurals.search_category_description, count, category.getName(), count));
        if (child) {
            row.setPaddingRelative(row.getPaddingStart() + Math.round(CHILD_INDENT_DP * getResources().getDisplayMetrics().density),
                    row.getPaddingTop(), row.getPaddingEnd(), row.getPaddingBottom());
        } else if (!category.getChildren().isEmpty()) {
            ImageView expand = row.findViewById(R.id.search_row_expand_image_view);
            expand.setVisibility(View.VISIBLE);
            ImageViewCompat.setImageTintList(expand, ColorStateList.valueOf(theme.getIconColor()));
            bindExpand(expand, category);
            expand.setOnClickListener(v -> toggleExpanded(category, expand));
        }
        row.setOnClickListener(v -> onRowClick(category));
        ViewCompat.setAccessibilityDelegate(row, new AccessibilityDelegateCompat() {

            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                int state = checkBox.getCheckedState();
                info.setClassName(CheckBox.class.getName());
                info.setCheckable(true);
                info.setChecked(state == MaterialCheckBox.STATE_CHECKED);
                if (state == MaterialCheckBox.STATE_INDETERMINATE) {
                    info.setStateDescription(getString(R.string.search_partly_checked));
                }
            }

        });
        mList.addView(row, index);
        mRows.put(category, row);
    }

    private void bindExpand(ImageView expand, SearchCategoryLoader.Entry category) {
        boolean expanded = mExpanded.contains(category.getId());
        expand.setRotation(expanded ? 180f : 0f);
        expand.setContentDescription(getString(expanded ? R.string.search_hide_subcategories
                : R.string.search_show_subcategories, category.getName()));
    }

    /**
     * Adds or removes the children under the parent's row, so the rest of the list stays put.
     */
    private void toggleExpanded(SearchCategoryLoader.Entry category, ImageView expand) {
        boolean expanding = !mExpanded.remove(category.getId());
        if (expanding) {
            mExpanded.add(category.getId());
        }
        int index = mList.indexOfChild(mRows.get(category)) + 1;
        for (SearchCategoryLoader.Entry child : category.getChildren()) {
            if (expanding) {
                addRow(child, true, index++);
            } else {
                mList.removeView(mRows.remove(child));
            }
        }
        bindExpand(expand, category);
        bindChecks();
    }

    /**
     * A ticked parent unticks with every child, an unticked or partly ticked one ticks with every
     * child, and a child or a system category toggles alone.
     */
    private void onRowClick(SearchCategoryLoader.Entry category) {
        Set<Long> ids = new TreeSet<>(getFilter().getCategoryIds());
        if (getCheckedState(category, ids) == MaterialCheckBox.STATE_CHECKED) {
            ids.removeAll(category.getIds());
        } else {
            ids.addAll(category.getIds());
        }
        getFilter().setCategoryIds(ids);
        onFilterChanged();
        bindChecks();
    }

    private void bindChecks() {
        Set<Long> ids = getFilter().getCategoryIds();
        for (Map.Entry<SearchCategoryLoader.Entry, View> row : mRows.entrySet()) {
            MaterialCheckBox checkBox = row.getValue().findViewById(R.id.search_row_check_box);
            checkBox.setCheckedState(getCheckedState(row.getKey(), ids));
        }
    }

    private static int getCheckedState(SearchCategoryLoader.Entry category, Set<Long> ids) {
        List<Long> own = category.getIds();
        int ticked = 0;
        for (Long id : own) {
            if (ids.contains(id)) {
                ticked++;
            }
        }
        return ticked == 0 ? MaterialCheckBox.STATE_UNCHECKED
                : ticked == own.size() ? MaterialCheckBox.STATE_CHECKED : MaterialCheckBox.STATE_INDETERMINATE;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SS_TAB, mTab);
        long[] expanded = new long[mExpanded.size()];
        int i = 0;
        for (Long id : mExpanded) {
            expanded[i++] = id;
        }
        outState.putLongArray(SS_EXPANDED, expanded);
    }
}
