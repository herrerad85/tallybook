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

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.icu.text.DateFormatSymbols;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Group;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.storage.wrapper.DateRangeHeader;
import com.oriondev.moneywallet.ui.view.theme.ITheme;
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;
import com.oriondev.moneywallet.utils.DateUtils;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Days on or after one day, on or before it, or between two, picked on a month grid or from a
 * preset. Days are stored as yyyy-MM-dd with ASCII digits, whatever the locale.
 */
public class DateSearchEditorFragment extends SearchEditorFragment {

    private static final String SS_CHOICE = "DateSearchEditorFragment::SavedState::Choice";
    private static final String SS_FIRST = "DateSearchEditorFragment::SavedState::First";
    private static final String SS_SECOND = "DateSearchEditorFragment::SavedState::Second";
    private static final String SS_FOCUS_TO = "DateSearchEditorFragment::SavedState::FocusTo";
    private static final String SS_MONTH = "DateSearchEditorFragment::SavedState::Month";
    private static final String SS_YEARS = "DateSearchEditorFragment::SavedState::Years";

    private static final int AFTER = 0;
    private static final int BEFORE = 1;
    private static final int BETWEEN = 2;

    // in the order of the choices above
    private static final int[] CHOICE_CHIP_IDS = new int[] {R.id.search_date_after_chip,
            R.id.search_date_before_chip, R.id.search_date_between_chip};

    private static final int THIS_MONTH = 0;
    private static final int LAST_MONTH = 1;
    private static final int LAST_30_DAYS = 2;
    private static final int THIS_YEAR = 3;

    // in the order of the presets above
    private static final int[] PRESET_CHIP_IDS = new int[] {R.id.search_date_this_month_chip,
            R.id.search_date_last_month_chip, R.id.search_date_last_30_days_chip,
            R.id.search_date_this_year_chip};
    private static final int[] PRESET_NAMES = new int[] {R.string.search_date_this_month,
            R.string.search_date_last_month, R.string.search_date_last_30_days,
            R.string.search_date_this_year};

    private static final int WEEKS = 6;
    private static final int DAYS_IN_WEEK = 7;
    private static final int FIRST_YEAR = 1970;
    private static final int YEARS_AFTER_THIS = 10;
    private static final int YEAR_COLUMNS = 4;
    private static final float CELL_HEIGHT_DP = 44f;
    private static final int RANGE_ALPHA = 0x3D;

    /**
     * Today, in milliseconds, for tests, or null for the clock.
     */
    @VisibleForTesting
    static Long sToday;

    private int mChoice;

    /**
     * The one day of On or after and On or before, or the From day of Between, as yyyy-MM-dd.
     */
    private String mFirst;

    /**
     * The To day of Between.
     */
    private String mSecond;

    private boolean mFocusTo;

    /**
     * The first day of the month on screen.
     */
    private Calendar mMonth;

    private boolean mYearsShown;

    private ChipGroup mChoiceChipGroup;
    private ChipGroup mPresetChipGroup;
    private View mFromBox;
    private TextView mFromLabelTextView;
    private TextView mFromTextView;
    private View mToBox;
    private TextView mToTextView;
    private TextView mMonthTextView;
    private LinearLayout mGrid;
    private LinearLayout mYearList;
    private ScrollView mScrollView;
    private View mDoneButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search_editor_date, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mChoiceChipGroup = view.findViewById(R.id.search_date_choice_chip_group);
        mPresetChipGroup = view.findViewById(R.id.search_date_preset_chip_group);
        mFromBox = view.findViewById(R.id.search_date_from_box);
        mFromLabelTextView = view.findViewById(R.id.search_date_from_label_text_view);
        mFromTextView = view.findViewById(R.id.search_date_from_text_view);
        mToBox = view.findViewById(R.id.search_date_to_box);
        mToTextView = view.findViewById(R.id.search_date_to_text_view);
        mMonthTextView = view.findViewById(R.id.search_date_month_text_view);
        mGrid = view.findViewById(R.id.search_date_grid);
        mYearList = view.findViewById(R.id.search_date_year_list);
        mScrollView = view.findViewById(R.id.search_date_scroll_view);
        mDoneButton = view.findViewById(R.id.search_date_done_button);
        if (savedInstanceState != null) {
            mChoice = savedInstanceState.getInt(SS_CHOICE);
            mFirst = savedInstanceState.getString(SS_FIRST);
            mSecond = savedInstanceState.getString(SS_SECOND);
            mFocusTo = savedInstanceState.getBoolean(SS_FOCUS_TO);
            int month = savedInstanceState.getInt(SS_MONTH);
            mMonth = getMonth(month / 12, month % 12);
            mYearsShown = savedInstanceState.getBoolean(SS_YEARS);
        } else {
            String from = getFilter().getDateFrom();
            String to = getFilter().getDateTo();
            if (from != null && to != null) {
                mChoice = BETWEEN;
                mFirst = from;
                mSecond = to;
            } else if (to != null) {
                mChoice = BEFORE;
                mFirst = to;
            } else {
                mChoice = AFTER;
                mFirst = from;
            }
            mMonth = getMonth(mFirst != null ? DateUtils.getDateFromSQLDateString(mFirst) : today().getTime());
        }
        mChoiceChipGroup.check(CHOICE_CHIP_IDS[mChoice]);
        styleChoices(mChoiceChipGroup);
        styleChoices(mPresetChipGroup);
        mChoiceChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> onChoiceChanged(indexOf(CHOICE_CHIP_IDS, group.getCheckedChipId())));
        for (int i = 0; i < PRESET_CHIP_IDS.length; i++) {
            int preset = i;
            view.findViewById(PRESET_CHIP_IDS[i]).setOnClickListener(v -> applyPreset(preset));
        }
        mFromBox.setOnClickListener(v -> onBoxClick(false));
        mToBox.setOnClickListener(v -> onBoxClick(true));
        ITheme theme = ThemeEngine.getTheme();
        ImageView previous = view.findViewById(R.id.search_date_previous_button);
        ImageView next = view.findViewById(R.id.search_date_next_button);
        ImageViewCompat.setImageTintList(previous, ColorStateList.valueOf(theme.getIconColor()));
        ImageViewCompat.setImageTintList(next, ColorStateList.valueOf(theme.getIconColor()));
        previous.setOnClickListener(v -> moveMonth(-1));
        next.setOnClickListener(v -> moveMonth(1));
        Drawable expand = DrawableCompat.wrap(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_expand_more_black_24dp)).mutate();
        DrawableCompat.setTint(expand, theme.getIconColor());
        mMonthTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, expand, null);
        mMonthTextView.setTextColor(theme.getTextColorPrimary());
        mMonthTextView.setOnClickListener(v -> {
            mYearsShown = !mYearsShown;
            bindMonth();
        });
        mDoneButton.setOnClickListener(v -> close());
        ((TextView) mDoneButton).setTextColor(ThemedDialog.getAccentColor());
        bindClear(view, () -> getFilter().setDates(null, null));
        buildGrid();
        bind();
    }

    private void onChoiceChanged(int choice) {
        if (choice == mChoice) {
            return;
        }
        mChoice = choice;
        mSecond = null;
        mFocusTo = choice == BETWEEN && mFirst != null;
        mYearsShown = false;
        apply();
    }

    private void applyPreset(int preset) {
        String[] range = getPresetRange(preset, today());
        mChoice = BETWEEN;
        mChoiceChipGroup.check(R.id.search_date_between_chip);
        mFirst = range[0];
        mSecond = range[1];
        mFocusTo = false;
        mMonth = getMonth(DateUtils.getDateFromSQLDateString(mFirst));
        mYearsShown = false;
        apply();
    }

    private void onBoxClick(boolean to) {
        if (mChoice == BETWEEN) {
            mFocusTo = to;
        }
        String day = to ? mSecond : mFirst;
        if (day != null) {
            mMonth = getMonth(DateUtils.getDateFromSQLDateString(day));
        }
        mYearsShown = false;
        bind();
    }

    private void onDayClick(String day) {
        if (mChoice == BETWEEN && mFocusTo) {
            mSecond = day;
        } else {
            mFirst = day;
        }
        if (mChoice == BETWEEN) {
            mFocusTo = !mFocusTo;
        }
        apply();
    }

    private void moveMonth(int months) {
        mMonth.add(Calendar.MONTH, months);
        mYearsShown = false;
        bindMonth();
        mMonthTextView.announceForAccessibility(mMonthTextView.getText());
    }

    private void apply() {
        if (mChoice == AFTER) {
            getFilter().setDates(mFirst, null);
        } else if (mChoice == BEFORE) {
            getFilter().setDates(null, mFirst);
        } else if (mFirst != null && mSecond != null) {
            getFilter().setDates(mFirst, mSecond);
        } else {
            getFilter().setDates(null, null);
        }
        onFilterChanged();
        bind();
    }

    private void bind() {
        boolean between = mChoice == BETWEEN;
        mFromLabelTextView.setText(between ? R.string.search_date_from : R.string.search_type_date);
        mToBox.setVisibility(between ? View.VISIBLE : View.GONE);
        mDoneButton.setVisibility(between ? View.VISIBLE : View.GONE);
        bindBox(mFromBox, mFromTextView, mFirst, between && !mFocusTo);
        bindBox(mToBox, mToTextView, mSecond, between && mFocusTo);
        String from = getFilter().getDateFrom();
        String to = getFilter().getDateTo();
        String[] range = from != null && to != null ? getOrdered(from, to) : null;
        Calendar today = today();
        for (int i = 0; i < PRESET_CHIP_IDS.length; i++) {
            ((Chip) mPresetChipGroup.findViewById(PRESET_CHIP_IDS[i])).setChecked(range != null && Arrays.equals(range, getPresetRange(i, today)));
        }
        bindMonth();
    }

    private void bindBox(View box, TextView textView, @Nullable String day, boolean focused) {
        ITheme theme = ThemeEngine.getTheme();
        textView.setText(day != null ? DateFormat.getDateInstance(DateFormat.MEDIUM).format(DateUtils.getDateFromSQLDateString(day)) : getString(R.string.hint_search_date));
        textView.setTextColor(day != null ? theme.getTextColorPrimary() : theme.getHintTextColor());
        GradientDrawable outline = new GradientDrawable();
        outline.setCornerRadius(dp(4f));
        outline.setStroke(Math.round(dp(focused ? 2f : 1f)), focused ? ThemedDialog.getAccentColor()
                : theme.getBestHintColor(theme.getColorWindowForeground()));
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(dp(4f));
        mask.setColor(Color.WHITE);
        box.setBackground(withFocus(outline, mask));
        box.setSelected(focused);
    }

    private void bindMonth() {
        // in the time zone now in force, which can change while the editor is open
        mMonth = getMonth(mMonth.get(Calendar.YEAR), mMonth.get(Calendar.MONTH));
        Locale locale = Locale.getDefault();
        String title = new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMMy"), locale).format(mMonth.getTime());
        mMonthTextView.setText(title);
        mMonthTextView.setContentDescription(getString(R.string.search_date_pick_year, title));
        mGrid.setVisibility(mYearsShown ? View.GONE : View.VISIBLE);
        mYearList.setVisibility(mYearsShown ? View.VISIBLE : View.GONE);
        if (mYearsShown) {
            bindYears();
        } else {
            bindGrid();
        }
    }

    /**
     * A row of weekday initials over six weeks of seven days.
     */
    private void buildGrid() {
        Context context = requireContext();
        for (int row = 0; row <= WEEKS; row++) {
            LinearLayout line = new LinearLayout(context);
            line.setBaselineAligned(false);
            for (int column = 0; column < DAYS_IN_WEEK; column++) {
                TextView cell = new TextView(context);
                cell.setGravity(Gravity.CENTER);
                if (row == 0) {
                    cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    cell.setTextColor(ThemeEngine.getTheme().getTextColorSecondary());
                    cell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                }
                // a minimum, not a fixed height, so a tall script at a large font is not cut
                cell.setMinHeight(Math.round(dp(row == 0 ? 32f : CELL_HEIGHT_DP)));
                line.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            }
            mGrid.addView(line);
        }
    }

    private void bindGrid() {
        ITheme theme = ThemeEngine.getTheme();
        int accent = ThemedDialog.getAccentColor();
        int firstDayOfWeek = PreferenceManager.getFirstDayOfWeek();
        String[] initials = DateFormatSymbols.getInstance(Locale.getDefault()).getWeekdays(DateFormatSymbols.STANDALONE, DateFormatSymbols.NARROW);
        ViewGroup header = (ViewGroup) mGrid.getChildAt(0);
        for (int column = 0; column < DAYS_IN_WEEK; column++) {
            ((TextView) header.getChildAt(column)).setText(initials[(firstDayOfWeek - 1 + column) % DAYS_IN_WEEK + 1]);
        }
        boolean between = mChoice == BETWEEN;
        String low = mFirst;
        String high = mSecond;
        if (low != null && high != null && low.compareTo(high) > 0) {
            low = mSecond;
            high = mFirst;
        }
        String today = DateUtils.getSQLDateString(today().getTime());
        NumberFormat digits = NumberFormat.getIntegerInstance();
        DateFormat description = DateFormat.getDateInstance(DateFormat.FULL);
        Calendar day = (Calendar) mMonth.clone();
        day.add(Calendar.DAY_OF_MONTH, -((day.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + DAYS_IN_WEEK) % DAYS_IN_WEEK));
        for (int row = 1; row <= WEEKS; row++) {
            ViewGroup line = (ViewGroup) mGrid.getChildAt(row);
            for (int column = 0; column < DAYS_IN_WEEK; column++) {
                TextView cell = (TextView) line.getChildAt(column);
                if (day.get(Calendar.MONTH) != mMonth.get(Calendar.MONTH)) {
                    cell.setText(null);
                    cell.setBackground(null);
                    cell.setContentDescription(null);
                    cell.setOnClickListener(null);
                    cell.setClickable(false);
                    cell.setFocusable(false);
                    cell.setSelected(false);
                    cell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                } else {
                    String date = DateUtils.getSQLDateString(day.getTime());
                    boolean end = date.equals(mFirst) || (between && date.equals(mSecond));
                    boolean inside = between && low != null && high != null && date.compareTo(low) > 0 && date.compareTo(high) < 0;
                    Drawable background = end ? getPill(accent, 0) : inside ? new GradientDrawable() : null;
                    if (inside) {
                        ((GradientDrawable) background).setColor(ColorUtils.setAlphaComponent(accent, RANGE_ALPHA));
                    }
                    if (!end && date.equals(today)) {
                        Drawable outline = getPill(0, accent);
                        background = background != null ? new LayerDrawable(new Drawable[] {background, outline}) : outline;
                    }
                    cell.setText(digits.format(day.get(Calendar.DAY_OF_MONTH)));
                    cell.setTextColor(end ? theme.getBestTextColor(accent) : theme.getTextColorPrimary());
                    cell.setBackground(withFocus(background, getPill(Color.WHITE, 0)));
                    cell.setContentDescription(description.format(day.getTime()));
                    cell.setSelected(end);
                    cell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
                    cell.setOnClickListener(v -> onDayClick(date));
                    cell.setFocusable(true);
                }
                day.add(Calendar.DAY_OF_MONTH, 1);
            }
        }
    }

    /**
     * From 1970 to ten years after this one, scrolled to the year on screen.
     */
    private void bindYears() {
        Context context = requireContext();
        ITheme theme = ThemeEngine.getTheme();
        int accent = ThemedDialog.getAccentColor();
        NumberFormat digits = NumberFormat.getIntegerInstance();
        digits.setGroupingUsed(false);
        int shown = mMonth.get(Calendar.YEAR);
        int last = today().get(Calendar.YEAR) + YEARS_AFTER_THIS;
        mYearList.removeAllViews();
        LinearLayout line = null;
        View selected = null;
        for (int year = FIRST_YEAR; year <= last; year++) {
            if ((year - FIRST_YEAR) % YEAR_COLUMNS == 0) {
                line = new LinearLayout(context);
                mYearList.addView(line);
            }
            TextView cell = new TextView(context);
            cell.setGravity(Gravity.CENTER);
            cell.setText(digits.format(year));
            boolean current = year == shown;
            cell.setTextColor(current ? theme.getBestTextColor(accent) : theme.getTextColorPrimary());
            cell.setBackground(withFocus(current ? getPill(accent, 0) : null, getPill(Color.WHITE, 0)));
            cell.setSelected(current);
            cell.setFocusable(true);
            int picked = year;
            cell.setOnClickListener(v -> {
                // the list hides with the focused year in it, so hand focus to the title
                boolean accessibilityFocused = v.createAccessibilityNodeInfo().isAccessibilityFocused();
                if (v.isFocused()) {
                    mMonthTextView.requestFocus();
                }
                mMonth.set(Calendar.YEAR, picked);
                mYearsShown = false;
                bindMonth();
                // a short editor is left scrolled past the title once the list hides
                mMonthTextView.requestRectangleOnScreen(new Rect(0, 0, mMonthTextView.getWidth(), mMonthTextView.getHeight()), true);
                // after the bind, so the new month is what is read out
                if (accessibilityFocused) {
                    mMonthTextView.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
                }
            });
            cell.setMinHeight(Math.round(dp(48f)));
            line.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            if (current) {
                selected = cell;
            }
        }
        while (line.getChildCount() < YEAR_COLUMNS) {
            // a filler of any other height sets the row to its own
            line.addView(new Space(context), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        if (selected != null) {
            View target = selected;
            ScrollView scrollView = mScrollView;
            // read now, Robolectric has cleared it by the time the scroll runs
            boolean accessibilityFocused = mMonthTextView.createAccessibilityNodeInfo().isAccessibilityFocused();
            OneShotPreDrawListener.add(scrollView, () -> {
                // the list closed, or a later bindYears replaced it and queued its own scroll
                if (!mYearsShown || !target.isAttachedToWindow()) {
                    return;
                }
                Rect bounds = new Rect(0, 0, target.getWidth(), target.getHeight());
                scrollView.offsetDescendantRectToMyCoords(target, bounds);
                scrollView.scrollTo(0, bounds.centerY() - scrollView.getHeight() / 2);
                // the scroll moved the focused title off screen
                if (mMonthTextView.isFocused()) {
                    target.requestFocus();
                }
                if (accessibilityFocused) {
                    target.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
                }
            });
        }
    }

    /**
     * A ripple's focused state draws on every API, the platform's own focus highlight only from 26.
     */
    private Drawable withFocus(@Nullable Drawable background, Drawable mask) {
        return new RippleDrawable(ColorStateList.valueOf(ThemeEngine.getTheme().getColorRipple()), background, mask);
    }

    private GradientDrawable getPill(int fill, int stroke) {
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(CELL_HEIGHT_DP) / 2f);
        pill.setColor(fill);
        if (stroke != 0) {
            pill.setStroke(Math.round(dp(1f)), stroke);
        }
        return pill;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static int indexOf(int[] ids, int id) {
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == id) {
                return i;
            }
        }
        throw new IllegalStateException("No choice is checked");
    }

    private static Calendar getMonth(int year, int month) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, 1);
        return calendar;
    }

    private static Calendar getMonth(Date date) {
        Calendar calendar = DateUtils.getCalendar(date);
        return getMonth(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH));
    }

    private static String[] getOrdered(String from, String to) {
        return from.compareTo(to) > 0 ? new String[] {to, from} : new String[] {from, to};
    }

    /*package-local*/ static Calendar today() {
        Calendar today = Calendar.getInstance();
        if (sToday != null) {
            today.setTimeInMillis(sToday);
        }
        DateUtils.setTime(today, 0, 0, 0, 0);
        return today;
    }

    /**
     * @return the first and the last day of the preset, as yyyy-MM-dd. The months follow the
     *         first day of the month setting, as the transaction list's month headers do.
     */
    /*package-local*/ static String[] getPresetRange(int preset, Calendar today) {
        Date from;
        Date to;
        switch (preset) {
            case THIS_MONTH:
            case LAST_MONTH:
                DateRangeHeader month = new DateRangeHeader(Group.MONTHLY, null, null, today.getTime());
                if (preset == LAST_MONTH) {
                    Date before = DateUtils.addDays(DateUtils.getCalendar(month.getStartDate()), -1);
                    month = new DateRangeHeader(Group.MONTHLY, null, null, before);
                }
                from = month.getStartDate();
                to = month.getEndDate();
                break;
            case LAST_30_DAYS:
                to = today.getTime();
                from = DateUtils.addDays((Calendar) today.clone(), -29);
                break;
            default:
                DateRangeHeader year = new DateRangeHeader(Group.YEARLY, null, null, today.getTime());
                from = year.getStartDate();
                to = year.getEndDate();
                break;
        }
        return new String[] {DateUtils.getSQLDateString(from), DateUtils.getSQLDateString(to)};
    }

    /**
     * @return the chip text for the days, or null when neither end is set.
     */
    @Nullable
    /*package-local*/ static String getValue(Context context, @Nullable String from, @Nullable String to) {
        Calendar today = today();
        if (from != null && to != null) {
            String[] range = getOrdered(from, to);
            for (int i = 0; i < PRESET_NAMES.length; i++) {
                if (Arrays.equals(range, getPresetRange(i, today))) {
                    return context.getString(PRESET_NAMES[i]);
                }
            }
            return context.getString(R.string.search_value_date_between, formatDay(range[0], today), formatDay(range[1], today));
        }
        if (from != null) {
            return context.getString(R.string.search_value_date_after, formatDay(from, today));
        }
        if (to != null) {
            return context.getString(R.string.search_value_date_before, formatDay(to, today));
        }
        return null;
    }

    /**
     * The locale's own month and day, with the year when it is not this one.
     */
    private static String formatDay(String day, Calendar today) {
        Date date = DateUtils.getDateFromSQLDateString(day);
        boolean thisYear = DateUtils.getCalendar(date).get(Calendar.YEAR) == today.get(Calendar.YEAR);
        Locale locale = Locale.getDefault();
        return new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, thisYear ? "MMMd" : "yMMMd"), locale).format(date);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SS_CHOICE, mChoice);
        outState.putString(SS_FIRST, mFirst);
        outState.putString(SS_SECOND, mSecond);
        outState.putBoolean(SS_FOCUS_TO, mFocusTo);
        outState.putInt(SS_MONTH, mMonth.get(Calendar.YEAR) * 12 + mMonth.get(Calendar.MONTH));
        outState.putBoolean(SS_YEARS, mYearsShown);
    }
}
