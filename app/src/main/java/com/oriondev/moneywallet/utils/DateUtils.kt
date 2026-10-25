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

package com.oriondev.moneywallet.utils

import org.dmfs.rfc5545.DateTime

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Created by andrea on 03/03/18.
 */

object DateUtils {

    private const val SQL_DATE = "yyyy-MM-dd"
    private const val SQL_DATETIME = "yyyy-MM-dd HH:mm:ss"
    private const val FILENAME_DATETIME = "yyyy-MM-dd_HH-mm-ss"

    @JvmStatic
    fun getDateFromSQLDateString(date: String?): Date {
        val dateFormat: DateFormat = SimpleDateFormat(SQL_DATE, Locale.ENGLISH)
        try {
            return dateFormat.parse(date)
        } catch (e: ParseException) {
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    fun getDateFromSQLDateTimeString(dateTime: String?): Date {
        val dateFormat: DateFormat = SimpleDateFormat(SQL_DATETIME, Locale.ENGLISH)
        try {
            return dateFormat.parse(dateTime)
        } catch (e: ParseException) {
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    fun getDateTimeString(date: Date?, pattern: String?): String {
        val format: DateFormat = SimpleDateFormat(pattern, Locale.ENGLISH)
        return format.format(date)
    }

    @JvmStatic
    fun getSQLDateString(date: Date?): String {
        val format: DateFormat = SimpleDateFormat(SQL_DATE, Locale.ENGLISH)
        return format.format(date)
    }

    @JvmStatic
    fun getSQLDateTimeString(date: Date?): String {
        val format: DateFormat = SimpleDateFormat(SQL_DATETIME, Locale.ENGLISH)
        return format.format(date)
    }

    @JvmStatic
    fun getSQLDateTimeString(millis: Long): String {
        val format: DateFormat = SimpleDateFormat(SQL_DATETIME, Locale.ENGLISH)
        return format.format(Date(millis))
    }

    @JvmStatic
    fun getFilenameDateTimeString(date: Date?): String {
        val format: DateFormat = SimpleDateFormat(FILENAME_DATETIME, Locale.ENGLISH)
        return format.format(date)
    }

    @JvmStatic
    fun setTime(date: Date?, hour: Int, minute: Int, second: Int, millis: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        return setTime(calendar, hour, minute, second, millis)
    }

    @JvmStatic
    fun setTime(calendar: Calendar?, hour: Int, minute: Int, second: Int, millis: Int): Date {
        calendar!!.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, second)
        calendar.set(Calendar.MILLISECOND, millis)
        return calendar.time
    }

    @JvmStatic
    fun setDayOfMonth(calendar: Calendar?, day: Int): Date {
        calendar!!.set(Calendar.DAY_OF_MONTH, day)
        return calendar.time
    }

    @JvmStatic
    fun setMonth(calendar: Calendar?, month: Int): Date {
        calendar!!.set(Calendar.MONTH, month)
        return calendar.time
    }

    @JvmStatic
    fun setDayAndMonth(calendar: Calendar?, day: Int, month: Int): Date {
        calendar!!.set(Calendar.DAY_OF_MONTH, day)
        calendar.set(Calendar.MONTH, month)
        return calendar.time
    }

    @JvmStatic
    fun getFirstDateOfWeek(calendar: Calendar?): Date {
        return getFirstDateOfWeek(calendar, calendar!!.firstDayOfWeek)
    }

    @JvmStatic
    fun getFirstDateOfWeek(calendar: Calendar?, firstDayOfWeek: Int): Date {
        val currentDayOfWeek = calendar!!.get(Calendar.DAY_OF_WEEK)
        if (currentDayOfWeek < firstDayOfWeek) {
            // move to week before
            calendar.add(Calendar.WEEK_OF_YEAR, -1)
        }
        calendar.set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        return calendar.time
    }

    @JvmStatic
    fun getDate(year: Int, month: Int, day: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, day)
        return calendar.time
    }

    @JvmStatic
    fun addDays(calendar: Calendar?, days: Int): Date {
        calendar!!.add(Calendar.DAY_OF_MONTH, days)
        return calendar.time
    }

    @JvmStatic
    fun addMonths(calendar: Calendar?, months: Int): Date {
        calendar!!.add(Calendar.MONTH, months)
        return calendar.time
    }

    @JvmStatic
    fun addMonthAndDay(calendar: Calendar?, months: Int, days: Int): Date {
        calendar!!.add(Calendar.MONTH, months)
        calendar.add(Calendar.DAY_OF_MONTH, days)
        return calendar.time
    }

    @JvmStatic
    fun isBeforeToday(date: Date?): Boolean {
        val calendar = Calendar.getInstance()
        setTime(calendar, 0, 0, 0, 0)
        return date!!.time < calendar.timeInMillis
    }

    @JvmStatic
    fun isBeforeNow(date: Date?): Boolean {
        return date!!.compareTo(Date()) == -1
    }

    @JvmStatic
    fun isAfter(date1: Date?, date2: Date?): Boolean {
        return date1!!.compareTo(date2) == 1
    }

    @JvmStatic
    fun isAfterEqual(date1: Date?, date2: Date?): Boolean {
        return date1!!.compareTo(date2) != -1
    }

    @JvmStatic
    fun isBefore(date1: Date?, date2: Date?): Boolean {
        return date1!!.compareTo(date2) == -1
    }

    @JvmStatic
    fun isBeforeEqual(date1: Date?, date2: Date?): Boolean {
        return date1!!.compareTo(date2) != 1
    }

    @JvmStatic
    fun isTheSameDay(start: Date?, end: Date?): Boolean {
        val calendar1 = getCalendar(start)
        val calendar2 = getCalendar(end)
        return calendar1.get(Calendar.YEAR) == calendar2.get(Calendar.YEAR) &&
                calendar1.get(Calendar.MONTH) == calendar2.get(Calendar.MONTH) &&
                calendar1.get(Calendar.DAY_OF_MONTH) == calendar2.get(Calendar.DAY_OF_MONTH)
    }

    @JvmStatic
    fun isTheSameDayAndHour(start: Date?, end: Date?): Boolean {
        val calendar1 = getCalendar(start)
        val calendar2 = getCalendar(end)
        return calendar1.get(Calendar.YEAR) == calendar2.get(Calendar.YEAR) &&
                calendar1.get(Calendar.MONTH) == calendar2.get(Calendar.MONTH) &&
                calendar1.get(Calendar.DAY_OF_MONTH) == calendar2.get(Calendar.DAY_OF_MONTH) &&
                calendar1.get(Calendar.HOUR_OF_DAY) == calendar2.get(Calendar.HOUR_OF_DAY)
    }

    @JvmStatic
    fun getDaysBetween(start: Date?, end: Date?): Int {
        return ((Math.abs(end!!.time - start!!.time)) / (1000 * 60 * 60 * 24)).toInt()
    }

    /**
     * The number of calendar days from one date to the other, negative when the second date is
     * the earlier one. The months are zero based, the way [Calendar] numbers them.
     *
     * Counted in UTC, where every day is 24 hours long. Subtracting two local timestamps and
     * dividing by 24 hours does not answer this, because a zone whose offset grew between the two
     * dates leaves the span short of a whole number of days and the division truncates.
     */
    @JvmStatic
    fun getCalendarDaysBetween(fromYear: Int, fromMonth: Int, fromDay: Int,
                               toYear: Int, toMonth: Int, toDay: Int): Int {
        val span = utcMidnight(toYear, toMonth, toDay) - utcMidnight(fromYear, fromMonth, fromDay)
        return (span / TimeUnit.DAYS.toMillis(1)).toInt()
    }

    private fun utcMidnight(year: Int, month: Int, day: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        calendar.set(year, month, day)
        return calendar.timeInMillis
    }

    @JvmStatic
    fun getCalendar(date: Date?): Calendar {
        val calendar = Calendar.getInstance()
        calendar.time = date
        return calendar
    }

    @JvmStatic
    fun getFixedDateTime(date: Date?): DateTime {
        val calendar = Calendar.getInstance()
        calendar.time = date
        return DateTime(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    @JvmStatic
    fun getFixedDate(dateTime: DateTime?): Date {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, dateTime!!.year)
        calendar.set(Calendar.MONTH, dateTime.month)
        calendar.set(Calendar.DAY_OF_MONTH, dateTime.dayOfMonth)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }
}
