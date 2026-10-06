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
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.os.BundleCompat;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.background.SearchCategoryLoader;
import com.oriondev.moneywallet.background.SearchCursorLoader;
import com.oriondev.moneywallet.background.SearchRailLoader;
import com.oriondev.moneywallet.model.Category;
import com.oriondev.moneywallet.model.Icon;
import com.oriondev.moneywallet.model.Money;
import com.oriondev.moneywallet.model.SearchFilter;
import com.oriondev.moneywallet.model.Wallet;
import com.oriondev.moneywallet.picker.CategoryPicker;
import com.oriondev.moneywallet.picker.WalletPicker;
import com.oriondev.moneywallet.ui.activity.SearchActivity;
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter;
import com.oriondev.moneywallet.ui.adapter.recycler.TransactionCursorAdapter;
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelCursorListItemFragment;
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment;
import com.oriondev.moneywallet.ui.fragment.base.TransactionSelectionMode;
import com.oriondev.moneywallet.ui.fragment.secondary.TransactionItemFragment;
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView;
import com.oriondev.moneywallet.ui.view.theme.ITheme;
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;
import com.oriondev.moneywallet.utils.IconLoader;
import com.oriondev.moneywallet.utils.MoneyFormatter;
import com.oriondev.moneywallet.utils.SystemBars;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The search screen. A result opens in the same transaction panel the transaction list and the
 * calendar open, so a result can be duplicated and deleted and not only edited.
 */
public class SearchMultiPanelFragment extends MultiPanelCursorListItemFragment implements TransactionCursorAdapter.ActionListener, WalletPicker.SingleWalletController, CategoryPicker.Controller {

    private static final String SS_FILTER = "SearchMultiPanelFragment::SavedState::Filter";

    private static final String SECONDARY_PANEL_TAG = "SearchMultiPanelFragment::Tag::SecondaryPanel";

    private static final String SUMMARY_SEPARATOR = "  \u00B7  ";

    private static final float CHIP_ICON_SIZE_DP = 18f;

    // in the order of SearchFilter.Sort
    private static final int[] SORT_NAMES = new int[] {R.string.search_sort_newest,
            R.string.search_sort_oldest, R.string.search_sort_category,
            R.string.search_sort_largest, R.string.search_sort_smallest};

    // 24 and 60001 are taken by the list and the current wallet
    private static final int RAIL_LOADER_ID = 60002;

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

        /**
         * @return the icon of the set value, or null for the type's own symbol.
         */
        @Nullable
        Drawable getValueIcon(Fragment fragment, SearchFilter filter) {
            return null;
        }

        boolean isRuledOut(SearchRailLoader.Result result) {
            return false;
        }
    }

    private final List<Slot> mSlots = Arrays.asList(
            new Slot(R.string.search_type_category, R.drawable.ic_table_large_24dp, CategorySearchEditorFragment.class) {

                @Override
                CharSequence getValue(Fragment fragment, SearchFilter filter) {
                    if (filter.getCategoryIds().isEmpty()) {
                        return null;
                    }
                    List<String> names = new ArrayList<>();
                    for (SearchCategoryLoader.Entry category : getTickedCategories(filter.getCategoryIds())) {
                        names.add(category.getName());
                    }
                    return names.isEmpty() ? fragment.getString(R.string.search_type_category) : TextUtils.join(", ", names);
                }

                @Override
                Drawable getValueIcon(Fragment fragment, SearchFilter filter) {
                    List<SearchCategoryLoader.Entry> ticked = getTickedCategories(filter.getCategoryIds());
                    Icon icon = ticked.isEmpty() ? null : IconLoader.parse(ticked.get(0).getIcon());
                    return icon != null ? icon.getDrawable(fragment.requireContext()) : null;
                }

            },
            new Slot(R.string.search_type_text, R.drawable.ic_search_black_24dp, TextSearchEditorFragment.class) {

                @Override
                CharSequence getValue(Fragment fragment, SearchFilter filter) {
                    String text = filter.getText();
                    return text != null ? fragment.getString(R.string.search_value_text, text) : null;
                }

            },
            new Slot(R.string.search_type_people, R.drawable.ic_people_black_24dp, PeopleSearchEditorFragment.class) {

                @Override
                CharSequence getValue(Fragment fragment, SearchFilter filter) {
                    Set<Long> ids = filter.getPeopleIds();
                    return ids.isEmpty() ? null : joinNames(fragment, mRailResult != null ? mRailResult.getPeople() : null, ids, R.string.search_type_people);
                }

                @Override
                boolean isRuledOut(SearchRailLoader.Result result) {
                    return !result.hasPersonLinks();
                }

            },
            new Slot(R.string.search_type_status, R.drawable.ic_check_black_24dp, StatusSearchEditorFragment.class) {

                @Override
                CharSequence getValue(Fragment fragment, SearchFilter filter) {
                    SearchFilter.Status status = filter.getStatus();
                    if (status == null) {
                        return null;
                    }
                    return fragment.getString(status == SearchFilter.Status.UNCONFIRMED
                            ? R.string.search_value_unconfirmed : R.string.search_value_confirmed);
                }

                @Override
                boolean isRuledOut(SearchRailLoader.Result result) {
                    return !result.hasUnconfirmed();
                }

            },
            new Slot(R.string.search_type_wallet, R.drawable.ic_cash_multiple_24dp, WalletSearchEditorFragment.class) {

                @Override
                CharSequence getValue(Fragment fragment, SearchFilter filter) {
                    Set<Long> ids = filter.getWalletIds();
                    if (ids.isEmpty()) {
                        return filter.isTransfersOnly() ? fragment.getString(R.string.search_value_transfers) : null;
                    }
                    String names = joinNames(fragment, mRailResult != null ? mRailResult.getWallets() : null, ids, R.string.search_type_wallet);
                    return filter.isTransfersOnly() ? fragment.getString(R.string.search_value_wallet_transfers, names) : names;
                }

                @Override
                boolean isRuledOut(SearchRailLoader.Result result) {
                    return !result.hasWalletChoice();
                }

            },
            new Slot(R.string.search_type_amount, R.drawable.ic_coin_24dp, AmountSearchEditorFragment.class) {

                @Override
                CharSequence getValue(Fragment fragment, SearchFilter filter) {
                    if (!filter.isAmountSet()) {
                        return null;
                    }
                    BigDecimal amount = filter.getAmount();
                    String value;
                    switch (filter.getAmountOp()) {
                        case EXACTLY:
                            value = fragment.getString(R.string.search_value_amount_exactly, formatAmount(amount));
                            break;
                        case AT_LEAST:
                            value = fragment.getString(R.string.search_value_amount_at_least, formatAmount(amount));
                            break;
                        case AT_MOST:
                            value = fragment.getString(R.string.search_value_amount_at_most, formatAmount(amount));
                            break;
                        default:
                            BigDecimal to = filter.getAmountTo();
                            value = fragment.getString(R.string.search_value_amount_between,
                                    formatAmount(amount.min(to)), formatAmount(amount.max(to)));
                            break;
                    }
                    if (filter.getAmountSide() == SearchFilter.AmountSide.OUT) {
                        return fragment.getString(R.string.search_value_amount_out, value);
                    }
                    if (filter.getAmountSide() == SearchFilter.AmountSide.IN) {
                        return fragment.getString(R.string.search_value_amount_in, value);
                    }
                    return value;
                }

            },
            new Slot(R.string.search_type_date, R.drawable.ic_date_range_black_24dp, DateSearchEditorFragment.class) {

                @Override
                CharSequence getValue(Fragment fragment, SearchFilter filter) {
                    return DateSearchEditorFragment.getValue(fragment.requireContext(), filter.getDateFrom(), filter.getDateTo());
                }

            }
    );

    private final LoaderManager.LoaderCallbacks<SearchRailLoader.Result> mRailCallbacks = new LoaderManager.LoaderCallbacks<SearchRailLoader.Result>() {

        @NonNull
        @Override
        public Loader<SearchRailLoader.Result> onCreateLoader(int id, @Nullable Bundle args) {
            return new SearchRailLoader(requireContext());
        }

        @Override
        public void onLoadFinished(@NonNull Loader<SearchRailLoader.Result> loader, SearchRailLoader.Result result) {
            mRailResult = result;
            bindRail();
            // with an editor open the rule waits for the close, so no chip goes away under a
            // finger, and no finger can be on a rail not laid out yet
            if (getEditor() == null || !mRail.isLaidOut()) {
                applyRailVisibility();
            }
            // names landing after the layout change the width of the chips ahead of the open one
            if (!mRailDelivered) {
                mRailDelivered = true;
                if (mRail.isLaidOut()) {
                    showOpenChip();
                }
            }
        }

        @Override
        public void onLoaderReset(@NonNull Loader<SearchRailLoader.Result> loader) {
        }

    };

    private SearchFilter mFilter;

    /**
     * What the database holds for the rail, or null until its first load lands.
     */
    private SearchRailLoader.Result mRailResult;

    /**
     * Whether the rail's load has landed since this view was built.
     */
    private boolean mRailDelivered;

    private HorizontalScrollView mRailScrollView;
    private ChipGroup mRail;
    private Chip mMatchChip;
    private TextView mSummaryTextView;
    private TextView mSortTextView;
    private RecyclerView mRecyclerView;
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
        mSortTextView = primaryPanel.findViewById(R.id.search_sort_text_view);
        styleSortLabel();
        bindSort();
        mSortTextView.setOnClickListener(v -> showSorts());
        ViewGroup body = primaryPanel.findViewById(R.id.search_body_frame_layout);
        super.onCreatePrimaryPanel(inflater, body, savedInstanceState);
        mEditorPanel = inflater.inflate(R.layout.layout_search_editor_panel, body, false);
        body.addView(mEditorPanel);
        SystemBars.pad(mEditorPanel, false, sides, true);
        // a touch no child takes would otherwise reach the list row under the panel
        mEditorPanel.setOnTouchListener((v, event) -> true);
        mRailDelivered = false;
        buildRail();
        LoaderManager.getInstance(this).initLoader(RAIL_LOADER_ID, null, mRailCallbacks);
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
            slot.mChip.setOnClickListener(v -> onSlotClick(slot));
        }
        mRail.setOnClickListener(v -> closeEditor(true));
        mRail.setFocusable(false);
        bindRail();
        reorderRail();
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
            Drawable icon = slot.getValueIcon(this, mFilter);
            if (icon != null) {
                slot.mChip.setChipIcon(icon);
                // keeps the category's own colors
                slot.mChip.setChipIconTint(null);
            } else {
                slot.mChip.setChipIconResource(slot.mIcon);
            }
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

    /**
     * The arrow and the ripple take the theme engine's colors, as the text does.
     */
    private void styleSortLabel() {
        ITheme theme = ThemeEngine.getTheme();
        TextViewCompat.setCompoundDrawableTintList(mSortTextView, ColorStateList.valueOf(theme.getTextColorSecondary()));
        mSortTextView.setBackground(new RippleDrawable(ColorStateList.valueOf(theme.getColorRipple()), null, new ColorDrawable(Color.WHITE)));
    }

    private void bindSort() {
        String name = getString(SORT_NAMES[mFilter.getSort().ordinal()]);
        mSortTextView.setText(name);
        mSortTextView.setContentDescription(getString(R.string.search_sort_description, name));
    }

    private void showSorts() {
        String[] names = new String[SORT_NAMES.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = getString(SORT_NAMES[i]);
        }
        ThemedDialog.buildMaterialDialog(requireContext())
                .setSingleChoiceItems(names, mFilter.getSort().ordinal(), (dialog, which) -> {
                    dialog.dismiss();
                    mFilter.setSort(SearchFilter.Sort.values()[which]);
                    bindSort();
                    mRecyclerView.scrollToPosition(0);
                    restartLoader();
                })
                .show();
    }

    private void onSlotClick(Slot slot) {
        SearchEditorFragment editor = getEditor();
        if (editor != null && editor.getClass() == slot.mEditor) {
            closeEditor(true);
        } else {
            openEditor(slot);
        }
    }

    private void openEditor(Slot slot) {
        FragmentManager fragmentManager = getChildFragmentManager();
        SearchEditorFragment editor = (SearchEditorFragment) fragmentManager.getFragmentFactory()
                .instantiate(requireContext().getClassLoader(), slot.mEditor.getName());
        if (!editor.showsKeyboardOnOpen((SearchActivity) requireActivity())) {
            hideKeyboard();
        }
        fragmentManager.beginTransaction().replace(R.id.search_editor_container, editor).commitNow();
        applyEditorState();
        // a chip hidden under a finger still gets its click on the lift
        slot.mChip.setVisibility(View.VISIBLE);
        // a tapped chip part off the edge comes into view, as a focused one already does
        showChipOnNextLayout(mRailScrollView, slot.mChip);
        mRailScrollView.requestLayout();
        editor.onOpenedFromChip();
    }

    public void closeEditor() {
        closeEditor(false);
    }

    /**
     * @param fromRail true for a tap on a chip or on the rail, which leaves the chips where they
     *                 are, so none moves out from under the finger.
     */
    private void closeEditor(boolean fromRail) {
        SearchEditorFragment editor = getEditor();
        if (editor == null) {
            return;
        }
        hideKeyboard();
        getChildFragmentManager().beginTransaction().remove(editor).commitNow();
        applyEditorState();
        if (!fromRail) {
            applyRailVisibility();
            if (reorderRail()) {
                scrollRailToStart();
            }
        }
    }

    /**
     * Every type shows until the rail's load lands. After that a type is hidden when the database
     * rules it out, unless it is set or its editor is open.
     */
    private void applyRailVisibility() {
        if (mRailResult == null) {
            return;
        }
        SearchEditorFragment editor = getEditor();
        for (Slot slot : mSlots) {
            boolean hidden = slot.isRuledOut(mRailResult) && slot.getValue(this, mFilter) == null
                    && (editor == null || editor.getClass() != slot.mEditor);
            slot.mChip.setVisibility(hidden ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Moves the set chips, in type order, right after the All and Any toggle, each by a remove and
     * an add, which cancels a touch resting on it. Returns true when a chip moved.
     */
    private boolean reorderRail() {
        List<Chip> order = new ArrayList<>();
        for (Slot slot : mSlots) {
            if (slot.getValue(this, mFilter) != null) {
                order.add(slot.mChip);
            }
        }
        for (Slot slot : mSlots) {
            if (slot.getValue(this, mFilter) == null) {
                order.add(slot.mChip);
            }
        }
        boolean moved = false;
        for (int i = 0; i < order.size(); i++) {
            Chip chip = order.get(i);
            if (mRail.getChildAt(i + 1) == chip) {
                continue;
            }
            boolean focused = chip.isFocused();
            boolean accessibilityFocused = chip.createAccessibilityNodeInfo().isAccessibilityFocused();
            mRail.removeView(chip);
            mRail.addView(chip, i + 1);
            // the remove cleared both
            if (focused) {
                chip.requestFocus();
            }
            if (accessibilityFocused) {
                chip.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
            }
            moved = true;
        }
        return moved;
    }

    /**
     * After the next layout, and over a fling still running. Under a right to left layout the start
     * is the right edge, which the scroll clamps the rail's width to.
     */
    private void scrollRailToStart() {
        HorizontalScrollView scrollView = mRailScrollView;
        View rail = mRail;
        OneShotPreDrawListener.add(scrollView, () -> scrollView.smoothScrollTo(
                scrollView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? rail.getWidth() : 0, 0));
    }

    /**
     * Brings the chip into view on the scroll view's next layout with a width. A posted scroll
     * would move nothing while a result keeps the rail in a hidden panel, never laid out.
     */
    private static void showChipOnNextLayout(HorizontalScrollView scrollView, Chip chip) {
        scrollView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {

            @Override
            public void onLayoutChange(View view, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (right - left <= 0) {
                    return;
                }
                view.removeOnLayoutChangeListener(this);
                Rect bounds = new Rect(chip.getLeft(), chip.getTop(), chip.getRight(), chip.getBottom());
                scrollView.requestChildRectangleOnScreen((View) chip.getParent(), bounds, true);
            }

        });
    }

    /**
     * The ticked categories in the editor's order, a parent ticked with every child standing in
     * for them all. Empty until the names load.
     */
    private List<SearchCategoryLoader.Entry> getTickedCategories(Set<Long> ids) {
        List<SearchCategoryLoader.Entry> ticked = new ArrayList<>();
        if (mRailResult == null) {
            return ticked;
        }
        for (SearchCategoryLoader.Entry parent : mRailResult.getCategories()) {
            if (ids.containsAll(parent.getIds())) {
                ticked.add(parent);
                continue;
            }
            if (ids.contains(parent.getId())) {
                ticked.add(parent);
            }
            for (SearchCategoryLoader.Entry child : parent.getChildren()) {
                if (ids.contains(child.getId())) {
                    ticked.add(child);
                }
            }
        }
        return ticked;
    }

    private static String formatAmount(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMaximumFractionDigits(Math.max(0, amount.scale()));
        return format.format(amount);
    }

    /**
     * The names of the ids in list order, or the type's title until the names load.
     */
    private static String joinNames(Fragment fragment, @Nullable Map<Long, String> names, Set<Long> ids, @StringRes int title) {
        List<String> picked = new ArrayList<>();
        if (names != null) {
            for (Map.Entry<Long, String> entry : names.entrySet()) {
                if (ids.contains(entry.getKey())) {
                    picked.add(entry.getValue());
                }
            }
        }
        return picked.isEmpty() ? fragment.getString(title) : TextUtils.join(", ", picked);
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

    @Nullable
    /*package-local*/ SearchRailLoader.Result getRailResult() {
        return mRailResult;
    }

    /*package-local*/ void onFilterChanged() {
        bindRail();
        restartLoader();
    }

    @Override
    protected void onPrepareRecyclerView(AdvancedRecyclerView recyclerView) {
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setEmptyText(R.string.message_no_transaction_found);
        mRecyclerView = recyclerView.getRecyclerView();
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
        showOpenChip();
    }

    /**
     * Brings the open editor's chip, if any, into view.
     */
    private void showOpenChip() {
        SearchEditorFragment editor = getEditor();
        if (editor != null) {
            for (Slot slot : mSlots) {
                if (slot.mEditor == editor.getClass()) {
                    showChipOnNextLayout(mRailScrollView, slot.mChip);
                }
            }
        }
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
