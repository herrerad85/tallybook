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

package com.oriondev.moneywallet.utils;

import android.content.Context;
import androidx.annotation.StringRes;
import android.text.format.DateUtils;
import android.widget.TextView;

import com.oriondev.moneywallet.storage.preference.PreferenceManager;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by andrea on 07/03/18.
 */

public class DateFormatter {

    public static final String DATE_PATTERN_0 = "EEEE dd MMMM yyyy";
    public static final String DATE_PATTERN_1 = "EEEE dd MMM yyyy";
    public static final String DATE_PATTERN_2 = "EEE dd MMM yyyy";
    public static final String DATE_PATTERN_3 = "dd MMM yyyy";
    public static final String DATE_PATTERN_4 = "EEE dd/MM/yyyy";
    public static final String DATE_PATTERN_5 = "dd/MM/yyyy";
    public static final String DATE_PATTERN_6 = "yyyy/MM/dd";
    public static final String DATE_PATTERN_7 = "MM/dd/yyyy";
    public static final String DATE_PATTERN_8 = "EEE MM/dd/yyyy";

    private static final String[] DATE_FORMATS = new String[] {
            DATE_PATTERN_0,
            DATE_PATTERN_1,
            DATE_PATTERN_2,
            DATE_PATTERN_3,
            DATE_PATTERN_4,
            DATE_PATTERN_5,
            DATE_PATTERN_6,
            DATE_PATTERN_7,
            DATE_PATTERN_8
    };

    private static final String TIME_SKELETON_24_HOUR = "Hm";
    private static final String TIME_SKELETON_12_HOUR = "hm";

    public static void applyDate(TextView textView, Date date) {
        textView.setText(getFormattedDate(date));
    }

    public static void applyTime(TextView textView, Date date) {
        textView.setText(getFormattedTime(date));
    }

    public static String getDateFromToday(Date date) {
        // TODO: find a way to represent dates in a relative way
        return getFormattedDateTime(date);
    }

    public static void applyDateFromToday(TextView textView, Date date, @StringRes int header) {
        // TODO: find a way to represent dates in a relative way
        Context context = textView.getContext();
        String base = context.getString(header);
        String formatted = getFormattedDate(date);
        textView.setText(String.format(base, formatted));
    }

    public static void applyDateTime(TextView textView, Date date) {
        textView.setText(getFormattedDateTime(date));
    }

    public static String getFormattedDate(Date date) {
        return getFormattedDate(date, PreferenceManager.getCurrentDateFormatIndex());
    }

    public static String getFormattedDate(Date date, int index) {
        return getUserDateFormat(index).format(date);
    }

    public static String getFormattedTime(Date date) {
        return new SimpleDateFormat(getTimePattern(), Locale.getDefault()).format(date);
    }

    public static String getFormattedDateTime(Date date) {
        return getFormattedDateTime(date, PreferenceManager.getCurrentDateFormatIndex());
    }

    public static String getFormattedDateTime(Date date, int index) {
        return getUserDateTimeFormat(index).format(date);
    }

    public static String getDateRange(Context context, Date start, Date end) {
        long startMillis = start.getTime();
        long endMillis = end.getTime();
        int flags = DateUtils.FORMAT_SHOW_DATE;
        return DateUtils.formatDateRange(context, startMillis, endMillis, flags);
    }

    public static void applyDateRange(TextView textView, Date start, Date end) {
        Context context = textView.getContext();
        textView.setText(getDateRange(context, start, end));
    }

    public static String getTimeRange(Context context, Date start, Date end) {
        long startMillis = start.getTime();
        long endMillis = end.getTime();
        int flags = DateUtils.FORMAT_SHOW_TIME;
        return DateUtils.formatDateRange(context, startMillis, endMillis, flags);
    }

    public static void applyTimeRange(TextView textView, Date start, Date end) {
        Context context = textView.getContext();
        textView.setText(getTimeRange(context, start, end));
    }

    /**
     * The time pattern follows the device wide 12 or 24 hour setting rather than a preference of
     * this app, so that times here read the same way as times everywhere else on the device. The
     * Time format row in the interface settings says so and links to the system screen.
     * <p>
     * The pattern comes from the locale rather than being written out here, because the position
     * of the AM and PM marker is not the same in every language: Chinese puts it before the time.
     */
    public static String getTimePattern() {
        String skeleton = is24HourFormat() ? TIME_SKELETON_24_HOUR : TIME_SKELETON_12_HOUR;
        return android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
    }

    public static boolean is24HourFormat() {
        return android.text.format.DateFormat.is24HourFormat(PreferenceManager.getApplicationContext());
    }

    private static String getDatePattern(int index) {
        int safeIndex = 0;
        if (index >= 0 && index < DATE_FORMATS.length) {
            safeIndex = index;
        }
        return DATE_FORMATS[safeIndex];
    }

    private static DateFormat getUserDateFormat(int index) {
        return new SimpleDateFormat(getDatePattern(index), Locale.getDefault());
    }

    private static DateFormat getUserDateTimeFormat(int index) {
        return new SimpleDateFormat(getDatePattern(index) + ", " + getTimePattern(), Locale.getDefault());
    }
}