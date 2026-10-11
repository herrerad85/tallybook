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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One ledger holding split transactions of every kind the category lines treat apart, valid and
 * invalid, beside ordinary rows in the same wallets and month. Every expected figure a test
 * reads from here is written out by hand from the rows below, never read from the app.
 */
public final class SplitLedger {

    public static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";
    public static final String DATE = "2026-01-15 12:00:00";
    private static final long EDIT = 1565000000000L;

    public static final long CASH = 901;
    public static final long BANK = 902;
    public static final long YEN = 903;

    public static final long GROCERIES = 910;
    public static final long HOUSEHOLD = 911;
    public static final long FUEL = 912;
    public static final long PHARMACY = 913;
    /** A child of Household kept out of the reports, while Household itself is shown. */
    public static final long HIDDEN = 914;
    public static final long SALARY = 915;
    public static final long BONUS = 916;
    /** Named only by a part flagged deleted. */
    public static final long GIFTS = 917;
    /** Named by nothing. */
    public static final long SPARE = 918;

    /** id, type, parent or 0, show_report. */
    private static final long[][] CATEGORIES = {
            {GROCERIES, Schema.CategoryType.EXPENSE, 0, 1},
            {HOUSEHOLD, Schema.CategoryType.EXPENSE, 0, 1},
            {FUEL, Schema.CategoryType.EXPENSE, 0, 1},
            {PHARMACY, Schema.CategoryType.EXPENSE, 0, 1},
            {HIDDEN, Schema.CategoryType.EXPENSE, HOUSEHOLD, 0},
            {SALARY, Schema.CategoryType.INCOME, 0, 1},
            {BONUS, Schema.CategoryType.INCOME, 0, 1},
            {GIFTS, Schema.CategoryType.EXPENSE, 0, 1},
            {SPARE, Schema.CategoryType.EXPENSE, 0, 1},
    };

    public static final Map<Long, String> NAMES = new HashMap<>();

    static {
        NAMES.put(GROCERIES, "Groceries");
        NAMES.put(HOUSEHOLD, "Household");
        NAMES.put(FUEL, "Fuel");
        NAMES.put(PHARMACY, "Pharmacy");
        NAMES.put(HIDDEN, "Hidden");
        NAMES.put(SALARY, "Salary");
        NAMES.put(BONUS, "Bonus");
        NAMES.put(GIFTS, "Gifts");
        NAMES.put(SPARE, "Spare");
    }

    /** A valid split with a remainder, 10000 under Groceries with parts of 2000 and 3000. */
    public static final long RECEIPT = 920;
    /** Parts adding up to exactly the total, so no line under the main category. */
    public static final long EXACT = 921;
    /** Parts adding up to more than the total. */
    public static final long OVER = 922;
    /** A part below zero. */
    public static final long NEGATIVE = 923;
    /** A part in an income category on an expense. */
    public static final long CROSSED = 924;
    /** Parts on a debt row. */
    public static final long DEBT_ROW = 925;
    /** A part in a hidden child of Household. */
    public static final long WITH_HIDDEN = 926;
    /** Two parts in the same category. */
    public static final long TWICE = 927;
    public static final long PLAIN_CASH = 928;
    public static final long PLAIN_BANK = 929;
    public static final long PAY = 950;
    /** A valid split income. */
    public static final long PAY_SPLIT = 951;

    /** id, wallet, main category, direction, type, total. */
    public static final long[][] TRANSACTIONS = {
            {RECEIPT, CASH, GROCERIES, Schema.Direction.EXPENSE, Contract.TransactionType.STANDARD, 10000},
            {EXACT, CASH, GROCERIES, Schema.Direction.EXPENSE, Contract.TransactionType.STANDARD, 4000},
            {OVER, CASH, PHARMACY, Schema.Direction.EXPENSE, Contract.TransactionType.STANDARD, 1000},
            {NEGATIVE, CASH, FUEL, Schema.Direction.EXPENSE, Contract.TransactionType.STANDARD, 3000},
            {CROSSED, CASH, HOUSEHOLD, Schema.Direction.EXPENSE, Contract.TransactionType.STANDARD, 2000},
            {DEBT_ROW, CASH, FUEL, Schema.Direction.EXPENSE, Contract.TransactionType.DEBT, 6000},
            {WITH_HIDDEN, CASH, HOUSEHOLD, Schema.Direction.EXPENSE, Contract.TransactionType.STANDARD, 5000},
            {TWICE, CASH, GROCERIES, Schema.Direction.EXPENSE, Contract.TransactionType.STANDARD, 7000},
            {PLAIN_CASH, CASH, GROCERIES, Schema.Direction.EXPENSE, Contract.TransactionType.STANDARD, 1100},
            {PLAIN_BANK, BANK, HOUSEHOLD, Schema.Direction.EXPENSE, Contract.TransactionType.STANDARD, 2200},
            {PAY, CASH, SALARY, Schema.Direction.INCOME, Contract.TransactionType.STANDARD, 30000},
            {PAY_SPLIT, CASH, SALARY, Schema.Direction.INCOME, Contract.TransactionType.STANDARD, 8000},
    };

    /** id, transaction, category, money, deleted. */
    public static final long[][] PARTS = {
            {930, RECEIPT, HOUSEHOLD, 2000, 0},
            {931, RECEIPT, FUEL, 3000, 0},
            {944, RECEIPT, GIFTS, 100, 1},
            {932, EXACT, PHARMACY, 1500, 0},
            {933, EXACT, FUEL, 2500, 0},
            {934, OVER, HOUSEHOLD, 800, 0},
            {935, OVER, FUEL, 700, 0},
            {936, NEGATIVE, HOUSEHOLD, -500, 0},
            {937, NEGATIVE, GROCERIES, 1000, 0},
            {938, CROSSED, SALARY, 500, 0},
            {939, DEBT_ROW, GROCERIES, 1000, 0},
            {940, WITH_HIDDEN, HIDDEN, 1200, 0},
            {941, TWICE, HOUSEHOLD, 1000, 0},
            {942, TWICE, HOUSEHOLD, 1500, 0},
            {943, PAY_SPLIT, BONUS, 3000, 0},
    };

    /**
     * The lines each transaction should read as, category and money, worked out by hand. An
     * invalid split reads as one line under its main category for its whole total.
     */
    public static final Map<Long, List<long[]>> LINES = new HashMap<>();

    static {
        LINES.put(RECEIPT, Arrays.asList(new long[] {GROCERIES, 10000 - 2000 - 3000},
                new long[] {HOUSEHOLD, 2000}, new long[] {FUEL, 3000}));
        LINES.put(EXACT, Arrays.asList(new long[] {PHARMACY, 1500}, new long[] {FUEL, 2500}));
        LINES.put(OVER, Arrays.asList(new long[][] {{PHARMACY, 1000}}));
        LINES.put(NEGATIVE, Arrays.asList(new long[][] {{FUEL, 3000}}));
        LINES.put(CROSSED, Arrays.asList(new long[][] {{HOUSEHOLD, 2000}}));
        LINES.put(DEBT_ROW, Arrays.asList(new long[][] {{FUEL, 6000}}));
        LINES.put(WITH_HIDDEN, Arrays.asList(new long[] {HOUSEHOLD, 5000 - 1200}, new long[] {HIDDEN, 1200}));
        LINES.put(TWICE, Arrays.asList(new long[] {GROCERIES, 7000 - 1000 - 1500},
                new long[] {HOUSEHOLD, 1000}, new long[] {HOUSEHOLD, 1500}));
        LINES.put(PLAIN_CASH, Arrays.asList(new long[][] {{GROCERIES, 1100}}));
        LINES.put(PLAIN_BANK, Arrays.asList(new long[][] {{HOUSEHOLD, 2200}}));
        LINES.put(PAY, Arrays.asList(new long[][] {{SALARY, 30000}}));
        LINES.put(PAY_SPLIT, Arrays.asList(new long[] {SALARY, 8000 - 3000}, new long[] {BONUS, 3000}));
    }

    private SplitLedger() {
    }

    public static void build(Context context) {
        SQLiteDatabase db = SQLDatabase.getShared(context).getWritableDatabase();
        insertWallet(db, CASH, "Cash", "EUR");
        insertWallet(db, BANK, "Bank", "EUR");
        insertWallet(db, YEN, "Yen", "JPY");
        for (long[] category : CATEGORIES) {
            db.execSQL("INSERT INTO categories (category_id, category_name, category_icon, " +
                            "category_type, category_parent, category_show_report, uuid, last_edit, " +
                            "deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)",
                    new Object[] {category[0], NAMES.get(category[0]), ICON, category[1],
                            category[2] != 0 ? category[2] : null, category[3],
                            "category-" + category[0], EDIT});
        }
        for (long[] transaction : TRANSACTIONS) {
            db.execSQL("INSERT INTO transactions (transaction_id, transaction_money, " +
                            "transaction_date, transaction_description, transaction_category, " +
                            "transaction_direction, transaction_type, transaction_wallet, " +
                            "transaction_confirmed, transaction_count_in_total, uuid, last_edit, " +
                            "deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 1, ?, ?, 0)",
                    new Object[] {transaction[0], transaction[5], DATE, description(transaction[0]),
                            transaction[2], transaction[3], transaction[4], transaction[1],
                            "transaction-" + transaction[0], EDIT});
        }
        for (long[] part : PARTS) {
            db.execSQL("INSERT INTO transaction_splits (split_id, _transaction, split_category, " +
                            "split_money, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    new Object[] {part[0], part[1], part[2], part[3], "split-" + part[0], EDIT, part[4]});
        }
    }

    public static SQLiteDatabase database(Context context) {
        return SQLDatabase.getShared(context).getWritableDatabase();
    }

    public static String description(long transaction) {
        return "receipt-" + transaction;
    }

    public static long[] transaction(long id) {
        for (long[] transaction : TRANSACTIONS) {
            if (transaction[0] == id) {
                return transaction;
            }
        }
        throw new IllegalArgumentException("no transaction " + id);
    }

    /**
     * Inserts a category budget over the given categories and both euro wallets, for January.
     */
    public static long insertCategoryBudget(Context context, long id, long... categories) {
        SQLiteDatabase db = SQLDatabase.getShared(context).getWritableDatabase();
        db.execSQL("INSERT INTO budgets (budget_id, budget_type, budget_category, budget_start_date, " +
                        "budget_end_date, budget_money, budget_currency, uuid, last_edit, deleted) " +
                        "VALUES (?, ?, ?, '2026-01-01', '2026-01-31', 100000, 'EUR', ?, ?, 0)",
                new Object[] {id, Schema.BudgetType.CATEGORY, categories[0], "budget-" + id, EDIT});
        for (long wallet : new long[] {CASH, BANK}) {
            db.execSQL("INSERT INTO budget_wallets (_budget, _wallet, uuid, last_edit, deleted) " +
                    "VALUES (?, ?, ?, ?, 0)", new Object[] {id, wallet, "bw-" + id + "-" + wallet, EDIT});
        }
        for (long category : categories) {
            db.execSQL("INSERT INTO budget_categories (_budget, _category, uuid, last_edit, deleted) " +
                    "VALUES (?, ?, ?, ?, 0)", new Object[] {id, category, "bc-" + id + "-" + category, EDIT});
        }
        return id;
    }

    private static void insertWallet(SQLiteDatabase db, long id, String name, String currency) {
        db.execSQL("INSERT INTO wallets (wallet_id, wallet_name, wallet_icon, wallet_currency, " +
                        "wallet_count_in_total, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, 0)",
                new Object[] {id, name, ICON, currency, "EUR".equals(currency) ? 1 : 0, "wallet-" + id, EDIT});
    }
}
