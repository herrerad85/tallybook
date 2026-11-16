package com.oriondev.moneywallet.ui.fragment.primary;

import android.content.Context;
import android.database.Cursor;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.SplitLedger;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.MoneyFormatter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class BudgetListFragmentTest {

    private static final long CATEGORY_LIMIT = 100000;

    private Context mContext;
    private long mCurrentWallet;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(mContext);
        SplitLedger.build(mContext);
        SplitLedger.insertCategoryBudget(mContext, 1100, SplitLedger.GROCERIES);
        mCurrentWallet = PreferenceManager.getCurrentWallet();
        PreferenceManager.setCurrentWallet(mContext, PreferenceManager.TOTAL_WALLET_ID);
    }

    @After
    public void tearDown() {
        PreferenceManager.setCurrentWallet(mContext, mCurrentWallet);
    }

    @Test
    public void theCardOfAFollowedBudgetShowsTheSumAsItsLimit() {
        SplitLedger.database(mContext).execSQL("INSERT INTO budgets (budget_id, budget_type, " +
                        "budget_start_date, budget_end_date, budget_money, budget_currency, budget_tag, " +
                        "uuid, last_edit, deleted) VALUES (1101, ?, '2026-01-01', '2026-01-31', 999, 'EUR', ?, " +
                        "'budget-1101', 1, 0)",
                new Object[] {Contract.BudgetType.EXPENSES.getValue(), Contract.Budget.TAG_FOLLOWS_CATEGORIES});
        for (long wallet : new long[] {SplitLedger.CASH, SplitLedger.BANK}) {
            SplitLedger.database(mContext).execSQL("INSERT INTO budget_wallets (_budget, _wallet, uuid, " +
                    "last_edit, deleted) VALUES (1101, ?, ?, 1, 0)", new Object[] {wallet, "bw-1101-" + wallet});
        }
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                BudgetListFragment fragment = BudgetListFragment.newInstance(true);
                activity.getSupportFragmentManager()
                        .beginTransaction()
                        .add(android.R.id.content, fragment)
                        .commitNow();
                RecyclerView list = ((AdvancedRecyclerView) fragment.getView()).getRecyclerView();
                for (int i = 0; i < 200 && list.getAdapter().getItemCount() < 2; i++) {
                    idle();
                }
                assertEquals(2, list.getAdapter().getItemCount());
                View view = fragment.getView();
                view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
                view.layout(0, 0, 1080, 1920);
                TextView limit = null;
                for (int i = 0; i < list.getChildCount(); i++) {
                    View row = list.getChildAt(i);
                    TextView name = row.findViewById(R.id.primary_text_view);
                    if (activity.getString(R.string.hint_expenses).equals(name.getText().toString())) {
                        limit = row.findViewById(R.id.money_text_view);
                    }
                }
                assertNotNull(limit);
                assertEquals(MoneyFormatter.getInstance().getNotTintedString(CurrencyManager.getCurrency("EUR"),
                        CATEGORY_LIMIT), limit.getText().toString());
            });
        }
    }

    @Test
    public void theRunningTabPutsOverspentExpensesFirstThenEveryBudgetByTheFractionUsed() {
        insertRunningBudgets();
        assertEquals(Arrays.asList(1202L, 1201L, 1203L, 1204L, 1205L, 1206L, 1207L), budgetIds(false, 7));
    }

    @Test
    public void anOverspentExpenseCategoryBudgetSortsWithTheOverspentOnes() {
        insertRunningBudgets();
        insertBudget(1208, Contract.BudgetType.CATEGORY, SplitLedger.GROCERIES, "2026-01-01", "2099-07-31",
                100000, 120000);
        assertEquals(Arrays.asList(1202L, 1208L, 1201L, 1203L, 1204L, 1205L, 1206L, 1207L), budgetIds(false, 8));
    }

    @Test
    public void anIncomeCategoryBudgetOverItsLimitSortsWithTheOtherBudgetsByTheFractionUsed() {
        insertRunningBudgets();
        insertBudget(1213, Contract.BudgetType.CATEGORY, SplitLedger.SALARY, "2026-01-01", "2099-07-31",
                100000, 120000);
        assertEquals(Arrays.asList(1202L, 1201L, 1203L, 1213L, 1204L, 1205L, 1206L, 1207L), budgetIds(false, 8));
    }

    @Test
    public void aFollowedBudgetOverTheSumOfItsCategoryBudgetsSortsWithTheOverspentOnes() {
        insertRunningBudgets();
        insertBudget(1217, Contract.BudgetType.CATEGORY, SplitLedger.GROCERIES, "2026-01-01", "2099-07-31",
                50000, 60000);
        SplitLedger.database(mContext).execSQL("INSERT INTO budgets (budget_id, budget_type, " +
                        "budget_start_date, budget_end_date, budget_money, budget_currency, budget_tag, " +
                        "uuid, last_edit, deleted) VALUES (1216, ?, '2026-01-01', '2099-07-31', 100000, 'EUR', ?, " +
                        "'budget-1216', 1, 0)",
                new Object[] {Contract.BudgetType.EXPENSES.getValue(), Contract.Budget.TAG_FOLLOWS_CATEGORIES});
        SplitLedger.database(mContext).execSQL("INSERT INTO budget_wallets (_budget, _wallet, uuid, " +
                "last_edit, deleted) VALUES (1216, 1217, 'bw-1216-1217', 1, 0)");
        assertEquals(Arrays.asList(1202L, 1216L, 1217L, 1201L, 1203L, 1204L, 1205L, 1206L, 1207L),
                budgetIds(false, 9));
    }

    @Test
    public void aFollowedBudgetStoredWithAZeroLimitSortsByTheFractionOfTheSumOfItsCategoryBudgets() {
        insertRunningBudgets();
        insertBudget(1220, Contract.BudgetType.CATEGORY, SplitLedger.GROCERIES, "2026-01-01", "2099-07-31",
                100000, 60000);
        SplitLedger.database(mContext).execSQL("INSERT INTO budgets (budget_id, budget_type, " +
                        "budget_start_date, budget_end_date, budget_money, budget_currency, budget_tag, " +
                        "uuid, last_edit, deleted) VALUES (1219, ?, '2026-01-01', '2099-07-31', 0, 'EUR', ?, " +
                        "'budget-1219', 1, 0)",
                new Object[] {Contract.BudgetType.EXPENSES.getValue(), Contract.Budget.TAG_FOLLOWS_CATEGORIES});
        SplitLedger.database(mContext).execSQL("INSERT INTO budget_wallets (_budget, _wallet, uuid, " +
                "last_edit, deleted) VALUES (1219, 1220, 'bw-1219-1220', 1, 0)");
        assertEquals(Arrays.asList(1202L, 1201L, 1203L, 1204L, 1219L, 1220L, 1205L, 1206L, 1207L),
                budgetIds(false, 9));
    }

    @Test
    public void anExpensesBudgetWithAZeroLimitAndSomeExpensesSortsLastWithTheOverspentOnes() {
        insertRunningBudgets();
        insertBudget(1218, Contract.BudgetType.EXPENSES, 0, "2026-01-01", "2099-10-31", 0, 20000);
        assertEquals(Arrays.asList(1202L, 1201L, 1218L, 1203L, 1204L, 1205L, 1206L, 1207L), budgetIds(false, 8));
    }

    @Test
    public void runningBudgetsAtTheSameFractionKeepTheEndDateOrder() {
        insertBudget(1209, Contract.BudgetType.EXPENSES, 0, "2026-01-01", "2099-09-30", 100000, 30000);
        insertBudget(1210, Contract.BudgetType.EXPENSES, 0, "2026-01-01", "2099-08-31", 100000, 30000);
        assertEquals(Arrays.asList(1210L, 1209L), budgetIds(false, 2));
    }

    @Test
    public void runningBudgetsAtTheSameFractionAndEndDateKeepTheIdOrder() {
        insertBudget(1215, Contract.BudgetType.EXPENSES, 0, "2026-01-01", "2099-08-31", 100000, 30000);
        insertBudget(1214, Contract.BudgetType.EXPENSES, 0, "2026-01-01", "2099-08-31", 100000, 30000);
        assertEquals(Arrays.asList(1214L, 1215L), budgetIds(false, 2));
    }

    @Test
    public void theExpiredTabKeepsTheLatestEndDateFirst() {
        insertRunningBudgets();
        insertBudget(1211, Contract.BudgetType.EXPENSES, 0, "2026-01-01", "2026-02-28", 100000, 150000);
        insertBudget(1212, Contract.BudgetType.EXPENSES, 0, "2026-01-01", "2026-03-31", 100000, 10000);
        assertEquals(Arrays.asList(1212L, 1211L, 1100L), budgetIds(true, 3));
    }

    private void insertRunningBudgets() {
        insertBudget(1201, Contract.BudgetType.EXPENSES, 0, "2026-01-01", "2099-01-31", 100000, 110000);
        insertBudget(1202, Contract.BudgetType.EXPENSES, 0, "2026-01-01", "2099-02-28", 100000, 150000);
        insertBudget(1203, Contract.BudgetType.INCOMES, 0, "2026-01-01", "2099-03-31", 100000, 200000);
        insertBudget(1204, Contract.BudgetType.EXPENSES, 0, "2026-01-01", "2099-06-30", 100000, 70000);
        insertBudget(1205, Contract.BudgetType.EXPENSES, 0, "2026-01-01", "2099-05-31", 100000, 50000);
        insertBudget(1206, Contract.BudgetType.EXPENSES, 0, "2026-01-01", "2099-04-30", 100000, 0);
        insertBudget(1207, Contract.BudgetType.EXPENSES, 0, "2026-01-01", "2098-12-31", 0, 0);
    }

    private void insertBudget(long id, Contract.BudgetType type, long category, String startDate, String endDate,
                              long limit, long progress) {
        SplitLedger.database(mContext).execSQL("INSERT INTO wallets (wallet_id, wallet_name, wallet_icon, " +
                        "wallet_currency, wallet_count_in_total, uuid, last_edit, deleted) VALUES (?, ?, ?, " +
                        "'EUR', 1, ?, 1, 0)",
                new Object[] {id, "Wallet " + id, SplitLedger.ICON, "wallet-" + id});
        SplitLedger.database(mContext).execSQL("INSERT INTO budgets (budget_id, budget_type, budget_category, " +
                        "budget_start_date, budget_end_date, budget_money, budget_currency, uuid, last_edit, " +
                        "deleted) VALUES (?, ?, ?, ?, ?, ?, 'EUR', ?, 1, 0)",
                new Object[] {id, type.getValue(), category != 0 ? category : null, startDate, endDate, limit,
                        "budget-" + id});
        SplitLedger.database(mContext).execSQL("INSERT INTO budget_wallets (_budget, _wallet, uuid, " +
                "last_edit, deleted) VALUES (?, ?, ?, 1, 0)", new Object[] {id, id, "bw-" + id + "-" + id});
        if (category != 0) {
            SplitLedger.database(mContext).execSQL("INSERT INTO budget_categories (_budget, _category, uuid, " +
                            "last_edit, deleted) VALUES (?, ?, ?, 1, 0)",
                    new Object[] {id, category, "bc-" + id + "-" + category});
        }
        if (progress > 0) {
            long transactionCategory = category;
            if (type != Contract.BudgetType.CATEGORY) {
                transactionCategory = type == Contract.BudgetType.INCOMES ? SplitLedger.SALARY : SplitLedger.GROCERIES;
            }
            boolean incomes;
            try (Cursor cursor = SplitLedger.database(mContext).rawQuery("SELECT category_type FROM categories " +
                    "WHERE category_id = ?", new String[] {String.valueOf(transactionCategory)})) {
                assertTrue(cursor.moveToFirst());
                incomes = cursor.getInt(0) == Contract.CategoryType.INCOME.getValue();
            }
            SplitLedger.database(mContext).execSQL("INSERT INTO transactions (transaction_id, " +
                            "transaction_money, transaction_date, transaction_description, " +
                            "transaction_category, transaction_direction, transaction_type, transaction_wallet, " +
                            "transaction_confirmed, transaction_count_in_total, uuid, last_edit, deleted) VALUES " +
                            "(?, ?, ?, ?, ?, ?, ?, ?, 1, 1, ?, 1, 0)",
                    new Object[] {id, progress, SplitLedger.DATE, "budget-" + id,
                            transactionCategory,
                            incomes ? Contract.Direction.INCOME : Contract.Direction.EXPENSE,
                            Contract.TransactionType.STANDARD, id, "transaction-" + id});
        }
    }

    private static List<Long> budgetIds(boolean expired, int count) {
        List<Long> ids = new ArrayList<>();
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                BudgetListFragment fragment = BudgetListFragment.newInstance(expired);
                activity.getSupportFragmentManager()
                        .beginTransaction()
                        .add(android.R.id.content, fragment)
                        .commitNow();
                RecyclerView list = ((AdvancedRecyclerView) fragment.getView()).getRecyclerView();
                for (int i = 0; i < 200 && list.getAdapter().getItemCount() < count; i++) {
                    idle();
                }
                for (int i = 0; i < list.getAdapter().getItemCount(); i++) {
                    ids.add(list.getAdapter().getItemId(i));
                }
            });
        }
        return ids;
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
        try {
            Thread.sleep(25);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }
}
