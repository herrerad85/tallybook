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

import java.util.Date;

import static org.junit.Assert.assertEquals;

/**
 * The Transfers figure has to say which way the money went. The plus and minus setting is off by
 * default, and a tinted amount drops its sign when it is, so a tinted Transfers figure printed
 * the same string for money coming in and money going out and left the color to tell them apart.
 * The words Incomes and Expenses carry the direction themselves; the word Transfers does not.
 *
 * A period that moved nothing has to say nothing, so the case where the figure comes to zero is
 * here too.
 */
@RunWith(RobolectricTestRunner.class)
public class HeaderTransferSignTest {

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
    public void aTransferOutOfTheWalletIsShownWithItsMinus() {
        assertEquals("€ -250.00", transferFigureFor(Contract.Direction.EXPENSE));
    }

    @Test
    public void aTransferIntoTheWalletIsShownWithoutOne() {
        assertEquals("€ 250.00", transferFigureFor(Contract.Direction.INCOME));
    }

    /**
     * A period whose transfers cancelled grows no line. Both sides of a transfer between two
     * wallets counted in the total land on that list, so the figure comes to zero while still
     * holding a currency, and that is what the Total wallet shows on nearly every period. The
     * row is one line and a Transfers line reading nothing is worse than none.
     */
    @Test
    public void aPeriodWhoseTransfersCancelledGrowsNoLine() {
        View header = bindHeader(
                transferRow(1L, Contract.Direction.EXPENSE),
                transferRow(2L, Contract.Direction.INCOME),
                ordinaryRow(3L));

        assertEquals(View.GONE,
                header.findViewById(R.id.transfer_summary_layout).getVisibility());
    }

    private String transferFigureFor(int direction) {
        TextView amount = bindHeader(transferRow(1L, direction), ordinaryRow(2L))
                .findViewById(R.id.transfer_text_view);
        return amount.getText().toString();
    }

    private Object[] transferRow(long id, int direction) {
        return new Object[] {
                id, "2019-03-15 10:00:00", direction, 25000L, "EUR",
                1, 1, "Transfer", null, Contract.CategoryTag.TRANSFER, "Description"
        };
    }

    /**
     * Every fixture here carries one of these. A header counts every row into the total and a
     * transfer into the Transfers figure as well, so a period holding transfers alone makes the
     * two figures the same number and no assertion can tell which column reached the view.
     */
    private Object[] ordinaryRow(long id) {
        return new Object[] {
                id, "2019-03-15 10:00:00", Contract.Direction.INCOME, 10000L, "EUR",
                1, 1, "Salary", null, null, "Description"
        };
    }

    private View bindHeader(Object[]... rows) {
        MatrixCursor bare = new MatrixCursor(COLUMNS);
        for (Object[] row : rows) {
            bare.addRow(row);
        }
        TransactionCursorAdapter adapter = new TransactionCursorAdapter(new RecordsNothing(), false);
        adapter.changeCursor(new TransactionHeaderCursor(bare, Group.DAILY, null, null));
        // the application context carries the platform theme, not the one the manifest gives the
        // activities, and the row's ripple is an AppCompat attribute that only that one resolves
        FrameLayout parent = new FrameLayout(new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.MoneyWalletAppTheme));
        RecyclerView.ViewHolder header = adapter.onCreateViewHolder(
                parent, TransactionHeaderCursor.TYPE_HEADER);
        adapter.onBindViewHolder(header, 0);
        return header.itemView;
    }

    /** The adapter needs a listener, and none of these cases clicks anything. */
    private static class RecordsNothing implements TransactionCursorAdapter.ActionListener {

        @Override
        public void onHeaderClick(Date startDate, Date endDate) {
            // never called here
        }

        @Override
        public void onTransactionClick(long id) {
            // never called here
        }

        @Override
        public void onSelectionChanged(int count) {
            // never called here
        }
    }
}
