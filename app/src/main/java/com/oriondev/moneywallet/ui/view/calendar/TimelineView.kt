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

package com.oriondev.moneywallet.ui.view.calendar

import android.content.Context
import android.graphics.Typeface
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.text.format.DateUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import com.oriondev.moneywallet.R

import android.icu.text.DateFormatSymbols
import java.util.Calendar
import java.util.Collections
import java.util.Locale

import com.oriondev.moneywallet.utils.DateUtils.getCalendarDaysBetween

open class TimelineView : RecyclerView {

    companion object {

        private const val TAG = "TimelineView"

        /**
         * The narrow weekday headers, indexed by the [Calendar.SUNDAY] to
         * [Calendar.SATURDAY] values.
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
        private fun narrowWeekdays(): Array<String> {
            return DateFormatSymbols(Locale.getDefault()).getWeekdays(
                    DateFormatSymbols.STANDALONE, DateFormatSymbols.NARROW)
        }

        /**
         * A year, month and day read as one number. The month is the [Calendar] one, counted
         * from zero, so a caller holding a month counted from one subtracts before it calls this.
         */
        @JvmStatic
        fun dayKey(year: Int, month: Int, day: Int): Int {
            return (year * 100 + month) * 100 + day
        }
    }

    private val weekDays = narrowWeekdays()

    private var dayLabelSize = 0f

    private val calendar = Calendar.getInstance(Locale.getDefault())

    private var adapter: TimelineAdapter? = null
    private var layoutManager: LinearLayoutManager? = null
    private var onDateSelectedListener: OnDateSelectedListener? = null
    private var onMonthScrolledListener: OnMonthScrolledListener? = null
    private var dateLabelAdapter: MonthView.DateLabelAdapter? = null

    private var markedDays: Set<Int> = Collections.emptySet()

    private var startYear = 1970
    private var startMonth = 0
    private var startDay = 1
    private var selectedYear: Int = 0
    private var selectedMonth: Int = 0
    private var selectedDay: Int = 0
    private var selectedPosition = 1
    private var dayCount = Integer.MAX_VALUE

    // Day letter
    private var lblDayColor: Int = 0
    // Day number label
    private var lblDateColor: Int = 0
    private var lblDateSelectedColor: Int = 0

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        init()
    }

    private fun dayLabel(dayOfWeek: Int): String {
        return weekDays[dayOfWeek].uppercase(Locale.getDefault())
    }

    /**
     * The seven labels this strip renders. Built from the rendered form rather than passing the
     * array behind it, which is indexed from [Calendar.SUNDAY] rather than from zero and
     * does not carry the uppercasing this class adds.
     */
    private fun dayLabels(): Array<String?> {
        val labels = arrayOfNulls<String>(Calendar.SATURDAY - Calendar.SUNDAY + 1)
        for (dayOfWeek in Calendar.SUNDAY..Calendar.SATURDAY) {
            labels[dayOfWeek - Calendar.SUNDAY] = dayLabel(dayOfWeek)
        }
        return labels
    }

    /**
     * One text size for all seven weekday labels, small enough that each of them measures
     * within a cell. For most locales it returns the starting size unchanged.
     *
     * Of the locales bundled here only Malayalam grows wide enough to need this, and only at
     * accessibility font scales. The label comes from the system locale rather than from those
     * translations, so the check is not limited to that set.
     */
    private fun dayLabelSize(sample: TextView, cell: View): Float {
        if (dayLabelSize > 0f) {
            return dayLabelSize
        }
        val available = cell.layoutParams.width - cell.paddingLeft - cell.paddingRight
        dayLabelSize = LabelFit.sizeToFit(sample.paint, dayLabels(), available)
        return dayLabelSize
    }

    private fun init() {
        calendar.timeInMillis = System.currentTimeMillis()
        setSelectedDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        resetCalendar()

        setHasFixedSize(true)
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        adapter = TimelineAdapter()
        setLayoutManager(layoutManager)
        setAdapter(adapter)
        addOnScrollListener(object : OnScrollListener() {

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // A layout that changes which cells are laid out arrives here with nothing
                // scrolled, and the first layout of all lays the strip out on its own first day,
                // before the runnable that puts it on the selected day has run. A cell the strip
                // was placed on is reported by centerOnPosition instead.
                if (dx != 0) {
                    reportMonthAt(centerPosition())
                }
            }

        })
    }

    /**
     * The cell in the middle of the strip, or [NO_POSITION] when there is no cell there.
     *
     * The middle and not the first visible cell, which would name a month while every cell of it
     * is still off the screen.
     */
    private fun centerPosition(): Int {
        val center = findChildViewUnder(width / 2f, height / 2f)
        return if (center == null) NO_POSITION else getChildAdapterPosition(center)
    }

    /**
     * Hands the listener the month of one cell.
     *
     * Reported on every scroll and not only when that month changes, because what a listener does
     * with it depends on what the listener is showing, which this view cannot see. The month
     * comes from the same count the adapter binds a cell with, so the month reported is the month
     * of the numbers a person is looking at.
     */
    private fun reportMonthAt(position: Int) {
        if (onMonthScrolledListener == null || position == NO_POSITION) {
            return
        }
        resetCalendar()
        calendar.add(Calendar.DAY_OF_YEAR, position)
        onMonthScrolledListener!!.onMonthScrolled(calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH))
    }

    private fun resetCalendar() {
        calendar.set(startYear, startMonth, startDay, 1, 0, 0)
    }

    private fun onDateSelected(position: Int, year: Int, month: Int, day: Int) {
        if (position == selectedPosition) {
            centerOnPosition(selectedPosition)
            return
        }
        val oldPosition = selectedPosition
        selectedPosition = position
        this.selectedYear = year
        this.selectedMonth = month
        this.selectedDay = day
        if (adapter != null && layoutManager != null) {
            adapter!!.notifyItemChanged(oldPosition)
            adapter!!.notifyItemChanged(position)
            centerOnPosition(selectedPosition)
            if (onDateSelectedListener != null) {
                onDateSelectedListener!!.onDateSelected(selectedYear, selectedMonth, selectedDay, selectedPosition)
            }
        } else {
            post(object : Runnable {
                override fun run() {
                    centerOnPosition(selectedPosition)
                }
            })
        }
    }

    fun centerOnPosition(position: Int) {
        if (childCount == 0 || !isLaidOut) {
            return
        }
        // Animate scroll
        val offset = measuredWidth / 2 - getChildAt(0).measuredWidth / 2
        layoutManager!!.scrollToPositionWithOffset(position, offset)
        // the cell this lands on, which the scroll listener above does not report because
        // nothing is scrolled to get here. Tapping the day already selected reaches only this,
        // and it is how the strip is first put on the selected day
        reportMonthAt(position)
    }

    fun centerOnSelection() {
        centerOnPosition(selectedPosition)
    }

    fun setSelectedPosition(position: Int) {
        resetCalendar()
        calendar.add(Calendar.DAY_OF_YEAR, position)
        onDateSelected(position, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH))
    }

    fun setSelectedDate(year: Int, month: Int, day: Int) {
        var day = day
        if (year == startYear && month == startMonth && day < startDay) {
            day = startDay
        }
        // counted from the first date, which is how TimelineAdapter turns a position back into a
        // date. What this replaced adjusted the previous position by a difference, so it needed
        // that position to already be right
        onDateSelected(getCalendarDaysBetween(startYear, startMonth, startDay, year, month, day),
                year, month, day)
    }

    fun getSelectedYear(): Int {
        return selectedYear
    }

    fun getSelectedMonth(): Int {
        return selectedMonth
    }

    fun getSelectedDay(): Int {
        return selectedDay
    }

    fun getSelectedPosition(): Int {
        return selectedPosition
    }

    fun setOnDateSelectedListener(onDateSelectedListener: OnDateSelectedListener?) {
        this.onDateSelectedListener = onDateSelectedListener
    }

    fun setOnMonthScrolledListener(onMonthScrolledListener: OnMonthScrolledListener?) {
        this.onMonthScrolledListener = onMonthScrolledListener
    }

    /**
     * The days that get a mark under the number, as the keys [dayKey] builds. The strip
     * runs from 1900 to 2100 and binds a cell as it is scrolled into view, so the marked days are
     * held here and are not asked for one cell at a time.
     */
    fun setMarkedDays(markedDays: Set<Int>) {
        this.markedDays = markedDays
        if (adapter != null) {
            adapter!!.notifyDataSetChanged()
        }
    }

    fun setDateLabelAdapter(dateLabelAdapter: MonthView.DateLabelAdapter?) {
        this.dateLabelAdapter = dateLabelAdapter
    }

    fun setDayLabelColor(lblDayColor: Int) {
        this.lblDayColor = lblDayColor
    }

    fun setDateLabelColor(lblDateColor: Int) {
        this.lblDateColor = lblDateColor
    }

    fun setDateLabelSelectedColor(lblDateSelectedColor: Int) {
        this.lblDateSelectedColor = lblDateSelectedColor
    }

    fun getLblDateColor(): Int {
        return lblDateColor
    }

    fun setLblDateColor(lblDateColor: Int) {
        this.lblDateColor = lblDateColor
    }

    fun getLblDateSelectedColor(): Int {
        return lblDateSelectedColor
    }

    fun setLblDateSelectedColor(lblDateSelectedColor: Int) {
        this.lblDateSelectedColor = lblDateSelectedColor
    }

    fun getLblDayColor(): Int {
        return lblDayColor
    }

    fun setLblDayColor(lblDayColor: Int) {
        this.lblDayColor = lblDayColor
    }

    fun getStartYear(): Int {
        return startYear
    }

    fun getStartDay(): Int {
        return startDay
    }

    fun getStartMonth(): Int {
        return startMonth
    }

    fun setFirstDate(startYear: Int, startMonth: Int, startDay: Int) {
        this.startYear = startYear
        this.startMonth = startMonth
        this.startDay = startDay

        selectedYear = startYear
        selectedMonth = startMonth
        selectedDay = startDay
        selectedPosition = 0
        if (adapter != null) {
            adapter!!.notifyDataSetChanged()
        }
    }

    fun setLastDate(endYear: Int, endMonth: Int, endDay: Int) {
        // the last date is a cell of its own, so the count is one more than the span
        setDayCount(getCalendarDaysBetween(startYear, startMonth, startDay, endYear, endMonth, endDay) + 1)
    }

    private fun setDayCount(dayCount: Int) {
        if (this.dayCount == dayCount) {
            return
        }

        this.dayCount = dayCount
        if (adapter != null) {
            adapter!!.notifyDataSetChanged()
        }
    }

    private inner class TimelineAdapter : RecyclerView.Adapter<TimelineViewHolder> {

        constructor() {

        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimelineViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val view = inflater.inflate(R.layout.view_mti_item_day, parent, false)
            return TimelineViewHolder(view)
        }

        override fun onBindViewHolder(holder: TimelineViewHolder, position: Int) {
            resetCalendar()
            calendar.add(Calendar.DAY_OF_YEAR, position)
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val isToday = DateUtils.isToday(calendar.timeInMillis)
            holder.bind(position, year, month, day, dayOfWeek, position == selectedPosition, isToday,
                    markedDays.contains(dayKey(year, month, day)))
        }

        override fun getItemCount(): Int {
            return dayCount
        }
    }

    private inner class TimelineViewHolder : RecyclerView.ViewHolder {

        private val lblDay: TextView
        private val lblDate: TextView
        private val dotMarker: DotView

        private var position: Int = 0
        private var year: Int = 0
        private var month: Int = 0
        private var day: Int = 0

        constructor(root: View) : super(root) {

            lblDay = root.findViewById(R.id.mti_timeline_lbl_day)
            lblDate = root.findViewById(R.id.mti_timeline_lbl_date)
            dotMarker = root.findViewById(R.id.mti_timeline_dot_marker)

            lblDay.setTextColor(lblDayColor)
            lblDay.setTextSize(TypedValue.COMPLEX_UNIT_PX, dayLabelSize(lblDay, root))
            lblDate.setTextColor(lblDateColor)

            root.setOnClickListener(object : OnClickListener {

                override fun onClick(view: View?) {
                    onDateSelected(position, year, month, day)
                }

            })
        }

        fun bind(position: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, selected: Boolean, isToday: Boolean, marked: Boolean) {
            this.position = position
            this.year = year
            this.month = month
            this.day = day
            lblDay.text = dayLabel(dayOfWeek)
            lblDate.text = String.format(Locale.getDefault(), "%d", day)
            // The line below has never run here and neither drawable it names is in this project,
            // so the accent color is the only mark on a cell and it marks today and the shown day
            // alike. Weight is what separates them: the shown day is the bold one.
            // lblDate.setBackgroundResource(selected ? R.drawable.mti_bg_lbl_date_selected : (isToday ? R.drawable.mti_bg_lbl_date_today : 0));
            val dateColor = if (selected || isToday) lblDateSelectedColor else lblDateColor
            lblDate.setTextColor(dateColor)
            lblDate.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            dotMarker.setColor(dateColor)
            dotMarker.visibility = if (marked) VISIBLE else INVISIBLE
        }
    }

    /**
     * Told the month of the cell in the middle of the strip, every time the strip scrolls. A day
     * number on its own does not say which month it belongs to, so anything naming that month
     * elsewhere on the screen listens here to keep the name and the numbers in step.
     */
    interface OnMonthScrolledListener {

        fun onMonthScrolled(year: Int, month: Int)
    }
}
