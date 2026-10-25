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

import android.content.Context
import androidx.annotation.StringRes
import android.text.format.DateUtils
import android.widget.TextView

import com.oriondev.moneywallet.storage.preference.PreferenceManager

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Created by andrea on 07/03/18.
 */

object DateFormatter {

    const val DATE_PATTERN_0 = "EEEE dd MMMM yyyy"
    const val DATE_PATTERN_1 = "EEEE dd MMM yyyy"
    const val DATE_PATTERN_2 = "EEE dd MMM yyyy"
    const val DATE_PATTERN_3 = "dd MMM yyyy"
    const val DATE_PATTERN_4 = "EEE dd/MM/yyyy"
    const val DATE_PATTERN_5 = "dd/MM/yyyy"
    const val DATE_PATTERN_6 = "yyyy/MM/dd"
    const val DATE_PATTERN_7 = "MM/dd/yyyy"
    const val DATE_PATTERN_8 = "EEE MM/dd/yyyy"

    private val DATE_FORMATS = arrayOf(
            DATE_PATTERN_0,
            DATE_PATTERN_1,
            DATE_PATTERN_2,
            DATE_PATTERN_3,
            DATE_PATTERN_4,
            DATE_PATTERN_5,
            DATE_PATTERN_6,
            DATE_PATTERN_7,
            DATE_PATTERN_8
    )

    private const val TIME_SKELETON_24_HOUR = "Hm"
    private const val TIME_SKELETON_12_HOUR = "hm"

    @JvmStatic
    fun applyDate(textView: TextView?, date: Date?) {
        textView!!.text = getFormattedDate(date)
    }

    @JvmStatic
    fun applyTime(textView: TextView?, date: Date?) {
        textView!!.text = getFormattedTime(date)
    }

    @JvmStatic
    fun getDateFromToday(date: Date?): String {
        // TODO: find a way to represent dates in a relative way
        return getFormattedDateTime(date)
    }

    @JvmStatic
    fun applyDateFromToday(textView: TextView?, date: Date?, @StringRes header: Int) {
        // TODO: find a way to represent dates in a relative way
        val context = textView!!.context
        val base = context.getString(header)
        val formatted = getFormattedDate(date)
        textView.text = String.format(base, formatted)
    }

    @JvmStatic
    fun applyDateTime(textView: TextView?, date: Date?) {
        textView!!.text = getFormattedDateTime(date)
    }

    @JvmStatic
    fun getFormattedDate(date: Date?): String {
        return getFormattedDate(date, PreferenceManager.getCurrentDateFormatIndex())
    }

    @JvmStatic
    fun getFormattedDate(date: Date?, index: Int): String {
        return getUserDateFormat(index).format(date)
    }

    @JvmStatic
    fun getFormattedTime(date: Date?): String {
        return SimpleDateFormat(getTimePattern(), Locale.getDefault()).format(date)
    }

    @JvmStatic
    fun getFormattedDateTime(date: Date?): String {
        return getFormattedDateTime(date, PreferenceManager.getCurrentDateFormatIndex())
    }

    @JvmStatic
    fun getFormattedDateTime(date: Date?, index: Int): String {
        return getUserDateTimeFormat(index).format(date)
    }

    @JvmStatic
    fun getDateRange(context: Context?, start: Date?, end: Date?): String {
        val startMillis = start!!.time
        val endMillis = end!!.time
        val flags = DateUtils.FORMAT_SHOW_DATE
        return DateUtils.formatDateRange(context, startMillis, endMillis, flags)
    }

    @JvmStatic
    fun applyDateRange(textView: TextView?, start: Date?, end: Date?) {
        val context = textView!!.context
        textView.text = getDateRange(context, start, end)
    }

    @JvmStatic
    fun getTimeRange(context: Context?, start: Date?, end: Date?): String {
        val startMillis = start!!.time
        val endMillis = end!!.time
        val flags = DateUtils.FORMAT_SHOW_TIME
        return DateUtils.formatDateRange(context, startMillis, endMillis, flags)
    }

    @JvmStatic
    fun applyTimeRange(textView: TextView?, start: Date?, end: Date?) {
        val context = textView!!.context
        textView.text = getTimeRange(context, start, end)
    }

    /**
     * The time pattern follows the device wide 12 or 24 hour setting rather than a preference of
     * this app, so that times here read the same way as times everywhere else on the device. The
     * Time format row in the interface settings says so and links to the system screen.
     *
     * The pattern comes from the locale rather than being written out here, because the position
     * of the AM and PM marker is not the same in every language: Chinese puts it before the time.
     */
    @JvmStatic
    fun getTimePattern(): String {
        val skeleton = if (is24HourFormat()) TIME_SKELETON_24_HOUR else TIME_SKELETON_12_HOUR
        return android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
    }

    @JvmStatic
    fun is24HourFormat(): Boolean {
        return android.text.format.DateFormat.is24HourFormat(PreferenceManager.getApplicationContext())
    }

    private fun getDatePattern(index: Int): String {
        var safeIndex = 0
        if (index >= 0 && index < DATE_FORMATS.size) {
            safeIndex = index
        }
        return DATE_FORMATS[safeIndex]
    }

    private fun getUserDateFormat(index: Int): DateFormat {
        return SimpleDateFormat(getDatePattern(index), Locale.getDefault())
    }

    private fun getUserDateTimeFormat(index: Int): DateFormat {
        return SimpleDateFormat(getDatePattern(index) + ", " + getTimePattern(), Locale.getDefault())
    }
}
