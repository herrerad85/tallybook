package com.oriondev.moneywallet.ui.adapter.recycler;

import android.app.Activity;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Layout;
import android.util.StateSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.storage.wrapper.BudgetPeriodCursor;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;
import com.oriondev.moneywallet.ui.view.MaterialProgressBar;
import com.oriondev.moneywallet.ui.view.theme.ITheme;
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.DateUtils;
import com.oriondev.moneywallet.utils.MoneyFormatter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BudgetCursorAdapterTest {

    private static final String[] BAD_STARTS = new String[] {"", "null"};

    private static final int AMBER = 0xFFFFA000;

    private static final int YELLOW = 0xFFFFEB3B;

    private static final int INDIGO = 0xFF3F51B5;

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private static final String[] COLUMNS = new String[] {
            Contract.Budget.ID,
            Contract.Budget.TYPE,
            Contract.Budget.SHOWN_MONEY,
            Contract.Budget.START_DATE,
            Contract.Budget.END_DATE,
            Contract.Budget.CATEGORY_ICON,
            Contract.Budget.CATEGORY_NAME,
            Contract.Budget.CATEGORY_TYPE,
            Contract.Budget.CURRENCY,
            Contract.Budget.PROGRESS
    };

    private static final String[] FOLLOWED_COLUMNS = new String[] {
            Contract.Budget.ID,
            Contract.Budget.TYPE,
            Contract.Budget.SHOWN_MONEY,
            Contract.Budget.START_DATE,
            Contract.Budget.END_DATE,
            Contract.Budget.CATEGORY_ICON,
            Contract.Budget.CATEGORY_NAME,
            Contract.Budget.CATEGORY_TYPE,
            Contract.Budget.CURRENCY,
            Contract.Budget.PROGRESS,
            Contract.Budget.MONEY
    };

    private static final int[] PERIOD_COLUMN_IDS = new int[] {
            R.id.period_column_layout_1, R.id.period_column_layout_2, R.id.period_column_layout_3
    };

    private static final int[] PERIOD_BAR_IDS = new int[] {
            R.id.period_bar_view_1, R.id.period_bar_view_2, R.id.period_bar_view_3
    };

    private static final int[] PERIOD_DATE_IDS = new int[] {
            R.id.period_date_text_view_1, R.id.period_date_text_view_2, R.id.period_date_text_view_3
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
        View row = bindOne(row(Contract.BudgetType.INCOMES, null, 100000, 100000,
                "2019-11-01", "2019-11-30"));
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
    public void anExpensesBudgetAtEightyPercentOfItsLimitDrawsAnAmberBar() {
        View row = bindOne(expenses(100000, 80000));
        assertEquals(mActivity.getString(R.string.adapter_available_money), availableHeader(row));
        assertEquals(money(20000), availableBody(row));
        assertEquals(AMBER, barColor(row));
        assertNotEquals(defaultBarColor(), AMBER);
        assertNotEquals(expenseColor(), AMBER);
    }

    @Test
    public void anExpensesBudgetOneUnitUnderEightyPercentKeepsTheDefaultBar() {
        View row = bindOne(expenses(100000, 79999));
        assertEquals(defaultBarColor(), barColor(row));
    }

    @Test
    public void anExpensesBudgetExactlyOnItsLimitDrawsAnAmberBarWithNothingAvailable() {
        View row = bindOne(expenses(100000, 100000));
        assertEquals(mActivity.getString(R.string.adapter_available_money), availableHeader(row));
        assertEquals(money(0), availableBody(row));
        assertEquals(AMBER, barColor(row));
    }

    @Test
    public void anExpensesBudgetOneUnitOverItsLimitIsOverInTheExpenseColor() {
        View row = bindOne(expenses(100000, 100001));
        assertEquals(mActivity.getString(R.string.adapter_over_money), availableHeader(row));
        assertEquals(money(1), availableBody(row));
        assertEquals(expenseColor(), barColor(row));
    }

    @Test
    public void anExpenseCategoryBudgetAtNinetyPercentDrawsAnAmberBar() {
        View row = bindOne(category(Contract.CategoryType.EXPENSE, 100000, -90000));
        assertEquals(mActivity.getString(R.string.adapter_available_money), availableHeader(row));
        assertEquals(AMBER, barColor(row));
    }

    @Test
    public void anIncomesBudgetAtNinetyPercentKeepsTheDefaultBar() {
        View row = bindOne(row(Contract.BudgetType.INCOMES, null, 100000, 90000,
                "2019-11-01", "2019-11-30"));
        assertEquals(defaultBarColor(), barColor(row));
    }

    @Test
    public void anIncomeCategoryBudgetAtNinetyPercentKeepsTheDefaultBar() {
        View row = bindOne(category(Contract.CategoryType.INCOME, 100000, 90000));
        assertEquals(defaultBarColor(), barColor(row));
    }

    @Test
    public void anExpensesBudgetWithAZeroLimitAndNothingUsedKeepsTheDefaultBar() {
        View row = bindOne(expenses(0, 0));
        assertEquals(mActivity.getString(R.string.adapter_available_money), availableHeader(row));
        assertEquals(defaultBarColor(), reachedBarColor(row));
    }

    @Test
    public void aRowBoundAmberAndThenAtTenPercentGoesBackToTheDefaultBar() {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.addRow(expenses(100000, 90000));
        cursor.addRow(expenses(100000, 10000));
        BudgetCursorAdapter adapter = adapter(cursor);
        BudgetCursorAdapter.ViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(mActivity), 0);
        adapter.onBindViewHolder(holder, 0);
        View row = measure(holder.itemView);
        assertEquals(AMBER, barColor(row));
        adapter.onBindViewHolder(holder, 1);
        measure(row);
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

    @Test
    public void periodBarsAreScaledToTheBiggestPeriodWithTheLimitLineAtItsShare() {
        View row = bindPeriods(expenses(10000, 0), 5000, 10000, 15000);
        int area = periodAreaHeight(row);
        assertTrue(area > 0);
        assertEquals(View.VISIBLE, row.findViewById(R.id.period_layout).getVisibility());
        assertEquals(area, periodHeight(row, 2));
        assertEquals(area, 3 * periodHeight(row, 0));
        assertEquals(2 * area, 3 * periodHeight(row, 1));
        for (int i = 0; i < PERIOD_BAR_IDS.length; i++) {
            assertEquals(area, row.findViewById(PERIOD_BAR_IDS[i]).getBottom());
        }
        assertEquals(View.VISIBLE, row.findViewById(R.id.period_limit_view).getVisibility());
        assertEquals(2 * area, 3 * limitHeight(row));
        assertEquals(ThemeEngine.getTheme().getTextColorSecondary(), color(row.findViewById(R.id.period_limit_view)));
    }

    @Test
    public void anExpensesPeriodOneUnitOverTheLimitIsRedAndOneExactlyOnItKeepsTheBarColor() {
        View row = bindPeriods(expenses(10000, 0), 10001, 10000, 5000);
        assertEquals(expenseColor(), periodColor(row, 0));
        assertEquals(periodBarColor(), periodColor(row, 1));
        assertEquals(periodBarColor(), periodColor(row, 2));
        assertNotEquals(periodBarColor(), expenseColor());
    }

    @Test
    public void anExpenseCategoryPeriodOneUnitOverTheLimitIsRedAndOneExactlyOnItKeepsTheBarColor() {
        View row = bindPeriods(category(Contract.CategoryType.EXPENSE, 10000, 0), 10001, 10000, 5000);
        assertEquals(expenseColor(), periodColor(row, 0));
        assertEquals(periodBarColor(), periodColor(row, 1));
        assertEquals(periodBarColor(), periodColor(row, 2));
        assertNotEquals(periodBarColor(), expenseColor());
    }

    @Test
    public void incomePeriodsOverTheLimitAreNeverRed() {
        Object[][] rows = new Object[][] {
                row(Contract.BudgetType.INCOMES, null, 10000, 0, "2019-11-01", "2019-11-30"),
                category(Contract.CategoryType.INCOME, 10000, 0)
        };
        for (Object[] values : rows) {
            View row = bindPeriods(values, 20000, 10001, 5000);
            assertEquals(View.VISIBLE, row.findViewById(R.id.period_layout).getVisibility());
            for (int i = 0; i < PERIOD_BAR_IDS.length; i++) {
                assertEquals(periodBarColor(), periodColor(row, i));
            }
        }
    }

    @Test
    public void aZeroLimitHidesTheLimitLineAndKeepsTheBars() {
        View row = bindPeriods(expenses(0, 0), 100, 200, 300);
        assertEquals(View.VISIBLE, row.findViewById(R.id.period_layout).getVisibility());
        assertEquals(View.GONE, row.findViewById(R.id.period_limit_view).getVisibility());
        assertEquals(periodAreaHeight(row), periodHeight(row, 2));
    }

    @Test
    public void aPeriodOfOneUnitIsStillOnePixelTallAndAnEmptyOneIsFlat() {
        View row = bindPeriods(expenses(100000000, 0), 1, 0, 50000000);
        assertEquals(1, periodHeight(row, 0));
        assertEquals(0, periodHeight(row, 1));
        assertEquals(periodAreaHeight(row) / 2, periodHeight(row, 2));
    }

    @Test
    public void eachPeriodReadsAsOneNodeWithItsStartDateAndAmount() {
        View row = bindPeriods(expenses(10000, 0), 5000, 10000, 15000);
        String[] dates = new String[] {"Aug 14", "Sep 14", "Oct 14"};
        long[] amounts = new long[] {5000, 10000, 15000};
        for (int i = 0; i < PERIOD_COLUMN_IDS.length; i++) {
            View column = row.findViewById(PERIOD_COLUMN_IDS[i]);
            TextView date = row.findViewById(PERIOD_DATE_IDS[i]);
            assertEquals(dates[i], date.getText().toString());
            assertEquals(mActivity.getString(R.string.description_budget_period_spent, dates[i], money(amounts[i])),
                    column.getContentDescription().toString());
            assertEquals("Period starting " + dates[i] + ", spent " + money(amounts[i]),
                    column.getContentDescription().toString());
            assertTrue(column.isImportantForAccessibility());
            assertTrue(column.isFocusable());
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, date.getImportantForAccessibility());
        }
    }

    @Test
    public void anExpensesPeriodReadsAsSpentWithTheSameTooltip() {
        Object[][] rows = new Object[][] {
                expenses(10000, 0),
                category(Contract.CategoryType.EXPENSE, 10000, 0)
        };
        for (Object[] values : rows) {
            assertPeriodsRead(bindPeriods(values, 5000, 10000, 15000), true);
        }
    }

    @Test
    public void anIncomesPeriodReadsAsEarnedWithTheSameTooltip() {
        Object[][] rows = new Object[][] {
                row(Contract.BudgetType.INCOMES, null, 10000, 0, "2019-11-01", "2019-11-30"),
                category(Contract.CategoryType.INCOME, 10000, 0)
        };
        for (Object[] values : rows) {
            assertPeriodsRead(bindPeriods(values, 5000, 10000, 15000), false);
        }
    }

    @Test
    public void aRecycledRowReadsSpentOrEarnedForTheBudgetItIsBoundTo() {
        long[] progress = new long[] {5000, 10000, 15000};
        BudgetCursorAdapter adapter = periodAdapter(COLUMNS, new Object[][] {
                expenses(10000, 0),
                row(Contract.BudgetType.INCOMES, null, 10000, 0, "2019-11-01", "2019-11-30"),
                expenses(10000, 0)
        }, new long[][] {progress, progress, progress});
        BudgetCursorAdapter.ViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(mActivity), 0);
        adapter.onBindViewHolder(holder, 0);
        View row = measure(holder.itemView);
        assertPeriodsRead(row, true);
        adapter.onBindViewHolder(holder, 1);
        measure(row);
        assertPeriodsRead(row, false);
        adapter.onBindViewHolder(holder, 2);
        measure(row);
        assertPeriodsRead(row, true);
    }

    @Test
    public void eachPeriodNodeReadsItsDescriptionAndTooltipAndCanBeClicked() {
        View row = bindPeriods(category(Contract.CategoryType.EXPENSE, 10000, 0), 5000, 10000, 15000);
        mActivity.setContentView(row);
        measure(row);
        String[] dates = new String[] {"Aug 14", "Sep 14", "Oct 14"};
        long[] amounts = new long[] {5000, 10000, 15000};
        for (int i = 0; i < PERIOD_COLUMN_IDS.length; i++) {
            AccessibilityNodeInfo node = row.findViewById(PERIOD_COLUMN_IDS[i]).createAccessibilityNodeInfo();
            String description = mActivity.getString(R.string.description_budget_period_spent, dates[i],
                    money(amounts[i]));
            assertEquals(description, node.getContentDescription().toString());
            assertEquals(description, node.getTooltipText().toString());
            assertTrue(node.isClickable());
        }
    }

    @Test
    public void theLastPeriodsHeaderShowsWithTheBarsAndHidesWithThem() {
        BudgetCursorAdapter adapter = periodAdapter(COLUMNS, new Object[][] {
                category(Contract.CategoryType.EXPENSE, 10000, 0),
                row(Contract.BudgetType.INCOMES, null, 10000, 0, "2019-11-01", "2019-11-30"),
                expenses(10000, 0)
        }, new long[][] {null, new long[] {5000, 10000, 15000}, null});
        BudgetCursorAdapter.ViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(mActivity), 0);
        mActivity.setContentView(holder.itemView);
        adapter.onBindViewHolder(holder, 0);
        View row = measure(holder.itemView);
        TextView header = row.findViewById(R.id.period_header_text_view);
        View period = row.findViewById(R.id.period_layout);
        assertEquals(period, header.getParent());
        assertEquals(View.GONE, period.getVisibility());
        assertFalse(header.isShown());
        adapter.onBindViewHolder(holder, 1);
        measure(row);
        assertEquals(mActivity.getString(R.string.adapter_previous_periods), header.getText().toString());
        assertEquals("Last 3 periods", header.getText().toString());
        assertEquals(View.VISIBLE, header.getVisibility());
        assertEquals(View.VISIBLE, period.getVisibility());
        assertTrue(header.isShown());
        adapter.onBindViewHolder(holder, 2);
        measure(row);
        assertEquals(View.GONE, period.getVisibility());
        assertFalse(header.isShown());
    }

    @Test
    public void aRecycledRowDropsTheRedBarAndThenTheWholeRow() {
        BudgetCursorAdapter adapter = periodAdapter(COLUMNS, new Object[][] {
                expenses(10000, 0), expenses(10000, 0), expenses(10000, 0)
        }, new long[][] {
                new long[] {15000, 5000, 5000}, new long[] {5000, 6000, 7000}, null
        });
        BudgetCursorAdapter.ViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(mActivity), 0);
        adapter.onBindViewHolder(holder, 0);
        View row = measure(holder.itemView);
        assertEquals(expenseColor(), periodColor(row, 0));
        adapter.onBindViewHolder(holder, 1);
        measure(row);
        assertEquals(View.VISIBLE, row.findViewById(R.id.period_layout).getVisibility());
        int area = periodAreaHeight(row);
        long[] amounts = new long[] {5000, 6000, 7000};
        for (int i = 0; i < PERIOD_BAR_IDS.length; i++) {
            assertEquals(periodBarColor(), periodColor(row, i));
            assertEquals((int) (amounts[i] * area / 10000), periodHeight(row, i));
        }
        assertEquals(area, limitHeight(row));
        adapter.onBindViewHolder(holder, 2);
        measure(row);
        assertEquals(View.GONE, row.findViewById(R.id.period_layout).getVisibility());
    }

    @Test
    public void aFollowedBudgetScalesItsPeriodsToTheLimitTheCardShows() {
        Object[] values = new Object[FOLLOWED_COLUMNS.length];
        System.arraycopy(expenses(15000, 0), 0, values, 0, COLUMNS.length);
        values[COLUMNS.length] = 30000L;
        BudgetCursorAdapter adapter = periodAdapter(FOLLOWED_COLUMNS, new Object[][] {values},
                new long[][] {new long[] {5000, 10000, 15000}});
        BudgetCursorAdapter.ViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(mActivity), 0);
        adapter.onBindViewHolder(holder, 0);
        View row = measure(holder.itemView);
        int area = periodAreaHeight(row);
        assertEquals(area, periodHeight(row, 2));
        assertEquals(area, 3 * periodHeight(row, 0));
        assertEquals(area, limitHeight(row));
    }

    @Test
    public void aRowBoundWithAZeroLimitAndThenWithALimitShowsTheLimitLineAgainAtItsShare() {
        BudgetCursorAdapter adapter = periodAdapter(COLUMNS, new Object[][] {
                expenses(0, 0), expenses(10000, 0)
        }, new long[][] {
                new long[] {100, 200, 300}, new long[] {5000, 10000, 15000}
        });
        BudgetCursorAdapter.ViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(mActivity), 0);
        adapter.onBindViewHolder(holder, 0);
        View row = measure(holder.itemView);
        assertEquals(View.GONE, row.findViewById(R.id.period_limit_view).getVisibility());
        adapter.onBindViewHolder(holder, 1);
        measure(row);
        assertEquals(View.VISIBLE, row.findViewById(R.id.period_limit_view).getVisibility());
        assertEquals(2 * periodAreaHeight(row), 3 * limitHeight(row));
    }

    @Test
    public void anAccentTheCardCannotShowGivesEveryPeriodBarTheColorTheMainBarDraws() {
        boolean followSystem = ThemeEngine.isFollowingSystem();
        ThemeEngine.Mode mode = ThemeEngine.getTheme().getMode();
        int accent = ThemeEngine.getSavedColorAccent();
        try {
            ThemeEngine.setMode(ThemeEngine.Mode.LIGHT);
            ThemeEngine.setColorAccent(YELLOW);
            assertEquals(YELLOW, ThemeEngine.getTheme().getColorAccent());
            View row = bindPeriods(expenses(10000, 0), 5000, 6000, 7000);
            int barColor = reachedBarColor(row);
            assertNotEquals(YELLOW, barColor);
            for (int i = 0; i < PERIOD_BAR_IDS.length; i++) {
                assertEquals(barColor, periodColor(row, i));
            }
        } finally {
            ThemeEngine.setColorAccent(accent);
            if (followSystem) {
                ThemeEngine.setFollowSystem();
            } else {
                ThemeEngine.setMode(mode);
            }
        }
    }

    @Test
    public void aCustomExpenseColorGivesAPeriodOverTheLimitTheColorTheMainBarDrawsOverItsLimit() {
        boolean followSystem = ThemeEngine.isFollowingSystem();
        ThemeEngine.Mode mode = ThemeEngine.getTheme().getMode();
        int expenseColor = PreferenceManager.getCurrentExpenseColor();
        try {
            ThemeEngine.setMode(ThemeEngine.Mode.LIGHT);
            PreferenceManager.setCurrentExpenseColor(INDIGO);
            assertEquals(INDIGO, ThemeEngine.getTheme().getVisibleColor(INDIGO, PreferenceManager.getDefaultColorExpense()));
            View row = bindPeriods(expenses(10000, 10500), 5000, 15000, 7000);
            int barColor = barColor(row);
            assertEquals(INDIGO, barColor);
            assertEquals(barColor, periodColor(row, 1));
            assertNotEquals(Color.RED, periodColor(row, 1));
            assertEquals(periodBarColor(), periodColor(row, 0));
            assertEquals(periodBarColor(), periodColor(row, 2));
        } finally {
            PreferenceManager.setCurrentExpenseColor(expenseColor);
            if (followSystem) {
                ThemeEngine.setFollowSystem();
            } else {
                ThemeEngine.setMode(mode);
            }
        }
    }

    @Test
    public void anExpenseColorTheCardCannotShowGivesAPeriodOverTheLimitTheRedTheMainBarDrawsOverItsLimit() {
        boolean followSystem = ThemeEngine.isFollowingSystem();
        ThemeEngine.Mode mode = ThemeEngine.getTheme().getMode();
        int expenseColor = PreferenceManager.getCurrentExpenseColor();
        try {
            ThemeEngine.setMode(ThemeEngine.Mode.LIGHT);
            PreferenceManager.setCurrentExpenseColor(YELLOW);
            assertEquals(PreferenceManager.getDefaultColorExpense(),
                    ThemeEngine.getTheme().getVisibleColor(YELLOW, PreferenceManager.getDefaultColorExpense()));
            View row = bindPeriods(expenses(10000, 10500), 5000, 15000, 7000);
            int barColor = barColor(row);
            assertEquals(Color.RED, barColor);
            assertNotEquals(YELLOW, barColor);
            assertEquals(barColor, periodColor(row, 1));
            assertEquals(periodBarColor(), periodColor(row, 0));
            assertEquals(periodBarColor(), periodColor(row, 2));
        } finally {
            PreferenceManager.setCurrentExpenseColor(expenseColor);
            if (followSystem) {
                ThemeEngine.setFollowSystem();
            } else {
                ThemeEngine.setMode(mode);
            }
        }
    }

    @Test
    public void aLimitFarUnderTheBiggestPeriodPutsTheLimitLineOnTheOnePixelBarOfAPeriodOnTheLimit() {
        View row = bindPeriods(expenses(1, 0), 1, 0, 100000000);
        View period = row.findViewById(R.id.period_layout);
        View bar = row.findViewById(PERIOD_BAR_IDS[0]);
        View limit = row.findViewById(R.id.period_limit_view);
        assertEquals(1, bar.getHeight());
        assertEquals(View.VISIBLE, limit.getVisibility());
        assertEquals(top(bar, period), top(limit, period));
    }

    @Test
    public void theLimitLineRunsUnderExactlyTheThreeColumnsInsideTheMainBar() {
        assertPeriodLimitLaidOut(bindPeriods(expenses(10000, 0), 5000, 10000, 15000));
    }

    @Test
    @Config(qualifiers = "fa-ldrtl-w360dp-xhdpi")
    public void theLimitLineRunsUnderExactlyTheThreeColumnsInsideTheMainBarRightToLeft() {
        BudgetCursorAdapter adapter = periodAdapter(COLUMNS, new Object[][] {expenses(10000, 0)},
                new long[][] {new long[] {5000, 10000, 15000}});
        BudgetCursorAdapter.ViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(mActivity), 0);
        holder.itemView.setLayoutDirection(mActivity.getResources().getConfiguration().getLayoutDirection());
        adapter.onBindViewHolder(holder, 0);
        View row = measure(holder.itemView);
        assertEquals(View.LAYOUT_DIRECTION_RTL, row.getLayoutDirection());
        assertTrue(left(row.findViewById(PERIOD_COLUMN_IDS[0]), row) > left(row.findViewById(PERIOD_COLUMN_IDS[2]), row));
        assertPeriodLimitLaidOut(row);
    }

    @Test
    @Config(qualifiers = "pt-rBR-w360dp-xhdpi", fontScale = 1.0f)
    public void everyPeriodDateWrapsWholeUnderItsBarInBrazilianPortuguese() {
        assertPeriodDatesLaidOut(bindPeriods(expenses(10000, 0), 5000, 10000, 15000), "\\d{1,2} de .+");
    }

    @Test
    @Config(qualifiers = "en-w360dp-xhdpi", fontScale = 1.3f)
    public void everyPeriodDateWrapsWholeUnderItsBarAtLargeText() {
        Date[] startDates = new Date[] {
                DateUtils.getDateFromSQLDateString("2019-03-14"),
                DateUtils.getDateFromSQLDateString("2019-05-14"),
                DateUtils.getDateFromSQLDateString("2019-11-14")
        };
        assertPeriodDatesLaidOut(bindPeriods(startDates, expenses(10000, 0), 5000, 10000, 15000),
                "[A-Z][a-z]{2} \\d{1,2}");
    }

    @Test
    @Config(qualifiers = "en-w360dp-xhdpi", fontScale = 2.0f)
    public void everyPeriodDateWrapsWholeUnderItsBarAtTheLargestText() {
        assertPeriodDatesLaidOut(bindPeriods(expenses(10000, 0), 5000, 10000, 15000), "[A-Z][a-z]{2} \\d{1,2}");
    }

    @Test
    @Config(qualifiers = "pt-rBR-w360dp-xhdpi", fontScale = 2.0f)
    public void aBrazilianPortugueseDateOnThreeLinesWrapsWholeUnderItsBarAtTheLargestText() {
        Date[] startDates = new Date[] {
                DateUtils.getDateFromSQLDateString("2019-03-14"),
                DateUtils.getDateFromSQLDateString("2019-05-14"),
                DateUtils.getDateFromSQLDateString("2019-11-04")
        };
        View row = bindPeriods(startDates, expenses(10000, 0), 5000, 10000, 15000);
        TextView date = row.findViewById(PERIOD_DATE_IDS[1]);
        assertEquals("14 de mai.", date.getText().toString());
        assertEquals(3, date.getLayout().getLineCount());
        assertPeriodDatesLaidOut(row, "\\d{1,2} de .+");
    }

    @Test
    @Config(qualifiers = "en-w360dp-xhdpi", fontScale = 2.0f)
    public void everyPeriodDateBreaksOnlyAfterASpaceAtTheLargestText() {
        Date[] startDates = new Date[] {
                DateUtils.getDateFromSQLDateString("2019-05-14"),
                DateUtils.getDateFromSQLDateString("2019-09-04"),
                DateUtils.getDateFromSQLDateString("2019-12-24")
        };
        View row = bindPeriods(startDates, expenses(10000, 0), 5000, 10000, 15000);
        int wrapped = 0;
        for (int id : PERIOD_DATE_IDS) {
            TextView date = row.findViewById(id);
            String text = date.getText().toString();
            Layout layout = date.getLayout();
            if (layout.getLineCount() > 1) {
                wrapped++;
            }
            for (int line = 0; line < layout.getLineCount() - 1; line++) {
                assertEquals(text, ' ', text.charAt(layout.getLineEnd(line) - 1));
            }
        }
        assertTrue(wrapped > 0);
    }

    @Test
    @Config(qualifiers = "en-w360dp-xhdpi", fontScale = 1.0f)
    public void theLastPeriodsHeaderSitsAfterTheColumnsOnTheDateLine() {
        View row = bindPeriods(expenses(10000, 0), 5000, 10000, 15000);
        TextView header = row.findViewById(R.id.period_header_text_view);
        View column = row.findViewById(PERIOD_COLUMN_IDS[2]);
        View date = row.findViewById(PERIOD_DATE_IDS[2]);
        assertEquals(1, header.getLayout().getLineCount());
        assertTrue(left(header, row) + " < " + (left(column, row) + column.getWidth()),
                left(header, row) >= left(column, row) + column.getWidth());
        assertEquals(top(date, row) + date.getHeight(), top(header, row) + header.getHeight());
    }

    @Test
    @Config(qualifiers = "en-w360dp-xhdpi", fontScale = 2.0f)
    public void theLastPeriodsHeaderWrapsBesideTheColumnsAtTheLargestText() {
        View row = bindPeriods(expenses(10000, 0), 5000, 10000, 15000);
        assertHeaderLaidOut(row);
        row.measure(View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        row.layout(0, 0, row.getMeasuredWidth(), row.getMeasuredHeight());
        TextView header = row.findViewById(R.id.period_header_text_view);
        assertTrue(header.getLayout().getLineCount() > 1);
        assertHeaderLaidOut(row);
        assertPeriodLimitLaidOut(row);
    }

    @Test
    @Config(qualifiers = "fa-ldrtl-w360dp-xhdpi")
    public void theLastPeriodsHeaderSitsLeftOfTheColumnsRightToLeft() {
        BudgetCursorAdapter adapter = periodAdapter(COLUMNS, new Object[][] {expenses(10000, 0)},
                new long[][] {new long[] {5000, 10000, 15000}});
        BudgetCursorAdapter.ViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(mActivity), 0);
        holder.itemView.setLayoutDirection(mActivity.getResources().getConfiguration().getLayoutDirection());
        adapter.onBindViewHolder(holder, 0);
        View row = measure(holder.itemView);
        assertEquals(View.LAYOUT_DIRECTION_RTL, row.getLayoutDirection());
        View header = row.findViewById(R.id.period_header_text_view);
        for (int id : PERIOD_COLUMN_IDS) {
            View column = row.findViewById(id);
            assertTrue(left(header, row) + header.getWidth() + " > " + left(column, row),
                    left(header, row) + header.getWidth() <= left(column, row));
        }
        assertHeaderLaidOut(row);
    }

    @Test
    public void aTapOnAPeriodOpensItsBudgetAndALongPressDoesNot() {
        Object[] first = expenses(10000, 0);
        Object[] second = expenses(10000, 0);
        first[0] = 7L;
        second[0] = 8L;
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.addRow(first);
        cursor.addRow(second);
        List<Long> opened = new ArrayList<>();
        BudgetCursorAdapter adapter = new BudgetCursorAdapter(opened::add);
        adapter.changeCursor(new BudgetPeriodCursor(cursor, new Date[][] {periodStartDates(), periodStartDates()},
                new long[][] {new long[] {5000, 10000, 15000}, new long[] {5000, 10000, 15000}}));
        RecyclerView list = new RecyclerView(mActivity);
        list.setLayoutManager(new LinearLayoutManager(mActivity));
        list.setAdapter(adapter);
        mActivity.setContentView(list);
        shadowOf(Looper.getMainLooper()).idle();
        list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 1080, 1920);
        View row = list.findViewHolderForAdapterPosition(1).itemView;
        assertEquals(View.VISIBLE, row.findViewById(R.id.period_layout).getVisibility());
        tap(row, row.findViewById(R.id.primary_text_view));
        assertEquals(Collections.singletonList(8L), opened);
        opened.clear();
        for (int id : PERIOD_COLUMN_IDS) {
            View column = row.findViewById(id);
            tap(row, column);
            assertEquals(Collections.singletonList(8L), opened);
            opened.clear();
            assertTrue(column.performLongClick());
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(Collections.emptyList(), opened);
        }
    }

    @Test
    public void aPressOnAPeriodShowsTheRippleOnItsColumn() {
        View row = bindPeriods(expenses(10000, 0), 5000, 10000, 15000);
        for (int id : PERIOD_COLUMN_IDS) {
            View column = row.findViewById(id);
            assertTrue(column.getBackground() instanceof RippleDrawable);
            float x = left(column, row) + column.getWidth() / 2f;
            float y = top(column, row) + column.getHeight() / 2f;
            long time = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
            MotionEvent cancel = MotionEvent.obtain(time, time + 50, MotionEvent.ACTION_CANCEL, x, y, 0);
            row.dispatchTouchEvent(down);
            assertTrue(column.isPressed());
            assertFalse(row.isPressed());
            assertTrue(StateSet.stateSetMatches(new int[] {android.R.attr.state_pressed},
                    column.getBackground().getState()));
            row.dispatchTouchEvent(cancel);
            assertFalse(column.isPressed());
            down.recycle();
            cancel.recycle();
        }
    }

    private static void tap(View row, View view) {
        float x = left(view, row) + view.getWidth() / 2f;
        float y = top(view, row) + view.getHeight() / 2f;
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(time, time + 50, MotionEvent.ACTION_UP, x, y, 0);
        row.dispatchTouchEvent(down);
        row.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void assertPeriodsRead(View row, boolean expenses) {
        String[] dates = new String[] {"Aug 14", "Sep 14", "Oct 14"};
        long[] amounts = new long[] {5000, 10000, 15000};
        for (int i = 0; i < PERIOD_COLUMN_IDS.length; i++) {
            View column = row.findViewById(PERIOD_COLUMN_IDS[i]);
            String description = column.getContentDescription().toString();
            if (expenses) {
                assertEquals(mActivity.getString(R.string.description_budget_period_spent, dates[i],
                        money(amounts[i])), description);
                assertEquals("Period starting " + dates[i] + ", spent " + money(amounts[i]), description);
            } else {
                assertEquals(mActivity.getString(R.string.description_budget_period_earned, dates[i],
                        money(amounts[i])), description);
                assertEquals("Period starting " + dates[i] + ", earned " + money(amounts[i]), description);
            }
            assertEquals(description, column.getTooltipText().toString());
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

    private static Date[] periodStartDates() {
        return new Date[] {
                DateUtils.getDateFromSQLDateString("2019-08-14"),
                DateUtils.getDateFromSQLDateString("2019-09-14"),
                DateUtils.getDateFromSQLDateString("2019-10-14")
        };
    }

    private static BudgetCursorAdapter periodAdapter(String[] columns, Object[][] rows, long[][] progress) {
        MatrixCursor cursor = new MatrixCursor(columns);
        Date[][] startDates = new Date[rows.length][];
        for (int i = 0; i < rows.length; i++) {
            cursor.addRow(rows[i]);
            if (progress[i] != null) {
                startDates[i] = periodStartDates();
            }
        }
        BudgetCursorAdapter adapter = new BudgetCursorAdapter(null);
        adapter.changeCursor(new BudgetPeriodCursor(cursor, startDates, progress));
        return adapter;
    }

    private View bindPeriods(Object[] values, long... progress) {
        BudgetCursorAdapter adapter = periodAdapter(COLUMNS, new Object[][] {values}, new long[][] {progress});
        BudgetCursorAdapter.ViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(mActivity), 0);
        adapter.onBindViewHolder(holder, 0);
        return measure(holder.itemView);
    }

    private View bindPeriods(Date[] startDates, Object[] values, long... progress) {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.addRow(values);
        BudgetCursorAdapter adapter = new BudgetCursorAdapter(null);
        adapter.changeCursor(new BudgetPeriodCursor(cursor, new Date[][] {startDates}, new long[][] {progress}));
        BudgetCursorAdapter.ViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(mActivity), 0);
        adapter.onBindViewHolder(holder, 0);
        return measure(holder.itemView);
    }

    private static int periodAreaHeight(View row) {
        return ((View) row.findViewById(PERIOD_BAR_IDS[0]).getParent()).getHeight();
    }

    private static int periodHeight(View row, int index) {
        return row.findViewById(PERIOD_BAR_IDS[index]).getHeight();
    }

    private static int periodColor(View row, int index) {
        return color(row.findViewById(PERIOD_BAR_IDS[index]));
    }

    private static int limitHeight(View row) {
        return periodAreaHeight(row) - row.findViewById(R.id.period_limit_view).getTop();
    }

    private static int top(View view, View parent) {
        int top = 0;
        for (View child = view; child != parent; child = (View) child.getParent()) {
            top += child.getTop();
        }
        return top;
    }

    private static int left(View view, View parent) {
        int left = 0;
        for (View child = view; child != parent; child = (View) child.getParent()) {
            left += child.getLeft();
        }
        return left;
    }

    private static void assertPeriodDatesLaidOut(View row, String pattern) {
        View period = row.findViewById(R.id.period_layout);
        assertEquals(View.VISIBLE, period.getVisibility());
        for (int id : PERIOD_DATE_IDS) {
            TextView date = row.findViewById(id);
            String text = date.getText().toString();
            assertTrue(text, text.matches(pattern));
            Layout layout = date.getLayout();
            int lines = layout.getLineCount();
            assertEquals(text, text.length(), layout.getLineEnd(lines - 1));
            assertTrue(text, date.getHeight() >= date.getTotalPaddingTop() + layout.getLineBottom(lines - 1)
                    + date.getTotalPaddingBottom());
        }
        int bottom = top(period, row) + period.getHeight();
        ViewGroup section = (ViewGroup) period.getParent();
        View[] below = new View[] {
                section.getChildAt(section.indexOfChild(period) + 1),
                row.findViewById(R.id.progress_layout),
                row.findViewById(R.id.date_text_view)
        };
        for (View view : below) {
            assertTrue(top(view, row) + " < " + bottom, top(view, row) >= bottom);
        }
    }

    private static void assertPeriodLimitLaidOut(View row) {
        View limit = row.findViewById(R.id.period_limit_view);
        View bar = row.findViewById(R.id.progress_bar);
        assertEquals(View.VISIBLE, limit.getVisibility());
        int columnLeft = Integer.MAX_VALUE;
        int columnRight = Integer.MIN_VALUE;
        for (int id : PERIOD_COLUMN_IDS) {
            View column = row.findViewById(id);
            columnLeft = Math.min(columnLeft, left(column, row));
            columnRight = Math.max(columnRight, left(column, row) + column.getWidth());
        }
        int limitLeft = left(limit, row);
        int limitRight = limitLeft + limit.getWidth();
        int barLeft = left(bar, row);
        int barRight = barLeft + bar.getWidth();
        assertEquals(columnLeft, limitLeft);
        assertEquals(columnRight, limitRight);
        if (row.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            assertEquals(barRight, limitRight);
        } else {
            assertEquals(barLeft, limitLeft);
        }
        assertTrue(limitLeft + " < " + barLeft, limitLeft >= barLeft);
        assertTrue(limitRight + " > " + barRight, limitRight <= barRight);
    }

    private static void assertHeaderLaidOut(View row) {
        TextView header = row.findViewById(R.id.period_header_text_view);
        int headerLeft = left(header, row);
        int headerTop = top(header, row);
        int headerRight = headerLeft + header.getWidth();
        int headerBottom = headerTop + header.getHeight();
        View section = (View) row.findViewById(R.id.period_layout).getParent();
        int sectionLeft = left(section, row);
        int margin = header.getResources().getDimensionPixelSize(
                R.dimen.material_component_lists_two_line_primary_text_end_margin);
        if (row.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            assertEquals(sectionLeft + section.getPaddingLeft() + margin, headerLeft);
        } else {
            assertEquals(sectionLeft + section.getWidth() - section.getPaddingRight() - margin, headerRight);
        }
        assertTrue(headerLeft >= 0);
        assertTrue(headerRight + " > " + row.getWidth(), headerRight <= row.getWidth());
        assertTrue(headerBottom + " > " + row.getHeight(), headerBottom <= row.getHeight());
        String text = header.getText().toString();
        Layout layout = header.getLayout();
        assertEquals(text, text.length(), layout.getLineEnd(layout.getLineCount() - 1));
        assertTrue(header.getHeight() >= header.getTotalPaddingTop() + layout.getLineBottom(layout.getLineCount() - 1)
                + header.getTotalPaddingBottom());
        for (int line = 0; line < layout.getLineCount() - 1; line++) {
            assertEquals(text, ' ', text.charAt(layout.getLineEnd(line) - 1));
        }
        for (int[] ids : new int[][] {PERIOD_COLUMN_IDS, PERIOD_BAR_IDS, PERIOD_DATE_IDS}) {
            for (int id : ids) {
                View view = row.findViewById(id);
                int left = left(view, row);
                int top = top(view, row);
                boolean apart = headerRight <= left || headerLeft >= left + view.getWidth()
                        || headerBottom <= top || headerTop >= top + view.getHeight();
                assertTrue(view.getResources().getResourceEntryName(id), apart);
            }
        }
    }

    private static int color(View view) {
        return ((ColorDrawable) view.getBackground()).getColor();
    }

    private static int periodBarColor() {
        ITheme theme = ThemeEngine.getTheme();
        return theme.getVisibleColor(theme.getColorAccent(), theme.getBestColor(theme.getColorCardBackground()));
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

    private static int reachedBarColor(View row) {
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
