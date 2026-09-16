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

package com.oriondev.moneywallet.ui.adapter.pager;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ApplicationProvider;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.RadarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.RadarData;
import com.github.mikephil.charting.data.RadarDataSet;
import com.github.mikephil.charting.data.RadarEntry;
import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.OverviewData;
import com.oriondev.moneywallet.model.PeriodMoney;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The period numbers the overview bar and line pages write along their x axis. Both pages used to
 * leave room for a period that is not there, so twelve periods drew a thirteenth label and five
 * periods drew a zero and a six, while the radar page wrote the raw index and read one behind the
 * other two.
 */
@RunWith(RobolectricTestRunner.class)
public class OverviewAxisLabelRangeTest {

    private Context themed() {
        return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),
                R.style.MoneyWalletAppTheme);
    }

    private static BarData barData(int count) {
        List<BarEntry> entries = new ArrayList<>();
        for (int at = 0; at < count; at++) {
            entries.add(new BarEntry(at, 1f));
        }
        return new BarData(new BarDataSet(entries, "bar"));
    }

    private static LineData lineData(int count) {
        List<Entry> entries = new ArrayList<>();
        for (int at = 0; at < count; at++) {
            entries.add(new Entry(at, 1f));
        }
        return new LineData(new LineDataSet(entries, "line"));
    }

    private static RadarData radarData(int count) {
        List<RadarEntry> entries = new ArrayList<>();
        for (int at = 0; at < count; at++) {
            entries.add(new RadarEntry(1f));
        }
        return new RadarData(new RadarDataSet(entries, "radar"));
    }

    private static List<PeriodMoney> periods(int count) {
        List<PeriodMoney> periods = new ArrayList<>();
        for (int at = 0; at < count; at++) {
            periods.add(new PeriodMoney(new Date(), new Date()));
        }
        return periods;
    }

    private View overviewPage(int position, int count) {
        OverviewChartViewPagerAdapter adapter = new OverviewChartViewPagerAdapter();
        adapter.setData(new OverviewData(barData(count), lineData(count), radarData(count),
                periods(count)));
        return (View) adapter.instantiateItem(new FrameLayout(themed()), position);
    }

    private static List<String> drawnLabels(XAxis axis) {
        List<String> labels = new ArrayList<>();
        for (int at = 0; at < axis.mEntryCount; at++) {
            labels.add(axis.getValueFormatter().getFormattedValue(axis.mEntries[at]));
        }
        return labels;
    }

    private static void assertLabelsWithin(String where, List<String> labels, int count) {
        assertFalse(where + " drew no labels at all, so there is nothing to check", labels.isEmpty());
        for (String label : labels) {
            assertTrue(where + " drew " + labels + ", and " + label + " is not a period number"
                            + " within 1 and " + count,
                    label.matches("\\d+") && Integer.parseInt(label) >= 1
                            && Integer.parseInt(label) <= count);
        }
    }

    private static void assertHalfPeriodMargins(String where, XAxis axis, int count) {
        assertEquals(where + " starts its axis somewhere other than half a period before the first",
                -0.5f, axis.getAxisMinimum(), 0f);
        assertEquals(where + " ends its axis somewhere other than half a period after the last",
                count - 0.5f, axis.getAxisMaximum(), 0f);
    }

    private void assertBarLabelsWithin(int count) {
        BarChart chart = overviewPage(0, count).findViewById(R.id.bar_chart_view);
        assertNotNull("the page carries no bar chart, so there are no axis labels to read", chart);
        // the adapter moves the axis bounds after the chart took its data, so the chart has to be
        // told again before it holds the label positions those bounds produce
        chart.notifyDataSetChanged();
        String where = "the overview bar chart over " + count + " periods";
        assertLabelsWithin(where, drawnLabels(chart.getXAxis()), count);
        assertHalfPeriodMargins(where, chart.getXAxis(), count);
    }

    private void assertLineLabelsWithin(int count) {
        LineChart chart = overviewPage(1, count).findViewById(R.id.line_chart_view);
        assertNotNull("the page carries no line chart, so there are no axis labels to read", chart);
        chart.notifyDataSetChanged();
        String where = "the overview line chart over " + count + " periods";
        assertLabelsWithin(where, drawnLabels(chart.getXAxis()), count);
        assertHalfPeriodMargins(where, chart.getXAxis(), count);
    }

    @Test
    public void theBarAxisStaysWithinTwelvePeriods() {
        assertBarLabelsWithin(12);
    }

    @Test
    public void theBarAxisStaysWithinFivePeriods() {
        assertBarLabelsWithin(5);
    }

    @Test
    public void theLineAxisStaysWithinTwelvePeriods() {
        assertLineLabelsWithin(12);
    }

    @Test
    public void theLineAxisStaysWithinFivePeriods() {
        assertLineLabelsWithin(5);
    }

    @Test
    public void theRadarAxisCountsFromTheFirstPeriod() {
        RadarChart chart = overviewPage(2, 12).findViewById(R.id.radar_chart_view);
        assertNotNull("the page carries no radar chart, so there is no axis label to read", chart);
        assertEquals("the radar chart names the first period by its index and not by its number",
                "1", chart.getXAxis().getValueFormatter().getFormattedValue(0f));
    }

}
