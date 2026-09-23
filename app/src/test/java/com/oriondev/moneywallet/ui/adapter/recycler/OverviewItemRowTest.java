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

package com.oriondev.moneywallet.ui.adapter.recycler;

import android.text.Layout;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.OverviewData;
import com.oriondev.moneywallet.model.PeriodMoney;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Arrays;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A row of the overview list, one period and its net. A net over several currencies prints one
 * per line, and a net too wide for the row is cut where a reader can see it.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-xhdpi")
public class OverviewItemRowTest {

    @Test
    public void everyCurrencyTakesALineAndTheNextPeriodGetsOneBack() {
        PeriodMoney single = new PeriodMoney(new Date(0), new Date(0));
        single.addIncome("USD", 12345);
        OverviewItemAdapter adapter = adapter(single);
        int fresh = measure(bind(adapter, null, 0).itemView).getMeasuredHeight();

        PeriodMoney stacked = new PeriodMoney(new Date(0), new Date(0));
        stacked.addIncome("USD", 100000);
        stacked.addIncome("EUR", 100000);
        stacked.addIncome("GBP", 100000);
        adapter = adapter(stacked, single);
        OverviewItemAdapter.ViewHolder holder = bind(adapter, null, 0);
        View row = measure(holder.itemView);
        TextView name = row.findViewById(R.id.name_text_view);
        TextView money = row.findViewById(R.id.money_text_view);
        Layout layout = money.getLayout();
        assertEquals("the net took " + layout.getLineCount() + " lines for three currencies",
                3, layout.getLineCount());
        assertEquals("the net was cut", 0, layout.getEllipsisCount(2));
        assertEquals(money.getTop(), name.getTop());
        assertEquals(money.getHeight(), name.getHeight());

        // the adapter binds the next period into the row it drew this one in
        bind(adapter, holder, 1);
        assertEquals(fresh, measure(holder.itemView).getMeasuredHeight());
    }

    /**
     * The name keeps an 80dp floor, so a net wider than what is left has to be the one that
     * gives way, and it has to do it inside the row, not past the edge of the screen.
     */
    @Test
    @Config(qualifiers = "w320dp-xhdpi", fontScale = 2.0f)
    public void aWideNetIsCutInsideTheRow() {
        PeriodMoney period = new PeriodMoney(new Date(0), new Date(0));
        period.addIncome("USD", 123456789012L);
        View row = measure(bind(adapter(period), null, 0).itemView);

        TextView name = row.findViewById(R.id.name_text_view);
        TextView money = row.findViewById(R.id.money_text_view);
        assertTrue("the name starts at " + name.getLeft(), name.getLeft() >= 0);
        assertTrue("the net ends at " + money.getRight() + " on a row "
                + row.getMeasuredWidth() + " wide", money.getRight() <= row.getMeasuredWidth());
        assertTrue("the net drew no ellipsis", money.getLayout().getEllipsisCount(0) > 0);
    }

    private static OverviewItemAdapter adapter(PeriodMoney... periods) {
        OverviewItemAdapter adapter = new OverviewItemAdapter(null);
        adapter.setData(new OverviewData(null, null, null, Arrays.asList(periods)));
        return adapter;
    }

    private static OverviewItemAdapter.ViewHolder bind(OverviewItemAdapter adapter,
                                                       OverviewItemAdapter.ViewHolder holder,
                                                       int position) {
        if (holder == null) {
            FrameLayout parent = new FrameLayout(new ContextThemeWrapper(
                    ApplicationProvider.getApplicationContext(), R.style.MoneyWalletAppTheme));
            holder = adapter.onCreateViewHolder(parent, 0);
        }
        adapter.onBindViewHolder(holder, position);
        return holder;
    }

    private static View measure(View row) {
        int widthPx = row.getResources().getDisplayMetrics().widthPixels;
        row.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        row.layout(0, 0, row.getMeasuredWidth(), row.getMeasuredHeight());
        return row;
    }
}
