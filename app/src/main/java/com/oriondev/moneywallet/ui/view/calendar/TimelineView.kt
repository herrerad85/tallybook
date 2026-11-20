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
import android.graphics.Typeface;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.oriondev.moneywallet.R;

import android.icu.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import static com.oriondev.moneywallet.utils.DateUtils.getCalendarDaysBetween;

public class TimelineView extends RecyclerView {

    private static final String TAG = "TimelineView";

    private final String[] weekDays = narrowWeekdays();

    private float dayLabelSize = 0f;

    private final Calendar calendar = Calendar.getInstance(Locale.getDefault());

    private TimelineAdapter adapter;
    private LinearLayoutManager layoutManager;
    private OnDateSelectedListener onDateSelectedListener;
    private OnMonthScrolledListener onMonthScrolledListener;
    private MonthView.DateLabelAdapter dateLabelAdapter;

    private Set<Integer> markedDays = Collections.emptySet();

    private int startYear = 1970, startMonth = 0, startDay = 1;
    private int selectedYear, selectedMonth, selectedDay;
    private int selectedPosition = 1;
    private int dayCount = Integer.MAX_VALUE;

    // Day letter
    private int lblDayColor;
    // Day number label
    private int lblDateColor, lblDateSelectedColor;

    public TimelineView(Context context) {
        super(context);
        init();
    }

    public TimelineView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TimelineView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    /**
     * The narrow weekday headers, indexed by the {@link Calendar#SUNDAY} to
     * {@link Calendar#SATURDAY} values.
     *
     * These come from the locale rather than from cutting a longer name to a fixed
     * number of characters. Cutting assumes every language distinguishes its days in
     * the first character, and of the twenty locales bundled here only Persian does:
     * the Chinese short names all begin 周 and the Catalan ones all begin d, so the
     * whole week rendered as one repeated glyph. CLDR already publishes a narrow name
     * per weekday for exactly this position, so the shortening is a lookup rather than
     * a rule this app invents.
     *
     * A narrow name is not one char, which is what maxLength counted. CLDR gives Catalan
     * dg, dl, dt, dc, dj, dv and ds, and gives Malayalam names wider still, so the labels
     * are sized to fit rather than assumed to fit. The uppercasing below is this app's.
     */
    private static String[] narrowWeekdays() {
        return new DateFormatSymbols(Locale.getDefault()).getWeekdays(
                DateFormatSymbols.STANDALONE, DateFormatSymbols.NARROW);
    }

    private String dayLabel(int dayOfWeek) {
        return weekDays[dayOfWeek].toUpperCase(Locale.getDefault());
    }

    /**
     * The seven labels this strip renders. Built from the rendered form rather than passing the
     * array behind it, which is indexed from {@link Calendar#SUNDAY} rather than from zero and
     * does not carry the uppercasing this class adds.
     */
    private String[] dayLabels() {
        String[] labels = new String[Calendar.SATURDAY - Calendar.SUNDAY + 1];
        for (int dayOfWeek = Calendar.SUNDAY; dayOfWeek <= Calendar.SATURDAY; dayOfWeek++) {
            labels[dayOfWeek - Calendar.SUNDAY] = dayLabel(dayOfWeek);
        }
        return labels;
    }

    /**
     * One text size for all seven weekday labels, small enough that each of them measures
     * within a cell. For most locales it returns the starting size unchanged.
     *
     * Of the locales bundled here only Malayalam grows wide enough to need this, and only at
     * accessibility font scales. The label comes from the system locale rather than from those
     * translations, so the check is not limited to that set.
     */
    private float dayLabelSize(TextView sample, View cell) {
        if (dayLabelSize > 0f) {
            return dayLabelSize;
        }
        int available = cell.getLayoutParams().width - cell.getPaddingLeft() - cell.getPaddingRight();
        dayLabelSize = LabelFit.sizeToFit(sample.getPaint(), dayLabels(), available);
        return dayLabelSize;
    }

    private void init() {
        calendar.setTimeInMillis(System.currentTimeMillis());
        setSelectedDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        resetCalendar();

        setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        adapter = new TimelineAdapter();
        setLayoutManager(layoutManager);
        setAdapter(adapter);
        addOnScrollListener(new OnScrollListener() {

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                // A layout that changes which cells are laid out arrives here with nothing
                // scrolled, and the first layout of all lays the strip out on its own first day,
                // before the runnable that puts it on the selected day has run. A cell the strip
                // was placed on is reported by centerOnPosition instead.
                if (dx != 0) {
                    reportMonthAt(centerPosition());
                }
            }

        });
    }

    /**
     * The cell in the middle of the strip, or {@link #NO_POSITION} when there is no cell there.
     *
     * The middle and not the first visible cell, which would name a month while every cell of it
     * is still off the screen.
     */
    private int centerPosition() {
        View center = findChildViewUnder(getWidth() / 2f, getHeight() / 2f);
        return center == null ? NO_POSITION : getChildAdapterPosition(center);
    }

    /**
     * Hands the listener the month of one cell.
     *
     * Reported on every scroll and not only when that month changes, because what a listener does
     * with it depends on what the listener is showing, which this view cannot see. The month
     * comes from the same count the adapter binds a cell with, so the month reported is the month
     * of the numbers a person is looking at.
     */
    private void reportMonthAt(int position) {
        if (onMonthScrolledListener == null || position == NO_POSITION) {
            return;
        }
        resetCalendar();
        calendar.add(Calendar.DAY_OF_YEAR, position);
        onMonthScrolledListener.onMonthScrolled(calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH));
    }

    private void resetCalendar() {
        calendar.set(startYear, startMonth, startDay, 1, 0, 0);
    }

    private void onDateSelected(int position, int year, int month, int day) {
        if (position == selectedPosition) {
            centerOnPosition(selectedPosition);
            return;
        }
        int oldPosition = selectedPosition;
        selectedPosition = position;
        this.selectedYear = year;
        this.selectedMonth = month;
        this.selectedDay = day;
        if (adapter != null && layoutManager != null) {
            adapter.notifyItemChanged(oldPosition);
            adapter.notifyItemChanged(position);
            centerOnPosition(selectedPosition);
            if (onDateSelectedListener != null) {
                onDateSelectedListener.onDateSelected(selectedYear, selectedMonth, selectedDay, selectedPosition);
            }
        } else {
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
        int offset = getMeasuredWidth() / 2 - getChildAt(0).getMeasuredWidth() / 2;
        layoutManager.scrollToPositionWithOffset(position, offset);
        // the cell this lands on, which the scroll listener above does not report because
        // nothing is scrolled to get here. Tapping the day already selected reaches only this,
        // and it is how the strip is first put on the selected day
        reportMonthAt(position);
    }

    public void centerOnSelection() {
        centerOnPosition(selectedPosition);
    }

    public void setSelectedPosition(int position) {
        resetCalendar();
        calendar.add(Calendar.DAY_OF_YEAR, position);
        onDateSelected(position, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
    }

    public void setSelectedDate(int year, int month, int day) {
        if (year == startYear && month == startMonth && day < startDay) {
            day = startDay;
        }
        // counted from the first date, which is how TimelineAdapter turns a position back into a
        // date. What this replaced adjusted the previous position by a difference, so it needed
        // that position to already be right
        onDateSelected(getCalendarDaysBetween(startYear, startMonth, startDay, year, month, day),
                year, month, day);
    }

    public int getSelectedYear() {
        return selectedYear;
    }

    public int getSelectedMonth() {
        return selectedMonth;
    }

    public int getSelectedDay() {
        return selectedDay;
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public void setOnDateSelectedListener(OnDateSelectedListener onDateSelectedListener) {
        this.onDateSelectedListener = onDateSelectedListener;
    }

    public void setOnMonthScrolledListener(OnMonthScrolledListener onMonthScrolledListener) {
        this.onMonthScrolledListener = onMonthScrolledListener;
    }

    /**
     * The days that get a mark under the number, as the keys {@link #dayKey} builds. The strip
     * runs from 1900 to 2100 and binds a cell as it is scrolled into view, so the marked days are
     * held here and are not asked for one cell at a time.
     */
    public void setMarkedDays(@NonNull Set<Integer> markedDays) {
        this.markedDays = markedDays;
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    /**
     * A year, month and day read as one number. The month is the {@link Calendar} one, counted
     * from zero, so a caller holding a month counted from one subtracts before it calls this.
     */
    public static int dayKey(int year, int month, int day) {
        return (year * 100 + month) * 100 + day;
    }

    public void setDateLabelAdapter(@Nullable MonthView.DateLabelAdapter dateLabelAdapter) {
        this.dateLabelAdapter = dateLabelAdapter;
    }

    public void setDayLabelColor(int lblDayColor) {
        this.lblDayColor = lblDayColor;
    }

    public void setDateLabelColor(int lblDateColor) {
        this.lblDateColor = lblDateColor;
    }

    public void setDateLabelSelectedColor(int lblDateSelectedColor) {
        this.lblDateSelectedColor = lblDateSelectedColor;
    }

    public int getLblDateColor() {
        return lblDateColor;
    }

    public void setLblDateColor(int lblDateColor) {
        this.lblDateColor = lblDateColor;
    }

    public int getLblDateSelectedColor() {
        return lblDateSelectedColor;
    }

    public void setLblDateSelectedColor(int lblDateSelectedColor) {
        this.lblDateSelectedColor = lblDateSelectedColor;
    }

    public int getLblDayColor() {
        return lblDayColor;
    }

    public void setLblDayColor(int lblDayColor) {
        this.lblDayColor = lblDayColor;
    }

    public int getStartYear() {
        return startYear;
    }

    public int getStartDay() {
        return startDay;
    }

    public int getStartMonth() {
        return startMonth;
    }

    public void setFirstDate(int startYear, int startMonth, int startDay) {
        this.startYear = startYear;
        this.startMonth = startMonth;
        this.startDay = startDay;

        selectedYear = startYear;
        selectedMonth = startMonth;
        selectedDay = startDay;
        selectedPosition = 0;
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    public void setLastDate(int endYear, int endMonth, int endDay) {
        // the last date is a cell of its own, so the count is one more than the span
        setDayCount(getCalendarDaysBetween(startYear, startMonth, startDay, endYear, endMonth, endDay) + 1);
    }

    void setDayCount(int dayCount) {
        if (this.dayCount == dayCount) {
            return;
        }

        this.dayCount = dayCount;
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private class TimelineAdapter extends RecyclerView.Adapter<TimelineViewHolder> {

        TimelineAdapter() {

        }

        @Override
        public TimelineViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View view = inflater.inflate(R.layout.view_mti_item_day, parent, false);
            return new TimelineViewHolder(view);
        }

        @Override
        public void onBindViewHolder(TimelineViewHolder holder, int position) {
            resetCalendar();
            calendar.add(Calendar.DAY_OF_YEAR, position);
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            boolean isToday = DateUtils.isToday(calendar.getTimeInMillis());
            holder.bind(position, year, month, day, dayOfWeek,position == selectedPosition, isToday,
                    markedDays.contains(dayKey(year, month, day)));
        }

        @Override
        public int getItemCount() {
            return dayCount;
        }
    }

    private class TimelineViewHolder extends RecyclerView.ViewHolder {

        private final TextView lblDay;
        private final TextView lblDate;
        private final DotView dotMarker;

        private int position;
        private int year, month, day;

        TimelineViewHolder(View root) {
            super(root);

            lblDay = root.findViewById(R.id.mti_timeline_lbl_day);
            lblDate = root.findViewById(R.id.mti_timeline_lbl_date);
            dotMarker = root.findViewById(R.id.mti_timeline_dot_marker);

            lblDay.setTextColor(lblDayColor);
            lblDay.setTextSize(TypedValue.COMPLEX_UNIT_PX, dayLabelSize(lblDay, root));
            lblDate.setTextColor(lblDateColor);

            root.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View view) {
                    onDateSelected(position, year, month, day);
                }

            });
        }

        void bind(int position, int year, int month, int day, int dayOfWeek, boolean selected, boolean isToday, boolean marked) {
            this.position = position;
            this.year = year;
            this.month = month;
            this.day = day;
            lblDay.setText(dayLabel(dayOfWeek));
            lblDate.setText(String.format(Locale.getDefault(), "%d", day));
            // The line below has never run here and neither drawable it names is in this project,
            // so the accent color is the only mark on a cell and it marks today and the shown day
            // alike. Weight is what separates them: the shown day is the bold one.
            // lblDate.setBackgroundResource(selected ? R.drawable.mti_bg_lbl_date_selected : (isToday ? R.drawable.mti_bg_lbl_date_today : 0));
            int dateColor = selected || isToday ? lblDateSelectedColor : lblDateColor;
            lblDate.setTextColor(dateColor);
            lblDate.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            dotMarker.setColor(dateColor);
            dotMarker.setVisibility(marked ? VISIBLE : INVISIBLE);
        }
    }

    /**
     * Told the month of the cell in the middle of the strip, every time the strip scrolls. A day
     * number on its own does not say which month it belongs to, so anything naming that month
     * elsewhere on the screen listens here to keep the name and the numbers in step.
     */
    public interface OnMonthScrolledListener {

        void onMonthScrolled(int year, int month);
    }
}