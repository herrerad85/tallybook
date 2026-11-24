package com.oriondev.moneywallet.ui.adapter.recycler;

import android.app.Activity;
import android.database.MatrixCursor;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.wrapper.DebtHeaderCursor;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.MoneyFormatter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class DebtCursorAdapterTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

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
            Contract.Debt.PLACE_NAME
    };

    private ActivityScenario<BackupListActivity> mScenario;
    private Activity mActivity;

    @Before
    public void setUp() {
        mScenario = ActivityScenario.launch(BackupListActivity.class);
        mScenario.onActivity(activity -> mActivity = activity);
    }

    @After
    public void tearDown() {
        mScenario.close();
    }

    @Test
    public void aCreditStoredAtZeroShowsNoProgressLineAndOffersReceiveWithoutReceiveInFull() {
        View card = bindOne(row(Contract.DebtType.CREDIT, 0L, 0L));
        assertEquals(View.GONE, progressLine(card).getVisibility());
        assertEquals(View.VISIBLE, card.findViewById(R.id.receive_button).getVisibility());
        assertEquals(View.GONE, card.findViewById(R.id.receive_in_full_button).getVisibility());
    }

    @Test
    public void aDebtStoredAtZeroShowsNoProgressLineAndOffersPayWithoutPayInFull() {
        View card = bindOne(row(Contract.DebtType.DEBT, 0L, 0L));
        assertEquals(View.GONE, progressLine(card).getVisibility());
        assertEquals(View.VISIBLE, card.findViewById(R.id.pay_button).getVisibility());
        assertEquals(View.GONE, card.findViewById(R.id.pay_in_full_button).getVisibility());
    }

    @Test
    public void aCreditStoredAtZeroWithAPaymentReceivedStillShowsNoProgressLine() {
        View card = bindOne(row(Contract.DebtType.CREDIT, 0L, 500L));
        assertEquals(View.GONE, progressLine(card).getVisibility());
        assertEquals(View.GONE, card.findViewById(R.id.receive_in_full_button).getVisibility());
    }

    @Test
    public void aCreditOfTenWithNothingReceivedSaysWhatIsLeftToReceive() {
        View card = bindOne(row(Contract.DebtType.CREDIT, 1000L, 0L));
        assertEquals(View.VISIBLE, progressLine(card).getVisibility());
        assertEquals(mActivity.getString(R.string.relative_string_money_to_collect_credit, money(1000L)),
                progressLine(card).getText().toString());
        assertEquals(View.VISIBLE, card.findViewById(R.id.receive_in_full_button).getVisibility());
    }

    @Test
    public void aDebtOfTenPaidInFullSaysTheDebtIsCompleted() {
        View card = bindOne(row(Contract.DebtType.DEBT, 1000L, -1000L));
        assertEquals(View.VISIBLE, progressLine(card).getVisibility());
        assertEquals(mActivity.getString(R.string.relative_string_debt_completed),
                progressLine(card).getText().toString());
    }

    @Test
    public void aCardBoundAtZeroAndThenAtTenShowsTheProgressLineAgain() {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.addRow(row(Contract.DebtType.CREDIT, 0L, 0L));
        cursor.addRow(row(Contract.DebtType.CREDIT, 1000L, 0L));
        DebtCursorAdapter adapter = adapter(cursor);
        RecyclerView.ViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(mActivity), DebtHeaderCursor.TYPE_ITEM);
        adapter.onBindViewHolder(holder, 0);
        adapter.onBindViewHolder(holder, 1);
        assertEquals(View.VISIBLE, progressLine(holder.itemView).getVisibility());
    }

    private static Object[] row(Contract.DebtType debtType, long money, long progress) {
        return new Object[] {1L, debtType.getValue(), ICON, "Loan", money, progress, 0L, "EUR", null, 0, null, null};
    }

    private static DebtCursorAdapter adapter(MatrixCursor cursor) {
        DebtCursorAdapter adapter = new DebtCursorAdapter(null);
        adapter.changeCursor(cursor);
        return adapter;
    }

    private View bindOne(Object[] values) {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.addRow(values);
        DebtCursorAdapter adapter = adapter(cursor);
        RecyclerView.ViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(mActivity), DebtHeaderCursor.TYPE_ITEM);
        adapter.onBindViewHolder(holder, 0);
        return holder.itemView;
    }

    private static TextView progressLine(View card) {
        return card.findViewById(R.id.money_progress_text_view);
    }

    private static String money(long money) {
        CurrencyUnit currency = CurrencyManager.getCurrency("EUR");
        return MoneyFormatter.getInstance().getNotTintedString(currency, money);
    }
}
