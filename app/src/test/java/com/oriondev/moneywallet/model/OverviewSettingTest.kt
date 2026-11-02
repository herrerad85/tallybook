package com.oriondev.moneywallet.model

import android.os.Parcel

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar

@RunWith(RobolectricTestRunner::class)
class OverviewSettingTest {

    @Test
    fun aParcelRoundTripKeepsACashFlowSettingForIncomes() {
        val setting = OverviewSetting(date(2024, Calendar.MARCH, 5, 14, 30), date(2024, Calendar.MARCH, 28, 9, 15), Group.WEEKLY, OverviewSetting.CashFlow.INCOMES)
        val back = roundTrip(setting)
        assertEquals(date(2024, Calendar.MARCH, 5, 0, 0, 0, 0), back.getStartDate())
        assertEquals(date(2024, Calendar.MARCH, 28, 23, 59, 59, 999), back.getEndDate())
        assertEquals(Group.WEEKLY, back.getGroupType())
        assertEquals(OverviewSetting.Type.CASH_FLOW, back.getType())
        assertEquals(OverviewSetting.CashFlow.INCOMES, back.getCashFlow())
        assertEquals(0L, back.getCategoryId())
    }

    @Test
    fun aParcelRoundTripKeepsACashFlowSettingForExpenses() {
        val setting = OverviewSetting(date(2023, Calendar.JANUARY, 1, 8, 0), date(2023, Calendar.DECEMBER, 31, 18, 45), Group.MONTHLY, OverviewSetting.CashFlow.EXPENSES)
        val back = roundTrip(setting)
        assertEquals(date(2023, Calendar.JANUARY, 1, 0, 0, 0, 0), back.getStartDate())
        assertEquals(date(2023, Calendar.DECEMBER, 31, 23, 59, 59, 999), back.getEndDate())
        assertEquals(Group.MONTHLY, back.getGroupType())
        assertEquals(OverviewSetting.Type.CASH_FLOW, back.getType())
        assertEquals(OverviewSetting.CashFlow.EXPENSES, back.getCashFlow())
        assertEquals(0L, back.getCategoryId())
    }

    @Test
    fun aParcelRoundTripKeepsACashFlowSettingForNetIncomes() {
        val setting = OverviewSetting(date(2020, Calendar.JUNE, 10, 23, 1), date(2022, Calendar.JUNE, 9, 0, 1), Group.YEARLY, OverviewSetting.CashFlow.NET_INCOMES)
        val back = roundTrip(setting)
        assertEquals(date(2020, Calendar.JUNE, 10, 0, 0, 0, 0), back.getStartDate())
        assertEquals(date(2022, Calendar.JUNE, 9, 23, 59, 59, 999), back.getEndDate())
        assertEquals(Group.YEARLY, back.getGroupType())
        assertEquals(OverviewSetting.Type.CASH_FLOW, back.getType())
        assertEquals(OverviewSetting.CashFlow.NET_INCOMES, back.getCashFlow())
        assertEquals(0L, back.getCategoryId())
    }

    @Test
    fun aParcelRoundTripKeepsACategorySetting() {
        val setting = OverviewSetting(date(2024, Calendar.FEBRUARY, 1, 12, 0), date(2024, Calendar.FEBRUARY, 29, 12, 0), Group.DAILY, 731L)
        val back = roundTrip(setting)
        assertEquals(date(2024, Calendar.FEBRUARY, 1, 0, 0, 0, 0), back.getStartDate())
        assertEquals(date(2024, Calendar.FEBRUARY, 29, 23, 59, 59, 999), back.getEndDate())
        assertEquals(Group.DAILY, back.getGroupType())
        assertEquals(OverviewSetting.Type.CATEGORY, back.getType())
        assertEquals(OverviewSetting.CashFlow.NET_INCOMES, back.getCashFlow())
        assertEquals(731L, back.getCategoryId())
    }

    private fun roundTrip(setting: OverviewSetting): OverviewSetting {
        val parcel = Parcel.obtain()
        setting.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val back = OverviewSetting.CREATOR.createFromParcel(parcel)
        parcel.recycle()
        return back
    }

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0, millis: Int = 0): Date {
        val calendar = GregorianCalendar(year, month, day, hour, minute, second)
        calendar.set(Calendar.MILLISECOND, millis)
        return calendar.time
    }
}
