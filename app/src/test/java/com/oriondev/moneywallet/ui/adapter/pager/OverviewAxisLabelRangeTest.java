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

import com.github.mikephil.charting.charts.RadarChart;
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
import static org.junit.Assert.assertNotNull;

/**
 * The period numbers the overview radar page writes around its spokes. It used to write the raw
 * index and read one behind the bar and line pages.
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

    @Test
    public void theRadarAxisCountsFromTheFirstPeriod() {
        RadarChart chart = overviewPage(2, 12).findViewById(R.id.radar_chart_view);
        assertNotNull("the page carries no radar chart, so there is no axis label to read", chart);
        assertEquals("the radar chart names the first period by its index and not by its number",
                "1", chart.getXAxis().getValueFormatter().getFormattedValue(0f));
    }

}
