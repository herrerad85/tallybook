package com.oriondev.moneywallet.storage.database;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.preference.PreferenceManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.oriondev.moneywallet.storage.database.SplitLedger.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class BudgetCategoryBudgetsTest {

    private static final String JAN_START = "2026-01-01";
    private static final String JAN_END = "2026-01-31";
    private static final long EXPENSES = 1001;
    private static final long[] EURO = {CASH, BANK};
    private static final long[] CASH_ONLY = {CASH};

    private static final long CHECKING = 905;
    private static final long ELECTRICITY = 930;
    private static final long PHONE = 931;
    private static final long APPLE = 932;
    private static final long BANANA = 933;

    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        PreferenceManager.initialize(mContext);
        TestDatabases.useFreshDatabase(mContext);
        SplitLedger.build(mContext);
        insertBudget(EXPENSES, Schema.BudgetType.EXPENSES, JAN_START, JAN_END, 999, 0, EURO);
    }

    @Test
    public void onlyACategoryBudgetOverTheSameDatesWalletsAndExpenseCategoriesIsListed() {
        insertMatchingRules();
        insertBudget(1009, Schema.BudgetType.INCOMES, JAN_START, JAN_END, 5000, 0, EURO);
        assertCategoryBudgets(EXPENSES, row(1002, "Groceries", 400));
        assertEquals(Collections.emptyList(), categoryBudgetsOf(1009));
        assertNull(categorySumOf(1009));
    }

    @Test
    public void aParentAndItsChildListTheParentOnly() {
        insertCategoryBudget(1010, 300, EURO, HOUSEHOLD);
        insertCategoryBudget(1011, 120, CASH_ONLY, HIDDEN);
        assertCategoryBudgets(EXPENSES, row(1010, "Household", 300));
    }

    @Test
    public void theSameCategoryIsListedOnceAndAPartialOverlapListsBoth() {
        insertCategoryBudget(1012, 200, CASH_ONLY, FUEL);
        insertCategoryBudget(1013, 150, EURO, FUEL);
        insertCategoryBudget(1014, 100, CASH_ONLY, GROCERIES, PHARMACY);
        insertCategoryBudget(1015, 250, EURO, GROCERIES, HOUSEHOLD);
        assertCategoryBudgets(EXPENSES, row(1013, "Fuel", 150), row(1015, "Groceries, Household", 250),
                row(1014, "Groceries, Pharmacy", 100));
    }

    @Test
    public void theSameCategoryOnSeparateWalletsListsBothInIdOrder() {
        insertCategoryBudget(1018, 150, new long[] {BANK}, FUEL);
        insertCategoryBudget(1017, 200, CASH_ONLY, FUEL);
        assertCategoryBudgets(EXPENSES, row(1017, "Fuel", 200), row(1018, "Fuel", 150));
    }

    @Test
    public void namesAreListedAlphabeticallyWhateverTheirCaseOrAccents() {
        insertCategory(APPLE, "apple");
        insertCategory(BANANA, "Banana");
        insertCategory(ELECTRICITY, "\u00c9lectricit\u00e9");
        insertCategory(PHONE, "Phone");
        insertCategoryBudget(1035, 10, CASH_ONLY, PHONE);
        insertCategoryBudget(1036, 20, CASH_ONLY, ELECTRICITY);
        insertCategoryBudget(1037, 30, CASH_ONLY, BANANA);
        insertCategoryBudget(1038, 40, CASH_ONLY, APPLE);
        assertCategoryBudgets(EXPENSES, row(1038, "apple", 40), row(1037, "Banana", 30),
                row(1036, "\u00c9lectricit\u00e9", 20), row(1035, "Phone", 10));
    }

    @Test
    public void twoBudgetsOverTheSameCategoriesWalletsAndLimitListTheLowerId() {
        insertCategoryBudget(1022, 80, CASH_ONLY, FUEL);
        insertCategoryBudget(1023, 80, CASH_ONLY, FUEL);
        assertCategoryBudgets(EXPENSES, row(1022, "Fuel", 80));
    }

    @Test
    public void deletedAndUnlinkedWalletsDoNotDecideWhetherACategoryBudgetIsListed() {
        database(mContext).execSQL("INSERT INTO wallets (wallet_id, wallet_name, wallet_icon, " +
                "wallet_currency, wallet_count_in_total, uuid, last_edit, deleted) VALUES " +
                "(904, 'Gone', ?, 'EUR', 1, 'wallet-904', 1, 1)", new Object[] {ICON});
        insertCategoryBudget(1024, 60, new long[] {904}, FUEL);
        insertCategoryBudget(1025, 70, new long[] {CASH, 904}, PHARMACY);
        insertCategoryBudget(1026, 30, new long[] {CASH, YEN}, GIFTS);
        database(mContext).execSQL("UPDATE budget_wallets SET deleted = 1 WHERE _budget = 1026 AND _wallet = " + YEN);
        assertCategoryBudgets(EXPENSES, row(1026, "Gifts", 30), row(1025, "Pharmacy", 70));
    }

    @Test
    public void aWalletTakenOutOfTheExpensesBudgetLeavesItsCategoryBudgetsOut() {
        insertCategoryBudget(1033, 85, new long[] {BANK}, FUEL);
        insertCategoryBudget(1034, 35, CASH_ONLY, PHARMACY);
        database(mContext).execSQL("UPDATE budget_wallets SET deleted = 1 WHERE _budget = " + EXPENSES +
                " AND _wallet = " + BANK);
        assertCategoryBudgets(EXPENSES, row(1034, "Pharmacy", 35));
    }

    @Test
    public void deletedCategoriesDoNotDecideWhetherACategoryBudgetIsListedNorItsName() {
        database(mContext).execSQL("INSERT INTO categories (category_id, category_name, category_icon, " +
                "category_type, category_show_report, uuid, last_edit, deleted) VALUES " +
                "(919, 'Gone', ?, ?, 1, 'category-919', 1, 1)", new Object[] {ICON, Schema.CategoryType.INCOME});
        insertCategoryBudget(1027, 40, new long[] {BANK}, 919);
        insertCategoryBudget(1028, 20, CASH_ONLY, GROCERIES, 919);
        assertCategoryBudgets(EXPENSES, row(1028, "Groceries", 20));
    }

    @Test
    public void aCategoryBudgetWhoseDatesCarryATimeIsStillListed() {
        insertBudget(1016, Schema.BudgetType.CATEGORY, JAN_START + " 00:00:00", JAN_END + " 23:59:59",
                350, 0, CASH_ONLY, FUEL);
        assertCategoryBudgets(EXPENSES, row(1016, "Fuel", 350));
    }

    @Test
    public void aFlaggedExpensesBudgetListsWhatItsLimitAddsUp() {
        insertMatchingRules();
        insertBudget(1020, Schema.BudgetType.EXPENSES, JAN_START, JAN_END, 999, 0, EURO);
        insertBudget(1021, Schema.BudgetType.EXPENSES, "2026-02-01", "2026-02-28", 777, 0, new long[] {YEN});
        followCategoryBudgets(1020);
        followCategoryBudgets(1021);
        assertCategoryBudgets(1020, row(1002, "Groceries", 400));
        assertEquals(Long.valueOf(400), longOf(1020, Contract.Budget.SHOWN_MONEY));
        assertCategoryBudgets(EXPENSES, row(1002, "Groceries", 400));
        assertCategoryBudgets(1021);
    }

    @Test
    public void theListFollowsTheDatesAndWalletsOfEachExpensesBudget() {
        insertMatchingRules();
        insertBudget(1030, Schema.BudgetType.EXPENSES, JAN_START, JAN_END, 999, 0, new long[] {BANK});
        insertBudget(1031, Schema.BudgetType.EXPENSES, "2026-02-01", "2026-02-28", 999, 0, CASH_ONLY);
        insertBudget(1032, Schema.BudgetType.EXPENSES, JAN_START, JAN_END, 999, 0, new long[0]);
        assertEquals(Collections.singletonList(row(1002, "Groceries", 400)), categoryBudgetsOf(EXPENSES));
        assertEquals(sumFor(JAN_START, JAN_END, "<" + CASH + ">,<" + BANK + ">"), limitsOf(categoryBudgetsOf(EXPENSES)));
        assertEquals(Collections.emptyList(), categoryBudgetsOf(1030));
        assertEquals(sumFor(JAN_START, JAN_END, "<" + BANK + ">"), limitsOf(categoryBudgetsOf(1030)));
        assertEquals(Collections.emptyList(), categoryBudgetsOf(1031));
        assertEquals(sumFor("2026-02-01", "2026-02-28", "<" + CASH + ">"), limitsOf(categoryBudgetsOf(1031)));
        assertEquals(Collections.emptyList(), categoryBudgetsOf(1032));
        assertEquals(sumFor(JAN_START, JAN_END, null), limitsOf(categoryBudgetsOf(1032)));
    }

    @Test
    public void aCategoryBudgetStartingLaterIsLeftOut() {
        insertWallet(CHECKING, "Checking");
        insertCategory(ELECTRICITY, "Electricity");
        insertCategory(PHONE, "Phone");
        long[] checking = {CHECKING};
        insertBudget(1060, Schema.BudgetType.EXPENSES, "2026-11-01", "2026-11-30", 300000, 0, checking);
        insertBudget(1061, Schema.BudgetType.CATEGORY, "2026-11-01", "2026-11-30", 20000, 0, checking, ELECTRICITY);
        insertBudget(1062, Schema.BudgetType.CATEGORY, "2026-11-06", "2026-11-30", 10000, 0, checking, PHONE);
        assertCategoryBudgets(1060, row(1061, "Electricity", 20000));
    }

    @Test
    public void aCategoryBudgetEndingEarlierIsLeftOut() {
        insertCategoryBudget(1063, 45, CASH_ONLY, GROCERIES);
        insertBudget(1064, Schema.BudgetType.CATEGORY, JAN_START, "2026-01-30", 55, 0, CASH_ONLY, FUEL);
        assertCategoryBudgets(EXPENSES, row(1063, "Groceries", 45));
    }

    @Test
    public void aCategoryBudgetOverAnIncomeCategoryIsLeftOut() {
        insertCategoryBudget(1040, 800, CASH_ONLY, SALARY);
        insertCategoryBudget(1041, 50, CASH_ONLY, GROCERIES, BONUS);
        insertCategoryBudget(1042, 25, CASH_ONLY, PHARMACY);
        assertCategoryBudgets(EXPENSES, row(1042, "Pharmacy", 25));
    }

    @Test
    public void aCategoryBudgetOverAWiderWalletSetIsLeftOut() {
        insertCategoryBudget(1043, 90, new long[] {CASH, BANK, YEN}, FUEL);
        insertCategoryBudget(1044, 10, CASH_ONLY, PHARMACY);
        assertCategoryBudgets(EXPENSES, row(1044, "Pharmacy", 10));
    }

    @Test
    public void aDeletedCategoryBudgetIsLeftOut() {
        insertBudget(1045, Schema.BudgetType.CATEGORY, JAN_START, JAN_END, 65, 1, CASH_ONLY, FUEL);
        insertCategoryBudget(1046, 15, CASH_ONLY, PHARMACY);
        assertCategoryBudgets(EXPENSES, row(1046, "Pharmacy", 15));
    }

    @Test
    public void onlyALiveExpensesBudgetListsCategoryBudgets() {
        insertCategoryBudget(1050, 400, CASH_ONLY, GROCERIES);
        insertBudget(1051, Schema.BudgetType.INCOMES, JAN_START, JAN_END, 999, 0, EURO);
        insertBudget(1052, Schema.BudgetType.EXPENSES, JAN_START, JAN_END, 999, 1, EURO);
        assertEquals(Collections.singletonList(row(1050, "Groceries", 400)), categoryBudgetsOf(EXPENSES));
        assertEquals(Collections.emptyList(), categoryBudgetsOf(1051));
        assertEquals(Collections.emptyList(), categoryBudgetsOf(1050));
        assertEquals(Collections.emptyList(), categoryBudgetsOf(1052));
        assertEquals(Collections.emptyList(), categoryBudgetsOf(4242));
    }

    @Test
    public void theProviderPathReturnsTheRowsOfTheDatabase() {
        insertCategoryBudget(1013, 150, EURO, FUEL);
        insertCategoryBudget(1014, 100, CASH_ONLY, GROCERIES, PHARMACY);
        Uri uri = Uri.withAppendedPath(ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, EXPENSES),
                "category_budgets");
        List<String> rows;
        try (Cursor cursor = SQLDatabase.getShared(mContext).getBudgetCategoryBudgets(EXPENSES, null, null, null, null)) {
            rows = rowsOf(cursor);
        }
        assertEquals(Arrays.asList(row(1013, "Fuel", 150), row(1014, "Groceries, Pharmacy", 100)), rows);
        assertEquals(rows, categoryBudgetsOf(EXPENSES));
        assertEquals("vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.budget",
                mContext.getContentResolver().getType(uri));
    }

    private void assertCategoryBudgets(long budget, String... rows) {
        assertEquals(limitsOf(Arrays.asList(rows)), categorySumOf(budget));
        List<String> listed = categoryBudgetsOf(budget);
        assertEquals(Arrays.asList(rows), listed);
        assertEquals(categorySumOf(budget), limitsOf(listed));
    }

    private static String row(long id, String name, long money) {
        return id + " " + name + " " + money;
    }

    private List<String> categoryBudgetsOf(long budget) {
        try (Cursor cursor = mContext.getContentResolver().query(Uri.withAppendedPath(
                ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budget), "category_budgets"),
                null, null, null, null)) {
            return rowsOf(cursor);
        }
    }

    private static List<String> rowsOf(Cursor cursor) {
        List<String> rows = new ArrayList<>();
        while (cursor.moveToNext()) {
            rows.add(row(cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Budget.ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.CATEGORY_NAME)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Budget.MONEY))));
        }
        return rows;
    }

    private static Long limitsOf(List<String> rows) {
        Long limits = null;
        for (String row : rows) {
            long money = Long.parseLong(row.substring(row.lastIndexOf(' ') + 1));
            limits = limits == null ? money : limits + money;
        }
        return limits;
    }

    private void followCategoryBudgets(long budget) {
        database(mContext).execSQL("UPDATE budgets SET budget_tag = ? WHERE budget_id = ?",
                new Object[] {Contract.Budget.TAG_FOLLOWS_CATEGORIES, budget});
    }

    private void insertMatchingRules() {
        insertCategoryBudget(1002, 400, CASH_ONLY, GROCERIES);
        insertBudget(1003, Schema.BudgetType.CATEGORY, "2026-01-08", "2026-01-14", 50, 0, EURO, FUEL);
        insertBudget(1004, Schema.BudgetType.CATEGORY, "2025-12-25", "2026-01-24", 70, 0, CASH_ONLY, PHARMACY);
        insertBudget(1005, Schema.BudgetType.CATEGORY, JAN_START, JAN_END, 9000, 0, new long[] {YEN}, PHARMACY);
        insertCategoryBudget(1006, 800, CASH_ONLY, SALARY);
        insertCategoryBudget(1007, 600, CASH_ONLY, GROCERIES, SALARY);
        insertBudget(1008, Schema.BudgetType.CATEGORY, JAN_START, JAN_END, 999, 1, CASH_ONLY, FUEL);
    }

    private void insertWallet(long id, String name) {
        database(mContext).execSQL("INSERT INTO wallets (wallet_id, wallet_name, wallet_icon, " +
                "wallet_currency, wallet_count_in_total, uuid, last_edit, deleted) VALUES " +
                "(?, ?, ?, 'EUR', 1, ?, 1, 0)", new Object[] {id, name, ICON, "wallet-" + id});
    }

    private void insertCategory(long id, String name) {
        database(mContext).execSQL("INSERT INTO categories (category_id, category_name, category_icon, " +
                "category_type, category_show_report, uuid, last_edit, deleted) VALUES " +
                "(?, ?, ?, ?, 1, ?, 1, 0)", new Object[] {id, name, ICON, Schema.CategoryType.EXPENSE, "category-" + id});
    }

    private void insertCategoryBudget(long id, long money, long[] wallets, long... categories) {
        insertBudget(id, Schema.BudgetType.CATEGORY, JAN_START, JAN_END, money, 0, wallets, categories);
    }

    private void insertBudget(long id, int type, String start, String end, long money, int deleted,
                              long[] wallets, long... categories) {
        database(mContext).execSQL("INSERT INTO budgets (budget_id, budget_type, budget_category, " +
                        "budget_start_date, budget_end_date, budget_money, budget_currency, uuid, " +
                        "last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, 'EUR', ?, 1, ?)",
                new Object[] {id, type, categories.length > 0 ? categories[0] : null, start, end,
                        money, "budget-" + id, deleted});
        for (long wallet : wallets) {
            database(mContext).execSQL("INSERT INTO budget_wallets (_budget, _wallet, uuid, last_edit, " +
                    "deleted) VALUES (?, ?, ?, 1, 0)", new Object[] {id, wallet, "bw-" + id + "-" + wallet});
        }
        for (long category : categories) {
            database(mContext).execSQL("INSERT INTO budget_categories (_budget, _category, uuid, " +
                    "last_edit, deleted) VALUES (?, ?, ?, 1, 0)",
                    new Object[] {id, category, "bc-" + id + "-" + category});
        }
    }

    private Long sumFor(String start, String end, String walletIds) {
        try (Cursor cursor = mContext.getContentResolver().query(DataContentProvider.CONTENT_BUDGET_CATEGORY_SUM,
                null, null, new String[] {start, end, walletIds}, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.isNull(0) ? null : cursor.getLong(0);
        }
    }

    private Long categorySumOf(long budget) {
        try (Cursor cursor = mContext.getContentResolver().query(
                ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budget),
                new String[] {Contract.Budget.CATEGORY_SUM}, null, null, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.isNull(0) ? null : cursor.getLong(0);
        }
    }

    private Long longOf(long budget, String column) {
        try (Cursor cursor = mContext.getContentResolver().query(DataContentProvider.CONTENT_BUDGETS,
                new String[] {column}, Contract.Budget.ID + " = ?", new String[] {String.valueOf(budget)}, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.isNull(0) ? null : cursor.getLong(0);
        }
    }
}
