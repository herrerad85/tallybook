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

package com.oriondev.moneywallet.background

import android.content.Context

import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.model.Group
import com.oriondev.moneywallet.model.OverviewData
import com.oriondev.moneywallet.model.OverviewSetting
import com.oriondev.moneywallet.model.SearchFilter
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.SplitLedger
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.preference.PreferenceManager

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.math.BigDecimal
import java.util.Arrays
import java.util.Calendar
import java.util.Date
import java.util.HashMap

import com.oriondev.moneywallet.storage.database.SplitLedger.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

/**
 * The reports and the search over [SplitLedger]. Every figure is worked out by hand from
 * its rows. The expenses of the month are 10000 + 4000 + 1000 + 3000 + 2000 + 6000 + 5000 + 7000
 * + 1100 + 2200, of which the 1200 part in Hidden is kept out of every report, and the incomes
 * are 30000 + 8000.
 */
@RunWith(RobolectricTestRunner::class)
class SplitReportsTest {

    companion object {

        private const val EXPENSES: Long = 10000 + 4000 + 1000 + 3000 + 2000 + 6000 + 5000 + 7000 + 1100 + 2200
        private const val REPORTED_EXPENSES: Long = EXPENSES - 1200
        private const val INCOMES: Long = 30000 + 8000
        /** Every Household line, the Hidden one left out by the reports. */
        private const val HOUSEHOLD_REPORTED: Long = 2000 + 2000 + (5000 - 1200) + 1000 + 1500 + 2200
    }

    private lateinit var mContext: Context

    @Before
    fun setUp() {
        mContext = ApplicationProvider.getApplicationContext()
        PreferenceManager.initialize(mContext)
        TestDatabases.useFreshDatabase(mContext)
        PreferenceManager.setCurrentWallet(mContext, PreferenceManager.TOTAL_WALLET_ID)
        SplitLedger.build(mContext)
    }

    @Test
    fun theOverviewCashFlowLeavesOutAPartInAHiddenCategory() {
        val data = OverviewDataLoader(mContext, OverviewSetting(start(), end(),
                Group.YEARLY, OverviewSetting.CashFlow.NET_INCOMES)).loadInBackground()!!
        assertEquals(REPORTED_EXPENSES, expenses(data))
        assertEquals(INCOMES, incomes(data))
    }

    @Test
    fun theOverviewOfOneCategoryAddsUpItsLines() {
        var data = OverviewDataLoader(mContext, OverviewSetting(start(), end(),
                Group.YEARLY, HOUSEHOLD)).loadInBackground()!!
        assertEquals(HOUSEHOLD_REPORTED, expenses(data))
        data = OverviewDataLoader(mContext, OverviewSetting(start(), end(),
                Group.YEARLY, FUEL)).loadInBackground()!!
        assertEquals(3000L + 2500 + 3000 + 6000, expenses(data))
        data = OverviewDataLoader(mContext, OverviewSetting(start(), end(),
                Group.YEARLY, BONUS)).loadInBackground()!!
        assertEquals(3000L, incomes(data))
    }

    @Test
    fun thePeriodSummaryLeavesOutAPartInAHiddenCategory() {
        val data = PeriodDetailSummaryLoader(mContext, start(), end(),
                PeriodDetailSummaryLoader.GROUP_BY_YEAR).loadInBackground()!!
        var expenses: Long = 0
        var incomes: Long = 0
        for (i in 0 until data.getPeriodCount()) {
            expenses += data.getPeriodMoney(i).getExpenses().getMoney("EUR")
            incomes += data.getPeriodMoney(i).getIncomes().getMoney("EUR")
        }
        assertEquals(REPORTED_EXPENSES, expenses)
        assertEquals(INCOMES, incomes)
        assertEquals(INCOMES - REPORTED_EXPENSES, data.getNetIncomes().getMoney("EUR"))
    }

    @Test
    fun thePieGivesEachCategoryItsLines() {
        val expected: MutableMap<Long, Long> = HashMap()
        expected.put(GROCERIES, (10000L - 2000 - 3000) + (7000 - 1000 - 1500) + 1100)
        expected.put(HOUSEHOLD, HOUSEHOLD_REPORTED)
        expected.put(FUEL, 3000L + 2500 + 3000 + 6000)
        expected.put(PHARMACY, 1500L + 1000)
        assertEquals(expected, pie(false))
        val incomes: MutableMap<Long, Long> = HashMap()
        incomes.put(SALARY, 30000L + (8000 - 3000))
        incomes.put(BONUS, 3000L)
        assertEquals(incomes, pie(true))
    }

    @Test
    fun aSearchWithACategoryTickedListsEachRowOnceWithItsShare() {
        val filter = SearchFilter()
        filter.setCategoryIds(Arrays.asList(HOUSEHOLD))
        val expected: MutableMap<Long, Long> = HashMap()
        expected.put(RECEIPT, 2000L)
        expected.put(CROSSED, 2000L)
        expected.put(WITH_HIDDEN, 5000L - 1200)
        expected.put(TWICE, 1000L + 1500)
        expected.put(PLAIN_BANK, 2200L)
        assertSearch(filter, expected, 2000L + 2000 + (5000 - 1200) + 2500 + 2200, null)
    }

    /** A parent's checkbox ticks its children too, and a row in both counts once. */
    @Test
    fun aSearchWithAParentAndItsChildTickedCountsARowInBothOnce() {
        val filter = SearchFilter()
        filter.setCategoryIds(Arrays.asList(HOUSEHOLD, HIDDEN))
        val expected: MutableMap<Long, Long> = HashMap()
        expected.put(RECEIPT, 2000L)
        expected.put(CROSSED, 2000L)
        expected.put(WITH_HIDDEN, 5000L)
        expected.put(TWICE, 1000L + 1500)
        expected.put(PLAIN_BANK, 2200L)
        assertSearch(filter, expected, 2000L + 2000 + 5000 + 2500 + 2200, null)
    }

    /** The amount still matches the receipt, and the row still carries the share. */
    @Test
    fun theAmountOfASearchMatchesTheTotalAndTheRowCarriesTheShare() {
        val filter = SearchFilter()
        filter.setCategoryIds(Arrays.asList(HOUSEHOLD))
        filter.setAmount(SearchFilter.AmountOp.EXACTLY, SearchFilter.AmountSide.EITHER, BigDecimal("100"), null)
        val expected: MutableMap<Long, Long> = HashMap()
        expected.put(RECEIPT, 2000L)
        assertSearch(filter, expected, 2000L, null)
    }

    @Test
    fun aSearchWithNoCategoryTickedReadsTheWholeTransactions() {
        val expected: MutableMap<Long, Long> = HashMap()
        for (transaction in TRANSACTIONS) {
            expected.put(transaction[0], transaction[5])
        }
        assertSearch(SearchFilter(), expected, EXPENSES, INCOMES)
    }

    /**
     * Each category counts the transactions with a line in it. Household counts the one with a
     * part in its hidden child once, though it has a line in both.
     */
    @Test
    fun theCategoryStripCountsTransactionsWithALineInEachCategory() {
        val counts: MutableMap<String?, Int> = HashMap()
        for (category in SearchCategoryLoader(mContext).loadInBackground()!!) {
            counts.put(category.getName(), category.getCount())
            for (child in category.getChildren()) {
                counts.put(child.getName(), child.getCount())
            }
        }
        // Receipt, Twice and the plain one. Exact has no line left in Groceries, and the part
        // of Negative does not count since its split is invalid
        assertEquals(3, counts.get("Groceries")!!)
        // Receipt, Crossed, With hidden, Twice and the plain one in the bank
        assertEquals(5, counts.get("Household")!!)
        assertEquals(1, counts.get("Hidden")!!)
        // Receipt, Exact, Negative and the debt row
        assertEquals(4, counts.get("Fuel")!!)
        // Exact and Over
        assertEquals(2, counts.get("Pharmacy")!!)
        assertEquals(2, counts.get("Salary")!!)
        assertEquals(1, counts.get("Bonus")!!)
        assertEquals(0, counts.get("Gifts")!!)
    }

    private fun assertSearch(filter: SearchFilter, expected: Map<Long, Long>, out: Long, `in`: Long?) {
        val cursor = SearchCursorLoader(mContext, filter).loadInBackground()!!
        try {
            val rows: MutableMap<Long, Long> = HashMap()
            while (cursor.moveToNext()) {
                assertNull("each row once", rows.put(
                        cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.ID)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.MONEY))))
            }
            assertEquals(expected, rows)
            val summary = cursor as SearchCursorLoader.Summary
            assertEquals(expected.size, summary.getMatchCount())
            assertEquals(out, summary.getOut())
            assertEquals(`in`, summary.getIn())
        } finally {
            cursor.close()
        }
    }

    private fun pie(incomes: Boolean): Map<Long, Long> {
        val data = PeriodDetailFlowLoader(mContext, start(), end(), incomes).loadInBackground()!!
        val money: MutableMap<Long, Long> = HashMap()
        for (i in 0 until data.getCategoryCount()) {
            val category = data.getCategory(i)
            money.put(category.getId(), category.getMoney().getMoney("EUR"))
        }
        return money
    }

    private fun expenses(data: OverviewData): Long {
        var money: Long = 0
        for (i in 0 until data.getPeriodCount()) {
            money += data.getPeriodMoney(i).getExpenses().getMoney("EUR")
        }
        return money
    }

    private fun incomes(data: OverviewData): Long {
        var money: Long = 0
        for (i in 0 until data.getPeriodCount()) {
            money += data.getPeriodMoney(i).getIncomes().getMoney("EUR")
        }
        return money
    }

    private fun start(): Date {
        val calendar = Calendar.getInstance()
        calendar.clear()
        calendar.set(2026, Calendar.JANUARY, 1, 0, 0, 0)
        return calendar.time
    }

    private fun end(): Date {
        val calendar = Calendar.getInstance()
        calendar.clear()
        calendar.set(2026, Calendar.JANUARY, 31, 23, 59, 59)
        return calendar.time
    }
}
