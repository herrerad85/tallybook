package com.oriondev.moneywallet.ui.fragment.base

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DeletedLedger
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.CalendarActivity
import com.oriondev.moneywallet.ui.activity.MainActivity
import com.oriondev.moneywallet.ui.activity.SearchActivity
import com.oriondev.moneywallet.ui.activity.TransactionListActivity
import com.oriondev.moneywallet.ui.adapter.recycler.TransactionCursorAdapter
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.awaitWorker
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.executorOf
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.idleMainLooper
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.idleMainLooperFor

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransactionSwipeScreensTest {

    private lateinit var context: Context
    private lateinit var ledger: DeletedLedger
    private var storedWallet = 0L
    private var category = 0L
    private var transaction = 0L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.initialize(context)
        storedWallet = PreferenceManager.getCurrentWallet()
        TestDatabases.useFreshDatabase(context)
        ledger = DeletedLedger(context)
        val cash = ledger.insertWallet("Cash", "EUR")
        category = ledger.insertCategory("Groceries", Contract.CategoryType.EXPENSE)
        transaction = ledger.insertTransaction(ledger.transaction(cash, category, 400L))
        PreferenceManager.setCurrentWallet(context, cash)
    }

    @After
    fun tearDown() {
        PreferenceManager.setCurrentWallet(context, storedWallet)
    }

    @Test
    fun theMainListAttachesTheSwipe() {
        assertEquals(listOf(1), swipeHelpersOn(Intent(context, MainActivity::class.java)))
    }

    @Test
    fun theFilteredListAttachesTheSwipe() {
        assertEquals(listOf(1), swipeHelpersOn(Intent(context, TransactionListActivity::class.java)
                .putExtra(TransactionListActivity.CATEGORY_ID, category)))
    }

    @Test
    fun theSearchResultsAttachTheSwipe() {
        assertEquals(listOf(1), swipeHelpersOn(Intent(context, SearchActivity::class.java)))
    }

    @Test
    fun theCalendarAttachesTheSwipe() {
        assertEquals(listOf(1), swipeHelpersOn(Intent(context, CalendarActivity::class.java)))
    }

    @Test
    fun onTheMainListALeftwardDragTurnsThePagerAndDeletesNothing() {
        val stored = ledger.transactionRows(transaction)
        assertEquals(1, dragOnTheMainList(false))
        assertEquals(stored, ledger.transactionRows(transaction))
    }

    @Test
    fun onTheMainListARightwardDragDeletesTheRowAndThePagerStays() {
        assertEquals(0, dragOnTheMainList(true))
        assertNotEquals(setOf<Any?>(0L), ledger.deletedOf(ledger.transactionRows(transaction)))
    }

    private fun dragOnTheMainList(rightward: Boolean): Int {
        var page = -1
        ActivityScenario.launch<Activity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                var row: View? = null
                for (i in 0 until 200) {
                    idleMainLooper()
                    row = rowOf(activity.window.decorView)
                    if (row != null) {
                        break
                    }
                    Thread.sleep(25)
                }
                val pager = pagerAbove(row!!)
                assertEquals(0, pager.currentItem)
                val location = IntArray(2)
                row.getLocationInWindow(location)
                val width = row.width
                val y = location[1] + row.height / 2f
                val slop = ViewConfiguration.get(activity).scaledTouchSlop
                assertTrue(slop < ViewConfiguration.get(activity).scaledPagingTouchSlop)
                val distance = 3f * slop / 8f
                val step = if (rightward) distance else -distance
                val from = location[0] + (if (rightward) 0.15f else 0.85f) * width
                val moves = (0.75f * width / distance).toInt()
                val time = SystemClock.uptimeMillis()
                val window = activity.window.decorView
                touch(window, time, 0, MotionEvent.ACTION_DOWN, from, y)
                for (i in 1..moves) {
                    touch(window, time, i * 10L, MotionEvent.ACTION_MOVE, from + i * step, y)
                }
                touch(window, time, (moves + 1) * 10L, MotionEvent.ACTION_UP, from + moves * step, y)
                for (i in 0 until 60) {
                    idleMainLooperFor(16)
                }
                assertTrue(awaitWorker(executorOf(TransactionSelectionMode::class.java)))
                idleMainLooper()
                page = pager.currentItem
            }
        }
        return page
    }

    private fun touch(view: View, downTime: Long, after: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, downTime + after, action, x, y, 0)
        view.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun rowOf(view: View): View? = when {
        view is RecyclerView && view.adapter is TransactionCursorAdapter -> {
            val adapter = view.adapter as TransactionCursorAdapter
            (0 until adapter.itemCount).firstOrNull { adapter.getItemId(it) == transaction && adapter.isSwipeable(it) }
                    ?.let { view.findViewHolderForAdapterPosition(it)?.itemView }
        }
        view is ViewGroup -> (0 until view.childCount).asSequence().mapNotNull { rowOf(view.getChildAt(it)) }.firstOrNull()
        else -> null
    }

    private fun pagerAbove(view: View): ViewPager = view.parent as? ViewPager ?: pagerAbove(view.parent as View)

    private fun swipeHelpersOn(intent: Intent): List<Int> {
        var helpers = emptyList<Int>()
        ActivityScenario.launch<Activity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                for (i in 0 until 200) {
                    idleMainLooper()
                    helpers = swipeHelpersUnder(activity.window.decorView)
                    if (helpers.isNotEmpty()) {
                        break
                    }
                    Thread.sleep(25)
                }
            }
        }
        return helpers
    }

    private fun swipeHelpersUnder(view: View): List<Int> = when {
        view is RecyclerView && view.adapter is TransactionCursorAdapter ->
            listOf((0 until view.itemDecorationCount).count { view.getItemDecorationAt(it) is ItemTouchHelper })
        view is ViewGroup -> (0 until view.childCount).flatMap { swipeHelpersUnder(view.getChildAt(it)) }
        else -> emptyList()
    }
}
