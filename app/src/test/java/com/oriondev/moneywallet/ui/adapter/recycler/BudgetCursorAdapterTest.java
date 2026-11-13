package com.oriondev.moneywallet.ui.adapter.recycler;

import android.app.Activity;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;
import com.oriondev.moneywallet.ui.view.MaterialProgressBar;
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.MoneyFormatter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.GraphicsMode;

import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BudgetCursorAdapterTest {

    private static final String[] BAD_STARTS = new String[] {"", "null"};

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private static final String[] COLUMNS = new String[] {
            Contract.Budget.ID,
            Contract.Budget.TYPE,
            Contract.Budget.MONEY,
            Contract.Budget.START_DATE,
            Contract.Budget.END_DATE,
            Contract.Budget.CATEGORY_ICON,
            Contract.Budget.CATEGORY_NAME,
            Contract.Budget.CATEGORY_TYPE,
            Contract.Budget.CURRENCY,
            Contract.Budget.PROGRESS
    };

    private ActivityScenario<BackupListActivity> mScenario;
    private Activity mActivity;
    private TimeZone mTimeZone;

    @Before
    public void setUp() {
        mTimeZone = TimeZone.getDefault();
        mScenario = ActivityScenario.launch(BackupListActivity.class);
        mScenario.onActivity(activity -> mActivity = activity);
        pinToday(2019, 11, 12);
    }

    @After
    public void tearDown() {
        BudgetCursorAdapter.sToday = null;
        TimeZone.setDefault(mTimeZone);
        mScenario.close();
    }

    @Test
    public void aBudgetExactlyOnTargetHasNothingAvailableAndTheDefaultBar() {
        View row = bindOne(expenses(100000, 100000));
        assertEquals(mActivity.getString(R.string.adapter_available_money), availableHeader(row));
        assertEquals(money(0), availableBody(row));
        assertEquals(defaultBarColor(), barColor(row));
    }

    @Test
    public void anExpensesBudgetOverItsTargetSaysByHowMuchInTheExpenseColor() {
        View row = bindOne(expenses(100000, 100500));
        assertEquals(mActivity.getString(R.string.adapter_over_money), availableHeader(row));
        assertEquals(money(500), availableBody(row));
        assertEquals(expenseColor(), barColor(row));
        assertNotEquals(defaultBarColor(), expenseColor());
    }

    @Test
    public void anExpenseCategoryBudgetOverItsTargetSaysByHowMuch() {
        View row = bindOne(category(Contract.CategoryType.EXPENSE, 100000, -100500));
        assertEquals(mActivity.getString(R.string.adapter_over_money), availableHeader(row));
        assertEquals(money(500), availableBody(row));
        assertEquals(expenseColor(), barColor(row));
    }

    @Test
    public void anIncomesBudgetPastItsTargetReadsAsBefore() {
        View row = bindOne(row(Contract.BudgetType.INCOMES, null, 100000, 100500,
                "2019-11-01", "2019-11-30"));
        assertEquals(mActivity.getString(R.string.adapter_available_money), availableHeader(row));
        assertEquals(money(0), availableBody(row));
        assertEquals(defaultBarColor(), barColor(row));
    }

    @Test
    public void anIncomeCategoryBudgetPastItsTargetReadsAsBefore() {
        View row = bindOne(category(Contract.CategoryType.INCOME, 100000, 100500));
        assertEquals(mActivity.getString(R.string.adapter_available_money), availableHeader(row));
        assertEquals(money(0), availableBody(row));
        assertEquals(defaultBarColor(), barColor(row));
    }

    @Test
    public void aRowBoundOverAndThenUnderGoesBackToAvailableAndTheDefaultBar() {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.addRow(expenses(100000, 100500));
        cursor.addRow(expenses(100000, 40000));
        BudgetCursorAdapter adapter = adapter(cursor);
        BudgetCursorAdapter.ViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(mActivity), 0);
        adapter.onBindViewHolder(holder, 0);
        View row = measure(holder.itemView);
        assertEquals(mActivity.getString(R.string.adapter_over_money), availableHeader(row));
        assertEquals(expenseColor(), barColor(row));
        adapter.onBindViewHolder(holder, 1);
        measure(row);
        assertEquals(mActivity.getString(R.string.adapter_available_money), availableHeader(row));
        assertEquals(money(60000), availableBody(row));
        assertEquals(defaultBarColor(), barColor(row));
    }

    @Test
    public void leftPerDayDividesWhatIsLeftOverTheDaysLeftCountingToday() {
        View row = bindOne(expenses(100000, 40000));
        assertEquals(View.VISIBLE, leftPerDayHeader(row).getVisibility());
        assertEquals(mActivity.getString(R.string.adapter_left_per_day_money), leftPerDayHeader(row).getText().toString());
        assertEquals(View.VISIBLE, leftPerDayBody(row).getVisibility());
        assertEquals(money(60000 / 19), leftPerDayBody(row).getText().toString());
    }

    @Test
    public void onTheLastDayEverythingLeftIsForToday() {
        pinToday(2019, 11, 30);
        View row = bindOne(expenses(100000, 40000));
        assertEquals(View.VISIBLE, leftPerDayBody(row).getVisibility());
        assertEquals(money(60000), leftPerDayBody(row).getText().toString());
    }

    @Test
    public void aBudgetThatHasNotStartedDividesOverItsWholePeriod() {
        View row = bindOne(row(Contract.BudgetType.EXPENSES, null, 100000, 39000,
                "2019-12-01", "2019-12-31"));
        assertEquals(View.VISIBLE, leftPerDayBody(row).getVisibility());
        assertEquals(money(61000 / 31), leftPerDayBody(row).getText().toString());
    }

    @Test
    public void leftPerDayCountsWholeDaysAcrossAClockChange() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        pinToday(2026, 3, 1);
        View row = bindOne(row(Contract.BudgetType.EXPENSES, null, 4000, 900,
                "2026-03-01", "2026-03-31"));
        assertEquals(View.VISIBLE, leftPerDayBody(row).getVisibility());
        assertEquals(money(3100 / 31), leftPerDayBody(row).getText().toString());
    }

    @Test
    public void leftPerDayIsHiddenOnABudgetOverItsTarget() {
        View row = bindOne(expenses(100000, 100500));
        assertEquals(View.GONE, leftPerDayHeader(row).getVisibility());
        assertEquals(View.GONE, leftPerDayBody(row).getVisibility());
    }

    @Test
    public void leftPerDayIsHiddenWhenItWouldReadZero() {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.addRow(expenses(100000, 100000));
        cursor.addRow(expenses(100000, 99982));
        BudgetCursorAdapter adapter = adapter(cursor);
        for (int position = 0; position < 2; position++) {
            BudgetCursorAdapter.ViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(mActivity), 0);
            adapter.onBindViewHolder(holder, position);
            assertEquals(View.GONE, leftPerDayHeader(holder.itemView).getVisibility());
            assertEquals(View.GONE, leftPerDayBody(holder.itemView).getVisibility());
        }
    }

    @Test
    public void leftPerDayIsHiddenWhenTheStartIsAfterTheEnd() {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.addRow(row(Contract.BudgetType.EXPENSES, null, 100000, 40000, "2019-11-21", "2019-11-20"));
        cursor.addRow(row(Contract.BudgetType.EXPENSES, null, 100000, 40000, "2019-11-22", "2019-11-20"));
        BudgetCursorAdapter adapter = adapter(cursor);
        for (int position = 0; position < 2; position++) {
            BudgetCursorAdapter.ViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(mActivity), 0);
            adapter.onBindViewHolder(holder, position);
            assertEquals(View.GONE, leftPerDayHeader(holder.itemView).getVisibility());
            assertEquals(View.GONE, leftPerDayBody(holder.itemView).getVisibility());
        }
    }

    @Test
    public void aStartDateThatDoesNotParseShowsTheCardWithoutLeftPerDay() {
        for (String start : BAD_STARTS) {
            View row = bindOne(row(Contract.BudgetType.EXPENSES, null, 100000, 40000, start, "2019-11-30"));
            assertCard(row, R.string.adapter_available_money, 60000);
        }
    }

    @Test
    public void aStartDateThatDoesNotParseLeavesOverExpiredAndIncomeCardsAsTheyWere() {
        for (String start : BAD_STARTS) {
            assertCard(bindOne(row(Contract.BudgetType.EXPENSES, null, 100000, 100500, start, "2019-11-30")),
                    R.string.adapter_over_money, 500);
            assertCard(bindOne(row(Contract.BudgetType.EXPENSES, null, 100000, 40000, start, "2019-11-11")),
                    R.string.adapter_available_money, 60000);
            assertCard(bindOne(row(Contract.BudgetType.INCOMES, null, 100000, 40000, start, "2019-11-30")),
                    R.string.adapter_available_money, 60000);
            assertCard(bindOne(row(Contract.BudgetType.CATEGORY, Contract.CategoryType.INCOME, 100000, 40000,
                    start, "2019-11-30")), R.string.adapter_available_money, 60000);
        }
    }

    private void assertCard(View row, int header, long available) {
        assertEquals(mActivity.getString(header), availableHeader(row));
        assertEquals(money(available), availableBody(row));
        assertEquals(View.GONE, leftPerDayHeader(row).getVisibility());
        assertEquals(View.GONE, leftPerDayBody(row).getVisibility());
    }

    @Test
    public void leftPerDayIsHiddenOnAnExpiredBudget() {
        View row = bindOne(row(Contract.BudgetType.EXPENSES, null, 100000, 40000,
                "2019-10-01", "2019-11-11"));
        assertEquals(View.GONE, leftPerDayHeader(row).getVisibility());
        assertEquals(View.GONE, leftPerDayBody(row).getVisibility());
    }

    @Test
    public void leftPerDayIsHiddenOnIncomeBudgets() {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.addRow(row(Contract.BudgetType.INCOMES, null, 100000, 40000, "2019-11-01", "2019-11-30"));
        cursor.addRow(category(Contract.CategoryType.INCOME, 100000, 40000));
        BudgetCursorAdapter adapter = adapter(cursor);
        for (int position = 0; position < 2; position++) {
            BudgetCursorAdapter.ViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(mActivity), 0);
            adapter.onBindViewHolder(holder, position);
            assertEquals(View.GONE, leftPerDayHeader(holder.itemView).getVisibility());
            assertEquals(View.GONE, leftPerDayBody(holder.itemView).getVisibility());
        }
    }

    private static Object[] expenses(long target, long progress) {
        return row(Contract.BudgetType.EXPENSES, null, target, progress, "2019-11-01", "2019-11-30");
    }

    private static Object[] category(Contract.CategoryType categoryType, long target, long progress) {
        return row(Contract.BudgetType.CATEGORY, categoryType, target, progress, "2019-11-01", "2019-11-30");
    }

    private static Object[] row(Contract.BudgetType type, Contract.CategoryType categoryType, long target,
                                long progress, String startDate, String endDate) {
        return new Object[] {
                1L, type.getValue(), target, startDate, endDate, ICON, "Category",
                categoryType != null ? categoryType.getValue() : null, "EUR", progress
        };
    }

    private static void pinToday(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day, 12, 0);
        BudgetCursorAdapter.sToday = calendar.getTimeInMillis();
    }

    private static BudgetCursorAdapter adapter(MatrixCursor cursor) {
        BudgetCursorAdapter adapter = new BudgetCursorAdapter(null);
        adapter.changeCursor(cursor);
        return adapter;
    }

    private View bindOne(Object[] values) {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.addRow(values);
        BudgetCursorAdapter adapter = adapter(cursor);
        BudgetCursorAdapter.ViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(mActivity), 0);
        adapter.onBindViewHolder(holder, 0);
        return measure(holder.itemView);
    }

    private static View measure(View row) {
        row.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        row.layout(0, 0, row.getMeasuredWidth(), row.getMeasuredHeight());
        return row;
    }

    private static String availableHeader(View row) {
        return ((TextView) row.findViewById(R.id.available_header_text_view)).getText().toString();
    }

    private static String availableBody(View row) {
        return ((TextView) row.findViewById(R.id.available_body_text_view)).getText().toString();
    }

    private static TextView leftPerDayHeader(View row) {
        return row.findViewById(R.id.left_per_day_header_text_view);
    }

    private static TextView leftPerDayBody(View row) {
        return row.findViewById(R.id.left_per_day_body_text_view);
    }

    private static String money(long money) {
        CurrencyUnit currency = CurrencyManager.getCurrency("EUR");
        return MoneyFormatter.getInstance().getNotTintedString(currency, money);
    }

    private static int expenseColor() {
        return ThemeEngine.getTheme().getVisibleColor(PreferenceManager.getCurrentExpenseColor(),
                PreferenceManager.getDefaultColorExpense());
    }

    private int defaultBarColor() {
        View row = measure(new BudgetCursorAdapter(null).onCreateViewHolder(new FrameLayout(mActivity), 0).itemView);
        MaterialProgressBar bar = row.findViewById(R.id.progress_bar);
        bar.setMaxValue(1);
        bar.setProgressValue(1);
        return barColor(row);
    }

    private static int barColor(View row) {
        MaterialProgressBar bar = row.findViewById(R.id.progress_bar);
        Bitmap bitmap = Bitmap.createBitmap(bar.getWidth(), bar.getHeight(), Bitmap.Config.ARGB_8888);
        bar.draw(new Canvas(bitmap));
        return bitmap.getPixel(1, bar.getHeight() / 2);
    }
}
