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

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
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
 * The month header's summary boxes have to stay inside the room their Flow was given.
 * ConstraintLayout measures a wrap content child against the whole row, and the Flow draws it at
 * whatever width came back, so without a container bounding them a wide box runs under the fold
 * arrow and the report arrow and ellipsizes only at the far edge of the row.
 *
 * Native graphics is what makes these assertions mean anything, since the legacy shadow gives
 * every character one unit of width whatever its glyph.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-xhdpi")
public class HeaderSummaryWidthTest {

    private static final String THREE_CURRENCIES =
            "$ 1,234,567.89 - € 987,654.32 - £ 121,212.00";

    @Test
    public void aSummaryBoxTooWideForTheRowStaysInsideTheFlowAndSaysItWasCut() {
        onHeaderRow(row -> {
            ((TextView) row.findViewById(R.id.income_text_view)).setText(THREE_CURRENCIES);
            ((TextView) row.findViewById(R.id.expense_text_view)).setText("$ 1.00");
            row.findViewById(R.id.transfer_summary_layout).setVisibility(View.GONE);
        }, row -> {
            View incomeBox = row.findViewById(R.id.income_summary_layout);
            View toggle = row.findViewById(R.id.period_toggle_image_view);
            TextView amount = row.findViewById(R.id.income_text_view);

            // checked against the fold arrow for the reason the three box case below gives
            int endsAt = ((View) incomeBox.getParent()).getLeft() + incomeBox.getRight();
            assertTrue("the box ends at " + endsAt + " and the fold arrow starts at "
                            + toggle.getLeft() + ", so the arrow is painted over it",
                    endsAt <= toggle.getLeft());
            assertTrue("the box overflowed with nothing saying so",
                    amount.getLayout().getEllipsisCount(0) > 0);
        });
    }

    /**
     * The word is never cut, because the weight in a box sits on the figure alone and an
     * unweighted child keeps the width it asked for. Weighting the word instead took the room off
     * the word until it reached 0px, where the platform drops the view from the accessibility tree
     * and draws nothing, leaving the row as three numbers a reader cannot tell apart. Weighting
     * both split the shortfall and cut the word to one letter and an ellipsis. A system font of
     * 1.5 with a Total wallet in several currencies is the box that showed all three.
     */
    @Test
    @Config(qualifiers = "w360dp-xhdpi", fontScale = 1.5f)
    public void theWordIsNeverCutBecauseTheWeightSitsOnTheFigure() {
        onHeaderRow(row -> {
            ((TextView) row.findViewById(R.id.income_text_view)).setText(THREE_CURRENCIES);
            ((TextView) row.findViewById(R.id.expense_text_view)).setText(THREE_CURRENCIES);
            ((TextView) row.findViewById(R.id.transfer_text_view)).setText(THREE_CURRENCIES);
        }, row -> {
            int[] labels = new int[] {R.id.income_label_text_view, R.id.expense_label_text_view,
                    R.id.transfer_label_text_view};
            for (int id : labels) {
                TextView label = row.findViewById(id);
                int cut = label.getLayout().getEllipsisCount(0);
                assertTrue(row.getResources().getResourceEntryName(id) + " is "
                                + label.getWidth() + "px wide with " + cut + " characters cut,"
                                + " so the word is not whole",
                        label.getWidth() > 0 && cut == 0);
            }
        });
    }

    /**
     * Three boxes at once, which is the configuration the row was rebuilt for and the one the
     * case above cannot reach, since it hides the third. The Transfers box appears on a Total
     * wallet holding wallets in more than one currency, because the two sides of a transfer
     * between them do not cancel, and that is the same wallet whose figures are each several
     * currencies joined into one string. So all three are wide together, the Flow gives each a
     * line of its own, and not one of them may reach the fold arrow.
     */
    @Test
    public void everyBoxEndsBeforeTheFoldArrowWhenTheTransfersOneIsShownToo() {
        onHeaderRow(row -> {
            ((TextView) row.findViewById(R.id.income_text_view)).setText(THREE_CURRENCIES);
            ((TextView) row.findViewById(R.id.expense_text_view)).setText(THREE_CURRENCIES);
            ((TextView) row.findViewById(R.id.transfer_text_view)).setText(THREE_CURRENCIES);
        }, row -> {
            // checked against the fold arrow and not against the Flow, because an unbounded
            // Flow grows with the box that overflowed it, so a box checked against the Flow can
            // never come out wider than it. The arrow is drawn after the boxes and paints over
            // them
            View toggle = row.findViewById(R.id.period_toggle_image_view);
            int[] boxes = new int[] {R.id.income_summary_layout, R.id.expense_summary_layout,
                    R.id.transfer_summary_layout};
            for (int id : boxes) {
                View box = row.findViewById(id);
                int endsAt = ((View) box.getParent()).getLeft() + box.getRight();
                assertTrue(row.getResources().getResourceEntryName(id) + " ends at " + endsAt
                                + " and the fold arrow starts at " + toggle.getLeft()
                                + ", so the arrow is painted over it",
                        endsAt <= toggle.getLeft());
            }
        });
    }

    private static void onHeaderRow(Check fill, Check check) {
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                View row = inflate(activity);
                fill.run(row);
                int widthPx = activity.getResources().getDisplayMetrics().widthPixels;
                row.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                row.layout(0, 0, row.getMeasuredWidth(), row.getMeasuredHeight());
                check.run(row);
            });
        }
    }

    private static View inflate(Activity activity) {
        return LayoutInflater.from(activity)
                .inflate(R.layout.adapter_transaction_header_item, null);
    }

    private interface Check {

        void run(View row);
    }
}
