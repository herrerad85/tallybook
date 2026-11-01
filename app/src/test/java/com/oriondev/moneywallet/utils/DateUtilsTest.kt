package com.oriondev.moneywallet.utils;

import org.junit.After;
import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

public class DateUtilsTest {

    /**
     * A spread of zones whose offsets have moved differently since 1900, and UTC, which has never
     * moved at all. None of the five has ever dropped a whole calendar date the way a zone that
     * crossed the date line has, so every day counted below is one that existed locally.
     */
    private static final String[] ZONES = {
            "Australia/Sydney", "Europe/Paris", "Europe/Madrid", "America/Chicago", "UTC"
    };

    /** 1 January 1900 to 31 December 2100, the range the calendar strip is given. */
    private static final int LAST_POSITION = 73413;

    private final TimeZone systemTimeZone = TimeZone.getDefault();

    @After
    public void restoreSystemTimeZone() {
        TimeZone.setDefault(systemTimeZone);
    }

    /**
     * The strip turns a position back into a day by adding that many days to its first date, so a
     * day count is right only if it inverts that walk. Counting from a span of local milliseconds
     * did not, and 1 January of a later year came back naming 1 January of the year before.
     */
    @Test
    public void countsThePositionThatRendersTheDayAsked() {
        for (String zone : ZONES) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            Calendar day = Calendar.getInstance();
            for (int position = 0; position <= LAST_POSITION; position++) {
                day.set(1900, Calendar.JANUARY, 1, 1, 0, 0);
                day.add(Calendar.DAY_OF_YEAR, position);
                assertEquals(zone, position, DateUtils.getCalendarDaysBetween(
                        1900, Calendar.JANUARY, 1, day.get(Calendar.YEAR),
                        day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH)));
            }
        }
    }

    @Test
    public void countsWholeDays() {
        // 7 days left in August, then 30, 31, 30 and 31, then 1 January itself
        assertEquals(130, DateUtils.getCalendarDaysBetween(
                2026, Calendar.AUGUST, 24, 2027, Calendar.JANUARY, 1));
        assertEquals(0, DateUtils.getCalendarDaysBetween(
                2027, Calendar.JANUARY, 1, 2027, Calendar.JANUARY, 1));
        assertEquals(1, DateUtils.getCalendarDaysBetween(
                2026, Calendar.DECEMBER, 31, 2027, Calendar.JANUARY, 1));
        assertEquals(-1, DateUtils.getCalendarDaysBetween(
                2027, Calendar.JANUARY, 1, 2026, Calendar.DECEMBER, 31));
        // 2026 is a common year and 2024 is a leap year
        assertEquals(365, DateUtils.getCalendarDaysBetween(
                2026, Calendar.JANUARY, 1, 2027, Calendar.JANUARY, 1));
        assertEquals(366, DateUtils.getCalendarDaysBetween(
                2024, Calendar.JANUARY, 1, 2025, Calendar.JANUARY, 1));
        // 127 years of 365 days, plus a day for each leap year from 1904 to 2024. 1900 is not
        // one, being a century that 400 does not divide, and 2000 is one for the same rule
        assertEquals(127 * 365 + ((2024 - 1904) / 4 + 1), DateUtils.getCalendarDaysBetween(
                1900, Calendar.JANUARY, 1, 2027, Calendar.JANUARY, 1));
    }
}
