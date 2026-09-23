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

import android.database.MatrixCursor;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Group;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.wrapper.TransactionHeaderCursor;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A Total wallet over several currencies used to join them into one string per figure, and three
 * of those do not fit a header at any width. Each currency now takes a line of its own, each box
 * a line of its own, and the fold arrow moves up to the date it folds. A header whose figures
 * hold one currency each is drawn as it was.
 *
 * Native graphics is what makes these assertions mean anything, since the legacy shadow gives
 * every character one unit of width whatever its glyph.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-xhdpi")
public class HeaderStackedCurrenciesTest {

    private static final String[] COLUMNS = new String[] {
            Contract.Transaction.ID,
            Contract.Transaction.DATE,
            Contract.Transaction.DIRECTION,
            Contract.Transaction.MONEY,
            Contract.Transaction.WALLET_CURRENCY,
            Contract.Transaction.CONFIRMED,
            Contract.Transaction.COUNT_IN_TOTAL,
            Contract.Transaction.CATEGORY_NAME,
            Contract.Transaction.CATEGORY_ICON,
            Contract.Transaction.CATEGORY_TAG,
            Contract.Transaction.DESCRIPTION
    };

    @Test
    public void everyCurrencyIsShownWholeOnALineOfItsOwn() {
        // small amounts, so two boxes would fit on one line if they were allowed to share it
        View row = bindHeader(
                row(1L, Contract.Direction.INCOME, 10000L, "USD", null),
                row(2L, Contract.Direction.INCOME, 2000L, "EUR", null),
                row(3L, Contract.Direction.INCOME, 3000L, "GBP", null),
                row(4L, Contract.Direction.EXPENSE, 1000L, "USD", null),
                row(5L, Contract.Direction.EXPENSE, 500L, "EUR", null),
                row(6L, Contract.Direction.EXPENSE, 700L, "GBP", null),
                row(7L, Contract.Direction.EXPENSE, 500L, "USD", Contract.CategoryTag.TRANSFER),
                row(8L, Contract.Direction.INCOME, 400L, "EUR", Contract.CategoryTag.TRANSFER));

        int[] figures = new int[] {R.id.right_text_view, R.id.income_text_view,
                R.id.expense_text_view, R.id.transfer_text_view};
        int[] currencies = new int[] {3, 3, 3, 2};
        for (int i = 0; i < figures.length; i++) {
            TextView figure = row.findViewById(figures[i]);
            String name = row.getResources().getResourceEntryName(figures[i]);
            assertEquals(name + " read " + figure.getText(),
                    currencies[i], figure.getLayout().getLineCount());
            assertEquals(name + " was cut", 0,
                    figure.getLayout().getEllipsisCount(currencies[i] - 1));
        }

        // each box on a line of its own, in order
        View income = row.findViewById(R.id.income_summary_layout);
        View expense = row.findViewById(R.id.expense_summary_layout);
        View transfer = row.findViewById(R.id.transfer_summary_layout);
        assertTrue(income.getBottom() <= expense.getTop());
        assertTrue(expense.getBottom() <= transfer.getTop());

        // the total is not drawn over the summary under it
        TextView total = row.findViewById(R.id.right_text_view);
        View summary = (View) income.getParent();
        assertTrue("the total ends at " + total.getBottom() + " and the summary starts at "
                + summary.getTop(), total.getBottom() <= summary.getTop());

        // the fold arrow sits beside the date, not halfway down a row several lines tall
        View toggle = row.findViewById(R.id.period_toggle_image_view);
        View date = row.findViewById(R.id.left_text_view);
        assertTrue("the arrow spans " + toggle.getTop() + " to " + toggle.getBottom()
                        + " and the date " + date.getTop() + " to " + date.getBottom(),
                toggle.getTop() <= date.getTop() && toggle.getBottom() >= date.getBottom());
    }

    /**
     * The same adapter binds the next period into the row it drew this one in, so a one
     * currency header after a stacked one has to get the whole arrangement back.
     */
    @Test
    public void aOneCurrencyHeaderIsDrawnAsItWas() {
        TransactionCursorAdapter adapter = adapter(
                row(1L, Contract.Direction.INCOME, 10000L, "USD", null),
                row(2L, Contract.Direction.EXPENSE, 2000L, "USD", null));
        View fresh = measure(bind(adapter, null).itemView);
        int[] freshTop = tops(fresh);

        RecyclerView.ViewHolder reused = bind(adapter(
                row(1L, Contract.Direction.INCOME, 123456789L, "USD", null),
                row(2L, Contract.Direction.EXPENSE, 98765432L, "EUR", null)), null);
        View row = measure(bind(adapter, reused).itemView);

        assertEquals(fresh.getMeasuredHeight(), row.getMeasuredHeight());
        int[] rowTop = tops(row);
        for (int i = 0; i < rowTop.length; i++) {
            assertEquals(freshTop[i], rowTop[i]);
        }
        assertEquals(1, ((TextView) row.findViewById(R.id.right_text_view)).getMaxLines());
    }

    /**
     * A figure is capped at one line per currency, so one too wide for its box is cut, and it
     * has to end in an ellipsis. Cut without one it reads as a whole number and a currency it
     * still holds is simply not there.
     */
    @Test
    @Config(qualifiers = "w320dp-xhdpi", fontScale = 2.0f)
    public void aCappedFigureTooWideForItsLinesEndsInAnEllipsis() {
        View row = bindHeader(
                row(1L, Contract.Direction.INCOME, 12345678901L, "USD", null),
                row(2L, Contract.Direction.INCOME, 2345678901L, "EUR", null),
                row(3L, Contract.Direction.INCOME, 345678901L, "GBP", null),
                row(4L, Contract.Direction.EXPENSE, 12345678901L, "USD", null),
                row(5L, Contract.Direction.EXPENSE, 2345678901L, "EUR", null),
                row(6L, Contract.Direction.EXPENSE, 345678901L, "GBP", null));

        for (int id : new int[] {R.id.income_text_view, R.id.expense_text_view}) {
            TextView figure = row.findViewById(id);
            String name = row.getResources().getResourceEntryName(id);
            assertEquals(name + " was laid out past its cap", 3, figure.getLayout().getLineCount());
            assertTrue(name + " drew no ellipsis", figure.getLayout().getEllipsisCount(2) > 0);
        }
    }

    /**
     * The list of transactions opens a period's report from its header, and that arrow has to
     * stay beside the fold arrow when the row is several lines tall, not halfway down it.
     */
    @Test
    public void theReportArrowStaysBesideTheFoldArrow() {
        View row = measure(bind(adapter(true,
                row(1L, Contract.Direction.INCOME, 10000L, "USD", null),
                row(2L, Contract.Direction.INCOME, 2000L, "EUR", null),
                row(3L, Contract.Direction.INCOME, 3000L, "GBP", null)), null).itemView);

        View toggle = row.findViewById(R.id.period_toggle_image_view);
        View report = row.findViewById(R.id.report_image_view);
        assertEquals(View.VISIBLE, report.getVisibility());
        int toggleCenter = toggle.getTop() + toggle.getBottom();
        assertTrue("the fold arrow was not lifted, so this cannot see the defect",
                row.getMeasuredHeight() - toggleCenter > 2);
        int offset = (report.getTop() + report.getBottom()) - toggleCenter;
        assertTrue("the report arrow is " + offset / 2 + "px off the fold arrow",
                Math.abs(offset) <= 2);
    }

    private static int[] tops(View row) {
        int[] ids = new int[] {R.id.period_toggle_image_view, R.id.income_summary_layout,
                R.id.expense_summary_layout, R.id.right_text_view};
        int[] tops = new int[ids.length];
        for (int i = 0; i < ids.length; i++) {
            View view = row.findViewById(ids[i]);
            tops[i] = view.getTop() + ((View) view.getParent()).getTop();
        }
        return tops;
    }

    private static Object[] row(long id, int direction, long money, String currency, String tag) {
        return new Object[] {
                id, "2019-03-15 10:00:00", direction, money, currency,
                1, 1, "Category", null, tag, "Description"
        };
    }

    private static View bindHeader(Object[]... rows) {
        return measure(bind(adapter(rows), null).itemView);
    }

    private static TransactionCursorAdapter adapter(Object[]... rows) {
        return adapter(false, rows);
    }

    private static TransactionCursorAdapter adapter(boolean opensReport, Object[]... rows) {
        MatrixCursor bare = new MatrixCursor(COLUMNS);
        for (Object[] row : rows) {
            bare.addRow(row);
        }
        TransactionCursorAdapter adapter = new TransactionCursorAdapter(null, opensReport);
        adapter.changeCursor(new TransactionHeaderCursor(bare, Group.DAILY, null, null));
        return adapter;
    }

    private static RecyclerView.ViewHolder bind(TransactionCursorAdapter adapter,
                                                RecyclerView.ViewHolder holder) {
        if (holder == null) {
            // the application context carries the platform theme, and the row's ripple is an
            // AppCompat attribute that only the app theme resolves
            FrameLayout parent = new FrameLayout(new ContextThemeWrapper(
                    ApplicationProvider.getApplicationContext(), R.style.MoneyWalletAppTheme));
            holder = adapter.onCreateViewHolder(parent, TransactionHeaderCursor.TYPE_HEADER);
        }
        adapter.onBindViewHolder(holder, 0);
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
