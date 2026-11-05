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

package com.oriondev.moneywallet.model

import android.content.Context
import android.os.Parcel
import android.os.Parcelable

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.DateUtils

import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.Weekday
import org.dmfs.rfc5545.recur.Freq
import org.dmfs.rfc5545.recur.InvalidRecurrenceRuleException
import org.dmfs.rfc5545.recur.RecurrenceRule

import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.Objects

/**
 * Created by andrea on 07/11/18.
 */
class RecurrenceSetting : Parcelable {

    private var mStartDate: Date?
    private var mRecurrenceRule: RecurrenceRule

    constructor(startDate: Date?, type: Int) {
        mStartDate = startDate
        mRecurrenceRule = RecurrenceRule(getFreq(type))
    }

    constructor(startDate: Date?, rule: String?) {
        mStartDate = startDate
        try {
            mRecurrenceRule = RecurrenceRule(rule)
        } catch (ignore: InvalidRecurrenceRuleException) {
            mRecurrenceRule = RecurrenceRule(Freq.DAILY)
        }
    }

    private constructor(startDate: Date?, recurrenceRule: RecurrenceRule) {
        mStartDate = startDate
        mRecurrenceRule = recurrenceRule
    }

    private constructor(source: Parcel) {
        try {
            mStartDate = source.readSerializable() as Date?
            mRecurrenceRule = RecurrenceRule(source.readString())
        } catch (e: InvalidRecurrenceRuleException) {
            throw RuntimeException(e)
        }
    }

    fun getType(): Int {
        when (mRecurrenceRule.freq!!) {
            Freq.DAILY ->
                return TYPE_DAILY
            Freq.WEEKLY ->
                return TYPE_WEEKLY
            Freq.MONTHLY ->
                return TYPE_MONTHLY
            Freq.YEARLY ->
                return TYPE_YEARLY
            else ->
                return TYPE_DAILY
        }
    }

    fun getStartDate(): Date? {
        return mStartDate
    }

    fun getOffsetValue(): Int {
        return mRecurrenceRule.interval
    }

    fun getWeekDays(): BooleanArray {
        val weekDays = booleanArrayOf(false, false, false, false, false, false, false)
        val weekdayNumList: List<RecurrenceRule.WeekdayNum>? = mRecurrenceRule.byDayPart
        if (weekdayNumList != null && weekdayNumList.size > 0) {
            for (weekdayNum in weekdayNumList) {
                when (weekdayNum.weekday!!) {
                    Weekday.SU ->
                        weekDays[0] = true
                    Weekday.MO ->
                        weekDays[1] = true
                    Weekday.TU ->
                        weekDays[2] = true
                    Weekday.WE ->
                        weekDays[3] = true
                    Weekday.TH ->
                        weekDays[4] = true
                    Weekday.FR ->
                        weekDays[5] = true
                    Weekday.SA ->
                        weekDays[6] = true
                }
            }
        } else {
            val calendar = Calendar.getInstance()
            calendar.time = mStartDate
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            weekDays[dayOfWeek - 1] = true
        }
        return weekDays
    }

    fun getMonthDay(): Int {
        return FLAG_MONTHLY_SAME_DAY
    }

    fun getEndType(): Int {
        if (mRecurrenceRule.isInfinite) {
            return END_FOREVER
        } else if (mRecurrenceRule.until != null) {
            return END_UNTIL
        } else {
            return END_FOR
        }
    }

    fun getEndDate(): Date? {
        val dateTime: DateTime? = mRecurrenceRule.until
        return if (dateTime != null) Date(dateTime.timestamp) else mStartDate
    }

    fun getOccurrenceValue(): Int {
        val integer: Int? = mRecurrenceRule.count
        return if (integer != null) integer else 1
    }

    fun getRule(): String {
        return mRecurrenceRule.toString()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeSerializable(mStartDate)
        dest.writeString(mRecurrenceRule.toString())
    }

    fun getUserReadableString(context: Context?): String {
        val builder = StringBuilder()
        val offset = mRecurrenceRule.interval
        when (mRecurrenceRule.freq!!) {
            Freq.DAILY -> {
                if (offset <= 1) {
                    builder.append(context!!.getString(R.string.recurrence_hint_repeat_every_day))
                } else {
                    builder.append(context!!.getString(R.string.recurrence_hint_repeat_every_n_days, offset))
                }
            }
            Freq.WEEKLY -> {
                if (offset <= 1) {
                    builder.append(context!!.getString(R.string.recurrence_hint_repeat_every_week))
                } else {
                    builder.append(context!!.getString(R.string.recurrence_hint_repeat_every_n_weeks, offset))
                }
                val weekDays = getWeekDays()
                if (weekDays[0]) {builder.append(context.getString(R.string.recurrence_hint_repeat_weekly_sunday))}
                if (weekDays[1]) {builder.append(context.getString(R.string.recurrence_hint_repeat_weekly_monday))}
                if (weekDays[2]) {builder.append(context.getString(R.string.recurrence_hint_repeat_weekly_tuesday))}
                if (weekDays[3]) {builder.append(context.getString(R.string.recurrence_hint_repeat_weekly_wednesday))}
                if (weekDays[4]) {builder.append(context.getString(R.string.recurrence_hint_repeat_weekly_thursday))}
                if (weekDays[5]) {builder.append(context.getString(R.string.recurrence_hint_repeat_weekly_friday))}
                if (weekDays[6]) {builder.append(context.getString(R.string.recurrence_hint_repeat_weekly_saturday))}
            }
            Freq.MONTHLY -> {
                if (offset <= 1) {
                    builder.append(context!!.getString(R.string.recurrence_hint_repeat_every_month))
                } else {
                    builder.append(context!!.getString(R.string.recurrence_hint_repeat_every_n_months, offset))
                }
            }
            Freq.YEARLY -> {
                if (offset <= 1) {
                    builder.append(context!!.getString(R.string.recurrence_hint_repeat_every_year))
                } else {
                    builder.append(context!!.getString(R.string.recurrence_hint_repeat_every_n_years, offset))
                }
            }
            else -> {}
        }
        val formattedStartDate = DateFormatter.getFormattedDate(mStartDate)
        builder.append(context!!.getString(R.string.recurrence_hint_repeat_starting_from, formattedStartDate))
        if (mRecurrenceRule.isInfinite) {
            builder.append(context.getString(R.string.recurrence_hint_repeat_forever))
        } else if (mRecurrenceRule.until != null) {
            val endDate = Date(mRecurrenceRule.until.timestamp)
            val formattedEndDate = DateFormatter.getFormattedDate(endDate)
            builder.append(context.getString(R.string.recurrence_hint_repeat_until, formattedEndDate))
        } else {
            val occurrences: Int = mRecurrenceRule.count
            if (occurrences <= 1) {
                builder.append(context.getString(R.string.recurrence_hint_repeat_for_one_occurrence))
            } else {
                builder.append(context.getString(R.string.recurrence_hint_repeat_for_n_occurrence, occurrences))
            }
        }
        return builder.toString()
    }

    fun getNextOccurrence(lastOccurrence: Date?): Date? {
        val startDateTime = DateUtils.getFixedDateTime(mStartDate)
        val lastOccurrenceDateTime = DateUtils.getFixedDateTime(lastOccurrence)
        var nextOccurrenceDateTime: DateTime? = null
        val iterator = mRecurrenceRule.iterator(startDateTime)
        while (iterator.hasNext()) {
            val nextInstance = iterator.nextDateTime()
            if (nextInstance.after(lastOccurrenceDateTime)) {
                nextOccurrenceDateTime = nextInstance
                break
            }
        }
        return if (nextOccurrenceDateTime != null) DateUtils.getFixedDate(nextOccurrenceDateTime) else null
    }

    /**
     * Immutable result of [computeOccurrences]: the occurrence dates
     * that have come due inside the window, plus the values the LAST_OCCURRENCE and
     * NEXT_OCCURRENCE columns must be updated to afterwards. Every date is already normalised
     * through [DateUtils.getFixedDate], so callers only format and store it.
     */
    class OccurrenceUpdate private constructor(occurrenceDates: List<Date>, lastOccurrence: Date?, nextOccurrence: Date?) {

        private val mOccurrenceDates: List<Date> = occurrenceDates
        private val mLastOccurrence: Date? = lastOccurrence
        private val mNextOccurrence: Date? = nextOccurrence

        /**
         * The occurrence dates, in chronological order, for which a ledger row must be inserted.
         */
        fun getOccurrenceDates(): List<Date> {
            return mOccurrenceDates
        }

        /**
         * The value the LAST_OCCURRENCE column must be set to: the last due occurrence, or the
         * seed itself when nothing came due.
         */
        fun getLastOccurrence(): Date? {
            return mLastOccurrence
        }

        /**
         * The value the NEXT_OCCURRENCE column must be set to: the first instance after the
         * window, or null when the rule is exhausted (or failed to parse).
         */
        fun getNextOccurrence(): Date? {
            return mNextOccurrence
        }

        internal companion object {

            @JvmSynthetic
            internal fun create(occurrenceDates: List<Date>, lastOccurrence: Date?, nextOccurrence: Date?): OccurrenceUpdate {
                return OccurrenceUpdate(occurrenceDates, lastOccurrence, nextOccurrence)
            }
        }
    }

    class Builder(startDate: Date?, type: Int) {

        private var mStartDate: Date? = startDate
        private var mRecurrenceRule: RecurrenceRule = RecurrenceRule(getFreq(type))

        fun setOffset(offset: Int) {
            mRecurrenceRule.interval = offset
        }

        fun setRepeatWeekDay(weekDays: BooleanArray?) {
            val weekdayNumList: MutableList<RecurrenceRule.WeekdayNum> = ArrayList()
            if (weekDays!![0]) {weekdayNumList.add(RecurrenceRule.WeekdayNum(0, Weekday.SU))}
            if (weekDays[1]) {weekdayNumList.add(RecurrenceRule.WeekdayNum(0, Weekday.MO))}
            if (weekDays[2]) {weekdayNumList.add(RecurrenceRule.WeekdayNum(0, Weekday.TU))}
            if (weekDays[3]) {weekdayNumList.add(RecurrenceRule.WeekdayNum(0, Weekday.WE))}
            if (weekDays[4]) {weekdayNumList.add(RecurrenceRule.WeekdayNum(0, Weekday.TH))}
            if (weekDays[5]) {weekdayNumList.add(RecurrenceRule.WeekdayNum(0, Weekday.FR))}
            if (weekDays[6]) {weekdayNumList.add(RecurrenceRule.WeekdayNum(0, Weekday.SA))}
            mRecurrenceRule.setByDayPart(weekdayNumList)
        }

        /**
         * Repeat on the same day of the month as the start date, or on the last day of the month
         * when that month is too short to have it. The last day is offered alongside the day
         * itself and BYSETPOS takes whichever of the two comes first, so a schedule starting on
         * the 31st reads 31 January, 28 February, 31 March. Asking for the day alone leaves a
         * month that does not have it with no occurrence at all: the 31st then skips February,
         * April, June, September and November, which for a budget means one period covering two
         * months. Every day from the 1st to the 28th is unaffected, since the day it names always
         * comes before the last day of the month or is the last day of the month.
         */
        fun setRepeatSameMonthDay() {
            try {
                val calendar = Calendar.getInstance()
                calendar.time = mStartDate
                val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
                if (dayOfMonth > LAST_DAY_EVERY_MONTH_HAS) {
                    mRecurrenceRule.setByPart(RecurrenceRule.Part.BYMONTHDAY, dayOfMonth, -1)
                    mRecurrenceRule.setByPart(RecurrenceRule.Part.BYSETPOS, 1)
                } else {
                    mRecurrenceRule.setByPart(RecurrenceRule.Part.BYMONTHDAY, dayOfMonth)
                }
            } catch (e: InvalidRecurrenceRuleException) {
                throw RuntimeException(e)
            }
        }

        /**
         * Repeat on the same day of the year as the start date, falling back to the last day of
         * that month when the year is too short to have it. Only 29 February needs it, and only
         * that anchor builds a different rule than it built before: asking for it alone skips
         * every year that is not a leap year, so a budget anchored to it covers four years with
         * one year of money.
         */
        fun setRepeatSameYearDay() {
            val calendar = Calendar.getInstance()
            calendar.time = mStartDate
            if (calendar.get(Calendar.MONTH) != Calendar.FEBRUARY
                    || calendar.get(Calendar.DAY_OF_MONTH) <= LAST_DAY_EVERY_MONTH_HAS) {
                return
            }
            try {
                // the library counts months from zero here and writes them from one, so passing
                // Calendar.FEBRUARY is what puts BYMONTH=2 in the rule
                mRecurrenceRule.setByPart(RecurrenceRule.Part.BYMONTH, Calendar.FEBRUARY)
                mRecurrenceRule.setByPart(RecurrenceRule.Part.BYMONTHDAY, calendar.get(Calendar.DAY_OF_MONTH), -1)
                mRecurrenceRule.setByPart(RecurrenceRule.Part.BYSETPOS, 1)
            } catch (e: InvalidRecurrenceRuleException) {
                throw RuntimeException(e)
            }
        }

        fun setEndFor(occurrences: Int) {
            mRecurrenceRule.setCount(occurrences)
        }

        fun setEndUntil(endDate: Date?) {
            mRecurrenceRule.until = DateUtils.getFixedDateTime(endDate)
        }

        fun build(): RecurrenceSetting {
            return RecurrenceSetting(mStartDate, mRecurrenceRule)
        }
    }

    companion object {

        const val TYPE_DAILY = 1
        const val TYPE_WEEKLY = 2
        const val TYPE_MONTHLY = 3
        const val TYPE_YEARLY = 4

        const val FLAG_MONTHLY_SAME_DAY = 1

        /** February is the shortest month, so the 28th is the last day every month is sure to have. */
        private const val LAST_DAY_EVERY_MONTH_HAS = 28

        const val END_FOREVER = 1
        const val END_UNTIL = 2
        const val END_FOR = 3

        @JvmStatic
        @Throws(InvalidRecurrenceRuleException::class)
        fun build(startDate: Date?, rule: String?): RecurrenceSetting {
            val recurrenceRule = RecurrenceRule(rule)
            return RecurrenceSetting(startDate, rule)
        }

        @JvmField
        val CREATOR: Parcelable.Creator<RecurrenceSetting> = object : Parcelable.Creator<RecurrenceSetting> {

            override fun createFromParcel(source: Parcel): RecurrenceSetting {
                return RecurrenceSetting(source)
            }

            override fun newArray(size: Int): Array<RecurrenceSetting?> {
                return arrayOfNulls(size)
            }

        }

        /**
         * Pure, Android-free computation of the recurrence occurrences that have come due, extracted
         * verbatim from the loop that `RecurrenceHandlerIntentService` used to run inline.
         *
         * The rfc5545 iterator is seeded from `seedDate` (the stored next occurrence), which per
         * RFC 5545 is itself the first instance produced. Every instance that is not strictly after
         * `now` (that is, `!instance.after(now)`) is collected as due and becomes the new
         * last occurrence; the first instance strictly after `now` becomes the next occurrence
         * and stops the walk. When the rule exhausts (UNTIL / COUNT) or fails to parse, the next
         * occurrence is null and the last occurrence stays at the seed, exactly as the service behaved.
         *
         * The rule is parsed here from its raw string rather than through a [RecurrenceSetting]
         * instance, so an invalid rule yields no occurrences instead of silently falling back to a
         * daily rule the way the `(Date, String)` constructor does.
         *
         * @param rule     the RFC 5545 recurrence rule string stored on the row
         * @param seedDate the stored next occurrence used to seed the iterator (inclusive first instance)
         * @param now      the moment the window closes at, compared at day granularity
         * @return the due occurrence dates and the updated last / next occurrence values
         */
        @JvmStatic
        fun computeOccurrences(rule: String?, seedDate: Date?, now: Date?): OccurrenceUpdate {
            val occurrenceDates: MutableList<Date> = ArrayList()
            val currentDateTime = DateUtils.getFixedDateTime(now)
            val startDateTime = DateUtils.getFixedDateTime(seedDate)
            var lastOccurrence = DateUtils.getFixedDateTime(seedDate)
            var nextOccurrence: DateTime? = null
            try {
                val recurrenceRule = RecurrenceRule(rule)
                val iterator = recurrenceRule.iterator(startDateTime)
                while (iterator.hasNext()) {
                    val nextInstance = iterator.nextDateTime()
                    if (!nextInstance.after(currentDateTime)) {
                        occurrenceDates.add(DateUtils.getFixedDate(nextInstance))
                        lastOccurrence = nextInstance
                    } else {
                        nextOccurrence = nextInstance
                        break
                    }
                }
            } catch (ignore: InvalidRecurrenceRuleException) {
                // leave nextOccurrence null so the row is not processed again, matching the old service
            }
            return OccurrenceUpdate.create(
                    occurrenceDates,
                    DateUtils.getFixedDate(lastOccurrence),
                    if (nextOccurrence != null) DateUtils.getFixedDate(nextOccurrence) else null
            )
        }

        /**
         * Where an edited recurrence resumes, as an SQL date string, or null when the rule it now
         * carries is exhausted. Every date argument is an SQL date string as stored on the row.
         *
         * When neither the rule nor the start date moved, the stored pointer is handed back
         * untouched. An edit to an amount, a description or a category cannot then step over
         * occurrences that have come due and have not yet been written to the ledger, and that
         * matters here because the recurrence service only ever walks forward from this pointer, so
         * an occurrence it has been moved past is never written.
         *
         * When the schedule itself moved, the pointer becomes the first instance of the new rule
         * after `now`, seeded from the new start date so the rule keeps the anchor its own
         * fields are relative to. Occurrences the old rule left owed are not carried across, since
         * the new rule need not contain them at all.
         *
         * @param oldRule           rule currently stored on the row.
         * @param oldStartDate      start date currently stored on the row.
         * @param oldNextOccurrence pointer currently stored on the row, possibly already past.
         * @param newRule           rule the editor is saving.
         * @param newStartDate      start date the editor is saving.
         * @param now               the moment the edit is being saved at.
         * @return the value the NEXT_OCCURRENCE column must be set to.
         */
        @JvmStatic
        fun resumeAfterEdit(oldRule: String?, oldStartDate: String?, oldNextOccurrence: String?,
                            newRule: String?, newStartDate: String?, now: Date?): String? {
            if (Objects.equals(oldRule, newRule) && Objects.equals(oldStartDate, newStartDate)) {
                return oldNextOccurrence
            }
            val startDate = DateUtils.getDateFromSQLDateString(newStartDate)
            val nextOccurrence = computeOccurrences(newRule, startDate, now).getNextOccurrence()
            return if (nextOccurrence != null) DateUtils.getSQLDateString(nextOccurrence) else null
        }

        /**
         * The first instance of `rule` strictly after `after`, counted from
         * `anchor`.
         *
         * The anchor is where the schedule was set to begin, and it has to be the seed: a rule only
         * carries the day it repeats on when that day is written into the rule itself, which is true
         * of a monthly rule and of nothing else. Seeding a weekly rule at the day a budget's current
         * period happens to start would repeat it on that weekday, and seeding one that repeats every
         * second month would put it on the wrong months, whatever the schedule says it does.
         *
         * @param rule   the RFC 5545 recurrence rule string stored on the row.
         * @param anchor the day the schedule is anchored to.
         * @param after  the day to find the first instance beyond.
         * @return the next instance, or null when the rule is exhausted or fails to parse.
         */
        @JvmStatic
        fun nextInstanceAfter(rule: String?, anchor: Date?, after: Date?): Date? {
            val afterDateTime = DateUtils.getFixedDateTime(after)
            try {
                val iterator = RecurrenceRule(rule).iterator(DateUtils.getFixedDateTime(anchor))
                while (iterator.hasNext()) {
                    val instance = iterator.nextDateTime()
                    if (instance.after(afterDateTime)) {
                        return DateUtils.getFixedDate(instance)
                    }
                }
            } catch (ignore: InvalidRecurrenceRuleException) {
                // an unparseable rule has no further instance, so the caller stops repeating
            }
            return null
        }

        /**
         * The last day covered by a period that begins on `start`: the day before the next
         * instance of `rule` counted from `anchor`. A monthly schedule anchored to the
         * 15th therefore runs the 15th to the 14th, which is what makes the period follow the
         * schedule instead of a fixed day count.
         *
         * @param rule   the RFC 5545 recurrence rule string stored on the row.
         * @param anchor the day the schedule is anchored to.
         * @param start  the first day of the period.
         * @return the last day of the period, or null when the rule produces no further instance.
         */
        @JvmStatic
        fun periodEnd(rule: String?, anchor: Date?, start: Date?): Date? {
            val next = nextInstanceAfter(rule, anchor, start)
            return if (next != null) DateUtils.addDays(DateUtils.getCalendar(next), -1) else null
        }

        /**
         * The first period of `rule` whose last day is not already behind `now`, as the
         * pair {start, end}. A schedule anchored in the past therefore lands on the period it is in
         * today instead of on the one it began with, which is what stops an editor writing a period
         * that finished long ago and leaving the roll forward to open every period since.
         *
         * @param rule  the RFC 5545 recurrence rule string.
         * @param start the day the schedule is anchored to.
         * @param now   the day to land on.
         * @return the pair {start, end}, or null when the rule produces no period at all.
         */
        @JvmStatic
        fun periodOn(rule: String?, start: Date?, now: Date?): Array<Date>? {
            val nowDateTime = DateUtils.getFixedDateTime(now)
            try {
                val iterator = RecurrenceRule(rule).iterator(DateUtils.getFixedDateTime(start))
                if (!iterator.hasNext()) {
                    return null
                }
                var periodStart = iterator.nextDateTime()
                while (iterator.hasNext()) {
                    val nextStart = iterator.nextDateTime()
                    if (nextStart.after(nowDateTime)) {
                        val end = DateUtils.addDays(DateUtils.getCalendar(DateUtils.getFixedDate(nextStart)), -1)
                        return arrayOf(DateUtils.getFixedDate(periodStart), end)
                    }
                    periodStart = nextStart
                }
            } catch (ignore: InvalidRecurrenceRuleException) {
                // a rule that does not parse has no period, and the caller refuses it
            }
            return null
        }

        private fun getFreq(type: Int): Freq {
            when (type) {
                TYPE_DAILY ->
                    return Freq.DAILY
                TYPE_WEEKLY ->
                    return Freq.WEEKLY
                TYPE_MONTHLY ->
                    return Freq.MONTHLY
                TYPE_YEARLY ->
                    return Freq.YEARLY
            }
            return Freq.DAILY
        }
    }
}
