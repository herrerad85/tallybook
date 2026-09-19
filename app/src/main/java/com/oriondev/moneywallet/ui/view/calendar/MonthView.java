/*
 * Copyright (c) 2018.
 *
 * Portions derived from datepicker-timeline, Copyright (c) 2015 Yann Badoual,
 * MIT License.
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

package com.oriondev.moneywallet.ui.view.calendar;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.oriondev.moneywallet.R;

import android.icu.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Locale;

public class MonthView extends RecyclerView {

    // per instance so the names follow an app language change, which rebuilds this view
    private final String[] months = abbreviatedMonths();

    private float labelSize = 0f;

    private MonthAdapter adapter;
    private LinearLayoutManager layoutManager;
    private OnMonthSelectedListener onMonthSelectedListener;

    private int defaultColor, colorSelected, colorBeforeSelection;

    private int startYear = 1970, startMonth = 0;
    private int yearDigitCount = 2;

    private int selectedYear, selectedMonth;
    private int selectedPosition = -1;
    private int monthCount = Integer.MAX_VALUE;

    public MonthView(Context context) {
        super(context);
        init();
    }

    public MonthView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MonthView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    /**
     * The abbreviated month names, indexed by the {@link Calendar#JANUARY} to
     * {@link Calendar#DECEMBER} values.
     *
     * Standalone rather than the form a full date uses, because this strip names a month on its
     * own rather than inside a date. Catalan is the case that showed it here: its formatting
     * abbreviations are de gen., de febr. and so on, so cutting them to three characters left
     * five of the seven visible cells reading DE.
     */
    private static String[] abbreviatedMonths() {
        return new DateFormatSymbols(Locale.getDefault()).getMonths(
                DateFormatSymbols.STANDALONE, DateFormatSymbols.ABBREVIATED);
    }

    private String monthName(int month) {
        return months[month].toUpperCase(Locale.getDefault());
    }

    /**
     * Every label this strip can render, each carrying a stand in for its year.
     *
     * The year differs per cell, so the stand in is the widest digit repeated rather than one
     * chosen year, which keeps the derived size from depending on which months and years
     * happened to be on screen when it was derived.
     */
    private String[] widestLabels(TextPaint paint) {
        String year = widestYear(paint);
        String[] labels = new String[months.length];
        for (int month = 0; month < months.length; month++) {
            labels[month] = monthName(month) + year;
        }
        return labels;
    }

    private String widestYear(TextPaint paint) {
        if (yearDigitCount <= 0) {
            return "";
        }
        // the digits as the locale writes them, which is not the Latin set in every language
        String widest = String.format(Locale.getDefault(), "%d", 0);
        float widestWidth = paint.measureText(widest);
        for (int value = 1; value <= 9; value++) {
            String rendered = String.format(Locale.getDefault(), "%d", value);
            float width = paint.measureText(rendered);
            if (width > widestWidth) {
                widestWidth = width;
                widest = rendered;
            }
        }
        StringBuilder year = new StringBuilder(" ");
        for (int digit = 0; digit < yearDigitCount; digit++) {
            year.append(widest);
        }
        return year.toString();
    }

    /**
     * One text size for every month label, small enough that each of them measures within a
     * cell.
     *
     * Sized rather than cut, because cutting to a character count is the defect this change
     * removes, and sized rather than left to wrap: read off the running strip in Malayalam,
     * master renders month labels at two different heights in one row.
     */
    private float labelSize(TextView sample, View cell) {
        if (labelSize > 0f) {
            return labelSize;
        }
        int available = cell.getLayoutParams().width - cell.getPaddingLeft() - cell.getPaddingRight();
        labelSize = LabelFit.sizeToFit(sample.getPaint(), widestLabels(sample.getPaint()), available);
        return labelSize;
    }

    private void init() {
        final Calendar calendar = Calendar.getInstance();
        setSelectedMonth(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), false);

        setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        adapter = new MonthAdapter();
        setLayoutManager(layoutManager);
        setAdapter(adapter);
    }

    public void setSelectedMonth(int year, int month) {
        setSelectedMonth(year, month, true, true);
    }

    public void setSelectedMonth(int year, int month, boolean callListener) {
        setSelectedMonth(year, month, callListener, true);
    }

    public void setSelectedMonth(int year, int month, boolean callListener, boolean centerOnPosition) {
        onMonthSelected(year, month, callListener, centerOnPosition);
    }

    public int getSelectedYear() {
        return selectedYear;
    }

    public int getSelectedMonth() {
        return selectedMonth;
    }

    private void onMonthSelected(int year, int month, boolean callListener, boolean centerOnPosition) {
        int oldPosition = selectedPosition;
        selectedPosition = getPositionForDate(year, month);
        selectedYear = year;
        selectedMonth = month;

        if (selectedPosition == oldPosition) {
            if (centerOnPosition) {
                centerOnPosition(selectedPosition);
            }
            // the month tapped is the one already marked. That used to be a request for the month
            // the selected day was already in, and the listener was skipped; the mark now follows
            // a strip that scrolls, so this is somebody asking to go to the month they can see
            if (callListener && onMonthSelectedListener != null) {
                onMonthSelectedListener.onMonthSelected(year, month, selectedPosition);
            }
            return;
        }

        if (adapter != null && layoutManager != null) {
            final int rangeStart = Math.min(oldPosition, selectedPosition);
            final int rangeEnd = Math.max(oldPosition, selectedPosition);
            adapter.notifyItemRangeChanged(rangeStart, rangeEnd - rangeStart + 1);

            // Animate scroll
            if (centerOnPosition) {
                centerOnPosition(selectedPosition);
            }

            if (callListener && onMonthSelectedListener != null) {
                onMonthSelectedListener.onMonthSelected(year, month, selectedPosition);
            }
        } else if (centerOnPosition) {
            post(new Runnable() {
                @Override
                public void run() {
                    centerOnPosition(selectedPosition);
                }
            });
        }
    }

    public void centerOnPosition(int position) {
        if (getChildCount() == 0 || !isLaidOut()) {
            return;
        }
        // Animate scroll
        int offset = getMeasuredWidth() / 2 - getItemWidth() / 2;
        layoutManager.scrollToPositionWithOffset(position, offset);
    }

    public void centerOnDate(int year, int month) {
        centerOnPosition(getPositionForDate(year, month));
    }

    public void centerOnSelection() {
        centerOnPosition(selectedPosition);
    }

    void scrollToYearPosition(int year, int offsetYear) {
        if (getChildCount() == 0 || !isLaidOut()) {
            return;
        }
        // Animate scroll
        layoutManager.scrollToPositionWithOffset(getPositionForDate(year + 1, 0),
                offsetYear + getMeasuredWidth() / 2 - getItemWidth() / 2);
    }

    int getItemWidth() {
        return getChildAt(0).getMeasuredWidth();
    }

    int getYearWidth() {
        return getItemWidth() * 12;
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    private int getYearForPosition(int position) {
        return (position + startMonth) / 12 + startYear;
    }

    private int getMonthForPosition(int position) {
        return (startMonth + position) % 12;
    }

    private int getPositionForDate(int year, int month) {
        return (12 * (year - startYear) + month) - startMonth;
    }

    public void setOnMonthSelectedListener(OnMonthSelectedListener onMonthSelectedListener) {
        this.onMonthSelectedListener = onMonthSelectedListener;
    }

    public OnMonthSelectedListener getOnMonthSelectedListener() {
        return onMonthSelectedListener;
    }

    public int getMonthCount() {
        return monthCount;
    }

    /**
     * Default indicator and text color
     */
    public void setDefaultColor(int defaultColor) {
        this.defaultColor = defaultColor;
    }

    /**
     * Color when month is selected
     */
    public void setColorSelected(int colorSelected) {
        this.colorSelected = colorSelected;
    }

    /**
     * Color when month is before the current selected month
     */
    public void setColorBeforeSelection(int colorBeforeSelection) {
        this.colorBeforeSelection = colorBeforeSelection;
    }

    public int getDefaultColor() {
        return defaultColor;
    }

    public int getColorBeforeSelection() {
        return colorBeforeSelection;
    }

    public int getColorSelected() {
        return colorSelected;
    }

    public void setYearDigitCount(int yearDigitCount) {
        if (yearDigitCount < 0 || yearDigitCount > 4) {
            throw new IllegalArgumentException("yearDigitCount cannot be smaller than 0 or greater than 4");
        }
        this.yearDigitCount = yearDigitCount;
    }

    public int getYearDigitCount() {
        return yearDigitCount;
    }

    public void setFirstDate(int startYear, int startMonth) {
        this.startYear = startYear;
        this.startMonth = startMonth;
        selectedYear = startYear;
        selectedMonth = startMonth;
        selectedPosition = 0;
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    void setMonthCount(int monthCount) {
        if (this.monthCount == monthCount) {
            return;
        }

        this.monthCount = monthCount;
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    public void setLastDate(int endYear, int endMonth) {
        if (endYear < startYear || (endYear == startYear && endMonth < startMonth)) {
            throw new IllegalArgumentException("Last visible date cannot be before first visible date");
        }

        Calendar firstDate = Calendar.getInstance();
        firstDate.set(startYear, startMonth, 1);
        Calendar lastDate = Calendar.getInstance();
        lastDate.set(endYear, endMonth, 1);
        int diffYear = lastDate.get(Calendar.YEAR) - firstDate.get(Calendar.YEAR);
        int diffMonth = diffYear * 12 + lastDate.get(Calendar.MONTH) - firstDate.get(Calendar.MONTH);

        setMonthCount(diffMonth + 1);
    }

    private class MonthAdapter extends RecyclerView.Adapter<MonthViewHolder> {

        @Override
        public MonthViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View view = inflater.inflate(R.layout.view_mti_item_month, parent, false);
            return new MonthViewHolder(view);
        }

        @Override
        public void onBindViewHolder(MonthViewHolder holder, int position) {
            final int year = getYearForPosition(position);
            final int month = getMonthForPosition(position);
            holder.bind(year, month, position == selectedPosition, position < selectedPosition);
        }

        @Override
        public int getItemCount() {
            return monthCount;
        }
    }

    private class MonthViewHolder extends RecyclerView.ViewHolder {

        private final TextView lbl;
        private final DotView indicator;

        private int year, month;

        MonthViewHolder(View root) {
            super(root);

            indicator = root.findViewById(R.id.mti_view_indicator);
            lbl = root.findViewById(R.id.mti_month_lbl);
            lbl.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize(lbl, root));

            root.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    onMonthSelected(year, month, true, true);
                }
            });
        }

        void bind(int year, int month, boolean selected, boolean beforeSelection) {
            this.year = year;
            this.month = month;

            String text = monthName(month);
            if (yearDigitCount > 0) {
                text += " ";
                // padded, because a year ending in 00 through 09 leaves a remainder of one
                // digit, and 2009 unpadded reads as the year 9
                text += String.format(Locale.getDefault(), "%0" + yearDigitCount + "d",
                        year % (int) Math.pow(10, yearDigitCount));
            }
            lbl.setText(text);
            int color = selected ? colorSelected : beforeSelection ? colorBeforeSelection : defaultColor;
            lbl.setTextColor(color);
            indicator.setColor(color);
            indicator.setCircleSizeDp(selected ? 12 : 5);
        }
    }

    public interface OnMonthSelectedListener {

        void onMonthSelected(int year, int month, int index);
    }

    public interface DateLabelAdapter {

        CharSequence getLabel(Calendar calendar, int index);
    }
}