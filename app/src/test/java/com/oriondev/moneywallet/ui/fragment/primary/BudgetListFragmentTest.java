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
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.SplitLedger;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.storage.wrapper.BudgetPeriodCursor;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;
import com.oriondev.moneywallet.ui.adapter.recycler.BudgetCursorAdapter;
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.DateUtils;
import com.oriondev.moneywallet.utils.MoneyFormatter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class BudgetListFragmentTest {

    private static final long CATEGORY_LIMIT = 100000;

    private static final String MONTHLY = "FREQ=MONTHLY;BYMONTHDAY=1";

    private static final long EXPENSES_BUDGET = 1310;
    private static final long OTHER_WALLET = 1311;
    private static final long CATEGORY_BUDGET = 1320;
    private static final long NOT_STARTED_BUDGET = 1330;
    private static final long ONE_OFF_BUDGET = 1340;
    private static final long EMPTY_BUDGET = 1350;
    private static final long EXPIRED_BUDGET = 1360;
    private static final long BAD_RULE_BUDGET = 1370;
    private static final long SKIPPING_RULE_BUDGET = 1380;

    private long mTransactionId = 1400;

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

    @Test
    public void aRepeatingExpensesBudgetGetsItsThreePreviousPeriodsFromAllItsOwnWallets() {
        insertPeriodBudgets();
        PreferenceManager.setCurrentWallet(mContext, EXPENSES_BUDGET);
        assertEquals(Arrays.asList(month(-3) + "=1100", month(-2) + "=2200", month(-1) + "=3300"),
                periods(false, 1).get(EXPENSES_BUDGET));
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                RecyclerView list = list(activity, false, 1);
                View view = (View) list.getParent();
                view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
                view.layout(0, 0, 1080, 1920);
                View row = list.getChildAt(0);
                assertEquals(View.VISIBLE, row.findViewById(R.id.period_layout).getVisibility());
                String date = android.text.format.DateUtils.formatDateTime(activity,
                        DateUtils.getDateFromSQLDateString(month(-3)).getTime(),
                        android.text.format.DateUtils.FORMAT_SHOW_DATE | android.text.format.DateUtils.FORMAT_ABBREV_MONTH
                                | android.text.format.DateUtils.FORMAT_NO_YEAR);
                assertEquals(activity.getString(R.string.description_budget_period, date,
                        MoneyFormatter.getInstance().getNotTintedString(CurrencyManager.getCurrency("EUR"), 1100)),
                        row.findViewById(R.id.period_column_layout_1).getContentDescription().toString());
            });
        }
    }

    @Test
    public void aRepeatingCategoryBudgetCountsOnlyItsOwnCategories() {
        insertPeriodBudgets();
        assertEquals(Arrays.asList(month(-3) + "=500", month(-2) + "=600", month(-1) + "=700"),
                periods(false, 5).get(CATEGORY_BUDGET));
    }

    @Test
    public void aBudgetThatStartsAfterTodayGetsThePeriodsBeforeTheOneTodayIsIn() {
        insertPeriodBudgets();
        assertEquals(Arrays.asList(month(-3) + "=10", month(-2) + "=20", month(-1) + "=30"),
                periods(false, 5).get(NOT_STARTED_BUDGET));
    }

    @Test
    public void aOneOffBudgetGetsNoPeriods() {
        insertPeriodBudgets();
        Map<Long, List<String>> periods = periods(false, 5);
        assertTrue(periods.containsKey(ONE_OFF_BUDGET));
        assertNull(periods.get(ONE_OFF_BUDGET));
    }

    @Test
    public void aRepeatingBudgetWithNothingInItsPreviousPeriodsGetsNoPeriods() {
        insertPeriodBudgets();
        Map<Long, List<String>> periods = periods(false, 5);
        assertTrue(periods.containsKey(EMPTY_BUDGET));
        assertNull(periods.get(EMPTY_BUDGET));
    }

    @Test
    public void aRepeatingBudgetWhoseRuleNeverMatchesGetsNoPeriodsAndTheOthersKeepTheirs() {
        insertPeriodBudgets();
        insertPeriodBudget(BAD_RULE_BUDGET, Contract.BudgetType.EXPENSES, 0, month(0), monthEnd(0),
                "FREQ=MONTHLY;BYMONTHDAY=31;BYMONTH=2", month(-6), BAD_RULE_BUDGET);
        for (int i = 0; i <= 3; i++) {
            insertTransaction(BAD_RULE_BUDGET, SplitLedger.GROCERIES, 500, month(-i));
        }
        Map<Long, List<String>> periods = periods(false, 6);
        assertTrue(periods.containsKey(BAD_RULE_BUDGET));
        assertNull(periods.get(BAD_RULE_BUDGET));
        assertEquals(Arrays.asList(month(-3) + "=1100", month(-2) + "=2200", month(-1) + "=3300"),
                periods.get(EXPENSES_BUDGET));
    }

    @Test
    public void aRepeatingBudgetWhoseRuleGivesFewerThanThreePeriodsGetsNoPeriods() {
        insertPeriodBudget(SKIPPING_RULE_BUDGET, Contract.BudgetType.EXPENSES, 0, "2026-08-31", "2099-12-31",
                "FREQ=MONTHLY;BYMONTHDAY=31", "2026-07-31", SKIPPING_RULE_BUDGET);
        insertTransaction(SKIPPING_RULE_BUDGET, SplitLedger.GROCERIES, 500, "2026-06-15");
        insertTransaction(SKIPPING_RULE_BUDGET, SplitLedger.GROCERIES, 500, "2026-08-15");
        Map<Long, List<String>> periods = periods(false, 1);
        assertTrue(periods.containsKey(SKIPPING_RULE_BUDGET));
        assertNull(periods.get(SKIPPING_RULE_BUDGET));
    }

    @Test
    public void theExpiredTabGetsNoPeriodsEvenForARepeatingBudget() {
        insertPeriodBudgets();
        Map<Long, List<String>> periods = periods(true, 2);
        assertEquals(Arrays.asList(EXPIRED_BUDGET, 1100L), new ArrayList<>(periods.keySet()));
        assertNull(periods.get(EXPIRED_BUDGET));
        assertNull(periods.get(1100L));
    }

    @Test
    public void theIdsTheListPassesCountWhatTheCardCounts() {
        insertPeriodBudgets();
        List<Long> checked = new ArrayList<>();
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                Cursor cursor = ((BudgetCursorAdapter) list(activity, false, 5).getAdapter()).getCursor();
                for (int i = 0; cursor.moveToPosition(i); i++) {
                    String[] selectionArgs = new String[] {
                            cursor.getString(cursor.getColumnIndex(Contract.Budget.TYPE)),
                            cursor.getString(cursor.getColumnIndex(Contract.Budget.CATEGORY_IDS)),
                            cursor.getString(cursor.getColumnIndex(Contract.Budget.WALLET_IDS)),
                            cursor.getString(cursor.getColumnIndex(Contract.Budget.START_DATE)),
                            cursor.getString(cursor.getColumnIndex(Contract.Budget.END_DATE))
                    };
                    try (Cursor progress = activity.getContentResolver().query(
                            DataContentProvider.CONTENT_BUDGET_PERIOD_PROGRESS, null, null, selectionArgs, null)) {
                        assertNotNull(progress);
                        assertTrue(progress.moveToFirst());
                        assertEquals(Math.abs(cursor.getLong(cursor.getColumnIndex(Contract.Budget.PROGRESS))),
                                progress.getLong(progress.getColumnIndex(Contract.Budget.PROGRESS)));
                    }
                    checked.add(cursor.getLong(cursor.getColumnIndex(Contract.Budget.ID)));
                }
                cursor.moveToPosition(-1);
            });
        }
        assertEquals(5, checked.size());
        assertTrue(checked.containsAll(Arrays.asList(EXPENSES_BUDGET, CATEGORY_BUDGET, NOT_STARTED_BUDGET,
                ONE_OFF_BUDGET, EMPTY_BUDGET)));
    }

    @Test
    public void theRunningTabKeepsItsOrderWhenEveryBudgetRepeats() {
        insertRunningBudgets();
        SplitLedger.database(mContext).execSQL("UPDATE budgets SET budget_rule = ?, budget_rule_start = " +
                "'2026-01-01' WHERE budget_id BETWEEN 1201 AND 1207", new Object[] {MONTHLY});
        Map<Long, List<String>> periods = periods(false, 7);
        assertEquals(Arrays.asList(1202L, 1201L, 1203L, 1204L, 1205L, 1206L, 1207L),
                new ArrayList<>(periods.keySet()));
        assertEquals(Arrays.asList(1202L, 1201L, 1203L, 1204L, 1205L, 1206L, 1207L), budgetIds(false, 7));
    }

    private void insertPeriodBudgets() {
        insertPeriodBudget(EXPENSES_BUDGET, Contract.BudgetType.EXPENSES, 0, month(0), monthEnd(0), MONTHLY,
                month(-6), EXPENSES_BUDGET, OTHER_WALLET);
        for (int i = 1; i <= 3; i++) {
            insertTransaction(EXPENSES_BUDGET, SplitLedger.GROCERIES, 1000 * (4 - i), month(-i));
            insertTransaction(OTHER_WALLET, SplitLedger.GROCERIES, 100 * (4 - i), month(-i));
        }
        insertTransaction(EXPENSES_BUDGET, SplitLedger.GROCERIES, 50, month(0));
        insertPeriodBudget(CATEGORY_BUDGET, Contract.BudgetType.CATEGORY, SplitLedger.GROCERIES, month(0),
                monthEnd(0), MONTHLY, month(-6), CATEGORY_BUDGET);
        for (int i = 1; i <= 3; i++) {
            insertTransaction(CATEGORY_BUDGET, SplitLedger.GROCERIES, 400 + 100 * (4 - i), month(-i));
            insertTransaction(CATEGORY_BUDGET, SplitLedger.HOUSEHOLD, 7000, month(-i));
        }
        insertTransaction(CATEGORY_BUDGET, SplitLedger.GROCERIES, 60, month(0));
        insertTransaction(CATEGORY_BUDGET, SplitLedger.HOUSEHOLD, 70, month(0));
        insertPeriodBudget(NOT_STARTED_BUDGET, Contract.BudgetType.EXPENSES, 0, month(1), monthEnd(1), MONTHLY,
                month(-6), NOT_STARTED_BUDGET);
        for (int i = 0; i <= 3; i++) {
            insertTransaction(NOT_STARTED_BUDGET, SplitLedger.GROCERIES, 10 * (4 - i), month(-i));
        }
        insertPeriodBudget(ONE_OFF_BUDGET, Contract.BudgetType.EXPENSES, 0, month(0), monthEnd(0), null, null,
                ONE_OFF_BUDGET);
        for (int i = 0; i <= 3; i++) {
            insertTransaction(ONE_OFF_BUDGET, SplitLedger.GROCERIES, 500, month(-i));
        }
        insertPeriodBudget(EMPTY_BUDGET, Contract.BudgetType.EXPENSES, 0, month(0), monthEnd(0), MONTHLY,
                month(-6), EMPTY_BUDGET);
        insertTransaction(EMPTY_BUDGET, SplitLedger.GROCERIES, 900, month(0));
        insertTransaction(EMPTY_BUDGET, SplitLedger.GROCERIES, 800, month(-4));
        insertPeriodBudget(EXPIRED_BUDGET, Contract.BudgetType.EXPENSES, 0, month(-2), monthEnd(-2), MONTHLY,
                month(-8), EXPIRED_BUDGET);
        for (int i = 3; i <= 5; i++) {
            insertTransaction(EXPIRED_BUDGET, SplitLedger.GROCERIES, 600, month(-i));
        }
    }

    private void insertPeriodBudget(long id, Contract.BudgetType type, long category, String startDate,
                                    String endDate, String rule, String ruleStart, long... wallets) {
        for (long wallet : wallets) {
            SplitLedger.database(mContext).execSQL("INSERT INTO wallets (wallet_id, wallet_name, wallet_icon, " +
                            "wallet_currency, wallet_count_in_total, uuid, last_edit, deleted) VALUES (?, ?, ?, " +
                            "'EUR', 1, ?, 1, 0)",
                    new Object[] {wallet, "Wallet " + wallet, SplitLedger.ICON, "wallet-" + wallet});
        }
        SplitLedger.database(mContext).execSQL("INSERT INTO budgets (budget_id, budget_type, budget_category, " +
                        "budget_start_date, budget_end_date, budget_money, budget_currency, budget_rule, " +
                        "budget_rule_start, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, 2000, 'EUR', ?, ?, ?, 1, 0)",
                new Object[] {id, type.getValue(), category != 0 ? category : null, startDate, endDate, rule,
                        ruleStart, "budget-" + id});
        for (long wallet : wallets) {
            SplitLedger.database(mContext).execSQL("INSERT INTO budget_wallets (_budget, _wallet, uuid, " +
                    "last_edit, deleted) VALUES (?, ?, ?, 1, 0)", new Object[] {id, wallet, "bw-" + id + "-" + wallet});
        }
        if (category != 0) {
            SplitLedger.database(mContext).execSQL("INSERT INTO budget_categories (_budget, _category, uuid, " +
                            "last_edit, deleted) VALUES (?, ?, ?, 1, 0)",
                    new Object[] {id, category, "bc-" + id + "-" + category});
        }
    }

    private void insertTransaction(long wallet, long category, long money, String date) {
        long id = mTransactionId++;
        SplitLedger.database(mContext).execSQL("INSERT INTO transactions (transaction_id, " +
                        "transaction_money, transaction_date, transaction_description, " +
                        "transaction_category, transaction_direction, transaction_type, transaction_wallet, " +
                        "transaction_confirmed, transaction_count_in_total, uuid, last_edit, deleted) VALUES " +
                        "(?, ?, ?, ?, ?, ?, ?, ?, 1, 1, ?, 1, 0)",
                new Object[] {id, money, date + " 00:00:00", "transaction-" + id, category,
                        Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet, "transaction-" + id});
    }

    private static String month(int offset) {
        Calendar calendar = Calendar.getInstance();
        DateUtils.setTime(calendar, 0, 0, 0, 0);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.add(Calendar.MONTH, offset);
        return DateUtils.getSQLDateString(calendar.getTime());
    }

    private static String monthEnd(int offset) {
        return DateUtils.getSQLDateString(DateUtils.addDays(DateUtils.getCalendar(
                DateUtils.getDateFromSQLDateString(month(offset + 1))), -1));
    }

    private static Map<Long, List<String>> periods(boolean expired, int count) {
        Map<Long, List<String>> periods = new LinkedHashMap<>();
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                Cursor cursor = ((BudgetCursorAdapter) list(activity, expired, count).getAdapter()).getCursor();
                assertTrue(expired || cursor instanceof BudgetPeriodCursor);
                for (int i = 0; cursor.moveToPosition(i); i++) {
                    List<String> budgetPeriods = null;
                    if (cursor instanceof BudgetPeriodCursor) {
                        Date[] startDates = ((BudgetPeriodCursor) cursor).getPeriodStartDates();
                        long[] progress = ((BudgetPeriodCursor) cursor).getPeriodProgress();
                        assertEquals(startDates == null, progress == null);
                        if (startDates != null) {
                            budgetPeriods = new ArrayList<>();
                            for (int j = 0; j < startDates.length; j++) {
                                budgetPeriods.add(DateUtils.getSQLDateString(startDates[j]) + "=" + progress[j]);
                            }
                        }
                    }
                    periods.put(cursor.getLong(cursor.getColumnIndex(Contract.Budget.ID)), budgetPeriods);
                }
                cursor.moveToPosition(-1);
            });
        }
        assertFalse(periods.isEmpty());
        return periods;
    }

    private static RecyclerView list(BackupListActivity activity, boolean expired, int count) {
        BudgetListFragment fragment = BudgetListFragment.newInstance(expired);
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();
        RecyclerView list = ((AdvancedRecyclerView) fragment.getView()).getRecyclerView();
        for (int i = 0; i < 200 && list.getAdapter().getItemCount() < count; i++) {
            idle();
        }
        assertEquals(count, list.getAdapter().getItemCount());
        return list;
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
