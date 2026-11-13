package com.oriondev.moneywallet.ui.fragment.secondary;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.SplitLedger;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.MoneyFormatter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class BudgetItemFragmentTest {

    private static final long CATEGORY_LIMIT = 100000;
    private static final long PACE = 990;
    private static final String COUNTED = "Counts category budgets with the same dates, inside these wallets";
    private static final String UNCOVERED = "Not in a category budget";

    private Context mContext;
    private TimeZone mTimeZone;

    @Before
    public void setUp() {
        mTimeZone = TimeZone.getDefault();
        mContext = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(mContext);
        SplitLedger.build(mContext);
        SplitLedger.insertCategoryBudget(mContext, 1100, SplitLedger.GROCERIES);
    }

    @After
    public void tearDown() {
        BudgetItemFragment.sToday = null;
        TimeZone.setDefault(mTimeZone);
    }

    @Test
    public void tappingTheSumMakesItTheLimit() {
        insertExpensesBudget(1101, 999, null);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                View row = showSumRow(activity, 1101);
                assertEquals(View.VISIBLE, row.getVisibility());
                assertTrue(row.isClickable());
                row.performClick();
            });
        }
        assertEquals(CATEGORY_LIMIT, storedMoneyOf(1101));
    }

    @Test
    public void aFollowedBudgetAndOneAlreadyAtTheSumCannotBeTapped() {
        insertExpensesBudget(1102, 999, Contract.Budget.TAG_FOLLOWS_CATEGORIES);
        insertExpensesBudget(1103, CATEGORY_LIMIT, null);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                for (long budget : new long[] {1102, 1103}) {
                    View row = showSumRow(activity, budget);
                    assertEquals(View.VISIBLE, row.getVisibility());
                    assertFalse(row.isClickable());
                }
            });
        }
    }

    @Test
    public void spentPerDayDividesWhatWasSpentOverTheDaysSoFarCountingToday() {
        insertPaceWallet();
        insertPaceTransaction(1201, SplitLedger.GROCERIES, Contract.Direction.EXPENSE, 1000);
        insertPaceBudget(1202, Contract.BudgetType.EXPENSES, null);
        insertPaceBudget(1203, Contract.BudgetType.CATEGORY, SplitLedger.GROCERIES);
        pinToday(2026, 1, 7);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                for (long budget : new long[] {1202, 1203}) {
                    TextView row = showPanel(activity, budget).findViewById(R.id.spent_per_day_text_view);
                    assertEquals(View.VISIBLE, row.getVisibility());
                    assertEquals(money(1000 / 7), row.getText().toString());
                }
            });
        }
    }

    @Test
    public void onTheStartDaySpentPerDayIsWhatWasSpentThatDay() {
        insertPaceWallet();
        insertPaceTransaction(1201, SplitLedger.GROCERIES, Contract.Direction.EXPENSE, 1000,
                "2026-01-01 09:00:00");
        insertPaceBudget(1202, Contract.BudgetType.EXPENSES, null);
        pinToday(2026, 1, 1);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TextView row = showPanel(activity, 1202).findViewById(R.id.spent_per_day_text_view);
                assertEquals(View.VISIBLE, row.getVisibility());
                assertEquals(money(1000), row.getText().toString());
            });
        }
    }

    @Test
    public void spentPerDayCountsWholeDaysAcrossAClockChange() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        insertPaceWallet();
        insertPaceTransaction(1201, SplitLedger.GROCERIES, Contract.Direction.EXPENSE, 1000,
                "2026-03-05 12:00:00");
        insertPaceBudget(1202, Contract.BudgetType.EXPENSES, null, "2026-03-01", "2026-03-31");
        pinToday(2026, 3, 10);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TextView row = showPanel(activity, 1202).findViewById(R.id.spent_per_day_text_view);
                assertEquals(View.VISIBLE, row.getVisibility());
                assertEquals(money(1000 / 10), row.getText().toString());
            });
        }
    }

    @Test
    public void aPanelShowingAnIncomeBudgetAfterAnExpenseOneHidesSpentPerDay() {
        insertPaceWallet();
        insertPaceTransaction(1201, SplitLedger.GROCERIES, Contract.Direction.EXPENSE, 1000);
        insertPaceBudget(1202, Contract.BudgetType.EXPENSES, null);
        insertPaceBudget(1203, Contract.BudgetType.INCOMES, null);
        pinToday(2026, 1, 7);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                BudgetItemFragment fragment = addPanel(activity);
                View row = load(fragment, 1202).findViewById(R.id.spent_per_day_text_view);
                assertEquals(View.VISIBLE, row.getVisibility());
                row = load(fragment, 1203).findViewById(R.id.spent_per_day_text_view);
                assertEquals(View.GONE, row.getVisibility());
            });
        }
    }

    @Test
    public void anExpiredBudgetSpreadsWhatWasSpentOverItsWholePeriod() {
        insertPaceWallet();
        insertPaceTransaction(1201, SplitLedger.GROCERIES, Contract.Direction.EXPENSE, 1000);
        insertPaceBudget(1202, Contract.BudgetType.EXPENSES, null);
        pinToday(2026, 3, 1);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TextView row = showPanel(activity, 1202).findViewById(R.id.spent_per_day_text_view);
                assertEquals(View.VISIBLE, row.getVisibility());
                assertEquals(money(1000 / 31), row.getText().toString());
            });
        }
    }

    @Test
    public void spentPerDayIsHiddenBeforeTheBudgetStarts() {
        insertPaceWallet();
        insertPaceBudget(1202, Contract.BudgetType.EXPENSES, null);
        pinToday(2025, 12, 20);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                View row = showPanel(activity, 1202).findViewById(R.id.spent_per_day_text_view);
                assertEquals(View.GONE, row.getVisibility());
            });
        }
    }

    @Test
    public void spentPerDayIsHiddenWhenTheStartIsAfterTheEnd() {
        insertPaceWallet();
        insertPaceBudget(1202, Contract.BudgetType.EXPENSES, null, "2026-02-01", "2026-01-31");
        insertPaceBudget(1203, Contract.BudgetType.EXPENSES, null, "2026-02-02", "2026-01-31");
        pinToday(2026, 3, 1);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                for (long budget : new long[] {1202, 1203}) {
                    View row = showPanel(activity, budget).findViewById(R.id.spent_per_day_text_view);
                    assertEquals(View.GONE, row.getVisibility());
                }
            });
        }
    }

    @Test
    public void spentPerDayIsHiddenOnIncomeBudgets() {
        insertPaceWallet();
        insertPaceTransaction(1201, SplitLedger.SALARY, Contract.Direction.INCOME, 1000);
        insertPaceBudget(1202, Contract.BudgetType.INCOMES, null);
        insertPaceBudget(1203, Contract.BudgetType.CATEGORY, SplitLedger.SALARY);
        pinToday(2026, 1, 7);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                for (long budget : new long[] {1202, 1203}) {
                    View row = showPanel(activity, budget).findViewById(R.id.spent_per_day_text_view);
                    assertEquals(View.GONE, row.getVisibility());
                }
            });
        }
    }

    @Test
    public void theSumRowListsEachCountedCategoryBudgetByNameAndWhatCounts() {
        insertCategoryBudget(1104, 30000, SplitLedger.PHARMACY);
        insertCategoryBudget(1105, 20000, SplitLedger.FUEL);
        insertCategoryBudget(1109, 70000, SplitLedger.SALARY);
        insertExpensesBudget(1101, 999, null);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TextView row = showSumRow(activity, 1101);
                awaitText(row, text(money(150000), line("Fuel", 20000), line("Groceries", CATEGORY_LIMIT),
                        line("Pharmacy", 30000), line(UNCOVERED, 41300 - 10600 - 14500 - 2500), COUNTED));
                assertEquals(View.VISIBLE, row.getVisibility());
            });
        }
    }

    @Test
    public void theSumRowHidesForABudgetWithNoCountedCategoryBudget() {
        insertExpensesBudget(1101, 999, null);
        insertExpensesBudget(1106, 999, null, "2026-02-01", "2026-02-28");
        SlowDataContentProvider provider = Robolectric.setupContentProvider(SlowDataContentProvider.class,
                DataContentProvider.CONTENT_BUDGETS.getAuthority());
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                BudgetItemFragment fragment = addPanel(activity);
                TextView row = load(fragment, 1101).findViewById(R.id.category_sum_text_view);
                awaitText(row, text(money(CATEGORY_LIMIT), line("Groceries", CATEGORY_LIMIT),
                        line(UNCOVERED, 41300 - 10600), COUNTED));
                SlowDataContentProvider.watch(categoryBudgetsUri(1106));
                load(fragment, 1106);
                awaitCountDown(SlowDataContentProvider.sRead);
                assertEquals(View.GONE, row.getVisibility());
            });
        } finally {
            SlowDataContentProvider.reset();
            provider.shutdown();
        }
    }

    @Test
    public void theSumRowIsTheSameWhicheverLoaderFinishesFirst() {
        insertCategoryBudget(1104, 30000, SplitLedger.FUEL);
        insertExpensesBudget(1101, 999, null);
        String full = text(money(130000), line("Fuel", 30000), line("Groceries", CATEGORY_LIMIT),
                line(UNCOVERED, 41300 - 10600 - 14500), COUNTED);
        SlowDataContentProvider provider = Robolectric.setupContentProvider(SlowDataContentProvider.class,
                DataContentProvider.CONTENT_BUDGETS.getAuthority());
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                BudgetItemFragment fragment = addPanel(activity);
                SlowDataContentProvider.hold(categoryBudgetsUri(1101));
                TextView row = load(fragment, 1101).findViewById(R.id.category_sum_text_view);
                assertEquals(text(money(130000), line(UNCOVERED, 41300 - 10600 - 14500), COUNTED),
                        row.getText().toString());
                SlowDataContentProvider.sRelease.countDown();
                awaitText(row, full);
                SlowDataContentProvider.hold(ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, 1101));
                SlowDataContentProvider.watch(categoryBudgetsUri(1101));
                fragment.showItemId(1101);
                awaitCountDown(SlowDataContentProvider.sRead);
                SlowDataContentProvider.sRelease.countDown();
                awaitLoaded(fragment);
                assertEquals(full, row.getText().toString());
                assertEquals(View.VISIBLE, row.getVisibility());
            });
        } finally {
            SlowDataContentProvider.reset();
            provider.shutdown();
        }
    }

    @Test
    public void theSumRowListsEachCategoryBudgetOnceAfterTheDataChanges() {
        insertCategoryBudget(1104, 30000, SplitLedger.FUEL);
        insertExpensesBudget(1101, 999, null);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TextView row = showSumRow(activity, 1101);
                awaitText(row, text(money(130000), line("Fuel", 30000), line("Groceries", CATEGORY_LIMIT),
                        line(UNCOVERED, 41300 - 10600 - 14500), COUNTED));
                mContext.getContentResolver().delete(
                        ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, 1104), null, null);
                awaitText(row, text(money(CATEGORY_LIMIT), line("Groceries", CATEGORY_LIMIT),
                        line(UNCOVERED, 41300 - 10600), COUNTED));
                assertEquals(View.VISIBLE, row.getVisibility());
            });
        }
    }

    @Test
    public void aPanelShowingASecondBudgetListsOnlyItsOwnCategoryBudgets() {
        insertCategoryBudget(1104, 30000, SplitLedger.FUEL);
        insertCategoryBudget(1108, 40000, SplitLedger.PHARMACY, "2026-02-01", "2026-02-28");
        insertExpensesBudget(1101, 999, null);
        insertExpensesBudget(1106, 999, null, "2026-02-01", "2026-02-28");
        SlowDataContentProvider provider = Robolectric.setupContentProvider(SlowDataContentProvider.class,
                DataContentProvider.CONTENT_BUDGETS.getAuthority());
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                BudgetItemFragment fragment = addPanel(activity);
                TextView row = load(fragment, 1101).findViewById(R.id.category_sum_text_view);
                awaitText(row, text(money(130000), line("Fuel", 30000), line("Groceries", CATEGORY_LIMIT),
                        line(UNCOVERED, 41300 - 10600 - 14500), COUNTED));
                SlowDataContentProvider.hold(categoryBudgetsUri(1106));
                load(fragment, 1106);
                assertEquals(text(money(40000), COUNTED), row.getText().toString());
                SlowDataContentProvider.sRelease.countDown();
                awaitText(row, text(money(40000), line("Pharmacy", 40000), COUNTED));
            });
        } finally {
            SlowDataContentProvider.reset();
            provider.shutdown();
        }
    }

    @Test
    public void tappingTheSumUnderItsListStillMakesTheSumTheLimit() {
        insertCategoryBudget(1104, 30000, SplitLedger.FUEL);
        insertExpensesBudget(1101, 999, null);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TextView row = showSumRow(activity, 1101);
                awaitText(row, text(money(130000), line("Fuel", 30000), line("Groceries", CATEGORY_LIMIT),
                        line(UNCOVERED, 41300 - 10600 - 14500), COUNTED));
                assertTrue(row.isClickable());
                row.performClick();
            });
        }
        assertEquals(130000, storedMoneyOf(1101));
    }

    private TextView showSumRow(FragmentActivity activity, long budget) {
        return showPanel(activity, budget).findViewById(R.id.category_sum_text_view);
    }

    private View showPanel(FragmentActivity activity, long budget) {
        return load(addPanel(activity), budget);
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
        return awaitLoaded(fragment);
    }

    private static View awaitLoaded(BudgetItemFragment fragment) {
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

    private static void awaitCountDown(CountDownLatch latch) {
        for (int i = 0; i < 200 && latch.getCount() > 0; i++) {
            idle();
        }
        assertEquals("the category budgets never loaded", 0, latch.getCount());
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
        try {
            Thread.sleep(25);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    private static Uri categoryBudgetsUri(long budget) {
        return Uri.withAppendedPath(ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budget),
                "category_budgets");
    }

    private static String text(String... lines) {
        return String.join("\n", lines);
    }

    private static String line(String name, long money) {
        return name + ": " + money(money);
    }

    private void insertCategoryBudget(long id, long money, long category) {
        insertCategoryBudget(id, money, category, "2026-01-01", "2026-01-31");
    }

    private void insertCategoryBudget(long id, long money, long category, String startDate, String endDate) {
        SplitLedger.insertCategoryBudget(mContext, id, category);
        SplitLedger.database(mContext).execSQL("UPDATE budgets SET budget_money = ?, budget_start_date = ?, " +
                "budget_end_date = ? WHERE budget_id = ?", new Object[] {money, startDate, endDate, id});
    }

    private void insertPaceWallet() {
        SplitLedger.database(mContext).execSQL("INSERT INTO wallets (wallet_id, wallet_name, wallet_icon, " +
                        "wallet_currency, wallet_count_in_total, uuid, last_edit, deleted) VALUES (?, 'Pace', ?, " +
                        "'EUR', 1, ?, 1, 0)",
                new Object[] {PACE, SplitLedger.ICON, "wallet-" + PACE});
    }

    private void insertPaceTransaction(long id, long category, int direction, long money) {
        insertPaceTransaction(id, category, direction, money, "2026-01-05 12:00:00");
    }

    private void insertPaceTransaction(long id, long category, int direction, long money, String date) {
        SplitLedger.database(mContext).execSQL("INSERT INTO transactions (transaction_id, transaction_money, " +
                        "transaction_date, transaction_description, transaction_category, " +
                        "transaction_direction, transaction_type, transaction_wallet, " +
                        "transaction_confirmed, transaction_count_in_total, uuid, last_edit, " +
                        "deleted) VALUES (?, ?, ?, 'Pace', ?, ?, ?, ?, 1, 1, ?, 1, 0)",
                new Object[] {id, money, date, category, direction, Contract.TransactionType.STANDARD, PACE,
                        "transaction-" + id});
    }

    private void insertPaceBudget(long id, Contract.BudgetType type, Long category) {
        insertPaceBudget(id, type, category, "2026-01-01", "2026-01-31");
    }

    private void insertPaceBudget(long id, Contract.BudgetType type, Long category, String startDate,
                                  String endDate) {
        SplitLedger.database(mContext).execSQL("INSERT INTO budgets (budget_id, budget_type, budget_category, " +
                        "budget_start_date, budget_end_date, budget_money, budget_currency, uuid, last_edit, " +
                        "deleted) VALUES (?, ?, ?, ?, ?, 50000, 'EUR', ?, 1, 0)",
                new Object[] {id, type.getValue(), category, startDate, endDate, "budget-" + id});
        SplitLedger.database(mContext).execSQL("INSERT INTO budget_wallets (_budget, _wallet, uuid, " +
                "last_edit, deleted) VALUES (?, ?, ?, 1, 0)", new Object[] {id, PACE, "bw-" + id + "-" + PACE});
        if (category != null) {
            SplitLedger.database(mContext).execSQL("INSERT INTO budget_categories (_budget, _category, uuid, " +
                    "last_edit, deleted) VALUES (?, ?, ?, 1, 0)", new Object[] {id, category, "bc-" + id + "-" + category});
        }
    }

    private static void pinToday(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day, 12, 0);
        BudgetItemFragment.sToday = calendar.getTimeInMillis();
    }

    private static String money(long money) {
        CurrencyUnit currency = CurrencyManager.getCurrency("EUR");
        return MoneyFormatter.getInstance().getNotTintedString(currency, money);
    }

    private void insertExpensesBudget(long id, long money, String tag) {
        insertExpensesBudget(id, money, tag, "2026-01-01", "2026-01-31");
    }

    private void insertExpensesBudget(long id, long money, String tag, String startDate, String endDate) {
        SplitLedger.database(mContext).execSQL("INSERT INTO budgets (budget_id, budget_type, " +
                        "budget_start_date, budget_end_date, budget_money, budget_currency, budget_tag, " +
                        "uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, 'EUR', ?, ?, 1, 0)",
                new Object[] {id, Contract.BudgetType.EXPENSES.getValue(), startDate, endDate, money, tag, "budget-" + id});
        for (long wallet : new long[] {SplitLedger.CASH, SplitLedger.BANK}) {
            SplitLedger.database(mContext).execSQL("INSERT INTO budget_wallets (_budget, _wallet, uuid, " +
                    "last_edit, deleted) VALUES (?, ?, ?, 1, 0)", new Object[] {id, wallet, "bw-" + id + "-" + wallet});
        }
    }

    private long storedMoneyOf(long budget) {
        try (Cursor cursor = SplitLedger.database(mContext).rawQuery(
                "SELECT budget_money FROM budgets WHERE budget_id = " + budget, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
        }
    }

    public static class SlowDataContentProvider extends DataContentProvider {

        static Uri sHeld;
        static CountDownLatch sRelease;
        static Uri sWatched;
        static CountDownLatch sRead;

        static void hold(Uri uri) {
            sHeld = uri;
            sRelease = new CountDownLatch(1);
        }

        static void watch(Uri uri) {
            sWatched = uri;
            sRead = new CountDownLatch(1);
        }

        static void reset() {
            if (sRelease != null) {
                sRelease.countDown();
            }
            sHeld = null;
            sRelease = null;
            sWatched = null;
            sRead = null;
        }

        @Override
        public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
            CountDownLatch release = uri.equals(sHeld) ? sRelease : null;
            if (release != null) {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            Cursor cursor = super.query(uri, projection, selection, selectionArgs, sortOrder);
            CountDownLatch read = uri.equals(sWatched) ? sRead : null;
            if (cursor != null && read != null) {
                return new CursorWrapper(cursor) {

                    @Override
                    public boolean moveToPosition(int position) {
                        read.countDown();
                        return super.moveToPosition(position);
                    }

                };
            }
            return cursor;
        }
    }
}
