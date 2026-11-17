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

package com.oriondev.moneywallet.service

import android.content.Context
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase

import androidx.test.core.app.ApplicationProvider

import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import com.oriondev.moneywallet.model.DataFormat
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.SplitLedger
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.database.data.Constants
import com.oriondev.moneywallet.storage.database.data.csv.CSVDataImporter
import com.oriondev.moneywallet.storage.database.data.csv.CsvImportMapping
import com.oriondev.moneywallet.storage.preference.PreferenceManager

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.math.BigDecimal
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.HashMap

import com.oriondev.moneywallet.storage.database.SplitLedger.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

/**
 * An export writes one row per category line, so the rows of a receipt add up to its total and
 * each names its own category.
 */
@RunWith(RobolectricTestRunner::class)
class SplitExportTest {

    private lateinit var mContext: Context

    @Before
    fun setUp() {
        mContext = ApplicationProvider.getApplicationContext()
        PreferenceManager.initialize(mContext)
        TestDatabases.useFreshDatabase(mContext)
        SplitLedger.build(mContext)
    }

    @Test
    @Throws(Exception::class)
    fun theRowsOfAReceiptAddUpToItsTotalEachUnderItsCategory() {
        val folder = File(mContext.cacheDir, "split-export")
        assertTrue(folder.isDirectory || folder.mkdirs())
        val exporter = ImportExportIntentService.exportTransactions(mContext,
                DataFormat.CSV, folder, null, null, null, false, null)
        val rows: MutableMap<String, MutableList<String>> = HashMap()
        CSVReader(FileReader(exporter.outputFile)).use { reader ->
            val header = Arrays.asList(*reader.readNext())
            val category = header.indexOf(Constants.COLUMN_CATEGORY)
            val money = header.indexOf(Constants.COLUMN_MONEY)
            val description = header.indexOf(Constants.COLUMN_DESCRIPTION)
            var row = reader.readNext()
            while (row != null) {
                val minor = BigDecimal(row[money]).movePointRight(2).longValueExact()
                rows.computeIfAbsent(row[description]) { ArrayList() }.add(row[category] + ":" + minor)
                row = reader.readNext()
            }
        }
        for (transaction in TRANSACTIONS) {
            val sign: Long = if (transaction[3] == Contract.Direction.EXPENSE.toLong()) -1 else 1
            val expected: MutableList<String> = ArrayList()
            var sum: Long = 0
            for (line in LINES.get(transaction[0])!!) {
                expected.add(NAMES.get(line[0]) + ":" + sign * line[1])
            }
            val actual = rows.get(description(transaction[0]))!!
            for (cell in actual) {
                sum += cell.substring(cell.indexOf(':') + 1).toLong()
            }
            Collections.sort(expected)
            Collections.sort(actual)
            assertEquals("rows of " + transaction[0], expected, actual)
            assertEquals("sum of " + transaction[0], sign * transaction[5], sum)
        }
    }

    @Test
    @Throws(Exception::class)
    fun reimportingTheExportIntoItsOwnLedgerSkipsEveryRow() {
        val folder = File(mContext.cacheDir, "split-reimport")
        assertTrue(folder.isDirectory || folder.mkdirs())
        val exporter = ImportExportIntentService.exportTransactions(mContext,
                DataFormat.CSV, folder, null, null, null, false, null)
        var rows = 0
        CSVReader(FileReader(exporter.outputFile)).use { reader ->
            reader.readNext()
            while (reader.readNext() != null) {
                rows++
            }
        }
        val importer = CSVDataImporter(mContext, exporter.outputFile)
        try {
            importer.importData()
            assertEquals(rows, importer.alreadySavedRows)
        } finally {
            importer.close()
        }
    }

    /**
     * A file made before the receipts were split, a bank statement or an older export, holds each
     * receipt as one row for its whole total, and the ledger still holds that total.
     */
    @Test
    @Throws(Exception::class)
    fun aFileMadeBeforeTheSplitInsertsNothing() {
        val db = database(mContext)
        db.execSQL("UPDATE transaction_splits SET deleted = 1")
        val folder = File(mContext.cacheDir, "split-before")
        assertTrue(folder.isDirectory || folder.mkdirs())
        val exporter = ImportExportIntentService.exportTransactions(mContext,
                DataFormat.CSV, folder, null, null, null, false, null)
        for (part in PARTS) {
            db.execSQL("UPDATE transaction_splits SET deleted = ? WHERE split_id = ?",
                    arrayOf<Any>(part[4], part[0]))
        }
        val rows = readRows(exporter.outputFile)
        assertEquals(TRANSACTIONS.size, rows.size - 1)
        val importer = CSVDataImporter(mContext, exporter.outputFile)
        try {
            importer.importData()
            assertEquals(TRANSACTIONS.size, importer.alreadySavedRows)
        } finally {
            importer.close()
        }
        assertEquals(TRANSACTIONS.size, countTransactions(db))
    }

    /**
     * A row the file holds twice and the ledger once is a real repeat, so the second copy is
     * imported, also when the ledger holds split receipts beside it.
     */
    @Test
    @Throws(Exception::class)
    fun anUnsplitRowTheFileHoldsTwiceImportsOnce() {
        val folder = File(mContext.cacheDir, "split-twice")
        assertTrue(folder.isDirectory || folder.mkdirs())
        val exporter = ImportExportIntentService.exportTransactions(mContext,
                DataFormat.CSV, folder, null, null, null, false, null)
        val rows = readRows(exporter.outputFile)
        var lines = 0
        for (transactionLines in LINES.values) {
            lines += transactionLines.size
        }
        assertEquals(lines, rows.size - 1)
        val description = Arrays.asList(*rows.get(0)).indexOf(Constants.COLUMN_DESCRIPTION)
        var repeat: Array<String>? = null
        for (row in rows) {
            if (description(PLAIN_CASH).equals(row[description])) {
                repeat = row
            }
        }
        rows.add(repeat!!)
        val file = File(folder, "twice.csv")
        CSVWriter(FileWriter(file)).use { writer ->
            writer.writeAll(rows)
        }
        val importer = CSVDataImporter(mContext, file)
        val skipped: Int
        try {
            importer.importData()
            skipped = importer.alreadySavedRows
        } finally {
            importer.close()
        }
        val db = database(mContext)
        db.rawQuery("SELECT COUNT(*) FROM transactions WHERE deleted = 0 " +
                "AND transaction_description = ?", arrayOf(description(PLAIN_CASH))).use { cursor ->
            cursor.moveToFirst()
            assertEquals(2, cursor.getInt(0))
        }
        assertEquals(lines, skipped)
        assertEquals(TRANSACTIONS.size + 1, countTransactions(db))
    }

    /**
     * A file made before the split uses up the receipt by its total, so a new purchase the same
     * day at the same shop, for the amount of one of its parts, is still imported, wherever the
     * file puts it.
     */
    @Test
    @Throws(Exception::class)
    fun aNewRowEqualToAPartAfterAFileMadeBeforeTheSplitIsImported() {
        importNewPartAfterAFileMadeBeforeTheSplit("split-new-part-last", false)
    }

    @Test
    @Throws(Exception::class)
    fun aNewRowEqualToAPartBeforeTheTotalOfAFileMadeBeforeTheSplitIsImported() {
        importNewPartAfterAFileMadeBeforeTheSplit("split-new-part-first", true)
    }

    @Throws(Exception::class)
    private fun importNewPartAfterAFileMadeBeforeTheSplit(name: String, first: Boolean) {
        val part = LINES.get(RECEIPT)!!.get(2)[1]
        val rows = exportRows(name, true)
        rows.add(if (first) 1 else rows.size, withMoney(rows, RECEIPT, -part))
        assertEquals(TRANSACTIONS.size, importRows(name, rows))
        assertEquals(sorted(part, transaction(RECEIPT)[5]), moneysOf(RECEIPT, CASH))
        assertEquals(TRANSACTIONS.size + 1, countTransactions(database(mContext)))
    }

    /**
     * An export made after the split uses up the receipt by its lines, so a new purchase the same
     * day at the same shop, for the receipt's whole total, is still imported, wherever the file
     * puts it.
     */
    @Test
    @Throws(Exception::class)
    fun aNewRowEqualToTheTotalAfterAnExportMadeAfterTheSplitIsImported() {
        importNewTotalAfterAnExportMadeAfterTheSplit("split-new-total-last", false)
    }

    @Test
    @Throws(Exception::class)
    fun aNewRowEqualToTheTotalBeforeTheLinesOfAnExportMadeAfterTheSplitIsImported() {
        importNewTotalAfterAnExportMadeAfterTheSplit("split-new-total-first", true)
    }

    @Throws(Exception::class)
    private fun importNewTotalAfterAnExportMadeAfterTheSplit(name: String, first: Boolean) {
        val total = transaction(RECEIPT)[5]
        val rows = exportRows(name, false)
        val lines = rows.size - 1
        rows.add(if (first) 1 else rows.size, withMoney(rows, RECEIPT, -total))
        assertEquals(lines, importRows(name, rows))
        assertEquals(sorted(total, total), moneysOf(RECEIPT, CASH))
        assertEquals(TRANSACTIONS.size + 1, countTransactions(database(mContext)))
    }

    /**
     * An unsplit row the same day at the same shop, for the amount of a part of the receipt, is
     * matched by the file's row of that amount, which leaves the receipt to its total, in either
     * order.
     */
    @Test
    @Throws(Exception::class)
    fun anUnsplitRowEqualToAPartDoesNotTakeTheReceipt() {
        val part = LINES.get(RECEIPT)!!.get(2)[1]
        val total = transaction(RECEIPT)[5]
        insertUnsplit(961, part)
        for (receiptFirst in booleanArrayOf(true, false)) {
            val name = "split-unsplit-part-" + receiptFirst
            val rows = exportRows(name, true)
            assertEquals(TRANSACTIONS.size + 1, rows.size - 1)
            moveToTop(rows, RECEIPT, if (receiptFirst) part else total)
            moveToTop(rows, RECEIPT, if (receiptFirst) total else part)
            assertEquals(name, TRANSACTIONS.size + 1, importRows(name, rows))
            assertEquals(name, sorted(part, total), moneysOf(RECEIPT, CASH))
        }
    }

    /**
     * When unsplit rows the same day at the same shop hold the amount of every line of the
     * receipt, a file made before the split matches each of them by its own row and the receipt
     * by its total, in any order.
     */
    @Test
    @Throws(Exception::class)
    fun unsplitRowsEqualToEveryLineLeaveTheReceiptToItsTotal() {
        val total = transaction(RECEIPT)[5]
        val remainder = LINES.get(RECEIPT)!!.get(0)[1]
        val household = LINES.get(RECEIPT)!!.get(1)[1]
        val fuel = LINES.get(RECEIPT)!!.get(2)[1]
        insertUnsplit(961, remainder)
        insertUnsplit(962, household)
        insertUnsplit(963, fuel)
        val expected = sorted(total, remainder, household, fuel)
        for (totalFirst in booleanArrayOf(true, false)) {
            val name = "split-unsplit-every-" + totalFirst
            val rows = exportRows(name, true)
            assertEquals(TRANSACTIONS.size + 3, rows.size - 1)
            for (money in if (totalFirst) longArrayOf(fuel, household, remainder, total)
                    else longArrayOf(total, remainder, household, fuel)) {
                moveToTop(rows, RECEIPT, money)
            }
            assertEquals(name, TRANSACTIONS.size + 3, importRows(name, rows))
            assertEquals(name, expected, moneysOf(RECEIPT, CASH))
        }
    }

    /** Two parts of the same amount are two lines, and its own export skips each once. */
    @Test
    @Throws(Exception::class)
    fun twoEqualPartsAreEachSkippedOnce() {
        val part: Long = 2500
        val total: Long = 9000
        val db = database(mContext)
        insertUnsplit(970, total)
        db.execSQL("UPDATE transactions SET transaction_description = ? WHERE transaction_id = 970",
                arrayOf<Any>(description(970)))
        for (id in longArrayOf(971, 972)) {
            db.execSQL("INSERT INTO transaction_splits (split_id, _transaction, split_category, " +
                    "split_money, uuid, last_edit, deleted) VALUES (?, 970, ?, ?, ?, 1, 0)",
                    arrayOf<Any>(id, HOUSEHOLD, part, "split-" + id))
        }
        val rows = exportRows("split-equal-parts", false)
        val lines = rows.size - 1
        assertEquals(sorted(total - 2 * part, part, part), exportedMoneys(rows, 970))
        rows.add(withMoney(rows, 970, -part))
        assertEquals(lines, importRows("split-equal-parts", rows))
        assertEquals(sorted(part, total), moneysOf(970, CASH))
    }

    /**
     * Each wallet is settled by its own rows of the file. The receipt's lines filed under another
     * wallet are new there and do not stand for the receipt.
     */
    @Test
    @Throws(Exception::class)
    fun theLinesOfAReceiptInAnotherWalletDoNotTakeIt() {
        val rows = exportRows("split-other-wallet", true)
        val after = exportRows("split-other-wallet-after", false)
        val header = Arrays.asList(*after.get(0))
        val wallet = header.indexOf(Constants.COLUMN_WALLET)
        val description = header.indexOf(Constants.COLUMN_DESCRIPTION)
        for (row in after.subList(1, after.size)) {
            if (description(RECEIPT).equals(row[description])) {
                val copy = row.clone()
                copy[wallet] = "Bank"
                rows.add(1, copy)
            }
        }
        assertEquals(TRANSACTIONS.size, importRows("split-other-wallet", rows))
        assertEquals(sorted(transaction(RECEIPT)[5]), moneysOf(RECEIPT, CASH))
        val lines: MutableList<Long> = ArrayList()
        for (line in LINES.get(RECEIPT)!!) {
            lines.add(line[1])
        }
        Collections.sort(lines)
        assertEquals(lines, moneysOf(RECEIPT, BANK))
    }

    /**
     * A statement read through a mapping never holds category lines, so the receipt is matched
     * by its total, and new purchases the same day at the same shop for the amounts of its lines
     * are all imported. A date only statement puts every row of a day at midnight.
     */
    @Test
    @Throws(Exception::class)
    fun aMappedStatementSkipsTheTotalAndImportsNewRowsEqualToTheLines() {
        val day = DATE.substring(0, 10)
        database(mContext).execSQL("UPDATE transactions SET transaction_date = ? WHERE transaction_id = ?",
                arrayOf<Any>(day + " 00:00:00", RECEIPT))
        val total = transaction(RECEIPT)[5]
        val expected: MutableList<Long> = ArrayList()
        expected.add(total)
        val rows: MutableList<Array<String>> = ArrayList()
        rows.add(statementRow(day, RECEIPT, total))
        for (line in LINES.get(RECEIPT)!!) {
            rows.add(statementRow(day, RECEIPT, line[1]))
            expected.add(line[1])
        }
        Collections.sort(expected)
        val skipped = importMapped("split-mapped", rows)
        assertEquals(expected, moneysOf(RECEIPT, CASH))
        assertEquals(1, skipped)
    }

    /**
     * Three receipts the same day at the same shop, 100.00 split 60.00 and 40.00, 60.00 and 40.00,
     * each split too. A date only statement made before the splits holds the three totals, and
     * the parts of the first equal the totals of the others, so it adds nothing in any order.
     */
    @Test
    @Throws(Exception::class)
    fun aMappedStatementAddsNothingWhenAPartEqualsAnotherSplitTotal() {
        val date = DATE.substring(0, 10) + " 00:00:00"
        insertExpense(980, 10000, date, 980)
        insertPart(990, 980, 6000)
        insertExpense(981, 6000, date, 980)
        insertPart(991, 981, 2500)
        insertExpense(982, 4000, date, 980)
        insertPart(992, 982, 1000)
        val expected = sorted(10000, 6000, 4000)
        val orders = arrayOf(longArrayOf(10000, 6000, 4000), longArrayOf(6000, 4000, 10000), longArrayOf(6000, 10000, 4000))
        for (order in orders) {
            val name = "split-mapped-three-" + order[0] + "-" + order[1]
            val rows: MutableList<Array<String>> = ArrayList()
            for (cents in order) {
                rows.add(statementRow(DATE.substring(0, 10), 980, cents))
            }
            val skipped = importMapped(name, rows)
            assertEquals(name, expected, moneysOf(980, CASH))
            assertEquals(name, order.size, skipped)
        }
    }

    /**
     * The same day, 100.00 split 60.00 and 40.00, and 60.00 split too. A date only statement
     * holding both totals and a new purchase of 40.00 skips the totals and imports the new row.
     */
    @Test
    @Throws(Exception::class)
    fun aMappedStatementImportsANewRowEqualToAPartWhenAnotherSplitTotalEqualsTheOther() {
        val day = DATE.substring(0, 10)
        insertExpense(980, 10000, day + " 00:00:00", 980)
        insertPart(990, 980, 6000)
        insertExpense(981, 6000, day + " 00:00:00", 980)
        insertPart(991, 981, 2500)
        val rows: MutableList<Array<String>> = ArrayList()
        rows.add(statementRow(day, 980, 10000))
        rows.add(statementRow(day, 980, 6000))
        rows.add(statementRow(day, 980, 10000 - 6000))
        val skipped = importMapped("split-mapped-remainder", rows)
        assertEquals(sorted(10000, 6000, 10000 - 6000), moneysOf(980, CASH))
        assertEquals(2, skipped)
    }

    /**
     * A receipt with two equal parts holds that line twice, so a file made before the split that
     * holds the receipt's total and one new row of the part's amount leaves the receipt to its
     * total, and the new rows import.
     */
    @Test
    @Throws(Exception::class)
    fun aRowEqualToAPartTheReceiptHoldsTwiceDoesNotTakeItsLines() {
        val part: Long = 2500
        val total: Long = 9000
        val db = database(mContext)
        insertUnsplit(970, total)
        db.execSQL("UPDATE transactions SET transaction_description = ? WHERE transaction_id = 970",
                arrayOf<Any>(description(970)))
        val rows = exportRows("split-equal-parts-before", false)
        var lines = 1
        for (transactionLines in LINES.values) {
            lines += transactionLines.size
        }
        assertEquals(lines, rows.size - 1)
        insertPart(971, 970, part)
        insertPart(972, 970, part)
        rows.add(withMoney(rows, 970, -part))
        rows.add(withMoney(rows, 970, -(total - 2 * part)))
        val skipped = importRows("split-equal-parts-before", rows)
        assertEquals(sorted(total, part, total - 2 * part), moneysOf(970, CASH))
        assertEquals(lines, skipped)
    }

    /**
     * The check pass reads the wallet a row names, and a failure of that read is the database's,
     * so it is not reported against the row.
     */
    @Test
    @Throws(Exception::class)
    fun aDatabaseFailureWhileCheckingIsNotBlamedOnALine() {
        database(mContext).execSQL("DROP TABLE transaction_splits")
        val rows: MutableList<Array<String>> = ArrayList()
        rows.add(arrayOf(Constants.COLUMN_WALLET, Constants.COLUMN_CURRENCY,
                Constants.COLUMN_CATEGORY, Constants.COLUMN_DATETIME, Constants.COLUMN_MONEY,
                Constants.COLUMN_DESCRIPTION))
        rows.add(arrayOf("Cash", "EUR", "Groceries", DATE, "-1.00", "new"))
        try {
            importRows("split-failed-read", rows)
            fail("the read has to fail")
        } catch (expected: SQLException) {
            assertFalse(expected.message, expected.message!!.startsWith("Line"))
        }
    }

    /** An expense in Cash under Groceries at that date, under the description of another id. */
    private fun insertExpense(id: Long, money: Long, date: String, describedAs: Long) {
        database(mContext).execSQL("INSERT INTO transactions (transaction_id, transaction_money, " +
                "transaction_date, transaction_description, transaction_category, " +
                "transaction_direction, transaction_type, transaction_wallet, transaction_confirmed, " +
                "transaction_count_in_total, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, " +
                "1, 1, ?, 1, 0)", arrayOf<Any>(id, money, date, description(describedAs), GROCERIES,
                Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, CASH, "transaction-" + id))
    }

    /** A part under Fuel. */
    private fun insertPart(id: Long, transaction: Long, money: Long) {
        database(mContext).execSQL("INSERT INTO transaction_splits (split_id, _transaction, split_category, " +
                "split_money, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, 1, 0)",
                arrayOf<Any>(id, transaction, FUEL, money, "split-" + id))
    }

    /** A row of a bank statement, date, description and the amount spent, in cents. */
    private fun statementRow(day: String, transaction: Long, cents: Long): Array<String> {
        return arrayOf(day, description(transaction), BigDecimal.valueOf(-cents, 2).toPlainString())
    }

    /**
     * Imports the rows under a statement header through a mapping into Cash, reading dates
     * without a time, and answers how many were skipped as already saved.
     */
    @Throws(Exception::class)
    private fun importMapped(name: String, rows: List<Array<String>>): Int {
        val file = File(mContext.cacheDir, name + ".csv")
        val lines: MutableList<Array<String>> = ArrayList()
        lines.add(arrayOf("Date", "Description", "Amount"))
        lines.addAll(rows)
        CSVWriter(FileWriter(file)).use { writer ->
            writer.writeAll(lines)
        }
        val mapping = CsvImportMapping()
        mapping.date = 0
        mapping.description = 1
        mapping.amount = 2
        mapping.datePattern = "yyyy-MM-dd"
        mapping.walletId = CASH
        val importer = CSVDataImporter(mContext, file, mapping)
        try {
            importer.importData()
            return importer.alreadySavedRows
        } finally {
            importer.close()
        }
    }

    /**
     * The pass that checks the file, which also reads the wallets it names, creates nothing, so a
     * file refused on a later row leaves no new wallet, category, place or person behind.
     */
    @Test
    @Throws(Exception::class)
    fun aRefusedFileCreatesNothing() {
        val db = database(mContext)
        val tables = arrayOf("wallets", "categories", "places", "people", "transactions")
        val before = IntArray(tables.size)
        for (i in 0 until tables.size) {
            before[i] = count(db, tables[i])
        }
        val rows: MutableList<Array<String>> = ArrayList()
        rows.add(arrayOf(Constants.COLUMN_WALLET, Constants.COLUMN_CURRENCY,
                Constants.COLUMN_CATEGORY, Constants.COLUMN_DATETIME, Constants.COLUMN_MONEY,
                Constants.COLUMN_DESCRIPTION, Constants.COLUMN_PLACE, Constants.COLUMN_PEOPLE))
        rows.add(arrayOf("Cash", "EUR", "Groceries", DATE, "-100.00", description(RECEIPT), "", ""))
        rows.add(arrayOf("Fresh", "EUR", "Brand new", DATE, "-1.00", "new", "Somewhere", "Someone"))
        rows.add(arrayOf("Fresh", "EUR", "Brand new", DATE, "not money", "new", "", ""))
        try {
            importRows("split-refused", rows)
            fail("the file has to be refused")
        } catch (expected: RuntimeException) {
            assertTrue(expected.message, expected.message!!.startsWith("Line 4: "))
        }
        for (i in 0 until tables.size) {
            assertEquals(tables[i], before[i], count(db, tables[i]))
        }
    }

    private fun count(db: SQLiteDatabase, table: String): Int {
        db.rawQuery("SELECT COUNT(*) FROM " + table, null).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    /** An unsplit row on the receipt's wallet, date, description and main category. */
    private fun insertUnsplit(id: Long, money: Long) {
        val receipt = transaction(RECEIPT)
        database(mContext).execSQL("INSERT INTO transactions (transaction_id, transaction_money, " +
                "transaction_date, transaction_description, transaction_category, " +
                "transaction_direction, transaction_type, transaction_wallet, transaction_confirmed, " +
                "transaction_count_in_total, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, " +
                "1, 1, ?, 1, 0)", arrayOf<Any>(id, money, DATE, description(RECEIPT), receipt[2],
                receipt[3], receipt[4], receipt[1], "transaction-" + id))
    }

    /** Moves the row of the transaction holding this amount, in cents, to the top of the file. */
    private fun moveToTop(rows: MutableList<Array<String>>, transaction: Long, cents: Long) {
        val header = Arrays.asList(*rows.get(0))
        val description = header.indexOf(Constants.COLUMN_DESCRIPTION)
        val money = header.indexOf(Constants.COLUMN_MONEY)
        for (i in 1 until rows.size) {
            val row = rows.get(i)
            if (description(transaction).equals(row[description])
                    && BigDecimal(row[money]).movePointRight(2).abs().longValueExact() == cents) {
                rows.add(1, rows.removeAt(i))
                return
            }
        }
        throw AssertionError("no row of " + transaction + " for " + cents)
    }

    private fun exportedMoneys(rows: List<Array<String>>, transaction: Long): List<Long> {
        val header = Arrays.asList(*rows.get(0))
        val description = header.indexOf(Constants.COLUMN_DESCRIPTION)
        val money = header.indexOf(Constants.COLUMN_MONEY)
        val moneys: MutableList<Long> = ArrayList()
        for (row in rows.subList(1, rows.size)) {
            if (description(transaction).equals(row[description])) {
                moneys.add(BigDecimal(row[money]).movePointRight(2).abs().longValueExact())
            }
        }
        Collections.sort(moneys)
        return moneys
    }

    /** The amounts the wallet holds under the transaction's description, smallest first. */
    private fun moneysOf(transaction: Long, wallet: Long): List<Long> {
        val moneys: MutableList<Long> = ArrayList()
        database(mContext).rawQuery("SELECT transaction_money FROM transactions " +
                "WHERE deleted = 0 AND transaction_description = ? AND transaction_wallet = ? " +
                "ORDER BY transaction_money", arrayOf(description(transaction), wallet.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                moneys.add(cursor.getLong(0))
            }
        }
        return moneys
    }

    private fun sorted(vararg moneys: Long): List<Long> {
        val list: MutableList<Long> = ArrayList()
        for (money in moneys) {
            list.add(money)
        }
        Collections.sort(list)
        return list
    }

    /**
     * A part of zero leaves a line under the main category for the whole total. A file made
     * before the split that holds the receipt twice imports the second copy.
     */
    @Test
    @Throws(Exception::class)
    fun aReceiptWithAPartOfZeroIsUsedUpOnce() {
        insertZeroPart(database(mContext))
        val rows = exportRows("split-zero-twice", true)
        val description = Arrays.asList(*rows.get(0)).indexOf(Constants.COLUMN_DESCRIPTION)
        for (row in ArrayList(rows)) {
            if (description(PLAIN_CASH).equals(row[description])) {
                rows.add(row)
            }
        }
        assertEquals(TRANSACTIONS.size, importRows("split-zero-twice", rows))
        val db = database(mContext)
        assertEquals(2, countDescribed(db, PLAIN_CASH))
        assertEquals(TRANSACTIONS.size + 1, countTransactions(db))
    }

    /** Its own export, the zero line included, still skips every row. */
    @Test
    @Throws(Exception::class)
    fun theExportOfAReceiptWithAPartOfZeroSkipsEveryRow() {
        insertZeroPart(database(mContext))
        val rows = exportRows("split-zero-own", false)
        var lines = 1
        for (transactionLines in LINES.values) {
            lines += transactionLines.size
        }
        assertEquals(lines, rows.size - 1)
        assertEquals(lines, importRows("split-zero-own", rows))
        assertEquals(TRANSACTIONS.size, countTransactions(database(mContext)))
    }

    private fun insertZeroPart(db: SQLiteDatabase) {
        db.execSQL("INSERT INTO transaction_splits (split_id, _transaction, split_category, " +
                "split_money, uuid, last_edit, deleted) VALUES (960, ?, ?, 0, 'split-960', 1, 0)",
                arrayOf<Any>(PLAIN_CASH, HOUSEHOLD))
    }

    /** The rows of an export, made with every part flagged deleted when before is set. */
    @Throws(Exception::class)
    private fun exportRows(name: String, before: Boolean): MutableList<Array<String>> {
        val db = database(mContext)
        val live: MutableList<Long> = ArrayList()
        db.rawQuery("SELECT split_id FROM transaction_splits WHERE deleted = 0", null).use { cursor ->
            while (cursor.moveToNext()) {
                live.add(cursor.getLong(0))
            }
        }
        if (before) {
            db.execSQL("UPDATE transaction_splits SET deleted = 1")
        }
        val folder = File(mContext.cacheDir, name)
        assertTrue(folder.isDirectory || folder.mkdirs())
        val exporter = ImportExportIntentService.exportTransactions(mContext,
                DataFormat.CSV, folder, null, null, null, false, null)
        for (id in live) {
            db.execSQL("UPDATE transaction_splits SET deleted = 0 WHERE split_id = ?", arrayOf<Any>(id))
        }
        return readRows(exporter.outputFile)
    }

    /** Imports the rows and answers how many were skipped as already saved. */
    @Throws(Exception::class)
    private fun importRows(name: String, rows: List<Array<String>>): Int {
        val file = File(mContext.cacheDir, name + ".csv")
        CSVWriter(FileWriter(file)).use { writer ->
            writer.writeAll(rows)
        }
        val importer = CSVDataImporter(mContext, file)
        try {
            importer.importData()
            return importer.alreadySavedRows
        } finally {
            importer.close()
        }
    }

    /** A copy of a row of the transaction carrying another amount, in cents. */
    private fun withMoney(rows: List<Array<String>>, transaction: Long, cents: Long): Array<String> {
        val header = Arrays.asList(*rows.get(0))
        val description = header.indexOf(Constants.COLUMN_DESCRIPTION)
        for (row in rows) {
            if (description(transaction).equals(row[description])) {
                val copy = row.clone()
                copy[header.indexOf(Constants.COLUMN_MONEY)] = BigDecimal.valueOf(cents, 2).toPlainString()
                return copy
            }
        }
        throw AssertionError("no row of " + transaction)
    }

    private fun countDescribed(db: SQLiteDatabase, transaction: Long): Int {
        db.rawQuery("SELECT COUNT(*) FROM transactions WHERE deleted = 0 " +
                "AND transaction_description = ?", arrayOf(description(transaction))).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    @Throws(Exception::class)
    private fun readRows(file: File): MutableList<Array<String>> {
        CSVReader(FileReader(file)).use { reader ->
            return ArrayList(reader.readAll())
        }
    }

    private fun countTransactions(db: SQLiteDatabase): Int {
        db.rawQuery("SELECT COUNT(*) FROM transactions WHERE deleted = 0", null).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }
}
