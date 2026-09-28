package com.oriondev.moneywallet.ui.activity;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.broadcast.RecurrenceBroadcastReceiver;
import com.oriondev.moneywallet.model.Category;
import com.oriondev.moneywallet.model.RecurrenceSetting;
import com.oriondev.moneywallet.model.Wallet;
import com.oriondev.moneywallet.picker.BudgetTypePicker;
import com.oriondev.moneywallet.picker.CategoryPicker;
import com.oriondev.moneywallet.picker.DateTimePicker;
import com.oriondev.moneywallet.picker.MoneyPicker;
import com.oriondev.moneywallet.picker.RecurrencePicker;
import com.oriondev.moneywallet.picker.WalletPicker;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.SyncContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.database.TransferContentValuesBuilder;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.view.text.MaterialEditText;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.DateFormatter;
import com.oriondev.moneywallet.utils.DateUtils;
import com.oriondev.moneywallet.utils.IconLoader;
import com.oriondev.moneywallet.utils.MoneyFormatter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Drives the real budget editor on the JVM, against the real content provider over a fresh
 * database, and reads back through the provider what it wrote. Every save is checked against the
 * PROGRESS column, which the provider sums over the transactions a budget actually covers, so a
 * transposed wallet set, a wrong period, a dropped category or a wrong type all show there even
 * when the row's own copies of those values read correctly. The rest of the cases cover what the
 * screen does with a repeating budget, whose dates and anchor are held or moved by rules the two
 * date fields on screen cannot show.
 */
@RunWith(RobolectricTestRunner.class)
public class NewEditBudgetActivityTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private static final String TAG_MONEY_PICKER = "NewEditBudgetActivity::Tag::MoneyPicker";
    private static final String TAG_BUDGET_TYPE_PICKER = "NewEditBudgetActivity::Tag::BudgetTypePicker";
    private static final String TAG_CATEGORY_PICKER = "NewEditBudgetActivity::Tag::CategoryPicker";
    private static final String TAG_START_DATE_PICKER = "NewEditBudgetActivity::Tag::StartDatePicker";
    private static final String TAG_END_DATE_PICKER = "NewEditBudgetActivity::Tag::EndDatePicker";
    private static final String TAG_WALLETS_PICKER = "NewEditBudgetActivity::Tag::WalletsPicker";
    private static final String TAG_RECURRENCE_PICKER = "NewEditBudgetActivity::Tag::RecurrencePicker";

    private static final String APRIL_START = "2019-04-01";
    private static final String APRIL_END = "2019-04-30";

    private Context mContext;
    private ContentResolver mResolver;
    private long mUnusedWallet;
    private long mWalletA;
    private long mWalletB;
    private long mWalletD;
    private long mFood;
    private long mSnacks;
    private long mRent;
    private long mSalary;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(mContext);
        mResolver = mContext.getContentResolver();
        // the app schedules the roll alarm from its own onCreate, off whichever database the
        // shared helper was still holding, so a case that reads the alarm starts from nothing
        cancelRollAlarm();
        // one throwaway wallet takes id 1, so the first wallet row is never the current wallet
        mUnusedWallet = insertWallet("Unused", "EUR");
        mWalletA = insertWallet("Cash", "EUR");
        mWalletB = insertWallet("Bank", "EUR");
        mWalletD = insertWallet("Tokyo", "JPY");
        // nine system categories already hold ids 1 to 9 and eleven throwaway rows carry that on
        // to 20, so Rent is 21, Food 22, Snacks 23 and Salary 24, and none of them equals a wallet
        // id. Rent goes in first so that the order the ids run in and the order the names run in
        // disagree, and an assertion cannot hold by accident of the names
        for (int i = 0; i < 11; i++) {
            insertCategory("Unused", Contract.CategoryType.EXPENSE, null);
        }
        mRent = insertCategory("Rent", Contract.CategoryType.EXPENSE, null);
        mFood = insertCategory("Food", Contract.CategoryType.EXPENSE, null);
        mSnacks = insertCategory("Snacks", Contract.CategoryType.EXPENSE, mFood);
        mSalary = insertCategory("Salary", Contract.CategoryType.INCOME, null);
        // seven throwaway budgets take ids 1 to 7, so a budget a case makes starts at 8 and equals
        // no wallet id and none of the four category ids above. They sit on wallet A and never
        // repeat, so deleting a wallet in one case cannot take them out of the budget list
        for (int i = 0; i < 7; i++) {
            insertBudget(Contract.BudgetType.EXPENSES, 100L, "EUR", new long[] {mWalletA}, null,
                    "2001-01-01", "2001-01-31", null, null, null);
        }
        // every amount is one digit times a distinct power of ten, so any subset sum names exactly
        // which rows were added up
        insertTransaction(mWalletA, "2019-04-05 12:00:00", Contract.Direction.EXPENSE, mFood, 1000L);
        insertTransaction(mWalletA, "2019-04-06 12:00:00", Contract.Direction.EXPENSE, mSnacks, 20000L);
        insertTransaction(mWalletA, "2019-04-07 12:00:00", Contract.Direction.EXPENSE, mRent, 300000L);
        insertTransaction(mWalletA, "2019-04-08 12:00:00", Contract.Direction.INCOME, mSalary, 4000000L);
        insertTransaction(mWalletB, "2019-04-09 12:00:00", Contract.Direction.EXPENSE, mFood, 50000000L);
        insertTransaction(mWalletD, "2019-04-10 12:00:00", Contract.Direction.EXPENSE, mFood, 600000000L);
        insertTransaction(mWalletA, "2019-04-25 12:00:00", Contract.Direction.EXPENSE, mRent, 7000000000L);
        insertTransaction(mWalletA, "2019-05-03 12:00:00", Contract.Direction.EXPENSE, mFood, 80000000000L);
        insertTransaction(mWalletA, secondsAgo(5), Contract.Direction.EXPENSE, mFood, 900000000000L);
        insertTransaction(mWalletA, secondsAgo(5), Contract.Direction.INCOME, mSalary, 1000000000000L);
        insertTransaction(mWalletB, secondsAgo(5), Contract.Direction.EXPENSE, mRent, 20000000000000L);
        // the only rent row inside 10 to 20 April, which is the window one edit narrows down to
        insertTransaction(mWalletA, "2019-04-15 12:00:00", Contract.Direction.EXPENSE, mRent, 300000000000000L);
        insertTransfer(mWalletA, mWalletB, 4000000000000000L, "2019-04-12 12:00:00");
    }

    @Test
    public void aNewExpensesBudgetOpensOnTodayAndSavesTheMonthAhead() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        Calendar calendar = Calendar.getInstance();
        String today = DateUtils.getSQLDateString(calendar.getTime());
        calendar.add(Calendar.MONTH, 1);
        String monthAhead = DateUtils.getSQLDateString(calendar.getTime());
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launchActivityForResult(newItemIntent())) {
            scenario.onActivity(activity -> {
                assertEquals(activity.getString(R.string.hint_expenses),
                        fieldText(activity, R.id.type_edit_text));
                assertEquals(View.GONE, activity.findViewById(R.id.category_edit_text).getVisibility());
                assertFalse(checkBox(activity, R.id.repeat_checkbox).isChecked());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.repeat_checkbox).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.recurrence_edit_text).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.start_date_edit_text).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.end_date_edit_text).getVisibility());
                assertFalse(fieldText(activity, R.id.start_date_edit_text).isEmpty());
                assertFalse(fieldText(activity, R.id.end_date_edit_text).isEmpty());
                assertEquals(Collections.singletonList(mWalletA), walletPickerIds(activity));
                assertEquals(CurrencyManager.getCurrency("EUR"), moneyPicker(activity).getCurrentCurrency());
                assertEquals(CurrencyManager.getCurrency("EUR").getSymbol(),
                        headerText(activity, R.id.currency_text_view));
                assertEquals(today, DateUtils.getSQLDateString(startDatePicker(activity).getCurrentDateTime()));
                assertEquals(monthAhead, DateUtils.getSQLDateString(endDatePicker(activity).getCurrentDateTime()));
                moneyPicker(activity).setMoney(500000L);
                assertEquals(formattedMoney("EUR", 500000L),
                        headerText(activity, R.id.money_text_view));
                save(activity);
                assertTrue(activity.isFinishing());
            });
            assertEquals(Activity.RESULT_OK, scenario.getResult().getResultCode());
        }
        assertEquals(before + 1, countBudgets());
        long budget = newestBudget();
        Cursor row = budgetRow(budget);
        assertEquals(Contract.BudgetType.EXPENSES.getValue(), row.getInt(row.getColumnIndex(Contract.Budget.TYPE)));
        assertEquals(500000L, row.getLong(row.getColumnIndex(Contract.Budget.MONEY)));
        assertEquals("EUR", row.getString(row.getColumnIndex(Contract.Budget.CURRENCY)));
        assertTrue(row.isNull(row.getColumnIndex(Contract.Budget.CATEGORY_ID)));
        assertEquals(today, row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals(monthAhead, row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        assertTrue(row.isNull(row.getColumnIndex(Contract.Budget.RULE)));
        assertTrue(row.isNull(row.getColumnIndex(Contract.Budget.RULE_START)));
        // only the expense in wallet A dated seconds ago, not the income beside it and not the
        // rent expense in wallet B
        assertEquals(900000000000L, row.getLong(row.getColumnIndex(Contract.Budget.PROGRESS)));
        row.close();
        assertEquals(Collections.singletonList(mWalletA), walletIdsOf(budget));
        assertEquals(Collections.emptyList(), categoryIdsOf(budget));
    }

    @Test
    public void anIncomeBudgetLeavesOutATransferBetweenItsOwnWallets() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(newItemIntent())) {
            scenario.onActivity(activity -> {
                budgetTypePicker(activity).onBudgetTypeSelected(Contract.BudgetType.INCOMES);
                assertEquals(activity.getString(R.string.hint_incomes),
                        fieldText(activity, R.id.type_edit_text));
                assertEquals(View.GONE, activity.findViewById(R.id.category_edit_text).getVisibility());
                walletsPicker(activity).onWalletsSelected(new Wallet[] {
                        wallet(mWalletA, "Cash", "EUR"), wallet(mWalletB, "Bank", "EUR")});
                startDatePicker(activity).setCurrentDateTime(date(APRIL_START));
                endDatePicker(activity).setCurrentDateTime(date(APRIL_END));
                moneyPicker(activity).setMoney(700000L);
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before + 1, countBudgets());
        long budget = newestBudget();
        Cursor row = budgetRow(budget);
        assertEquals(Contract.BudgetType.INCOMES.getValue(), row.getInt(row.getColumnIndex(Contract.Budget.TYPE)));
        assertEquals(700000L, row.getLong(row.getColumnIndex(Contract.Budget.MONEY)));
        assertTrue(row.isNull(row.getColumnIndex(Contract.Budget.CATEGORY_ID)));
        assertEquals(APRIL_START, row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals(APRIL_END, row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        // the one Salary income in wallet A, dated 8 April. The leg the transfer pays into
        // wallet B is left out because wallet A, the wallet it came from, is in the budget too,
        // and the income dated seconds ago is years past April
        assertEquals(4000000L, row.getLong(row.getColumnIndex(Contract.Budget.PROGRESS)));
        row.close();
        assertEquals(Arrays.asList(mWalletA, mWalletB), walletIdsOf(budget));
        assertEquals(Collections.emptyList(), categoryIdsOf(budget));
        long otherSide = insertBudget(Contract.BudgetType.INCOMES, 700000L, "EUR",
                new long[] {mWalletB}, null, APRIL_START, APRIL_END, null, null, null);
        // the same transfer read from a budget wallet A is not part of, where the leg it pays in
        // is the only income wallet B holds in April
        assertEquals(4000000000000000L, progressOf(otherSide));
    }

    @Test
    public void anIncomeBudgetEditedOffTheWalletThatPaysCountsTheTransferAgain() {
        long budget = insertBudget(Contract.BudgetType.INCOMES, 700000L, "EUR",
                new long[] {mWalletA, mWalletB}, null, APRIL_START, APRIL_END, null, null, null);
        // the one Salary income in wallet A. The leg the transfer pays into wallet B is left out
        // while wallet A, the wallet it comes from, is in the budget too
        assertEquals(4000000L, progressOf(budget));
        assertEquals(4000000L, listedMoneyOf(budget));
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(budget))) {
            scenario.onActivity(activity -> {
                walletsPicker(activity).onWalletsSelected(new Wallet[] {
                        wallet(mWalletB, "Bank", "EUR")});
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(Collections.singletonList(mWalletB), walletIdsOf(budget));
        // the edit takes wallet A out of the budget, so the leg the transfer pays into wallet B
        // counts, and it is the only income wallet B holds in April
        assertEquals(4000000000000000L, progressOf(budget));
        assertEquals(4000000000000000L, listedMoneyOf(budget));
    }

    @Test
    public void anExpensesBudgetEditedOffTheWalletThatReceivesCountsTheTransferAgain() {
        long budget = insertBudget(Contract.BudgetType.EXPENSES, 900000L, "EUR",
                new long[] {mWalletA, mWalletB}, null, APRIL_START, APRIL_END, null, null, null);
        assertEquals(aprilExpensesProgress(), progressOf(budget));
        assertEquals(aprilExpensesProgress(), listedMoneyOf(budget));
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(budget))) {
            scenario.onActivity(activity -> {
                walletsPicker(activity).onWalletsSelected(new Wallet[] {
                        wallet(mWalletA, "Cash", "EUR")});
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(Collections.singletonList(mWalletA), walletIdsOf(budget));
        assertEquals(aprilExpensesInWalletAWithTheTransfer(), progressOf(budget));
        assertEquals(aprilExpensesInWalletAWithTheTransfer(), listedMoneyOf(budget));
    }

    @Test
    public void aNewBudgetOnAYenWalletSavesInYen() {
        PreferenceManager.setCurrentWallet(mContext, mWalletD);
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(newItemIntent())) {
            scenario.onActivity(activity -> {
                assertEquals(CurrencyManager.getCurrency("JPY"), moneyPicker(activity).getCurrentCurrency());
                assertEquals(CurrencyManager.getCurrency("JPY").getSymbol(),
                        headerText(activity, R.id.currency_text_view));
                moneyPicker(activity).setMoney(250000L);
                assertEquals(formattedMoney("JPY", 250000L),
                        headerText(activity, R.id.money_text_view));
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before + 1, countBudgets());
        Cursor row = budgetRow(newestBudget());
        assertEquals("JPY", row.getString(row.getColumnIndex(Contract.Budget.CURRENCY)));
        assertEquals(250000L, row.getLong(row.getColumnIndex(Contract.Budget.MONEY)));
        // the one row in wallet D is dated 2019-04-10, outside the month this budget opens on
        assertEquals(0L, row.getLong(row.getColumnIndex(Contract.Budget.PROGRESS)));
        row.close();
    }

    @Test
    public void aNewBudgetOnTheTotalWalletOpensOnTheFirstWalletRow() {
        insertArchivedWallet("AAA Archived", "EUR");
        PreferenceManager.setCurrentWallet(mContext, PreferenceManager.TOTAL_WALLET_ID);
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(newItemIntent())) {
            // the archived wallet sorts first by name but is skipped, so the editor lands on Bank,
            // the first unarchived wallet in the drawer's INDEX, NAME order
            scenario.onActivity(activity ->
                    assertEquals(Collections.singletonList(mWalletB), walletPickerIds(activity)));
        }
    }

    @Test
    public void aNewCategoryBudgetCarriesEveryCategoryAndEveryWallet() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(newItemIntent())) {
            scenario.onActivity(activity -> {
                budgetTypePicker(activity).onBudgetTypeSelected(Contract.BudgetType.CATEGORY);
                assertEquals(activity.getString(R.string.hint_category),
                        fieldText(activity, R.id.type_edit_text));
                assertEquals(View.VISIBLE, activity.findViewById(R.id.category_edit_text).getVisibility());
                categoryPicker(activity).onCategoriesSelected(new Category[] {
                        category(mFood, "Food"), category(mRent, "Rent")});
                assertEquals("Food, Rent", fieldText(activity, R.id.category_edit_text));
                walletsPicker(activity).onWalletsSelected(new Wallet[] {
                        wallet(mWalletB, "Bank", "EUR"), wallet(mWalletA, "Cash", "EUR")});
                assertEquals("Bank, Cash", fieldText(activity, R.id.wallets_edit_text));
                assertEquals(CurrencyManager.getCurrency("EUR"), moneyPicker(activity).getCurrentCurrency());
                moneyPicker(activity).setMoney(900000L);
                startDatePicker(activity).setCurrentDateTime(date(APRIL_START));
                endDatePicker(activity).setCurrentDateTime(date(APRIL_END));
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before + 1, countBudgets());
        long budget = newestBudget();
        Cursor row = budgetRow(budget);
        assertEquals(Contract.BudgetType.CATEGORY.getValue(), row.getInt(row.getColumnIndex(Contract.Budget.TYPE)));
        assertEquals(900000L, row.getLong(row.getColumnIndex(Contract.Budget.MONEY)));
        assertEquals("<" + mRent + ">,<" + mFood + ">",
                row.getString(row.getColumnIndex(Contract.Budget.CATEGORY_IDS)));
        // the picker was handed Food first and the first of that order is what the row keeps,
        // while the column above comes back lowest id first, which is Rent
        assertEquals(mFood, row.getLong(row.getColumnIndex(Contract.Budget.CATEGORY_ID)));
        assertEquals(APRIL_START, row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals(APRIL_END, row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        assertEquals(aprilCategoryProgress(), row.getLong(row.getColumnIndex(Contract.Budget.PROGRESS)));
        row.close();
        assertEquals(Arrays.asList(mWalletA, mWalletB), walletIdsOf(budget));
        assertEquals(Arrays.asList(mRent, mFood), categoryIdsOf(budget));
    }

    @Test
    public void aNewBudgetWithNoWalletIsRefused() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(newItemIntent())) {
            scenario.onActivity(activity -> {
                walletsPicker(activity).onWalletsSelected(null);
                moneyPicker(activity).setMoney(500000L);
                save(activity);
                assertFalse(activity.isFinishing());
                assertNull(ShadowDialog.getLatestDialog());
                assertFalse(field(activity, R.id.wallets_edit_text).validate());
                assertTrue(field(activity, R.id.start_date_edit_text).validate());
                assertTrue(field(activity, R.id.end_date_edit_text).validate());
                assertTrue(field(activity, R.id.type_edit_text).validate());
            });
        }
        assertEquals(before, countBudgets());
    }

    @Test
    public void aNewCategoryBudgetWithNoCategoryIsRefused() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(newItemIntent())) {
            scenario.onActivity(activity -> {
                budgetTypePicker(activity).onBudgetTypeSelected(Contract.BudgetType.CATEGORY);
                walletsPicker(activity).onWalletsSelected(new Wallet[] {wallet(mWalletA, "Cash", "EUR")});
                startDatePicker(activity).setCurrentDateTime(date(APRIL_START));
                endDatePicker(activity).setCurrentDateTime(date(APRIL_END));
                moneyPicker(activity).setMoney(500000L);
                save(activity);
                assertFalse(activity.isFinishing());
                assertNull(ShadowDialog.getLatestDialog());
                assertFalse(field(activity, R.id.category_edit_text).validate());
                assertTrue(field(activity, R.id.wallets_edit_text).validate());
            });
        }
        assertEquals(before, countBudgets());
    }

    @Test
    public void aNewBudgetEndingBeforeItStartsIsRefused() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(newItemIntent())) {
            scenario.onActivity(activity -> {
                startDatePicker(activity).setCurrentDateTime(date(APRIL_END));
                endDatePicker(activity).setCurrentDateTime(date(APRIL_START));
                moneyPicker(activity).setMoney(500000L);
                save(activity);
                assertFalse(activity.isFinishing());
                assertNull(ShadowDialog.getLatestDialog());
                assertFalse(field(activity, R.id.start_date_edit_text).validate());
                assertTrue(field(activity, R.id.end_date_edit_text).validate());
                assertTrue(field(activity, R.id.wallets_edit_text).validate());
            });
        }
        assertEquals(before, countBudgets());
    }

    @Test
    public void aNewBudgetOverTwoCurrenciesIsRefused() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(newItemIntent())) {
            scenario.onActivity(activity -> {
                walletsPicker(activity).onWalletsSelected(new Wallet[] {
                        wallet(mWalletA, "Cash", "EUR"), wallet(mWalletD, "Tokyo", "JPY")});
                assertEquals(CurrencyManager.getCurrency("EUR"), moneyPicker(activity).getCurrentCurrency());
                assertEquals(CurrencyManager.getCurrency("EUR").getSymbol(),
                        headerText(activity, R.id.currency_text_view));
                walletsPicker(activity).onWalletsSelected(new Wallet[] {
                        wallet(mWalletD, "Tokyo", "JPY"), wallet(mWalletA, "Cash", "EUR")});
                assertEquals(CurrencyManager.getCurrency("JPY"), moneyPicker(activity).getCurrentCurrency());
                assertEquals(CurrencyManager.getCurrency("JPY").getSymbol(),
                        headerText(activity, R.id.currency_text_view));
                moneyPicker(activity).setMoney(500000L);
                save(activity);
                assertFalse(activity.isFinishing());
                assertNull(ShadowDialog.getLatestDialog());
                assertFalse(field(activity, R.id.wallets_edit_text).validate());
                assertTrue(field(activity, R.id.start_date_edit_text).validate());
                assertTrue(field(activity, R.id.end_date_edit_text).validate());
            });
        }
        assertEquals(before, countBudgets());
    }

    @Test
    public void aNewBudgetOnAWalletWithNoCurrencyIsRefused() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(newItemIntent())) {
            scenario.onActivity(activity -> {
                walletsPicker(activity).onWalletsSelected(new Wallet[] {
                        new Wallet(mWalletA, "Cash", IconLoader.parse(ICON), null, 0L, 0L)});
                moneyPicker(activity).setMoney(500000L);
                save(activity);
                assertFalse(activity.isFinishing());
                assertNull(ShadowDialog.getLatestDialog());
                assertFalse(field(activity, R.id.wallets_edit_text).validate());
            });
        }
        assertEquals(before, countBudgets());
    }

    @Test
    public void aNewRepeatingBudgetLandsOnThePeriodTodayIsIn() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        Calendar anchorCalendar = Calendar.getInstance();
        anchorCalendar.add(Calendar.MONTH, -3);
        anchorCalendar.set(Calendar.DAY_OF_MONTH, 15);
        Date anchor = anchorCalendar.getTime();
        String[] period = monthlyPeriod(15);
        AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(newItemIntent())) {
            scenario.onActivity(activity -> {
                checkBox(activity, R.id.repeat_checkbox).performClick();
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.recurrence_edit_text).getVisibility());
                assertFalse(fieldText(activity, R.id.recurrence_edit_text).isEmpty());
                assertEquals(View.GONE, activity.findViewById(R.id.start_date_edit_text).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.end_date_edit_text).getVisibility());
                RecurrenceSetting monthly = monthlyOn(anchor);
                recurrencePicker(activity).onRecurrenceSettingChanged(monthly);
                assertEquals(monthly.getUserReadableString(activity),
                        fieldText(activity, R.id.recurrence_edit_text));
                moneyPicker(activity).setMoney(250000L);
                assertTrue(Shadows.shadowOf(alarmManager).getScheduledAlarms().isEmpty());
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before + 1, countBudgets());
        long budget = newestBudget();
        Cursor row = budgetRow(budget);
        assertEquals(monthlyOn(anchor).getRule(), row.getString(row.getColumnIndex(Contract.Budget.RULE)));
        assertEquals(DateUtils.getSQLDateString(anchor),
                row.getString(row.getColumnIndex(Contract.Budget.RULE_START)));
        assertEquals(period[0], row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals(period[1], row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        assertEquals(900000000000L, row.getLong(row.getColumnIndex(Contract.Budget.PROGRESS)));
        row.close();
        assertEquals(Collections.singletonList(mWalletA), walletIdsOf(budget));
        assertEquals(Collections.emptyList(), categoryIdsOf(budget));
        // the roll wakes the day after the period ends, at midnight
        assertEquals(1, Shadows.shadowOf(alarmManager).getScheduledAlarms().size());
        assertEquals(DateUtils.addDays(DateUtils.getCalendar(date(period[1])), 1).getTime(),
                Shadows.shadowOf(alarmManager).getScheduledAlarms().get(0).triggerAtTime);
    }

    @Test
    public void aNewRepeatingBudgetIsAnchoredThroughTheStartField() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        Calendar anchorCalendar = Calendar.getInstance();
        anchorCalendar.add(Calendar.MONTH, -3);
        anchorCalendar.add(Calendar.DAY_OF_MONTH, -20);
        Date anchor = anchorCalendar.getTime();
        String[] period = monthlyPeriod(anchorCalendar.get(Calendar.DAY_OF_MONTH));
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(newItemIntent())) {
            scenario.onActivity(activity -> {
                startDatePicker(activity).setCurrentDateTime(anchor);
                checkBox(activity, R.id.repeat_checkbox).performClick();
                assertEquals(monthlyOn(anchor).getUserReadableString(activity),
                        fieldText(activity, R.id.recurrence_edit_text));
                moneyPicker(activity).setMoney(250000L);
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before + 1, countBudgets());
        Cursor row = budgetRow(newestBudget());
        assertEquals(monthlyOn(anchor).getRule(), row.getString(row.getColumnIndex(Contract.Budget.RULE)));
        assertEquals(DateUtils.getSQLDateString(anchor),
                row.getString(row.getColumnIndex(Contract.Budget.RULE_START)));
        assertEquals(period[0], row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals(period[1], row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        assertEquals(900000000000L, row.getLong(row.getColumnIndex(Contract.Budget.PROGRESS)));
        row.close();
    }

    @Test
    public void theDayChosenInTheDialogSurvivesTickingTheRepeatBox() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        Calendar fieldCalendar = Calendar.getInstance();
        fieldCalendar.add(Calendar.DAY_OF_MONTH, -40);
        Calendar dialogCalendar = Calendar.getInstance();
        dialogCalendar.add(Calendar.DAY_OF_MONTH, -25);
        Date fieldDay = fieldCalendar.getTime();
        Date chosenDay = dialogCalendar.getTime();
        String[] period = monthlyPeriod(dialogCalendar.get(Calendar.DAY_OF_MONTH));
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(newItemIntent())) {
            scenario.onActivity(activity -> {
                startDatePicker(activity).setCurrentDateTime(fieldDay);
                recurrencePicker(activity).onRecurrenceSettingChanged(monthlyOn(chosenDay));
                checkBox(activity, R.id.repeat_checkbox).performClick();
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked());
                assertEquals(monthlyOn(chosenDay).getUserReadableString(activity),
                        fieldText(activity, R.id.recurrence_edit_text));
                moneyPicker(activity).setMoney(250000L);
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before + 1, countBudgets());
        Cursor row = budgetRow(newestBudget());
        assertEquals(monthlyOn(chosenDay).getRule(), row.getString(row.getColumnIndex(Contract.Budget.RULE)));
        assertEquals(DateUtils.getSQLDateString(chosenDay),
                row.getString(row.getColumnIndex(Contract.Budget.RULE_START)));
        assertEquals(period[0], row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals(period[1], row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        assertEquals(900000000000L, row.getLong(row.getColumnIndex(Contract.Budget.PROGRESS)));
        row.close();
    }

    @Test
    public void anUntouchedEditOfACategoryBudgetKeepsEveryField() {
        long budget = insertAprilCategoryBudget(new long[] {mWalletA, mWalletB});
        int before = countBudgets();
        long progress = progressOf(budget);
        assertEquals(aprilCategoryProgress(), progress);
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(budget))) {
            scenario.onActivity(activity -> {
                assertEquals(activity.getString(R.string.hint_category),
                        fieldText(activity, R.id.type_edit_text));
                assertEquals(View.VISIBLE, activity.findViewById(R.id.category_edit_text).getVisibility());
                assertEquals("Rent, Food", fieldText(activity, R.id.category_edit_text));
                assertEquals(Arrays.asList(mRent, mFood), categoryPickerIds(activity));
                assertEquals(Arrays.asList(mWalletA, mWalletB), sorted(walletPickerIds(activity)));
                assertEquals("Cash, Bank", fieldText(activity, R.id.wallets_edit_text));
                assertEquals(900000L, moneyPicker(activity).getCurrentMoney());
                assertEquals(CurrencyManager.getCurrency("EUR"), moneyPicker(activity).getCurrentCurrency());
                assertFalse(checkBox(activity, R.id.repeat_checkbox).isChecked());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.repeat_checkbox).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.start_date_edit_text).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.end_date_edit_text).getVisibility());
                assertEquals(formattedDate(activity, date(APRIL_START)),
                        fieldText(activity, R.id.start_date_edit_text));
                assertEquals(formattedDate(activity, date(APRIL_END)),
                        fieldText(activity, R.id.end_date_edit_text));
                assertEquals(APRIL_START, DateUtils.getSQLDateString(startDatePicker(activity).getCurrentDateTime()));
                assertEquals(APRIL_END, DateUtils.getSQLDateString(endDatePicker(activity).getCurrentDateTime()));
                // a budget carrying no anchor seeds the recurrence dialog off the day it starts
                assertEquals(APRIL_START, DateUtils.getSQLDateString(
                        recurrencePicker(activity).getCurrentSettings().getStartDate()));
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before, countBudgets());
        Cursor row = budgetRow(budget);
        assertEquals(Contract.BudgetType.CATEGORY.getValue(), row.getInt(row.getColumnIndex(Contract.Budget.TYPE)));
        assertEquals(900000L, row.getLong(row.getColumnIndex(Contract.Budget.MONEY)));
        assertEquals("EUR", row.getString(row.getColumnIndex(Contract.Budget.CURRENCY)));
        assertEquals(mRent, row.getLong(row.getColumnIndex(Contract.Budget.CATEGORY_ID)));
        assertEquals("<" + mRent + ">,<" + mFood + ">",
                row.getString(row.getColumnIndex(Contract.Budget.CATEGORY_IDS)));
        assertEquals(APRIL_START, row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals(APRIL_END, row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        assertTrue(row.isNull(row.getColumnIndex(Contract.Budget.RULE)));
        assertTrue(row.isNull(row.getColumnIndex(Contract.Budget.RULE_START)));
        assertEquals(progress, row.getLong(row.getColumnIndex(Contract.Budget.PROGRESS)));
        row.close();
        assertEquals(Arrays.asList(mWalletA, mWalletB), walletIdsOf(budget));
        assertEquals(Arrays.asList(mRent, mFood), categoryIdsOf(budget));
    }

    @Test
    public void anEditWritesEveryChangedValue() {
        long budget = insertAprilCategoryBudget(new long[] {mWalletA, mWalletB});
        int before = countBudgets();
        String uuid = uuidOf(budget);
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(budget))) {
            scenario.onActivity(activity -> {
                moneyPicker(activity).setMoney(1200000L);
                walletsPicker(activity).onWalletsSelected(new Wallet[] {wallet(mWalletA, "Cash", "EUR")});
                categoryPicker(activity).onCategoriesSelected(new Category[] {category(mRent, "Rent")});
                startDatePicker(activity).setCurrentDateTime(date("2019-04-10"));
                endDatePicker(activity).setCurrentDateTime(date("2019-04-20"));
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before, countBudgets());
        Cursor row = budgetRow(budget);
        assertEquals(Contract.BudgetType.CATEGORY.getValue(), row.getInt(row.getColumnIndex(Contract.Budget.TYPE)));
        assertEquals(1200000L, row.getLong(row.getColumnIndex(Contract.Budget.MONEY)));
        assertEquals("EUR", row.getString(row.getColumnIndex(Contract.Budget.CURRENCY)));
        assertEquals(mRent, row.getLong(row.getColumnIndex(Contract.Budget.CATEGORY_ID)));
        assertEquals("<" + mRent + ">", row.getString(row.getColumnIndex(Contract.Budget.CATEGORY_IDS)));
        assertEquals("2019-04-10", row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals("2019-04-20", row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        // only the rent expense of 15 April, signed negative the way a category budget counts it
        assertEquals(-300000000000000L, row.getLong(row.getColumnIndex(Contract.Budget.PROGRESS)));
        row.close();
        assertEquals(Collections.singletonList(mWalletA), walletIdsOf(budget));
        assertEquals(Collections.singletonList(mRent), categoryIdsOf(budget));
        assertEquals(uuid, uuidOf(budget));
    }

    @Test
    public void anEditOntoAYenWalletRewritesTheCurrency() {
        long budget = insertBudget(Contract.BudgetType.EXPENSES, 900000L, "EUR",
                new long[] {mWalletA}, null, APRIL_START, APRIL_END, null, null, null);
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(budget))) {
            scenario.onActivity(activity -> {
                walletsPicker(activity).onWalletsSelected(new Wallet[] {
                        wallet(mWalletD, "Tokyo", "JPY")});
                assertEquals(CurrencyManager.getCurrency("JPY"), moneyPicker(activity).getCurrentCurrency());
                assertEquals(CurrencyManager.getCurrency("JPY").getSymbol(),
                        headerText(activity, R.id.currency_text_view));
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before, countBudgets());
        Cursor row = budgetRow(budget);
        assertEquals("JPY", row.getString(row.getColumnIndex(Contract.Budget.CURRENCY)));
        assertEquals(900000L, row.getLong(row.getColumnIndex(Contract.Budget.MONEY)));
        // the one Food expense in wallet D, dated 10 April, and wallet D holds no other row
        assertEquals(600000000L, row.getLong(row.getColumnIndex(Contract.Budget.PROGRESS)));
        row.close();
        assertEquals(Collections.singletonList(mWalletD), walletIdsOf(budget));
    }

    @Test
    public void switchingTheTypeClearsTheCategories() {
        long budget = insertAprilCategoryBudget(new long[] {mWalletA, mWalletB});
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(budget))) {
            scenario.onActivity(activity -> {
                budgetTypePicker(activity).onBudgetTypeSelected(Contract.BudgetType.EXPENSES);
                assertEquals(View.GONE, activity.findViewById(R.id.category_edit_text).getVisibility());
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before, countBudgets());
        Cursor row = budgetRow(budget);
        assertEquals(Contract.BudgetType.EXPENSES.getValue(), row.getInt(row.getColumnIndex(Contract.Budget.TYPE)));
        assertTrue(row.isNull(row.getColumnIndex(Contract.Budget.CATEGORY_ID)));
        assertEquals(APRIL_START, row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals(APRIL_END, row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        assertEquals(aprilExpensesProgress(), row.getLong(row.getColumnIndex(Contract.Budget.PROGRESS)));
        row.close();
        assertEquals(Arrays.asList(mWalletA, mWalletB), walletIdsOf(budget));
        assertEquals(Collections.emptyList(), categoryIdsOf(budget));
    }

    @Test
    public void anUnchangedScheduleHoldsTheStoredDates() {
        RecurrenceSetting schedule = monthlyOn(date("2019-01-15"));
        long budget = insertBudget(Contract.BudgetType.EXPENSES, 300000L, "EUR",
                new long[] {mWalletA}, null, "2019-03-15", "2019-04-14",
                schedule.getRule(), "2019-01-15", null);
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(budget))) {
            scenario.onActivity(activity -> {
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.repeat_checkbox).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.recurrence_edit_text).getVisibility());
                assertEquals(schedule.getUserReadableString(activity),
                        fieldText(activity, R.id.recurrence_edit_text));
                assertEquals(View.GONE, activity.findViewById(R.id.start_date_edit_text).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.end_date_edit_text).getVisibility());
                assertEquals("2019-03-15", DateUtils.getSQLDateString(startDatePicker(activity).getCurrentDateTime()));
                assertEquals("2019-01-15", DateUtils.getSQLDateString(
                        recurrencePicker(activity).getCurrentSettings().getStartDate()));
                assertEquals(schedule.getRule(),
                        recurrencePicker(activity).getCurrentSettings().getRule());
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before, countBudgets());
        assertRepeatingRow(budget, "2019-03-15", "2019-04-14", schedule.getRule(), "2019-01-15");
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(budget))) {
            scenario.onActivity(activity -> {
                checkBox(activity, R.id.repeat_checkbox).performClick();
                assertFalse(checkBox(activity, R.id.repeat_checkbox).isChecked());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.start_date_edit_text).getVisibility());
                startDatePicker(activity).setCurrentDateTime(date("2019-03-01"));
                checkBox(activity, R.id.repeat_checkbox).performClick();
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked());
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before, countBudgets());
        assertRepeatingRow(budget, "2019-03-15", "2019-04-14", schedule.getRule(), "2019-01-15");
        // the three early April expenses in wallet A plus the from leg of the transfer, which
        // counts here because wallet B is not part of this budget
        assertEquals(4000000000321000L, progressOf(budget));
    }

    @Test
    public void anEditOfARepeatingBudgetSchedulesTheRollAlarm() {
        Calendar anchorCalendar = Calendar.getInstance();
        anchorCalendar.add(Calendar.MONTH, -3);
        anchorCalendar.set(Calendar.DAY_OF_MONTH, 15);
        Date anchor = anchorCalendar.getTime();
        String anchorDate = DateUtils.getSQLDateString(anchor);
        String[] period = monthlyPeriod(15);
        long budget = insertBudget(Contract.BudgetType.EXPENSES, 300000L, "EUR",
                new long[] {mWalletA}, null, period[0], period[1], monthlyOn(anchor).getRule(),
                anchorDate, null);
        RecurrenceSetting yearly = yearlyOn(anchor);
        // a year from the stored anchor closes the period well after today, whatever day of the
        // month this runs on
        String yearlyEnd = yearlyEndAfter(anchorDate, period[0]);
        int before = countBudgets();
        AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        assertTrue(Shadows.shadowOf(alarmManager).getScheduledAlarms().isEmpty());
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(budget))) {
            scenario.onActivity(activity -> {
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked());
                recurrencePicker(activity).onRecurrenceSettingChanged(yearly);
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before, countBudgets());
        assertRepeatingRow(budget, period[0], yearlyEnd, yearly.getRule(), anchorDate);
        // the expense dated seconds ago is the only expense in wallet A inside that year
        assertEquals(900000000000L, progressOf(budget));
        // the roll wakes the day after the period the save wrote ends, at midnight
        assertEquals(1, Shadows.shadowOf(alarmManager).getScheduledAlarms().size());
        assertEquals(DateUtils.addDays(DateUtils.getCalendar(date(yearlyEnd)), 1).getTime(),
                Shadows.shadowOf(alarmManager).getScheduledAlarms().get(0).triggerAtTime);
    }

    @Test
    public void untickingRepeatMovesTheRollAlarmOntoTheNextRepeatingBudget() {
        Calendar anchorCalendar = Calendar.getInstance();
        anchorCalendar.add(Calendar.MONTH, -3);
        anchorCalendar.set(Calendar.DAY_OF_MONTH, 15);
        Date anchor = anchorCalendar.getTime();
        String anchorDate = DateUtils.getSQLDateString(anchor);
        String rule = monthlyOn(anchor).getRule();
        String[] period = monthlyPeriod(15);
        String[] periodPlusOne = periodYearsLater(period, 1);
        String[] periodPlusTwo = periodYearsLater(period, 2);
        long current = insertBudget(Contract.BudgetType.EXPENSES, 300000L, "EUR",
                new long[] {mWalletA}, null, period[0], period[1], rule, anchorDate, null);
        // the same schedule a year further out, so it owns the earliest end only once the budget
        // above stops repeating
        long later = insertBudget(Contract.BudgetType.EXPENSES, 300000L, "EUR",
                new long[] {mWalletA}, null, periodPlusOne[0], periodPlusOne[1], rule,
                periodPlusOne[0], null);
        // a third one on the same schedule two years out, so two candidates are left once the
        // budget above stops repeating and the alarm has to take the earlier of the two
        long latest = insertBudget(Contract.BudgetType.EXPENSES, 300000L, "EUR",
                new long[] {mWalletA}, null, periodPlusTwo[0], periodPlusTwo[1], rule,
                periodPlusTwo[0], null);
        int before = countBudgets();
        AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        assertTrue(Shadows.shadowOf(alarmManager).getScheduledAlarms().isEmpty());
        // arm the roll the way the app itself does when it starts up, on the earliest of the
        // three ends, which is the current budget's own
        RecurrenceBroadcastReceiver.scheduleRecurrenceTask(mContext);
        assertEquals(1, Shadows.shadowOf(alarmManager).getScheduledAlarms().size());
        assertEquals(DateUtils.addDays(DateUtils.getCalendar(date(period[1])), 1).getTime(),
                Shadows.shadowOf(alarmManager).getScheduledAlarms().get(0).triggerAtTime);
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(current))) {
            scenario.onActivity(activity -> {
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked());
                checkBox(activity, R.id.repeat_checkbox).performClick();
                assertFalse(checkBox(activity, R.id.repeat_checkbox).isChecked());
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before, countBudgets());
        Cursor row = budgetRow(current);
        assertTrue(row.isNull(row.getColumnIndex(Contract.Budget.RULE)));
        assertTrue(row.isNull(row.getColumnIndex(Contract.Budget.RULE_START)));
        assertEquals(period[0], row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals(period[1], row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        row.close();
        assertRepeatingRow(later, periodPlusOne[0], periodPlusOne[1], rule, periodPlusOne[0]);
        assertRepeatingRow(latest, periodPlusTwo[0], periodPlusTwo[1], rule, periodPlusTwo[0]);
        // two budgets are left repeating, and the roll moves onto the earlier of their two ends
        assertEquals(1, Shadows.shadowOf(alarmManager).getScheduledAlarms().size());
        assertEquals(DateUtils.addDays(DateUtils.getCalendar(date(periodPlusOne[1])), 1).getTime(),
                Shadows.shadowOf(alarmManager).getScheduledAlarms().get(0).triggerAtTime);
    }

    @Test
    public void untickingTheOnlyRepeatingBudgetClearsTheRollAlarm() {
        Calendar anchorCalendar = Calendar.getInstance();
        anchorCalendar.add(Calendar.MONTH, -3);
        anchorCalendar.set(Calendar.DAY_OF_MONTH, 15);
        Date anchor = anchorCalendar.getTime();
        String anchorDate = DateUtils.getSQLDateString(anchor);
        String rule = monthlyOn(anchor).getRule();
        String[] period = monthlyPeriod(15);
        long budget = insertBudget(Contract.BudgetType.EXPENSES, 300000L, "EUR",
                new long[] {mWalletA}, null, period[0], period[1], rule, anchorDate, null);
        int before = countBudgets();
        AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        assertTrue(Shadows.shadowOf(alarmManager).getScheduledAlarms().isEmpty());
        // arm the roll the way the app itself does when it starts up, off the row just inserted
        RecurrenceBroadcastReceiver.scheduleRecurrenceTask(mContext);
        assertEquals(1, Shadows.shadowOf(alarmManager).getScheduledAlarms().size());
        assertEquals(DateUtils.addDays(DateUtils.getCalendar(date(period[1])), 1).getTime(),
                Shadows.shadowOf(alarmManager).getScheduledAlarms().get(0).triggerAtTime);
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(budget))) {
            scenario.onActivity(activity -> {
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked());
                checkBox(activity, R.id.repeat_checkbox).performClick();
                assertFalse(checkBox(activity, R.id.repeat_checkbox).isChecked());
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before, countBudgets());
        // nothing repeats any more, and the receiver drops the alarm it holds before it looks,
        // so the roll armed above is gone and nothing takes its place
        assertTrue(Shadows.shadowOf(alarmManager).getScheduledAlarms().isEmpty());
        Cursor row = budgetRow(budget);
        assertTrue(row.isNull(row.getColumnIndex(Contract.Budget.RULE)));
        assertTrue(row.isNull(row.getColumnIndex(Contract.Budget.RULE_START)));
        assertEquals(period[0], row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals(period[1], row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        row.close();
    }

    @Test
    public void reAnchoringKeepsTheStoredAnchorAndMakesOneOddPeriod() {
        RecurrenceSetting monthly = monthlyOn(date("2019-01-15"));
        long budget = insertBudget(Contract.BudgetType.EXPENSES, 300000L, "EUR",
                new long[] {mWalletA}, null, "2019-03-15", "2019-04-14",
                monthly.getRule(), "2019-01-15", null);
        int before = countBudgets();
        RecurrenceSetting fortnightly = weeklyEvery(date("2019-01-20"), 2);
        RecurrenceSetting rebuilt = weeklyEvery(date("2019-01-15"), 2);
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(budget))) {
            scenario.onActivity(activity -> {
                recurrencePicker(activity).onRecurrenceSettingChanged(fortnightly);
                assertEquals(rebuilt.getUserReadableString(activity),
                        fieldText(activity, R.id.recurrence_edit_text));
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before, countBudgets());
        Cursor row = budgetRow(budget);
        assertEquals(rebuilt.getRule(), row.getString(row.getColumnIndex(Contract.Budget.RULE)));
        assertEquals("2019-01-15", row.getString(row.getColumnIndex(Contract.Budget.RULE_START)));
        assertEquals("2019-03-15", row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals(weeklyEndAfter("2019-01-15", "2019-03-15", 14),
                row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        // nothing in wallet A falls in the eleven days this odd period covers
        assertEquals(0L, row.getLong(row.getColumnIndex(Contract.Budget.PROGRESS)));
        row.close();
        assertEquals(Collections.singletonList(mWalletA), walletIdsOf(budget));
    }

    @Test
    public void aYearlyScheduleIsRebuiltOnTheStoredAnchor() {
        RecurrenceSetting monthly = monthlyOn(date("2019-01-15"));
        long budget = insertBudget(Contract.BudgetType.EXPENSES, 300000L, "EUR",
                new long[] {mWalletA}, null, "2019-03-15", "2019-04-14",
                monthly.getRule(), "2019-01-15", null);
        int before = countBudgets();
        RecurrenceSetting yearly = yearlyOn(date("2019-02-20"));
        RecurrenceSetting rebuilt = yearlyOn(date("2019-01-15"));
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(budget))) {
            scenario.onActivity(activity -> {
                recurrencePicker(activity).onRecurrenceSettingChanged(yearly);
                assertEquals(rebuilt.getUserReadableString(activity),
                        fieldText(activity, R.id.recurrence_edit_text));
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before, countBudgets());
        Cursor row = budgetRow(budget);
        assertEquals(rebuilt.getRule(), row.getString(row.getColumnIndex(Contract.Budget.RULE)));
        assertEquals("2019-01-15", row.getString(row.getColumnIndex(Contract.Budget.RULE_START)));
        assertEquals("2019-03-15", row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals(yearlyEndAfter("2019-01-15", "2019-03-15"),
                row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        // every expense in wallet A from 15 March 2019 to 14 January 2020, plus the leg the
        // transfer pays out of it, which counts because wallet B is not in this budget
        assertEquals(4300087000321000L, row.getLong(row.getColumnIndex(Contract.Budget.PROGRESS)));
        row.close();
        assertEquals(Collections.singletonList(mWalletA), walletIdsOf(budget));
    }

    @Test
    public void aYearlyScheduleAnchoredToALeapDayKeepsTheLastDayFallback() {
        RecurrenceSetting monthly = monthlyOn(date("2020-02-29"));
        long budget = insertBudget(Contract.BudgetType.EXPENSES, 300000L, "EUR",
                new long[] {mWalletA}, null, "2020-02-29", "2020-03-28",
                monthly.getRule(), "2020-02-29", null);
        int before = countBudgets();
        RecurrenceSetting rebuilt = yearlyOn(date("2020-02-29"));
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(budget))) {
            scenario.onActivity(activity -> {
                recurrencePicker(activity).onRecurrenceSettingChanged(yearlyOn(date("2021-07-04")));
                assertEquals(rebuilt.getUserReadableString(activity),
                        fieldText(activity, R.id.recurrence_edit_text));
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before, countBudgets());
        Cursor row = budgetRow(budget);
        assertEquals(rebuilt.getRule(), row.getString(row.getColumnIndex(Contract.Budget.RULE)));
        assertEquals("2020-02-29", row.getString(row.getColumnIndex(Contract.Budget.RULE_START)));
        assertEquals("2020-02-29", row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        // the next instance is 28 February 2021, the last day of a February with no 29th
        assertEquals("2021-02-27", row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        // no fixture row is dated in 2020 or 2021
        assertEquals(0L, row.getLong(row.getColumnIndex(Contract.Budget.PROGRESS)));
        row.close();
        assertEquals(Collections.singletonList(mWalletA), walletIdsOf(budget));
    }

    @Test
    public void turningRepeatOffWritesTheFieldsAndTurningItOnAnchorsToTheStartField() {
        RecurrenceSetting monthly = monthlyOn(date("2019-01-15"));
        long budget = insertBudget(Contract.BudgetType.EXPENSES, 300000L, "EUR",
                new long[] {mWalletA}, null, "2019-03-15", "2019-04-14",
                monthly.getRule(), "2019-01-15", null);
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(budget))) {
            scenario.onActivity(activity -> {
                checkBox(activity, R.id.repeat_checkbox).performClick();
                startDatePicker(activity).setCurrentDateTime(date("2019-05-01"));
                endDatePicker(activity).setCurrentDateTime(date("2019-05-31"));
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before, countBudgets());
        Cursor row = budgetRow(budget);
        assertEquals("2019-05-01", row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals("2019-05-31", row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        assertTrue(row.isNull(row.getColumnIndex(Contract.Budget.RULE)));
        assertTrue(row.isNull(row.getColumnIndex(Contract.Budget.RULE_START)));
        // the only expense in wallet A inside May 2019
        assertEquals(80000000000L, row.getLong(row.getColumnIndex(Contract.Budget.PROGRESS)));
        row.close();
        RecurrenceSetting fromTheDialog = monthlyOn(date("2019-06-20"));
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(budget))) {
            scenario.onActivity(activity -> {
                assertFalse(checkBox(activity, R.id.repeat_checkbox).isChecked());
                startDatePicker(activity).setCurrentDateTime(date("2019-05-10"));
                recurrencePicker(activity).onRecurrenceSettingChanged(fromTheDialog);
                assertEquals(monthlyOn(date("2019-05-10")).getUserReadableString(activity),
                        fieldText(activity, R.id.recurrence_edit_text));
                checkBox(activity, R.id.repeat_checkbox).performClick();
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked());
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before, countBudgets());
        row = budgetRow(budget);
        assertEquals("2019-05-10", row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals("2019-06-09", row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        assertEquals(monthlyOn(date("2019-05-10")).getRule(),
                row.getString(row.getColumnIndex(Contract.Budget.RULE)));
        assertEquals("2019-05-10", row.getString(row.getColumnIndex(Contract.Budget.RULE_START)));
        // nothing in wallet A between 10 May and 9 June 2019, the 3 May row is outside now
        assertEquals(0L, row.getLong(row.getColumnIndex(Contract.Budget.PROGRESS)));
        row.close();
    }

    @Test
    public void aPeriodTheChainHasMovedPastHidesTheRepeatBox() {
        long root = insertBudget(Contract.BudgetType.EXPENSES, 300000L, "EUR",
                new long[] {mWalletA}, null, "2019-03-15", "2019-04-14", null, "2019-01-15", null);
        long live = insertRolledPeriod(root, "2019-04-15", "2019-05-14");
        assertEquals(uuidOf(root) + ":2019-04-15", uuidOf(live));
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(root))) {
            scenario.onActivity(activity -> {
                assertEquals(View.GONE, activity.findViewById(R.id.repeat_checkbox).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.start_date_edit_text).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.end_date_edit_text).getVisibility());
                moneyPicker(activity).setMoney(350000L);
                save(activity);
                assertTrue(activity.isFinishing());
                assertNull(ShadowDialog.getLatestDialog());
            });
        }
        assertEquals(before, countBudgets());
        Cursor row = budgetRow(root);
        assertEquals(350000L, row.getLong(row.getColumnIndex(Contract.Budget.MONEY)));
        assertTrue(row.isNull(row.getColumnIndex(Contract.Budget.RULE)));
        assertEquals("2019-01-15", row.getString(row.getColumnIndex(Contract.Budget.RULE_START)));
        assertEquals("2019-03-15", row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals("2019-04-14", row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        row.close();
        // the save above finished the first launch, so the rotation runs on a second one
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(root))) {
            scenario.onActivity(activity ->
                    assertEquals(View.GONE, activity.findViewById(R.id.repeat_checkbox).getVisibility()));
            scenario.recreate();
            scenario.onActivity(activity ->
                    assertEquals(View.GONE, activity.findViewById(R.id.repeat_checkbox).getVisibility()));
        }
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(live))) {
            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE, activity.findViewById(R.id.repeat_checkbox).getVisibility());
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked());
            });
        }
    }

    @Test
    public void aClosedPeriodWithASpentRuleDropsTheRuleAndKeepsItsAnchor() {
        long root = insertBudget(Contract.BudgetType.EXPENSES, 300000L, "EUR",
                new long[] {mWalletA}, null, "2019-03-15", "2019-04-14",
                monthlyUntil(date("2019-01-15"), date("2019-02-01")).getRule(), "2019-01-15", null);
        insertRolledPeriod(root, "2019-04-15", "2019-05-14");
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(root))) {
            scenario.onActivity(activity -> {
                assertEquals(View.GONE, activity.findViewById(R.id.repeat_checkbox).getVisibility());
                moneyPicker(activity).setMoney(350000L);
                save(activity);
                assertTrue(activity.isFinishing());
                assertNull(ShadowDialog.getLatestDialog());
            });
        }
        assertEquals(before, countBudgets());
        Cursor row = budgetRow(root);
        assertEquals(350000L, row.getLong(row.getColumnIndex(Contract.Budget.MONEY)));
        assertTrue(row.isNull(row.getColumnIndex(Contract.Budget.RULE)));
        assertEquals("2019-01-15", row.getString(row.getColumnIndex(Contract.Budget.RULE_START)));
        assertEquals("2019-03-15", row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals("2019-04-14", row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        row.close();
    }

    @Test
    public void aRepeatingPeriodTheChainHasMovedPastKeepsTheBoxAndRefusesTheSave() {
        String rule = monthlyOn(date("2019-01-15")).getRule();
        long root = insertBudget(Contract.BudgetType.EXPENSES, 300000L, "EUR",
                new long[] {mWalletA}, null, "2019-03-15", "2019-04-14", rule, "2019-01-15", null);
        long live = insertRolledPeriod(root, "2019-04-15", "2019-05-14");
        assertEquals(uuidOf(root) + ":2019-04-15", uuidOf(live));
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(root))) {
            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE, activity.findViewById(R.id.repeat_checkbox).getVisibility());
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked());
                save(activity);
                assertFalse(activity.isFinishing());
                AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
                TextView message = dialog.findViewById(android.R.id.message);
                assertEquals(activity.getString(R.string.message_budget_period_superseded),
                        message.getText().toString());
            });
        }
        assertEquals(before, countBudgets());
        assertRepeatingRow(root, "2019-03-15", "2019-04-14", rule, "2019-01-15");
    }

    @Test
    public void aScheduleThatHasRunOutIsRefused() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(newItemIntent())) {
            scenario.onActivity(activity -> {
                recurrencePicker(activity).onRecurrenceSettingChanged(
                        monthlyUntil(date("2019-01-15"), date("2019-02-01")));
                checkBox(activity, R.id.repeat_checkbox).performClick();
                moneyPicker(activity).setMoney(250000L);
                save(activity);
                assertFalse(activity.isFinishing());
                assertNull(ShadowDialog.getLatestDialog());
                assertFalse(field(activity, R.id.recurrence_edit_text).validate());
            });
        }
        assertEquals(before, countBudgets());
    }

    @Test
    public void aStoredScheduleThatHasRunOutOpensOnTheDateFields() {
        long budget = insertBudget(Contract.BudgetType.EXPENSES, 300000L, "EUR",
                new long[] {mWalletA}, null, "2019-03-15", "2019-04-14",
                monthlyUntil(date("2019-01-15"), date("2019-02-01")).getRule(), "2019-01-15", null);
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(budget))) {
            scenario.onActivity(activity -> {
                assertFalse(checkBox(activity, R.id.repeat_checkbox).isChecked());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.repeat_checkbox).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.start_date_edit_text).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.end_date_edit_text).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.recurrence_edit_text).getVisibility());
            });
        }
    }

    @Test
    public void aChainIsOrderedByItsNamesAndNotByItsDates() {
        long root = insertBudget(Contract.BudgetType.EXPENSES, 300000L, "EUR",
                new long[] {mWalletA}, null, "2019-02-15", "2019-03-14", null, "2019-01-15", null);
        long earlier = insertRolledPeriod(root, "2019-03-15", "2019-04-14");
        long live = insertRolledPeriod(earlier, "2019-04-15", "2019-05-14");
        assertEquals(uuidOf(root) + ":2019-03-15", uuidOf(earlier));
        assertEquals(uuidOf(root) + ":2019-04-15", uuidOf(live));
        // the earlier period is moved past the live one, so the two run the other way round by
        // date while the names they were opened under are untouched
        ContentValues moved = new ContentValues();
        moved.put(Contract.Budget.TYPE, Contract.BudgetType.EXPENSES.getValue());
        moved.put(Contract.Budget.MONEY, 300000L);
        moved.put(Contract.Budget.WALLET_IDS, idList(new long[] {mWalletA}));
        moved.put(Contract.Budget.START_DATE, "2030-01-01");
        moved.put(Contract.Budget.END_DATE, "2030-01-31");
        mResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, earlier),
                moved, null, null);
        int before = countBudgets();
        String liveUuid = uuidOf(live);
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(live))) {
            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE, activity.findViewById(R.id.repeat_checkbox).getVisibility());
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked());
                save(activity);
                assertTrue(activity.isFinishing());
                assertNull(ShadowDialog.getLatestDialog());
            });
        }
        assertEquals(before, countBudgets());
        assertEquals(liveUuid, uuidOf(live));
        assertRepeatingRow(live, "2019-04-15", "2019-05-14",
                monthlyOn(date("2019-01-15")).getRule(), "2019-01-15");
        // the three expenses in wallet A between 15 April and 14 May 2019
        assertEquals(300087000000000L, progressOf(live));
    }

    @Test
    public void aRollOpeningALaterPeriodRefusesTheSave() {
        long root = insertBudget(Contract.BudgetType.EXPENSES, 300000L, "EUR",
                new long[] {mWalletA}, null, "2019-03-15", "2019-04-14", null, "2019-01-15", null);
        long live = insertRolledPeriod(root, "2019-04-15", "2019-05-14");
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(live))) {
            long later = insertRolledPeriod(live, "2019-05-15", "2019-06-14");
            assertEquals(uuidOf(root) + ":2019-05-15", uuidOf(later));
            int before = countBudgets();
            scenario.onActivity(activity -> {
                save(activity);
                assertFalse(activity.isFinishing());
                AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
                TextView message = dialog.findViewById(android.R.id.message);
                assertEquals(activity.getString(R.string.message_budget_period_superseded),
                        message.getText().toString());
            });
            assertEquals(before, countBudgets());
        }
        assertRepeatingRow(live, "2019-04-15", "2019-05-14",
                monthlyOn(date("2019-01-15")).getRule(), "2019-01-15");
        assertEquals(300087000000000L, progressOf(live));
    }

    @Test
    public void aRollOpeningALaterPeriodThenTurningRepeatOffKeepsTheAnchor() {
        long root = insertBudget(Contract.BudgetType.EXPENSES, 300000L, "EUR",
                new long[] {mWalletA}, null, "2019-03-15", "2019-04-14", null, "2019-01-15", null);
        long live = insertRolledPeriod(root, "2019-04-15", "2019-05-14");
        int before;
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(live))) {
            long later = insertRolledPeriod(live, "2019-05-15", "2019-06-14");
            assertEquals(uuidOf(root) + ":2019-05-15", uuidOf(later));
            before = countBudgets();
            scenario.onActivity(activity -> {
                checkBox(activity, R.id.repeat_checkbox).performClick();
                assertFalse(checkBox(activity, R.id.repeat_checkbox).isChecked());
                save(activity);
                assertTrue(activity.isFinishing());
                assertNull(ShadowDialog.getLatestDialog());
            });
        }
        assertEquals(before, countBudgets());
        Cursor row = budgetRow(live);
        assertTrue(row.isNull(row.getColumnIndex(Contract.Budget.RULE)));
        assertEquals("2019-01-15", row.getString(row.getColumnIndex(Contract.Budget.RULE_START)));
        assertEquals("2019-04-15", row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals("2019-05-14", row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        row.close();
    }

    @Test
    public void theLivePeriodOfAChainTurnedOffDropsTheAnchor() {
        long root = insertBudget(Contract.BudgetType.EXPENSES, 300000L, "EUR",
                new long[] {mWalletA}, null, "2019-03-15", "2019-04-14", null, "2019-01-15", null);
        long live = insertRolledPeriod(root, "2019-04-15", "2019-05-14");
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(live))) {
            scenario.onActivity(activity -> {
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked());
                checkBox(activity, R.id.repeat_checkbox).performClick();
                assertFalse(checkBox(activity, R.id.repeat_checkbox).isChecked());
                save(activity);
                assertTrue(activity.isFinishing());
                assertNull(ShadowDialog.getLatestDialog());
            });
        }
        assertEquals(before, countBudgets());
        Cursor row = budgetRow(live);
        assertTrue(row.isNull(row.getColumnIndex(Contract.Budget.RULE)));
        assertTrue(row.isNull(row.getColumnIndex(Contract.Budget.RULE_START)));
        assertEquals("2019-04-15", row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals("2019-05-14", row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        row.close();
    }

    @Test
    public void aWalletDeletedWhileTheEditorIsOpenReachesTheRefusalDialog() {
        long budget = insertAprilCategoryBudget(new long[] {mWalletA, mUnusedWallet});
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(budget))) {
            mResolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS,
                    mUnusedWallet), null, null);
            scenario.onActivity(activity -> {
                save(activity);
                AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
                TextView message = dialog.findViewById(android.R.id.message);
                assertEquals(activity.getString(R.string.error_input_missing_multiple_wallets),
                        message.getText().toString());
                assertFalse(activity.isFinishing());
            });
        }
        assertEquals(before, countBudgets());
        assertEquals(Collections.singletonList(mWalletA), walletIdsOf(budget));
    }

    @Test
    public void aNewBudgetOverAWalletMovedOntoAnotherCurrencyReachesTheRefusalDialog() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(newItemIntent())) {
            scenario.onActivity(activity -> {
                walletsPicker(activity).onWalletsSelected(new Wallet[] {
                        wallet(mWalletA, "Cash", "EUR"), wallet(mWalletB, "Bank", "EUR")});
                moneyPicker(activity).setMoney(500000L);
                startDatePicker(activity).setCurrentDateTime(date(APRIL_START));
                endDatePicker(activity).setCurrentDateTime(date(APRIL_END));
                ContentValues yen = new ContentValues();
                yen.put(Contract.Wallet.CURRENCY, "JPY");
                mResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS,
                        mWalletB), yen, null, null);
                save(activity);
                AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
                TextView message = dialog.findViewById(android.R.id.message);
                assertEquals(activity.getString(R.string.error_input_invalid_multiple_wallets),
                        message.getText().toString());
            });
        }
        assertEquals(before, countBudgets());
    }

    @Test
    public void aWalletMovedOntoAnotherCurrencyWhileTheEditorIsOpenReachesTheRefusalDialog() {
        long budget = insertBudget(Contract.BudgetType.EXPENSES, 900000L, "EUR",
                new long[] {mWalletA, mWalletB}, null, APRIL_START, APRIL_END, null, null, null);
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(budget))) {
            ContentValues yen = new ContentValues();
            yen.put(Contract.Wallet.CURRENCY, "JPY");
            mResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS,
                    mWalletB), yen, null, null);
            scenario.onActivity(activity -> {
                moneyPicker(activity).setMoney(999999L);
                save(activity);
                AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
                TextView message = dialog.findViewById(android.R.id.message);
                assertEquals(activity.getString(R.string.error_input_invalid_multiple_wallets),
                        message.getText().toString());
            });
        }
        assertEquals(before, countBudgets());
        Cursor row = budgetRow(budget);
        assertEquals(900000L, row.getLong(row.getColumnIndex(Contract.Budget.MONEY)));
        assertEquals(APRIL_START, row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals(APRIL_END, row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        row.close();
        assertEquals(Arrays.asList(mWalletA, mWalletB), walletIdsOf(budget));
    }

    @Test
    public void theOnlyWalletMovedOntoAnotherCurrencyWhileTheEditorIsOpenSavesInThatCurrency() {
        long budget = insertBudget(Contract.BudgetType.EXPENSES, 900000L, "EUR",
                new long[] {mWalletA}, null, APRIL_START, APRIL_END, null, null, null);
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(editIntent(budget))) {
            ContentValues yen = new ContentValues();
            yen.put(Contract.Wallet.CURRENCY, "JPY");
            mResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS,
                    mWalletA), yen, null, null);
            scenario.onActivity(activity -> {
                moneyPicker(activity).setMoney(999999L);
                save(activity);
                assertTrue(activity.isFinishing());
                assertNull(ShadowDialog.getLatestDialog());
            });
        }
        assertEquals(before, countBudgets());
        Cursor row = budgetRow(budget);
        assertEquals(999999L, row.getLong(row.getColumnIndex(Contract.Budget.MONEY)));
        assertEquals("JPY", row.getString(row.getColumnIndex(Contract.Budget.CURRENCY)));
        assertEquals(APRIL_START, row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals(APRIL_END, row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        row.close();
        assertEquals(Collections.singletonList(mWalletA), walletIdsOf(budget));
    }

    @Test
    public void aNewBudgetOverTheOnlyWalletMovedOntoAnotherCurrencySavesInThatCurrency() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        int before = countBudgets();
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(newItemIntent())) {
            scenario.onActivity(activity -> {
                moneyPicker(activity).setMoney(500000L);
                startDatePicker(activity).setCurrentDateTime(date(APRIL_START));
                endDatePicker(activity).setCurrentDateTime(date(APRIL_END));
                ContentValues yen = new ContentValues();
                yen.put(Contract.Wallet.CURRENCY, "JPY");
                mResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS,
                        mWalletA), yen, null, null);
                save(activity);
                assertTrue(activity.isFinishing());
                assertNull(ShadowDialog.getLatestDialog());
            });
        }
        assertEquals(before + 1, countBudgets());
        long budget = newestBudget();
        Cursor row = budgetRow(budget);
        assertEquals(500000L, row.getLong(row.getColumnIndex(Contract.Budget.MONEY)));
        assertEquals("JPY", row.getString(row.getColumnIndex(Contract.Budget.CURRENCY)));
        assertEquals(APRIL_START, row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals(APRIL_END, row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        row.close();
        assertEquals(Collections.singletonList(mWalletA), walletIdsOf(budget));
    }

    @Test
    public void aCurrencySentWithABudgetIsReplacedByItsWalletsCurrency() {
        long budget = insertBudget(Contract.BudgetType.EXPENSES, 900000L, "JPY",
                new long[] {mWalletA}, null, APRIL_START, APRIL_END, null, null, null);
        Cursor row = budgetRow(budget);
        assertEquals("EUR", row.getString(row.getColumnIndex(Contract.Budget.CURRENCY)));
        row.close();
        ContentValues sent = new ContentValues();
        sent.put(Contract.Budget.TYPE, Contract.BudgetType.EXPENSES.getValue());
        sent.put(Contract.Budget.MONEY, 900000L);
        sent.put(Contract.Budget.CURRENCY, "JPY");
        sent.put(Contract.Budget.WALLET_IDS, idList(new long[] {mWalletA}));
        sent.put(Contract.Budget.START_DATE, APRIL_START);
        sent.put(Contract.Budget.END_DATE, APRIL_END);
        mResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budget),
                sent, null, null);
        row = budgetRow(budget);
        assertEquals("EUR", row.getString(row.getColumnIndex(Contract.Budget.CURRENCY)));
        row.close();
    }

    @Test
    public void aRecreateHoldsTypedStateOnANewBudget() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        try (ActivityScenario<NewEditBudgetActivity> scenario =
                     ActivityScenario.launch(newItemIntent())) {
            String[] recurrenceText = new String[1];
            scenario.onActivity(activity -> {
                budgetTypePicker(activity).onBudgetTypeSelected(Contract.BudgetType.CATEGORY);
                categoryPicker(activity).onCategoriesSelected(new Category[] {category(mFood, "Food")});
                moneyPicker(activity).setMoney(7777L);
                checkBox(activity, R.id.repeat_checkbox).performClick();
                recurrenceText[0] = fieldText(activity, R.id.recurrence_edit_text);
                assertFalse(recurrenceText[0].isEmpty());
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(activity.getString(R.string.hint_category),
                        fieldText(activity, R.id.type_edit_text));
                assertEquals(View.VISIBLE, activity.findViewById(R.id.category_edit_text).getVisibility());
                assertEquals("Food", fieldText(activity, R.id.category_edit_text));
                assertEquals(Collections.singletonList(mFood), categoryPickerIds(activity));
                assertEquals(7777L, moneyPicker(activity).getCurrentMoney());
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.recurrence_edit_text).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.start_date_edit_text).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.end_date_edit_text).getVisibility());
                assertEquals(recurrenceText[0], fieldText(activity, R.id.recurrence_edit_text));
            });
        }
    }

    // expected figures

    /**
     * The signed sum a category budget over Food and Rent, in wallets A and B, reports for April
     * 2019. Snacks counts through its parent Food, Salary is in neither, the wallet D row and the
     * May row are outside, and both transfer legs carry a system category.
     */
    private static long aprilCategoryProgress() {
        return -(1000L + 20000L + 300000L + 50000000L + 7000000000L + 300000000000000L);
    }

    /**
     * The same rows read as an expenses budget, which counts them unsigned, leaves the income out
     * and drops the from leg of the transfer because wallet B is in the budget too.
     */
    private static long aprilExpensesProgress() {
        return 1000L + 20000L + 300000L + 50000000L + 7000000000L + 300000000000000L;
    }

    /**
     * The April expenses of wallet A alone, where the leg the transfer pays out of it counts
     * because wallet B is no longer in the budget, and the wallet B row of 9 April is gone.
     */
    private static long aprilExpensesInWalletAWithTheTransfer() {
        return 1000L + 20000L + 300000L + 7000000000L + 300000000000000L + 4000000000000000L;
    }

    // fixtures

    private void cancelRollAlarm() {
        AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(PendingIntent.getBroadcast(mContext, 0,
                new Intent(mContext, RecurrenceBroadcastReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    private long insertWallet(String name, String currency) {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, name);
        values.put(Contract.Wallet.ICON, ICON);
        values.put(Contract.Wallet.CURRENCY, currency);
        values.put(Contract.Wallet.START_MONEY, 0L);
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        values.put(Contract.Wallet.ARCHIVED, false);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_WALLETS, values));
    }

    private long insertArchivedWallet(String name, String currency) {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, name);
        values.put(Contract.Wallet.ICON, ICON);
        values.put(Contract.Wallet.CURRENCY, currency);
        values.put(Contract.Wallet.START_MONEY, 0L);
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        values.put(Contract.Wallet.ARCHIVED, true);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_WALLETS, values));
    }

    private long insertCategory(String name, Contract.CategoryType type, Long parent) {
        ContentValues values = new ContentValues();
        values.put(Contract.Category.NAME, name);
        values.put(Contract.Category.ICON, ICON);
        values.put(Contract.Category.TYPE, type.getValue());
        values.put(Contract.Category.SHOW_REPORT, true);
        if (parent != null) {
            values.put(Contract.Category.PARENT, parent);
        }
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, values));
    }

    private long insertTransaction(long walletId, String date, int direction, long categoryId, long money) {
        ContentValues values = new ContentValues();
        values.put(Contract.Transaction.MONEY, money);
        values.put(Contract.Transaction.DATE, date);
        values.put(Contract.Transaction.DESCRIPTION, "Fixture row");
        values.put(Contract.Transaction.CATEGORY_ID, categoryId);
        values.put(Contract.Transaction.DIRECTION, direction);
        values.put(Contract.Transaction.TYPE, NewEditTransactionActivity.TYPE_STANDARD);
        values.put(Contract.Transaction.WALLET_ID, walletId);
        values.put(Contract.Transaction.CONFIRMED, true);
        values.put(Contract.Transaction.COUNT_IN_TOTAL, true);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values));
    }

    private long insertTransfer(long from, long to, long money, String date) {
        ContentValues values = new TransferContentValuesBuilder()
                .description("Fixture transfer")
                .date(date)
                .fromWalletId(from)
                .toWalletId(to)
                .taxWalletId(from)
                .fromMoney(money)
                .toMoney(money)
                .taxMoney(0L)
                .note("")
                .placeId(null)
                .eventId(null)
                .confirmed(true)
                .countInTotal(true)
                .peopleIds(null)
                .attachmentIds(null)
                .build();
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_TRANSFERS, values));
    }

    private long insertBudget(Contract.BudgetType type, long money, String currency, long[] wallets,
                              long[] categories, String startDate, String endDate, String rule,
                              String ruleStart, Long rolledFrom) {
        ContentValues values = new ContentValues();
        values.put(Contract.Budget.TYPE, type.getValue());
        values.put(Contract.Budget.MONEY, money);
        values.put(Contract.Budget.CURRENCY, currency);
        values.put(Contract.Budget.WALLET_IDS, idList(wallets));
        values.put(Contract.Budget.CATEGORY_IDS, idList(categories));
        values.put(Contract.Budget.START_DATE, startDate);
        values.put(Contract.Budget.END_DATE, endDate);
        values.put(Contract.Budget.RULE, rule);
        values.put(Contract.Budget.RULE_START, ruleStart);
        if (rolledFrom != null) {
            values.put(Contract.Budget.ROLLED_FROM_ID, rolledFrom);
        }
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_BUDGETS, values));
    }

    private long insertAprilCategoryBudget(long[] wallets) {
        return insertBudget(Contract.BudgetType.CATEGORY, 900000L, "EUR", wallets,
                new long[] {mFood, mRent}, APRIL_START, APRIL_END, null, null, null);
    }

    private long insertRolledPeriod(long rolledFrom, String startDate, String endDate) {
        return insertBudget(Contract.BudgetType.EXPENSES, 300000L, "EUR", new long[] {mWalletA},
                null, startDate, endDate, monthlyOn(date("2019-01-15")).getRule(), "2019-01-15",
                rolledFrom);
    }

    private static String idList(long[] ids) {
        if (ids == null || ids.length == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (i != 0) {
                builder.append(",");
            }
            builder.append("<").append(ids[i]).append(">");
        }
        return builder.toString();
    }

    private static String secondsAgo(int seconds) {
        return DateUtils.getSQLDateTimeString(
                new Date(System.currentTimeMillis() - seconds * 1000L));
    }

    private static Date date(String sqlDate) {
        return DateUtils.getDateFromSQLDateString(sqlDate);
    }

    private static Category category(long id, String name) {
        return new Category(id, name, IconLoader.parse(ICON), Contract.CategoryType.EXPENSE);
    }

    private static Wallet wallet(long id, String name, String currency) {
        return new Wallet(id, name, IconLoader.parse(ICON), CurrencyManager.getCurrency(currency), 0L, 0L);
    }

    // schedules

    private static RecurrenceSetting monthlyOn(Date startDate) {
        RecurrenceSetting.Builder builder =
                new RecurrenceSetting.Builder(startDate, RecurrenceSetting.TYPE_MONTHLY);
        builder.setRepeatSameMonthDay();
        return builder.build();
    }

    private static RecurrenceSetting monthlyUntil(Date startDate, Date endDate) {
        RecurrenceSetting.Builder builder =
                new RecurrenceSetting.Builder(startDate, RecurrenceSetting.TYPE_MONTHLY);
        builder.setRepeatSameMonthDay();
        builder.setEndUntil(endDate);
        return builder.build();
    }

    private static RecurrenceSetting weeklyEvery(Date startDate, int weeks) {
        RecurrenceSetting.Builder builder =
                new RecurrenceSetting.Builder(startDate, RecurrenceSetting.TYPE_WEEKLY);
        builder.setOffset(weeks);
        return builder.build();
    }

    private static RecurrenceSetting yearlyOn(Date startDate) {
        RecurrenceSetting.Builder builder =
                new RecurrenceSetting.Builder(startDate, RecurrenceSetting.TYPE_YEARLY);
        builder.setRepeatSameYearDay();
        return builder.build();
    }

    /**
     * The period a monthly schedule anchored to the given day of the month is in today, as the
     * pair {start, end} in SQL date form. A month too short for that day takes its last day, the
     * same fallback the rule carries.
     */
    private static String[] monthlyPeriod(int anchorDay) {
        Calendar today = Calendar.getInstance();
        Calendar start = occurrenceIn(today, anchorDay);
        if (start.get(Calendar.DAY_OF_MONTH) > today.get(Calendar.DAY_OF_MONTH)) {
            Calendar previous = (Calendar) today.clone();
            previous.add(Calendar.MONTH, -1);
            start = occurrenceIn(previous, anchorDay);
        }
        Calendar next = (Calendar) start.clone();
        next.add(Calendar.MONTH, 1);
        next = occurrenceIn(next, anchorDay);
        next.add(Calendar.DAY_OF_MONTH, -1);
        return new String[] {DateUtils.getSQLDateString(start.getTime()),
                DateUtils.getSQLDateString(next.getTime())};
    }

    /**
     * The same period a whole number of years later, both ends stepped by the same amount. The
     * anchor day is always the 15th, so no month is short of it and no leap day arises.
     */
    private static String[] periodYearsLater(String[] period, int years) {
        Calendar start = DateUtils.getCalendar(date(period[0]));
        start.add(Calendar.YEAR, years);
        Calendar end = DateUtils.getCalendar(date(period[1]));
        end.add(Calendar.YEAR, years);
        return new String[] {DateUtils.getSQLDateString(start.getTime()),
                DateUtils.getSQLDateString(end.getTime())};
    }

    private static Calendar occurrenceIn(Calendar month, int anchorDay) {
        Calendar calendar = (Calendar) month.clone();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.DAY_OF_MONTH,
                Math.min(anchorDay, calendar.getActualMaximum(Calendar.DAY_OF_MONTH)));
        return calendar;
    }

    /**
     * The day before the first weekly instance strictly after a day, stepping the given number of
     * days at a time from the anchor.
     */
    private static String weeklyEndAfter(String anchor, String after, int step) {
        Calendar instance = DateUtils.getCalendar(date(anchor));
        Calendar limit = DateUtils.getCalendar(date(after));
        while (!instance.after(limit)) {
            instance.add(Calendar.DAY_OF_MONTH, step);
        }
        instance.add(Calendar.DAY_OF_MONTH, -1);
        return DateUtils.getSQLDateString(instance.getTime());
    }

    /**
     * The day before the first yearly instance strictly after a day, stepping a year at a time
     * from the anchor.
     */
    private static String yearlyEndAfter(String anchor, String after) {
        Calendar instance = DateUtils.getCalendar(date(anchor));
        Calendar limit = DateUtils.getCalendar(date(after));
        while (!instance.after(limit)) {
            instance.add(Calendar.YEAR, 1);
        }
        instance.add(Calendar.DAY_OF_MONTH, -1);
        return DateUtils.getSQLDateString(instance.getTime());
    }

    // intents

    private static Intent newItemIntent() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                NewEditBudgetActivity.class);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM);
        return intent;
    }

    private static Intent editIntent(long budgetId) {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                NewEditBudgetActivity.class);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.EDIT_ITEM);
        intent.putExtra(NewEditItemActivity.ID, budgetId);
        return intent;
    }

    // driving the screen

    private static MoneyPicker moneyPicker(NewEditBudgetActivity activity) {
        return (MoneyPicker) activity.getSupportFragmentManager().findFragmentByTag(TAG_MONEY_PICKER);
    }

    private static BudgetTypePicker budgetTypePicker(NewEditBudgetActivity activity) {
        return (BudgetTypePicker) activity.getSupportFragmentManager().findFragmentByTag(TAG_BUDGET_TYPE_PICKER);
    }

    private static CategoryPicker categoryPicker(NewEditBudgetActivity activity) {
        return (CategoryPicker) activity.getSupportFragmentManager().findFragmentByTag(TAG_CATEGORY_PICKER);
    }

    private static DateTimePicker startDatePicker(NewEditBudgetActivity activity) {
        return (DateTimePicker) activity.getSupportFragmentManager().findFragmentByTag(TAG_START_DATE_PICKER);
    }

    private static DateTimePicker endDatePicker(NewEditBudgetActivity activity) {
        return (DateTimePicker) activity.getSupportFragmentManager().findFragmentByTag(TAG_END_DATE_PICKER);
    }

    private static WalletPicker walletsPicker(NewEditBudgetActivity activity) {
        return (WalletPicker) activity.getSupportFragmentManager().findFragmentByTag(TAG_WALLETS_PICKER);
    }

    private static RecurrencePicker recurrencePicker(NewEditBudgetActivity activity) {
        return (RecurrencePicker) activity.getSupportFragmentManager().findFragmentByTag(TAG_RECURRENCE_PICKER);
    }

    private static MaterialEditText field(NewEditBudgetActivity activity, int viewId) {
        return activity.findViewById(viewId);
    }

    private static String fieldText(NewEditBudgetActivity activity, int viewId) {
        return field(activity, viewId).getTextAsString();
    }

    private static String headerText(NewEditBudgetActivity activity, int viewId) {
        TextView textView = activity.findViewById(viewId);
        return textView.getText().toString();
    }

    private static String formattedMoney(String currency, long money) {
        return MoneyFormatter.getInstance().getNotTintedString(CurrencyManager.getCurrency(currency),
                money, MoneyFormatter.CurrencyMode.ALWAYS_HIDDEN);
    }

    /**
     * What the formatter writes for a day into a date field, read off a throwaway view so the
     * expectation is the formatter itself and not a second copy of its pattern.
     */
    private static String formattedDate(NewEditBudgetActivity activity, Date date) {
        TextView textView = new TextView(activity);
        DateFormatter.applyDate(textView, date);
        return textView.getText().toString();
    }

    private static CheckBox checkBox(NewEditBudgetActivity activity, int viewId) {
        return activity.findViewById(viewId);
    }

    private static List<Long> walletPickerIds(NewEditBudgetActivity activity) {
        List<Long> ids = new ArrayList<>();
        for (Wallet wallet : walletsPicker(activity).getCurrentWallets()) {
            ids.add(wallet.getId());
        }
        return ids;
    }

    private static List<Long> categoryPickerIds(NewEditBudgetActivity activity) {
        List<Long> ids = new ArrayList<>();
        for (Category category : categoryPicker(activity).getCurrentCategories()) {
            ids.add(category.getId());
        }
        return ids;
    }

    private static List<Long> sorted(List<Long> ids) {
        List<Long> copy = new ArrayList<>(ids);
        Collections.sort(copy);
        return copy;
    }

    /**
     * Saves through the toolbar menu, the way a tap on the check mark does. The dispatch answers
     * false even after it has run the save, because NewEditItemActivity.onMenuItemClick returns
     * false for every item, so what proves the save arrived is the row read back afterwards.
     */
    private static void save(NewEditBudgetActivity activity) {
        Toolbar toolbar = activity.findViewById(R.id.primary_toolbar);
        boolean handled = toolbar.getMenu().performIdentifierAction(R.id.action_save_changes, 0);
        assertFalse("the toolbar menu answered " + handled, handled);
    }

    // reading back

    private static String[] budgetProjection() {
        return new String[] {
                Contract.Budget.ID,
                Contract.Budget.TYPE,
                Contract.Budget.CATEGORY_ID,
                Contract.Budget.CATEGORY_IDS,
                Contract.Budget.START_DATE,
                Contract.Budget.END_DATE,
                Contract.Budget.MONEY,
                Contract.Budget.CURRENCY,
                Contract.Budget.RULE,
                Contract.Budget.RULE_START,
                Contract.Budget.PROGRESS
        };
    }

    private Cursor budgetRow(long budgetId) {
        Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budgetId);
        Cursor cursor = mResolver.query(uri, budgetProjection(), null, null, null);
        assertTrue(cursor.moveToFirst());
        return cursor;
    }

    private long newestBudget() {
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_BUDGETS,
                new String[] {Contract.Budget.ID}, null, null, Contract.Budget.ID + " DESC");
        assertTrue(cursor.moveToFirst());
        long id = cursor.getLong(0);
        cursor.close();
        return id;
    }

    private int countBudgets() {
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_BUDGETS,
                new String[] {Contract.Budget.ID}, null, null, null);
        int count = cursor.getCount();
        cursor.close();
        return count;
    }

    private long progressOf(long budgetId) {
        Cursor cursor = budgetRow(budgetId);
        long progress = cursor.getLong(cursor.getColumnIndex(Contract.Budget.PROGRESS));
        cursor.close();
        return progress;
    }

    /**
     * The money on every transaction the budget's own list carries, added up.
     */
    private long listedMoneyOf(long budgetId) {
        Uri uri = Uri.withAppendedPath(
                ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budgetId),
                "transactions");
        Cursor cursor = mResolver.query(uri, new String[] {Contract.Transaction.MONEY},
                null, null, null);
        long total = 0L;
        while (cursor.moveToNext()) {
            total += cursor.getLong(0);
        }
        cursor.close();
        return total;
    }

    private void assertRepeatingRow(long budgetId, String startDate, String endDate, String rule,
                                    String ruleStart) {
        Cursor row = budgetRow(budgetId);
        assertEquals(startDate, row.getString(row.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals(endDate, row.getString(row.getColumnIndex(Contract.Budget.END_DATE)));
        assertEquals(rule, row.getString(row.getColumnIndex(Contract.Budget.RULE)));
        assertEquals(ruleStart, row.getString(row.getColumnIndex(Contract.Budget.RULE_START)));
        row.close();
    }

    private String uuidOf(long budgetId) {
        Cursor cursor = mResolver.query(SyncContentProvider.CONTENT_BUDGET,
                new String[] {Contract.BUDGET_UUID}, Contract.Budget.ID + " = ?",
                new String[] {String.valueOf(budgetId)}, null);
        assertTrue(cursor.moveToFirst());
        String uuid = cursor.getString(0);
        cursor.close();
        assertNotNull(uuid);
        return uuid;
    }

    private List<Long> walletIdsOf(long budgetId) {
        return linkedIds(budgetId, "wallets", Contract.Wallet.ID);
    }

    private List<Long> categoryIdsOf(long budgetId) {
        return linkedIds(budgetId, "categories", Contract.Category.ID);
    }

    private List<Long> linkedIds(long budgetId, String path, String column) {
        Uri uri = Uri.withAppendedPath(
                ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budgetId), path);
        Cursor cursor = mResolver.query(uri, new String[] {column}, null, null, null);
        List<Long> ids = new ArrayList<>();
        while (cursor.moveToNext()) {
            ids.add(cursor.getLong(0));
        }
        cursor.close();
        Collections.sort(ids);
        return ids;
    }
}
