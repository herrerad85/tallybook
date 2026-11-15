/*
 * Copyright (c) 2026.
 *
 * This file is part of MoneyWallet.
 *
 * MoneyWallet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MoneyWallet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MoneyWallet.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.storage.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.model.RecurrenceSetting;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.utils.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.oriondev.moneywallet.storage.database.SplitLedger.*;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class BudgetPeriodProgressTest {

    private static final String START = "2026-01-01";
    private static final String END = "2099-12-31";
    private static final long EXPENSES = 1101;
    private static final long INCOMES = 1102;
    private static final long HOUSEHOLD_BUDGET = 1103;
    private static final long SPARE_WALLET = 960;
    private static final long[] EURO = {CASH, BANK};

    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        PreferenceManager.initialize(mContext);
        TestDatabases.useFreshDatabase(mContext);
        SplitLedger.build(mContext);
    }

    @Test
    public void eachBudgetOverItsOwnDatesReadsWhatItsProgressShows() {
        insertTransaction(970, CASH, HIDDEN, Schema.Direction.EXPENSE, 80, "2026-02-01 12:00:00", 1, 0);
        insertTransaction(971, CASH, HOUSEHOLD, Schema.Direction.EXPENSE, 50000, "2026-02-02 12:00:00", 1, 1);
        insertTransaction(972, CASH, HOUSEHOLD, Schema.Direction.EXPENSE, 60000, "2026-02-03 12:00:00", 0, 0);
        insertTransaction(973, CASH, HOUSEHOLD, Schema.Direction.EXPENSE, 70000, "2099-06-01 12:00:00", 1, 0);
        insertTransfer(CASH, BANK, 500, "2026-01-10 12:00:00");
        insertTransfer(CASH, YEN, 700, "2026-01-11 12:00:00");
        insertTransfer(YEN, BANK, 900, "2026-01-12 12:00:00");
        insertBudget(EXPENSES, Schema.BudgetType.EXPENSES, START, END, EURO);
        insertBudget(INCOMES, Schema.BudgetType.INCOMES, START, END, EURO);
        insertBudget(HOUSEHOLD_BUDGET, Schema.BudgetType.CATEGORY, START, END, EURO, HOUSEHOLD);
        assertEquals(10000 + 4000 + 1000 + 3000 + 2000 + 6000 + 5000 + 7000 + 1100 + 2200 + 80 + 700,
                cardProgressOf(EXPENSES));
        assertEquals(30000 + 8000 + 900, cardProgressOf(INCOMES));
        assertEquals(2000 + 2000 + 3800 + 1200 + 1000 + 1500 + 2200 + 80, cardProgressOf(HOUSEHOLD_BUDGET));
        for (long budget : new long[] {EXPENSES, INCOMES, HOUSEHOLD_BUDGET}) {
            assertEquals(Collections.singletonList(cardProgressOf(budget)),
                    progressOf(typeOf(budget), budget == HOUSEHOLD_BUDGET ? ids(HOUSEHOLD) : null, ids(EURO),
                            new String[] {START, END}));
        }
    }

    @Test
    public void threePeriodsBeforeOneCountTheirFirstAndLastDaysAndNotTheDayItStarts() {
        database(mContext).execSQL("INSERT INTO wallets (wallet_id, wallet_name, wallet_icon, " +
                "wallet_currency, wallet_count_in_total, uuid, last_edit, deleted) VALUES " +
                "(?, 'Spare', ?, 'EUR', 1, 'wallet-960', 1, 0)", new Object[] {SPARE_WALLET, ICON});
        insertTransaction(980, SPARE_WALLET, SPARE, Schema.Direction.EXPENSE, 10000000, "2026-01-14 12:00:00", 1, 0);
        insertTransaction(981, SPARE_WALLET, SPARE, Schema.Direction.EXPENSE, 1, "2026-01-15 00:00:00", 1, 0);
        insertTransaction(982, SPARE_WALLET, SPARE, Schema.Direction.EXPENSE, 10, "2026-02-14 23:59:59", 1, 0);
        insertTransaction(983, SPARE_WALLET, SPARE, Schema.Direction.EXPENSE, 100, "2026-02-15 00:00:00", 1, 0);
        insertTransaction(984, SPARE_WALLET, SPARE, Schema.Direction.EXPENSE, 1000, "2026-03-14 23:59:59", 1, 0);
        insertTransaction(985, SPARE_WALLET, SPARE, Schema.Direction.EXPENSE, 10000, "2026-03-15 00:00:00", 1, 0);
        insertTransaction(986, SPARE_WALLET, SPARE, Schema.Direction.EXPENSE, 100000, "2026-04-14 23:59:59", 1, 0);
        insertTransaction(987, SPARE_WALLET, SPARE, Schema.Direction.EXPENSE, 1000000, "2026-04-15 00:00:00", 1, 0);
        List<Date[]> periods = RecurrenceSetting.periodsBefore("FREQ=MONTHLY;BYMONTHDAY=15",
                DateUtils.getDateFromSQLDateString("2025-11-15"), DateUtils.getDateFromSQLDateString("2026-04-15"), 3);
        String[] dates = new String[2 * periods.size()];
        for (int i = 0; i < periods.size(); i++) {
            dates[2 * i] = DateUtils.getSQLDateString(periods.get(i)[0]);
            dates[2 * i + 1] = DateUtils.getSQLDateString(periods.get(i)[1]);
        }
        assertEquals(Arrays.asList("2026-01-15", "2026-02-14", "2026-02-15", "2026-03-14", "2026-03-15", "2026-04-14"),
                Arrays.asList(dates));
        String wallets = ids(new long[] {SPARE_WALLET});
        assertEquals(Arrays.asList(1L + 10L, 100L + 1000L, 10000L + 100000L),
                progressOf(Schema.BudgetType.EXPENSES, null, wallets, dates));
        assertEquals(Arrays.asList(10000L + 100000L, 0L, 1L + 10L),
                progressOf(Schema.BudgetType.EXPENSES, null, wallets, new String[] {dates[4], dates[5],
                        "2026-02-20", "2026-02-21", dates[0], dates[1]}));
    }

    @Test
    public void aTransferToTheSameWalletCountsOnBothSidesAsTheCardCountsIt() {
        insertBudget(EXPENSES, Schema.BudgetType.EXPENSES, START, END, EURO);
        insertBudget(INCOMES, Schema.BudgetType.INCOMES, START, END, EURO);
        long expenses = cardProgressOf(EXPENSES);
        long incomes = cardProgressOf(INCOMES);
        insertTransfer(CASH, CASH, 400, "2026-01-13 12:00:00");
        assertEquals(expenses + 400, cardProgressOf(EXPENSES));
        assertEquals(incomes + 400, cardProgressOf(INCOMES));
        for (long budget : new long[] {EXPENSES, INCOMES}) {
            assertEquals(Collections.singletonList(cardProgressOf(budget)),
                    progressOf(typeOf(budget), null, ids(EURO), new String[] {START, END}));
        }
    }

    @Test
    public void aWalletOnlyAnotherBudgetHoldsDoesNotHideATransferIn() {
        insertBudget(INCOMES, Schema.BudgetType.INCOMES, START, END, EURO);
        insertBudget(EXPENSES, Schema.BudgetType.EXPENSES, START, END, new long[] {YEN});
        long incomes = cardProgressOf(INCOMES);
        insertTransfer(YEN, BANK, 900, "2026-01-12 12:00:00");
        assertEquals(incomes + 900, cardProgressOf(INCOMES));
        assertEquals(Collections.singletonList(cardProgressOf(INCOMES)),
                progressOf(Schema.BudgetType.INCOMES, null, ids(EURO), new String[] {START, END}));
    }

    private int typeOf(long budget) {
        if (budget == INCOMES) {
            return Schema.BudgetType.INCOMES;
        }
        return budget == EXPENSES ? Schema.BudgetType.EXPENSES : Schema.BudgetType.CATEGORY;
    }

    private void insertTransaction(long id, long wallet, long category, int direction, long money, String date,
                                   int confirmed, int deleted) {
        database(mContext).execSQL("INSERT INTO transactions (transaction_id, transaction_money, " +
                        "transaction_date, transaction_description, transaction_category, " +
                        "transaction_direction, transaction_type, transaction_wallet, " +
                        "transaction_confirmed, transaction_count_in_total, uuid, last_edit, " +
                        "deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, 1, ?)",
                new Object[] {id, money, date, description(id), category, direction,
                        Contract.TransactionType.STANDARD, wallet, confirmed, "transaction-" + id, deleted});
    }

    private void insertTransfer(long from, long to, long money, String date) {
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
        mContext.getContentResolver().insert(DataContentProvider.CONTENT_TRANSFERS, values);
    }

    private void insertBudget(long id, int type, String start, String end, long[] wallets, long... categories) {
        database(mContext).execSQL("INSERT INTO budgets (budget_id, budget_type, budget_category, " +
                        "budget_start_date, budget_end_date, budget_money, budget_currency, uuid, " +
                        "last_edit, deleted) VALUES (?, ?, ?, ?, ?, 100000, 'EUR', ?, 1, 0)",
                new Object[] {id, type, categories.length > 0 ? categories[0] : null, start, end, "budget-" + id});
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

    private static String ids(long... ids) {
        StringBuilder builder = new StringBuilder();
        for (long id : ids) {
            if (builder.length() > 0) {
                builder.append(",");
            }
            builder.append("<").append(id).append(">");
        }
        return builder.toString();
    }

    private long cardProgressOf(long budget) {
        try (Cursor cursor = mContext.getContentResolver().query(DataContentProvider.CONTENT_BUDGETS,
                new String[] {Contract.Budget.PROGRESS}, Contract.Budget.ID + " = ?",
                new String[] {String.valueOf(budget)}, null)) {
            cursor.moveToFirst();
            return Math.abs(cursor.getLong(0));
        }
    }

    private List<Long> progressOf(int type, String categoryIds, String walletIds, String[] dates) {
        String[] selectionArgs = new String[3 + dates.length];
        selectionArgs[0] = String.valueOf(type);
        selectionArgs[1] = categoryIds;
        selectionArgs[2] = walletIds;
        System.arraycopy(dates, 0, selectionArgs, 3, dates.length);
        List<Long> progress = new ArrayList<>();
        try (Cursor cursor = mContext.getContentResolver().query(DataContentProvider.CONTENT_BUDGET_PERIOD_PROGRESS,
                null, null, selectionArgs, null)) {
            for (int i = 0; cursor.moveToNext(); i++) {
                assertEquals(dates[2 * i], cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.START_DATE)));
                assertEquals(dates[2 * i + 1], cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.END_DATE)));
                progress.add(cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Budget.PROGRESS)));
            }
        }
        return progress;
    }
}
