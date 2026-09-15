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

import android.content.Intent;
import android.database.MatrixCursor;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.wrapper.DebtHeaderCursor;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;
import com.oriondev.moneywallet.ui.activity.NewEditTransactionActivity;
import com.oriondev.moneywallet.ui.adapter.recycler.DebtCursorAdapter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

/**
 * The debt card's own half of settling in full, which is the choice of which of its four buttons
 * a row shows and what the button a user taps sends to the editor. The amount the editor then
 * opens on is decided and pinned elsewhere, and nothing but the card decides these two things.
 *
 * A row is built here the way the list builds one, so a visibility rule that offered settling in
 * full on a debt already paid off, or a button wired to the wrong action constant, fails here.
 * Every visibility case reads all four buttons, so a rule that turned one on for the wrong kind
 * of row cannot pass by going unread.
 */
@RunWith(RobolectricTestRunner.class)
public class DebtCardButtonsTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";
    private static final String TAG_FRAGMENT = "DebtCardButtonsTest::Fragment";

    private static final long DEBT_ID = 42L;
    private static final long SECOND_DEBT_ID = 43L;
    private static final long THIRD_DEBT_ID = 44L;

    /** The projection DebtListFragment loads, in its order, ending on the settled expression. */
    private static final String[] COLUMNS = new String[] {
            Contract.Debt.ID,
            Contract.Debt.TYPE,
            Contract.Debt.ICON,
            Contract.Debt.DESCRIPTION,
            Contract.Debt.MONEY,
            Contract.Debt.PROGRESS,
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

    // ---- visibility -------------------------------------------------------------------------

    @Test
    public void aDebtStillBeingPaidOffOffersPayAndPayInFull() {
        onCard(Contract.DebtType.DEBT, 10000L, -2500L, false, 1, (activity, card) ->
                // a debt's payments sum negative and the card reads them as an amount paid
                assertButtons(card, View.VISIBLE, View.VISIBLE, View.GONE, View.GONE));
    }

    @Test
    public void aDebtPaidOffKeepsPayAndDropsPayInFull() {
        onCard(Contract.DebtType.DEBT, 10000L, -10000L, true, 1, (activity, card) ->
                assertButtons(card, View.VISIBLE, View.GONE, View.GONE, View.GONE));
    }

    @Test
    public void aDebtPaidPastItsTargetDropsPayInFull() {
        onCard(Contract.DebtType.DEBT, 10000L, -12000L, true, 1, (activity, card) ->
                assertButtons(card, View.VISIBLE, View.GONE, View.GONE, View.GONE));
    }

    @Test
    public void aCreditStillBeingRepaidOffersReceiveAndReceiveInFull() {
        onCard(Contract.DebtType.CREDIT, 10000L, 2500L, false, 1, (activity, card) ->
                assertButtons(card, View.GONE, View.GONE, View.VISIBLE, View.VISIBLE));
    }

    @Test
    public void aCreditRepaidKeepsReceiveAndDropsReceiveInFull() {
        onCard(Contract.DebtType.CREDIT, 10000L, 10000L, true, 1, (activity, card) ->
                assertButtons(card, View.GONE, View.GONE, View.VISIBLE, View.GONE));
    }

    @Test
    public void aDebtWithNoPaymentYetOffersPayAndPayInFull() {
        // the provider sums payments through a LEFT JOIN, so a debt nobody has paid against
        // reaches the card with no progress at all
        onCard(Contract.DebtType.DEBT, 10000L, null, false, 1, (activity, card) ->
                assertButtons(card, View.VISIBLE, View.VISIBLE, View.GONE, View.GONE));
    }

    // ---- what each button sends the editor --------------------------------------------------

    @Test
    public void payInFullOnADebtAsksTheEditorToSettleInFull() {
        onCard(Contract.DebtType.DEBT, 10000L, -2500L, false, 2, (activity, card) -> {
            card.findViewById(R.id.pay_in_full_button).performClick();
            assertEditorOpened(activity, NewEditTransactionActivity.DEBT_PAY_IN_FULL);
        });
    }

    @Test
    public void receiveInFullOnACreditAsksTheEditorToSettleInFull() {
        onCard(Contract.DebtType.CREDIT, 10000L, 2500L, false, 2, (activity, card) -> {
            card.findViewById(R.id.receive_in_full_button).performClick();
            assertEditorOpened(activity, NewEditTransactionActivity.DEBT_RECEIVE_IN_FULL);
        });
    }

    @Test
    public void payOnADebtStillAsksForAPlainPayment() {
        onCard(Contract.DebtType.DEBT, 10000L, -2500L, false, 2, (activity, card) -> {
            card.findViewById(R.id.pay_button).performClick();
            assertEditorOpened(activity, NewEditTransactionActivity.DEBT_PAY);
        });
    }

    @Test
    public void receiveOnACreditStillAsksForAPlainRepayment() {
        onCard(Contract.DebtType.CREDIT, 10000L, 2500L, false, 2, (activity, card) -> {
            card.findViewById(R.id.receive_button).performClick();
            assertEditorOpened(activity, NewEditTransactionActivity.DEBT_RECEIVE);
        });
    }

    // ---- the card under a real listener -----------------------------------------------------

    /**
     * Lays out three rows of the given kind and hands the card at that position to the check. The
     * listener is a real DebtListFragment attached to an activity, since the click handlers build
     * their intent from the activity they are hosted by. Position 0 holds the group header,
     * then positions 1, 2 and 3 the cards for DEBT_ID, SECOND_DEBT_ID and THIRD_DEBT_ID, for
     * settled rows as much as for ones that still need attention.
     */
    private void onCard(Contract.DebtType debtType, long money, Long progress, boolean settled,
                        int position, CardCheck check) {
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                DebtListFragment fragment = DebtListFragment.newInstance(debtType);
                activity.getSupportFragmentManager()
                        .beginTransaction()
                        .add(android.R.id.content, fragment, TAG_FRAGMENT)
                        .commitNow();
                DebtCursorAdapter adapter = new DebtCursorAdapter(fragment);
                adapter.changeCursor(new DebtHeaderCursor(
                        threeRows(debtType, money, progress, settled), debtType));
                check.run(activity, cardOf(adapter, position));
            });
        }
    }

    private interface CardCheck {

        void run(BackupListActivity activity, View card);
    }

    /**
     * Three rows alike but for their id, grouping under a single header. The intent cases tap the
     * middle one, so a holder that read the first row, the last row, or the row the layout pass
     * bound last instead of the row that was tapped carries a different id and fails.
     */
    private static MatrixCursor threeRows(Contract.DebtType debtType, long money, Long progress,
                                          boolean settled) {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        for (long id : new long[] {DEBT_ID, SECOND_DEBT_ID, THIRD_DEBT_ID}) {
            cursor.addRow(new Object[] {
                    id, debtType.getValue(), ICON, "Rent", money, progress, "EUR", null, 0,
                    null, null, settled ? 1 : 0
            });
        }
        return cursor;
    }

    private static View cardOf(DebtCursorAdapter adapter, int position) {
        RecyclerView recyclerView = new RecyclerView(new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.MoneyWalletAppTheme));
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        recyclerView.setAdapter(adapter);
        recyclerView.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
        recyclerView.layout(0, 0, 1080, 2400);
        View card = recyclerView.getLayoutManager().findViewByPosition(position);
        assertNotNull("nothing was laid out where the card belongs", card);
        return card;
    }

    private static void assertButtons(View card, int pay, int payInFull, int receive,
                                      int receiveInFull) {
        assertEquals("pay_button", pay, card.findViewById(R.id.pay_button).getVisibility());
        assertEquals("pay_in_full_button", payInFull,
                card.findViewById(R.id.pay_in_full_button).getVisibility());
        assertEquals("receive_button", receive,
                card.findViewById(R.id.receive_button).getVisibility());
        assertEquals("receive_in_full_button", receiveInFull,
                card.findViewById(R.id.receive_in_full_button).getVisibility());
    }

    private static void assertEditorOpened(BackupListActivity activity, int debtAction) {
        Intent next = shadowOf(activity).getNextStartedActivity();
        assertNotNull("the tap started nothing", next);
        assertEquals(NewEditTransactionActivity.class.getName(),
                next.getComponent().getClassName());
        assertEquals("TYPE", NewEditTransactionActivity.TYPE_DEBT,
                next.getIntExtra(NewEditTransactionActivity.TYPE, -1));
        assertEquals("DEBT_ID", SECOND_DEBT_ID,
                next.getLongExtra(NewEditTransactionActivity.DEBT_ID, -1L));
        assertEquals("DEBT_ACTION", debtAction,
                next.getIntExtra(NewEditTransactionActivity.DEBT_ACTION, -1));
    }
}
