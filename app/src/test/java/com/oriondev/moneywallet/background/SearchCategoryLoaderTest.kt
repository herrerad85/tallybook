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

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context

import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.ui.activity.NewEditTransactionActivity

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.util.ArrayList
import java.util.Arrays
import java.util.HashMap

import org.junit.Assert.assertEquals

/**
 * The Category editor's rows and counts over rows written through the provider. Every expected
 * figure is counted from what the rows mean.
 */
@RunWith(RobolectricTestRunner::class)
class SearchCategoryLoaderTest {

    companion object {

        private const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"
    }

    private lateinit var mContext: Context
    private lateinit var mResolver: ContentResolver
    private var mEuro: Long = 0
    private var mGroceries: Long = 0

    /**
     * Food with one row of its own, Groceries under it with two and Dining under it with one,
     * Salary with one, and a taxed transfer between two wallets.
     */
    @Before
    fun setUp() {
        mContext = ApplicationProvider.getApplicationContext()
        TestDatabases.useFreshDatabase(mContext)
        mResolver = mContext.contentResolver
        mEuro = insertWallet("Euro")
        val savings = insertWallet("Savings")
        val food = insertCategory("Food", Contract.CategoryType.EXPENSE, null)
        mGroceries = insertCategory("Groceries", Contract.CategoryType.EXPENSE, food)
        val dining = insertCategory("Dining", Contract.CategoryType.EXPENSE, food)
        val salary = insertCategory("Salary", Contract.CategoryType.INCOME, null)
        insertTransaction(food)
        insertTransaction(mGroceries)
        insertTransaction(mGroceries)
        insertTransaction(dining)
        insertTransaction(salary)
        insertTransfer(mEuro, savings)
    }

    @Test
    fun aParentCountsItsOwnRowsAndEveryChilds() {
        assertEquals(4, counts().get("Food")!!)
    }

    @Test
    fun aChildCountsOnlyItsOwnRows() {
        assertEquals(2, counts().get("Groceries")!!)
        assertEquals(1, counts().get("Dining")!!)
    }

    @Test
    fun theTransferCategoriesCountBothSidesAndTheTaxOfATransfer() {
        assertEquals(2, counts().get("Transfer")!!)
        assertEquals(1, counts().get("Transfer tax")!!)
        assertEquals(1, counts().get("Salary")!!)
    }

    @Test
    fun aSystemCategoryWithNoRowsCountsZero() {
        assertEquals(0, counts().get("Debt")!!)
    }

    @Test
    fun aDeletedTransactionIsNotCounted() {
        val deleted = insertTransaction(mGroceries)
        assertEquals(1, mResolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, deleted), null, null))
        assertEquals(2, counts().get("Groceries")!!)
        assertEquals(4, counts().get("Food")!!)
    }

    @Test
    fun expenseParentsComeFirstThenIncomeThenTheSystemCategoriesWithChildrenUnderTheirParent() {
        val categories = SearchCategoryLoader(mContext).loadInBackground()!!
        assertEquals(Arrays.asList("Food", "Salary", "Credit", "Credit paid", "Debt", "Debt paid",
                "Deposit", "Interest paid", "Interest received", "Tax", "Transfer", "Transfer tax",
                "Withdraw"), names(categories))
        assertEquals(Arrays.asList("Dining", "Groceries"), names(categories.get(0).getChildren()))
        for (category in categories.subList(2, categories.size)) {
            assertEquals(Contract.CategoryType.SYSTEM, category.getType())
        }
    }

    @Test
    fun parentsAndTheChildrenUnderThemComeInNameOrderWhateverTheCaseOrAccentOfTheirFirstLetter() {
        insertCategory("Zed", Contract.CategoryType.EXPENSE, null)
        val apple = insertCategory("apple", Contract.CategoryType.EXPENSE, null)
        insertCategory("bob", Contract.CategoryType.EXPENSE, null)
        insertCategory("Émile", Contract.CategoryType.EXPENSE, null)
        insertCategory("Zinc", Contract.CategoryType.EXPENSE, apple)
        insertCategory("acorn", Contract.CategoryType.EXPENSE, apple)
        insertCategory("berry", Contract.CategoryType.EXPENSE, apple)
        insertCategory("Éclair", Contract.CategoryType.EXPENSE, apple)
        val categories = SearchCategoryLoader(mContext).loadInBackground()!!
        assertEquals(Arrays.asList("apple", "bob", "Émile", "Food", "Zed", "Salary", "Credit",
                "Credit paid", "Debt", "Debt paid", "Deposit", "Interest paid", "Interest received",
                "Tax", "Transfer", "Transfer tax", "Withdraw"), names(categories))
        assertEquals(Arrays.asList("acorn", "berry", "Éclair", "Zinc"), names(categories.get(0).getChildren()))
    }

    private fun counts(): Map<String?, Int> {
        val counts: MutableMap<String?, Int> = HashMap()
        for (category in SearchCategoryLoader(mContext).loadInBackground()!!) {
            counts.put(category.getName(), category.getCount())
            for (child in category.getChildren()) {
                counts.put(child.getName(), child.getCount())
            }
        }
        return counts
    }

    private fun names(categories: List<SearchCategoryLoader.Entry>): List<String?> {
        val names: MutableList<String?> = ArrayList()
        for (category in categories) {
            names.add(category.getName())
        }
        return names
    }

    private fun insertWallet(name: String): Long {
        val values = ContentValues()
        values.put(Contract.Wallet.NAME, name)
        values.put(Contract.Wallet.ICON, ICON)
        values.put(Contract.Wallet.CURRENCY, "EUR")
        values.put(Contract.Wallet.START_MONEY, 0L)
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true)
        values.put(Contract.Wallet.ARCHIVED, false)
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_WALLETS, values)!!)
    }

    private fun insertCategory(name: String, type: Contract.CategoryType, parent: Long?): Long {
        val values = ContentValues()
        values.put(Contract.Category.NAME, name)
        values.put(Contract.Category.ICON, ICON)
        values.put(Contract.Category.TYPE, type.value)
        values.put(Contract.Category.SHOW_REPORT, true)
        values.put(Contract.Category.PARENT, parent)
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, values)!!)
    }

    private fun insertTransaction(category: Long): Long {
        val values = ContentValues()
        values.put(Contract.Transaction.MONEY, 200L)
        values.put(Contract.Transaction.DATE, "2026-01-15 12:00:00")
        values.put(Contract.Transaction.DESCRIPTION, "Row")
        values.put(Contract.Transaction.CATEGORY_ID, category)
        values.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE)
        values.put(Contract.Transaction.TYPE, NewEditTransactionActivity.TYPE_STANDARD)
        values.put(Contract.Transaction.WALLET_ID, mEuro)
        values.put(Contract.Transaction.CONFIRMED, true)
        values.put(Contract.Transaction.COUNT_IN_TOTAL, true)
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values)!!)
    }

    private fun insertTransfer(from: Long, to: Long) {
        val values = ContentValues()
        values.put(Contract.Transfer.DESCRIPTION, "Moved")
        values.put(Contract.Transfer.DATE, "2026-01-15 12:00:00")
        values.put(Contract.Transfer.TRANSACTION_FROM_WALLET_ID, from)
        values.put(Contract.Transfer.TRANSACTION_FROM_MONEY, 1000L)
        values.put(Contract.Transfer.TRANSACTION_TO_WALLET_ID, to)
        values.put(Contract.Transfer.TRANSACTION_TO_MONEY, 1000L)
        values.put(Contract.Transfer.TRANSACTION_TAX_WALLET_ID, from)
        values.put(Contract.Transfer.TRANSACTION_TAX_MONEY, 50L)
        values.put(Contract.Transfer.CONFIRMED, true)
        values.put(Contract.Transfer.COUNT_IN_TOTAL, true)
        mResolver.insert(DataContentProvider.CONTENT_TRANSFERS, values)
    }
}
