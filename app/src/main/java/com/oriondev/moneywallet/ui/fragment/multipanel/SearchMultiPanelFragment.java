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

package com.oriondev.moneywallet.ui.fragment.multipanel;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.background.SearchCursorLoader;
import com.oriondev.moneywallet.model.Category;
import com.oriondev.moneywallet.model.Money;
import com.oriondev.moneywallet.model.SearchFilter;
import com.oriondev.moneywallet.model.Wallet;
import com.oriondev.moneywallet.picker.CategoryPicker;
import com.oriondev.moneywallet.picker.WalletPicker;
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter;
import com.oriondev.moneywallet.ui.adapter.recycler.TransactionCursorAdapter;
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelCursorListItemFragment;
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment;
import com.oriondev.moneywallet.ui.fragment.base.TransactionSelectionMode;
import com.oriondev.moneywallet.ui.fragment.secondary.TransactionItemFragment;
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView;
import com.oriondev.moneywallet.ui.view.theme.ITheme;
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;
import com.oriondev.moneywallet.utils.MoneyFormatter;
import com.oriondev.moneywallet.utils.SystemBars;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * The search screen. A result opens in the same transaction panel the transaction list and the
 * calendar open, so a result can be duplicated and deleted and not only edited.
 */
public class SearchMultiPanelFragment extends MultiPanelCursorListItemFragment implements TransactionCursorAdapter.ActionListener, WalletPicker.SingleWalletController, CategoryPicker.Controller {

    private static final String SS_FILTER = "SearchMultiPanelFragment::SavedState::Filter";

    private static final String SECONDARY_PANEL_TAG = "SearchMultiPanelFragment::Tag::SecondaryPanel";

    private static final String SUMMARY_SEPARATOR = "  \u00B7  ";

    private static final float CHIP_ICON_SIZE_DP = 18f;

    /**
     * A chip on the rail after the All and Any toggle, which opens its type's editor.
     */
    private abstract static class Slot {

        private final @StringRes int mTitle;
        private final @DrawableRes int mIcon;
        private final Class<? extends SearchEditorFragment> mEditor;
        private Chip mChip;

        private Slot(@StringRes int title, @DrawableRes int icon, Class<? extends SearchEditorFragment> editor) {
            mTitle = title;
            mIcon = icon;
            mEditor = editor;
        }

        /**
         * @return the chip text when the type is set, or null when it is not.
         */
        @Nullable
        abstract CharSequence getValue(Fragment fragment, SearchFilter filter);
    }

    private final List<Slot> mSlots = Collections.singletonList(
            new Slot(R.string.search_type_text, R.drawable.ic_search_black_24dp, TextSearchEditorFragment.class) {

                @Override
                CharSequence getValue(Fragment fragment, SearchFilter filter) {
                    String text = filter.getText();
                    return text != null ? fragment.getString(R.string.search_value_text, text) : null;
                }

            }
    );

    private SearchFilter mFilter;

    private HorizontalScrollView mRailScrollView;
    private ChipGroup mRail;
    private Chip mMatchChip;
    private TextView mSummaryTextView;
    private View mEditorPanel;
    private TransactionSelectionMode mSelectionMode;

    /**
     * What the primary panel's descendant focusability was before a result blocked it, or null
     * while it is not blocked.
     */
    private Integer mSavedFocusability;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SearchFilter filter = savedInstanceState != null ? BundleCompat.getParcelable(savedInstanceState, SS_FILTER, SearchFilter.class) : null;
        mFilter = filter != null ? filter : new SearchFilter();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreatePrimaryPanel(LayoutInflater inflater, @NonNull ViewGroup primaryPanel, @Nullable Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_search_primary_panel, primaryPanel, true);
        boolean sides = getResources().getBoolean(R.bool.panel_fills_window);
        mRailScrollView = primaryPanel.findViewById(R.id.search_rail_scroll_view);
        mRailScrollView.setSaveEnabled(false);
        mRail = primaryPanel.findViewById(R.id.search_rail_chip_group);
        SystemBars.pad(mRail, false, sides, false);
        SystemBars.pad(primaryPanel.findViewById(R.id.search_strip), false, sides, false);
        mSummaryTextView = primaryPanel.findViewById(R.id.search_summary_text_view);
        ViewGroup body = primaryPanel.findViewById(R.id.search_body_frame_layout);
        super.onCreatePrimaryPanel(inflater, body, savedInstanceState);
        mEditorPanel = inflater.inflate(R.layout.layout_search_editor_panel, body, false);
        body.addView(mEditorPanel);
        SystemBars.pad(mEditorPanel, false, sides, true);
        // a touch no child takes would otherwise reach the list row under the panel
        mEditorPanel.setOnTouchListener((v, event) -> true);
        buildRail();
    }

    private void buildRail() {
        mMatchChip = addChip();
        styleChip(mMatchChip, false);
        mMatchChip.setOnClickListener(v -> {
            boolean all = mFilter.getMatch() == SearchFilter.Match.ALL;
            mFilter.setMatch(all ? SearchFilter.Match.ANY : SearchFilter.Match.ALL);
            onFilterChanged();
        });
        for (Slot slot : mSlots) {
            slot.mChip = addChip();
            slot.mChip.setChipIconResource(slot.mIcon);
            slot.mChip.setOnClickListener(v -> onSlotClick(slot));
        }
        mRail.setOnClickListener(v -> closeEditor());
        mRail.setFocusable(false);
        bindRail();
    }

    private Chip addChip() {
        Chip chip = new Chip(new ContextThemeWrapper(mRail.getContext(), R.style.ThemeOverlay_SearchRailChip));
        chip.setChipIconSize(CHIP_ICON_SIZE_DP * getResources().getDisplayMetrics().density);
        mRail.addView(chip);
        return chip;
    }

    private void bindRail() {
        boolean any = mFilter.getMatch() == SearchFilter.Match.ANY;
        mMatchChip.setText(any ? R.string.search_match_any : R.string.search_match_all);
        mMatchChip.setContentDescription(getString(any ? R.string.search_match_any_description : R.string.search_match_all_description));
        for (Slot slot : mSlots) {
            CharSequence value = slot.getValue(this, mFilter);
            String title = getString(slot.mTitle);
            slot.mChip.setText(value != null ? value : title);
            slot.mChip.setContentDescription(value != null ? title + ", " + value : null);
            styleChip(slot.mChip, value != null);
        }
    }

    /**
     * The XML theme is always the light one, so the chips take their colors from the theme engine.
     */
    private void styleChip(Chip chip, boolean set) {
        ITheme theme = ThemeEngine.getTheme();
        int foreground;
        if (set) {
            int accent = theme.getColorAccent();
            foreground = theme.getBestTextColor(accent);
            chip.setChipBackgroundColor(ColorStateList.valueOf(accent));
            chip.setChipStrokeWidth(0f);
        } else {
            foreground = theme.getTextColorSecondary();
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.TRANSPARENT));
            chip.setChipStrokeColor(ColorStateList.valueOf(theme.getBestHintColor(theme.getColorWindowForeground())));
            chip.setChipStrokeWidth(getResources().getDisplayMetrics().density);
        }
        chip.setTextColor(foreground);
        chip.setChipIconTint(ColorStateList.valueOf(foreground));
        chip.setRippleColor(ColorStateList.valueOf(theme.getColorRipple()));
    }

    private void onSlotClick(Slot slot) {
        SearchEditorFragment editor = getEditor();
        if (editor != null && editor.getClass() == slot.mEditor) {
            closeEditor();
        } else {
            openEditor(slot);
        }
    }

    private void openEditor(Slot slot) {
        FragmentManager fragmentManager = getChildFragmentManager();
        SearchEditorFragment editor = (SearchEditorFragment) fragmentManager.getFragmentFactory()
                .instantiate(requireContext().getClassLoader(), slot.mEditor.getName());
        if (!editor.showsKeyboardOnOpen()) {
            hideKeyboard();
        }
        fragmentManager.beginTransaction().replace(R.id.search_editor_container, editor).commitNow();
        applyEditorState();
        editor.onOpenedFromChip();
    }

    public void closeEditor() {
        SearchEditorFragment editor = getEditor();
        if (editor == null) {
            return;
        }
        hideKeyboard();
        getChildFragmentManager().beginTransaction().remove(editor).commitNow();
        applyEditorState();
    }

    @Nullable
    private SearchEditorFragment getEditor() {
        return (SearchEditorFragment) getChildFragmentManager().findFragmentById(R.id.search_editor_container);
    }

    /**
     * An editor left in a hidden panel, by a rotation or by TalkBack opening a result, does not
     * count as open until the panel shows again.
     */
    public boolean isEditorShown() {
        return getEditor() != null && mEditorPanel != null && mEditorPanel.isShown();
    }

    /**
     * @return true when the point, in screen coordinates, falls on the editor panel or the rail.
     */
    public boolean isOnEditorOrRail(float rawX, float rawY) {
        return contains(mEditorPanel, rawX, rawY) || contains(mRailScrollView, rawX, rawY);
    }

    private static boolean contains(View view, float rawX, float rawY) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return rawX >= location[0] && rawX < location[0] + view.getWidth()
                && rawY >= location[1] && rawY < location[1] + view.getHeight();
    }

    private void applyEditorState() {
        boolean open = getEditor() != null;
        mEditorPanel.setVisibility(open ? View.VISIBLE : View.GONE);
        mRail.setClickable(open);
        mRail.setContentDescription(open ? getString(R.string.search_close_editor) : null);
    }

    private void hideKeyboard() {
        Activity activity = requireActivity();
        InputMethodManager manager = ContextCompat.getSystemService(activity, InputMethodManager.class);
        if (manager != null) {
            manager.hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), 0);
        }
    }

    /*package-local*/ SearchFilter getFilter() {
        return mFilter;
    }

    /*package-local*/ void onFilterChanged() {
        bindRail();
        restartLoader();
    }

    @Override
    protected void onPrepareRecyclerView(AdvancedRecyclerView recyclerView) {
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setEmptyText(R.string.message_no_transaction_found);
    }

    @Override
    protected AbstractCursorAdapter onCreateAdapter() {
        TransactionCursorAdapter adapter = new TransactionCursorAdapter(this);
        mSelectionMode = new TransactionSelectionMode(this, this, adapter);
        return adapter;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mSelectionMode.onRestoreInstanceState(savedInstanceState);
        // after the rail's click listener, which makes it clickable again
        applyEditorState();
    }

    @Override
    public boolean navigateBack() {
        if (isEditorShown()) {
            closeEditor();
            return true;
        }
        return super.navigateBack();
    }

    /**
     * Below the wide layout a result hides the primary panel, where a field could still take the
     * focus a rotation restores, and the typing after it.
     */
    @Override
    protected void showSecondaryPanel() {
        if (!isExtendedLayout()) {
            hideKeyboard();
            ViewGroup primaryPanel = getPrimaryPanel();
            if (primaryPanel.getDescendantFocusability() != ViewGroup.FOCUS_BLOCK_DESCENDANTS) {
                mSavedFocusability = primaryPanel.getDescendantFocusability();
                primaryPanel.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            }
        }
        super.showSecondaryPanel();
    }

    @Override
    protected void hideSecondaryPanel() {
        super.hideSecondaryPanel();
        if (mSavedFocusability != null) {
            getPrimaryPanel().setDescendantFocusability(mSavedFocusability);
            mSavedFocusability = null;
        }
    }

    @Override
    protected SecondaryPanelFragment onCreateSecondaryPanel() {
        return new TransactionItemFragment();
    }

    @Override
    protected String getSecondaryFragmentTag() {
        return SECONDARY_PANEL_TAG;
    }

    @Override
    protected int getTitleRes() {
        return R.string.title_activity_search_transaction;
    }

    @Override
    protected boolean isFloatingActionButtonEnabled() {
        return false;
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new SearchCursorLoader(requireActivity(), mFilter);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        super.onLoadFinished(loader, cursor);
        bindSummary(cursor instanceof SearchCursorLoader.Summary ? (SearchCursorLoader.Summary) cursor : null);
    }

    private void bindSummary(@Nullable SearchCursorLoader.Summary summary) {
        if (summary == null) {
            mSummaryTextView.setText(null);
            return;
        }
        int count = summary.getMatchCount();
        SpannableStringBuilder text = new SpannableStringBuilder(getResources().getQuantityString(R.plurals.search_result_count, count, count));
        MoneyFormatter formatter = MoneyFormatter.getInstance();
        Long out = summary.getOut();
        if (out != null) {
            text.append(SUMMARY_SEPARATOR).append(formatter.getTintedString(new Money(summary.getCurrency(), out), MoneyFormatter.TintMode.EXPENSE));
        }
        Long in = summary.getIn();
        if (in != null) {
            text.append(SUMMARY_SEPARATOR).append(formatter.getTintedString(new Money(summary.getCurrency(), in), MoneyFormatter.TintMode.INCOME));
        }
        mSummaryTextView.setText(text);
    }

    @Override
    public void onHeaderClick(Date startDate, Date endDate) {
        // this method will never be called by the adapter!
    }

    @Override
    public void onTransactionClick(long id) {
        showItemId(id);
        showSecondaryPanel();
    }

    @Override
    public void onSelectionChanged(int count) {
        mSelectionMode.onSelectionChanged(count);
    }

    @Override
    public void onWalletChanged(String tag, Wallet wallet) {
        mSelectionMode.onWalletPicked(wallet);
    }

    @Override
    public void onCategoryChanged(String tag, Category category) {
        mSelectionMode.onCategoryPicked(category);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(SS_FILTER, mFilter);
        if (mSelectionMode != null) {
            mSelectionMode.onSaveInstanceState(outState);
        }
    }
}
