package com.oriondev.moneywallet.ui.adapter.recycler

import android.database.Cursor
import android.database.MatrixCursor

import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.model.Group
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.storage.wrapper.CurrencyHeaderCursor
import com.oriondev.moneywallet.storage.wrapper.TransactionHeaderCursor

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.util.Date

@RunWith(RobolectricTestRunner::class)
class TransactionSwipeRuleTest {

    companion object {

        private const val FIRST_DAY = "0:2019-03-15 00:00:00"
        private const val DAY_15 = "2019-03-15 10:00:00"
        private const val DAY_14 = "2019-03-14 10:00:00"

        private val COLUMNS = arrayOf(
                Contract.Transaction.ID,
                Contract.Transaction.TYPE,
                Contract.Transaction.DATE,
                Contract.Transaction.DIRECTION,
                Contract.Transaction.MONEY,
                Contract.Transaction.WALLET_CURRENCY,
                Contract.Transaction.CONFIRMED,
                Contract.Transaction.COUNT_IN_TOTAL,
                Contract.Transaction.CATEGORY_NAME,
                Contract.Transaction.CATEGORY_ICON,
                Contract.Transaction.CATEGORY_TAG,
                Contract.Transaction.DESCRIPTION)

        fun rows(vararg rows: Triple<Long, Int, String>): MatrixCursor = MatrixCursor(COLUMNS).apply {
            for ((id, type, date) in rows) {
                addRow(arrayOf<Any?>(id, type, date, Contract.Direction.EXPENSE, 1000L, "EUR", 1, 1, "Category",
                        null, null, "Description"))
            }
        }

        fun twoDays(): TransactionHeaderCursor = TransactionHeaderCursor(rows(
                Triple(1L, Contract.TransactionType.STANDARD, DAY_15),
                Triple(2L, Contract.TransactionType.DEBT, DAY_15),
                Triple(3L, Contract.TransactionType.SAVING, DAY_15),
                Triple(4L, Contract.TransactionType.TRANSFER, DAY_15),
                Triple(5L, Contract.TransactionType.STANDARD, DAY_14)), Group.DAILY, null, null)
    }

    private lateinit var storedPeriods: Set<String>

    @Before
    fun setUp() {
        storedPeriods = PreferenceManager.getCollapsedPeriods()
        PreferenceManager.setCollapsedPeriods(emptySet())
    }

    @After
    fun tearDown() {
        PreferenceManager.setCollapsedPeriods(storedPeriods)
    }

    private fun adapterOn(cursor: Cursor): TransactionCursorAdapter =
            TransactionCursorAdapter(Listener()).apply { changeCursor(cursor) }

    private fun swipeable(adapter: TransactionCursorAdapter): List<Boolean> =
            (0 until adapter.itemCount).map { adapter.isSwipeable(it) }

    @Test
    fun onlyARowALongPressCouldSelectCanSwipeAndDebtAndSavingRowsAreAmongThem() {
        val adapter = adapterOn(twoDays())
        assertEquals(listOf(false, true, true, true, false, false, true), swipeable(adapter))
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), listOf(1, 2, 3, 4, 6).map { adapter.getItemId(it) })
    }

    @Test
    fun aCurrencyHeaderCannotSwipeAndTheRowsUnderItCan() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext())
        val adapter = adapterOn(CurrencyHeaderCursor(rows(
                Triple(1L, Contract.TransactionType.STANDARD, DAY_15),
                Triple(2L, Contract.TransactionType.TRANSFER, DAY_15))))
        assertEquals(listOf(false, true, false), swipeable(adapter))
    }

    @Test
    fun noRowSwipesWhileASelectionIsOpenAndEveryOneThatCouldSwipesAgainOnceItIsCleared() {
        val adapter = adapterOn(twoDays())
        adapter.setSelectedIds(longArrayOf(5L))
        assertEquals(List(7) { false }, swipeable(adapter))
        adapter.clearSelection()
        assertEquals(listOf(false, true, true, true, false, false, true), swipeable(adapter))
    }

    @Test
    fun aRowUnderAFoldedPeriodIsNotReachedAndTheRowAfterItAnswersForItself() {
        PreferenceManager.setCollapsedPeriods(setOf(FIRST_DAY))
        val adapter = adapterOn(twoDays())
        assertEquals(3, adapter.itemCount)
        assertEquals(listOf(false, false, true), swipeable(adapter))
        assertEquals(5L, adapter.getItemId(2))
        assertEquals(false, adapter.isSwipeable(3))
        assertEquals(false, adapter.isSwipeable(-1))
    }

    @Test
    fun aListWithNoCursorHasNothingToSwipe() {
        assertEquals(false, TransactionCursorAdapter(Listener()).isSwipeable(0))
    }

    private class Listener : TransactionCursorAdapter.ActionListener {

        override fun onHeaderClick(startDate: Date, endDate: Date) {
        }

        override fun onTransactionClick(id: Long) {
        }

        override fun onSelectionChanged(count: Int) {
        }
    }
}
