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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.oriondev.moneywallet.utils.DateUtils;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Host-side coverage for the pure occurrence computation extracted from
 * {@code RecurrenceHandlerIntentService}. {@link RecurrenceSetting#computeOccurrences(String, Date, Date)}
 * carries the exact rule that decides when a recurring transaction or transfer silently enters the
 * ledger, so every semantic the service relied on is pinned here: the seed is the inclusive first
 * instance, the window boundary is {@code !instance.after(now)}, an exhausted or invalid rule yields
 * a null next occurrence, and month-end rules skip absent days without crashing.
 */
public class RecurrenceSettingTest {

    // ---------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------

    /** Builds a Date at the given calendar day (month is 0-based, as everywhere in this codebase). */
    private static Date day(int year, int month, int dayOfMonth) {
        return DateUtils.getDate(year, month, dayOfMonth);
    }

    /** Asserts a computed Date falls on the expected calendar day, ignoring time-of-day. */
    private static void assertDay(Date date, int year, int month, int dayOfMonth) {
        assertNotNull(date);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        assertEquals(year, calendar.get(Calendar.YEAR));
        assertEquals(month, calendar.get(Calendar.MONTH));
        assertEquals(dayOfMonth, calendar.get(Calendar.DAY_OF_MONTH));
    }

    // ---------------------------------------------------------------------------------------------
    // instantiability
    // ---------------------------------------------------------------------------------------------

    /**
     * The model class carries android.os.Parcelable plumbing, so first prove it loads and works in a
     * plain JVM test: construct it and exercise the legacy getNextOccurrence the importer depends on.
     */
    @Test
    public void recurrenceSettingIsInstantiableInPlainJvm() {
        RecurrenceSetting setting = new RecurrenceSetting(day(2020, 0, 1), "FREQ=DAILY");
        assertNotNull(setting.getRule());
        Date next = setting.getNextOccurrence(day(2020, 0, 1));
        assertDay(next, 2020, 0, 2);
    }

    // ---------------------------------------------------------------------------------------------
    // daily / weekly / monthly advancement between a seed and a now
    // ---------------------------------------------------------------------------------------------

    @Test
    public void dailyAdvancementCollectsEveryDayUpToNow() {
        RecurrenceSetting.OccurrenceUpdate update = RecurrenceSetting.computeOccurrences(
                "FREQ=DAILY", day(2020, 0, 1), day(2020, 0, 5));
        List<Date> dates = update.getOccurrenceDates();
        assertEquals(5, dates.size());
        assertDay(dates.get(0), 2020, 0, 1);
        assertDay(dates.get(1), 2020, 0, 2);
        assertDay(dates.get(2), 2020, 0, 3);
        assertDay(dates.get(3), 2020, 0, 4);
        assertDay(dates.get(4), 2020, 0, 5);
        assertDay(update.getLastOccurrence(), 2020, 0, 5);
        assertDay(update.getNextOccurrence(), 2020, 0, 6);
    }

    @Test
    public void weeklyAdvancementSteppsSevenDays() {
        RecurrenceSetting.OccurrenceUpdate update = RecurrenceSetting.computeOccurrences(
                "FREQ=WEEKLY", day(2020, 0, 1), day(2020, 0, 20));
        List<Date> dates = update.getOccurrenceDates();
        assertEquals(3, dates.size());
        assertDay(dates.get(0), 2020, 0, 1);
        assertDay(dates.get(1), 2020, 0, 8);
        assertDay(dates.get(2), 2020, 0, 15);
        assertDay(update.getLastOccurrence(), 2020, 0, 15);
        assertDay(update.getNextOccurrence(), 2020, 0, 22);
    }

    @Test
    public void monthlyAdvancementSteppsOneMonth() {
        RecurrenceSetting.OccurrenceUpdate update = RecurrenceSetting.computeOccurrences(
                "FREQ=MONTHLY", day(2020, 0, 15), day(2020, 3, 20));
        List<Date> dates = update.getOccurrenceDates();
        assertEquals(4, dates.size());
        assertDay(dates.get(0), 2020, 0, 15);
        assertDay(dates.get(1), 2020, 1, 15);
        assertDay(dates.get(2), 2020, 2, 15);
        assertDay(dates.get(3), 2020, 3, 15);
        assertDay(update.getLastOccurrence(), 2020, 3, 15);
        assertDay(update.getNextOccurrence(), 2020, 4, 15);
    }

    // ---------------------------------------------------------------------------------------------
    // boundary exactness: an occurrence exactly at "now" is included, the day after is not
    // ---------------------------------------------------------------------------------------------

    @Test
    public void occurrenceExactlyAtNowIsIncludedNextDayIsNext() {
        RecurrenceSetting.OccurrenceUpdate update = RecurrenceSetting.computeOccurrences(
                "FREQ=DAILY", day(2020, 5, 10), day(2020, 5, 10));
        List<Date> dates = update.getOccurrenceDates();
        // seed == now: !instance.after(now) is true, so the seed day is due
        assertEquals(1, dates.size());
        assertDay(dates.get(0), 2020, 5, 10);
        assertDay(update.getLastOccurrence(), 2020, 5, 10);
        // the first instance strictly after now stops the walk and becomes the next occurrence
        assertDay(update.getNextOccurrence(), 2020, 5, 11);
    }

    // ---------------------------------------------------------------------------------------------
    // month-end rule advances as rfc5545 dictates without skipping or crashing
    // ---------------------------------------------------------------------------------------------

    @Test
    public void monthEndRuleSkipsShortMonthsWithoutCrashing() {
        // BYMONTHDAY=31 has no instance in Feb/Apr/Jun; org.dmfs simply omits those months
        RecurrenceSetting.OccurrenceUpdate update = RecurrenceSetting.computeOccurrences(
                "FREQ=MONTHLY;BYMONTHDAY=31", day(2020, 0, 31), day(2020, 6, 15));
        List<Date> dates = update.getOccurrenceDates();
        assertEquals(3, dates.size());
        assertDay(dates.get(0), 2020, 0, 31); // Jan 31
        assertDay(dates.get(1), 2020, 2, 31); // Mar 31 (Feb skipped)
        assertDay(dates.get(2), 2020, 4, 31); // May 31 (Apr skipped)
        assertDay(update.getLastOccurrence(), 2020, 4, 31);
        assertDay(update.getNextOccurrence(), 2020, 6, 31); // Jul 31 (Jun skipped)
    }

    // ---------------------------------------------------------------------------------------------
    // a rule with an end (COUNT / UNTIL) that exhausts inside the window -> null next occurrence
    // ---------------------------------------------------------------------------------------------

    @Test
    public void countRuleExhaustingInsideWindowLeavesNoNextOccurrence() {
        RecurrenceSetting.OccurrenceUpdate update = RecurrenceSetting.computeOccurrences(
                "FREQ=DAILY;COUNT=3", day(2020, 1, 1), day(2020, 1, 28));
        List<Date> dates = update.getOccurrenceDates();
        assertEquals(3, dates.size());
        assertDay(dates.get(0), 2020, 1, 1);
        assertDay(dates.get(1), 2020, 1, 2);
        assertDay(dates.get(2), 2020, 1, 3);
        assertDay(update.getLastOccurrence(), 2020, 1, 3);
        assertNull(update.getNextOccurrence());
    }

    @Test
    public void untilRuleExhaustingInsideWindowLeavesNoNextOccurrence() {
        // UNTIL is inclusive per RFC 5545; the Jan 22 instance is kept, then the rule is done
        RecurrenceSetting.OccurrenceUpdate update = RecurrenceSetting.computeOccurrences(
                "FREQ=WEEKLY;UNTIL=20200122", day(2020, 0, 1), day(2020, 11, 31));
        List<Date> dates = update.getOccurrenceDates();
        assertEquals(4, dates.size());
        assertDay(dates.get(0), 2020, 0, 1);
        assertDay(dates.get(1), 2020, 0, 8);
        assertDay(dates.get(2), 2020, 0, 15);
        assertDay(dates.get(3), 2020, 0, 22);
        assertDay(update.getLastOccurrence(), 2020, 0, 22);
        assertNull(update.getNextOccurrence());
    }

    // ---------------------------------------------------------------------------------------------
    // a window with nothing due returns empty and leaves the seed as the (unchanged) next occurrence
    // ---------------------------------------------------------------------------------------------

    @Test
    public void seedInTheFutureYieldsNoOccurrencesAndKeepsSeedAsNext() {
        RecurrenceSetting.OccurrenceUpdate update = RecurrenceSetting.computeOccurrences(
                "FREQ=MONTHLY", day(2020, 4, 10), day(2020, 4, 1));
        assertTrue(update.getOccurrenceDates().isEmpty());
        // nothing was inserted, so last occurrence stays at the seed and next stays at the seed too
        assertDay(update.getLastOccurrence(), 2020, 4, 10);
        assertDay(update.getNextOccurrence(), 2020, 4, 10);
    }

    // ---------------------------------------------------------------------------------------------
    // an unparseable rule yields no occurrences and a null next occurrence (no daily fallback)
    // ---------------------------------------------------------------------------------------------

    @Test
    public void invalidRuleYieldsNoOccurrencesAndNullNext() {
        RecurrenceSetting.OccurrenceUpdate update = RecurrenceSetting.computeOccurrences(
                "this is not a rule", day(2020, 2, 3), day(2020, 2, 30));
        assertTrue(update.getOccurrenceDates().isEmpty());
        assertDay(update.getLastOccurrence(), 2020, 2, 3);
        assertNull(update.getNextOccurrence());
    }

    // ---------------------------------------------------------------------------------------------
    // editing a saved recurrence. resumeAfterEdit owns the whole decision, so these fail if the
    // editor path in SQLDatabase is reverted to writing the pointer unconditionally.
    // ---------------------------------------------------------------------------------------------

    /** The service only walks forward from the pointer, so an unrelated edit must not move it. */
    @Test
    public void anEditThatLeavesTheScheduleAloneKeepsAnAlreadyOwedPointer() {
        // the service is behind: the stored pointer is a month in the past and nothing has emitted it
        String owed = sql(day(2020, 1, 10));
        String resumed = RecurrenceSetting.resumeAfterEdit(
                "FREQ=MONTHLY", sql(day(2020, 0, 10)), owed,
                "FREQ=MONTHLY", sql(day(2020, 0, 10)), day(2020, 2, 5));
        assertEquals(owed, resumed);
    }

    @Test
    public void anEditThatLeavesTheScheduleAloneKeepsAFuturePointerToo() {
        String pointer = sql(day(2020, 3, 10));
        String resumed = RecurrenceSetting.resumeAfterEdit(
                "FREQ=MONTHLY", sql(day(2020, 0, 10)), pointer,
                "FREQ=MONTHLY", sql(day(2020, 0, 10)), day(2020, 2, 5));
        assertEquals(pointer, resumed);
    }

    @Test
    public void anEditThatLeavesTheScheduleAloneKeepsAnExhaustedPointer() {
        String resumed = RecurrenceSetting.resumeAfterEdit(
                "FREQ=DAILY;UNTIL=20200201", sql(day(2020, 0, 10)), null,
                "FREQ=DAILY;UNTIL=20200201", sql(day(2020, 0, 10)), day(2020, 2, 5));
        assertNull(resumed);
    }

    @Test
    public void changingTheRuleMovesThePointerOntoTheNewCadence() {
        // daily would fire tomorrow; monthly keeps the day of month the rule started on
        String resumed = RecurrenceSetting.resumeAfterEdit(
                "FREQ=DAILY", sql(day(2020, 0, 10)), sql(day(2020, 2, 6)),
                "FREQ=MONTHLY", sql(day(2020, 0, 10)), day(2020, 2, 5));
        assertEquals(sql(day(2020, 2, 10)), resumed);
    }

    @Test
    public void changingOnlyTheStartDateAlsoMovesThePointer() {
        String resumed = RecurrenceSetting.resumeAfterEdit(
                "FREQ=MONTHLY", sql(day(2020, 0, 10)), sql(day(2020, 2, 10)),
                "FREQ=MONTHLY", sql(day(2020, 0, 20)), day(2020, 2, 5));
        assertEquals(sql(day(2020, 2, 20)), resumed);
    }

    /** Pinned deliberately: a rule change does drop occurrences the old rule left owed. */
    @Test
    public void changingTheRuleDropsAnOwedPointer() {
        String resumed = RecurrenceSetting.resumeAfterEdit(
                "FREQ=DAILY", sql(day(2020, 0, 10)), sql(day(2020, 1, 10)),
                "FREQ=MONTHLY", sql(day(2020, 0, 10)), day(2020, 2, 5));
        assertEquals(sql(day(2020, 2, 10)), resumed);
    }

    @Test
    public void editingOntoAnAlreadyExhaustedRuleLeavesNoNextOccurrence() {
        String resumed = RecurrenceSetting.resumeAfterEdit(
                "FREQ=DAILY", sql(day(2020, 0, 10)), sql(day(2020, 2, 6)),
                "FREQ=DAILY;UNTIL=20200201", sql(day(2020, 0, 10)), day(2020, 2, 5));
        assertNull(resumed);
    }

    @Test
    public void editingAScheduleThatHasNotStartedResumesOnItsStartDate() {
        String resumed = RecurrenceSetting.resumeAfterEdit(
                "FREQ=DAILY", sql(day(2020, 0, 10)), sql(day(2020, 4, 2)),
                "FREQ=MONTHLY", sql(day(2020, 5, 10)), day(2020, 4, 1));
        assertEquals(sql(day(2020, 5, 10)), resumed);
    }

    // ---------------------------------------------------------------------------------------------
    // the period a repeating budget covers
    // ---------------------------------------------------------------------------------------------

    @Test
    public void aMonthlyPeriodRunsFromItsAnchorDayToTheDayBeforeTheNextOne() {
        assertDay(RecurrenceSetting.periodEnd("FREQ=MONTHLY;BYMONTHDAY=15", day(2026, 0, 15), day(2026, 0, 15)), 2026, 1, 14);
    }

    @Test
    public void aMonthlyPeriodFollowsTheCalendarInsteadOfAFixedDayCount() {
        // february is three days shorter than january, so a fixed length would land on 17 march
        assertDay(RecurrenceSetting.periodEnd("FREQ=MONTHLY;BYMONTHDAY=15", day(2026, 1, 15), day(2026, 1, 15)), 2026, 2, 14);
    }

    @Test
    public void aWeeklyPeriodIsSevenDaysLong() {
        assertDay(RecurrenceSetting.periodEnd("FREQ=WEEKLY;BYDAY=MO", day(2026, 0, 5), day(2026, 0, 5)), 2026, 0, 11);
    }

    @Test
    public void anIntervalIsPartOfThePeriodLength() {
        assertDay(RecurrenceSetting.periodEnd("FREQ=MONTHLY;INTERVAL=3;BYMONTHDAY=1", day(2026, 0, 1), day(2026, 0, 1)), 2026, 2, 31);
    }

    @Test
    public void aPeriodEndsTheDayBeforeTheNextInstanceEvenAcrossAYear() {
        assertDay(RecurrenceSetting.periodEnd("FREQ=YEARLY", day(2026, 5, 30), day(2026, 5, 30)), 2027, 5, 29);
    }

    @Test
    public void aRuleThatNeverRepeatsSetsNoPeriodLength() {
        assertNull(RecurrenceSetting.periodEnd("FREQ=MONTHLY;COUNT=1", day(2026, 0, 15), day(2026, 0, 15)));
    }

    @Test
    public void anUnparseableRuleSetsNoPeriodLength() {
        assertNull(RecurrenceSetting.periodEnd("this is not a rule", day(2026, 0, 15), day(2026, 0, 15)));
    }

    @Test
    public void theNextInstanceIsStrictlyAfterTheOneItIsWalkedFrom() {
        assertDay(RecurrenceSetting.nextInstanceAfter("FREQ=MONTHLY;BYMONTHDAY=15", day(2026, 0, 15), day(2026, 0, 15)), 2026, 1, 15);
    }

    @Test
    public void aMonthDayThatSomeMonthsDoNotHaveFallsBackToTheLastDayOfTheMonth() {
        String rule = monthlyOn(day(2026, 0, 31));
        assertDay(RecurrenceSetting.nextInstanceAfter(rule, day(2026, 0, 31), day(2026, 0, 31)), 2026, 1, 28);
        assertDay(RecurrenceSetting.periodEnd(rule, day(2026, 0, 31), day(2026, 0, 31)), 2026, 1, 27);
    }

    @Test
    public void everyMonthOfAYearIsCoveredWhenTheAnchorIsTheThirtyFirst() {
        // asking for the 31st alone leaves february, april, june, september and november with no
        // occurrence, and each of those turns two months into one budget period
        String rule = monthlyOn(day(2026, 0, 31));
        Date instance = day(2026, 0, 31);
        int[] months = new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
        for (int month : months) {
            instance = RecurrenceSetting.nextInstanceAfter(rule, day(2026, 0, 31), instance);
            assertNotNull(instance);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(instance);
            assertEquals(month, calendar.get(Calendar.MONTH));
        }
    }

    @Test
    public void anAnchorTheMonthAlwaysHasIsUnchanged() {
        String rule = monthlyOn(day(2026, 0, 15));
        assertDay(RecurrenceSetting.nextInstanceAfter(rule, day(2026, 0, 15), day(2026, 0, 15)), 2026, 1, 15);
        assertDay(RecurrenceSetting.periodEnd(rule, day(2026, 0, 15), day(2026, 0, 15)), 2026, 1, 14);
    }

    @Test
    public void theFirstAndTheTwentyEighthAreUnchangedToo() {
        assertDay(RecurrenceSetting.nextInstanceAfter(monthlyOn(day(2026, 0, 1)), day(2026, 0, 1), day(2026, 0, 1)), 2026, 1, 1);
        assertDay(RecurrenceSetting.nextInstanceAfter(monthlyOn(day(2026, 0, 28)), day(2026, 0, 28), day(2026, 0, 28)), 2026, 1, 28);
        assertDay(RecurrenceSetting.nextInstanceAfter(monthlyOn(day(2026, 1, 28)), day(2026, 1, 28), day(2026, 1, 28)), 2026, 2, 28);
    }

    @Test
    public void anAnchorEveryMonthHasBuildsTheRuleItAlwaysBuilt() {
        // the string matters and not only the dates it produces: a recurring transaction keeps a
        // pointer at the occurrence it is owed, and the editor keeps that pointer only while the
        // rule it saves is the one already stored, compared as text
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=1", monthlyOn(day(2026, 0, 1)));
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=15", monthlyOn(day(2026, 0, 15)));
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=28", monthlyOn(day(2026, 0, 28)));
    }

    @Test
    public void anAnchorSomeMonthsLackAsksForTheLastDayAsWell() {
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=29,-1;BYSETPOS=1", monthlyOn(day(2026, 0, 29)));
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=30,-1;BYSETPOS=1", monthlyOn(day(2026, 0, 30)));
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=31,-1;BYSETPOS=1", monthlyOn(day(2026, 0, 31)));
    }

    @Test
    public void aYearlyScheduleAnchoredToALeapDayComesRoundEveryYear() {
        String rule = yearlyOn(day(2024, 1, 29));
        assertEquals("FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=29,-1;BYSETPOS=1", rule);
        assertDay(RecurrenceSetting.nextInstanceAfter(rule, day(2024, 1, 29), day(2024, 1, 29)), 2025, 1, 28);
        assertDay(RecurrenceSetting.periodEnd(rule, day(2024, 1, 29), day(2024, 1, 29)), 2025, 1, 27);
    }

    @Test
    public void aYearlyScheduleAnchoredAnywhereElseBuildsTheRuleItAlwaysBuilt() {
        assertEquals("FREQ=YEARLY", yearlyOn(day(2026, 5, 30)));
        assertEquals("FREQ=YEARLY", yearlyOn(day(2026, 1, 28)));
        assertEquals("FREQ=YEARLY", yearlyOn(day(2026, 0, 31)));
    }

    @Test
    public void aYearlyPeriodIsAYearEvenAcrossALeapDay() {
        assertDay(RecurrenceSetting.periodEnd(yearlyOn(day(2027, 1, 28)), day(2027, 1, 28), day(2027, 1, 28)), 2028, 1, 27);
    }

    /** The rule the recurrence picker builds for a yearly schedule anchored to the given day. */
    private static String yearlyOn(Date startDate) {
        RecurrenceSetting.Builder builder = new RecurrenceSetting.Builder(startDate, RecurrenceSetting.TYPE_YEARLY);
        builder.setRepeatSameYearDay();
        return builder.build().getRule();
    }

    // ---------------------------------------------------------------------------------------------
    // the day a schedule is anchored to is what it counts from
    // ---------------------------------------------------------------------------------------------

    @Test
    public void aWeeklyScheduleKeepsItsWeekdayWhateverDayThePeriodStartsOn() {
        // wednesday 26 august 2026 is the anchor; the row starts on monday 3 august
        String rule = "FREQ=WEEKLY";
        assertDay(RecurrenceSetting.nextInstanceAfter(rule, day(2026, 7, 26), day(2026, 7, 3)), 2026, 7, 26);
        assertDay(RecurrenceSetting.nextInstanceAfter(rule, day(2026, 7, 26), day(2026, 7, 26)), 2026, 8, 2);
        assertDay(RecurrenceSetting.nextInstanceAfter(rule, day(2026, 7, 26), day(2026, 8, 2)), 2026, 8, 9);
    }

    @Test
    public void aScheduleThatRepeatsEveryOtherMonthKeepsItsMonths() {
        // anchored to 15 january, so march, may, july: never february, april, june
        String rule = "FREQ=MONTHLY;INTERVAL=2;BYMONTHDAY=15";
        assertDay(RecurrenceSetting.nextInstanceAfter(rule, day(2026, 0, 15), day(2026, 5, 3)), 2026, 6, 15);
        assertDay(RecurrenceSetting.nextInstanceAfter(rule, day(2026, 0, 15), day(2026, 6, 15)), 2026, 8, 15);
    }

    @Test
    public void aYearlyScheduleKeepsItsDayWhateverDayThePeriodStartsOn() {
        String rule = "FREQ=YEARLY";
        assertDay(RecurrenceSetting.nextInstanceAfter(rule, day(2027, 0, 1), day(2026, 7, 3)), 2027, 0, 1);
        assertDay(RecurrenceSetting.nextInstanceAfter(rule, day(2027, 0, 1), day(2027, 0, 1)), 2028, 0, 1);
    }

    @Test
    public void aPeriodStartingOffTheScheduleStillEndsWhereTheScheduleSays() {
        // the period a budget is in when its schedule is changed starts where it already started
        // and ends the day before the schedule next comes round
        assertDay(RecurrenceSetting.periodEnd("FREQ=MONTHLY;BYMONTHDAY=15", day(2026, 0, 15), day(2026, 7, 3)), 2026, 7, 14);
    }

    /** The rule the recurrence picker builds for a monthly schedule anchored to the given day. */
    private static String monthlyOn(Date startDate) {
        RecurrenceSetting.Builder builder = new RecurrenceSetting.Builder(startDate, RecurrenceSetting.TYPE_MONTHLY);
        builder.setRepeatSameMonthDay();
        return builder.build().getRule();
    }

    // ---------------------------------------------------------------------------------------------
    // the period a repeating budget is in today
    // ---------------------------------------------------------------------------------------------

    @Test
    public void aScheduleAnchoredInThePastLandsOnThePeriodItIsInToday() {
        Date[] period = RecurrenceSetting.periodOn("FREQ=MONTHLY;BYMONTHDAY=15", day(2025, 5, 15), day(2026, 7, 24));
        assertNotNull(period);
        assertDay(period[0], 2026, 7, 15);
        assertDay(period[1], 2026, 8, 14);
    }

    @Test
    public void aScheduleAnchoredTodayStaysOnItsFirstPeriod() {
        Date[] period = RecurrenceSetting.periodOn("FREQ=MONTHLY;BYMONTHDAY=24", day(2026, 7, 24), day(2026, 7, 24));
        assertNotNull(period);
        assertDay(period[0], 2026, 7, 24);
        assertDay(period[1], 2026, 8, 23);
    }

    @Test
    public void aScheduleAnchoredInTheFutureIsNotDraggedForward() {
        Date[] period = RecurrenceSetting.periodOn("FREQ=MONTHLY;BYMONTHDAY=1", day(2026, 11, 1), day(2026, 7, 24));
        assertNotNull(period);
        assertDay(period[0], 2026, 11, 1);
        assertDay(period[1], 2026, 11, 31);
    }

    @Test
    public void thePeriodEndingTodayIsStillTheCurrentOne() {
        Date[] period = RecurrenceSetting.periodOn("FREQ=MONTHLY;BYMONTHDAY=25", day(2026, 6, 25), day(2026, 7, 24));
        assertNotNull(period);
        assertDay(period[0], 2026, 6, 25);
        assertDay(period[1], 2026, 7, 24);
    }

    @Test
    public void thePeriodThatEndedYesterdayIsNotTheCurrentOne() {
        Date[] period = RecurrenceSetting.periodOn("FREQ=MONTHLY;BYMONTHDAY=24", day(2026, 6, 24), day(2026, 7, 24));
        assertNotNull(period);
        assertDay(period[0], 2026, 7, 24);
        assertDay(period[1], 2026, 8, 23);
    }

    @Test
    public void aRuleThatRunsOutBeforeTodayHasNoCurrentPeriod() {
        assertNull(RecurrenceSetting.periodOn("FREQ=MONTHLY;COUNT=2", day(2025, 0, 15), day(2026, 7, 24)));
    }

    @Test
    public void aRuleThatNeverRepeatsHasNoCurrentPeriod() {
        assertNull(RecurrenceSetting.periodOn("FREQ=MONTHLY;COUNT=1", day(2026, 7, 24), day(2026, 7, 24)));
    }

    @Test
    public void anUnparseableRuleHasNoCurrentPeriod() {
        assertNull(RecurrenceSetting.periodOn("this is not a rule", day(2026, 7, 24), day(2026, 7, 24)));
    }

    private static String sql(Date date) {
        return DateUtils.getSQLDateString(date);
    }
}
