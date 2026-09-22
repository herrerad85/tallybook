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
import com.oriondev.moneywallet.model.Money;
import com.oriondev.moneywallet.model.PeriodDetailSummaryData;
import com.oriondev.moneywallet.model.PeriodMoney;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The summary screen's two bar series leave transfers out and the figure beside each period
 * counts them, so a period that only moved money between the user's own wallets draws both bars
 * at nothing over a net that moved. The Transfers line is what says why, and a period that moved
 * none must not grow one, because that is nearly every period and the row is one line.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-xhdpi")
public class PeriodSummaryTransferRowTest {

    private static final String USD = "USD";

    @Test
    public void aPeriodThatMovedMoneyNamesItAndSaysWhichWay() {
        // earned, sent and received, so the net and the Transfers figure are different numbers
        // and each assertion below can only be met by the one it names
        PeriodMoney period = new PeriodMoney(new Date(0), new Date(0));
        period.addIncome(USD, 100000);
        period.addTransfer(USD, 40000, false);
        period.addTransfer(USD, 10000, true);
        View row = bind(period);

        View transfer = row.findViewById(R.id.transfer_text_view);
        assertEquals(View.VISIBLE, transfer.getVisibility());
        String text = ((android.widget.TextView) transfer).getText().toString();
        assertTrue("the Transfers line read " + text, text.startsWith("Transfers "));
        assertTrue("the Transfers line read " + text, text.contains("-300.00"));

        View name = row.findViewById(R.id.name_text_view);
        View money = row.findViewById(R.id.money_text_view);
        String net = ((android.widget.TextView) money).getText().toString();
        // the net counts a transfer in both directions, because a wallet that sent money holds
        // less of it afterwards and one that received it holds more
        assertTrue("the net read " + net, net.contains("700.00"));
        assertEquals("the name is not centered on the amount",
                money.getTop() + money.getBottom(), name.getTop() + name.getBottom());
    }

    /**
     * The row was a fixed height before the Transfers line went in and is a wrap_content with
     * a minimum now, so a period with no transfers has to come out at exactly the height it had.
     */
    @Test
    public void aPeriodThatMovedNothingStaysOneLine() {
        PeriodMoney period = new PeriodMoney(new Date(0), new Date(0));
        period.addExpense(USD, 40000);
        View row = bind(period);

        assertEquals(View.GONE, row.findViewById(R.id.transfer_text_view).getVisibility());
        int oneLine = row.getResources().getDimensionPixelSize(
                R.dimen.material_component_lists_one_line_with_avatar_height);
        assertEquals(oneLine, row.getMeasuredHeight());

        // and it sits where a one line row sits. The amount shares a chain with the Transfers
        // line, so room kept for that line on one side only leaves the whole row hanging low
        // against every other list in the app, on the periods that are most of them.
        View money = row.findViewById(R.id.money_text_view);
        int offCenter = (money.getTop() + money.getBottom()) - row.getMeasuredHeight();
        assertTrue("the amount is " + offCenter + " off the row's center",
                Math.abs(offCenter) <= 1);
    }

    /**
     * A period that ended down prints its minus. With the plus and minus setting off, which is
     * the default, a tinted amount drops the sign and leaves the color to carry it, on a figure
     * the Transfers line below it signs unconditionally.
     */
    @Test
    public void aPeriodThatEndedDownPrintsItsMinus() {
        PeriodMoney period = new PeriodMoney(new Date(0), new Date(0));
        period.addExpense(USD, 29236);
        View row = bind(period);

        String text = ((android.widget.TextView) row.findViewById(
                R.id.money_text_view)).getText().toString();
        assertTrue("the net read " + text, text.contains("-292.36"));
    }

    /**
     * The name takes the room the amount leaves, so at the largest system font on a narrow
     * screen it wraps to more lines than the amount has. The Transfers line is right aligned
     * across the whole row, so a name allowed to overflow its own band is drawn straight
     * through it and both are unreadable.
     */
    @Test
    @Config(qualifiers = "de-rDE-w320dp-420dpi", fontScale = 2.0f)
    public void aWrappedNameIsNotDrawnOverTheTransfersLine() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, Calendar.SEPTEMBER, 1, 0, 0, 0);
        Date start = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, 29);
        PeriodMoney period = new PeriodMoney(start, calendar.getTime());
        period.addIncome(USD, 100000);
        period.addTransfer(USD, 40000, false);
        period.addTransfer(USD, 10000, true);
        View row = bind(period);

        TextView name = row.findViewById(R.id.name_text_view);
        TextView transfer = row.findViewById(R.id.transfer_text_view);
        assertEquals(View.VISIBLE, transfer.getVisibility());
        Layout nameLayout = name.getLayout();
        assertTrue("the name took one line, so this cannot see the defect",
                nameLayout.getLineCount() > 1);

        int nameLastBottom = name.getTop() + name.getPaddingTop()
                + nameLayout.getLineBottom(nameLayout.getLineCount() - 1);
        int transferTextTop = transfer.getTop() + transfer.getPaddingTop()
                + transfer.getLayout().getLineTop(0);
        assertTrue("the name's last line ends at " + nameLastBottom
                        + " and the Transfers line starts at " + transferTextTop,
                nameLastBottom <= transferTextTop);
    }

    @Test
    public void aPeriodWhoseTransfersCancelledStaysOneLine() {
        PeriodMoney period = new PeriodMoney(new Date(0), new Date(0));
        period.addTransfer(USD, 40000, false);
        period.addTransfer(USD, 40000, true);
        View row = bind(period);

        assertEquals(View.GONE, row.findViewById(R.id.transfer_text_view).getVisibility());
    }

    /**
     * A Total wallet over several currencies joins them into one amount wide enough to take the
     * whole row, and the name is what gives way. Left to itself it resolves to nothing wide and
     * lays its date range out one character per line, which on a phone is a row taller than the
     * screen. The amount is the one that has to give.
     */
    @Test
    public void aMultiCurrencyAmountDoesNotSqueezeThePeriodNameAway() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, Calendar.SEPTEMBER, 1, 0, 0, 0);
        Date start = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, 29);
        PeriodMoney period = new PeriodMoney(start, calendar.getTime());
        period.addIncome(USD, 123456789);
        period.addIncome("EUR", 123456789);
        period.addIncome("GBP", 123456789);
        View row = bind(period);

        TextView name = row.findViewById(R.id.name_text_view);
        int floor = (int) (80 * row.getResources().getDisplayMetrics().density);
        assertTrue("the period name is " + name.getMeasuredWidth() + "px wide",
                name.getMeasuredWidth() >= floor);

        // three lines of name is the worst this fixture reaches, and the row was 873px here
        // before the amount was made to give way
        int oneLine = row.getResources().getDimensionPixelSize(
                R.dimen.material_component_lists_one_line_with_avatar_height);
        assertTrue("the row is " + row.getMeasuredHeight() + "px tall",
                row.getMeasuredHeight() <= 3 * oneLine);

        // and it gives way by being cut, not by hanging off the end of the row, which draws
        // the same number with its own ellipsis clipped away
        TextView money = row.findViewById(R.id.money_text_view);
        assertTrue("the amount ends at " + money.getRight() + " on a row "
                        + row.getMeasuredWidth() + " wide",
                money.getRight() <= row.getMeasuredWidth());
        assertTrue("the amount drew no ellipsis",
                money.getLayout().getEllipsisCount(0) > 0);
    }

    /**
     * The Transfers line spans the whole row and is aligned to the far side, so it only reaches
     * the near one when it is long enough to be cut, which a Total wallet over several currencies
     * makes it. It then starts inside the margin every other line on the row starts at. Right to
     * left is the worse half and it is not only the long lines, the end margin is the only one
     * that survives the direction swap, so the line runs to the screen edge on every period.
     */
    @Test
    @Config(qualifiers = "w320dp-420dpi")
    public void theTransfersLineStartsWhereThePeriodNameStarts() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, Calendar.SEPTEMBER, 1, 0, 0, 0);
        Date start = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, 29);
        PeriodMoney period = new PeriodMoney(start, calendar.getTime());
        period.addTransfer(USD, 123456789, false);
        period.addTransfer("EUR", 123456789, false);
        period.addTransfer("GBP", 123456789, false);
        View row = bind(period);

        TextView name = row.findViewById(R.id.name_text_view);
        TextView transfer = row.findViewById(R.id.transfer_text_view);
        assertTrue("the fixture left the line short enough to fit, so this cannot see the defect",
                transfer.getLayout().getEllipsisCount(0) > 0);
        int textStart = transfer.getLeft() + (int) transfer.getLayout().getLineLeft(0);
        assertTrue("the Transfers line starts at " + textStart + " and the period name at "
                + name.getLeft(), textStart >= name.getLeft());

        // the same margin on the other side once the direction swaps, where the line reaches the
        // edge whatever its length, so the view's own edge is what there is to assert
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        measure(row);
        assertEquals("the Transfers line ends at " + transfer.getRight()
                + " and the period name at " + name.getRight(),
                name.getRight(), transfer.getRight());
    }

    private View bind(PeriodMoney period) {
        List<PeriodMoney> periods = Collections.singletonList(period);
        PeriodDetailSummaryData data = new PeriodDetailSummaryData(
                new Money(), Collections.<com.github.mikephil.charting.data.BarData>emptyList(),
                Collections.<com.oriondev.moneywallet.model.CurrencyUnit>emptyList(), periods);
        PeriodDetailSummaryAdapter adapter = new PeriodDetailSummaryAdapter(null);
        adapter.setData(data);
        FrameLayout parent = new FrameLayout(new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.MoneyWalletAppTheme));
        PeriodDetailSummaryAdapter.ViewHolder holder = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(holder, 0);
        return measure(holder.itemView);
    }

    private View measure(View row) {
        int widthPx = row.getResources().getDisplayMetrics().widthPixels;
        row.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        row.layout(0, 0, row.getMeasuredWidth(), row.getMeasuredHeight());
        return row;
    }
}
