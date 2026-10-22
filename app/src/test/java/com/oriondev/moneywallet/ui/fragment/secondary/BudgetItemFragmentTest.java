package com.oriondev.moneywallet.ui.fragment.secondary;

import android.content.Context;
import android.database.Cursor;
import android.os.Looper;
import android.view.View;

import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.SplitLedger;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class BudgetItemFragmentTest {

    private static final long CATEGORY_LIMIT = 100000;

    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(mContext);
        SplitLedger.build(mContext);
        SplitLedger.insertCategoryBudget(mContext, 1100, SplitLedger.GROCERIES);
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

    private View showSumRow(FragmentActivity activity, long budget) {
        BudgetItemFragment fragment = new BudgetItemFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();
        fragment.showItemId(budget);
        View wheel = fragment.getView().findViewById(R.id.secondary_panel_progress_wheel);
        for (int i = 0; i < 200 && wheel.getVisibility() != View.GONE; i++) {
            shadowOf(Looper.getMainLooper()).idle();
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
        assertEquals("the budget never loaded", View.GONE, wheel.getVisibility());
        return fragment.getView().findViewById(R.id.category_sum_text_view);
    }

    private void insertExpensesBudget(long id, long money, String tag) {
        SplitLedger.database(mContext).execSQL("INSERT INTO budgets (budget_id, budget_type, " +
                        "budget_start_date, budget_end_date, budget_money, budget_currency, budget_tag, " +
                        "uuid, last_edit, deleted) VALUES (?, ?, '2026-01-01', '2026-01-31', ?, 'EUR', ?, ?, 1, 0)",
                new Object[] {id, Contract.BudgetType.EXPENSES.getValue(), money, tag, "budget-" + id});
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
}
