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

import android.database.Cursor;
import android.database.MatrixCursor;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
public class UnconfirmedMarkerTest {

    private static final String[] COLUMNS = new String[] {
            Contract.Transaction.ID,
            Contract.Transaction.TYPE,
            Contract.Transaction.DATE,
            Contract.Transaction.DIRECTION,
            Contract.Transaction.MONEY,
            Contract.Transaction.WALLET_CURRENCY,
            Contract.Transaction.CATEGORY_NAME,
            Contract.Transaction.CATEGORY_ICON,
            Contract.Transaction.DESCRIPTION,
            Contract.Transaction.CONFIRMED
    };

    private static Object[] row(long id, int confirmed) {
        return new Object[] {
                id, Contract.TransactionType.STANDARD, "2019-03-15 10:00:00", Contract.Direction.EXPENSE,
                1000L, "EUR", "Category", null, "Description", confirmed
        };
    }

    private static RecyclerView listWith(Cursor cursor) {
        TransactionCursorAdapter adapter = new TransactionCursorAdapter(new TransactionCursorAdapter.ActionListener() {

            @Override
            public void onHeaderClick(Date startDate, Date endDate) {
            }

            @Override
            public void onTransactionClick(long id) {
            }

            @Override
            public void onSelectionChanged(int count) {
            }
        });
        adapter.changeCursor(cursor);
        RecyclerView recyclerView = new RecyclerView(new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.MoneyWalletAppTheme));
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        recyclerView.setAdapter(adapter);
        recyclerView.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
        recyclerView.layout(0, 0, 1080, 2400);
        return recyclerView;
    }

    private static View markerAt(RecyclerView recyclerView, int position) {
        View view = recyclerView.getLayoutManager().findViewByPosition(position);
        assertNotNull("nothing was laid out at position " + position, view);
        return view.findViewById(R.id.unconfirmed_image_view);
    }

    @Test
    public void theMarkerShowsOnlyOnAnUnconfirmedRow() {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.addRow(row(1L, 1));
        cursor.addRow(row(2L, 0));
        RecyclerView list = listWith(cursor);
        View confirmed = markerAt(list, 0);
        assertEquals(View.GONE, confirmed.getVisibility());
        assertNull(confirmed.getContentDescription());
        View unconfirmed = markerAt(list, 1);
        assertEquals(View.VISIBLE, unconfirmed.getVisibility());
        assertEquals(list.getContext().getString(R.string.search_value_unconfirmed),
                unconfirmed.getContentDescription());
    }

    @Test
    public void aRecycledRowHidesTheMarkerWhenItsNextRowIsConfirmed() {
        MatrixCursor unconfirmedCursor = new MatrixCursor(COLUMNS);
        unconfirmedCursor.addRow(row(1L, 0));
        RecyclerView list = listWith(unconfirmedCursor);
        View before = markerAt(list, 0);
        assertEquals(View.VISIBLE, before.getVisibility());
        MatrixCursor confirmedCursor = new MatrixCursor(COLUMNS);
        confirmedCursor.addRow(row(2L, 1));
        ((TransactionCursorAdapter) list.getAdapter()).changeCursor(confirmedCursor);
        list.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 1080, 2400);
        View after = markerAt(list, 0);
        assertSame("the row was not reused, so this did not test a rebind", before, after);
        assertEquals(View.GONE, after.getVisibility());
        assertNull(after.getContentDescription());
    }

    @Test
    public void aCursorWithoutTheConfirmedColumnShowsNoMarker() {
        MatrixCursor cursor = new MatrixCursor(Arrays.copyOf(COLUMNS, COLUMNS.length - 1));
        cursor.addRow(Arrays.copyOf(row(1L, 0), COLUMNS.length - 1));
        View marker = markerAt(listWith(cursor), 0);
        assertEquals(View.GONE, marker.getVisibility());
        assertNull(marker.getContentDescription());
    }
}
