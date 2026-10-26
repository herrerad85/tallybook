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

package com.oriondev.moneywallet.ui.fragment.primary;

import android.database.MatrixCursor;
import android.text.Layout;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.wrapper.DebtHeaderCursor;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;
import com.oriondev.moneywallet.ui.adapter.recycler.DebtCursorAdapter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DebtCardButtonBarWrapTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";
    private static final String TAG_FRAGMENT = "DebtCardButtonBarWrapTest::Fragment";

    private static final String[] COLUMNS = new String[] {
            Contract.Debt.ID,
            Contract.Debt.TYPE,
            Contract.Debt.ICON,
            Contract.Debt.DESCRIPTION,
            Contract.Debt.MONEY,
            Contract.Debt.PROGRESS,
            Contract.Debt.INTEREST,
            Contract.Debt.WALLET_CURRENCY,
            Contract.Debt.EXPIRATION_DATE,
            Contract.Debt.ARCHIVED,
            Contract.Debt.PLACE_ID,
            Contract.Debt.PLACE_NAME,
            DebtHeaderCursor.COLUMN_IS_SETTLED
    };

    @Before
    public void setUp() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
    }

    @Test
    @Config(qualifiers = "en-w360dp-xhdpi", fontScale = 1.3f)
    public void everyLabelIsWholeInEnglishAtLargeTextOnASmallScreen() {
        assertEveryLabelWhole(Contract.DebtType.DEBT);
        assertEveryLabelWhole(Contract.DebtType.CREDIT);
    }

    @Test
    @Config(qualifiers = "de-w411dp-xxhdpi", fontScale = 2.0f)
    public void everyLabelIsWholeInGermanAtTheLargestText() {
        assertEveryLabelWhole(Contract.DebtType.DEBT);
        assertEveryLabelWhole(Contract.DebtType.CREDIT);
    }

    @Test
    @Config(qualifiers = "ru-w411dp-xxhdpi", fontScale = 2.0f)
    public void everyLabelIsWholeInRussianAtTheLargestText() {
        assertEveryLabelWhole(Contract.DebtType.DEBT);
        assertEveryLabelWhole(Contract.DebtType.CREDIT);
    }

    @Test
    @Config(qualifiers = "fa-w360dp-xhdpi", fontScale = 2.0f)
    public void everyLabelIsWholeRightToLeftAtTheLargestText() {
        assertEveryLabelWhole(Contract.DebtType.DEBT);
        assertEveryLabelWhole(Contract.DebtType.CREDIT);
    }

    @Test
    @Config(qualifiers = "en-w360dp-xhdpi")
    public void buttonsThatFitStayOnOneRowAsHighAsBefore() {
        assertOneRowAgainstTheEnd();
    }

    @Test
    @Config(qualifiers = "fa-w360dp-xhdpi")
    public void buttonsThatFitStayOnOneRowRightToLeft() {
        assertOneRowAgainstTheEnd();
    }

    private void assertOneRowAgainstTheEnd() {
        for (Contract.DebtType debtType : Contract.DebtType.values()) {
            onBar(debtType, bar -> {
                boolean rtl = bar.getResources().getConfiguration().getLayoutDirection()
                        == View.LAYOUT_DIRECTION_RTL;
                List<Button> buttons = visibleButtons(bar);
                assertEquals(debtType + " visible buttons", 3, buttons.size());
                assertEquals(debtType + " bar height", bar.getResources().getDimensionPixelSize(
                        R.dimen.material_component_lists_card_action_button_height), bar.getHeight());
                for (Button button : buttons) {
                    assertEquals(debtType + " " + button.getText() + " top",
                            bar.getPaddingTop(), button.getTop());
                    assertEquals(debtType + " " + button.getText() + " bottom",
                            bar.getHeight() - bar.getPaddingBottom(), button.getBottom());
                }
                int gap = bar.getResources().getDimensionPixelSize(
                        R.dimen.material_component_lists_card_action_button_padding);
                for (int i = 1; i < buttons.size(); i++) {
                    Button before = buttons.get(i - 1);
                    Button after = buttons.get(i);
                    assertEquals(debtType + " gap before " + after.getText(),
                            rtl ? before.getLeft() - gap : before.getRight() + gap,
                            rtl ? after.getRight() : after.getLeft());
                }
                Button last = buttons.get(buttons.size() - 1);
                assertEquals(debtType + " last button against the end",
                        rtl ? bar.getPaddingLeft() : bar.getWidth() - bar.getPaddingRight(),
                        rtl ? last.getLeft() : last.getRight());
            });
        }
    }

    private void assertEveryLabelWhole(Contract.DebtType debtType) {
        onBar(debtType, bar -> {
            List<Button> buttons = visibleButtons(bar);
            assertEquals(debtType + " visible buttons", 3, buttons.size());
            for (Button button : buttons) {
                String name = debtType + " " + button.getText();
                Layout layout = button.getLayout();
                assertEquals(name + " lines", 1, layout.getLineCount());
                assertEquals(name + " ellipsis", 0, layout.getEllipsisCount(0));
                int textWidth = button.getWidth() - button.getCompoundPaddingLeft()
                        - button.getCompoundPaddingRight();
                assertTrue(name + " text of " + layout.getLineWidth(0) + " fits in " + textWidth,
                        layout.getLineWidth(0) <= textWidth);
                int textHeight = button.getHeight() - button.getExtendedPaddingTop()
                        - button.getExtendedPaddingBottom();
                assertTrue(name + " text of height " + layout.getHeight() + " fits in " + textHeight,
                        layout.getHeight() <= textHeight);
                assertTrue(name + " starts at " + button.getLeft() + " inside the bar",
                        button.getLeft() >= bar.getPaddingLeft());
                assertTrue(name + " ends at " + button.getRight() + " inside the bar of "
                        + bar.getWidth(), button.getRight() <= bar.getWidth() - bar.getPaddingRight());
                assertTrue(name + " top at " + button.getTop() + " inside the bar",
                        button.getTop() >= bar.getPaddingTop());
                assertTrue(name + " bottom at " + button.getBottom() + " inside the bar of "
                        + bar.getHeight(), button.getBottom() <= bar.getHeight() - bar.getPaddingBottom());
            }
            for (int i = 0; i < buttons.size(); i++) {
                for (int j = i + 1; j < buttons.size(); j++) {
                    Button a = buttons.get(i);
                    Button b = buttons.get(j);
                    assertTrue(debtType + " " + a.getText() + " overlaps " + b.getText(),
                            a.getRight() <= b.getLeft() || b.getRight() <= a.getLeft()
                                    || a.getBottom() <= b.getTop() || b.getBottom() <= a.getTop());
                }
            }
        });
    }

    private void onBar(Contract.DebtType debtType, BarCheck check) {
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                DebtListFragment fragment = DebtListFragment.newInstance(debtType);
                activity.getSupportFragmentManager()
                        .beginTransaction()
                        .add(android.R.id.content, fragment, TAG_FRAGMENT)
                        .commitNow();
                DebtCursorAdapter adapter = new DebtCursorAdapter(fragment);
                MatrixCursor cursor = new MatrixCursor(COLUMNS);
                long progress = debtType == Contract.DebtType.DEBT ? -2500L : 2500L;
                cursor.addRow(new Object[] {
                        42L, debtType.getValue(), ICON, "Rent", 10000L, progress, null, "EUR",
                        null, 0, null, null, 0
                });
                adapter.changeCursor(new DebtHeaderCursor(cursor, debtType));
                RecyclerView recyclerView = new RecyclerView(new ContextThemeWrapper(
                        activity, R.style.MoneyWalletAppTheme));
                recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
                recyclerView.setAdapter(adapter);
                recyclerView.setLayoutDirection(
                        activity.getResources().getConfiguration().getLayoutDirection());
                int width = activity.getResources().getDisplayMetrics().widthPixels;
                recyclerView.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(width * 4, View.MeasureSpec.EXACTLY));
                recyclerView.layout(0, 0, width, width * 4);
                View card = recyclerView.getLayoutManager().findViewByPosition(1);
                assertTrue("nothing was laid out where the card belongs", card != null);
                check.run(card.findViewById(R.id.button_bar_layout));
            });
        }
    }

    private interface BarCheck {

        void run(ViewGroup bar);
    }

    private static List<Button> visibleButtons(ViewGroup bar) {
        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < bar.getChildCount(); i++) {
            View child = bar.getChildAt(i);
            if (child instanceof Button && child.getVisibility() == View.VISIBLE) {
                buttons.add((Button) child);
            }
        }
        return buttons;
    }
}
