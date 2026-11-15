package com.oriondev.moneywallet.model

import com.oriondev.moneywallet.utils.DateUtils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

import java.time.LocalDate
import java.util.Calendar
import java.util.Date

class RecurrenceSettingPeriodsBeforeTest {

    @Test
    fun aMonthlyScheduleOnThe15thGivesTheThreeMonthsBefore() {
        val periods = RecurrenceSetting.periodsBefore("FREQ=MONTHLY;BYMONTHDAY=15", day(2026, 0, 15), day(2026, 6, 15), 3)
        assertEquals(listOf("2026-04-15 2026-05-14", "2026-05-15 2026-06-14", "2026-06-15 2026-07-14"), sql(periods))
    }

    @Test
    fun aNewScheduleAnchoredOnThePeriodItsStartGivesTheSameMonths() {
        val periods = RecurrenceSetting.periodsBefore("FREQ=MONTHLY;BYMONTHDAY=15", day(2026, 6, 15), day(2026, 6, 15), 3)
        assertEquals(listOf("2026-04-15 2026-05-14", "2026-05-15 2026-06-14", "2026-06-15 2026-07-14"), sql(periods))
    }

    @Test
    fun aMonthlyScheduleOnThe31stTakesTheLastDayOfAShortMonth() {
        val rule = monthlyOn(day(2026, 2, 31))
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=31,-1;BYSETPOS=1", rule)
        val periods = RecurrenceSetting.periodsBefore(rule, day(2026, 2, 31), day(2026, 2, 31), 3)
        assertEquals(listOf("2025-12-31 2026-01-30", "2026-01-31 2026-02-27", "2026-02-28 2026-03-30"), sql(periods))
    }

    @Test
    fun aMonthlyScheduleOnThe31stGivesThreeFullMonthsOnTheDayBeforeAnInstance() {
        val rule = monthlyOn(day(2026, 9, 31))
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=31,-1;BYSETPOS=1", rule)
        val periods = RecurrenceSetting.periodsBefore(rule, day(2026, 9, 31), day(2026, 9, 30), 3)
        assertEquals(listOf("2026-06-30 2026-07-30", "2026-07-31 2026-08-30", "2026-08-31 2026-09-29"), sql(periods))
    }

    @Test
    fun aScheduleEverySecondMonthKeepsItsMonths() {
        val periods = RecurrenceSetting.periodsBefore("FREQ=MONTHLY;INTERVAL=2;BYMONTHDAY=15", day(2026, 4, 15), day(2026, 8, 15), 3)
        assertEquals(listOf("2026-03-15 2026-05-14", "2026-05-15 2026-07-14", "2026-07-15 2026-09-14"), sql(periods))
    }

    @Test
    fun aWeeklyScheduleKeepsTheWeekdayOfItsAnchor() {
        val periods = RecurrenceSetting.periodsBefore("FREQ=WEEKLY", day(2026, 7, 26), day(2026, 8, 16), 3)
        assertEquals(listOf("2026-08-26 2026-09-01", "2026-09-02 2026-09-08", "2026-09-09 2026-09-15"), sql(periods))
        assertWednesdays(periods)
    }

    @Test
    fun aScheduleEverySecondWeekKeepsTheWeekdayOfItsAnchor() {
        val periods = RecurrenceSetting.periodsBefore("FREQ=WEEKLY;INTERVAL=2", day(2026, 9, 7), day(2026, 9, 7), 3)
        assertEquals(listOf("2026-08-26 2026-09-08", "2026-09-09 2026-09-22", "2026-09-23 2026-10-06"), sql(periods))
        assertWednesdays(periods)
    }

    @Test
    fun aScheduleEveryThreeDaysStepsBackThreeDaysAtATime() {
        val periods = RecurrenceSetting.periodsBefore("FREQ=DAILY;INTERVAL=3", day(2026, 0, 10), day(2026, 0, 10), 3)
        assertEquals(listOf("2026-01-01 2026-01-03", "2026-01-04 2026-01-06", "2026-01-07 2026-01-09"), sql(periods))
    }

    @Test
    fun aYearlyScheduleOn29FebruaryTakesThe28thInAYearWithoutIt() {
        val rule = yearlyOn(day(2024, 1, 29))
        assertEquals("FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=29,-1;BYSETPOS=1", rule)
        val periods = RecurrenceSetting.periodsBefore(rule, day(2024, 1, 29), day(2027, 1, 28), 3)
        assertEquals(listOf("2024-02-29 2025-02-27", "2025-02-28 2026-02-27", "2026-02-28 2027-02-27"), sql(periods))
    }

    @Test
    fun anAnchorYearsBeforeGivesThePeriodsPeriodOnWalks() {
        val anchor = day(2020, 2, 31)
        val start = day(2026, 7, 31)
        val periods = RecurrenceSetting.periodsBefore("FREQ=MONTHLY", anchor, start, 3)
        assertEquals(listOf("2026-03-31 2026-05-30", "2026-05-31 2026-07-30", "2026-07-31 2026-08-30"), sql(periods))
        assertWalkedByPeriodOn("FREQ=MONTHLY", anchor, start, periods)
    }

    @Test
    fun anAnchorYearsBeforeOnThe31stGivesThePeriodsPeriodOnWalks() {
        val anchor = day(2019, 0, 31)
        val start = day(2026, 2, 31)
        val rule = monthlyOn(anchor)
        val periods = RecurrenceSetting.periodsBefore(rule, anchor, start, 3)
        assertEquals(listOf("2025-12-31 2026-01-30", "2026-01-31 2026-02-27", "2026-02-28 2026-03-30"), sql(periods))
        assertWalkedByPeriodOn(rule, anchor, start, periods)
    }

    @Test
    fun aScheduleThatSkipsShortMonthsGivesOnlyThePeriodsItHas() {
        val periods = RecurrenceSetting.periodsBefore("FREQ=MONTHLY;BYMONTHDAY=31", day(2026, 6, 31), day(2026, 7, 31), 3)
        assertEquals(listOf("2026-05-31 2026-07-30", "2026-07-31 2026-08-30"), sql(periods))
    }

    @Test
    fun anAnchorOldEnoughToWalkFromIsItselfLeftOut() {
        val periods = RecurrenceSetting.periodsBefore("FREQ=MONTHLY;BYMONTHDAY=31", day(2026, 2, 31), day(2026, 7, 31), 3)
        assertEquals(listOf("2026-05-31 2026-07-30", "2026-07-31 2026-08-30"), sql(periods))
    }

    @Test
    fun aDateOffTheScheduleLeavesOutThePeriodItFallsIn() {
        val periods = RecurrenceSetting.periodsBefore("FREQ=WEEKLY", day(2026, 0, 15), day(2026, 3, 15), 3)
        assertEquals(listOf("2026-03-19 2026-03-25", "2026-03-26 2026-04-01", "2026-04-02 2026-04-08"), sql(periods))
    }

    @Test
    fun aDateInsideAMonthGivesTheThreeFullMonthsBeforeIt() {
        val periods = RecurrenceSetting.periodsBefore("FREQ=MONTHLY;BYMONTHDAY=15", day(2026, 0, 15), day(2026, 6, 20), 3)
        assertEquals(listOf("2026-04-15 2026-05-14", "2026-05-15 2026-06-14", "2026-06-15 2026-07-14"), sql(periods))
    }

    @Test
    fun anAnchorAfterTheDateGivesTheMonthsBeforeTheDate() {
        val periods = RecurrenceSetting.periodsBefore("FREQ=MONTHLY;BYMONTHDAY=15", day(2028, 0, 15), day(2026, 6, 15), 3)
        assertEquals(listOf("2026-04-15 2026-05-14", "2026-05-15 2026-06-14", "2026-06-15 2026-07-14"), sql(periods))
    }

    @Test
    fun aRuleThatDoesNotParseGivesNoPeriod() {
        assertTrue(RecurrenceSetting.periodsBefore("this is not a rule", day(2026, 0, 15), day(2026, 6, 15), 3).isEmpty())
    }

    @Test
    fun everyScheduleTheEditorBuildsGivesTheThreeFullPeriodsBeforeEachDay() {
        val firstDate = LocalDate.of(2028, 1, 15)
        val lastDate = LocalDate.of(2028, 3, 15)
        for (type in listOf(RecurrenceSetting.TYPE_DAILY, RecurrenceSetting.TYPE_WEEKLY,
                RecurrenceSetting.TYPE_MONTHLY, RecurrenceSetting.TYPE_YEARLY)) {
            for (interval in 1..3) {
                var anchor = LocalDate.of(2027, 1, 1)
                while (!anchor.isAfter(LocalDate.of(2028, 12, 31))) {
                    val rule = ruleOf(type, interval, day(anchor))
                    val instances = instancesAround(type, interval, anchor, firstDate, lastDate)
                    var date = firstDate
                    while (!date.isAfter(lastDate)) {
                        val starts = instances.filter { !it.isAfter(date) }.takeLast(4)
                        val expected = starts.zipWithNext { start, next -> "$start ${next.minusDays(1)}" }
                        val periods = RecurrenceSetting.periodsBefore(rule, day(anchor), day(date), 3)
                        assertEquals("$rule from $anchor on $date", expected, sql(periods))
                        date = date.plusDays(1)
                    }
                    anchor = anchor.plusDays(1)
                }
            }
        }
    }

    @Test(timeout = 5000)
    fun anIntervalTooLongToStepBackGivesNoPeriod() {
        assertTrue(RecurrenceSetting.periodsBefore("FREQ=DAILY;INTERVAL=1073741824", day(2026, 11, 1), day(2026, 10, 14), 3).isEmpty())
    }

    @Test(timeout = 5000)
    fun anIntervalWhoseStepsWrapAroundGivesNoPeriod() {
        assertTrue(RecurrenceSetting.periodsBefore("FREQ=DAILY;INTERVAL=858993459", day(2026, 11, 1), day(2026, 10, 14), 3).isEmpty())
    }

    private fun assertWalkedByPeriodOn(rule: String, anchor: Date, start: Date, periods: List<Array<Date>>) {
        for (period in periods) {
            val walked = RecurrenceSetting.periodOn(rule, anchor, period[0])
            assertNotNull(walked)
            assertEquals(sql(listOf(walked!!)), sql(listOf(period)))
        }
        val current = RecurrenceSetting.periodOn(rule, anchor, start)
        assertNotNull(current)
        assertEquals(DateUtils.getSQLDateString(start), DateUtils.getSQLDateString(current!![0]))
    }

    private fun assertWednesdays(periods: List<Array<Date>>) {
        for (period in periods) {
            assertEquals(Calendar.WEDNESDAY, DateUtils.getCalendar(period[0]).get(Calendar.DAY_OF_WEEK))
        }
    }

    private fun day(year: Int, month: Int, dayOfMonth: Int): Date {
        return DateUtils.getDate(year, month, dayOfMonth)
    }

    private fun day(date: LocalDate): Date {
        return day(date.year, date.monthValue - 1, date.dayOfMonth)
    }

    private fun instancesAround(type: Int, interval: Int, anchor: LocalDate, first: LocalDate, last: LocalDate): List<LocalDate> {
        var cycles = 0
        while (instanceOf(type, interval, anchor, cycles).isBefore(first)) {
            cycles++
        }
        while (!instanceOf(type, interval, anchor, cycles).isBefore(first)) {
            cycles--
        }
        cycles -= 3
        val instances: MutableList<LocalDate> = ArrayList()
        var instance = instanceOf(type, interval, anchor, cycles)
        while (!instance.isAfter(last)) {
            instances.add(instance)
            cycles++
            instance = instanceOf(type, interval, anchor, cycles)
        }
        return instances
    }

    private fun instanceOf(type: Int, interval: Int, anchor: LocalDate, cycles: Int): LocalDate {
        val steps = (cycles * interval).toLong()
        return when (type) {
            RecurrenceSetting.TYPE_DAILY -> anchor.plusDays(steps)
            RecurrenceSetting.TYPE_WEEKLY -> anchor.plusWeeks(steps)
            RecurrenceSetting.TYPE_MONTHLY -> anchor.plusMonths(steps)
            else -> anchor.plusYears(steps)
        }
    }

    private fun sql(periods: List<Array<Date>>): List<String> {
        return periods.map { DateUtils.getSQLDateString(it[0]) + " " + DateUtils.getSQLDateString(it[1]) }
    }

    private fun ruleOf(type: Int, interval: Int, startDate: Date): String {
        val builder = RecurrenceSetting.Builder(startDate, type)
        builder.setOffset(interval)
        when (type) {
            RecurrenceSetting.TYPE_MONTHLY -> builder.setRepeatSameMonthDay()
            RecurrenceSetting.TYPE_YEARLY -> builder.setRepeatSameYearDay()
        }
        return builder.build().getRule()
    }

    private fun monthlyOn(startDate: Date): String {
        val builder = RecurrenceSetting.Builder(startDate, RecurrenceSetting.TYPE_MONTHLY)
        builder.setRepeatSameMonthDay()
        return builder.build().getRule()
    }

    private fun yearlyOn(startDate: Date): String {
        val builder = RecurrenceSetting.Builder(startDate, RecurrenceSetting.TYPE_YEARLY)
        builder.setRepeatSameYearDay()
        return builder.build().getRule()
    }
}
