package com.oriondev.moneywallet.background

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context

import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.database.TransferContentValuesBuilder
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.NewEditTransactionActivity
import com.oriondev.moneywallet.utils.DateUtils

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.util.Calendar
import java.util.Date

import org.junit.Assert.assertEquals

/**
 * The period summary sorts each row by its category tag. A transfer leg reaches the net and the
 * transfer figure but neither bar, while a transfer fee is money spent. Seen from one wallet, the
 * transfer figure is signed by the leg's direction.
 */
@RunWith(RobolectricTestRunner::class)
class PeriodDetailSummaryLoaderTest {

    companion object {

        private const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"
    }

    private lateinit var mContext: Context
    private lateinit var mResolver: ContentResolver

    @Before
    fun setUp() {
        mContext = ApplicationProvider.getApplicationContext()
        TestDatabases.useFreshDatabase(mContext)
        mResolver = mContext.contentResolver
    }

    @Test
    fun transferLegsAndTheirFeeLandInTheirOwnFigures() {
        val wallet = insertWallet("Checking")
        val other = insertWallet("Savings")
        PreferenceManager.setCurrentWallet(mContext, wallet)
        insertIncome(wallet, 50L)
        insertTransfer(other, wallet, 300L, 0L)
        insertTransfer(wallet, other, 100L, 10L)

        val data = PeriodDetailSummaryLoader(mContext,
                at(1, 0, 0, 0, 0), at(31, 23, 59, 59, 999),
                PeriodDetailSummaryLoader.GROUP_BY_YEAR).loadInBackground()!!

        assertEquals(1, data.getPeriodCount())
        val period = data.getPeriodMoney(0)
        assertEquals("only the ordinary income is earned", 50L, period.getIncomes().getMoney("EUR"))
        assertEquals("only the fee is spent", 10L, period.getExpenses().getMoney("EUR"))
        assertEquals("300 in and 100 out", 200L, period.getTransfers().getMoney("EUR"))
        assertEquals(240L, period.getNetIncomes().getMoney("EUR"))
        assertEquals(240L, data.getNetIncomes().getMoney("EUR"))
    }

    private fun at(day: Int, hour: Int, minute: Int, second: Int, millisecond: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.clear()
        calendar.set(2026, Calendar.JANUARY, day, hour, minute, second)
        calendar.set(Calendar.MILLISECOND, millisecond)
        return calendar.time
    }

    private fun midMonth(): String {
        return DateUtils.getSQLDateTimeString(at(15, 12, 0, 0, 0))
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

    private fun insertIncome(walletId: Long, money: Long) {
        val category = ContentValues()
        category.put(Contract.Category.NAME, "Salary")
        category.put(Contract.Category.ICON, ICON)
        category.put(Contract.Category.TYPE, Contract.CategoryType.INCOME.value)
        category.put(Contract.Category.SHOW_REPORT, true)
        val categoryId = ContentUris.parseId(
                mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, category)!!)
        val values = ContentValues()
        values.put(Contract.Transaction.MONEY, money)
        values.put(Contract.Transaction.DATE, midMonth())
        values.put(Contract.Transaction.DESCRIPTION, "Pay")
        values.put(Contract.Transaction.CATEGORY_ID, categoryId)
        values.put(Contract.Transaction.DIRECTION, Contract.Direction.INCOME)
        values.put(Contract.Transaction.TYPE, NewEditTransactionActivity.TYPE_STANDARD)
        values.put(Contract.Transaction.WALLET_ID, walletId)
        values.put(Contract.Transaction.CONFIRMED, true)
        values.put(Contract.Transaction.COUNT_IN_TOTAL, true)
        mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values)
    }

    private fun insertTransfer(fromWalletId: Long, toWalletId: Long, money: Long, fee: Long) {
        val values = TransferContentValuesBuilder()
                .description("Move")
                .date(midMonth())
                .fromWalletId(fromWalletId)
                .toWalletId(toWalletId)
                .taxWalletId(fromWalletId)
                .fromMoney(money)
                .toMoney(money)
                .taxMoney(fee)
                .note("")
                .confirmed(true)
                .countInTotal(true)
                .build()
        mResolver.insert(DataContentProvider.CONTENT_TRANSFERS, values)
    }
}
