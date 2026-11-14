package com.oriondev.moneywallet.ui.fragment.primary;

import android.content.Context;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
        try {
            Thread.sleep(25);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }
}
