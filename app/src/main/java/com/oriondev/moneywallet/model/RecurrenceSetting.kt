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

package com.oriondev.moneywallet.model;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.utils.DateFormatter;
import com.oriondev.moneywallet.utils.DateUtils;

import org.dmfs.rfc5545.DateTime;
import org.dmfs.rfc5545.Weekday;
import org.dmfs.rfc5545.recur.Freq;
import org.dmfs.rfc5545.recur.InvalidRecurrenceRuleException;
import org.dmfs.rfc5545.recur.RecurrenceRule;
import org.dmfs.rfc5545.recur.RecurrenceRuleIterator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Created by andrea on 07/11/18.
 */
public class RecurrenceSetting implements Parcelable {

    public static final int TYPE_DAILY = 1;
    public static final int TYPE_WEEKLY = 2;
    public static final int TYPE_MONTHLY = 3;
    public static final int TYPE_YEARLY = 4;

    public static final int FLAG_MONTHLY_SAME_DAY = 1;

    /** February is the shortest month, so the 28th is the last day every month is sure to have. */
    private static final int LAST_DAY_EVERY_MONTH_HAS = 28;

    public static final int END_FOREVER = 1;
    public static final int END_UNTIL = 2;
    public static final int END_FOR = 3;

    private Date mStartDate;
    private RecurrenceRule mRecurrenceRule;

    public RecurrenceSetting(Date startDate, int type) {
        mStartDate = startDate;
        mRecurrenceRule = new RecurrenceRule(getFreq(type));
    }

    public RecurrenceSetting(Date startDate, String rule) {
        mStartDate = startDate;
        try {
            mRecurrenceRule = new RecurrenceRule(rule);
        } catch (InvalidRecurrenceRuleException ignore) {
            mRecurrenceRule = new RecurrenceRule(Freq.DAILY);
        }
    }

    public static RecurrenceSetting build(Date startDate, String rule) throws InvalidRecurrenceRuleException {
        RecurrenceRule recurrenceRule = new RecurrenceRule(rule);
        return new RecurrenceSetting(startDate, rule);
    }

    private RecurrenceSetting(Date startDate, RecurrenceRule recurrenceRule) {
        mStartDate = startDate;
        mRecurrenceRule = recurrenceRule;
    }

    private RecurrenceSetting(Parcel in) {
        try {
            mStartDate = (Date) in.readSerializable();
            mRecurrenceRule = new RecurrenceRule(in.readString());
        } catch (InvalidRecurrenceRuleException e) {
            throw new RuntimeException(e);
        }
    }

    public int getType() {
        switch (mRecurrenceRule.getFreq()) {
            case DAILY:
                return TYPE_DAILY;
            case WEEKLY:
                return TYPE_WEEKLY;
            case MONTHLY:
                return TYPE_MONTHLY;
            case YEARLY:
                return TYPE_YEARLY;
            default:
                return TYPE_DAILY;
        }
    }

    public Date getStartDate() {
        return mStartDate;
    }

    public int getOffsetValue() {
        return mRecurrenceRule.getInterval();
    }

    public boolean[] getWeekDays() {
        boolean[] weekDays = new boolean[] {false, false, false, false, false, false, false};
        List<RecurrenceRule.WeekdayNum> weekdayNumList = mRecurrenceRule.getByDayPart();
        if (weekdayNumList != null && weekdayNumList.size() > 0) {
            for (RecurrenceRule.WeekdayNum weekdayNum : weekdayNumList) {
                switch (weekdayNum.weekday) {
                    case SU:
                        weekDays[0] = true;
                        break;
                    case MO:
                        weekDays[1] = true;
                        break;
                    case TU:
                        weekDays[2] = true;
                        break;
                    case WE:
                        weekDays[3] = true;
                        break;
                    case TH:
                        weekDays[4] = true;
                        break;
                    case FR:
                        weekDays[5] = true;
                        break;
                    case SA:
                        weekDays[6] = true;
                        break;
                }
            }
        } else {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(mStartDate);
            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
            weekDays[dayOfWeek - 1] = true;
        }
        return weekDays;
    }

    public int getMonthDay() {
        return FLAG_MONTHLY_SAME_DAY;
    }

    public int getEndType() {
        if (mRecurrenceRule.isInfinite()) {
            return END_FOREVER;
        } else if (mRecurrenceRule.getUntil() != null) {
            return END_UNTIL;
        } else {
            return END_FOR;
        }
    }

    public Date getEndDate() {
        DateTime dateTime = mRecurrenceRule.getUntil();
        return dateTime != null ? new Date(dateTime.getTimestamp()) : mStartDate;
    }

    public int getOccurrenceValue() {
        Integer integer = mRecurrenceRule.getCount();
        return integer != null ? integer : 1;
    }

    public String getRule() {
        return mRecurrenceRule.toString();
    }

    public static final Creator<RecurrenceSetting> CREATOR = new Creator<RecurrenceSetting>() {

        @Override
        public RecurrenceSetting createFromParcel(Parcel in) {
            return new RecurrenceSetting(in);
        }

        @Override
        public RecurrenceSetting[] newArray(int size) {
            return new RecurrenceSetting[size];
        }

    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeSerializable(mStartDate);
        dest.writeString(mRecurrenceRule.toString());
    }

    public String getUserReadableString(Context context) {
        StringBuilder builder = new StringBuilder();
        int offset = mRecurrenceRule.getInterval();
        switch (mRecurrenceRule.getFreq()) {
            case DAILY:
                if (offset <= 1) {
                    builder.append(context.getString(R.string.recurrence_hint_repeat_every_day));
                } else {
                    builder.append(context.getString(R.string.recurrence_hint_repeat_every_n_days, offset));
                }
                break;
            case WEEKLY:
                if (offset <= 1) {
                    builder.append(context.getString(R.string.recurrence_hint_repeat_every_week));
                } else {
                    builder.append(context.getString(R.string.recurrence_hint_repeat_every_n_weeks, offset));
                }
                boolean[] weekDays = getWeekDays();
                if (weekDays[0]) {builder.append(context.getString(R.string.recurrence_hint_repeat_weekly_sunday));}
                if (weekDays[1]) {builder.append(context.getString(R.string.recurrence_hint_repeat_weekly_monday));}
                if (weekDays[2]) {builder.append(context.getString(R.string.recurrence_hint_repeat_weekly_tuesday));}
                if (weekDays[3]) {builder.append(context.getString(R.string.recurrence_hint_repeat_weekly_wednesday));}
                if (weekDays[4]) {builder.append(context.getString(R.string.recurrence_hint_repeat_weekly_thursday));}
                if (weekDays[5]) {builder.append(context.getString(R.string.recurrence_hint_repeat_weekly_friday));}
                if (weekDays[6]) {builder.append(context.getString(R.string.recurrence_hint_repeat_weekly_saturday));}
                break;
            case MONTHLY:
                if (offset <= 1) {
                    builder.append(context.getString(R.string.recurrence_hint_repeat_every_month));
                } else {
                    builder.append(context.getString(R.string.recurrence_hint_repeat_every_n_months, offset));
                }
                break;
            case YEARLY:
                if (offset <= 1) {
                    builder.append(context.getString(R.string.recurrence_hint_repeat_every_year));
                } else {
                    builder.append(context.getString(R.string.recurrence_hint_repeat_every_n_years, offset));
                }
                break;
        }
        String formattedStartDate = DateFormatter.getFormattedDate(mStartDate);
        builder.append(context.getString(R.string.recurrence_hint_repeat_starting_from, formattedStartDate));
        if (mRecurrenceRule.isInfinite()) {
            builder.append(context.getString(R.string.recurrence_hint_repeat_forever));
        } else if (mRecurrenceRule.getUntil() != null) {
            Date endDate = new Date(mRecurrenceRule.getUntil().getTimestamp());
            String formattedEndDate = DateFormatter.getFormattedDate(endDate);
            builder.append(context.getString(R.string.recurrence_hint_repeat_until, formattedEndDate));
        } else {
            int occurrences = mRecurrenceRule.getCount();
            if (occurrences <= 1) {
                builder.append(context.getString(R.string.recurrence_hint_repeat_for_one_occurrence));
            } else {
                builder.append(context.getString(R.string.recurrence_hint_repeat_for_n_occurrence, occurrences));
            }
        }
        return builder.toString();
    }

    public Date getNextOccurrence(Date lastOccurrence) {
        DateTime startDateTime = DateUtils.getFixedDateTime(mStartDate);
        DateTime lastOccurrenceDateTime = DateUtils.getFixedDateTime(lastOccurrence);
        DateTime nextOccurrenceDateTime = null;
        RecurrenceRuleIterator iterator = mRecurrenceRule.iterator(startDateTime);
        while (iterator.hasNext()) {
            DateTime nextInstance = iterator.nextDateTime();
            if (nextInstance.after(lastOccurrenceDateTime)) {
                nextOccurrenceDateTime = nextInstance;
                break;
            }
        }
        return nextOccurrenceDateTime != null ? DateUtils.getFixedDate(nextOccurrenceDateTime) : null;
    }

    /**
     * Immutable result of {@link #computeOccurrences(String, Date, Date)}: the occurrence dates
     * that have come due inside the window, plus the values the LAST_OCCURRENCE and
     * NEXT_OCCURRENCE columns must be updated to afterwards. Every date is already normalised
     * through {@link DateUtils#getFixedDate(DateTime)}, so callers only format and store it.
     */
    public static class OccurrenceUpdate {

        private final List<Date> mOccurrenceDates;
        private final Date mLastOccurrence;
        private final Date mNextOccurrence;

        private OccurrenceUpdate(List<Date> occurrenceDates, Date lastOccurrence, Date nextOccurrence) {
            mOccurrenceDates = occurrenceDates;
            mLastOccurrence = lastOccurrence;
            mNextOccurrence = nextOccurrence;
        }

        /**
         * The occurrence dates, in chronological order, for which a ledger row must be inserted.
         */
        public List<Date> getOccurrenceDates() {
            return mOccurrenceDates;
        }

        /**
         * The value the LAST_OCCURRENCE column must be set to: the last due occurrence, or the
         * seed itself when nothing came due.
         */
        public Date getLastOccurrence() {
            return mLastOccurrence;
        }

        /**
         * The value the NEXT_OCCURRENCE column must be set to: the first instance after the
         * window, or null when the rule is exhausted (or failed to parse).
         */
        public Date getNextOccurrence() {
            return mNextOccurrence;
        }
    }

    /**
     * Pure, Android-free computation of the recurrence occurrences that have come due, extracted
     * verbatim from the loop that {@code RecurrenceHandlerIntentService} used to run inline.
     *
     * The rfc5545 iterator is seeded from {@code seedDate} (the stored next occurrence), which per
     * RFC 5545 is itself the first instance produced. Every instance that is not strictly after
     * {@code now} (that is, {@code !instance.after(now)}) is collected as due and becomes the new
     * last occurrence; the first instance strictly after {@code now} becomes the next occurrence
     * and stops the walk. When the rule exhausts (UNTIL / COUNT) or fails to parse, the next
     * occurrence is null and the last occurrence stays at the seed, exactly as the service behaved.
     *
     * The rule is parsed here from its raw string rather than through a {@link RecurrenceSetting}
     * instance, so an invalid rule yields no occurrences instead of silently falling back to a
     * daily rule the way the {@code (Date, String)} constructor does.
     *
     * @param rule     the RFC 5545 recurrence rule string stored on the row
     * @param seedDate the stored next occurrence used to seed the iterator (inclusive first instance)
     * @param now      the moment the window closes at, compared at day granularity
     * @return the due occurrence dates and the updated last / next occurrence values
     */
    public static OccurrenceUpdate computeOccurrences(String rule, Date seedDate, Date now) {
        List<Date> occurrenceDates = new ArrayList<>();
        DateTime currentDateTime = DateUtils.getFixedDateTime(now);
        DateTime startDateTime = DateUtils.getFixedDateTime(seedDate);
        DateTime lastOccurrence = DateUtils.getFixedDateTime(seedDate);
        DateTime nextOccurrence = null;
        try {
            RecurrenceRule recurrenceRule = new RecurrenceRule(rule);
            RecurrenceRuleIterator iterator = recurrenceRule.iterator(startDateTime);
            while (iterator.hasNext()) {
                DateTime nextInstance = iterator.nextDateTime();
                if (!nextInstance.after(currentDateTime)) {
                    occurrenceDates.add(DateUtils.getFixedDate(nextInstance));
                    lastOccurrence = nextInstance;
                } else {
                    nextOccurrence = nextInstance;
                    break;
                }
            }
        } catch (InvalidRecurrenceRuleException ignore) {
            // leave nextOccurrence null so the row is not processed again, matching the old service
        }
        return new OccurrenceUpdate(
                occurrenceDates,
                DateUtils.getFixedDate(lastOccurrence),
                nextOccurrence != null ? DateUtils.getFixedDate(nextOccurrence) : null
        );
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
     * after {@code now}, seeded from the new start date so the rule keeps the anchor its own
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
    public static String resumeAfterEdit(String oldRule, String oldStartDate, String oldNextOccurrence,
                                         String newRule, String newStartDate, Date now) {
        if (Objects.equals(oldRule, newRule) && Objects.equals(oldStartDate, newStartDate)) {
            return oldNextOccurrence;
        }
        Date startDate = DateUtils.getDateFromSQLDateString(newStartDate);
        Date nextOccurrence = computeOccurrences(newRule, startDate, now).getNextOccurrence();
        return nextOccurrence != null ? DateUtils.getSQLDateString(nextOccurrence) : null;
    }

    /**
     * The first instance of {@code rule} strictly after {@code after}, counted from
     * {@code anchor}.
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
    public static Date nextInstanceAfter(String rule, Date anchor, Date after) {
        DateTime afterDateTime = DateUtils.getFixedDateTime(after);
        try {
            RecurrenceRuleIterator iterator = new RecurrenceRule(rule).iterator(DateUtils.getFixedDateTime(anchor));
            while (iterator.hasNext()) {
                DateTime instance = iterator.nextDateTime();
                if (instance.after(afterDateTime)) {
                    return DateUtils.getFixedDate(instance);
                }
            }
        } catch (InvalidRecurrenceRuleException ignore) {
            // an unparseable rule has no further instance, so the caller stops repeating
        }
        return null;
    }

    /**
     * The last day covered by a period that begins on {@code start}: the day before the next
     * instance of {@code rule} counted from {@code anchor}. A monthly schedule anchored to the
     * 15th therefore runs the 15th to the 14th, which is what makes the period follow the
     * schedule instead of a fixed day count.
     *
     * @param rule   the RFC 5545 recurrence rule string stored on the row.
     * @param anchor the day the schedule is anchored to.
     * @param start  the first day of the period.
     * @return the last day of the period, or null when the rule produces no further instance.
     */
    public static Date periodEnd(String rule, Date anchor, Date start) {
        Date next = nextInstanceAfter(rule, anchor, start);
        return next != null ? DateUtils.addDays(DateUtils.getCalendar(next), -1) : null;
    }

    /**
     * The first period of {@code rule} whose last day is not already behind {@code now}, as the
     * pair {start, end}. A schedule anchored in the past therefore lands on the period it is in
     * today instead of on the one it began with, which is what stops an editor writing a period
     * that finished long ago and leaving the roll forward to open every period since.
     *
     * @param rule  the RFC 5545 recurrence rule string.
     * @param start the day the schedule is anchored to.
     * @param now   the day to land on.
     * @return the pair {start, end}, or null when the rule produces no period at all.
     */
    public static Date[] periodOn(String rule, Date start, Date now) {
        DateTime nowDateTime = DateUtils.getFixedDateTime(now);
        try {
            RecurrenceRuleIterator iterator = new RecurrenceRule(rule).iterator(DateUtils.getFixedDateTime(start));
            if (!iterator.hasNext()) {
                return null;
            }
            DateTime periodStart = iterator.nextDateTime();
            while (iterator.hasNext()) {
                DateTime nextStart = iterator.nextDateTime();
                if (nextStart.after(nowDateTime)) {
                    Date end = DateUtils.addDays(DateUtils.getCalendar(DateUtils.getFixedDate(nextStart)), -1);
                    return new Date[] {DateUtils.getFixedDate(periodStart), end};
                }
                periodStart = nextStart;
            }
        } catch (InvalidRecurrenceRuleException ignore) {
            // a rule that does not parse has no period, and the caller refuses it
        }
        return null;
    }

    public static class Builder {

        private Date mStartDate;
        private RecurrenceRule mRecurrenceRule;

        public Builder(Date startDate, int type) {
            mStartDate = startDate;
            mRecurrenceRule = new RecurrenceRule(getFreq(type));
        }

        public void setOffset(int offset) {
            mRecurrenceRule.setInterval(offset);
        }

        public void setRepeatWeekDay(boolean[] weekDays) {
            List<RecurrenceRule.WeekdayNum> weekdayNumList = new ArrayList<>();
            if (weekDays[0]) {weekdayNumList.add(new RecurrenceRule.WeekdayNum(0, Weekday.SU));}
            if (weekDays[1]) {weekdayNumList.add(new RecurrenceRule.WeekdayNum(0, Weekday.MO));}
            if (weekDays[2]) {weekdayNumList.add(new RecurrenceRule.WeekdayNum(0, Weekday.TU));}
            if (weekDays[3]) {weekdayNumList.add(new RecurrenceRule.WeekdayNum(0, Weekday.WE));}
            if (weekDays[4]) {weekdayNumList.add(new RecurrenceRule.WeekdayNum(0, Weekday.TH));}
            if (weekDays[5]) {weekdayNumList.add(new RecurrenceRule.WeekdayNum(0, Weekday.FR));}
            if (weekDays[6]) {weekdayNumList.add(new RecurrenceRule.WeekdayNum(0, Weekday.SA));}
            mRecurrenceRule.setByDayPart(weekdayNumList);
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
        public void setRepeatSameMonthDay() {
            try {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(mStartDate);
                int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
                if (dayOfMonth > LAST_DAY_EVERY_MONTH_HAS) {
                    mRecurrenceRule.setByPart(RecurrenceRule.Part.BYMONTHDAY, dayOfMonth, -1);
                    mRecurrenceRule.setByPart(RecurrenceRule.Part.BYSETPOS, 1);
                } else {
                    mRecurrenceRule.setByPart(RecurrenceRule.Part.BYMONTHDAY, dayOfMonth);
                }
            } catch (InvalidRecurrenceRuleException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Repeat on the same day of the year as the start date, falling back to the last day of
         * that month when the year is too short to have it. Only 29 February needs it, and only
         * that anchor builds a different rule than it built before: asking for it alone skips
         * every year that is not a leap year, so a budget anchored to it covers four years with
         * one year of money.
         */
        public void setRepeatSameYearDay() {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(mStartDate);
            if (calendar.get(Calendar.MONTH) != Calendar.FEBRUARY
                    || calendar.get(Calendar.DAY_OF_MONTH) <= LAST_DAY_EVERY_MONTH_HAS) {
                return;
            }
            try {
                // the library counts months from zero here and writes them from one, so passing
                // Calendar.FEBRUARY is what puts BYMONTH=2 in the rule
                mRecurrenceRule.setByPart(RecurrenceRule.Part.BYMONTH, Calendar.FEBRUARY);
                mRecurrenceRule.setByPart(RecurrenceRule.Part.BYMONTHDAY, calendar.get(Calendar.DAY_OF_MONTH), -1);
                mRecurrenceRule.setByPart(RecurrenceRule.Part.BYSETPOS, 1);
            } catch (InvalidRecurrenceRuleException e) {
                throw new RuntimeException(e);
            }
        }

        public void setEndFor(int occurrences) {
            mRecurrenceRule.setCount(occurrences);
        }

        public void setEndUntil(Date endDate) {
            mRecurrenceRule.setUntil(DateUtils.getFixedDateTime(endDate));
        }

        public RecurrenceSetting build() {
            return new RecurrenceSetting(mStartDate, mRecurrenceRule);
        }
    }

    private static Freq getFreq(int type) {
        switch (type) {
            case TYPE_DAILY:
                return Freq.DAILY;
            case TYPE_WEEKLY:
                return Freq.WEEKLY;
            case TYPE_MONTHLY:
                return Freq.MONTHLY;
            case TYPE_YEARLY:
                return Freq.YEARLY;
        }
        return Freq.DAILY;
    }
}