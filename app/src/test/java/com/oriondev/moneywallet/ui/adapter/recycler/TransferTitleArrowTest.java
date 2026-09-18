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
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * The model and recurrence transfer rows title a transfer the way the transfer list does, with an
 * ASCII arrow. U+2192 has no bidi mirror, so in a right to left language it pointed back at the
 * wallet the money left, while the mirrored ">" points at the one it went to.
 */
@RunWith(RobolectricTestRunner.class)
public class TransferTitleArrowTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";
    private static final String FROM = "Cash";
    private static final String TO = "Bank";

    @Test
    public void aTransferModelIsTitledWithTheAsciiArrow() {
        MatrixCursor cursor = new MatrixCursor(new String[] {
                Contract.TransferModel.ID,
                Contract.TransferModel.WALLET_FROM_NAME,
                Contract.TransferModel.WALLET_FROM_CURRENCY,
                Contract.TransferModel.WALLET_TO_NAME,
                Contract.TransferModel.WALLET_TO_ICON,
                Contract.TransferModel.MONEY_FROM,
                Contract.TransferModel.MONEY_TAX
        });
        cursor.addRow(new Object[] {1L, FROM, "EUR", TO, ICON, 1000L, 0L});
        TransferModelCursorAdapter adapter = new TransferModelCursorAdapter(null);
        adapter.changeCursor(cursor);
        assertEquals(FROM + " -> " + TO, titleOf(adapter));
    }

    @Test
    public void aRecurrentTransferIsTitledWithTheAsciiArrow() {
        MatrixCursor cursor = new MatrixCursor(new String[] {
                Contract.RecurrentTransfer.ID,
                Contract.RecurrentTransfer.WALLET_FROM_NAME,
                Contract.RecurrentTransfer.WALLET_FROM_CURRENCY,
                Contract.RecurrentTransfer.WALLET_TO_NAME,
                Contract.RecurrentTransfer.WALLET_TO_ICON,
                Contract.RecurrentTransfer.MONEY_FROM,
                Contract.RecurrentTransfer.NEXT_OCCURRENCE
        });
        cursor.addRow(new Object[] {1L, FROM, "EUR", TO, ICON, 1000L, null});
        RecurrentTransferCursorAdapter adapter = new RecurrentTransferCursorAdapter(null);
        adapter.changeCursor(cursor);
        assertEquals(FROM + " -> " + TO, titleOf(adapter));
    }

    private static String titleOf(RecyclerView.Adapter<?> adapter) {
        RecyclerView recyclerView = new RecyclerView(new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.MoneyWalletAppTheme));
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        recyclerView.setAdapter(adapter);
        recyclerView.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
        recyclerView.layout(0, 0, 1080, 2400);
        View row = recyclerView.getLayoutManager().findViewByPosition(0);
        assertNotNull("nothing was laid out where the row belongs", row);
        TextView title = row.findViewById(R.id.secondary_text_view);
        return title.getText().toString();
    }
}
