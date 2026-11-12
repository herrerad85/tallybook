/*
 * Copyright (c) 2018.
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

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.net.Uri

import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.CategoryMoney
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Money
import com.oriondev.moneywallet.model.PeriodDetailFlowData
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.view.chart.PieData
import com.oriondev.moneywallet.ui.view.chart.PieSlice
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader

import java.util.Collections
import java.util.Comparator
import java.util.Date

/**
 * Created by andrea on 13/08/18.
 */
class PeriodDetailFlowLoader : AbstractGenericLoader<PeriodDetailFlowData> {


    private val mStartDate: Date?
    private val mEndDate: Date?
    private val mIncomes: Boolean

    constructor(context: Context, startDate: Date?, endDate: Date?, incomes: Boolean) : super(context) {
        mStartDate = startDate
        mEndDate = endDate
        mIncomes = incomes
    }

    override fun getObservedUri(): Uri? {
        return DataContentProvider.CONTENT_ALL
    }

    @SuppressLint("UseSparseArrays")
    override fun loadInBackground(): PeriodDetailFlowData? {
        val totalMoney = Money()
        val pieDataSets: MutableMap<CurrencyUnit?, PieData> = HashMap()
        val categoryMoneyMap: MutableMap<Long, CategoryMoney> = HashMap()
        val childMoneyMap: MutableMap<Long, MutableMap<Long, CategoryMoney>> = HashMap()
        val directMoneyMap: MutableMap<Long, Money> = HashMap()
        val categoryCache = loadCategoryCache()
        val childCategoryCache = loadChildCategoryCache()
        val uri = DataContentProvider.CONTENT_CATEGORY_LINES
        val projection = arrayOf(
                Contract.Transaction.CATEGORY_ID,
                Contract.Transaction.CATEGORY_PARENT_ID,
                Contract.Transaction.MONEY,
                Contract.Transaction.WALLET_CURRENCY
        )
        var selection: String
        val selectionArgs: Array<String?>?
        val currentWallet = PreferenceManager.getCurrentWallet()
        if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
            selection = Contract.Transaction.WALLET_COUNT_IN_TOTAL + " = 1"
            selectionArgs = null
        } else {
            selection = Contract.Transaction.WALLET_ID + " = ?"
            selectionArgs = arrayOf(currentWallet.toString())
        }
        selection += " AND " + Contract.Transaction.CONFIRMED + " = '1' AND " + Contract.Transaction.COUNT_IN_TOTAL + " = '1'"
        selection += " AND " + Contract.Transaction.REPORT_FILTER
        selection += " AND DATETIME(" + Contract.Transaction.DATE + ") <= DATETIME('now', 'localtime')"
        selection += " AND " + Contract.Transaction.DIRECTION + " = " + (if (mIncomes) Contract.Direction.INCOME else Contract.Direction.EXPENSE)
        // This tab sums one direction on its own, so it matches the month header on the
        // transactions list and holds transfers out of the total and off the pie.
        selection += " AND " + Contract.Transaction.NOT_TRANSFER
        if (mStartDate != null) {
            selection += " AND DATETIME(" + Contract.Transaction.DATE + ") >= DATETIME('" + DateUtils.getSQLDateTimeString(mStartDate) + "')"
        }
        if (mEndDate != null) {
            selection += " AND DATETIME(" + Contract.Transaction.DATE + ") <= DATETIME('" + DateUtils.getSQLDateTimeString(mEndDate) + "')"
        }
        val sortOrder = Contract.Transaction.CATEGORY_ID
        val cursor: Cursor? = context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    val ownId = cursor.getLong(cursor.getColumnIndex(Contract.Transaction.CATEGORY_ID))
                    val categoryId: Long
                    val childId: Long
                    if (cursor.isNull(cursor.getColumnIndex(Contract.Transaction.CATEGORY_PARENT_ID))) {
                        categoryId = ownId
                        childId = 0L
                    } else {
                        categoryId = cursor.getLong(cursor.getColumnIndex(Contract.Transaction.CATEGORY_PARENT_ID))
                        childId = ownId
                    }
                    val money = cursor.getLong(cursor.getColumnIndex(Contract.Transaction.MONEY))
                    val iso = cursor.getString(cursor.getColumnIndex(Contract.Transaction.WALLET_CURRENCY))
                    if (categoryCache.containsKey(categoryId)) {
                        val child = if (childId != 0L) childCategoryCache.get(childId) else null
                        if (child != null) {
                            addChildMoney(childMoneyMap, categoryId, childId, child, iso, money)
                        } else {
                            addDirectMoney(directMoneyMap, categoryId, iso, money)
                        }
                    }
                    if (categoryMoneyMap.containsKey(categoryId)) {
                        val categoryMoney = categoryMoneyMap.get(categoryId)
                        categoryMoney!!.getMoney().addMoney(iso, money)
                    } else {
                        val category = categoryCache.get(categoryId)
                        if (category != null) {
                            val categoryMoney = CategoryMoney(
                                    categoryId,
                                    category.name,
                                    category.icon,
                                    Money(iso, money)
                            )
                            categoryMoneyMap.put(categoryId, categoryMoney)
                        }
                    }
                } while (cursor.moveToNext())
            }
            cursor.close()
        }
        val categoryMoneyList: MutableList<CategoryMoney> = ArrayList()
        for (categoryMoney in categoryMoneyMap.values) {
            val money = categoryMoney.getMoney()
            totalMoney.addMoney(money)
            for (entry in money.currencyMoneys.entries) {
                val currency = CurrencyManager.getCurrency(entry.key)
                if (pieDataSets.containsKey(currency)) {
                    val pieData = pieDataSets.get(currency)
                    pieData!!.add(PieSlice(categoryMoney.getName(), entry.value, categoryMoney.getIcon()!!.getDrawable(context)))
                } else {
                    val pieData = PieData()
                    pieData.add(PieSlice(categoryMoney.getName(), entry.value, categoryMoney.getIcon()!!.getDrawable(context)))
                    pieDataSets.put(currency, pieData)
                }
            }
            attachChildren(categoryMoney, childMoneyMap.get(categoryMoney.getId()),
                    directMoneyMap.get(categoryMoney.getId()))
            categoryMoneyList.add(categoryMoney)
        }
        val pieDataList: MutableList<PieData> = ArrayList()
        for (entry in pieDataSets.entries) {
            pieDataList.add(entry.value)
        }
        return PeriodDetailFlowData(totalMoney, pieDataList, categoryMoneyList)
    }

    /** The caller checked that the parent has a row, so a child counted here is also counted in
     * the parent row above it. */
    private fun addChildMoney(childMoneyMap: MutableMap<Long, MutableMap<Long, CategoryMoney>>,
                              parentId: Long, childId: Long, child: Category, iso: String?, money: Long) {
        var children = childMoneyMap.get(parentId)
        if (children == null) {
            children = HashMap()
            childMoneyMap.put(parentId, children)
        }
        val childMoney = children.get(childId)
        if (childMoney != null) {
            childMoney.getMoney().addMoney(iso, money)
        } else {
            children.put(childId, CategoryMoney(childId, child.name, child.icon,
                    Money(iso, money)))
        }
    }

    private fun addDirectMoney(directMoneyMap: MutableMap<Long, Money>, categoryId: Long, iso: String?, money: Long) {
        val direct = directMoneyMap.get(categoryId)
        if (direct == null) {
            directMoneyMap.put(categoryId, Money(iso, money))
        } else {
            direct.addMoney(iso, money)
        }
    }

    /**
     * Sorted by name, because the map has no order of its own and rows that move between two loads
     * of one period read as a defect. Money filed on the category itself leads the list under the
     * category's own name, so the rows always add up to the category.
     */
    private fun attachChildren(parent: CategoryMoney, children: MutableMap<Long, CategoryMoney>?, direct: Money?) {
        if (children == null) {
            return
        }
        if (direct != null) {
            parent.addChild(CategoryMoney(parent.getId(), parent.getName(), parent.getIcon(), direct))
        }
        val sorted: MutableList<CategoryMoney> = ArrayList(children.values)
        Collections.sort(sorted, object : Comparator<CategoryMoney> {

            override fun compare(left: CategoryMoney, right: CategoryMoney): Int {
                return String.CASE_INSENSITIVE_ORDER.compare(left.getName()!!, right.getName()!!)
            }

        })
        for (child in sorted) {
            parent.addChild(child)
        }
    }

    /**
     * Both caches name rows and neither decides what counts. A category hidden from the reports is
     * already out, because REPORT_FILTER drops its transactions before they reach this loader.
     */
    private fun loadChildCategoryCache(): MutableMap<Long, Category> {
        return loadCategories(Contract.Category.PARENT + " IS NOT NULL")
    }

    @SuppressLint("UseSparseArrays")
    private fun loadCategoryCache(): MutableMap<Long, Category> {
        return loadCategories(Contract.Category.PARENT + " IS NULL")
    }

    @SuppressLint("UseSparseArrays")
    private fun loadCategories(selection: String): MutableMap<Long, Category> {
        val cache: MutableMap<Long, Category> = HashMap()
        val uri = DataContentProvider.CONTENT_CATEGORIES
        val projection = arrayOf(
                Contract.Category.ID,
                Contract.Category.NAME,
                Contract.Category.ICON,
                Contract.Category.TYPE
        )
        val cursor: Cursor? = context.contentResolver.query(uri, projection, selection, null, null)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    val categoryId = cursor.getLong(cursor.getColumnIndex(Contract.Category.ID))
                    val category = Category(categoryId,
                            cursor.getString(cursor.getColumnIndex(Contract.Category.NAME)),
                            IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Category.ICON))),
                            Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndex(Contract.Category.TYPE)))
                    )
                    cache.put(categoryId, category)
                } while (cursor.moveToNext())
            }
            cursor.close()
        }
        return cache
    }
}
