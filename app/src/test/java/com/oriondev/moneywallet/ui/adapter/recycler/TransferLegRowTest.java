package com.oriondev.moneywallet.ui.adapter.recycler;

import android.database.MatrixCursor;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.TextView;

import androidx.core.text.BidiFormatter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class TransferLegRowTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";
    private static final String DATE = "2020-03-01 12:00:00";
    private static final String PERSIAN_NAME = "پس انداز";

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
            Contract.Transaction.CONFIRMED,
            Contract.Transaction.TRANSFER_WALLET_NAME,
            Contract.Transaction.TRANSFER_DIRECTION
    };

    private static Object[] row(long id, int type, int direction, long money, String category,
                                String transferWallet, Integer transferDirection) {
        return new Object[] {id, type, DATE, direction, money, "EUR", category, ICON, "Move", 1,
                transferWallet, transferDirection};
    }

    private static RecyclerView listOfAllKinds(String toWallet) {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.addRow(row(1L, Contract.TransactionType.TRANSFER, Contract.Direction.EXPENSE, 5000L,
                "Transfer", toWallet, Contract.Direction.EXPENSE));
        cursor.addRow(row(2L, Contract.TransactionType.TRANSFER, Contract.Direction.INCOME, 5000L,
                "Transfer", "Cash", Contract.Direction.INCOME));
        cursor.addRow(row(3L, Contract.TransactionType.TRANSFER, Contract.Direction.EXPENSE, 150L,
                "Transfer tax", null, null));
        cursor.addRow(row(4L, Contract.TransactionType.STANDARD, Contract.Direction.EXPENSE, 1000L,
                "Groceries", null, null));
        return listOf(cursor);
    }

    private static RecyclerView listOf(MatrixCursor cursor) {
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

    private static TextView textAt(RecyclerView recyclerView, int position, int id) {
        View row = recyclerView.getLayoutManager().findViewByPosition(position);
        assertNotNull("nothing was laid out at position " + position, row);
        return row.findViewById(id);
    }

    private static int colorOf(TextView textView) {
        CharSequence text = textView.getText();
        assertTrue("the amount carries no span", text instanceof Spanned);
        ForegroundColorSpan[] spans = ((Spanned) text).getSpans(0, text.length(), ForegroundColorSpan.class);
        assertEquals(1, spans.length);
        return spans[0].getForegroundColor();
    }

    private static int expenseColor() {
        return ThemeEngine.getTheme().getVisibleColor(PreferenceManager.getCurrentExpenseColor(),
                PreferenceManager.getDefaultColorExpense());
    }

    @Test
    public void eachLegReadsTheWalletOnItsOtherSide() {
        RecyclerView list = listOfAllKinds("Savings");
        assertEquals(list.getContext().getString(R.string.adapter_transfer_to_wallet, "Savings"),
                textAt(list, 0, R.id.primary_text_view).getText().toString());
        assertEquals("To Savings", textAt(list, 0, R.id.primary_text_view).getText().toString());
        assertEquals("From Cash", textAt(list, 1, R.id.primary_text_view).getText().toString());
        assertEquals("Transfer tax", textAt(list, 2, R.id.primary_text_view).getText().toString());
        assertEquals("Groceries", textAt(list, 3, R.id.primary_text_view).getText().toString());
    }

    @Test
    public void eachLegReadsItsSideInTheTransferAndNotTheDirectionItStores() {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.addRow(row(1L, Contract.TransactionType.TRANSFER, Contract.Direction.INCOME, 5000L,
                "Transfer", "Savings", Contract.Direction.EXPENSE));
        cursor.addRow(row(2L, Contract.TransactionType.TRANSFER, Contract.Direction.EXPENSE, 5000L,
                "Transfer", "Cash", Contract.Direction.INCOME));
        RecyclerView list = listOf(cursor);
        assertEquals("To Savings", textAt(list, 0, R.id.primary_text_view).getText().toString());
        assertEquals("From Cash", textAt(list, 1, R.id.primary_text_view).getText().toString());
    }

    @Test
    public void aRightToLeftWalletNameIsWrappedForTheDirectionOfTheRow() {
        String wrapped = BidiFormatter.getInstance().unicodeWrap(PERSIAN_NAME);
        assertNotEquals(PERSIAN_NAME, wrapped);
        RecyclerView list = listOfAllKinds(PERSIAN_NAME);
        assertEquals(list.getContext().getString(R.string.adapter_transfer_to_wallet, wrapped),
                textAt(list, 0, R.id.primary_text_view).getText().toString());
    }

    @Test
    public void legAmountsAreSignedByDirectionWhenThePlusAndMinusSettingIsOn() {
        PreferenceManager.setCurrencyEnabled(false);
        PreferenceManager.setShowPlusMinusSymbolEnabled(true);
        RecyclerView list = listOfAllKinds("Savings");
        assertEquals("-50.00", textAt(list, 0, R.id.money_text_view).getText().toString());
        assertEquals("+50.00", textAt(list, 1, R.id.money_text_view).getText().toString());
    }

    @Test
    public void legAmountsCarryNoSignWhenThePlusAndMinusSettingIsOff() {
        PreferenceManager.setCurrencyEnabled(false);
        PreferenceManager.setShowPlusMinusSymbolEnabled(false);
        RecyclerView list = listOfAllKinds("Savings");
        assertEquals("50.00", textAt(list, 0, R.id.money_text_view).getText().toString());
        assertEquals("50.00", textAt(list, 1, R.id.money_text_view).getText().toString());
    }

    @Test
    public void legAmountsAreInThePrimaryTextColorAndTheTaxAndAnExpenseInTheExpenseColor() {
        RecyclerView list = listOfAllKinds("Savings");
        int primary = ThemeEngine.getTheme().getTextColorPrimary();
        assertNotEquals(expenseColor(), primary);
        assertEquals(primary, colorOf(textAt(list, 0, R.id.money_text_view)));
        assertEquals(primary, colorOf(textAt(list, 1, R.id.money_text_view)));
        assertEquals(expenseColor(), colorOf(textAt(list, 2, R.id.money_text_view)));
        assertEquals(expenseColor(), colorOf(textAt(list, 3, R.id.money_text_view)));
    }
}
