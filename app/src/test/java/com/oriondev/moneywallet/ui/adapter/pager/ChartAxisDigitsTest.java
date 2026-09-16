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
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.model.Money;
import com.oriondev.moneywallet.model.OverviewData;
import com.oriondev.moneywallet.model.PeriodDetailSummaryData;
import com.oriondev.moneywallet.model.PeriodMoney;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The period numbers the two overview charts and the period detail summary chart write along
 * their x axis, in the digits of the language the app is in. All three used to build those
 * numbers without a locale, so a Persian screen read 1 2 3 under amounts written in Persian
 * digits.
 */
@RunWith(RobolectricTestRunner.class)
public class ChartAxisDigitsTest {

    private static final int PERSIAN_ZERO = 0x06F0;
    private static final int PERSIAN_NINE = 0x06F9;

    private static final String PERSIAN_ONE = "۱";

    private Context themed() {
        return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),
                R.style.MoneyWalletAppTheme);
    }

    /**
     * Fails when the locale under test is not the Persian one. The digit assertions below would
     * fail on their own and say why, but a label read in the wrong language tells nothing about
     * the formatter this file is here to check.
     */
    private void requirePersianLocale() {
        assertEquals("the qualifier did not reach the default locale, so nothing below is a check",
                "fa", Locale.getDefault().getLanguage());
    }

    private static void assertPersianDigits(String where, String text) {
        assertTrue(where + " is empty, so it carries no digits to check", text.length() > 0);
        for (int at = 0; at < text.length(); at++) {
            char character = text.charAt(at);
            assertTrue(where + " drew " + text + ", which carries the character " + character
                            + " from outside the Persian set",
                    character >= PERSIAN_ZERO && character <= PERSIAN_NINE);
        }
        assertEquals(where + " drew " + text + ", and the formatter was asked for the first "
                + "period, which is the Persian one", PERSIAN_ONE, text);
    }

    private static List<PeriodMoney> onePeriod() {
        return Collections.singletonList(new PeriodMoney(new Date(), new Date()));
    }

    private static BarDataSet oneBar(String label) {
        return new BarDataSet(new ArrayList<>(Collections.singletonList(new BarEntry(0f, 1f))),
                label);
    }

    /**
     * Two data sets, because the summary adapter groups its bars and MPAndroidChart refuses to
     * group below two of them.
     */
    private static BarData groupedBarData() {
        return new BarData(oneBar("first"), oneBar("second"));
    }

    private static LineData oneLineData() {
        return new LineData(new LineDataSet(
                new ArrayList<>(Collections.singletonList(new Entry(0f, 1f))), "line"));
    }

    private View overviewPage(int position) {
        OverviewChartViewPagerAdapter adapter = new OverviewChartViewPagerAdapter();
        adapter.setData(new OverviewData(new BarData(oneBar("bar")), oneLineData(), null,
                onePeriod()));
        return (View) adapter.instantiateItem(new FrameLayout(themed()), position);
    }

    private View summaryPage() {
        BarChartViewPagerAdapter adapter = new BarChartViewPagerAdapter();
        adapter.setData(new PeriodDetailSummaryData(new Money(),
                Collections.singletonList(groupedBarData()),
                Collections.singletonList(new CurrencyUnit("USD", "US Dollar", "$", 2)),
                onePeriod()));
        return (View) adapter.instantiateItem(new FrameLayout(themed()), 0);
    }

    private static String barAxisLabel(View page) {
        BarChart chart = page.findViewById(R.id.bar_chart_view);
        assertNotNull("the page carries no bar chart, so there is no axis label to read", chart);
        return chart.getXAxis().getValueFormatter().getFormattedValue(0f);
    }

    private static String lineAxisLabel(View page) {
        LineChart chart = page.findViewById(R.id.line_chart_view);
        assertNotNull("the page carries no line chart, so there is no axis label to read", chart);
        return chart.getXAxis().getValueFormatter().getFormattedValue(0f);
    }

    @Test
    @Config(qualifiers = "fa-rIR")
    public void theOverviewBarAxisIsWrittenInPersianDigits() {
        requirePersianLocale();
        assertPersianDigits("the overview bar chart period number", barAxisLabel(overviewPage(0)));
    }

    @Test
    @Config(qualifiers = "fa-rIR")
    public void theOverviewLineAxisIsWrittenInPersianDigits() {
        requirePersianLocale();
        assertPersianDigits("the overview line chart period number", lineAxisLabel(overviewPage(1)));
    }

    @Test
    @Config(qualifiers = "fa-rIR")
    public void theSummaryBarAxisIsWrittenInPersianDigits() {
        requirePersianLocale();
        assertPersianDigits("the summary chart period number", barAxisLabel(summaryPage()));
    }

    @Test
    @Config(qualifiers = "en-rUS")
    public void theOverviewBarAxisStaysLatinInEnglish() {
        assertEquals("the overview bar chart period number is not the Latin one in English",
                "1", barAxisLabel(overviewPage(0)));
    }

}
