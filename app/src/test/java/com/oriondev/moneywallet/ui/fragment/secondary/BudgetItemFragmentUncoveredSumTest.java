package com.oriondev.moneywallet.ui.fragment.secondary;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.SplitLedger;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.MoneyFormatter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class BudgetItemFragmentUncoveredSumTest {

    private static final long CATEGORY_LIMIT = 100000;
    private static final String COUNTED = "Counts category budgets with the same dates, inside these wallets";
    private static final String UNCOVERED = "Not in a category budget";

    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(mContext);
        SplitLedger.build(mContext);
        SplitLedger.insertCategoryBudget(mContext, 1100, SplitLedger.GROCERIES);
    }

    @Test
    public void theSumRowShowsWhatNoCategoryBudgetCoversBeforeWhatCounts() {
        insertCategoryBudget(1104, 30000, SplitLedger.FUEL, "2026-01-01", "2026-01-31");
        insertExpensesBudget(1101, "2026-01-01", "2026-01-31");
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TextView row = showSumRow(activity, 1101);
                awaitText(row, text(money(130000), line("Fuel", 30000), line("Groceries", CATEGORY_LIMIT),
                        line(UNCOVERED, 41300 - 10600 - 14500), COUNTED));
                assertEquals(View.VISIBLE, row.getVisibility());
            });
        }
    }

    @Test
    public void theSumRowLeavesTheLineOutWhenEveryLineIsCovered() {
        insertCategoryBudget(1108, 40000, SplitLedger.GROCERIES, "2026-02-01", "2026-02-28");
        insertExpensesBudget(1106, "2026-02-01", "2026-02-28");
        insertTransaction(1301, SplitLedger.GROCERIES, 700, "2026-02-10 12:00:00");
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TextView row = showSumRow(activity, 1106);
                awaitText(row, text(money(40000), line("Groceries", 40000), COUNTED));
                assertEquals(View.VISIBLE, row.getVisibility());
            });
        }
    }

    @Test
    public void theSumRowStaysHiddenForABudgetWithNoCountedCategoryBudget() {
        insertExpensesBudget(1101, "2026-01-01", "2026-01-31");
        insertExpensesBudget(1107, "2026-03-01", "2026-03-31");
        insertTransaction(1302, SplitLedger.FUEL, 500, "2026-03-10 12:00:00");
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                BudgetItemFragment fragment = addPanel(activity);
                TextView row = load(fragment, 1101).findViewById(R.id.category_sum_text_view);
                awaitText(row, text(money(CATEGORY_LIMIT), line("Groceries", CATEGORY_LIMIT),
                        line(UNCOVERED, 41300 - 10600), COUNTED));
                load(fragment, 1107);
                assertEquals(View.GONE, row.getVisibility());
            });
        }
    }

    @Test
    public void aPanelShowingAnotherBudgetShowsOnlyWhatThatOneLeavesUncovered() {
        insertCategoryBudget(1108, 40000, SplitLedger.GROCERIES, "2026-02-01", "2026-02-28");
        insertCategoryBudget(1109, 20000, SplitLedger.PHARMACY, "2026-03-01", "2026-03-31");
        insertExpensesBudget(1101, "2026-01-01", "2026-01-31");
        insertExpensesBudget(1106, "2026-02-01", "2026-02-28");
        insertExpensesBudget(1107, "2026-03-01", "2026-03-31");
        insertTransaction(1301, SplitLedger.GROCERIES, 700, "2026-02-10 12:00:00");
        insertTransaction(1302, SplitLedger.FUEL, 500, "2026-03-10 12:00:00");
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                BudgetItemFragment fragment = addPanel(activity);
                TextView row = load(fragment, 1101).findViewById(R.id.category_sum_text_view);
                awaitText(row, text(money(CATEGORY_LIMIT), line("Groceries", CATEGORY_LIMIT),
                        line(UNCOVERED, 41300 - 10600), COUNTED));
                load(fragment, 1106);
                awaitText(row, text(money(40000), line("Groceries", 40000), COUNTED));
                load(fragment, 1107);
                awaitText(row, text(money(20000), line("Pharmacy", 20000), line(UNCOVERED, 500), COUNTED));
            });
        }
    }

    private TextView showSumRow(FragmentActivity activity, long budget) {
        return load(addPanel(activity), budget).findViewById(R.id.category_sum_text_view);
    }

    private BudgetItemFragment addPanel(FragmentActivity activity) {
        BudgetItemFragment fragment = new BudgetItemFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();
        return fragment;
    }

    private View load(BudgetItemFragment fragment, long budget) {
        fragment.showItemId(budget);
        View wheel = fragment.getView().findViewById(R.id.secondary_panel_progress_wheel);
        for (int i = 0; i < 200 && wheel.getVisibility() != View.GONE; i++) {
            idle();
        }
        assertEquals("the budget never loaded", View.GONE, wheel.getVisibility());
        return fragment.getView();
    }

    private static void awaitText(TextView view, String text) {
        for (int i = 0; i < 200 && !text.equals(view.getText().toString()); i++) {
            idle();
        }
        assertEquals(text, view.getText().toString());
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
        try {
            Thread.sleep(25);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    private static String text(String... lines) {
        return String.join("\n", lines);
    }

    private static String line(String name, long money) {
        return name + ": " + money(money);
    }

    private static String money(long money) {
        CurrencyUnit currency = CurrencyManager.getCurrency("EUR");
        return MoneyFormatter.getInstance().getNotTintedString(currency, money);
    }

    private void insertCategoryBudget(long id, long money, long category, String startDate, String endDate) {
        SplitLedger.insertCategoryBudget(mContext, id, category);
        SplitLedger.database(mContext).execSQL("UPDATE budgets SET budget_money = ?, budget_start_date = ?, " +
                "budget_end_date = ? WHERE budget_id = ?", new Object[] {money, startDate, endDate, id});
    }

    private void insertTransaction(long id, long category, long money, String date) {
        SplitLedger.database(mContext).execSQL("INSERT INTO transactions (transaction_id, transaction_money, " +
                        "transaction_date, transaction_description, transaction_category, " +
                        "transaction_direction, transaction_type, transaction_wallet, " +
                        "transaction_confirmed, transaction_count_in_total, uuid, last_edit, " +
                        "deleted) VALUES (?, ?, ?, 'Spent', ?, ?, ?, ?, 1, 1, ?, 1, 0)",
                new Object[] {id, money, date, category, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD,
                        SplitLedger.CASH, "transaction-" + id});
    }

    private void insertExpensesBudget(long id, String startDate, String endDate) {
        SplitLedger.database(mContext).execSQL("INSERT INTO budgets (budget_id, budget_type, " +
                        "budget_start_date, budget_end_date, budget_money, budget_currency, " +
                        "uuid, last_edit, deleted) VALUES (?, ?, ?, ?, 999, 'EUR', ?, 1, 0)",
                new Object[] {id, Contract.BudgetType.EXPENSES.getValue(), startDate, endDate, "budget-" + id});
        for (long wallet : new long[] {SplitLedger.CASH, SplitLedger.BANK}) {
            SplitLedger.database(mContext).execSQL("INSERT INTO budget_wallets (_budget, _wallet, uuid, " +
                    "last_edit, deleted) VALUES (?, ?, ?, 1, 0)", new Object[] {id, wallet, "bw-" + id + "-" + wallet});
        }
    }
}
