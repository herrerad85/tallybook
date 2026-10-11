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

package com.oriondev.moneywallet.service;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.oriondev.moneywallet.model.DataFormat;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.SplitLedger;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.database.data.AbstractDataExporter;
import com.oriondev.moneywallet.storage.database.data.Constants;
import com.oriondev.moneywallet.storage.database.data.csv.CSVDataImporter;
import com.oriondev.moneywallet.storage.database.data.csv.CsvImportMapping;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.oriondev.moneywallet.storage.database.SplitLedger.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * An export writes one row per category line, so the rows of a receipt add up to its total and
 * each names its own category.
 */
@RunWith(RobolectricTestRunner.class)
public class SplitExportTest {

    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        PreferenceManager.initialize(mContext);
        TestDatabases.useFreshDatabase(mContext);
        SplitLedger.build(mContext);
    }

    @Test
    public void theRowsOfAReceiptAddUpToItsTotalEachUnderItsCategory() throws Exception {
        File folder = new File(mContext.getCacheDir(), "split-export");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        AbstractDataExporter exporter = ImportExportIntentService.exportTransactions(mContext,
                DataFormat.CSV, folder, null, null, null, false, null);
        Map<String, List<String>> rows = new HashMap<>();
        try (CSVReader reader = new CSVReader(new FileReader(exporter.getOutputFile()))) {
            List<String> header = Arrays.asList(reader.readNext());
            int category = header.indexOf(Constants.COLUMN_CATEGORY);
            int money = header.indexOf(Constants.COLUMN_MONEY);
            int description = header.indexOf(Constants.COLUMN_DESCRIPTION);
            String[] row;
            while ((row = reader.readNext()) != null) {
                long minor = new BigDecimal(row[money]).movePointRight(2).longValueExact();
                rows.computeIfAbsent(row[description], key -> new ArrayList<>()).add(row[category] + ":" + minor);
            }
        }
        for (long[] transaction : TRANSACTIONS) {
            long sign = transaction[3] == Contract.Direction.EXPENSE ? -1 : 1;
            List<String> expected = new ArrayList<>();
            long sum = 0;
            for (long[] line : LINES.get(transaction[0])) {
                expected.add(NAMES.get(line[0]) + ":" + sign * line[1]);
            }
            List<String> actual = rows.get(description(transaction[0]));
            for (String cell : actual) {
                sum += Long.parseLong(cell.substring(cell.indexOf(':') + 1));
            }
            Collections.sort(expected);
            Collections.sort(actual);
            assertEquals("rows of " + transaction[0], expected, actual);
            assertEquals("sum of " + transaction[0], sign * transaction[5], sum);
        }
    }

    @Test
    public void reimportingTheExportIntoItsOwnLedgerSkipsEveryRow() throws Exception {
        File folder = new File(mContext.getCacheDir(), "split-reimport");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        AbstractDataExporter exporter = ImportExportIntentService.exportTransactions(mContext,
                DataFormat.CSV, folder, null, null, null, false, null);
        int rows = 0;
        try (CSVReader reader = new CSVReader(new FileReader(exporter.getOutputFile()))) {
            reader.readNext();
            while (reader.readNext() != null) {
                rows++;
            }
        }
        CSVDataImporter importer = new CSVDataImporter(mContext, exporter.getOutputFile());
        try {
            importer.importData();
            assertEquals(rows, importer.getAlreadySavedRows());
        } finally {
            importer.close();
        }
    }

    /**
     * A file made before the receipts were split, a bank statement or an older export, holds each
     * receipt as one row for its whole total, and the ledger still holds that total.
     */
    @Test
    public void aFileMadeBeforeTheSplitInsertsNothing() throws Exception {
        SQLiteDatabase db = database(mContext);
        db.execSQL("UPDATE transaction_splits SET deleted = 1");
        File folder = new File(mContext.getCacheDir(), "split-before");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        AbstractDataExporter exporter = ImportExportIntentService.exportTransactions(mContext,
                DataFormat.CSV, folder, null, null, null, false, null);
        for (long[] part : PARTS) {
            db.execSQL("UPDATE transaction_splits SET deleted = ? WHERE split_id = ?",
                    new Object[] {part[4], part[0]});
        }
        List<String[]> rows = readRows(exporter.getOutputFile());
        assertEquals(TRANSACTIONS.length, rows.size() - 1);
        CSVDataImporter importer = new CSVDataImporter(mContext, exporter.getOutputFile());
        try {
            importer.importData();
            assertEquals(TRANSACTIONS.length, importer.getAlreadySavedRows());
        } finally {
            importer.close();
        }
        assertEquals(TRANSACTIONS.length, countTransactions(db));
    }

    /**
     * A row the file holds twice and the ledger once is a real repeat, so the second copy is
     * imported, also when the ledger holds split receipts beside it.
     */
    @Test
    public void anUnsplitRowTheFileHoldsTwiceImportsOnce() throws Exception {
        File folder = new File(mContext.getCacheDir(), "split-twice");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        AbstractDataExporter exporter = ImportExportIntentService.exportTransactions(mContext,
                DataFormat.CSV, folder, null, null, null, false, null);
        List<String[]> rows = readRows(exporter.getOutputFile());
        int lines = 0;
        for (List<long[]> transactionLines : LINES.values()) {
            lines += transactionLines.size();
        }
        assertEquals(lines, rows.size() - 1);
        int description = Arrays.asList(rows.get(0)).indexOf(Constants.COLUMN_DESCRIPTION);
        String[] repeat = null;
        for (String[] row : rows) {
            if (description(PLAIN_CASH).equals(row[description])) {
                repeat = row;
            }
        }
        rows.add(repeat);
        File file = new File(folder, "twice.csv");
        try (CSVWriter writer = new CSVWriter(new FileWriter(file))) {
            writer.writeAll(rows);
        }
        CSVDataImporter importer = new CSVDataImporter(mContext, file);
        int skipped;
        try {
            importer.importData();
            skipped = importer.getAlreadySavedRows();
        } finally {
            importer.close();
        }
        SQLiteDatabase db = database(mContext);
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM transactions WHERE deleted = 0 " +
                "AND transaction_description = ?", new String[] {description(PLAIN_CASH)})) {
            cursor.moveToFirst();
            assertEquals(2, cursor.getInt(0));
        }
        assertEquals(lines, skipped);
        assertEquals(TRANSACTIONS.length + 1, countTransactions(db));
    }

    /**
     * A file made before the split uses up the receipt by its total, so a new purchase the same
     * day at the same shop, for the amount of one of its parts, is still imported, wherever the
     * file puts it.
     */
    @Test
    public void aNewRowEqualToAPartAfterAFileMadeBeforeTheSplitIsImported() throws Exception {
        importNewPartAfterAFileMadeBeforeTheSplit("split-new-part-last", false);
    }

    @Test
    public void aNewRowEqualToAPartBeforeTheTotalOfAFileMadeBeforeTheSplitIsImported() throws Exception {
        importNewPartAfterAFileMadeBeforeTheSplit("split-new-part-first", true);
    }

    private void importNewPartAfterAFileMadeBeforeTheSplit(String name, boolean first) throws Exception {
        long part = LINES.get(RECEIPT).get(2)[1];
        List<String[]> rows = exportRows(name, true);
        rows.add(first ? 1 : rows.size(), withMoney(rows, RECEIPT, -part));
        assertEquals(TRANSACTIONS.length, importRows(name, rows));
        assertEquals(sorted(part, transaction(RECEIPT)[5]), moneysOf(RECEIPT, CASH));
        assertEquals(TRANSACTIONS.length + 1, countTransactions(database(mContext)));
    }

    /**
     * An export made after the split uses up the receipt by its lines, so a new purchase the same
     * day at the same shop, for the receipt's whole total, is still imported, wherever the file
     * puts it.
     */
    @Test
    public void aNewRowEqualToTheTotalAfterAnExportMadeAfterTheSplitIsImported() throws Exception {
        importNewTotalAfterAnExportMadeAfterTheSplit("split-new-total-last", false);
    }

    @Test
    public void aNewRowEqualToTheTotalBeforeTheLinesOfAnExportMadeAfterTheSplitIsImported() throws Exception {
        importNewTotalAfterAnExportMadeAfterTheSplit("split-new-total-first", true);
    }

    private void importNewTotalAfterAnExportMadeAfterTheSplit(String name, boolean first) throws Exception {
        long total = transaction(RECEIPT)[5];
        List<String[]> rows = exportRows(name, false);
        int lines = rows.size() - 1;
        rows.add(first ? 1 : rows.size(), withMoney(rows, RECEIPT, -total));
        assertEquals(lines, importRows(name, rows));
        assertEquals(sorted(total, total), moneysOf(RECEIPT, CASH));
        assertEquals(TRANSACTIONS.length + 1, countTransactions(database(mContext)));
    }

    /**
     * An unsplit row the same day at the same shop, for the amount of a part of the receipt, is
     * matched by the file's row of that amount, which leaves the receipt to its total, in either
     * order.
     */
    @Test
    public void anUnsplitRowEqualToAPartDoesNotTakeTheReceipt() throws Exception {
        long part = LINES.get(RECEIPT).get(2)[1];
        long total = transaction(RECEIPT)[5];
        insertUnsplit(961, part);
        for (boolean receiptFirst : new boolean[] {true, false}) {
            String name = "split-unsplit-part-" + receiptFirst;
            List<String[]> rows = exportRows(name, true);
            assertEquals(TRANSACTIONS.length + 1, rows.size() - 1);
            moveToTop(rows, RECEIPT, receiptFirst ? part : total);
            moveToTop(rows, RECEIPT, receiptFirst ? total : part);
            assertEquals(name, TRANSACTIONS.length + 1, importRows(name, rows));
            assertEquals(name, sorted(part, total), moneysOf(RECEIPT, CASH));
        }
    }

    /**
     * When unsplit rows the same day at the same shop hold the amount of every line of the
     * receipt, a file made before the split matches each of them by its own row and the receipt
     * by its total, in any order.
     */
    @Test
    public void unsplitRowsEqualToEveryLineLeaveTheReceiptToItsTotal() throws Exception {
        long total = transaction(RECEIPT)[5];
        long remainder = LINES.get(RECEIPT).get(0)[1];
        long household = LINES.get(RECEIPT).get(1)[1];
        long fuel = LINES.get(RECEIPT).get(2)[1];
        insertUnsplit(961, remainder);
        insertUnsplit(962, household);
        insertUnsplit(963, fuel);
        List<Long> expected = sorted(total, remainder, household, fuel);
        for (boolean totalFirst : new boolean[] {true, false}) {
            String name = "split-unsplit-every-" + totalFirst;
            List<String[]> rows = exportRows(name, true);
            assertEquals(TRANSACTIONS.length + 3, rows.size() - 1);
            for (long money : totalFirst ? new long[] {fuel, household, remainder, total}
                    : new long[] {total, remainder, household, fuel}) {
                moveToTop(rows, RECEIPT, money);
            }
            assertEquals(name, TRANSACTIONS.length + 3, importRows(name, rows));
            assertEquals(name, expected, moneysOf(RECEIPT, CASH));
        }
    }

    /** Two parts of the same amount are two lines, and its own export skips each once. */
    @Test
    public void twoEqualPartsAreEachSkippedOnce() throws Exception {
        long part = 2500;
        long total = 9000;
        SQLiteDatabase db = database(mContext);
        insertUnsplit(970, total);
        db.execSQL("UPDATE transactions SET transaction_description = ? WHERE transaction_id = 970",
                new Object[] {description(970)});
        for (long id : new long[] {971, 972}) {
            db.execSQL("INSERT INTO transaction_splits (split_id, _transaction, split_category, " +
                    "split_money, uuid, last_edit, deleted) VALUES (?, 970, ?, ?, ?, 1, 0)",
                    new Object[] {id, HOUSEHOLD, part, "split-" + id});
        }
        List<String[]> rows = exportRows("split-equal-parts", false);
        int lines = rows.size() - 1;
        assertEquals(sorted(total - 2 * part, part, part), exportedMoneys(rows, 970));
        rows.add(withMoney(rows, 970, -part));
        assertEquals(lines, importRows("split-equal-parts", rows));
        assertEquals(sorted(part, total), moneysOf(970, CASH));
    }

    /**
     * Each wallet is settled by its own rows of the file. The receipt's lines filed under another
     * wallet are new there and do not stand for the receipt.
     */
    @Test
    public void theLinesOfAReceiptInAnotherWalletDoNotTakeIt() throws Exception {
        List<String[]> rows = exportRows("split-other-wallet", true);
        List<String[]> after = exportRows("split-other-wallet-after", false);
        List<String> header = Arrays.asList(after.get(0));
        int wallet = header.indexOf(Constants.COLUMN_WALLET);
        int description = header.indexOf(Constants.COLUMN_DESCRIPTION);
        for (String[] row : after.subList(1, after.size())) {
            if (description(RECEIPT).equals(row[description])) {
                String[] copy = row.clone();
                copy[wallet] = "Bank";
                rows.add(1, copy);
            }
        }
        assertEquals(TRANSACTIONS.length, importRows("split-other-wallet", rows));
        assertEquals(sorted(transaction(RECEIPT)[5]), moneysOf(RECEIPT, CASH));
        List<Long> lines = new ArrayList<>();
        for (long[] line : LINES.get(RECEIPT)) {
            lines.add(line[1]);
        }
        Collections.sort(lines);
        assertEquals(lines, moneysOf(RECEIPT, BANK));
    }

    /**
     * A statement read through a mapping never holds category lines, so the receipt is matched
     * by its total, and new purchases the same day at the same shop for the amounts of its lines
     * are all imported. A date only statement puts every row of a day at midnight.
     */
    @Test
    public void aMappedStatementSkipsTheTotalAndImportsNewRowsEqualToTheLines() throws Exception {
        String day = DATE.substring(0, 10);
        database(mContext).execSQL("UPDATE transactions SET transaction_date = ? WHERE transaction_id = ?",
                new Object[] {day + " 00:00:00", RECEIPT});
        long total = transaction(RECEIPT)[5];
        List<Long> expected = new ArrayList<>();
        expected.add(total);
        List<String[]> rows = new ArrayList<>();
        rows.add(statementRow(day, RECEIPT, total));
        for (long[] line : LINES.get(RECEIPT)) {
            rows.add(statementRow(day, RECEIPT, line[1]));
            expected.add(line[1]);
        }
        Collections.sort(expected);
        int skipped = importMapped("split-mapped", rows);
        assertEquals(expected, moneysOf(RECEIPT, CASH));
        assertEquals(1, skipped);
    }

    /**
     * Three receipts the same day at the same shop, 100.00 split 60.00 and 40.00, 60.00 and 40.00,
     * each split too. A date only statement made before the splits holds the three totals, and
     * the parts of the first equal the totals of the others, so it adds nothing in any order.
     */
    @Test
    public void aMappedStatementAddsNothingWhenAPartEqualsAnotherSplitTotal() throws Exception {
        String date = DATE.substring(0, 10) + " 00:00:00";
        insertExpense(980, 10000, date, 980);
        insertPart(990, 980, 6000);
        insertExpense(981, 6000, date, 980);
        insertPart(991, 981, 2500);
        insertExpense(982, 4000, date, 980);
        insertPart(992, 982, 1000);
        List<Long> expected = sorted(10000, 6000, 4000);
        long[][] orders = {{10000, 6000, 4000}, {6000, 4000, 10000}, {6000, 10000, 4000}};
        for (long[] order : orders) {
            String name = "split-mapped-three-" + order[0] + "-" + order[1];
            List<String[]> rows = new ArrayList<>();
            for (long cents : order) {
                rows.add(statementRow(DATE.substring(0, 10), 980, cents));
            }
            int skipped = importMapped(name, rows);
            assertEquals(name, expected, moneysOf(980, CASH));
            assertEquals(name, order.length, skipped);
        }
    }

    /**
     * The same day, 100.00 split 60.00 and 40.00, and 60.00 split too. A date only statement
     * holding both totals and a new purchase of 40.00 skips the totals and imports the new row.
     */
    @Test
    public void aMappedStatementImportsANewRowEqualToAPartWhenAnotherSplitTotalEqualsTheOther() throws Exception {
        String day = DATE.substring(0, 10);
        insertExpense(980, 10000, day + " 00:00:00", 980);
        insertPart(990, 980, 6000);
        insertExpense(981, 6000, day + " 00:00:00", 980);
        insertPart(991, 981, 2500);
        List<String[]> rows = new ArrayList<>();
        rows.add(statementRow(day, 980, 10000));
        rows.add(statementRow(day, 980, 6000));
        rows.add(statementRow(day, 980, 10000 - 6000));
        int skipped = importMapped("split-mapped-remainder", rows);
        assertEquals(sorted(10000, 6000, 10000 - 6000), moneysOf(980, CASH));
        assertEquals(2, skipped);
    }

    /**
     * A receipt with two equal parts holds that line twice, so a file made before the split that
     * holds the receipt's total and one new row of the part's amount leaves the receipt to its
     * total, and the new rows import.
     */
    @Test
    public void aRowEqualToAPartTheReceiptHoldsTwiceDoesNotTakeItsLines() throws Exception {
        long part = 2500;
        long total = 9000;
        SQLiteDatabase db = database(mContext);
        insertUnsplit(970, total);
        db.execSQL("UPDATE transactions SET transaction_description = ? WHERE transaction_id = 970",
                new Object[] {description(970)});
        List<String[]> rows = exportRows("split-equal-parts-before", false);
        int lines = 1;
        for (List<long[]> transactionLines : LINES.values()) {
            lines += transactionLines.size();
        }
        assertEquals(lines, rows.size() - 1);
        insertPart(971, 970, part);
        insertPart(972, 970, part);
        rows.add(withMoney(rows, 970, -part));
        rows.add(withMoney(rows, 970, -(total - 2 * part)));
        int skipped = importRows("split-equal-parts-before", rows);
        assertEquals(sorted(total, part, total - 2 * part), moneysOf(970, CASH));
        assertEquals(lines, skipped);
    }

    /**
     * The check pass reads the wallet a row names, and a failure of that read is the database's,
     * so it is not reported against the row.
     */
    @Test
    public void aDatabaseFailureWhileCheckingIsNotBlamedOnALine() throws Exception {
        database(mContext).execSQL("DROP TABLE transaction_splits");
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {Constants.COLUMN_WALLET, Constants.COLUMN_CURRENCY,
                Constants.COLUMN_CATEGORY, Constants.COLUMN_DATETIME, Constants.COLUMN_MONEY,
                Constants.COLUMN_DESCRIPTION});
        rows.add(new String[] {"Cash", "EUR", "Groceries", DATE, "-1.00", "new"});
        try {
            importRows("split-failed-read", rows);
            fail("the read has to fail");
        } catch (SQLException expected) {
            assertFalse(expected.getMessage(), expected.getMessage().startsWith("Line"));
        }
    }

    /** An expense in Cash under Groceries at that date, under the description of another id. */
    private void insertExpense(long id, long money, String date, long describedAs) {
        database(mContext).execSQL("INSERT INTO transactions (transaction_id, transaction_money, " +
                "transaction_date, transaction_description, transaction_category, " +
                "transaction_direction, transaction_type, transaction_wallet, transaction_confirmed, " +
                "transaction_count_in_total, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, " +
                "1, 1, ?, 1, 0)", new Object[] {id, money, date, description(describedAs), GROCERIES,
                Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, CASH, "transaction-" + id});
    }

    /** A part under Fuel. */
    private void insertPart(long id, long transaction, long money) {
        database(mContext).execSQL("INSERT INTO transaction_splits (split_id, _transaction, split_category, " +
                "split_money, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, 1, 0)",
                new Object[] {id, transaction, FUEL, money, "split-" + id});
    }

    /** A row of a bank statement, date, description and the amount spent, in cents. */
    private static String[] statementRow(String day, long transaction, long cents) {
        return new String[] {day, description(transaction), BigDecimal.valueOf(-cents, 2).toPlainString()};
    }

    /**
     * Imports the rows under a statement header through a mapping into Cash, reading dates
     * without a time, and answers how many were skipped as already saved.
     */
    private int importMapped(String name, List<String[]> rows) throws Exception {
        File file = new File(mContext.getCacheDir(), name + ".csv");
        List<String[]> lines = new ArrayList<>();
        lines.add(new String[] {"Date", "Description", "Amount"});
        lines.addAll(rows);
        try (CSVWriter writer = new CSVWriter(new FileWriter(file))) {
            writer.writeAll(lines);
        }
        CsvImportMapping mapping = new CsvImportMapping();
        mapping.date = 0;
        mapping.description = 1;
        mapping.amount = 2;
        mapping.datePattern = "yyyy-MM-dd";
        mapping.walletId = CASH;
        CSVDataImporter importer = new CSVDataImporter(mContext, file, mapping);
        try {
            importer.importData();
            return importer.getAlreadySavedRows();
        } finally {
            importer.close();
        }
    }

    /**
     * The pass that checks the file, which also reads the wallets it names, creates nothing, so a
     * file refused on a later row leaves no new wallet, category, place or person behind.
     */
    @Test
    public void aRefusedFileCreatesNothing() throws Exception {
        SQLiteDatabase db = database(mContext);
        String[] tables = {"wallets", "categories", "places", "people", "transactions"};
        int[] before = new int[tables.length];
        for (int i = 0; i < tables.length; i++) {
            before[i] = count(db, tables[i]);
        }
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {Constants.COLUMN_WALLET, Constants.COLUMN_CURRENCY,
                Constants.COLUMN_CATEGORY, Constants.COLUMN_DATETIME, Constants.COLUMN_MONEY,
                Constants.COLUMN_DESCRIPTION, Constants.COLUMN_PLACE, Constants.COLUMN_PEOPLE});
        rows.add(new String[] {"Cash", "EUR", "Groceries", DATE, "-100.00", description(RECEIPT), "", ""});
        rows.add(new String[] {"Fresh", "EUR", "Brand new", DATE, "-1.00", "new", "Somewhere", "Someone"});
        rows.add(new String[] {"Fresh", "EUR", "Brand new", DATE, "not money", "new", "", ""});
        try {
            importRows("split-refused", rows);
            fail("the file has to be refused");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().startsWith("Line 4: "));
        }
        for (int i = 0; i < tables.length; i++) {
            assertEquals(tables[i], before[i], count(db, tables[i]));
        }
    }

    private static int count(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    /** An unsplit row on the receipt's wallet, date, description and main category. */
    private void insertUnsplit(long id, long money) {
        long[] receipt = transaction(RECEIPT);
        database(mContext).execSQL("INSERT INTO transactions (transaction_id, transaction_money, " +
                "transaction_date, transaction_description, transaction_category, " +
                "transaction_direction, transaction_type, transaction_wallet, transaction_confirmed, " +
                "transaction_count_in_total, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, " +
                "1, 1, ?, 1, 0)", new Object[] {id, money, DATE, description(RECEIPT), receipt[2],
                receipt[3], receipt[4], receipt[1], "transaction-" + id});
    }

    /** Moves the row of the transaction holding this amount, in cents, to the top of the file. */
    private static void moveToTop(List<String[]> rows, long transaction, long cents) {
        List<String> header = Arrays.asList(rows.get(0));
        int description = header.indexOf(Constants.COLUMN_DESCRIPTION);
        int money = header.indexOf(Constants.COLUMN_MONEY);
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (description(transaction).equals(row[description])
                    && new BigDecimal(row[money]).movePointRight(2).abs().longValueExact() == cents) {
                rows.add(1, rows.remove(i));
                return;
            }
        }
        throw new AssertionError("no row of " + transaction + " for " + cents);
    }

    private static List<Long> exportedMoneys(List<String[]> rows, long transaction) {
        List<String> header = Arrays.asList(rows.get(0));
        int description = header.indexOf(Constants.COLUMN_DESCRIPTION);
        int money = header.indexOf(Constants.COLUMN_MONEY);
        List<Long> moneys = new ArrayList<>();
        for (String[] row : rows.subList(1, rows.size())) {
            if (description(transaction).equals(row[description])) {
                moneys.add(new BigDecimal(row[money]).movePointRight(2).abs().longValueExact());
            }
        }
        Collections.sort(moneys);
        return moneys;
    }

    /** The amounts the wallet holds under the transaction's description, smallest first. */
    private List<Long> moneysOf(long transaction, long wallet) {
        List<Long> moneys = new ArrayList<>();
        try (Cursor cursor = database(mContext).rawQuery("SELECT transaction_money FROM transactions " +
                "WHERE deleted = 0 AND transaction_description = ? AND transaction_wallet = ? " +
                "ORDER BY transaction_money", new String[] {description(transaction), String.valueOf(wallet)})) {
            while (cursor.moveToNext()) {
                moneys.add(cursor.getLong(0));
            }
        }
        return moneys;
    }

    private static List<Long> sorted(long... moneys) {
        List<Long> list = new ArrayList<>();
        for (long money : moneys) {
            list.add(money);
        }
        Collections.sort(list);
        return list;
    }

    /**
     * A part of zero leaves a line under the main category for the whole total. A file made
     * before the split that holds the receipt twice imports the second copy.
     */
    @Test
    public void aReceiptWithAPartOfZeroIsUsedUpOnce() throws Exception {
        insertZeroPart(database(mContext));
        List<String[]> rows = exportRows("split-zero-twice", true);
        int description = Arrays.asList(rows.get(0)).indexOf(Constants.COLUMN_DESCRIPTION);
        for (String[] row : new ArrayList<>(rows)) {
            if (description(PLAIN_CASH).equals(row[description])) {
                rows.add(row);
            }
        }
        assertEquals(TRANSACTIONS.length, importRows("split-zero-twice", rows));
        SQLiteDatabase db = database(mContext);
        assertEquals(2, countDescribed(db, PLAIN_CASH));
        assertEquals(TRANSACTIONS.length + 1, countTransactions(db));
    }

    /** Its own export, the zero line included, still skips every row. */
    @Test
    public void theExportOfAReceiptWithAPartOfZeroSkipsEveryRow() throws Exception {
        insertZeroPart(database(mContext));
        List<String[]> rows = exportRows("split-zero-own", false);
        int lines = 1;
        for (List<long[]> transactionLines : LINES.values()) {
            lines += transactionLines.size();
        }
        assertEquals(lines, rows.size() - 1);
        assertEquals(lines, importRows("split-zero-own", rows));
        assertEquals(TRANSACTIONS.length, countTransactions(database(mContext)));
    }

    private static void insertZeroPart(SQLiteDatabase db) {
        db.execSQL("INSERT INTO transaction_splits (split_id, _transaction, split_category, " +
                "split_money, uuid, last_edit, deleted) VALUES (960, ?, ?, 0, 'split-960', 1, 0)",
                new Object[] {PLAIN_CASH, HOUSEHOLD});
    }

    /** The rows of an export, made with every part flagged deleted when before is set. */
    private List<String[]> exportRows(String name, boolean before) throws Exception {
        SQLiteDatabase db = database(mContext);
        List<Long> live = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("SELECT split_id FROM transaction_splits WHERE deleted = 0", null)) {
            while (cursor.moveToNext()) {
                live.add(cursor.getLong(0));
            }
        }
        if (before) {
            db.execSQL("UPDATE transaction_splits SET deleted = 1");
        }
        File folder = new File(mContext.getCacheDir(), name);
        assertTrue(folder.isDirectory() || folder.mkdirs());
        AbstractDataExporter exporter = ImportExportIntentService.exportTransactions(mContext,
                DataFormat.CSV, folder, null, null, null, false, null);
        for (long id : live) {
            db.execSQL("UPDATE transaction_splits SET deleted = 0 WHERE split_id = ?", new Object[] {id});
        }
        return readRows(exporter.getOutputFile());
    }

    /** Imports the rows and answers how many were skipped as already saved. */
    private int importRows(String name, List<String[]> rows) throws Exception {
        File file = new File(mContext.getCacheDir(), name + ".csv");
        try (CSVWriter writer = new CSVWriter(new FileWriter(file))) {
            writer.writeAll(rows);
        }
        CSVDataImporter importer = new CSVDataImporter(mContext, file);
        try {
            importer.importData();
            return importer.getAlreadySavedRows();
        } finally {
            importer.close();
        }
    }

    /** A copy of a row of the transaction carrying another amount, in cents. */
    private static String[] withMoney(List<String[]> rows, long transaction, long cents) {
        List<String> header = Arrays.asList(rows.get(0));
        int description = header.indexOf(Constants.COLUMN_DESCRIPTION);
        for (String[] row : rows) {
            if (description(transaction).equals(row[description])) {
                String[] copy = row.clone();
                copy[header.indexOf(Constants.COLUMN_MONEY)] = BigDecimal.valueOf(cents, 2).toPlainString();
                return copy;
            }
        }
        throw new AssertionError("no row of " + transaction);
    }

    private static int countDescribed(SQLiteDatabase db, long transaction) {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM transactions WHERE deleted = 0 " +
                "AND transaction_description = ?", new String[] {description(transaction)})) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    private static List<String[]> readRows(File file) throws Exception {
        try (CSVReader reader = new CSVReader(new FileReader(file))) {
            return new ArrayList<>(reader.readAll());
        }
    }

    private static int countTransactions(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM transactions WHERE deleted = 0", null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }
}
