/*
 * Copyright (c) 2026.
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

package com.oriondev.moneywallet.ui.view.calendar;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS")
public class CalendarMonthRowTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 400;

    private final List<List<Integer>> reports = new ArrayList<>();

    private Context themed() {
        return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),
                R.style.MoneyWalletAppTheme);
    }

    private void laidOut(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, WIDTH, view.getMeasuredHeight());
    }

    private TimelineView strip(Context context) {
        TimelineView strip = new TimelineView(context);
        strip.setFirstDate(2026, Calendar.SEPTEMBER, 1);
        strip.setLastDate(2026, Calendar.DECEMBER, 31);
        strip.setOnMonthScrolledListener((year, month) -> reports.add(Arrays.asList(year, month)));
        return strip;
    }

    private MonthView monthRow() {
        MonthView row = new MonthView(themed());
        row.setFirstDate(2026, Calendar.JANUARY);
        row.setLastDate(2026, Calendar.DECEMBER);
        row.setOnMonthSelectedListener((year, month, index) ->
                reports.add(Arrays.asList(year, month, index)));
        laidOut(row);
        return row;
    }

    private static int middlePosition(TimelineView strip) {
        View middle = strip.findChildViewUnder(strip.getWidth() / 2f, strip.getHeight() / 2f);
        assertNotNull("no day sits in the middle of the strip", middle);
        return strip.getChildAdapterPosition(middle);
    }

    private static int firstPosition(TimelineView strip) {
        return strip.getChildAdapterPosition(strip.getChildAt(0));
    }

    @Test
    public void aLayoutAloneReportsNoMonth() {
        TimelineView strip = strip(themed());
        laidOut(strip);
        assertTrue("the strip laid out no days", strip.getChildCount() > 0);
        assertEquals("the strip named a month before anyone scrolled it, which puts the month row "
                        + "on the strip's first month every time the calendar opens",
                Collections.emptyList(), reports);
    }

    @Test
    public void aScrollReportsTheMonthOfTheDayInTheMiddle() {
        TimelineView strip = strip(themed());
        laidOut(strip);
        strip.scrollBy(25 * strip.getChildAt(0).getWidth(), 0);
        assertEquals("the first day on screen is not September 26", 25, firstPosition(strip));
        assertEquals("the day in the middle of the strip is not October 6", 35, middlePosition(strip));
        assertEquals("the month row names September while the middle of the strip is on October",
                Collections.singletonList(Arrays.asList(2026, Calendar.OCTOBER)), reports);
    }

    @Test
    public void twoScrollsInARowEachReportTheirOwnMonth() {
        TimelineView strip = strip(themed());
        laidOut(strip);
        int day = strip.getChildAt(0).getWidth();
        strip.scrollBy(25 * day, 0);
        strip.scrollBy(31 * day, 0);
        assertEquals("the day in the middle of the strip is not November 6", 66, middlePosition(strip));
        assertEquals("after a second drag the month row names a month the strip is not on",
                Arrays.asList(Arrays.asList(2026, Calendar.OCTOBER),
                        Arrays.asList(2026, Calendar.NOVEMBER)), reports);
    }

    @Test
    public void aStripNobodyListensToScrolls() {
        TimelineView strip = new TimelineView(themed());
        strip.setFirstDate(2026, Calendar.SEPTEMBER, 1);
        strip.setLastDate(2026, Calendar.DECEMBER, 31);
        laidOut(strip);
        try {
            strip.scrollBy(25 * strip.getChildAt(0).getWidth(), 0);
        } catch (NullPointerException e) {
            fail("a calendar strip that nobody asks for the month crashes the app as soon as it "
                    + "is dragged");
        }
        assertEquals("the strip did not move when it was dragged", 25, firstPosition(strip));
    }

    @Test
    public void placingTheStripOnADayReportsItsMonthAndPutsItInTheMiddle() {
        TimelineView strip = strip(themed());
        laidOut(strip);
        strip.centerOnPosition(40);
        laidOut(strip);
        assertEquals("the strip was put on October 11 and the month row did not follow it",
                Collections.singletonList(Arrays.asList(2026, Calendar.OCTOBER)), reports);
        assertEquals("the strip was asked to put October 11 in its middle and put another day "
                + "there", 40, middlePosition(strip));
    }

    @Test
    public void placingAStripNotLaidOutYetReportsNoMonth() {
        TimelineView strip = strip(themed());
        strip.centerOnPosition(40);
        assertEquals("a strip that is not on the screen yet moved the month row",
                Collections.emptyList(), reports);
    }

    @Test
    public void tappingTheSelectedDayReportsItsMonth() {
        TimelineView strip = strip(themed());
        laidOut(strip);
        strip.setSelectedDate(2026, Calendar.OCTOBER, 5);
        laidOut(strip);
        strip.scrollBy(-8 * strip.getChildAt(0).getWidth(), 0);
        reports.clear();
        View selected = strip.getLayoutManager().findViewByPosition(strip.getSelectedPosition());
        assertNotNull("October 5 is not on screen to be tapped", selected);
        selected.performClick();
        assertEquals("tapping the day already selected left the month row on the month the last "
                        + "drag reached",
                Collections.singletonList(Arrays.asList(2026, Calendar.OCTOBER)), reports);
    }

    @Test
    public void theFirstLayoutOnScreenReportsTheMonthOfTheSelectedDay() {
        Activity activity = Robolectric.buildActivity(AppCompatActivity.class).setup().get();
        TimelineView strip = strip(new ContextThemeWrapper(activity, R.style.MoneyWalletAppTheme));
        strip.setSelectedDate(2026, Calendar.NOVEMBER, 15);
        activity.setContentView(strip, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue("the strip laid out no days on screen", strip.getChildCount() > 0);
        assertEquals("the calendar opened with the month row on the strip's first month and not "
                        + "on the month of the day it shows",
                Collections.singletonList(Arrays.asList(2026, Calendar.NOVEMBER)), reports);
    }

    @Test
    public void aScrollWithNoDayInTheMiddleReportsNoMonth() {
        TimelineView strip = strip(themed());
        strip.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        strip.layout(0, 0, WIDTH, HEIGHT);
        strip.scrollBy(25 * strip.getChildAt(0).getWidth(), 0);
        assertEquals("the strip did not move when it was dragged", 25, firstPosition(strip));
        assertNull("a day sits in the middle of the strip",
                strip.findChildViewUnder(WIDTH / 2f, HEIGHT / 2f));
        assertEquals("a drag with no day under the middle of the strip moved the month row to "
                        + "August, a month the strip does not show",
                Collections.emptyList(), reports);
    }

    @Test
    public void tappingTheMarkedMonthReachesTheCalendar() {
        MonthView row = monthRow();
        row.getLayoutManager().findViewByPosition(0).performClick();
        assertEquals("tapping the month the row already marks did nothing, so the marked month is "
                        + "the one month that does not load its days",
                Collections.singletonList(Arrays.asList(2026, Calendar.JANUARY, 0)), reports);
    }

    @Test
    public void tappingAnotherMonthReachesTheCalendarAndMarksIt() {
        MonthView row = monthRow();
        row.getLayoutManager().findViewByPosition(2).performClick();
        assertEquals("tapping March did not load March",
                Collections.singletonList(Arrays.asList(2026, Calendar.MARCH, 2)), reports);
        assertEquals("tapping March left another month marked", 2, row.getSelectedPosition());
    }

    @Test
    public void placingAStripWithNoDays() {
        TimelineView strip = strip(themed());
        strip.setLastDate(2026, Calendar.AUGUST, 31);
        laidOut(strip);
        assertEquals("the strip laid out days it does not have", 0, strip.getChildCount());
        assertTrue("the strip is not laid out", strip.isLaidOut());
        try {
            strip.centerOnPosition(0);
        } catch (NullPointerException e) {
            fail("a calendar strip with no days crashes the app as soon as it is put on a day");
        }
    }

    @Test
    public void tappingTheMarkedMonthAfterADragPutsItBackInTheMiddle() {
        MonthView row = new MonthView(themed());
        row.setFirstDate(2025, Calendar.JANUARY);
        row.setLastDate(2027, Calendar.DECEMBER);
        row.setSelectedMonth(2026, Calendar.JUNE, false, true);
        laidOut(row);
        row.scrollBy(7 * row.getChildAt(0).getWidth(), 0);
        View marked = row.getLayoutManager().findViewByPosition(row.getSelectedPosition());
        assertNotNull("June 2026 is not on screen to be tapped", marked);
        assertEquals("the drag did not move June 2026 out of the middle of the row", 14,
                row.getChildAdapterPosition(row.findChildViewUnder(WIDTH / 2f, row.getHeight() / 2f)));
        marked.performClick();
        laidOut(row);
        assertEquals("tapping the month the row already marks left the row where the last drag put "
                        + "it, so the marked month stays off to the side", 17,
                row.getChildAdapterPosition(row.findChildViewUnder(WIDTH / 2f, row.getHeight() / 2f)));
    }
}
