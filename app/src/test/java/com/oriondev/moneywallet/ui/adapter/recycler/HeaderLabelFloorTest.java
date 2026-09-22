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

import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.assertTrue;

/**
 * Both header rows put a label against an amount that has no width of its own to give up. A Total
 * wallet joins every currency it holds into one amount, and an amount that wide takes the whole
 * row and leaves the label nothing, so the header stops saying what it is a header for. The floor
 * is what stops that, and the label is the figure a reader needs to make sense of the rest.
 *
 * Native graphics is what makes these assertions mean anything, since the legacy shadow gives
 * every character one unit of width whatever its glyph.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HeaderLabelFloorTest {

    private static final String THREE_CURRENCIES =
            "$ 1,234,567.89 - € 987,654.32 - £ 121,212.00";

    private static final int FLOOR_DP = 80;

    @Test
    @Config(qualifiers = "w360dp-xhdpi")
    public void theTransactionsListHeaderStillSaysWhichPeriodItCovers() {
        onRow(R.layout.adapter_transaction_header_item, row -> {
            ((TextView) row.findViewById(R.id.left_text_view)).setText("September 1 - 30");
            ((TextView) row.findViewById(R.id.right_text_view)).setText(THREE_CURRENCIES);
            row.findViewById(R.id.transfer_summary_layout).setVisibility(View.GONE);
        }, row -> {
            TextView date = row.findViewById(R.id.left_text_view);
            assertTrue("the date range is " + date.getWidth() + "px wide, and a header that "
                            + "cannot say which period it covers is a header of nothing",
                    date.getWidth() >= floorPx(row));
            sitsBesideTheAmount(row, date, "date range");
            View summary = (View) row.findViewById(R.id.income_summary_layout).getParent();
            TextView amount = row.findViewById(R.id.right_text_view);
            assertTrue("the amount ends " + amount.getBottom() + "px down and the summary starts "
                            + summary.getTop() + "px down, so the two are drawn over each other",
                    amount.getBottom() <= summary.getTop());
        });
    }

    /**
     * The shared row at the largest system font, which is the case the floor alone does not
     * carry. Its height is fixed, so a label or an amount that takes two lines has the second one
     * drawn outside the row and lost, and a reader is given no sign that anything is missing.
     */
    @Test
    @Config(qualifiers = "w320dp-420dpi", fontScale = 2.0f)
    public void neitherSideOfTheSharedHeaderIsDrawnOutsideIt() {
        onRow(R.layout.adapter_header_item, row -> {
            ((TextView) row.findViewById(R.id.left_text_view)).setText("Net incomes");
            ((TextView) row.findViewById(R.id.right_text_view)).setText(THREE_CURRENCIES);
        }, row -> {
            TextView label = row.findViewById(R.id.left_text_view);
            assertTrue("the label is " + label.getWidth() + "px wide, so the amount beside it "
                            + "took the whole row", label.getWidth() >= floorPx(row));
            sitsBesideTheAmount(row, label, "label");
            fitsInsideTheRow(row, label, "label");
            fitsInsideTheRow(row, row.findViewById(R.id.right_text_view), "amount");
        });
    }

    /**
     * A width the label is given is not a width a reader gets. An amount anchored by its end alone
     * is bounded by nothing, so it takes its full width off the start of the row and the label is
     * given its floor somewhere off the left edge, underneath the number. Both rows passed on
     * width while a device showed one clipped character.
     */
    private static void sitsBesideTheAmount(View row, TextView label, String name) {
        TextView amount = row.findViewById(R.id.right_text_view);
        assertTrue("the " + name + " starts at " + label.getLeft() + "px, which is off the start "
                + "of the row", label.getLeft() >= 0);
        assertTrue("the " + name + " ends at " + label.getRight() + "px and the amount starts at "
                        + amount.getLeft() + "px, so the two are drawn over each other",
                label.getRight() <= amount.getLeft());
    }

    private static void fitsInsideTheRow(View row, TextView view, String name) {
        int room = view.getHeight() - view.getPaddingTop() - view.getPaddingBottom();
        assertTrue("the " + name + " needs " + view.getLayout().getHeight() + "px and has "
                        + room + "px in a row of " + row.getHeight() + "px, so the rest of it is "
                        + "drawn outside the row with nothing saying it was cut",
                view.getLayout().getHeight() <= room);
    }

    private static int floorPx(View row) {
        return (int) (FLOOR_DP * row.getResources().getDisplayMetrics().density);
    }

    private static void onRow(int layout, Check fill, Check check) {
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                // into a parent, because a row inflated against none loses the layout_height its
                // own root declares
                FrameLayout parent = new FrameLayout(activity);
                View row = LayoutInflater.from(activity).inflate(layout, parent, false);
                parent.addView(row);
                fill.run(row);
                int widthPx = activity.getResources().getDisplayMetrics().widthPixels;
                parent.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                parent.layout(0, 0, parent.getMeasuredWidth(), parent.getMeasuredHeight());
                check.run(row);
            });
        }
    }

    private interface Check {

        void run(View row);
    }
}
