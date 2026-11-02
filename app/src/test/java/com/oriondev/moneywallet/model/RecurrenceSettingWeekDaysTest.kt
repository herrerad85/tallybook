package com.oriondev.moneywallet.model

import com.oriondev.moneywallet.utils.DateUtils

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class RecurrenceSettingWeekDaysTest {

    @Test
    fun aWeeklyRuleOnSundaySetsOnlyIndexZero() {
        assertArrayEquals(booleanArrayOf(true, false, false, false, false, false, false), weekDays("SU"))
    }

    @Test
    fun aWeeklyRuleOnMondaySetsOnlyIndexOne() {
        assertArrayEquals(booleanArrayOf(false, true, false, false, false, false, false), weekDays("MO"))
    }

    @Test
    fun aWeeklyRuleOnTuesdaySetsOnlyIndexTwo() {
        assertArrayEquals(booleanArrayOf(false, false, true, false, false, false, false), weekDays("TU"))
    }

    @Test
    fun aWeeklyRuleOnWednesdaySetsOnlyIndexThree() {
        assertArrayEquals(booleanArrayOf(false, false, false, true, false, false, false), weekDays("WE"))
    }

    @Test
    fun aWeeklyRuleOnThursdaySetsOnlyIndexFour() {
        assertArrayEquals(booleanArrayOf(false, false, false, false, true, false, false), weekDays("TH"))
    }

    @Test
    fun aWeeklyRuleOnFridaySetsOnlyIndexFive() {
        assertArrayEquals(booleanArrayOf(false, false, false, false, false, true, false), weekDays("FR"))
    }

    @Test
    fun aWeeklyRuleOnSaturdaySetsOnlyIndexSix() {
        assertArrayEquals(booleanArrayOf(false, false, false, false, false, false, true), weekDays("SA"))
    }

    @Test
    fun aWeeklyRuleOnSeveralDaysSetsExactlyThoseIndexes() {
        assertArrayEquals(booleanArrayOf(true, false, true, false, true, false, true), weekDays("SU,TU,TH,SA"))
    }

    private fun weekDays(byDay: String): BooleanArray {
        val setting = RecurrenceSetting(DateUtils.getDate(2024, 2, 13), "FREQ=WEEKLY;BYDAY=$byDay")
        return setting.getWeekDays()
    }
}
