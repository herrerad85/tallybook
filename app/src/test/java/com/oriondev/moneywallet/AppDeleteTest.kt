package com.oriondev.moneywallet

import android.database.sqlite.SQLiteException
import android.util.Log

import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DeletedLedger
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.awaitWorker
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.executorOf
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.whileParked

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class AppDeleteTest {

    private lateinit var app: App
    private lateinit var ledger: DeletedLedger
    private var cash = 0L
    private var bank = 0L
    private var groceries = 0L

    private val worker = executorOf(App::class.java)

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        TestDatabases.useFreshDatabase(app)
        ledger = DeletedLedger(app)
        cash = ledger.insertWallet("Cash", "EUR")
        bank = ledger.insertWallet("Bank", "EUR")
        groceries = ledger.insertCategory("Groceries", Contract.CategoryType.EXPENSE)
    }

    @Test
    fun startupDeletesForeverWhatWasDeletedMoreThan90DaysAgoOffTheMainThread() {
        val now = System.currentTimeMillis()
        val live = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val old = ledger.insertTransaction(ledger.transaction(cash, groceries, 200L))
        val recent = ledger.insertTransaction(ledger.transaction(cash, groceries, 300L))
        val oldTransfer = ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null)
        val legs = ledger.legsOf(oldTransfer)
        ledger.flagTransactionDeleted(old, now - TimeUnit.DAYS.toMillis(91))
        ledger.flagTransactionDeleted(recent, now - TimeUnit.DAYS.toMillis(89))
        ledger.flagTransferDeleted(oldTransfer, now - TimeUnit.DAYS.toMillis(91))
        val liveRows = ledger.transactionRows(live)
        val recentRows = ledger.transactionRows(recent)
        val oldRows = ledger.transactionRows(old)

        whileParked(worker) {
            app.onCreate()
            assertEquals("the delete ran inside onCreate", oldRows, ledger.transactionRows(old))
        }

        assertEquals(emptyList<Map<String, Any?>>(), ledger.transactionRows(old))
        assertEquals(0L, ledger.count("transfers", "transfer_id = $oldTransfer"))
        assertEquals(0L, ledger.count("transactions", "transaction_id IN (${legs.joinToString(",")})"))
        assertEquals(recentRows, ledger.transactionRows(recent))
        assertEquals(liveRows, ledger.transactionRows(live))
    }

    @Test
    fun aFailingStartupDeleteIsLoggedAndSwallowed() {
        val uncaught = mutableListOf<Throwable>()
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        ledger.database().execSQL("ALTER TABLE transfers RENAME TO transfers_aside")
        Thread.setDefaultUncaughtExceptionHandler { _, e -> synchronized(uncaught) { uncaught.add(e) } }
        try {
            ShadowLog.clear()
            app.onCreate()
            assertTrue("the worker did not finish within 5 seconds", awaitWorker(worker))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(handler)
            ledger.database().execSQL("ALTER TABLE transfers_aside RENAME TO transfers")
        }

        assertEquals(emptyList<Throwable>(), synchronized(uncaught) { uncaught.toList() })
        val errors = ShadowLog.getLogsForTag("App").filter { it.type == Log.ERROR }
        assertEquals(1, errors.size)
        assertTrue(errors.single().throwable is SQLiteException)
    }
}
